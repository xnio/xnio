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

import static org.xnio.IoUtils.safeClose;
import static org.xnio.nio.Log.log;

import java.io.Closeable;
import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.nio.channels.DatagramChannel;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.LockSupport;

import org.wildfly.common.net.CidrAddressTable;
import org.xnio.Bits;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.ManagementRegistration;
import org.xnio.ClosedWorkerException;
import org.xnio.IoUtils;
import org.xnio.Option;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.StreamConnection;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.MulticastMessageChannel;
import org.xnio.management.XnioServerMXBean;
import org.xnio.management.XnioWorkerMXBean;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class NioXnioWorker extends XnioWorker {

    private static final int CLOSE_REQ = (1 << 31);
    private static final int CLOSE_COMP = (1 << 30);
    private final long workerStackSize;

    private volatile int state = 1;

    private final WorkerThread[] workerThreads;
    private final WorkerThread acceptThread;
    private final NioWorkerMetrics metrics;

    @SuppressWarnings("unused")
    private volatile Thread shutdownWaiter;

    private static final AtomicReferenceFieldUpdater<NioXnioWorker, Thread> shutdownWaiterUpdater = AtomicReferenceFieldUpdater.newUpdater(NioXnioWorker.class, Thread.class, "shutdownWaiter");

    private static final AtomicIntegerFieldUpdater<NioXnioWorker> stateUpdater = AtomicIntegerFieldUpdater.newUpdater(NioXnioWorker.class, "state");

    @SuppressWarnings("deprecation")
    NioXnioWorker(final Builder builder) {
        super(builder);
        final NioXnio xnio = (NioXnio) builder.getXnio();
        final int threadCount = builder.getWorkerIoThreads();
        this.workerStackSize = builder.getWorkerStackSize();
        final String workerName = getName();
        WorkerThread[] workerThreads;
        workerThreads = new WorkerThread[threadCount];
        final ThreadGroup threadGroup = builder.getThreadGroup();
        final boolean markWorkerThreadAsDaemon = builder.isDaemon();
        boolean ok = false;
        try {
            for (int i = 0; i < threadCount; i++) {
                final Selector threadSelector;
                try {
                    threadSelector = xnio.mainSelectorCreator.open();
                } catch (IOException e) {
                    throw Log.log.unexpectedSelectorOpenProblem(e);
                }
                final WorkerThread workerThread = new WorkerThread(this, threadSelector, String.format("%s:I/O-%d", workerName, Integer.valueOf(i + 1)), threadGroup, workerStackSize, i);
                // Mark as daemon if the Options.THREAD_DAEMON has been set
                if (markWorkerThreadAsDaemon) {
                    workerThread.setDaemon(true);
                }
                workerThreads[i] = workerThread;
            }
            final Selector threadSelector;
            try {
                threadSelector = xnio.mainSelectorCreator.open();
            } catch (IOException e) {
                throw Log.log.unexpectedSelectorOpenProblem(e);
            }
            acceptThread = new WorkerThread(this, threadSelector, String.format("%s:Accept", workerName), threadGroup, workerStackSize, threadCount);
            if (markWorkerThreadAsDaemon) {
                acceptThread.setDaemon(true);
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
        this.metrics = new NioWorkerMetrics(workerName);
        metrics.register();
    }

    void start() {
        for (WorkerThread worker : workerThreads) {
            openResourceUnconditionally();
            worker.start();
        }
        openResourceUnconditionally();
        acceptThread.start();
    }

    protected CidrAddressTable<InetSocketAddress> getBindAddressTable() {
        return super.getBindAddressTable();
    }

    protected WorkerThread chooseThread() {
        return getIoThread(ThreadLocalRandom.current().nextInt());
    }

    public WorkerThread getIoThread(final int hashCode) {
        final WorkerThread[] workerThreads = this.workerThreads;
        final int length = workerThreads.length;
        if (length == 0) {
            throw log.noThreads();
        }
        if (length == 1) {
            return workerThreads[0];
        }
        return workerThreads[Math.abs(hashCode % length)];
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
            if (false) {
                final NioTcpServer server = new NioTcpServer(this, channel, optionMap, false);
                server.setAcceptListener(acceptListener);
                ok = true;
                return server;
            } else {
                final QueuedNioTcpServer2 server = new QueuedNioTcpServer2(new NioTcpServer(this, channel, optionMap, true));
                server.setAcceptListener(acceptListener);
                ok = true;
                return server;
            }
        } finally {
            if (! ok) {
                IoUtils.safeClose(channel);
            }
        }
    }


    /** {@inheritDoc} */
    public MulticastMessageChannel createUdpServer(final InetSocketAddress bindAddress, final ChannelListener<? super MulticastMessageChannel> bindListener, final OptionMap optionMap) throws IOException {
        checkShutdown();
        final DatagramChannel channel;
        if (bindAddress != null) {
            InetAddress address = bindAddress.getAddress();
            if (address instanceof Inet6Address) {
                channel = DatagramChannel.open(StandardProtocolFamily.INET6);
            } else {
                channel = DatagramChannel.open(StandardProtocolFamily.INET);
            }
        } else {
            channel = DatagramChannel.open();
        }
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
        if (isShutdown())
            throw log.workerShutDown(this);
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
            acceptThread.shutdown();
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
        safeClose(metrics);
        closeResource();
    }

    @Override
    public <T> T getOption(Option<T> option) throws IOException {
        if (option.equals(Options.WORKER_IO_THREADS)) {
            return option.cast(workerThreads.length);
        } else if (option.equals(Options.STACK_SIZE)) {
            return option.cast(workerStackSize);
        } else {
            return super.getOption(option);
        }
    }

    public NioXnio getXnio() {
        return (NioXnio) super.getXnio();
    }

    WorkerThread getAcceptThread() {
        return acceptThread;
    }

    @Override
    public XnioWorkerMXBean getMXBean() {
        return metrics;
    }

    @Override
    protected ManagementRegistration registerServerMXBean(XnioServerMXBean serverMXBean) {
        return metrics.registerServerMXBean(serverMXBean);
    }

    private class NioWorkerMetrics implements XnioWorkerMXBean,Closeable {
        private final String workerName;
        private final CopyOnWriteArrayList<XnioServerMXBean> serverMetrics = new CopyOnWriteArrayList<>();
        private Closeable mbeanHandle;

        private NioWorkerMetrics(String workerName) {
            this.workerName = workerName;
        }

        public String getProviderName() {
            return "nio";
        }

        public String getName() {
            return workerName;
        }

        public boolean isShutdownRequested() {
            return isShutdown();
        }

        public int getCoreWorkerPoolSize() {
            return NioXnioWorker.this.getCoreWorkerPoolSize();
        }

        public int getMaxWorkerPoolSize() {
            return NioXnioWorker.this.getMaxWorkerPoolSize();
        }

        public int getWorkerPoolSize() {
            return NioXnioWorker.this.getWorkerPoolSize();
        }

        public int getBusyWorkerThreadCount() {
            return NioXnioWorker.this.getBusyWorkerThreadCount();
        }

        public int getIoThreadCount() {
            return NioXnioWorker.this.getIoThreadCount();
        }

        public int getWorkerQueueSize() {
            return NioXnioWorker.this.getWorkerQueueSize();
        }

        private ManagementRegistration registerServerMXBean(XnioServerMXBean serverMXBean){
            serverMetrics.addIfAbsent(serverMXBean);
            final Closeable handle = NioXnio.register(serverMXBean);
            return () -> {
                serverMetrics.remove(serverMXBean);
                safeClose(handle);
            };
        }

        public Set<XnioServerMXBean> getServerMXBeans() {
            return new LinkedHashSet<>(serverMetrics);
        }
        private void register(){
            this.mbeanHandle = NioXnio.register(this);
        }

        @Override
        public void close() throws IOException {
            safeClose(mbeanHandle);
            serverMetrics.clear();
        }
    }
}
