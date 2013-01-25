/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
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

package org.xnio.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.DatagramChannel;
import java.nio.channels.Pipe;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.LockSupport;
import org.xnio.Bits;
import org.xnio.Cancellable;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.ChannelPipe;
import org.xnio.ClosedWorkerException;
import org.xnio.FailedIoFuture;
import org.xnio.FinishedIoFuture;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.Option;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.StreamConnection;
import org.xnio.XnioExecutor;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.AssembledConnectedStreamChannel;
import org.xnio.channels.AssembledStreamChannel;
import org.xnio.channels.BoundChannel;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.channels.MulticastMessageChannel;
import org.xnio.channels.StreamChannel;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.ReadReadyHandler;
import org.xnio.conduits.WriteReadyHandler;

import static org.xnio.IoUtils.safeClose;
import static org.xnio.nio.Log.log;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class NioXnioWorker extends XnioWorker {

    private static final int CLOSE_REQ = (1 << 31);
    private static final int CLOSE_COMP = (1 << 30);
    private static final WrappingHandler WRAPPING_HANDLER = new WrappingHandler();

    // start at 1 for the provided thread pool
    private volatile int state = 1;

    private final WorkerThread[] readWorkers;
    private final WorkerThread[] writeWorkers;

    @SuppressWarnings("unused")
    private volatile Thread shutdownWaiter;

    private static final AtomicReferenceFieldUpdater<NioXnioWorker, Thread> shutdownWaiterUpdater = AtomicReferenceFieldUpdater.newUpdater(NioXnioWorker.class, Thread.class, "shutdownWaiter");

    private static final AtomicIntegerFieldUpdater<NioXnioWorker> stateUpdater = AtomicIntegerFieldUpdater.newUpdater(NioXnioWorker.class, "state");

    NioXnioWorker(final NioXnio xnio, final ThreadGroup threadGroup, final OptionMap optionMap, final Runnable terminationTask) throws IOException {
        super(xnio, threadGroup, optionMap, terminationTask);
        final int readCount = optionMap.get(Options.WORKER_READ_THREADS, 1);
        if (readCount < 0) {
            throw new IllegalArgumentException("Worker read thread count must be >= 0");
        }
        final int writeCount = optionMap.get(Options.WORKER_WRITE_THREADS, 1);
        if (writeCount < 0) {
            throw new IllegalArgumentException("Worker write thread count must be >= 0");
        }
        final long workerStackSize = optionMap.get(Options.STACK_SIZE, 0L);
        if (workerStackSize < 0L) {
            throw new IllegalArgumentException("Worker stack size must be >= 0");
        }
        String workerName = getName();
        WorkerThread[] readWorkers, writeWorkers;
        readWorkers = new WorkerThread[readCount];
        writeWorkers = new WorkerThread[writeCount];
        final boolean markWorkerThreadAsDaemon = optionMap.get(Options.THREAD_DAEMON, false);
        boolean ok = false;
        try {
            for (int i = 0; i < readCount; i++) {
                final WorkerThread readWorker = new WorkerThread(this, xnio.mainSelectorCreator.open(), String.format("%s read-%d", workerName, Integer.valueOf(i + 1)), threadGroup, workerStackSize, false, i);
                // Mark as daemon if the Options.THREAD_DAEMON has been set
                if (markWorkerThreadAsDaemon) {
                    readWorker.setDaemon(true);
                }
                readWorkers[i] = readWorker;
            }
            for (int i = 0; i < writeCount; i++) {
                final WorkerThread writeWorker = new WorkerThread(this, xnio.mainSelectorCreator.open(), String.format("%s write-%d", workerName, Integer.valueOf(i + 1)), threadGroup, workerStackSize, true, i);
                // Mark as daemon if Options.THREAD_DAEMON has been set
                if (markWorkerThreadAsDaemon) {
                    writeWorker.setDaemon(true);
                }
                writeWorkers[i] = writeWorker;
            }
            ok = true;
        } finally {
            if (! ok) {
                for (WorkerThread worker : readWorkers) {
                    if (worker != null) safeClose(worker.getSelector());
                }
                for (WorkerThread worker : writeWorkers) {
                    if (worker != null) safeClose(worker.getSelector());
                }
            }
        }
        this.readWorkers = readWorkers;
        this.writeWorkers = writeWorkers;
    }

    void start() {
        for (WorkerThread worker : readWorkers) {
            openResourceUnconditionally();
            worker.start();
        }
        for (WorkerThread worker : writeWorkers) {
            openResourceUnconditionally();
            worker.start();
        }
    }

    private static final WorkerThread[] NO_WORKERS = new WorkerThread[0];

    WorkerThread chooseOptional(final boolean write) {
        final WorkerThread[] orig = write ? writeWorkers : readWorkers;
        final int length = orig.length;
        if (length == 0) {
            return null;
        }
        if (length == 1) {
            return orig[0];
        }
        final Random random = IoUtils.getThreadLocalRandom();
        return orig[random.nextInt(length)];
    }

    WorkerThread choose(final boolean write) {
        final WorkerThread result = chooseOptional(write);
        if (result == null) {
            throw new IllegalArgumentException("No threads configured");
        }
        return result;
    }

    WorkerThread[] getAll(final boolean write) {
        return write ? writeWorkers : readWorkers;
    }

    WorkerThread[] choose(int count, boolean write) {
        if (count == 0) {
            return NO_WORKERS;
        }
        final WorkerThread[] orig = write ? writeWorkers : readWorkers;
        final int length = orig.length;
        final int halfLength = length >> 1;
        if (length == 0) {
            throw new IllegalArgumentException("No threads configured");
        }
        if (count == length) {
            return orig;
        }
        if (count > length) {
            throw new IllegalArgumentException("Not enough " + (write ? "write" : "read") + " threads configured");
        }
        final WorkerThread[] result = new WorkerThread[count];
        final Random random = IoUtils.getThreadLocalRandom();
        if (count == 1) {
            result[0] = orig[random.nextInt(length)];
            return result;
        }
        if (length < 32) {
            if (count >= halfLength) {
                int bits = (1 << length) - 1;
                do {
                    bits &= ~(1 << random.nextInt(length));
                } while (Integer.bitCount(bits) > count);
                for (int i = 0; i < count; i ++) {
                    final int bit = Integer.numberOfTrailingZeros(bits);
                    result[i] = orig[bit];
                    bits ^= Integer.lowestOneBit(bits);
                }
                return result;
            } else {
                int bits = 0;
                do {
                    bits |= (1 << random.nextInt(length));
                } while (Integer.bitCount(bits) < count);
                for (int i = 0; i < count; i ++) {
                    final int bit = Integer.numberOfTrailingZeros(bits);
                    result[i] = orig[bit];
                    bits ^= Integer.lowestOneBit(bits);
                }
                return result;
            }
        }
        if (length < 64) {
            if (count >= halfLength) {
                long bits = (1L << (long) length) - 1L;
                do {
                    bits &= ~(1L << (long) random.nextInt(length));
                } while (Long.bitCount(bits) > count);
                for (int i = 0; i < count; i ++) {
                    final int bit = Long.numberOfTrailingZeros(bits);
                    result[i] = orig[bit];
                    bits ^= Long.lowestOneBit(bits);
                }
                return result;
            } else {
                long bits = 0;
                do {
                    bits |= (1L << (long) random.nextInt(length));
                } while (Long.bitCount(bits) < count);
                for (int i = 0; i < count; i ++) {
                    final int bit = Long.numberOfTrailingZeros(bits);
                    result[i] = orig[bit];
                    bits ^= Long.lowestOneBit(bits);
                }
                return result;
            }
        }
        // lots of threads.  No faster way to do it.
        final HashSet<WorkerThread> set;
        if (count >= halfLength) {
            // We're returning half or more of the threads.
            set = new HashSet<WorkerThread>(Arrays.asList(orig));
            while (set.size() > count) {
                set.remove(orig[random.nextInt(length)]);
            }
        } else {
            // We're returning less than half of the threads.
            set = new HashSet<WorkerThread>(length);
            while (set.size() < count) {
                set.add(orig[random.nextInt(length)]);
            }
        }
        return set.toArray(result);
    }

    protected AcceptingChannel<? extends ConnectedStreamChannel> createTcpServer(final InetSocketAddress bindAddress, final ChannelListener<? super AcceptingChannel<ConnectedStreamChannel>> acceptListener, final OptionMap optionMap) throws IOException {
        final AcceptingChannel<StreamConnection> server = createTcpConnectionServer(bindAddress, null, optionMap);
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

            public XnioExecutor getAcceptThread() {
                return server.getAcceptThread();
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

    protected AcceptingChannel<StreamConnection> createTcpConnectionServer(final InetSocketAddress bindAddress, final ChannelListener<? super AcceptingChannel<StreamConnection>> acceptListener, final OptionMap optionMap) throws IOException {
        checkShutdown();
        boolean ok = false;
        final ServerSocketChannel channel = ServerSocketChannel.open();
        try {
            if (optionMap.contains(Options.RECEIVE_BUFFER)) channel.socket().setReceiveBufferSize(optionMap.get(Options.RECEIVE_BUFFER, -1));
            channel.socket().setReuseAddress(optionMap.get(Options.REUSE_ADDRESSES, true));
            channel.configureBlocking(false);
            if (optionMap.contains(Options.BACKLOG)) {
                channel.socket().bind(bindAddress, optionMap.get(Options.BACKLOG, 128));
            } else {
                channel.socket().bind(bindAddress);
            }
            final NioTcpServer server = new NioTcpServer(this, channel, optionMap);
            server.setAcceptListener(acceptListener);
            ok = true;
            return server;
        } finally {
            if (! ok) {
                IoUtils.safeClose(channel);
            }
        }
    }

    static class CompleteHandler implements WriteReadyHandler, ReadReadyHandler {
        private final NioSocketSourceConduit sourceConduit;
        private final NioSocketSinkConduit sinkConduit;
        private final NioSocketStreamConnection connection;
        private final FutureResult<StreamConnection> futureResult;
        private final ChannelListener<? super StreamConnection> openListener;

        CompleteHandler(final FutureResult<StreamConnection> futureResult, final NioSocketStreamConnection connection, final NioSocketSinkConduit sinkConduit, final NioSocketSourceConduit sourceConduit, final ChannelListener<? super StreamConnection> openListener) {
            this.futureResult = futureResult;
            this.connection = connection;
            this.sinkConduit = sinkConduit;
            this.sourceConduit = sourceConduit;
            this.openListener = openListener;
        }

        public void readReady() {
            ready();
        }

        public void writeReady() {
            ready();
        }

        private void ready() {
            final SocketChannel channel = connection.getChannel();
            boolean ok = false;
            try {
                if (channel.finishConnect()) {
                    sinkConduit.suspend();
                    sourceConduit.suspend();
                    sinkConduit.setOps(SelectionKey.OP_WRITE);
                    sourceConduit.setOps(SelectionKey.OP_READ);
                    connection.setSourceConduit(sourceConduit);
                    connection.setSinkConduit(sinkConduit);
                    ok = futureResult.setResult(connection);
                    ChannelListeners.invokeChannelListener(connection, openListener);
                }
            } catch (IOException e) {
                futureResult.setException(e);
            } finally {
                if (! ok) safeClose(connection);
            }
        }

        public void forceTermination() {
        }

        public void terminated() {
        }
    }

    protected IoFuture<StreamConnection> openTcpStreamConnection(final InetSocketAddress bindAddress, final InetSocketAddress destinationAddress, final ChannelListener<? super StreamConnection> openListener, final ChannelListener<? super BoundChannel> bindListener, final OptionMap optionMap) {
        try {
            checkShutdown();
        } catch (ClosedWorkerException e) {
            return new FailedIoFuture<StreamConnection>(e);
        }
        try {
            final SocketChannel channel = SocketChannel.open();
            boolean ok = false;
            try {
                channel.configureBlocking(false);
                if (optionMap.contains(Options.TCP_OOB_INLINE)) channel.socket().setOOBInline(optionMap.get(Options.TCP_OOB_INLINE, false));
                if (optionMap.contains(Options.TCP_NODELAY)) channel.socket().setTcpNoDelay(optionMap.get(Options.TCP_NODELAY, false));
                if (optionMap.contains(Options.IP_TRAFFIC_CLASS)) channel.socket().setTrafficClass(optionMap.get(Options.IP_TRAFFIC_CLASS, -1));
                if (optionMap.contains(Options.CLOSE_ABORT)) channel.socket().setSoLinger(optionMap.get(Options.CLOSE_ABORT, false), 0);
                if (optionMap.contains(Options.KEEP_ALIVE)) channel.socket().setKeepAlive(optionMap.get(Options.KEEP_ALIVE, false));
                if (optionMap.contains(Options.RECEIVE_BUFFER)) channel.socket().setReceiveBufferSize(optionMap.get(Options.RECEIVE_BUFFER, -1));
                if (optionMap.contains(Options.REUSE_ADDRESSES)) channel.socket().setReuseAddress(optionMap.get(Options.REUSE_ADDRESSES, false));
                if (optionMap.contains(Options.SEND_BUFFER)) channel.socket().setSendBufferSize(optionMap.get(Options.SEND_BUFFER, -1));
                channel.socket().bind(bindAddress);
                final NioSocketStreamConnection connection = new NioSocketStreamConnection(this, channel, null);
                ChannelListeners.invokeChannelListener(connection, bindListener);
                final WorkerThread readThread = chooseOptional(false);
                final WorkerThread writeThread = chooseOptional(true);
                final SelectionKey readKey = readThread == null ? new ThreadlessSelectionKey(this, channel): readThread.registerChannel(channel);
                final SelectionKey writeKey = writeThread == null ? new ThreadlessSelectionKey(this, channel) : writeThread.registerChannel(channel);
                final NioSocketSourceConduit sourceConduit = new NioSocketSourceConduit(connection, readKey, readThread);
                final NioSocketSinkConduit sinkConduit = new NioSocketSinkConduit(connection, writeKey, writeThread);
                final boolean establishWrite = optionMap.get(Options.WORKER_ESTABLISH_WRITING, false);
                final AbstractNioConduit<SocketChannel> connectConduit = establishWrite ? sinkConduit : sourceConduit;

                if (channel.connect(destinationAddress)) {
                    connection.setSourceConduit(sourceConduit);
                    connection.setSinkConduit(sinkConduit);
                    connectConduit.getWorkerThread().execute(ChannelListeners.getChannelListenerTask(connection, openListener));
                    final FinishedIoFuture<StreamConnection> finishedIoFuture = new FinishedIoFuture<StreamConnection>(connection);
                    ok = true;
                    return finishedIoFuture;
                }
                final FutureResult<StreamConnection> futureResult = new FutureResult<StreamConnection>(connectConduit.getWorkerThread());
                final CompleteHandler completeHandler = new CompleteHandler(futureResult, connection, sinkConduit, sourceConduit, openListener);
                if (establishWrite) {
                    sinkConduit.setWriteReadyHandler(completeHandler);
                } else {
                    sourceConduit.setReadReadyHandler(completeHandler);
                }
                futureResult.addCancelHandler(new Cancellable() {
                    public Cancellable cancel() {
                        if (futureResult.setCancelled()) {
                            safeClose(connection);
                        }
                        return this;
                    }
                });
                connectConduit.setOps(SelectionKey.OP_CONNECT);
                connectConduit.resume();
                ok = true;
                return futureResult.getIoFuture();
            } finally {
                if (! ok) safeClose(channel);
            }
        } catch (IOException e) {
            return new FailedIoFuture<StreamConnection>(e);
        }
    }

    protected IoFuture<ConnectedStreamChannel> connectTcpStream(final InetSocketAddress bindAddress, final InetSocketAddress destinationAddress, final ChannelListener<? super ConnectedStreamChannel> openListener, final ChannelListener<? super BoundChannel> bindListener, final OptionMap optionMap) {
        final FutureResult<ConnectedStreamChannel> futureResult = new FutureResult<ConnectedStreamChannel>();
        final ChannelListener<StreamConnection> nestedOpenListener = new ConnectionWrapListener(futureResult, openListener);
        final IoFuture<StreamConnection> future = openTcpStreamConnection(bindAddress, destinationAddress, nestedOpenListener, bindListener, optionMap);
        future.addNotifier(WRAPPING_HANDLER, futureResult);
        futureResult.addCancelHandler(future);
        return futureResult.getIoFuture();
    }

    protected IoFuture<ConnectedStreamChannel> acceptTcpStream(final InetSocketAddress destination, final ChannelListener<? super ConnectedStreamChannel> openListener, final ChannelListener<? super BoundChannel> bindListener, final OptionMap optionMap) {
        final FutureResult<ConnectedStreamChannel> futureResult = new FutureResult<ConnectedStreamChannel>();
        final ChannelListener<StreamConnection> nestedOpenListener = new ConnectionWrapListener(futureResult, openListener);
        final IoFuture<StreamConnection> future = acceptTcpStreamConnection(destination, nestedOpenListener, bindListener, optionMap);
        future.addNotifier(WRAPPING_HANDLER, futureResult);
        futureResult.addCancelHandler(future);
        return futureResult.getIoFuture();
    }

    /** {@inheritDoc} */
    public MulticastMessageChannel createUdpServer(final InetSocketAddress bindAddress, final ChannelListener<? super MulticastMessageChannel> bindListener, final OptionMap optionMap) throws IOException {
        checkShutdown();
        final DatagramChannel channel = DatagramChannel.open();
        channel.configureBlocking(false);
        if (optionMap.contains(Options.BROADCAST)) channel.socket().setBroadcast(optionMap.get(Options.BROADCAST, false));
        if (optionMap.contains(Options.IP_TRAFFIC_CLASS)) channel.socket().setTrafficClass(optionMap.get(Options.IP_TRAFFIC_CLASS, -1));
        if (optionMap.contains(Options.RECEIVE_BUFFER)) channel.socket().setReceiveBufferSize(optionMap.get(Options.RECEIVE_BUFFER, -1));
        channel.socket().setReuseAddress(optionMap.get(Options.REUSE_ADDRESSES, true));
        if (optionMap.contains(Options.SEND_BUFFER)) channel.socket().setSendBufferSize(optionMap.get(Options.SEND_BUFFER, -1));
        channel.socket().bind(bindAddress);
        final NioUdpChannel udpChannel = new NioUdpChannel(this, channel);
        ChannelListeners.invokeChannelListener(udpChannel, bindListener);
        return udpChannel;
    }

    public ChannelPipe<StreamConnection, StreamConnection> createFullDuplexPipeConnection() throws IOException {
        checkShutdown();
        boolean ok = false;
        final Pipe topPipe = Pipe.open();
        try {
            topPipe.source().configureBlocking(false);
            topPipe.sink().configureBlocking(false);
            final Pipe bottomPipe = Pipe.open();
            try {
                bottomPipe.source().configureBlocking(false);
                bottomPipe.sink().configureBlocking(false);
                final NioPipeStreamConnection leftConnection = new NioPipeStreamConnection(this, bottomPipe.source(), topPipe.sink());
                final NioPipeStreamConnection rightConnection = new NioPipeStreamConnection(this, topPipe.source(), bottomPipe.sink());
                final WorkerThread topSourceThread = chooseOptional(false);
                final WorkerThread topSinkThread = chooseOptional(true);
                final WorkerThread bottomSourceThread = chooseOptional(false);
                final WorkerThread bottomSinkThread = chooseOptional(true);
                final SelectionKey topSourceKey = topSourceThread == null ? new ThreadlessSelectionKey(this, topPipe.source()) : topSourceThread.registerChannel(topPipe.source());
                final SelectionKey topSinkKey = topSinkThread == null ? new ThreadlessSelectionKey(this, topPipe.sink()) : topSinkThread.registerChannel(topPipe.sink());
                final SelectionKey bottomSourceKey = bottomSourceThread == null ? new ThreadlessSelectionKey(this, bottomPipe.source()) : bottomSourceThread.registerChannel(bottomPipe.source());
                final SelectionKey bottomSinkKey = bottomSinkThread == null ? new ThreadlessSelectionKey(this, bottomPipe.sink()) : bottomSinkThread.registerChannel(bottomPipe.sink());
                final NioPipeSourceConduit leftSourceConduit = new NioPipeSourceConduit(leftConnection, bottomSourceKey, bottomSourceThread);
                final NioPipeSinkConduit leftSinkConduit = new NioPipeSinkConduit(leftConnection, topSinkKey, topSinkThread);
                final NioPipeSourceConduit rightSourceConduit = new NioPipeSourceConduit(rightConnection, topSourceKey, topSourceThread);
                final NioPipeSinkConduit rightSinkConduit = new NioPipeSinkConduit(rightConnection, bottomSinkKey, bottomSinkThread);
                leftSourceConduit.setOps(SelectionKey.OP_READ);
                leftSinkConduit.setOps(SelectionKey.OP_WRITE);
                rightSourceConduit.setOps(SelectionKey.OP_READ);
                rightSinkConduit.setOps(SelectionKey.OP_WRITE);
                leftConnection.setSourceConduit(leftSourceConduit);
                leftConnection.setSinkConduit(leftSinkConduit);
                rightConnection.setSourceConduit(rightSourceConduit);
                rightConnection.setSinkConduit(rightSinkConduit);
                final ChannelPipe<StreamConnection, StreamConnection> result = new ChannelPipe<StreamConnection, StreamConnection>(leftConnection, rightConnection);
                ok = true;
                return result;
            } finally {
                if (! ok) {
                    safeClose(bottomPipe.sink());
                    safeClose(bottomPipe.source());
                }
            }
        } finally {
            if (! ok) {
                safeClose(topPipe.sink());
                safeClose(topPipe.source());
            }
        }
    }

    public ChannelPipe<StreamChannel, StreamChannel> createFullDuplexPipe() throws IOException {
        final ChannelPipe<StreamConnection, StreamConnection> connection = createFullDuplexPipeConnection();
        final StreamChannel left = new AssembledStreamChannel(connection.getLeftSide(), connection.getLeftSide().getSourceChannel(), connection.getLeftSide().getSinkChannel());
        final StreamChannel right = new AssembledStreamChannel(connection.getRightSide(), connection.getRightSide().getSourceChannel(), connection.getRightSide().getSinkChannel());
        return new ChannelPipe<StreamChannel, StreamChannel>(left, right);
    }

    public ChannelPipe<StreamSourceChannel, StreamSinkChannel> createHalfDuplexPipe() throws IOException {
        checkShutdown();
        final Pipe pipe = Pipe.open();
        boolean ok = false;
        try {
            pipe.source().configureBlocking(false);
            pipe.sink().configureBlocking(false);
            final NioPipeStreamConnection leftConnection = new NioPipeStreamConnection(this, pipe.source(), null);
            final NioPipeStreamConnection rightConnection = new NioPipeStreamConnection(this, null, pipe.sink());
            final WorkerThread readThread = chooseOptional(false);
            final WorkerThread writeThread = chooseOptional(true);
            final SelectionKey readKey = readThread == null ? new ThreadlessSelectionKey(this, pipe.source()) : readThread.registerChannel(pipe.source());
            final SelectionKey writeKey = writeThread == null ? new ThreadlessSelectionKey(this, pipe.sink()) : writeThread.registerChannel(pipe.sink());
            final NioPipeSourceConduit sourceConduit = new NioPipeSourceConduit(leftConnection, readKey, readThread);
            final NioPipeSinkConduit sinkConduit = new NioPipeSinkConduit(rightConnection, writeKey, writeThread);
            sourceConduit.setOps(SelectionKey.OP_READ);
            sinkConduit.setOps(SelectionKey.OP_WRITE);
            leftConnection.setSourceConduit(sourceConduit);
            leftConnection.writeClosed();
            rightConnection.readClosed();
            rightConnection.setSinkConduit(sinkConduit);
            final ChannelPipe<StreamSourceChannel,StreamSinkChannel> result = new ChannelPipe<StreamSourceChannel, StreamSinkChannel>(leftConnection.getSourceChannel(), rightConnection.getSinkChannel());
            ok = true;
            return result;
        } finally {
            if (! ok) {
                safeClose(pipe.sink());
                safeClose(pipe.source());
            }
        }
    }

    protected IoFuture<StreamConnection> acceptTcpStreamConnection(final InetSocketAddress destination, final ChannelListener<? super StreamConnection> openListener, final ChannelListener<? super BoundChannel> bindListener, final OptionMap optionMap) {
        try {
            checkShutdown();
        } catch (ClosedWorkerException e) {
            return new FailedIoFuture<StreamConnection>(e);
        }
        final FutureResult<StreamConnection> futureResult = new FutureResult<StreamConnection>(this);
        try {
            boolean ok = false;
            final ServerSocketChannel serverChannel = ServerSocketChannel.open();
            try {
                serverChannel.configureBlocking(false);
                if (optionMap.contains(Options.RECEIVE_BUFFER)) {
                    serverChannel.socket().setReceiveBufferSize(optionMap.get(Options.RECEIVE_BUFFER, -1));
                }
                serverChannel.socket().setReuseAddress(optionMap.get(Options.REUSE_ADDRESSES, true));
                serverChannel.bind(destination);
                if (bindListener != null) ChannelListeners.invokeChannelListener(new BoundChannel() {
                    public SocketAddress getLocalAddress() {
                        return serverChannel.socket().getLocalSocketAddress();
                    }

                    public <A extends SocketAddress> A getLocalAddress(final Class<A> type) {
                        final SocketAddress address = getLocalAddress();
                        return type.isInstance(address) ? type.cast(address) : null;
                    }

                    public ChannelListener.Setter<? extends BoundChannel> getCloseSetter() {
                        return new ChannelListener.SimpleSetter<BoundChannel>();
                    }

                    public XnioWorker getWorker() {
                        return NioXnioWorker.this;
                    }

                    public void close() throws IOException {
                        serverChannel.close();
                    }

                    public boolean isOpen() {
                        return serverChannel.isOpen();
                    }

                    public boolean supportsOption(final Option<?> option) {
                        return false;
                    }

                    public <T> T getOption(final Option<T> option) throws IOException {
                        return null;
                    }

                    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
                        return null;
                    }
                }, bindListener);
                final WorkerThread thread = choose(optionMap.get(Options.WORKER_ESTABLISH_WRITING, false));
                final SelectionKey key = thread.registerChannel(serverChannel);
                final AbstractNioConduit<ServerSocketChannel> conduit = new AbstractNioConduit<ServerSocketChannel>(key, thread) {
                    void handleReady() {
                        boolean ok = false;
                        try {
                            final SocketChannel channel = serverChannel.accept();
                            if (channel == null) {
                                ok = true;
                                return;
                            } else {
                                safeClose(serverChannel);
                            }
                            try {
                                channel.configureBlocking(false);
                                if (optionMap.contains(Options.TCP_OOB_INLINE)) channel.socket().setOOBInline(optionMap.get(Options.TCP_OOB_INLINE, false));
                                if (optionMap.contains(Options.TCP_NODELAY)) channel.socket().setTcpNoDelay(optionMap.get(Options.TCP_NODELAY, false));
                                if (optionMap.contains(Options.IP_TRAFFIC_CLASS)) channel.socket().setTrafficClass(optionMap.get(Options.IP_TRAFFIC_CLASS, -1));
                                if (optionMap.contains(Options.CLOSE_ABORT)) channel.socket().setSoLinger(optionMap.get(Options.CLOSE_ABORT, false), 0);
                                if (optionMap.contains(Options.KEEP_ALIVE)) channel.socket().setKeepAlive(optionMap.get(Options.KEEP_ALIVE, false));
                                if (optionMap.contains(Options.SEND_BUFFER)) channel.socket().setSendBufferSize(optionMap.get(Options.SEND_BUFFER, -1));
                                final NioSocketStreamConnection connection = new NioSocketStreamConnection(NioXnioWorker.this, channel, null);
                                final WorkerThread readThread;
                                final WorkerThread writeThread;
                                if (thread.isWriteThread()) {
                                    readThread = chooseOptional(false);
                                    writeThread = thread;
                                } else {
                                    readThread = thread;
                                    writeThread = chooseOptional(true);
                                }
                                final SelectionKey readKey = readThread == null ? new ThreadlessSelectionKey(NioXnioWorker.this, channel) : readThread.registerChannel(channel);
                                final SelectionKey writeKey = writeThread == null ? new ThreadlessSelectionKey(NioXnioWorker.this, channel) : writeThread.registerChannel(channel);
                                final NioSocketSourceConduit sourceConduit = new NioSocketSourceConduit(connection, readKey, readThread);
                                final NioSocketSinkConduit sinkConduit = new NioSocketSinkConduit(connection, writeKey, writeThread);
                                sourceConduit.setOps(SelectionKey.OP_READ);
                                sinkConduit.setOps(SelectionKey.OP_WRITE);
                                connection.setSourceConduit(sourceConduit);
                                connection.setSinkConduit(sinkConduit);
                                if (futureResult.setResult(connection)) {
                                    ok = true;
                                    ChannelListeners.invokeChannelListener(connection, openListener);
                                }
                            } finally {
                                if (! ok) safeClose(channel);
                            }
                        } catch (IOException e) {
                            futureResult.setException(e);
                        } finally {
                            if (! ok) {
                                safeClose(serverChannel);
                            }
                        }
                    }

                    void forceTermination() {
                        futureResult.setCancelled();
                    }
                };
                conduit.setOps(SelectionKey.OP_ACCEPT);
                conduit.resume();
                ok = true;
                futureResult.addCancelHandler(new Cancellable() {
                    public Cancellable cancel() {
                        if (futureResult.setCancelled()) {
                            safeClose(serverChannel);
                        }
                        return this;
                    }
                });
                return futureResult.getIoFuture();
            } finally {
                if (! ok) safeClose(serverChannel);
            }
        } catch (IOException e) {
            return new FailedIoFuture<StreamConnection>(e);
        }
    }

    public boolean isShutdown() {
        return (state & CLOSE_REQ) != 0;
    }

    public boolean isTerminated() {
        return (state & CLOSE_COMP) != 0;
    }

    /**
     * Open a resource unconditionally (i.e. accepting a connection on an open server).
     */
    void openResourceUnconditionally() {
        int oldState = stateUpdater.getAndIncrement(this);
        if (log.isTraceEnabled()) {
            log.tracef("CAS %s %08x -> %08x", this, Integer.valueOf(oldState), Integer.valueOf(oldState + 1));
        }
    }

    void checkShutdown() throws ClosedWorkerException {
        if (isShutdown()) throw new ClosedWorkerException("Worker is shut down");
    }

    void closeResource() {
        int oldState = stateUpdater.decrementAndGet(this);
        if (log.isTraceEnabled()) {
            log.tracef("CAS %s %08x -> %08x", this, Integer.valueOf(oldState + 1), Integer.valueOf(oldState));
        }
        while (oldState == CLOSE_REQ) {
            if (stateUpdater.compareAndSet(this, CLOSE_REQ, CLOSE_REQ | CLOSE_COMP)) {
                log.tracef("CAS %s %08x -> %08x (close complete)", this, Integer.valueOf(CLOSE_REQ), Integer.valueOf(CLOSE_REQ | CLOSE_COMP));
                safeUnpark(shutdownWaiterUpdater.getAndSet(this, null));
                final Runnable task = getTerminationTask();
                if (task != null) try {
                    task.run();
                } catch (Throwable ignored) {}
                return;
            }
            oldState = state;
        }
    }

    public void shutdown() {
        int oldState;
        oldState = state;
        while ((oldState & CLOSE_REQ) == 0) {
            // need to do the close ourselves...
            if (! stateUpdater.compareAndSet(this, oldState, oldState | CLOSE_REQ)) {
                // changed in the meantime
                oldState = state;
                continue;
            }
            log.tracef("Initiating shutdown of %s", this);
            for (WorkerThread worker : readWorkers) {
                worker.shutdown();
            }
            for (WorkerThread worker : writeWorkers) {
                worker.shutdown();
            }
            shutDownTaskPool();
            return;
        }
        log.tracef("Idempotent shutdown of %s", this);
        return;
    }

    public List<Runnable> shutdownNow() {
        shutdown();
        return shutDownTaskPoolNow();
    }

    public boolean awaitTermination(final long timeout, final TimeUnit unit) throws InterruptedException {
        int oldState = state;
        if (Bits.allAreSet(oldState, CLOSE_COMP)) {
            return true;
        }
        long then = System.nanoTime();
        long duration = unit.toNanos(timeout);
        final Thread myThread = Thread.currentThread();
        while (Bits.allAreClear(oldState = state, CLOSE_COMP)) {
            final Thread oldThread = shutdownWaiterUpdater.getAndSet(this, myThread);
            try {
                if (Bits.allAreSet(oldState = state, CLOSE_COMP)) {
                    break;
                }
                LockSupport.parkNanos(this, duration);
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
                long now = System.nanoTime();
                duration -= now - then;
                if (duration < 0L) {
                    oldState = state;
                    break;
                }
            } finally {
                safeUnpark(oldThread);
            }
        }
        return Bits.allAreSet(oldState, CLOSE_COMP);
    }

    public void awaitTermination() throws InterruptedException {
        int oldState = state;
        if (Bits.allAreSet(oldState, CLOSE_COMP)) {
            return;
        }
        final Thread myThread = Thread.currentThread();
        while (Bits.allAreClear(state, CLOSE_COMP)) {
            final Thread oldThread = shutdownWaiterUpdater.getAndSet(this, myThread);
            try {
                if (Bits.allAreSet(state, CLOSE_COMP)) {
                    break;
                }
                LockSupport.park(this);
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
            } finally {
                safeUnpark(oldThread);
            }
        }
    }

    private static void safeUnpark(final Thread waiter) {
        if (waiter != null) LockSupport.unpark(waiter);
    }

    protected void taskPoolTerminated() {
        closeResource();
    }

    public NioXnio getXnio() {
        return (NioXnio) super.getXnio();
    }

    private static class ConnectionWrapListener implements ChannelListener<StreamConnection> {

        private final FutureResult<ConnectedStreamChannel> futureResult;
        private final ChannelListener<? super ConnectedStreamChannel> openListener;

        public ConnectionWrapListener(final FutureResult<ConnectedStreamChannel> futureResult, final ChannelListener<? super ConnectedStreamChannel> openListener) {
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

    private static class WrappingHandler extends IoFuture.HandlingNotifier<StreamConnection, FutureResult<ConnectedStreamChannel>> {

        public void handleCancelled(final FutureResult<ConnectedStreamChannel> attachment) {
            attachment.setCancelled();
        }

        public void handleFailed(final IOException exception, final FutureResult<ConnectedStreamChannel> attachment) {
            attachment.setException(exception);
        }
    }
}
