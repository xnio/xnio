/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.xnio.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.SocketAddress;
import java.nio.channels.DatagramChannel;
import java.nio.channels.Pipe;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.LockSupport;
import org.xnio.Cancellable;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.ClosedWorkerException;
import org.xnio.FailedIoFuture;
import org.xnio.FinishedIoFuture;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.Option;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.XnioExecutor;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.BoundChannel;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.channels.MulticastMessageChannel;
import org.xnio.channels.StreamChannel;

import static org.xnio.IoUtils.safeClose;
import static org.xnio.ChannelListener.SimpleSetter;
import static org.xnio.nio.Log.log;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class NioXnioWorker extends XnioWorker {

    private static final int CLOSE_REQ = (1 << 31);
    private static final int CLOSE_COMP = (1 << 30);

    // start at 1 for the provided thread pool
    private volatile int state = 1;

    private final WorkerThread[] readWorkers;
    private final WorkerThread[] writeWorkers;

    private volatile Thread[] shutdownWaiters = NONE;

    private static final Thread[] NONE = new Thread[0];
    private static final Thread[] SHUTDOWN_COMPLETE = new Thread[0];

    private static final AtomicReferenceFieldUpdater<NioXnioWorker, Thread[]> shutdownWaitersUpdater = AtomicReferenceFieldUpdater.newUpdater(NioXnioWorker.class, Thread[].class, "shutdownWaiters");
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
                final WorkerThread readWorker = new WorkerThread(this, Selector.open(), String.format("%s read-%d", workerName, Integer.valueOf(i + 1)), threadGroup, workerStackSize);
                // Mark as daemon if the Options.THREAD_DAEMON has been set
                if (markWorkerThreadAsDaemon) {
                    readWorker.setDaemon(true);
                }
                readWorkers[i] = readWorker;
            }
            for (int i = 0; i < writeCount; i++) {
                final WorkerThread writeWorker = new WorkerThread(this, Selector.open(), String.format("%s write-%d", workerName, Integer.valueOf(i + 1)), threadGroup, workerStackSize);
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

    WorkerThread choose() {
        final WorkerThread[] write = writeWorkers;
        final WorkerThread[] read = readWorkers;
        final int writeLength = write.length;
        final int readLength = read.length;
        if (writeLength == 0) {
            return choose(false);
        }
        if (readLength == 0) {
            return choose(true);
        }
        final Random random = IoUtils.getThreadLocalRandom();
        final int idx = random.nextInt(writeLength + readLength);
        return idx >= readLength ? write[idx - readLength] : read[idx];
    }

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
        boolean ok = false;
        final ServerSocketChannel channel = ServerSocketChannel.open();
        try {
            channel.configureBlocking(false);
            channel.socket().bind(bindAddress);
            final NioTcpServer server = new NioTcpServer(this, channel, optionMap);
            final ChannelListener.SimpleSetter<NioTcpServer> setter = server.getAcceptSetter();
            // not unsafe - http://youtrack.jetbrains.net/issue/IDEA-59290
            //noinspection unchecked
            setter.set((ChannelListener<? super NioTcpServer>) acceptListener);
            ok = true;
            return server;
        } finally {
            if (! ok) {
                IoUtils.safeClose(channel);
            }
        }
    }

    protected IoFuture<ConnectedStreamChannel> connectTcpStream(final InetSocketAddress bindAddress, final InetSocketAddress destinationAddress, final ChannelListener<? super ConnectedStreamChannel> openListener, final ChannelListener<? super BoundChannel> bindListener, final OptionMap optionMap) {
        try {
            final SocketChannel channel = SocketChannel.open();
            channel.configureBlocking(false);
            channel.socket().bind(bindAddress);
            final NioTcpChannel tcpChannel = new NioTcpChannel(this, null, channel);
            final NioHandle<NioTcpChannel> connectHandle = optionMap.get(Options.WORKER_ESTABLISH_WRITING, false) ? tcpChannel.getWriteHandle() : tcpChannel.getReadHandle();
            ChannelListeners.invokeChannelListener(tcpChannel.getBoundChannel(), bindListener);
            if (channel.connect(destinationAddress)) {
                // not unsafe - http://youtrack.jetbrains.net/issue/IDEA-59290
                //noinspection unchecked
                connectHandle.getWorkerThread().execute(ChannelListeners.getChannelListenerTask(tcpChannel, openListener));
                return new FinishedIoFuture<ConnectedStreamChannel>(tcpChannel);
            }
            final SimpleSetter<NioTcpChannel> setter = connectHandle.getHandlerSetter();
            final FutureResult<ConnectedStreamChannel> futureResult = new FutureResult<ConnectedStreamChannel>();
            setter.set(new ChannelListener<NioTcpChannel>() {
                public void handleEvent(final NioTcpChannel channel) {
                    final SocketChannel socketChannel = channel.getReadChannel();
                    try {
                        if (socketChannel.finishConnect()) {
                            connectHandle.suspend();
                            connectHandle.getHandlerSetter().set(null);
                            if (!futureResult.setResult(tcpChannel)) {
                                // if futureResult is canceled, close channel
                                IoUtils.safeClose(channel);
                            } else {
                                channel.configureFrom(optionMap);
                                //noinspection unchecked
                                ChannelListeners.invokeChannelListener(tcpChannel, openListener);
                            }
                        }
                    } catch (IOException e) {
                        IoUtils.safeClose(channel);
                        futureResult.setException(e);
                    }
                }

                public String toString() {
                    return "Connection finisher for " + channel;
                }
            });
            futureResult.addCancelHandler(new Cancellable() {
                public Cancellable cancel() {
                    if (futureResult.setCancelled()) {
                        IoUtils.safeClose(tcpChannel);
                    }
                    return this;
                }

                public String toString() {
                    return "Cancel handler for " + channel;
                }
            });
            connectHandle.resume(SelectionKey.OP_CONNECT);
            return futureResult.getIoFuture();
        } catch (IOException e) {
            return new FailedIoFuture<ConnectedStreamChannel>(e);
        }
    }

    protected IoFuture<ConnectedStreamChannel> acceptTcpStream(final InetSocketAddress destination, final ChannelListener<? super ConnectedStreamChannel> openListener, final ChannelListener<? super BoundChannel> bindListener, final OptionMap optionMap) {
        final WorkerThread connectThread = choose(optionMap.get(Options.WORKER_ESTABLISH_WRITING, false));
        try {
            final ServerSocketChannel channel = ServerSocketChannel.open();
            channel.configureBlocking(false);
            channel.socket().bind(destination);
            final NioSetter<NioTcpChannel> closeSetter = new NioSetter<NioTcpChannel>();
            //noinspection unchecked
            ChannelListeners.invokeChannelListener(new BoundChannel() {
                public XnioWorker getWorker() {
                    return NioXnioWorker.this;
                }

                public SocketAddress getLocalAddress() {
                    return channel.socket().getLocalSocketAddress();
                }

                public <A extends SocketAddress> A getLocalAddress(final Class<A> type) {
                    final SocketAddress address = getLocalAddress();
                    return type.isInstance(address) ? type.cast(address) : null;
                }

                public ChannelListener.Setter<? extends BoundChannel> getCloseSetter() {
                    return closeSetter;
                }

                public boolean isOpen() {
                    return channel.isOpen();
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

                public void close() throws IOException {
                    channel.close();
                }

                public String toString() {
                    return String.format("TCP acceptor bound channel (NIO) <%h>", this);
                }
            }, bindListener);
            final SocketChannel accepted = channel.accept();
            if (accepted != null) {
                IoUtils.safeClose(channel);
                final NioTcpChannel tcpChannel = new NioTcpChannel(this, null, accepted);
                tcpChannel.configureFrom(optionMap);
                //noinspection unchecked
                ChannelListeners.invokeChannelListener(tcpChannel, openListener);
                return new FinishedIoFuture<ConnectedStreamChannel>(tcpChannel);
            }
            final SimpleSetter<ServerSocketChannel> setter = new SimpleSetter<ServerSocketChannel>();
            final FutureResult<ConnectedStreamChannel> futureResult = new FutureResult<ConnectedStreamChannel>();
            final NioHandle<ServerSocketChannel> handle = connectThread.addChannel(channel, channel, 0, setter);
            setter.set(new ChannelListener<ServerSocketChannel>() {
                public void handleEvent(final ServerSocketChannel channel) {
                    final SocketChannel accepted;
                    try {
                        accepted = channel.accept();
                        if (accepted == null) {
                            return;
                        }
                    } catch (IOException e) {
                        IoUtils.safeClose(channel);
                        handle.cancelKey();
                        futureResult.setException(e);
                        return;
                    }
                    boolean ok = false;
                    try {
                        handle.cancelKey();
                        IoUtils.safeClose(channel);
                        try {
                            accepted.configureBlocking(false);
                            final NioTcpChannel tcpChannel;
                            tcpChannel = new NioTcpChannel(NioXnioWorker.this, null, accepted);
                            tcpChannel.configureFrom(optionMap);
                            futureResult.setResult(tcpChannel);
                            ok = true;
                            //noinspection unchecked
                            ChannelListeners.invokeChannelListener(tcpChannel, openListener);
                        } catch (IOException e) {
                            futureResult.setException(e);
                            return;
                        }
                    } finally {
                        if (! ok) {
                            IoUtils.safeClose(accepted);
                        }
                    }
                }

                public String toString() {
                    return "Accepting finisher for " + channel;
                }
            });
            handle.resume(SelectionKey.OP_ACCEPT);
            return futureResult.getIoFuture();
        } catch (IOException e) {
            return new FailedIoFuture<ConnectedStreamChannel>(e);
        }
    }

    /** {@inheritDoc} */
    public MulticastMessageChannel createUdpServer(final InetSocketAddress bindAddress, final ChannelListener<? super MulticastMessageChannel> bindListener, final OptionMap optionMap) throws IOException {
        if (!NioXnio.NIO2 && optionMap.get(Options.MULTICAST, false)) {
            final MulticastSocket socket = new MulticastSocket(bindAddress);
            final BioMulticastUdpChannel channel = new BioMulticastUdpChannel(this, optionMap.get(Options.SEND_BUFFER, 8192), optionMap.get(Options.RECEIVE_BUFFER, 8192), socket, chooseOptional(false), chooseOptional(true));
            channel.open();
            //noinspection unchecked
            ChannelListeners.invokeChannelListener(channel, bindListener);
            return channel;
        } else {
            final DatagramChannel channel = DatagramChannel.open();
            channel.configureBlocking(false);
            channel.socket().bind(bindAddress);
            final NioUdpChannel udpChannel = new NioUdpChannel(this, channel);
            //noinspection unchecked
            ChannelListeners.invokeChannelListener(udpChannel, bindListener);
            return udpChannel;
        }
    }

    public void createPipe(final ChannelListener<? super StreamChannel> leftOpenListener, final ChannelListener<? super StreamChannel> rightOpenListener, final OptionMap optionMap) throws IOException {
        boolean ok = false;
        final Pipe in = Pipe.open();
        try {
            final Pipe out = Pipe.open();
            try {
                final NioPipeChannel outbound = new NioPipeChannel(NioXnioWorker.this, in.sink(), out.source());
                try {
                    final NioPipeChannel inbound = new NioPipeChannel(NioXnioWorker.this, out.sink(), in.source());
                    try {
                        final boolean establishWriting = optionMap.get(Options.WORKER_ESTABLISH_WRITING, false);
                        XnioExecutor outboundExec = establishWriting ? outbound.getWriteThread() : outbound.getReadThread();
                        XnioExecutor inboundExec = establishWriting ? inbound.getWriteThread() : inbound.getReadThread();
                        // not unsafe - http://youtrack.jetbrains.net/issue/IDEA-59290
                        //noinspection unchecked
                        outboundExec.execute(ChannelListeners.getChannelListenerTask(outbound, leftOpenListener));
                        // not unsafe - http://youtrack.jetbrains.net/issue/IDEA-59290
                        //noinspection unchecked
                        inboundExec.execute(ChannelListeners.getChannelListenerTask(inbound, rightOpenListener));
                        ok = true;
                    } catch (RejectedExecutionException e) {
                        throw new IOException("Failed to execute open task(s)", e);
                    } finally {
                        if (! ok) {
                            safeClose(inbound);
                        }
                    }
                } finally {
                    if (! ok) {
                        safeClose(outbound);
                    }
                }
            } finally {
                if (! ok) {
                    safeClose(out.sink());
                    safeClose(out.source());
                }
            }
        } finally {
            if (! ok) {
                safeClose(in.sink());
                safeClose(in.source());
            }
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

    /**
     * Open a resource.  Must be matched with a corresponding {@code closeResource()}.
     *
     * @throws ClosedWorkerException if the worker is closed
     */
    void openResource() throws ClosedWorkerException {
        int oldState;
        do {
            oldState = state;
            if ((oldState & CLOSE_REQ) != 0) {
                throw new ClosedWorkerException("Worker is shutting down");
            }
        } while (! stateUpdater.compareAndSet(this, oldState, oldState + 1));
        if (log.isTraceEnabled()) {
            log.tracef("CAS %s %08x -> %08x", this, Integer.valueOf(oldState), Integer.valueOf(oldState + 1));
        }
    }

    void closeResource() {
        int oldState = stateUpdater.decrementAndGet(this);
        if (log.isTraceEnabled()) {
            log.tracef("CAS %s %08x -> %08x", this, Integer.valueOf(oldState + 1), Integer.valueOf(oldState));
        }
        while (oldState == CLOSE_REQ) {
            if (stateUpdater.compareAndSet(this, CLOSE_REQ, CLOSE_REQ | CLOSE_COMP)) {
                log.tracef("CAS %s %08x -> %08x (close complete)", this, Integer.valueOf(CLOSE_REQ), Integer.valueOf(CLOSE_REQ | CLOSE_COMP));
                final Thread[] waiters = shutdownWaitersUpdater.getAndSet(this, SHUTDOWN_COMPLETE);
                for (Thread waiter : waiters) {
                    LockSupport.unpark(waiter);
                }
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
        if ((oldState & CLOSE_COMP) != 0) {
            return true;
        }
        long start = System.nanoTime();
        long elapsed = 0L;
        Thread[] waiters, newWaiters;
        OUT: do {
            waiters = shutdownWaiters;
            if (waiters == SHUTDOWN_COMPLETE) {
                return true;
            }
            final Thread myThread = Thread.currentThread();
            for (Thread waiter : waiters) {
                if (waiter == myThread) {
                    break OUT;
                }
            }
            newWaiters = Arrays.copyOf(waiters, waiters.length + 1);
            newWaiters[waiters.length] = myThread;
        } while (! shutdownWaitersUpdater.compareAndSet(this, waiters, newWaiters));
        final long nanos = unit.toNanos(timeout);
        while (((oldState = state) & CLOSE_COMP) == 0) {
            LockSupport.parkNanos(this, nanos - elapsed);
            elapsed = System.nanoTime() - start;
            if (elapsed > nanos) {
                return false;
            }
        }
        return true;
    }

    protected void taskPoolTerminated() {
        closeResource();
    }

    public NioXnio getXnio() {
        return (NioXnio) super.getXnio();
    }
}
