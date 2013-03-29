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
import java.nio.channels.DatagramChannel;
import java.nio.channels.ServerSocketChannel;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.LockSupport;
import org.xnio.Bits;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.ClosedWorkerException;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.StreamConnection;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.MulticastMessageChannel;

import static org.xnio.IoUtils.safeClose;
import static org.xnio.nio.Log.log;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class NioXnioWorker extends XnioWorker {

    private static final int CLOSE_REQ = (1 << 31);
    private static final int CLOSE_COMP = (1 << 30);

    // start at 1 for the provided thread pool
    private volatile int state = 1;

    private final WorkerThread[] workerThreads;

    @SuppressWarnings("unused")
    private volatile Thread shutdownWaiter;

    private static final AtomicReferenceFieldUpdater<NioXnioWorker, Thread> shutdownWaiterUpdater = AtomicReferenceFieldUpdater.newUpdater(NioXnioWorker.class, Thread.class, "shutdownWaiter");

    private static final AtomicIntegerFieldUpdater<NioXnioWorker> stateUpdater = AtomicIntegerFieldUpdater.newUpdater(NioXnioWorker.class, "state");

    @SuppressWarnings("deprecation")
    NioXnioWorker(final NioXnio xnio, final ThreadGroup threadGroup, final OptionMap optionMap, final Runnable terminationTask) throws IOException {
        super(xnio, threadGroup, optionMap, terminationTask);
        final int threadCount;
        if (optionMap.contains(Options.WORKER_IO_THREADS)) {
            threadCount = optionMap.get(Options.WORKER_IO_THREADS, 0);
        } else {
            threadCount = Math.max(optionMap.get(Options.WORKER_READ_THREADS, 1), optionMap.get(Options.WORKER_WRITE_THREADS, 1));
        }
        if (threadCount < 0) {
            throw new IllegalArgumentException("Worker I/O thread count must be >= 0");
        }
        final long workerStackSize = optionMap.get(Options.STACK_SIZE, 0L);
        if (workerStackSize < 0L) {
            throw new IllegalArgumentException("Worker stack size must be >= 0");
        }
        String workerName = getName();
        WorkerThread[] workerThreads;
        workerThreads = new WorkerThread[threadCount];
        final boolean markWorkerThreadAsDaemon = optionMap.get(Options.THREAD_DAEMON, false);
        boolean ok = false;
        try {
            for (int i = 0; i < threadCount; i++) {
                final WorkerThread workerThread = new WorkerThread(this, xnio.mainSelectorCreator.open(), String.format("%s I/O-%d", workerName, Integer.valueOf(i + 1)), threadGroup, workerStackSize, i);
                // Mark as daemon if the Options.THREAD_DAEMON has been set
                if (markWorkerThreadAsDaemon) {
                    workerThread.setDaemon(true);
                }
                workerThreads[i] = workerThread;
            }
            ok = true;
        } finally {
            if (! ok) {
                for (WorkerThread worker : workerThreads) {
                    if (worker != null) safeClose(worker.getSelector());
                }
            }
        }
        this.workerThreads = workerThreads;
    }

    void start() {
        for (WorkerThread worker : workerThreads) {
            openResourceUnconditionally();
            worker.start();
        }
    }

    protected WorkerThread chooseThread() {
        final WorkerThread[] workerThreads = this.workerThreads;
        final int length = workerThreads.length;
        if (length == 0) {
            throw new IllegalArgumentException("No I/O threads configured");
        }
        if (length == 1) {
            return workerThreads[0];
        }
        final Random random = IoUtils.getThreadLocalRandom();
        return workerThreads[random.nextInt(length)];
    }

    public int getIoThreadCount() {
        return workerThreads.length;
    }

    WorkerThread[] getAll() {
        return workerThreads;
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
            for (WorkerThread worker : workerThreads) {
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
}
