/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2011 Red Hat, Inc. and/or its affiliates, and individual
 * contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.xnio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.PrivilegedAction;
import java.util.List;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.AssembledConnectedMessageChannel;
import org.xnio.channels.AssembledConnectedStreamChannel;
import org.xnio.channels.BoundChannel;
import org.xnio.channels.Configurable;
import org.xnio.channels.ConnectedMessageChannel;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.channels.MulticastMessageChannel;
import org.xnio.channels.StreamChannel;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.ConduitStreamSinkChannel;
import org.xnio.conduits.ConduitStreamSourceChannel;
import org.xnio.conduits.DeflatingStreamSinkConduit;
import org.xnio.conduits.InflatingStreamSourceConduit;
import org.xnio.conduits.StreamSinkChannelWrappingConduit;
import org.xnio.conduits.StreamSourceChannelWrappingConduit;

import static java.security.AccessController.doPrivileged;
import static org.xnio.IoUtils.safeClose;
import static org.xnio._private.Messages.msg;

/**
 * A worker for I/O channel notification.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 *
 * @since 3.0
 */
@SuppressWarnings("unused")
public abstract class XnioWorker extends AbstractExecutorService implements Configurable, ExecutorService, XnioIoFactory {

    private final Xnio xnio;
    private final TaskPool taskPool;
    private final String name;
    private final Runnable terminationTask;

    private volatile int taskSeq;
    private volatile int coreSize;

    private static final AtomicIntegerFieldUpdater<XnioWorker> taskSeqUpdater = AtomicIntegerFieldUpdater.newUpdater(XnioWorker.class, "taskSeq");
    private static final AtomicIntegerFieldUpdater<XnioWorker> coreSizeUpdater = AtomicIntegerFieldUpdater.newUpdater(XnioWorker.class, "coreSize");

    private static final AtomicInteger seq = new AtomicInteger(1);

    private static final RuntimePermission CREATE_WORKER_PERMISSION = new RuntimePermission("createXnioWorker");

    private int getNextSeq() {
        return taskSeqUpdater.incrementAndGet(this);
    }

    /**
     * Construct a new instance.  Intended to be called only from implementations.  To construct an XNIO worker,
     * use the {@link Xnio#createWorker(OptionMap)} method.
     *
     * @param xnio the XNIO provider which produced this worker instance
     * @param threadGroup the thread group for worker threads
     * @param optionMap the option map to use to configure this worker
     * @param terminationTask an optional runnable task to run when the worker shutdown completes
     */
    protected XnioWorker(final Xnio xnio, final ThreadGroup threadGroup, final OptionMap optionMap, final Runnable terminationTask) {
        this.xnio = xnio;
        this.terminationTask = terminationTask;
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(CREATE_WORKER_PERMISSION);
        }
        String workerName = optionMap.get(Options.WORKER_NAME);
        if (workerName == null) {
            workerName = "XNIO-" + seq.getAndIncrement();
        }
        name = workerName;
        BlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<Runnable>();
        this.coreSize = optionMap.get(Options.WORKER_TASK_CORE_THREADS, 4);
        final boolean markThreadAsDaemon = optionMap.get(Options.THREAD_DAEMON, false);
        final int threadCount = optionMap.get(Options.WORKER_TASK_MAX_THREADS, 16);
        taskPool = new TaskPool(
            threadCount, // ignore core threads setting, always fill to max
            threadCount,
            optionMap.get(Options.WORKER_TASK_KEEPALIVE, 60000), TimeUnit.MILLISECONDS,
            taskQueue,
            new WorkerThreadFactory(threadGroup, optionMap, markThreadAsDaemon),
            new ThreadPoolExecutor.AbortPolicy());
    }

    //==================================================
    //
    // Stream methods
    //
    //==================================================

    // Servers

    /**
     * Create a stream server, for TCP or UNIX domain servers.  The type of server is determined by the bind address.
     *
     * @param bindAddress the address to bind to
     * @param acceptListener the initial accept listener
     * @param optionMap the initial configuration for the server
     * @return the acceptor
     * @throws IOException if the server could not be created
     */
    @Deprecated
    public AcceptingChannel<? extends ConnectedStreamChannel> createStreamServer(SocketAddress bindAddress, ChannelListener<? super AcceptingChannel<ConnectedStreamChannel>> acceptListener, OptionMap optionMap) throws IOException {
        final AcceptingChannel<StreamConnection> server = createStreamConnectionServer(bindAddress, null, optionMap);
        final AcceptingChannel<ConnectedStreamChannel> acceptingChannel = new AcceptingChannel<ConnectedStreamChannel>() {
            public ConnectedStreamChannel accept() throws IOException {
                final StreamConnection connection = server.accept();
                return connection == null ? null : new AssembledConnectedStreamChannel(connection, connection.getSourceChannel(), connection.getSinkChannel());
            }

            public ChannelListener.Setter<? extends AcceptingChannel<ConnectedStreamChannel>> getAcceptSetter() {
                return ChannelListeners.getDelegatingSetter(server.getAcceptSetter(), this);
            }

            public ChannelListener.Setter<? extends AcceptingChannel<ConnectedStreamChannel>> getCloseSetter() {
                return ChannelListeners.getDelegatingSetter(server.getCloseSetter(), this);
            }

            public SocketAddress getLocalAddress() {
                return server.getLocalAddress();
            }

            public <A extends SocketAddress> A getLocalAddress(final Class<A> type) {
                return server.getLocalAddress(type);
            }

            public void suspendAccepts() {
                server.suspendAccepts();
            }

            public void resumeAccepts() {
                server.resumeAccepts();
            }

            public boolean isAcceptResumed() {
                return server.isAcceptResumed();
            }

            public void wakeupAccepts() {
                server.wakeupAccepts();
            }

            public void awaitAcceptable() throws IOException {
                server.awaitAcceptable();
            }

            public void awaitAcceptable(final long time, final TimeUnit timeUnit) throws IOException {
                server.awaitAcceptable(time, timeUnit);
            }

            public XnioWorker getWorker() {
                return server.getWorker();
            }

            @Deprecated
            public XnioExecutor getAcceptThread() {
                return server.getAcceptThread();
            }

            public XnioIoThread getIoThread() {
                return chooseThread();
            }

            public void close() throws IOException {
                server.close();
            }

            public boolean isOpen() {
                return server.isOpen();
            }

            public boolean supportsOption(final Option<?> option) {
                return server.supportsOption(option);
            }

            public <T> T getOption(final Option<T> option) throws IOException {
                return server.getOption(option);
            }

            public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
                return server.setOption(option, value);
            }
        };
        acceptingChannel.getAcceptSetter().set(acceptListener);
        return acceptingChannel;
    }

    /**
     * Create a stream server, for TCP or UNIX domain servers.  The type of server is determined by the bind address.
     *
     * @param bindAddress the address to bind to
     * @param acceptListener the initial accept listener
     * @param optionMap the initial configuration for the server
     * @return the acceptor
     * @throws IOException if the server could not be created
     */
    public AcceptingChannel<StreamConnection> createStreamConnectionServer(SocketAddress bindAddress, ChannelListener<? super AcceptingChannel<StreamConnection>> acceptListener, OptionMap optionMap) throws IOException {
        if (bindAddress == null) {
            throw msg.nullParameter("bindAddress");
        }
        if (bindAddress instanceof InetSocketAddress) {
            return createTcpConnectionServer((InetSocketAddress) bindAddress, acceptListener, optionMap);
        } else if (bindAddress instanceof LocalSocketAddress) {
            return createLocalStreamConnectionServer((LocalSocketAddress) bindAddress, acceptListener, optionMap);
        } else {
            throw msg.badSockType(bindAddress.getClass());
        }
    }

    /**
     * Implementation helper method to create a TCP stream server.
     *
     * @param bindAddress the address to bind to
     * @param acceptListener the initial accept listener
     * @param optionMap the initial configuration for the server
     * @return the acceptor
     * @throws IOException if the server could not be created
     */
    protected AcceptingChannel<StreamConnection> createTcpConnectionServer(InetSocketAddress bindAddress, ChannelListener<? super AcceptingChannel<StreamConnection>> acceptListener, OptionMap optionMap) throws IOException {
        throw msg.unsupported("createTcpConnectionServer");
    }

    /**
     * Implementation helper method to create a UNIX domain stream server.
     *
     * @param bindAddress the address to bind to
     * @param acceptListener the initial accept listener
     * @param optionMap the initial configuration for the server
     * @return the acceptor
     * @throws IOException if the server could not be created
     */
    protected AcceptingChannel<StreamConnection> createLocalStreamConnectionServer(LocalSocketAddress bindAddress, ChannelListener<? super AcceptingChannel<StreamConnection>> acceptListener, OptionMap optionMap) throws IOException {
        throw msg.unsupported("createLocalStreamConnectionServer");
    }

    // Connectors

    /**
     * Connect to a remote stream server.  The protocol family is determined by the type of the socket address given.
     *
     * @param destination the destination address
     * @param openListener the listener which will be notified when the channel is open, or {@code null} for none
     * @param optionMap the option map
     * @return the future result of this operation
     */
    @Deprecated
    public IoFuture<ConnectedStreamChannel> connectStream(SocketAddress destination, ChannelListener<? super ConnectedStreamChannel> openListener, OptionMap optionMap) {
        final FutureResult<ConnectedStreamChannel> futureResult = new FutureResult<ConnectedStreamChannel>();
        final ChannelListener<StreamConnection> nestedOpenListener = new StreamConnectionWrapListener(futureResult, openListener);
        final IoFuture<StreamConnection> future = openStreamConnection(destination, nestedOpenListener, optionMap);
        future.addNotifier(STREAM_WRAPPING_HANDLER, futureResult);
        futureResult.addCancelHandler(future);
        return futureResult.getIoFuture();
    }

    /**
     * Connect to a remote stream server.  The protocol family is determined by the type of the socket address given.
     *
     * @param destination the destination address
     * @param openListener the listener which will be notified when the channel is open, or {@code null} for none
     * @param bindListener the listener which will be notified when the channel is bound, or {@code null} for none
     * @param optionMap the option map
     * @return the future result of this operation
     */
    @Deprecated
    public IoFuture<ConnectedStreamChannel> connectStream(SocketAddress destination, ChannelListener<? super ConnectedStreamChannel> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        final FutureResult<ConnectedStreamChannel> futureResult = new FutureResult<ConnectedStreamChannel>();
        final ChannelListener<StreamConnection> nestedOpenListener = new StreamConnectionWrapListener(futureResult, openListener);
        final IoFuture<StreamConnection> future = openStreamConnection(destination, nestedOpenListener, bindListener, optionMap);
        future.addNotifier(STREAM_WRAPPING_HANDLER, futureResult);
        futureResult.addCancelHandler(future);
        return futureResult.getIoFuture();
    }

    /**
     * Connect to a remote stream server.  The protocol family is determined by the type of the socket addresses given
     * (which must match).
     *
     * @param bindAddress the local address to bind to
     * @param destination the destination address
     * @param openListener the listener which will be notified when the channel is open, or {@code null} for none
     * @param bindListener the listener which will be notified when the channel is bound, or {@code null} for none
     * @param optionMap the option map
     * @return the future result of this operation
     */
    @Deprecated
    public IoFuture<ConnectedStreamChannel> connectStream(SocketAddress bindAddress, SocketAddress destination, ChannelListener<? super ConnectedStreamChannel> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        final FutureResult<ConnectedStreamChannel> futureResult = new FutureResult<ConnectedStreamChannel>();
        final ChannelListener<StreamConnection> nestedOpenListener = new StreamConnectionWrapListener(futureResult, openListener);
        final IoFuture<StreamConnection> future = openStreamConnection(bindAddress, destination, nestedOpenListener, bindListener, optionMap);
        future.addNotifier(STREAM_WRAPPING_HANDLER, futureResult);
        futureResult.addCancelHandler(future);
        return futureResult.getIoFuture();
    }

    public IoFuture<StreamConnection> openStreamConnection(SocketAddress destination, ChannelListener<? super StreamConnection> openListener, OptionMap optionMap) {
        return chooseThread().openStreamConnection(destination, openListener, optionMap);
    }

    public IoFuture<StreamConnection> openStreamConnection(SocketAddress destination, ChannelListener<? super StreamConnection> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        return chooseThread().openStreamConnection(destination, openListener, bindListener, optionMap);
    }

    public IoFuture<StreamConnection> openStreamConnection(SocketAddress bindAddress, SocketAddress destination, ChannelListener<? super StreamConnection> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        return chooseThread().openStreamConnection(bindAddress, destination, openListener, bindListener, optionMap);
    }

    // Acceptors

    /**
     * Accept a stream connection at a destination address.  If a wildcard address is specified, then a destination address
     * is chosen in a manner specific to the OS and/or channel type.
     *
     * @param destination the destination (bind) address
     * @param openListener the listener which will be notified when the channel is open, or {@code null} for none
     * @param bindListener the listener which will be notified when the acceptor is bound, or {@code null} for none
     * @param optionMap the option map
     * @return the future connection
     */
    @Deprecated
    public IoFuture<ConnectedStreamChannel> acceptStream(SocketAddress destination, ChannelListener<? super ConnectedStreamChannel> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        final FutureResult<ConnectedStreamChannel> futureResult = new FutureResult<ConnectedStreamChannel>();
        final ChannelListener<StreamConnection> nestedOpenListener = new StreamConnectionWrapListener(futureResult, openListener);
        final IoFuture<StreamConnection> future = acceptStreamConnection(destination, nestedOpenListener, bindListener, optionMap);
        future.addNotifier(STREAM_WRAPPING_HANDLER, futureResult);
        futureResult.addCancelHandler(future);
        return futureResult.getIoFuture();
    }

    public IoFuture<StreamConnection> acceptStreamConnection(SocketAddress destination, ChannelListener<? super StreamConnection> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        return chooseThread().acceptStreamConnection(destination, openListener, bindListener, optionMap);
    }

    //==================================================
    //
    // Message (datagram) channel methods
    //
    //==================================================

    /**
     * Connect to a remote datagram server.  The protocol family is determined by the type of the socket address given.
     *
     * @param destination the destination address
     * @param openListener the listener which will be notified when the channel is open, or {@code null} for none
     * @param bindListener the listener which will be notified when the channel is bound, or {@code null} for none
     * @param optionMap the option map
     * @return the future result of this operation
     */
    @Deprecated
    // FIXME XNIO-192 invoke bind listener
    public IoFuture<ConnectedMessageChannel> connectDatagram(SocketAddress destination, ChannelListener<? super ConnectedMessageChannel> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        final FutureResult<ConnectedMessageChannel> futureResult = new FutureResult<ConnectedMessageChannel>();
        final ChannelListener<MessageConnection> nestedOpenListener = new MessageConnectionWrapListener(futureResult, openListener);
        final IoFuture<MessageConnection> future = openMessageConnection(destination, nestedOpenListener, optionMap);
        future.addNotifier(MESSAGE_WRAPPING_HANDLER, futureResult);
        futureResult.addCancelHandler(future);
        return futureResult.getIoFuture();
    }

    /**
     * Connect to a remote datagram server.  The protocol family is determined by the type of the socket addresses given
     * (which must match).
     *
     * @param bindAddress the local address to bind to
     * @param destination the destination address
     * @param openListener the listener which will be notified when the channel is open, or {@code null} for none
     * @param bindListener the listener which will be notified when the channel is bound, or {@code null} for none
     * @param optionMap the option map
     * @return the future result of this operation
     */
    @Deprecated
    // FIXME bindAddress is now ignored
    public IoFuture<ConnectedMessageChannel> connectDatagram(SocketAddress bindAddress, SocketAddress destination, ChannelListener<? super ConnectedMessageChannel> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        final FutureResult<ConnectedMessageChannel> futureResult = new FutureResult<ConnectedMessageChannel>();
        final ChannelListener<MessageConnection> nestedOpenListener = new MessageConnectionWrapListener(futureResult, openListener);
        final IoFuture<MessageConnection> future = openMessageConnection(destination, nestedOpenListener, optionMap);
        future.addNotifier(MESSAGE_WRAPPING_HANDLER, futureResult);
        futureResult.addCancelHandler(future);
        return futureResult.getIoFuture();
    }

    public IoFuture<MessageConnection> openMessageConnection(final SocketAddress destination, final ChannelListener<? super MessageConnection> openListener, final OptionMap optionMap) {
        return chooseThread().openMessageConnection(destination, openListener, optionMap);
    }

    // Acceptors

    /**
     * Accept a message connection at a destination address.  If a wildcard address is specified, then a destination address
     * is chosen in a manner specific to the OS and/or channel type.
     *
     * @param destination the destination (bind) address
     * @param openListener the listener which will be notified when the channel is open, or {@code null} for none
     * @param bindListener the listener which will be notified when the acceptor is bound, or {@code null} for none
     * @param optionMap the option map
     * @return the future connection
     */
    @Deprecated
    public IoFuture<ConnectedMessageChannel> acceptDatagram(SocketAddress destination, ChannelListener<? super ConnectedMessageChannel> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        final FutureResult<ConnectedMessageChannel> futureResult = new FutureResult<ConnectedMessageChannel>();
        final ChannelListener<MessageConnection> nestedOpenListener = new MessageConnectionWrapListener(futureResult, openListener);
        final IoFuture<MessageConnection> future = acceptMessageConnection(destination, nestedOpenListener, bindListener, optionMap);
        future.addNotifier(MESSAGE_WRAPPING_HANDLER, futureResult);
        futureResult.addCancelHandler(future);
        return futureResult.getIoFuture();
    }

    public IoFuture<MessageConnection> acceptMessageConnection(final SocketAddress destination, final ChannelListener<? super MessageConnection> openListener, final ChannelListener<? super BoundChannel> bindListener, final OptionMap optionMap) {
        return chooseThread().acceptMessageConnection(destination, openListener, bindListener, optionMap);
    }

    //==================================================
    //
    // UDP methods
    //
    //==================================================

    /**
     * Create a UDP server.  The UDP server can be configured to be multicast-capable; this should only be
     * done if multicast is needed, since some providers have a performance penalty associated with multicast.
     * The provider's default executor will be used to execute listener methods.
     *
     * @param bindAddress the bind address
     * @param bindListener the initial open-connection listener
     * @param optionMap the initial configuration for the server
     * @return the UDP server channel
     * @throws java.io.IOException if the server could not be created
     *
     * @since 3.0
     */
    public MulticastMessageChannel createUdpServer(InetSocketAddress bindAddress, ChannelListener<? super MulticastMessageChannel> bindListener, OptionMap optionMap) throws IOException {
        throw msg.unsupported("createUdpServer");
    }

    /**
     * Create a UDP server.  The UDP server can be configured to be multicast-capable; this should only be
     * done if multicast is needed, since some providers have a performance penalty associated with multicast.
     * The provider's default executor will be used to execute listener methods.
     *
     * @param bindAddress the bind address
     * @param optionMap the initial configuration for the server
     * @return the UDP server channel
     * @throws java.io.IOException if the server could not be created
     *
     * @since 3.0
     */
    public MulticastMessageChannel createUdpServer(InetSocketAddress bindAddress, OptionMap optionMap) throws IOException {
        return createUdpServer(bindAddress, ChannelListeners.nullChannelListener(), optionMap);
    }

    //==================================================
    //
    // Stream pipe methods
    //
    //==================================================

    /**
     * Open a bidirectional stream pipe.
     *
     * @param leftOpenListener the left-hand open listener
     * @param rightOpenListener the right-hand open listener
     * @param optionMap the pipe channel configuration
     * @throws java.io.IOException if the pipe could not be created
     * @deprecated Users should prefer the simpler {@link #createFullDuplexPipe()} instead.
     */
    @Deprecated
    public void createPipe(ChannelListener<? super StreamChannel> leftOpenListener, ChannelListener<? super StreamChannel> rightOpenListener, final OptionMap optionMap) throws IOException {
        final ChannelPipe<StreamChannel, StreamChannel> pipe = createFullDuplexPipe();
        final boolean establishWriting = optionMap.get(Options.WORKER_ESTABLISH_WRITING, false);
        final StreamChannel left = pipe.getLeftSide();
        XnioExecutor leftExec = establishWriting ? left.getWriteThread() : left.getReadThread();
        final StreamChannel right = pipe.getRightSide();
        XnioExecutor rightExec = establishWriting ? right.getWriteThread() : right.getReadThread();
        // not unsafe - http://youtrack.jetbrains.net/issue/IDEA-59290
        //noinspection unchecked
        leftExec.execute(ChannelListeners.getChannelListenerTask(left, leftOpenListener));
        // not unsafe - http://youtrack.jetbrains.net/issue/IDEA-59290
        //noinspection unchecked
        rightExec.execute(ChannelListeners.getChannelListenerTask(right, rightOpenListener));
    }

    /**
     * Open a unidirectional stream pipe.
     *
     * @param sourceListener the source open listener
     * @param sinkListener the sink open listener
     * @param optionMap the pipe channel configuration
     * @throws java.io.IOException if the pipe could not be created
     * @deprecated Users should prefer the simpler {@link #createHalfDuplexPipe()} instead.
     */
    @Deprecated
    public void createOneWayPipe(ChannelListener<? super StreamSourceChannel> sourceListener, ChannelListener<? super StreamSinkChannel> sinkListener, final OptionMap optionMap) throws IOException {
        final ChannelPipe<StreamSourceChannel, StreamSinkChannel> pipe = createHalfDuplexPipe();
        final StreamSourceChannel left = pipe.getLeftSide();
        XnioExecutor leftExec = left.getReadThread();
        final StreamSinkChannel right = pipe.getRightSide();
        XnioExecutor rightExec = right.getWriteThread();
        // not unsafe - http://youtrack.jetbrains.net/issue/IDEA-59290
        //noinspection unchecked
        leftExec.execute(ChannelListeners.getChannelListenerTask(left, sourceListener));
        // not unsafe - http://youtrack.jetbrains.net/issue/IDEA-59290
        //noinspection unchecked
        rightExec.execute(ChannelListeners.getChannelListenerTask(right, sinkListener));
    }

    //==================================================
    //
    // Compression methods
    //
    //==================================================

    /**
     * Create a stream channel that decompresses the source data according to the configuration in the given option map.
     *
     * @param delegate the compressed channel
     * @param options the configuration options for the channel
     * @return a decompressed channel
     * @throws IOException if the channel could not be constructed
     */
    public StreamSourceChannel getInflatingChannel(final StreamSourceChannel delegate, OptionMap options) throws IOException {
        final boolean nowrap;
        switch (options.get(Options.COMPRESSION_TYPE, CompressionType.DEFLATE)) {
            case DEFLATE: nowrap = false; break;
            case GZIP: nowrap = true; break;
            default: throw msg.badCompressionFormat();
        }
        return getInflatingChannel(delegate, new Inflater(nowrap));
    }

    /**
     * Create a stream channel that decompresses the source data according to the configuration in the given inflater.
     *
     * @param delegate the compressed channel
     * @param inflater the inflater to use
     * @return a decompressed channel
     * @throws IOException if the channel could not be constructed
     */
    protected StreamSourceChannel getInflatingChannel(final StreamSourceChannel delegate, final Inflater inflater) throws IOException {
        return new ConduitStreamSourceChannel(Configurable.EMPTY, new InflatingStreamSourceConduit(new StreamSourceChannelWrappingConduit(delegate), inflater));
    }

    /**
     * Create a stream channel that compresses to the destination according to the configuration in the given option map.
     *
     * @param delegate the channel to compress to
     * @param options the configuration options for the channel
     * @return a compressed channel
     * @throws IOException if the channel could not be constructed
     */
    public StreamSinkChannel getDeflatingChannel(final StreamSinkChannel delegate, final OptionMap options) throws IOException {
        final int level = options.get(Options.COMPRESSION_LEVEL, -1);
        final boolean nowrap;
        switch (options.get(Options.COMPRESSION_TYPE, CompressionType.DEFLATE)) {
            case DEFLATE: nowrap = false; break;
            case GZIP: nowrap = true; break;
            default: throw msg.badCompressionFormat();
        }
        return getDeflatingChannel(delegate, new Deflater(level, nowrap));
    }

    /**
     * Create a stream channel that compresses to the destination according to the configuration in the given inflater.
     *
     * @param delegate the channel to compress to
     * @param deflater the deflater to use
     * @return a compressed channel
     * @throws IOException if the channel could not be constructed
     */
    protected StreamSinkChannel getDeflatingChannel(final StreamSinkChannel delegate, final Deflater deflater) throws IOException {
        return new ConduitStreamSinkChannel(Configurable.EMPTY, new DeflatingStreamSinkConduit(new StreamSinkChannelWrappingConduit(delegate), deflater));
    }

    public ChannelPipe<StreamChannel, StreamChannel> createFullDuplexPipe() throws IOException {
        return chooseThread().createFullDuplexPipe();
    }

    public ChannelPipe<StreamConnection, StreamConnection> createFullDuplexPipeConnection() throws IOException {
        return chooseThread().createFullDuplexPipeConnection();
    }

    public ChannelPipe<StreamSourceChannel, StreamSinkChannel> createHalfDuplexPipe() throws IOException {
        return chooseThread().createHalfDuplexPipe();
    }

    public ChannelPipe<StreamConnection, StreamConnection> createFullDuplexPipeConnection(final XnioIoFactory peer) throws IOException {
        return chooseThread().createFullDuplexPipeConnection(peer);
    }

    public ChannelPipe<StreamSourceChannel, StreamSinkChannel> createHalfDuplexPipe(final XnioIoFactory peer) throws IOException {
        return chooseThread().createHalfDuplexPipe(peer);
    }

    //==================================================
    //
    // State methods
    //
    //==================================================

    /**
     * Shut down this worker.  This method returns immediately.  Upon return worker shutdown will have
     * commenced but not necessarily completed.  When worker shutdown is complete, the termination task (if one was
     * defined) will be executed.
     */
    public abstract void shutdown();

    /**
     * Immediately terminate the worker.  Any outstanding tasks are collected and returned in a list.  Upon return
     * worker shutdown will have commenced but not necessarily completed; however the worker will only complete its
     * current tasks instead of completing all tasks.
     *
     * @return the list of outstanding tasks
     */
    public abstract List<Runnable> shutdownNow();

    /**
     * Determine whether the worker has been shut down.  Will return {@code true} once either shutdown method has
     * been called.
     *
     * @return {@code true} the worker has been shut down
     */
    public abstract boolean isShutdown();

    /**
     * Determine whether the worker has terminated.  Will return {@code true} once all worker threads are exited
     * (with the possible exception of the thread running the termination task, if any).
     *
     * @return {@code true} if the worker is terminated
     */
    public abstract boolean isTerminated();

    /**
     * Wait for termination.
     *
     * @param timeout the amount of time to wait
     * @param unit the unit of time
     * @return {@code true} if termination completed before the timeout expired
     * @throws InterruptedException if the operation was interrupted
     */
    public abstract boolean awaitTermination(final long timeout, final TimeUnit unit) throws InterruptedException;

    /**
     * Wait for termination.
     *
     * @throws InterruptedException if the operation was interrupted
     */
    public abstract void awaitTermination() throws InterruptedException;

    //==================================================
    //
    // Thread pool methods
    //
    //==================================================

    /**
     * Get an I/O thread from this worker.  The thread may be chosen based on arbitrary rules.
     *
     * @return the I/O thread
     */
    public final XnioIoThread getIoThread() {
        return chooseThread();
    }

    /**
     * Get the user task to run once termination is complete.
     *
     * @return the termination task
     */
    protected Runnable getTerminationTask() {
        return terminationTask;
    }

    /**
     * Callback to indicate that the task thread pool has terminated.
     */
    protected void taskPoolTerminated() {}

    /**
     * Initiate shutdown of the task thread pool.  When all the tasks and threads have completed,
     * the {@link #taskPoolTerminated()} method is called.
     */
    protected void shutDownTaskPool() {
        taskPool.shutdown();
    }

    /**
     * Shut down the task thread pool immediately and return its pending tasks.
     *
     * @return the pending task list
     */
    protected List<Runnable> shutDownTaskPoolNow() {
        return taskPool.shutdownNow();
    }

    /**
     * Execute a command in the task pool.
     *
     * @param command the command to run
     */
    public void execute(final Runnable command) {
        taskPool.execute(command);
    }

    /**
     * Get the number of I/O threads configured on this worker.
     *
     * @return the number of I/O threads configured on this worker
     */
    public abstract int getIoThreadCount();

    //==================================================
    //
    // Configuration methods
    //
    //==================================================

    private static Set<Option<?>> OPTIONS = Option.setBuilder()
            .add(Options.WORKER_TASK_CORE_THREADS)
            .add(Options.WORKER_TASK_MAX_THREADS)
            .add(Options.WORKER_TASK_KEEPALIVE)
            .create();

    public boolean supportsOption(final Option<?> option) {
        return OPTIONS.contains(option);
    }

    public <T> T getOption(final Option<T> option) throws IOException {
        if (option.equals(Options.WORKER_TASK_CORE_THREADS)) {
            return option.cast(Integer.valueOf(coreSize));
        } else if (option.equals(Options.WORKER_TASK_MAX_THREADS)) {
            return option.cast(Integer.valueOf(taskPool.getMaximumPoolSize()));
        } else if (option.equals(Options.WORKER_TASK_KEEPALIVE)) {
            return option.cast(Integer.valueOf((int) Math.min((long) Integer.MAX_VALUE, taskPool.getKeepAliveTime(TimeUnit.MILLISECONDS))));
        } else {
            return null;
        }
    }

    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        if (option.equals(Options.WORKER_TASK_CORE_THREADS)) {
            return option.cast(Integer.valueOf(coreSizeUpdater.getAndSet(this, Options.WORKER_TASK_CORE_THREADS.cast(value).intValue())));
        } else if (option.equals(Options.WORKER_TASK_MAX_THREADS)) {
            final int old = taskPool.getMaximumPoolSize();
            taskPool.setCorePoolSize(Options.WORKER_TASK_MAX_THREADS.cast(value).intValue());
            taskPool.setMaximumPoolSize(Options.WORKER_TASK_MAX_THREADS.cast(value).intValue());
            return option.cast(Integer.valueOf(old));
        } else if (option.equals(Options.WORKER_TASK_KEEPALIVE)) {
            final long old = taskPool.getKeepAliveTime(TimeUnit.MILLISECONDS);
            taskPool.setKeepAliveTime(Options.WORKER_TASK_KEEPALIVE.cast(value).intValue(), TimeUnit.MILLISECONDS);
            return option.cast(Integer.valueOf((int) Math.min((long) Integer.MAX_VALUE, old)));
        } else {
            return null;
        }
    }

    //==================================================
    //
    // Accessor methods
    //
    //==================================================

    /**
     * Get the XNIO provider which produced this worker.
     *
     * @return the XNIO provider
     */
    public Xnio getXnio() {
        return xnio;
    }

    /**
     * Get the name of this worker.
     *
     * @return the name of the worker
     */
    public String getName() {
        return name;
    }

    //==================================================
    //
    // SPI methods
    //
    //==================================================

    /**
     * Choose a thread randomly from this worker.
     *
     * @return the thread
     */
    protected abstract XnioIoThread chooseThread();

    /**
     * Get the core worker pool size.
     *
     * @return the core worker pool size
     */
    protected final int getCoreWorkerPoolSize() {
        return coreSize;
    }

    /**
     * Get the maximum worker pool size.
     *
     * @return the maximum worker pool size
     */
    protected final int getMaxWorkerPoolSize() {
        return taskPool.getMaximumPoolSize();
    }

    final class TaskPool extends ThreadPoolExecutor {

        TaskPool(final int corePoolSize, final int maximumPoolSize, final long keepAliveTime, final TimeUnit unit, final BlockingQueue<Runnable> workQueue, final ThreadFactory threadFactory, final RejectedExecutionHandler handler) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
        }

        protected void terminated() {
            taskPoolTerminated();
        }
    }

    static class StreamConnectionWrapListener implements ChannelListener<StreamConnection> {

        private final FutureResult<ConnectedStreamChannel> futureResult;
        private final ChannelListener<? super ConnectedStreamChannel> openListener;

        public StreamConnectionWrapListener(final FutureResult<ConnectedStreamChannel> futureResult, final ChannelListener<? super ConnectedStreamChannel> openListener) {
            this.futureResult = futureResult;
            this.openListener = openListener;
        }

        public void handleEvent(final StreamConnection channel) {
            final AssembledConnectedStreamChannel assembledChannel = new AssembledConnectedStreamChannel(channel, channel.getSourceChannel(), channel.getSinkChannel());
            if (!futureResult.setResult(assembledChannel)) {
                safeClose(assembledChannel);
            } else {
                ChannelListeners.invokeChannelListener(assembledChannel, openListener);
            }
        }
    }

    static class MessageConnectionWrapListener implements ChannelListener<MessageConnection> {

        private final FutureResult<ConnectedMessageChannel> futureResult;
        private final ChannelListener<? super ConnectedMessageChannel> openListener;

        public MessageConnectionWrapListener(final FutureResult<ConnectedMessageChannel> futureResult, final ChannelListener<? super ConnectedMessageChannel> openListener) {
            this.futureResult = futureResult;
            this.openListener = openListener;
        }

        public void handleEvent(final MessageConnection channel) {
            final AssembledConnectedMessageChannel assembledChannel = new AssembledConnectedMessageChannel(channel, channel.getSourceChannel(), channel.getSinkChannel());
            if (!futureResult.setResult(assembledChannel)) {
                safeClose(assembledChannel);
            } else {
                ChannelListeners.invokeChannelListener(assembledChannel, openListener);
            }
        }
    }

    private static final IoFuture.HandlingNotifier<StreamConnection, FutureResult<ConnectedStreamChannel>> STREAM_WRAPPING_HANDLER = new IoFuture.HandlingNotifier<StreamConnection, FutureResult<ConnectedStreamChannel>>() {
        public void handleCancelled(final FutureResult<ConnectedStreamChannel> attachment) {
            attachment.setCancelled();
        }

        public void handleFailed(final IOException exception, final FutureResult<ConnectedStreamChannel> attachment) {
            attachment.setException(exception);
        }
    };

    private static final IoFuture.HandlingNotifier<MessageConnection, FutureResult<ConnectedMessageChannel>> MESSAGE_WRAPPING_HANDLER = new IoFuture.HandlingNotifier<MessageConnection, FutureResult<ConnectedMessageChannel>>() {
        public void handleCancelled(final FutureResult<ConnectedMessageChannel> attachment) {
            attachment.setCancelled();
        }

        public void handleFailed(final IOException exception, final FutureResult<ConnectedMessageChannel> attachment) {
            attachment.setException(exception);
        }
    };

    class WorkerThreadFactory implements ThreadFactory {

        private final ThreadGroup threadGroup;
        private final OptionMap optionMap;
        private final boolean markThreadAsDaemon;

        WorkerThreadFactory(final ThreadGroup threadGroup, final OptionMap optionMap, final boolean markThreadAsDaemon) {
            this.threadGroup = threadGroup;
            this.optionMap = optionMap;
            this.markThreadAsDaemon = markThreadAsDaemon;
        }

        public Thread newThread(final Runnable r) {
            return doPrivileged(new PrivilegedAction<Thread>() {
                public Thread run() {
                    final Thread taskThread = new Thread(threadGroup, r, name + " task-" + getNextSeq(), optionMap.get(Options.STACK_SIZE, 0L));
                    // Mark the thread as daemon if the Options.THREAD_DAEMON has been set
                    if (markThreadAsDaemon) {
                        taskThread.setDaemon(true);
                    }
                    return taskThread;
                }
            });
        }
    }
}

