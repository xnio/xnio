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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import org.jboss.threads.EnhancedQueueExecutor;
import org.wildfly.common.Assert;
import org.wildfly.common.context.ContextManager;
import org.wildfly.common.context.Contextual;
import org.wildfly.common.net.CidrAddress;
import org.wildfly.common.net.CidrAddressTable;
import org.xnio._private.Messages;
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
import org.xnio.management.XnioServerMXBean;
import org.xnio.management.XnioWorkerMXBean;

import static java.lang.Math.max;
import static java.security.AccessController.doPrivileged;
import org.jboss.logging.Logger;
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
public abstract class XnioWorker extends AbstractExecutorService implements Configurable, ExecutorService, XnioIoFactory, Contextual<XnioWorker> {

    private final Xnio xnio;
    private final TaskPool taskPool;
    private final String name;
    private final Runnable terminationTask;
    private final CidrAddressTable<InetSocketAddress> bindAddressTable;

    private volatile int taskSeq;

    private static final AtomicIntegerFieldUpdater<XnioWorker> taskSeqUpdater = AtomicIntegerFieldUpdater.newUpdater(XnioWorker.class, "taskSeq");

    private static final AtomicInteger seq = new AtomicInteger(1);

    private static final RuntimePermission CREATE_WORKER_PERMISSION = new RuntimePermission("createXnioWorker");

    private int getNextSeq() {
        return taskSeqUpdater.incrementAndGet(this);
    }

    private static final Logger log = Logger.getLogger("org.xnio");

    /**
     * Construct a new instance.  Intended to be called only from implementations.
     *
     * @param builder the worker builder
     */
    protected XnioWorker(final Builder builder) {
        this.xnio = builder.xnio;
        this.terminationTask = builder.terminationTask;
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(CREATE_WORKER_PERMISSION);
        }
        String workerName = builder.getWorkerName();
        if (workerName == null) {
            workerName = "XNIO-" + seq.getAndIncrement();
        }
        name = workerName;
        final boolean markThreadAsDaemon = builder.isDaemon();
        bindAddressTable = builder.getBindAddressConfigurations();
        final Runnable terminationTask = new Runnable() {
            public void run() {
                taskPoolTerminated();
            }
        };
        final ExecutorService executorService = builder.getExternalExecutorService();
        if (executorService != null) {
            if (executorService instanceof EnhancedQueueExecutor) {
                taskPool = new ExternalTaskPool(
                        new EnhancedQueueExecutorTaskPool((EnhancedQueueExecutor) executorService));
            } else if (executorService instanceof ThreadPoolExecutor) {
                taskPool = new ExternalTaskPool(new ThreadPoolExecutorTaskPool((ThreadPoolExecutor) executorService));
            } else {
                taskPool = new ExternalTaskPool(new ExecutorServiceTaskPool(executorService));
            }
        } else if (EnhancedQueueExecutor.DISABLE_HINT) {
            final int poolSize = max(builder.getMaxWorkerPoolSize(), builder.getCoreWorkerPoolSize());
            taskPool = new ThreadPoolExecutorTaskPool(new DefaultThreadPoolExecutor(
                poolSize,
                poolSize,
                builder.getWorkerKeepAlive(), TimeUnit.MILLISECONDS,
                new LinkedBlockingDeque<>(),
                new WorkerThreadFactory(builder.getThreadGroup(), builder.getWorkerStackSize(), markThreadAsDaemon),
                terminationTask));
        } else {
            taskPool = new EnhancedQueueExecutorTaskPool(new EnhancedQueueExecutor.Builder()
                .setCorePoolSize(builder.getCoreWorkerPoolSize())
                .setMaximumPoolSize(builder.getMaxWorkerPoolSize())
                .setKeepAliveTime(builder.getWorkerKeepAlive(), TimeUnit.MILLISECONDS)
                .setThreadFactory(new WorkerThreadFactory(builder.getThreadGroup(), builder.getWorkerStackSize(), markThreadAsDaemon))
                .setTerminationTask(terminationTask)
                .setRegisterMBean(true)
                .setMBeanName(workerName)
                .build()
            );
        }
    }

    //==================================================
    //
    // Context methods
    //
    //==================================================

    private static final ContextManager<XnioWorker> CONTEXT_MANAGER = doPrivileged((PrivilegedAction<ContextManager<XnioWorker>>) () -> new ContextManager<XnioWorker>(XnioWorker.class, "org.xnio.worker"));

    static {
        doPrivileged((PrivilegedAction<Void>) () -> {
            CONTEXT_MANAGER.setGlobalDefaultSupplier(() -> DefaultXnioWorkerHolder.INSTANCE);
            return null;
        });
    }

    /**
     * Get the context manager for XNIO workers.
     *
     * @return the context manager (not {@code null})
     */
    public static ContextManager<XnioWorker> getContextManager() {
        return CONTEXT_MANAGER;
    }

    /**
     * Get the instance context manager for XNIO workers by delegating to {@link #getContextManager()}.
     *
     * @return the context manager (not {@code null})
     */
    public ContextManager<XnioWorker> getInstanceContextManager() {
        return getContextManager();
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
                return server.getIoThread();
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
        Assert.checkNotNullParam("bindAddress", bindAddress);
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
     * Get an I/O thread from this worker.  The thread is chosen based on the given hash code.
     *
     * @param hashCode the hash code
     * @return the thread
     */
    public abstract XnioIoThread getIoThread(int hashCode);

    /**
     * Get the user task to run once termination is complete.
     *
     * @return the termination task
     */
    protected Runnable getTerminationTask() {
        return terminationTask;
    }

    /**
     * Callback to indicate that the task thread pool has terminated.  Not called if the task pool is external.
     */
    protected void taskPoolTerminated() {}

    /**
     * Initiate shutdown of the task thread pool.  When all the tasks and threads have completed,
     * the {@link #taskPoolTerminated()} method is called.
     */
    protected void shutDownTaskPool() {
        if (isTaskPoolExternal()) {
            taskPoolTerminated();
        } else {
            doPrivileged(new PrivilegedAction<Object>() {
                public Object run() {
                    taskPool.shutdown();
                    return null;
                }
            });
        }
    }

    /**
     * Shut down the task thread pool immediately and return its pending tasks.
     *
     * @return the pending task list
     */
    protected List<Runnable> shutDownTaskPoolNow() {
        if (! isTaskPoolExternal()) return doPrivileged(new PrivilegedAction<List<Runnable>>() {
            public List<Runnable> run() {
                return taskPool.shutdownNow();
            }
        });
        return Collections.emptyList();
    }

    /**
     * Determine whether the worker task pool is managed externally.  Externally managed task pools will never
     * respond to shut down requests.
     *
     * @return {@code true} if the task pool is externally managed, {@code false} otherwise
     */
    protected boolean isTaskPoolExternal() {
        return taskPool instanceof ExternalTaskPool;
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

    private final static Set<Option<?>> OPTIONS = Option.setBuilder()
            .add(Options.WORKER_TASK_CORE_THREADS)
            .add(Options.WORKER_TASK_MAX_THREADS)
            .add(Options.WORKER_TASK_KEEPALIVE)
            .create();

    private final static Set<Option<?>> EXTERNAL_POOL_OPTIONS = Option.setBuilder()
            .create();

    public boolean supportsOption(final Option<?> option) {
        return taskPool instanceof ExternalTaskPool ? EXTERNAL_POOL_OPTIONS.contains(option) : OPTIONS.contains(option);
    }

    public <T> T getOption(final Option<T> option) throws IOException {
        if (! supportsOption(option)) {
            return null;
        } else if (option.equals(Options.WORKER_TASK_CORE_THREADS)) {
            return option.cast(Integer.valueOf(taskPool.getCorePoolSize()));
        } else if (option.equals(Options.WORKER_TASK_MAX_THREADS)) {
            return option.cast(Integer.valueOf(taskPool.getMaximumPoolSize()));
        } else if (option.equals(Options.WORKER_TASK_KEEPALIVE)) {
            return option.cast(Integer.valueOf((int) Math.min((long) Integer.MAX_VALUE, taskPool.getKeepAliveTime(TimeUnit.MILLISECONDS))));
        } else {
            return null;
        }
    }

    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        if (! supportsOption(option)) {
            return null;
        } else if (option.equals(Options.WORKER_TASK_CORE_THREADS)) {
            final int old = taskPool.getCorePoolSize();
            taskPool.setCorePoolSize(Options.WORKER_TASK_CORE_THREADS.cast(value).intValue());
            return option.cast(Integer.valueOf(old));
        } else if (option.equals(Options.WORKER_TASK_MAX_THREADS)) {
            final int old = taskPool.getMaximumPoolSize();
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
        return taskPool.getCorePoolSize();
    }

    /**
     * Get an estimate of the number of busy threads in the worker pool.
     *
     * @return the estimated number of busy threads in the worker pool
     */
    protected final int getBusyWorkerThreadCount() {
        return taskPool.getActiveCount();
    }

    /**
     * Get an estimate of the number of threads in the worker pool.
     *
     * @return the estimated number of threads in the worker pool
     */
    protected final int getWorkerPoolSize() {
        return taskPool.getPoolSize();
    }

    /**
     * Get the maximum worker pool size.
     *
     * @return the maximum worker pool size
     */
    protected final int getMaxWorkerPoolSize() {
        return taskPool.getMaximumPoolSize();
    }

    /**
     * Get an estimate of the number of tasks in the worker queue.
     *
     * @return the estimated number of tasks
     */
    protected final int getWorkerQueueSize() {
        return taskPool.getQueueSize();
    }

    //==================================================
    //
    // Source address
    //
    //==================================================

    /**
     * Get the bind address table.
     *
     * @return the bind address table
     */
    protected CidrAddressTable<InetSocketAddress> getBindAddressTable() {
        return bindAddressTable;
    }

    /**
     * Get the expected bind address for the given destination, if any.
     *
     * @return the expected bind address for the given destination, or {@code null} if no explicit bind will be done
     */
    public InetSocketAddress getBindAddress(InetAddress destination) {
        return bindAddressTable.get(destination);
    }

    //==================================================
    //
    // JMX
    //
    //==================================================

    public abstract XnioWorkerMXBean getMXBean();

    protected abstract ManagementRegistration registerServerMXBean(XnioServerMXBean metrics);

    //==================================================
    //
    // Builder
    //
    //==================================================

    /**
     * A builder which allows workers to be programmatically configured.
     */
    public static class Builder {
        private final Xnio xnio;
        private ExecutorService externalExecutorService;
        private Runnable terminationTask;
        private String workerName;
        private int coreWorkerPoolSize = 4;
        private int maxWorkerPoolSize = 16;
        private ThreadGroup threadGroup;
        private boolean daemon;
        private int workerKeepAlive = 60_000;
        private int workerIoThreads = 1;
        private long workerStackSize = 0L;
        private CidrAddressTable<InetSocketAddress> bindAddressConfigurations = new CidrAddressTable<>();

        /**
         * Construct a new instance.
         *
         * @param xnio the XNIO instance (must not be {@code null})
         */
        protected Builder(final Xnio xnio) {
            this.xnio = xnio;
        }

        public Xnio getXnio() {
            return xnio;
        }

        public Builder populateFromOptions(OptionMap optionMap) {
            setWorkerName(optionMap.get(Options.WORKER_NAME));
            setCoreWorkerPoolSize(optionMap.get(Options.WORKER_TASK_CORE_THREADS, coreWorkerPoolSize));
            setMaxWorkerPoolSize(optionMap.get(Options.WORKER_TASK_MAX_THREADS, maxWorkerPoolSize));
            setDaemon(optionMap.get(Options.THREAD_DAEMON, daemon));
            setWorkerKeepAlive(optionMap.get(Options.WORKER_TASK_KEEPALIVE, workerKeepAlive));
            if (optionMap.contains(Options.WORKER_IO_THREADS)) {
                setWorkerIoThreads(optionMap.get(Options.WORKER_IO_THREADS, 1));
            } else if (optionMap.contains(Options.WORKER_READ_THREADS) || optionMap.contains(Options.WORKER_WRITE_THREADS)) {
                setWorkerIoThreads(max(optionMap.get(Options.WORKER_READ_THREADS, 1), optionMap.get(Options.WORKER_WRITE_THREADS, 1)));
            }
            setWorkerStackSize(optionMap.get(Options.STACK_SIZE, workerStackSize));
            return this;
        }

        public Builder addBindAddressConfiguration(CidrAddress cidrAddress, InetAddress bindAddress) {
            return addBindAddressConfiguration(cidrAddress, new InetSocketAddress(bindAddress, 0));
        }

        public Builder addBindAddressConfiguration(CidrAddress cidrAddress, InetSocketAddress bindAddress) {
            final Class<? extends InetAddress> networkAddrClass = cidrAddress.getNetworkAddress().getClass();
            if (bindAddress.isUnresolved()) {
                throw Messages.msg.addressUnresolved(bindAddress);
            }
            if (networkAddrClass != bindAddress.getAddress().getClass()) {
                throw Messages.msg.mismatchAddressType(networkAddrClass, bindAddress.getAddress().getClass());
            }
            bindAddressConfigurations.put(cidrAddress, bindAddress);
            return this;
        }

        public Builder setBindAddressConfigurations(CidrAddressTable<InetSocketAddress> newTable) {
            bindAddressConfigurations = newTable;
            return this;
        }

        public CidrAddressTable<InetSocketAddress> getBindAddressConfigurations() {
            return bindAddressConfigurations;
        }

        public Runnable getTerminationTask() {
            return terminationTask;
        }

        public Builder setTerminationTask(final Runnable terminationTask) {
            this.terminationTask = terminationTask;
            return this;
        }

        public String getWorkerName() {
            return workerName;
        }

        public Builder setWorkerName(final String workerName) {
            this.workerName = workerName;
            return this;
        }

        public int getCoreWorkerPoolSize() {
            return coreWorkerPoolSize;
        }

        public Builder setCoreWorkerPoolSize(final int coreWorkerPoolSize) {
            Assert.checkMinimumParameter("coreWorkerPoolSize", 0, coreWorkerPoolSize);
            this.coreWorkerPoolSize = coreWorkerPoolSize;
            return this;
        }

        public int getMaxWorkerPoolSize() {
            return maxWorkerPoolSize;
        }

        public Builder setMaxWorkerPoolSize(final int maxWorkerPoolSize) {
            Assert.checkMinimumParameter("maxWorkerPoolSize", 0, maxWorkerPoolSize);
            this.maxWorkerPoolSize = maxWorkerPoolSize;
            return this;
        }

        public ThreadGroup getThreadGroup() {
            return threadGroup;
        }

        public Builder setThreadGroup(final ThreadGroup threadGroup) {
            this.threadGroup = threadGroup;
            return this;
        }

        public boolean isDaemon() {
            return daemon;
        }

        public Builder setDaemon(final boolean daemon) {
            this.daemon = daemon;
            return this;
        }

        public long getWorkerKeepAlive() {
            return workerKeepAlive;
        }

        public Builder setWorkerKeepAlive(final int workerKeepAlive) {
            Assert.checkMinimumParameter("workerKeepAlive", 0, workerKeepAlive);
            this.workerKeepAlive = workerKeepAlive;
            return this;
        }

        public int getWorkerIoThreads() {
            return workerIoThreads;
        }

        public Builder setWorkerIoThreads(final int workerIoThreads) {
            Assert.checkMinimumParameter("workerIoThreads", 0, workerIoThreads);
            this.workerIoThreads = workerIoThreads;
            return this;
        }

        public long getWorkerStackSize() {
            return workerStackSize;
        }

        public Builder setWorkerStackSize(final long workerStackSize) {
            Assert.checkMinimumParameter("workerStackSize", 0, workerStackSize);
            this.workerStackSize = workerStackSize;
            return this;
        }

        public ExecutorService getExternalExecutorService() {
            return externalExecutorService;
        }

        public Builder setExternalExecutorService(final ExecutorService executorService) {
            this.externalExecutorService = executorService;
            return this;
        }

        public XnioWorker build() {
            return xnio.build(this);
        }
    }

    //==================================================
    //
    // Private
    //
    //==================================================

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
        private final long stackSize;
        private final boolean markThreadAsDaemon;

        WorkerThreadFactory(final ThreadGroup threadGroup, final long stackSize, final boolean markThreadAsDaemon) {
            this.threadGroup = threadGroup;
            this.stackSize = stackSize;
            this.markThreadAsDaemon = markThreadAsDaemon;
        }

        public Thread newThread(final Runnable r) {
            return doPrivileged(new PrivilegedAction<Thread>() {
                public Thread run() {
                    final Thread taskThread = new Thread(threadGroup, new Runnable() {
                        @Override
                        public void run() {
                            try {
                                r.run();
                            } finally {
                                xnio.handleThreadExit();
                            }
                        }
                    }, name + " task-" + getNextSeq(), stackSize);
                    // Mark the thread as daemon if the Options.THREAD_DAEMON has been set
                    if (markThreadAsDaemon) {
                        taskThread.setDaemon(true);
                    }
                    return taskThread;
                }
            });
        }
    }

    interface TaskPool {

        void shutdown();

        List<Runnable> shutdownNow();

        void execute(Runnable command);

        int getCorePoolSize();

        int getMaximumPoolSize();

        long getKeepAliveTime(TimeUnit unit);

        void setCorePoolSize(int size);

        void setMaximumPoolSize(int size);

        void setKeepAliveTime(long time, TimeUnit unit);

        int getActiveCount();

        int getPoolSize();

        int getQueueSize();
    }

    static class DefaultThreadPoolExecutor extends ThreadPoolExecutor {
        private final Runnable terminationTask;

        DefaultThreadPoolExecutor(final int corePoolSize, final int maximumPoolSize, final long keepAliveTime, final TimeUnit unit, final BlockingQueue<Runnable> workQueue, final ThreadFactory threadFactory, final Runnable terminationTask) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
            this.terminationTask = terminationTask;
        }

        protected void terminated() {
            terminationTask.run();
        }

        public void setCorePoolSize(final int size) {
            setMaximumPoolSize(size);
        }

        public void setMaximumPoolSize(final int size) {
            if (size > getCorePoolSize()) {
                super.setMaximumPoolSize(size);
                super.setCorePoolSize(size);
            } else {
                super.setCorePoolSize(size);
                super.setMaximumPoolSize(size);
            }
        }
    }

    static class ThreadPoolExecutorTaskPool implements TaskPool {
        private final ThreadPoolExecutor delegate;

        ThreadPoolExecutorTaskPool(final ThreadPoolExecutor delegate) {
            this.delegate = delegate;
        }

        @Override
        public void shutdown() {
            delegate.shutdown();
        }

        @Override
        public List<Runnable> shutdownNow() {
            return delegate.shutdownNow();
        }

        @Override
        public void execute(final Runnable command) {
            delegate.execute(command);
        }

        @Override
        public int getCorePoolSize() {
            return delegate.getCorePoolSize();
        }

        @Override
        public int getMaximumPoolSize() {
            return delegate.getMaximumPoolSize();
        }

        @Override
        public long getKeepAliveTime(final TimeUnit unit) {
            return delegate.getKeepAliveTime(unit);
        }

        @Override
        public void setCorePoolSize(final int size) {
            delegate.setCorePoolSize(size);
        }

        @Override
        public void setMaximumPoolSize(final int size) {
            delegate.setMaximumPoolSize(size);
        }

        @Override
        public void setKeepAliveTime(final long time, final TimeUnit unit) {
            delegate.setKeepAliveTime(time, unit);
        }

        @Override
        public int getActiveCount() {
            return delegate.getActiveCount();
        }

        @Override
        public int getPoolSize() {
            return delegate.getPoolSize();
        }

        @Override
        public int getQueueSize() {
            return delegate.getQueue().size();
        }
    }

    static class EnhancedQueueExecutorTaskPool implements TaskPool {
        private final EnhancedQueueExecutor executor;

        EnhancedQueueExecutorTaskPool(final EnhancedQueueExecutor executor) {
            this.executor = executor;
        }

        public void shutdown() {
            executor.shutdown();
        }

        public List<Runnable> shutdownNow() {
            return executor.shutdownNow();
        }

        public void execute(final Runnable command) {
            executor.execute(command);
        }

        public int getCorePoolSize() {
            return executor.getCorePoolSize();
        }

        public int getMaximumPoolSize() {
            return executor.getMaximumPoolSize();
        }

        public long getKeepAliveTime(final TimeUnit unit) {
            return executor.getKeepAliveTime(unit);
        }

        public void setCorePoolSize(final int size) {
            executor.setCorePoolSize(size);
        }

        public void setMaximumPoolSize(final int size) {
            executor.setMaximumPoolSize(size);
        }

        public void setKeepAliveTime(final long time, final TimeUnit unit) {
            executor.setKeepAliveTime(time, unit);
        }

        public int getActiveCount() {
            return executor.getActiveCount();
        }

        public int getPoolSize() {
            return executor.getPoolSize();
        }

        public int getQueueSize() {
            return executor.getQueueSize();
        }
    }

    static class ExecutorServiceTaskPool implements TaskPool {
        private final ExecutorService delegate;

        ExecutorServiceTaskPool(final ExecutorService delegate) {
            this.delegate = delegate;
        }

        public void shutdown() {
            delegate.shutdown();
        }

        public List<Runnable> shutdownNow() {
            return delegate.shutdownNow();
        }

        public void execute(final Runnable command) {
            delegate.execute(command);
        }

        public int getCorePoolSize() {
            return -1;
        }

        public int getMaximumPoolSize() {
            return -1;
        }

        public long getKeepAliveTime(final TimeUnit unit) {
            return -1;
        }

        public void setCorePoolSize(final int size) {
        }

        public void setMaximumPoolSize(final int size) {
        }

        public void setKeepAliveTime(final long time, final TimeUnit unit) {
        }

        public int getActiveCount() {
            return -1;
        }

        public int getPoolSize() {
            return -1;
        }

        public int getQueueSize() {
            return -1;
        }
    }

    static class ExternalTaskPool implements TaskPool {
        private final TaskPool delegate;

        ExternalTaskPool(final TaskPool delegate) {
            this.delegate = delegate;
        }

        @Override
        public void shutdown() {
            // no operation
        }

        @Override
        public List<Runnable> shutdownNow() {
            return Collections.emptyList();
        }

        @Override
        public void execute(final Runnable command) {
            delegate.execute(command);
        }

        @Override
        public int getCorePoolSize() {
            return delegate.getCorePoolSize();
        }

        @Override
        public int getMaximumPoolSize() {
            return delegate.getMaximumPoolSize();
        }

        @Override
        public long getKeepAliveTime(final TimeUnit unit) {
            return delegate.getKeepAliveTime(unit);
        }

        @Override
        public void setCorePoolSize(final int size) {
            delegate.setCorePoolSize(size);
        }

        @Override
        public void setMaximumPoolSize(final int size) {
            delegate.setMaximumPoolSize(size);
        }

        @Override
        public void setKeepAliveTime(final long time, final TimeUnit unit) {
            delegate.setKeepAliveTime(time, unit);
        }

        @Override
        public int getActiveCount() {
            return delegate.getActiveCount();
        }

        @Override
        public int getPoolSize() {
            return delegate.getPoolSize();
        }

        @Override
        public int getQueueSize() {
            return delegate.getQueueSize();
        }
    }
}

