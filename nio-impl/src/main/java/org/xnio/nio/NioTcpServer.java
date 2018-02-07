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
import static org.xnio.nio.Log.tcpServerLog;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import org.jboss.logging.Logger;
import org.xnio.ChannelListener;
import org.xnio.ManagementRegistration;
import org.xnio.IoUtils;
import org.xnio.LocalSocketAddress;
import org.xnio.Option;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.StreamConnection;
import org.xnio.XnioExecutor;
import org.xnio.channels.AcceptListenerSettable;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.UnsupportedOptionException;
import org.xnio.management.XnioServerMXBean;

final class NioTcpServer extends AbstractNioChannel<NioTcpServer> implements AcceptingChannel<StreamConnection>, AcceptListenerSettable<NioTcpServer> {
    private static final String FQCN = NioTcpServer.class.getName();

    private volatile ChannelListener<? super NioTcpServer> acceptListener;

    private final NioTcpServerHandle[] handles;

    private final ServerSocketChannel channel;
    private final ServerSocket socket;
    private final ManagementRegistration mbeanHandle;

    private static final Set<Option<?>> options = Option.setBuilder()
            .add(Options.REUSE_ADDRESSES)
            .add(Options.RECEIVE_BUFFER)
            .add(Options.SEND_BUFFER)
            .add(Options.KEEP_ALIVE)
            .add(Options.TCP_OOB_INLINE)
            .add(Options.TCP_NODELAY)
            .add(Options.CONNECTION_HIGH_WATER)
            .add(Options.CONNECTION_LOW_WATER)
            .add(Options.READ_TIMEOUT)
            .add(Options.WRITE_TIMEOUT)
            .create();

    @SuppressWarnings("unused")
    private volatile int keepAlive;
    @SuppressWarnings("unused")
    private volatile int oobInline;
    @SuppressWarnings("unused")
    private volatile int tcpNoDelay;
    @SuppressWarnings("unused")
    private volatile int sendBuffer = -1;
    @SuppressWarnings("unused")
    private volatile long connectionStatus = CONN_LOW_MASK | CONN_HIGH_MASK;
    @SuppressWarnings("unused")
    private volatile int readTimeout;
    @SuppressWarnings("unused")
    private volatile int writeTimeout;
    private volatile int tokenConnectionCount;
    volatile boolean resumed;

    private static final long CONN_LOW_MASK     = 0x000000007FFFFFFFL;
    private static final long CONN_LOW_BIT      = 0L;
    @SuppressWarnings("unused")
    private static final long CONN_LOW_ONE      = 1L;
    private static final long CONN_HIGH_MASK    = 0x3FFFFFFF80000000L;
    private static final long CONN_HIGH_BIT     = 31L;
    @SuppressWarnings("unused")
    private static final long CONN_HIGH_ONE     = 1L << CONN_HIGH_BIT;

    private static final AtomicIntegerFieldUpdater<NioTcpServer> keepAliveUpdater = AtomicIntegerFieldUpdater.newUpdater(NioTcpServer.class, "keepAlive");
    private static final AtomicIntegerFieldUpdater<NioTcpServer> oobInlineUpdater = AtomicIntegerFieldUpdater.newUpdater(NioTcpServer.class, "oobInline");
    private static final AtomicIntegerFieldUpdater<NioTcpServer> tcpNoDelayUpdater = AtomicIntegerFieldUpdater.newUpdater(NioTcpServer.class, "tcpNoDelay");
    private static final AtomicIntegerFieldUpdater<NioTcpServer> sendBufferUpdater = AtomicIntegerFieldUpdater.newUpdater(NioTcpServer.class, "sendBuffer");
    private static final AtomicIntegerFieldUpdater<NioTcpServer> readTimeoutUpdater = AtomicIntegerFieldUpdater.newUpdater(NioTcpServer.class, "readTimeout");
    private static final AtomicIntegerFieldUpdater<NioTcpServer> writeTimeoutUpdater = AtomicIntegerFieldUpdater.newUpdater(NioTcpServer.class, "writeTimeout");

    private static final AtomicLongFieldUpdater<NioTcpServer> connectionStatusUpdater = AtomicLongFieldUpdater.newUpdater(NioTcpServer.class, "connectionStatus");

    NioTcpServer(final NioXnioWorker worker, final ServerSocketChannel channel, final OptionMap optionMap) throws IOException {
        super(worker);
        this.channel = channel;
        final WorkerThread[] threads = worker.getAll();
        final int threadCount = threads.length;
        if (threadCount == 0) {
            throw log.noThreads();
        }
        final int tokens = optionMap.get(Options.BALANCING_TOKENS, -1);
        final int connections = optionMap.get(Options.BALANCING_CONNECTIONS, 16);
        if (tokens != -1) {
            if (tokens < 1 || tokens >= threadCount) {
                throw log.balancingTokens();
            }
            if (connections < 1) {
                throw log.balancingConnectionCount();
            }
            tokenConnectionCount = connections;
        }
        socket = channel.socket();
        if (optionMap.contains(Options.SEND_BUFFER)) {
            final int sendBufferSize = optionMap.get(Options.SEND_BUFFER, DEFAULT_BUFFER_SIZE);
            if (sendBufferSize < 1) {
                throw log.parameterOutOfRange("sendBufferSize");
            }
            sendBufferUpdater.set(this, sendBufferSize);
        }
        if (optionMap.contains(Options.KEEP_ALIVE)) {
            keepAliveUpdater.lazySet(this, optionMap.get(Options.KEEP_ALIVE, false) ? 1 : 0);
        }
        if (optionMap.contains(Options.TCP_OOB_INLINE)) {
            oobInlineUpdater.lazySet(this, optionMap.get(Options.TCP_OOB_INLINE, false) ? 1 : 0);
        }
        if (optionMap.contains(Options.TCP_NODELAY)) {
            tcpNoDelayUpdater.lazySet(this, optionMap.get(Options.TCP_NODELAY, false) ? 1 : 0);
        }
        if (optionMap.contains(Options.READ_TIMEOUT)) {
            readTimeoutUpdater.lazySet(this, optionMap.get(Options.READ_TIMEOUT, 0));
        }
        if (optionMap.contains(Options.WRITE_TIMEOUT)) {
            writeTimeoutUpdater.lazySet(this, optionMap.get(Options.WRITE_TIMEOUT, 0));
        }
        int perThreadLow, perThreadLowRem;
        int perThreadHigh, perThreadHighRem;
        if (optionMap.contains(Options.CONNECTION_HIGH_WATER) || optionMap.contains(Options.CONNECTION_LOW_WATER)) {
            final int highWater = optionMap.get(Options.CONNECTION_HIGH_WATER, Integer.MAX_VALUE);
            final int lowWater = optionMap.get(Options.CONNECTION_LOW_WATER, highWater);
            if (highWater <= 0) {
                throw badHighWater();
            }
            if (lowWater <= 0 || lowWater > highWater) {
                throw badLowWater(highWater);
            }
            final long highLowWater = (long) highWater << CONN_HIGH_BIT | (long) lowWater << CONN_LOW_BIT;
            connectionStatusUpdater.lazySet(this, highLowWater);
            perThreadLow = lowWater / threadCount;
            perThreadLowRem = lowWater % threadCount;
            perThreadHigh = highWater / threadCount;
            perThreadHighRem = highWater % threadCount;
        } else {
            perThreadLow = Integer.MAX_VALUE;
            perThreadLowRem = 0;
            perThreadHigh = Integer.MAX_VALUE;
            perThreadHighRem = 0;
            connectionStatusUpdater.lazySet(this, CONN_LOW_MASK | CONN_HIGH_MASK);
        }
        final NioTcpServerHandle[] handles = new NioTcpServerHandle[threadCount];
        for (int i = 0, length = threadCount; i < length; i++) {
            final SelectionKey key = threads[i].registerChannel(channel);
            handles[i] = new NioTcpServerHandle(this, key, threads[i], i < perThreadHighRem ? perThreadHigh + 1 : perThreadHigh, i < perThreadLowRem ? perThreadLow + 1 : perThreadLow);
            key.attach(handles[i]);
        }
        this.handles = handles;
        if (tokens > 0) {
            for (int i = 0; i < threadCount; i ++) {
                handles[i].initializeTokenCount(i < tokens ? connections : 0);
            }
        }
        mbeanHandle = worker.registerServerMXBean(
                new XnioServerMXBean() {
                    public String getProviderName() {
                        return "nio";
                    }

                    public String getWorkerName() {
                        return worker.getName();
                    }

                    public String getBindAddress() {
                        return String.valueOf(getLocalAddress());
                    }

                    public int getConnectionCount() {
                        final AtomicInteger counter = new AtomicInteger();
                        final CountDownLatch latch = new CountDownLatch(handles.length);
                        for (final NioTcpServerHandle handle : handles) {
                            handle.getWorkerThread().execute(() -> {
                                counter.getAndAdd(handle.getConnectionCount());
                                latch.countDown();
                            });
                        }
                        try {
                            latch.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return counter.get();
                    }

                    public int getConnectionLimitHighWater() {
                        return getHighWater(connectionStatus);
                    }

                    public int getConnectionLimitLowWater() {
                        return getLowWater(connectionStatus);
                    }
                }
        );

    }

    private static IllegalArgumentException badLowWater(final int highWater) {
        return new IllegalArgumentException("Low water must be greater than 0 and less than or equal to high water (" + highWater + ")");
    }

    private static IllegalArgumentException badHighWater() {
        return new IllegalArgumentException("High water must be greater than 0");
    }

    public void close() throws IOException {
        try {
            channel.close();
        } finally {
            for (NioTcpServerHandle handle : handles) {
                handle.cancelKey(false);
            }
            safeClose(mbeanHandle);
        }
    }

    public boolean supportsOption(final Option<?> option) {
        return options.contains(option);
    }

    public <T> T getOption(final Option<T> option) throws UnsupportedOptionException, IOException {
        if (option == Options.REUSE_ADDRESSES) {
            return option.cast(Boolean.valueOf(socket.getReuseAddress()));
        } else if (option == Options.RECEIVE_BUFFER) {
            return option.cast(Integer.valueOf(socket.getReceiveBufferSize()));
        } else if (option == Options.SEND_BUFFER) {
            final int value = sendBuffer;
            return value == -1 ? null : option.cast(Integer.valueOf(value));
        } else if (option == Options.KEEP_ALIVE) {
            return option.cast(Boolean.valueOf(keepAlive != 0));
        } else if (option == Options.TCP_OOB_INLINE) {
            return option.cast(Boolean.valueOf(oobInline != 0));
        } else if (option == Options.TCP_NODELAY) {
            return option.cast(Boolean.valueOf(tcpNoDelay != 0));
        } else if (option == Options.READ_TIMEOUT) {
            return option.cast(Integer.valueOf(readTimeout));
        } else if (option == Options.WRITE_TIMEOUT) {
            return option.cast(Integer.valueOf(writeTimeout));
        } else if (option == Options.CONNECTION_HIGH_WATER) {
            return option.cast(Integer.valueOf(getHighWater(connectionStatus)));
        } else if (option == Options.CONNECTION_LOW_WATER) {
            return option.cast(Integer.valueOf(getLowWater(connectionStatus)));
        } else {
            return null;
        }
    }

    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        final Object old;
        if (option == Options.REUSE_ADDRESSES) {
            old = Boolean.valueOf(socket.getReuseAddress());
            socket.setReuseAddress(Options.REUSE_ADDRESSES.cast(value, Boolean.FALSE).booleanValue());
        } else if (option == Options.RECEIVE_BUFFER) { 
            old = Integer.valueOf(socket.getReceiveBufferSize());
            final int newValue = Options.RECEIVE_BUFFER.cast(value, Integer.valueOf(DEFAULT_BUFFER_SIZE)).intValue();
            if (newValue < 1) {
                throw log.optionOutOfRange("RECEIVE_BUFFER");
            }
            socket.setReceiveBufferSize(newValue);
        } else if (option == Options.SEND_BUFFER) {
            final int newValue = Options.SEND_BUFFER.cast(value, Integer.valueOf(DEFAULT_BUFFER_SIZE)).intValue();
            if (newValue < 1) {
                throw log.optionOutOfRange("SEND_BUFFER");
            }
            final int oldValue = sendBufferUpdater.getAndSet(this, newValue);
            old = oldValue == -1 ? null : Integer.valueOf(oldValue);
        } else if (option == Options.KEEP_ALIVE) {
            old = Boolean.valueOf(keepAliveUpdater.getAndSet(this, Options.KEEP_ALIVE.cast(value, Boolean.FALSE).booleanValue() ? 1 : 0) != 0);
        } else if (option == Options.TCP_OOB_INLINE) {
            old = Boolean.valueOf(oobInlineUpdater.getAndSet(this, Options.TCP_OOB_INLINE.cast(value, Boolean.FALSE).booleanValue() ? 1 : 0) != 0);
        } else if (option == Options.TCP_NODELAY) {
            old = Boolean.valueOf(tcpNoDelayUpdater.getAndSet(this, Options.TCP_NODELAY.cast(value, Boolean.FALSE).booleanValue() ? 1 : 0) != 0);
        } else if (option == Options.READ_TIMEOUT) {
            old = Integer.valueOf(readTimeoutUpdater.getAndSet(this, Options.READ_TIMEOUT.cast(value, Integer.valueOf(0)).intValue()));
        } else if (option == Options.WRITE_TIMEOUT) {
            old = Integer.valueOf(writeTimeoutUpdater.getAndSet(this, Options.WRITE_TIMEOUT.cast(value, Integer.valueOf(0)).intValue()));
        } else if (option == Options.CONNECTION_HIGH_WATER) {
            old = Integer.valueOf(getHighWater(updateWaterMark(-1, Options.CONNECTION_HIGH_WATER.cast(value, Integer.valueOf(Integer.MAX_VALUE)).intValue())));
        } else if (option == Options.CONNECTION_LOW_WATER) {
            old = Integer.valueOf(getLowWater(updateWaterMark(Options.CONNECTION_LOW_WATER.cast(value, Integer.valueOf(Integer.MAX_VALUE)).intValue(), -1)));
        } else {
            return null;
        }
        return option.cast(old);
    }

    private long updateWaterMark(int reqNewLowWater, int reqNewHighWater) {
        // at least one must be specified
        assert reqNewLowWater != -1 || reqNewHighWater != -1;
        // if both given, low must be less than high
        assert reqNewLowWater == -1 || reqNewHighWater == -1 || reqNewLowWater <= reqNewHighWater;

        long oldVal, newVal;
        int oldHighWater, oldLowWater;
        int newLowWater, newHighWater;

        do {
            oldVal = connectionStatus;
            oldLowWater = getLowWater(oldVal);
            oldHighWater = getHighWater(oldVal);
            newLowWater = reqNewLowWater == -1 ? oldLowWater : reqNewLowWater;
            newHighWater = reqNewHighWater == -1 ? oldHighWater : reqNewHighWater;
            // Make sure the new values make sense
            if (reqNewLowWater != -1 && newLowWater > newHighWater) {
                newHighWater = newLowWater;
            } else if (reqNewHighWater != -1 && newHighWater < newLowWater) {
                newLowWater = newHighWater;
            }
            // See if the change would be redundant
            if (oldLowWater == newLowWater && oldHighWater == newHighWater) {
                return oldVal;
            }
            newVal = (long)newLowWater << CONN_LOW_BIT | (long)newHighWater << CONN_HIGH_BIT;
        } while (! connectionStatusUpdater.compareAndSet(this, oldVal, newVal));

        final NioTcpServerHandle[] conduits = handles;
        final int threadCount = conduits.length;

        int perThreadLow, perThreadLowRem;
        int perThreadHigh, perThreadHighRem;

        perThreadLow = newLowWater / threadCount;
        perThreadLowRem = newLowWater % threadCount;
        perThreadHigh = newHighWater / threadCount;
        perThreadHighRem = newHighWater % threadCount;

        for (int i = 0; i < conduits.length; i++) {
            NioTcpServerHandle conduit = conduits[i];
            conduit.executeSetTask(i < perThreadHighRem ? perThreadHigh + 1 : perThreadHigh, i < perThreadLowRem ? perThreadLow + 1 : perThreadLow);
        }

        return oldVal;
    }

    private static int getHighWater(final long value) {
        return (int) ((value & CONN_HIGH_MASK) >> CONN_HIGH_BIT);
    }

    private static int getLowWater(final long value) {
        return (int) ((value & CONN_LOW_MASK) >> CONN_LOW_BIT);
    }

    public NioSocketStreamConnection accept() throws IOException {
        final WorkerThread current = WorkerThread.getCurrent();
        final NioTcpServerHandle handle = handles[current.getNumber()];
        if (! handle.getConnection()) {
            return null;
        }
        final SocketChannel accepted;
        boolean ok = false;
        try {
            accepted = channel.accept();
            if (accepted != null) try {
                final SocketAddress localAddress = accepted.getLocalAddress();
                int hash;
                if (localAddress instanceof InetSocketAddress) {
                    final InetSocketAddress address = (InetSocketAddress) localAddress;
                    hash = address.getAddress().hashCode() * 23 + address.getPort();
                } else if (localAddress instanceof LocalSocketAddress) {
                    hash = ((LocalSocketAddress) localAddress).getName().hashCode();
                } else {
                    hash = localAddress.hashCode();
                }
                final SocketAddress remoteAddress = accepted.getRemoteAddress();
                if (remoteAddress instanceof InetSocketAddress) {
                    final InetSocketAddress address = (InetSocketAddress) remoteAddress;
                    hash = (address.getAddress().hashCode() * 23 + address.getPort()) * 23 + hash;
                } else if (remoteAddress instanceof LocalSocketAddress) {
                    hash = ((LocalSocketAddress) remoteAddress).getName().hashCode() * 23 + hash;
                } else {
                    hash = localAddress.hashCode() * 23 + hash;
                }
                accepted.configureBlocking(false);
                final Socket socket = accepted.socket();
                socket.setKeepAlive(keepAlive != 0);
                socket.setOOBInline(oobInline != 0);
                socket.setTcpNoDelay(tcpNoDelay != 0);
                final int sendBuffer = this.sendBuffer;
                if (sendBuffer > 0) socket.setSendBufferSize(sendBuffer);
                final WorkerThread ioThread = worker.getIoThread(hash);
                final SelectionKey selectionKey = ioThread.registerChannel(accepted);
                final NioSocketStreamConnection newConnection = new NioSocketStreamConnection(ioThread, selectionKey, handle);
                newConnection.setOption(Options.READ_TIMEOUT, Integer.valueOf(readTimeout));
                newConnection.setOption(Options.WRITE_TIMEOUT, Integer.valueOf(writeTimeout));
                ok = true;
                return newConnection;
            } finally {
                if (! ok) safeClose(accepted);
            }
        } catch (IOException e) {
            return null;
        } finally {
            if (! ok) {
                handle.freeConnection();
            }
        }
        // by contract, only a resume will do
        return null;
    }

    public String toString() {
        return String.format("TCP server (NIO) <%s>", Integer.toHexString(hashCode()));
    }

    public ChannelListener<? super NioTcpServer> getAcceptListener() {
        return acceptListener;
    }

    public void setAcceptListener(final ChannelListener<? super NioTcpServer> acceptListener) {
        this.acceptListener = acceptListener;
    }

    public ChannelListener.Setter<NioTcpServer> getAcceptSetter() {
        return new AcceptListenerSettable.Setter<NioTcpServer>(this);
    }

    public boolean isOpen() {
        return channel.isOpen();
    }

    public SocketAddress getLocalAddress() {
        return socket.getLocalSocketAddress();
    }

    public <A extends SocketAddress> A getLocalAddress(final Class<A> type) {
        final SocketAddress address = getLocalAddress();
        return type.isInstance(address) ? type.cast(address) : null;
    }

    public void suspendAccepts() {
        resumed = false;
        doResume(0);
    }

    public void resumeAccepts() {
        resumed = true;
        doResume(SelectionKey.OP_ACCEPT);
    }

    public boolean isAcceptResumed() {
        return resumed;
    }

    private void doResume(final int op) {
        if (op == 0) {
            for (NioTcpServerHandle handle : handles) {
                handle.suspend();
            }
        } else {
            for (NioTcpServerHandle handle : handles) {
                handle.resume();
            }
        }
    }

    public void wakeupAccepts() {
        tcpServerLog.logf(FQCN, Logger.Level.TRACE, null, "Wake up accepts on %s", this);
        resumeAccepts();
        final NioTcpServerHandle[] handles = this.handles;
        final int idx = IoUtils.getThreadLocalRandom().nextInt(handles.length);
        handles[idx].wakeup(SelectionKey.OP_ACCEPT);
    }

    public void awaitAcceptable() throws IOException {
        throw log.unsupported("awaitAcceptable");
    }

    public void awaitAcceptable(final long time, final TimeUnit timeUnit) throws IOException {
        throw log.unsupported("awaitAcceptable");
    }

    @Deprecated
    public XnioExecutor getAcceptThread() {
        return getIoThread();
    }

    NioTcpServerHandle getHandle(final int number) {
        return handles[number];
    }

    int getTokenConnectionCount() {
        return tokenConnectionCount;
    }
}
