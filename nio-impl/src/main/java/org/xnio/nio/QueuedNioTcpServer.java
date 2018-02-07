/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2015 Red Hat, Inc. and/or its affiliates.
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import org.jboss.logging.Logger;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
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

final class QueuedNioTcpServer extends AbstractNioChannel<QueuedNioTcpServer> implements AcceptingChannel<StreamConnection>, AcceptListenerSettable<QueuedNioTcpServer> {
    private static final String FQCN = QueuedNioTcpServer.class.getName();

    private volatile ChannelListener<? super QueuedNioTcpServer> acceptListener;

    private final QueuedNioTcpServerHandle handle;
    private final WorkerThread thread;

    private final ServerSocketChannel channel;
    private final ServerSocket socket;
    private final ManagementRegistration mbeanHandle;

    private final List<BlockingQueue<SocketChannel>> acceptQueues;

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

    private static final long CONN_LOW_MASK     = 0x000000007FFFFFFFL;
    private static final long CONN_LOW_BIT      = 0L;
    @SuppressWarnings("unused")
    private static final long CONN_LOW_ONE      = 1L;
    private static final long CONN_HIGH_MASK    = 0x3FFFFFFF80000000L;
    private static final long CONN_HIGH_BIT     = 31L;
    @SuppressWarnings("unused")
    private static final long CONN_HIGH_ONE     = 1L << CONN_HIGH_BIT;

    /**
     * The current number of open connections, can only be accessed by the accept thread
     */
    private int openConnections;
    private volatile boolean suspendedDueToWatermark;
    private volatile boolean suspended;

    private static final AtomicIntegerFieldUpdater<QueuedNioTcpServer> keepAliveUpdater = AtomicIntegerFieldUpdater.newUpdater(QueuedNioTcpServer.class, "keepAlive");
    private static final AtomicIntegerFieldUpdater<QueuedNioTcpServer> oobInlineUpdater = AtomicIntegerFieldUpdater.newUpdater(QueuedNioTcpServer.class, "oobInline");
    private static final AtomicIntegerFieldUpdater<QueuedNioTcpServer> tcpNoDelayUpdater = AtomicIntegerFieldUpdater.newUpdater(QueuedNioTcpServer.class, "tcpNoDelay");
    private static final AtomicIntegerFieldUpdater<QueuedNioTcpServer> sendBufferUpdater = AtomicIntegerFieldUpdater.newUpdater(QueuedNioTcpServer.class, "sendBuffer");
    private static final AtomicIntegerFieldUpdater<QueuedNioTcpServer> readTimeoutUpdater = AtomicIntegerFieldUpdater.newUpdater(QueuedNioTcpServer.class, "readTimeout");
    private static final AtomicIntegerFieldUpdater<QueuedNioTcpServer> writeTimeoutUpdater = AtomicIntegerFieldUpdater.newUpdater(QueuedNioTcpServer.class, "writeTimeout");

    private static final AtomicLongFieldUpdater<QueuedNioTcpServer> connectionStatusUpdater = AtomicLongFieldUpdater.newUpdater(QueuedNioTcpServer.class, "connectionStatus");
    private final Runnable acceptTask = new Runnable() {
        public void run() {
            final WorkerThread current = WorkerThread.getCurrent();
            assert current != null;
            final BlockingQueue<SocketChannel> queue = acceptQueues.get(current.getNumber());
            ChannelListeners.invokeChannelListener(QueuedNioTcpServer.this, getAcceptListener());
            if (! queue.isEmpty() && !suspendedDueToWatermark) {
                current.execute(this);
            }
        }
    };

    private final Runnable connectionClosedTask = new Runnable() {
        @Override
        public void run() {
            openConnections--;
            if(suspendedDueToWatermark && openConnections < getLowWater(connectionStatus)) {
                synchronized (QueuedNioTcpServer.this) {
                    suspendedDueToWatermark = false;
                }
            }
        }
    };

    QueuedNioTcpServer(final NioXnioWorker worker, final ServerSocketChannel channel, final OptionMap optionMap) throws IOException {
        super(worker);
        this.channel = channel;
        this.thread = worker.getAcceptThread();
        final WorkerThread[] workerThreads = worker.getAll();
        final List<BlockingQueue<SocketChannel>> acceptQueues = new ArrayList<>(workerThreads.length);
        for (int i = 0; i < workerThreads.length; i++) {
            acceptQueues.add(i, new LinkedBlockingQueue<SocketChannel>());
        }
        this.acceptQueues = acceptQueues;
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
        final int highWater;
        final int lowWater;
        if (optionMap.contains(Options.CONNECTION_HIGH_WATER) || optionMap.contains(Options.CONNECTION_LOW_WATER)) {
            highWater = optionMap.get(Options.CONNECTION_HIGH_WATER, Integer.MAX_VALUE);
            lowWater = optionMap.get(Options.CONNECTION_LOW_WATER, highWater);
            if (highWater <= 0) {
                throw badHighWater();
            }
            if (lowWater <= 0 || lowWater > highWater) {
                throw badLowWater(highWater);
            }
            final long highLowWater = (long) highWater << CONN_HIGH_BIT | (long) lowWater << CONN_LOW_BIT;
            connectionStatusUpdater.lazySet(this, highLowWater);
        } else {
            highWater = Integer.MAX_VALUE;
            lowWater = Integer.MAX_VALUE;
            connectionStatusUpdater.lazySet(this, CONN_LOW_MASK | CONN_HIGH_MASK);
        }
        final SelectionKey key = thread.registerChannel(channel);
        handle = new QueuedNioTcpServerHandle(this, thread, key, highWater, lowWater);
        key.attach(handle);
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
                        CompletableFuture<Integer> future = CompletableFuture.supplyAsync(
                                () -> openConnections, handle.getWorkerThread()
                        );
                        try {
                            return future.get();
                        } catch (InterruptedException | ExecutionException e) {
                            return -1;
                        }
                    }

                    public int getConnectionLimitHighWater() {
                        return getHighWater(connectionStatus);
                    }

                    public int getConnectionLimitLowWater() {
                        return getLowWater(connectionStatus);
                    }
                });
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
            handle.cancelKey(true);
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
        getIoThread().execute(new Runnable() {
            @Override
            public void run() {
                if(openConnections >= getHighWater(connectionStatus)) {
                    synchronized (QueuedNioTcpServer.this) {
                        suspendedDueToWatermark = true;
                        tcpServerLog.logf(FQCN, Logger.Level.DEBUG, null, "Total open connections reach high water limit (%s) after updating water mark", getHighWater(connectionStatus));
                    }
                } else if(suspendedDueToWatermark && openConnections <= getLowWater(connectionStatus)) {
                    suspendedDueToWatermark = false;
                }
            }
        });
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
        if (current == null) {
            return null;
        }
        final BlockingQueue<SocketChannel> socketChannels = acceptQueues.get(current.getNumber());
        final SocketChannel accepted;
        boolean ok = false;
        try {
            accepted = socketChannels.poll();
            if (accepted != null) try {
                final SelectionKey selectionKey = current.registerChannel(accepted);
                final NioSocketStreamConnection newConnection = new NioSocketStreamConnection(current, selectionKey, handle);
                newConnection.setOption(Options.READ_TIMEOUT, Integer.valueOf(readTimeout));
                newConnection.setOption(Options.WRITE_TIMEOUT, Integer.valueOf(writeTimeout));
                ok = true;
                return newConnection;
            } finally {
                if (! ok) {
                    safeClose(accepted);
                    handle.freeConnection();
                }
            }
        } catch (IOException e) {
            return null;
        }
        // by contract, only a resume will do
        return null;
    }

    public String toString() {
        return String.format("TCP server (NIO) <%s>", Integer.toHexString(hashCode()));
    }

    public ChannelListener<? super QueuedNioTcpServer> getAcceptListener() {
        return acceptListener;
    }

    public void setAcceptListener(final ChannelListener<? super QueuedNioTcpServer> acceptListener) {
        this.acceptListener = acceptListener;
    }

    public ChannelListener.Setter<QueuedNioTcpServer> getAcceptSetter() {
        return new Setter<QueuedNioTcpServer>(this);
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
        synchronized (this) {
            handle.suspend(SelectionKey.OP_ACCEPT);
            suspended = true;
        }
    }

    public void resumeAccepts() {
        synchronized (this) {
            suspended = false;
            handle.resume(SelectionKey.OP_ACCEPT);
        }
    }

    public boolean isAcceptResumed() {
        return !suspended;
    }

    public void wakeupAccepts() {
        tcpServerLog.logf(FQCN, Logger.Level.TRACE, null, "Wake up accepts on %s", this);
        resumeAccepts();
        handle.wakeup(SelectionKey.OP_ACCEPT);
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

    void handleReady() {
        final SocketChannel accepted;
        try {
            accepted = channel.accept();
            if(suspendedDueToWatermark) {
                tcpServerLog.logf(FQCN, Logger.Level.DEBUG, null, "Exceeding connection high water limit (%s). Closing this new accepting request %s", getHighWater(connectionStatus), accepted);
                IoUtils.safeClose(accepted);
                return;
            }
        } catch (IOException e) {
            tcpServerLog.logf(FQCN, Logger.Level.DEBUG, e, "Exception accepting request, closing server channel %s", this);
            IoUtils.safeClose(channel);
            return;
        }
        try {
            boolean ok = false;
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
                ok = true;
                final int number = ioThread.getNumber();
                final BlockingQueue<SocketChannel> queue = acceptQueues.get(number);
                queue.add(accepted);
                // todo: only execute if necessary
                ioThread.execute(acceptTask);
                openConnections++;
                if(openConnections >= getHighWater(connectionStatus)) {
                    synchronized (QueuedNioTcpServer.this) {
                        suspendedDueToWatermark = true;
                        tcpServerLog.logf(FQCN, Logger.Level.DEBUG, null, "Total open connections reach high water limit (%s) by this new accepting request %s", getHighWater(connectionStatus), accepted);
                    }
                }
            } finally {
                if (! ok) safeClose(accepted);
            }
        } catch (IOException ignored) {
        }
    }

    public void connectionClosed() {
        thread.execute(connectionClosedTask);
    }
}
