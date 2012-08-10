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
import java.io.InterruptedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import org.jboss.logging.Logger;
import org.xnio.IoUtils;
import org.xnio.Option;
import org.xnio.ChannelListener;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.UnsupportedOptionException;

import static java.util.concurrent.locks.LockSupport.*;
import static org.xnio.Bits.*;

final class NioTcpServer extends AbstractNioChannel<NioTcpServer> implements AcceptingChannel<NioTcpChannel> {
    private static final Logger log = Logger.getLogger("org.xnio.nio.tcp.server");
    private static final String FQCN = NioTcpServer.class.getName();

    private final ChannelListener.SimpleSetter<NioTcpServer> acceptSetter = new ChannelListener.SimpleSetter<NioTcpServer>();

    private final List<NioHandle<NioTcpServer>> acceptHandles;

    private final ServerSocketChannel channel;
    private final ServerSocket socket;

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
    private volatile int readTimeout = 0;
    @SuppressWarnings("unused")
    private volatile int writeTimeout = 0;

    @SuppressWarnings("unused")
    private volatile Thread waitingThread;

    private static final int  CONN_MAX          = (1 << 20) - 1;
    private static final long CONN_COUNT_MASK   = longBitMask(0, 19);
    private static final long CONN_COUNT_BIT    = 0L;
    private static final long CONN_COUNT_ONE    = 1L << CONN_COUNT_BIT;
    private static final long CONN_LOW_MASK     = longBitMask(20, 39);
    private static final long CONN_LOW_BIT      = 20L;
    @SuppressWarnings("unused")
    private static final long CONN_LOW_ONE      = 1L << CONN_LOW_BIT;
    private static final long CONN_HIGH_MASK    = longBitMask(40, 59);
    private static final long CONN_HIGH_BIT     = 40L;
    @SuppressWarnings("unused")
    private static final long CONN_HIGH_ONE     = 1L << CONN_HIGH_BIT;
    private static final long CONN_SUSPENDING   = 1L << 60L;
    private static final long CONN_FULL         = 1L << 61L;
    private static final long CONN_RESUMED      = 1L << 62L;

    private static final AtomicIntegerFieldUpdater<NioTcpServer> keepAliveUpdater = AtomicIntegerFieldUpdater.newUpdater(NioTcpServer.class, "keepAlive");
    private static final AtomicIntegerFieldUpdater<NioTcpServer> oobInlineUpdater = AtomicIntegerFieldUpdater.newUpdater(NioTcpServer.class, "oobInline");
    private static final AtomicIntegerFieldUpdater<NioTcpServer> tcpNoDelayUpdater = AtomicIntegerFieldUpdater.newUpdater(NioTcpServer.class, "tcpNoDelay");
    private static final AtomicIntegerFieldUpdater<NioTcpServer> sendBufferUpdater = AtomicIntegerFieldUpdater.newUpdater(NioTcpServer.class, "sendBuffer");
    private static final AtomicIntegerFieldUpdater<NioTcpServer> readTimeoutUpdater = AtomicIntegerFieldUpdater.newUpdater(NioTcpServer.class, "readTimeout");
    private static final AtomicIntegerFieldUpdater<NioTcpServer> writeTimeoutUpdater = AtomicIntegerFieldUpdater.newUpdater(NioTcpServer.class, "writeTimeout");

    private static final AtomicLongFieldUpdater<NioTcpServer> connectionStatusUpdater = AtomicLongFieldUpdater.newUpdater(NioTcpServer.class, "connectionStatus");

    private static final AtomicReferenceFieldUpdater<NioTcpServer, Thread> waitingThreadUpdater = AtomicReferenceFieldUpdater.newUpdater(NioTcpServer.class, Thread.class, "waitingThread");

    NioTcpServer(final NioXnioWorker worker, final ServerSocketChannel channel, final OptionMap optionMap) throws IOException {
        super(worker);
        this.channel = channel;
        final boolean write = optionMap.get(Options.WORKER_ESTABLISH_WRITING, false);
        final int count = optionMap.get(Options.WORKER_ACCEPT_THREADS, 1);
        if (count < 0) {
            throw new IllegalArgumentException("Total of worker accept threads must be greater than or equal to 0");
        }
        final WorkerThread[] threads = worker.choose(count, write);
        @SuppressWarnings("unchecked")
        final NioHandle<NioTcpServer>[] handles = new NioHandle[threads.length];
        for (int i = 0, length = threads.length; i < length; i++) {
            handles[i] = threads[i].addChannel(channel, this, 0, acceptSetter);
        }
        //noinspection unchecked
        acceptHandles = Arrays.asList(handles);
        socket = channel.socket();
        if (optionMap.contains(Options.REUSE_ADDRESSES)) {
            socket.setReuseAddress(optionMap.get(Options.REUSE_ADDRESSES, false));
        }
        final int receiveBufferSize = optionMap.get(Options.RECEIVE_BUFFER, DEFAULT_BUFFER_SIZE);
        if (receiveBufferSize < 1) {
            throw new IllegalArgumentException("Receive buffer size must be greater than 0");
        }
        socket.setReceiveBufferSize(receiveBufferSize);
        final int sendBufferSize = optionMap.get(Options.SEND_BUFFER, DEFAULT_BUFFER_SIZE);
        if (sendBufferSize < 1) {
            throw new IllegalArgumentException("Send buffer size must be greater than 0");
        }
        sendBufferUpdater.set(this, sendBufferSize);
        if (optionMap.contains(Options.KEEP_ALIVE)) {
            keepAliveUpdater.set(this, optionMap.get(Options.KEEP_ALIVE, false) ? 1 : 0);
        }
        if (optionMap.contains(Options.TCP_OOB_INLINE)) {
            oobInlineUpdater.set(this, optionMap.get(Options.TCP_OOB_INLINE, false) ? 1 : 0);
        }
        if (optionMap.contains(Options.TCP_NODELAY)) {
            tcpNoDelayUpdater.set(this, optionMap.get(Options.TCP_NODELAY, false) ? 1 : 0);
        }
        if (optionMap.contains(Options.READ_TIMEOUT)) {
            readTimeoutUpdater.set(this, optionMap.get(Options.READ_TIMEOUT, 0));
        }
        if (optionMap.contains(Options.WRITE_TIMEOUT)) {
            writeTimeoutUpdater.set(this, optionMap.get(Options.WRITE_TIMEOUT, 0));
        }
        if (optionMap.contains(Options.CONNECTION_HIGH_WATER) || optionMap.contains(Options.CONNECTION_LOW_WATER)) {
            final int highWater = optionMap.get(Options.CONNECTION_HIGH_WATER, CONN_MAX);
            final int lowWater = optionMap.get(Options.CONNECTION_LOW_WATER, highWater);
            if (highWater <= 0 || highWater > CONN_MAX) {
                throw badHighWater();
            }
            if (lowWater <= 0 || lowWater > highWater) {
                throw badLowWater(highWater);
            }
            final long highLowWater = (long) highWater << CONN_HIGH_BIT | (long) lowWater << CONN_LOW_BIT;
            connectionStatusUpdater.set(this, highLowWater);
        }
    }

    private static IllegalArgumentException badLowWater(final int highWater) {
        return new IllegalArgumentException("Low water must be greater than 0 and less than or equal to high water (" + highWater + ")");
    }

    private static IllegalArgumentException badHighWater() {
        return new IllegalArgumentException("High water must be greater than 0 and less than or equal to " + CONN_MAX);
    }

    public void close() throws IOException {
        try {
            channel.close();
        } finally {
            for (NioHandle<NioTcpServer> handle : acceptHandles) {
                handle.cancelKey();
            }
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
            socket.setReuseAddress(Options.REUSE_ADDRESSES.cast(value, false).booleanValue());
        } else if (option == Options.RECEIVE_BUFFER) { 
            old = Integer.valueOf(socket.getReceiveBufferSize());
            final int newValue = Options.RECEIVE_BUFFER.cast(value, DEFAULT_BUFFER_SIZE).intValue();
            if (newValue < 1) {
                throw new IllegalArgumentException("Receive buffer size must be greater than 0");
            }
            socket.setReceiveBufferSize(newValue);
        } else if (option == Options.SEND_BUFFER) {
            final int newValue = Options.SEND_BUFFER.cast(value, DEFAULT_BUFFER_SIZE).intValue();
            if (newValue < 1) {
                throw new IllegalArgumentException("Send buffer size must be greater than 0");
            }
            final int oldValue = sendBufferUpdater.getAndSet(this, newValue);
            old = oldValue == -1 ? null : Integer.valueOf(oldValue);
        } else if (option == Options.KEEP_ALIVE) {
            old = Boolean.valueOf(keepAliveUpdater.getAndSet(this, Options.KEEP_ALIVE.cast(value, false).booleanValue() ? 1 : 0) != 0);
        } else if (option == Options.TCP_OOB_INLINE) {
            old = Boolean.valueOf(oobInlineUpdater.getAndSet(this, Options.TCP_OOB_INLINE.cast(value, false).booleanValue() ? 1 : 0) != 0);
        } else if (option == Options.TCP_NODELAY) {
            old = Boolean.valueOf(tcpNoDelayUpdater.getAndSet(this, Options.TCP_NODELAY.cast(value, false).booleanValue() ? 1 : 0) != 0);
        } else if (option == Options.READ_TIMEOUT) {
            old = Integer.valueOf(readTimeoutUpdater.getAndSet(this, Options.READ_TIMEOUT.cast(value, 0).intValue()));
        } else if (option == Options.WRITE_TIMEOUT) {
            old = Integer.valueOf(writeTimeoutUpdater.getAndSet(this, Options.WRITE_TIMEOUT.cast(value, 0).intValue()));
        } else if (option == Options.CONNECTION_HIGH_WATER) {
            old = Integer.valueOf(getHighWater(updateWaterMark(-1, Options.CONNECTION_HIGH_WATER.cast(value,  (int) (CONN_HIGH_MASK >> CONN_HIGH_BIT)).intValue())));
        } else if (option == Options.CONNECTION_LOW_WATER) {
            old = Integer.valueOf(getLowWater(updateWaterMark(Options.CONNECTION_LOW_WATER.cast(value, (int) (CONN_LOW_MASK >> CONN_LOW_BIT)).intValue(), -1)));
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
        int oldHighWater, oldLowWater, connCount;
        int newLowWater, newHighWater;
        do {
            oldVal = connectionStatus;
            oldLowWater = getLowWater(oldVal);
            oldHighWater = getHighWater(oldVal);
            connCount = getCount(oldVal);
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
            newVal = withLowWater(withHighWater(oldVal, newHighWater), newLowWater);
            // determine if we need to suspend because the high water line dropped below count
            //    ...or if we need to resume because the low water line rose above count
            if (allAreClear(oldVal, CONN_FULL) && oldHighWater > connCount && newHighWater <= connCount) {
                newVal |= CONN_FULL | CONN_SUSPENDING;
            } else if (allAreSet(oldVal, CONN_FULL) && oldLowWater < connCount && newLowWater >= connCount) {
                newVal &= ~CONN_FULL;
                newVal |= CONN_SUSPENDING;
            }
        } while (! connectionStatusUpdater.compareAndSet(this, oldVal, newVal));
        if (allAreSet(oldVal, CONN_FULL) && allAreClear(newVal, CONN_FULL)) {
            final Thread thread = waitingThreadUpdater.getAndSet(this, null);
            if (thread != null) {
                unpark(thread);
            }
        }
        if (allAreClear(oldVal, CONN_SUSPENDING) && allAreSet(newVal, CONN_SUSPENDING)) {
            // we have work to do...
            if (allAreSet(newVal, CONN_FULL)) {
                doResume(0);
                synchronizeConnectionState(newVal, true);
            } else {
                doResume(SelectionKey.OP_ACCEPT);
                synchronizeConnectionState(newVal, false);
            }
        }
        return oldVal;
    }

    private static int getHighWater(final long value) {
        return (int) ((value & CONN_HIGH_MASK) >> CONN_HIGH_BIT);
    }

    private static int getLowWater(final long value) {
        return (int) ((value & CONN_LOW_MASK) >> CONN_LOW_BIT);
    }

    private static int getCount(final long value) {
        return (int) ((value & CONN_COUNT_MASK) >> CONN_COUNT_BIT);
    }

    private static long withHighWater(final long oldValue, final int highWater) {
        return oldValue & ~CONN_HIGH_MASK | (long)highWater << CONN_HIGH_BIT;
    }

    private static long withLowWater(final long oldValue, final int lowWater) {
        return oldValue & ~CONN_LOW_MASK | (long)lowWater << CONN_LOW_BIT;
    }

    public NioTcpChannel accept() throws IOException {
        // This method changes the state of the CONN_SUSPENDING flag.
        // As such it is responsible to make sure that when the flag is cleared, the resume state accurately
        // reflects the state of the CONN_RESUMED and CONN_FULL flags.
        long oldVal, newVal;
        do {
            oldVal = connectionStatus;
            if (allAreSet(oldVal, CONN_FULL)) {
                log.trace("No connection accepted (full)");
                return null;
            }
            newVal = oldVal + CONN_COUNT_ONE;
            if (getCount(newVal) >= getHighWater(newVal)) {
                newVal |= CONN_SUSPENDING | CONN_FULL;
            }
        } while (! connectionStatusUpdater.compareAndSet(this, oldVal, newVal));
        boolean wasSuspended = allAreClear(oldVal, CONN_RESUMED);
        boolean doSuspend = ! wasSuspended && allAreClear(oldVal, CONN_SUSPENDING) && allAreSet(newVal, CONN_FULL | CONN_SUSPENDING);
        final SocketChannel accepted;
        try {
            accepted = channel.accept();
        } catch (IOException e) {
            undoAccept(newVal, wasSuspended, doSuspend);
            log.tracef("No connection accepted (%s)", e);
            return null;
        }
        final NioTcpChannel newChannel;
        if (accepted == null) {
            undoAccept(newVal, wasSuspended, doSuspend);
            log.trace("No connection accepted");
            return null;
        }
        boolean ok = false;
        try {
            accepted.configureBlocking(false);
            final Socket socket = accepted.socket();
            socket.setKeepAlive(keepAlive != 0);
            socket.setOOBInline(oobInline != 0);
            socket.setTcpNoDelay(tcpNoDelay != 0);

            final int sendBuffer = this.sendBuffer;
            if (sendBuffer > 0) socket.setSendBufferSize(sendBuffer);
            newChannel = new NioTcpChannel(worker, this, accepted);
            newChannel.start();
            newChannel.setOption(Options.READ_TIMEOUT, Integer.valueOf(readTimeout));
            newChannel.setOption(Options.WRITE_TIMEOUT, Integer.valueOf(writeTimeout));
            ok = true;
            log.trace("TCP server accepted connection");
        } finally {
            if (!ok) {
                log.trace("Failed to accept a connection, undoing");
                undoAccept(newVal, wasSuspended, doSuspend);
                IoUtils.safeClose(accepted);
            }
        }
        if (doSuspend) {
            // handle suspend
            assert allAreSet(oldVal, CONN_RESUMED);
            // we were previously resumed, so stop calling accept handlers for now
            doResume(0);
            // now attempt to synchronize the connection state with the new suspend state
            synchronizeConnectionState(oldVal, doSuspend);
        }
        return newChannel;
    }

    private void synchronizeConnectionState(long oldVal, boolean suspended) {
        long newVal;
        newVal = oldVal & ~CONN_SUSPENDING;
        while (!connectionStatusUpdater.compareAndSet(this, oldVal, newVal)) {
            oldVal = connectionStatus;
            // it's up to whoever increments or decrements connectionStatus to set or clear CONN_FULL
            if ((allAreClear(oldVal, CONN_FULL) && allAreSet(oldVal, CONN_RESUMED)) != suspended) {
                doResume((suspended = !suspended) ? 0 : SelectionKey.OP_ACCEPT);
            }
            newVal = oldVal & ~CONN_SUSPENDING;
        }
    }

    private void undoAccept(long newVal, final boolean wasSuspended, boolean doSuspend) {
        // re-synchronize the resume status of this channel
        // first assume that the value hasn't changed
        long oldVal = newVal;
        newVal = oldVal - CONN_COUNT_ONE;
        newVal &= ~(CONN_FULL | CONN_SUSPENDING);
        doSuspend = !doSuspend && !wasSuspended;
        while (! connectionStatusUpdater.compareAndSet(this, oldVal, newVal)) {
            // the value has changed - reevaluate everything necessary to resynchronize resume and decrement the count
            oldVal = connectionStatus;
            newVal = (oldVal - CONN_COUNT_ONE) & ~CONN_SUSPENDING;
            if (allAreSet(newVal, CONN_FULL) && (newVal & CONN_COUNT_MASK) >> CONN_COUNT_BIT <= (newVal & CONN_LOW_MASK) >> CONN_LOW_BIT) {
                // dropped below the line
                newVal &= ~CONN_FULL;
            }
            if ((allAreClear(newVal, CONN_FULL) && allAreSet(newVal, CONN_RESUMED)) != doSuspend) {
                doResume((doSuspend = ! doSuspend) ? 0 : SelectionKey.OP_ACCEPT);
            }
        }
        if (allAreSet(oldVal, CONN_FULL) && allAreClear(newVal, CONN_FULL)) {
            final Thread thread = waitingThreadUpdater.getAndSet(this, null);
            if (thread != null) {
                unpark(thread);
            }
        }
    }

    void channelClosed() {
        long oldVal, newVal;
        do {
            oldVal = connectionStatus;
            newVal = oldVal - CONN_COUNT_ONE;
            if (allAreSet(newVal, CONN_FULL) && (newVal & CONN_COUNT_MASK) >> CONN_COUNT_BIT <= (newVal & CONN_LOW_MASK) >> CONN_LOW_BIT) {
                // dropped below the line
                newVal &= ~CONN_FULL;
                if (allAreSet(newVal, CONN_RESUMED)) {
                    newVal |= CONN_SUSPENDING;
                }
            }
        } while (! connectionStatusUpdater.compareAndSet(this, oldVal, newVal));
        if (allAreSet(oldVal, CONN_FULL) && allAreClear(newVal, CONN_FULL)) {
            final Thread thread = waitingThreadUpdater.getAndSet(this, null);
            if (thread != null) {
                unpark(thread);
            }
        }
        if (allAreSet(oldVal, CONN_SUSPENDING) || allAreClear(newVal, CONN_SUSPENDING)) {
            // done - we either didn't change the full setting, or we did but someone already has the suspending status,
            // or the user doesn't want to resume anyway, so we don't need to do anything about it
            return;
        }
        // We attempt to resume at this point.
        boolean doSuspend = false;
        doResume(SelectionKey.OP_ACCEPT);
        oldVal = newVal;
        newVal &= ~CONN_SUSPENDING;
        while (! connectionStatusUpdater.compareAndSet(this, oldVal, newVal)) {
            // the value has changed - reevaluate everything necessary to resynchronize resume and decrement the count
            oldVal = connectionStatus;
            newVal = (oldVal - CONN_COUNT_ONE) & ~CONN_SUSPENDING;
            if (allAreSet(newVal, CONN_FULL) && (newVal & CONN_COUNT_MASK) >> CONN_COUNT_BIT <= (newVal & CONN_LOW_MASK) >> CONN_LOW_BIT) {
                // dropped below the line
                newVal &= ~CONN_FULL;
            }
            if ((allAreClear(newVal, CONN_FULL) && allAreSet(newVal, CONN_RESUMED)) != doSuspend) {
                doResume((doSuspend = ! doSuspend) ? 0 : SelectionKey.OP_ACCEPT);
            }
        }
    }

    public String toString() {
        return String.format("TCP server (NIO) <%s>", Integer.toHexString(hashCode()));
    }

    public ChannelListener.SimpleSetter<NioTcpServer> getAcceptSetter() {
        return acceptSetter;
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
        doResumeWithFlag(false);
    }

    public void resumeAccepts() {
        doResumeWithFlag(true);
    }

    private void doResumeWithFlag(boolean flag) {
        long oldVal, newVal;
        do {
            oldVal = connectionStatus;
            if (allAreSet(oldVal, CONN_RESUMED) == flag) {
                // idempotent call
                return;
            }
            newVal = oldVal ^ CONN_RESUMED | CONN_SUSPENDING;
        } while (! connectionStatusUpdater.compareAndSet(this, oldVal, newVal));
        if (anyAreSet(oldVal, CONN_SUSPENDING | CONN_FULL)) {
            // someone else is in charge of the suspending status, or we cannot resume anyway
            return;
        }
        // we are officially the suspending thread.
        oldVal = newVal;
        newVal = oldVal & ~CONN_SUSPENDING;
        doResume(flag ? SelectionKey.OP_ACCEPT : 0);
        if (connectionStatusUpdater.compareAndSet(this, oldVal, newVal)) {
            // done!  most normal invocations will terminate here
            return;
        }
        // at this point the status has changed from another thread.
        // the other thread may have called suspend/resume, accept, or a connection may have closed.
        // now we have to make sure the resume status of the NIO channel catches up to connectionStatus.
        do {
            oldVal = connectionStatus;
            if ((allAreSet(oldVal, CONN_RESUMED) && allAreClear(oldVal, CONN_FULL)) != flag) {
                // the resumed status has been toggled
                doResume((flag = !flag) ? SelectionKey.OP_ACCEPT : 0);
            }
            newVal = oldVal & ~CONN_SUSPENDING;
        } while (! connectionStatusUpdater.compareAndSet(this, oldVal, newVal));
        // we've successfully cleared the SUSPENDING flag while ensuring that at the time it was cleared, the resume
        // status is accurate.
    }

    private void doResume(final int op) {
        for (NioHandle<NioTcpServer> handle : acceptHandles) {
            handle.resume(op);
        }
    }

    public void wakeupAccepts() {
        log.logf(FQCN, Logger.Level.TRACE, null, "Wake up accepts on %s", this);
        resumeAccepts();
        final List<NioHandle<NioTcpServer>> handles = acceptHandles;
        final int len = handles.size();
        if (len == 0) {
            throw new IllegalArgumentException("No thread configured");
        }
        final int idx = IoUtils.getThreadLocalRandom().nextInt(len);
        acceptHandles.get(idx).execute();
    }

    public void awaitAcceptable() throws IOException {
        // We need to park if there are no new accepts pending.  Else we need to wait properly.
        final Thread thread = Thread.currentThread();
        long val = connectionStatus;
        for (;;) {
            while (allAreSet(val, CONN_FULL)) {
                final Thread nextThread = waitingThreadUpdater.getAndSet(this, thread);
                try {
                    // if it's still set after we've registered...
                    if (allAreSet(connectionStatus, CONN_FULL)) {
                        park(this);
                    }
                } finally {
                    if (nextThread != null) {
                        unpark(nextThread);
                    }
                }
                if (thread.isInterrupted()) {
                    throw new InterruptedIOException();
                }
                val = connectionStatus;
            }
            SelectorUtils.await(worker.getXnio(), channel, SelectionKey.OP_ACCEPT);
            if (allAreClear(val = connectionStatus, CONN_FULL)) {
                return;
            }
        }
    }

    public void awaitAcceptable(final long time, final TimeUnit timeUnit) throws IOException {
        long then = System.nanoTime();
        long now;
        long duration = timeUnit.toNanos(time);
        final Thread thread = Thread.currentThread();
        long val = connectionStatus;
        for (;;) {
            while (allAreSet(val, CONN_FULL)) {
                final Thread nextThread = waitingThreadUpdater.getAndSet(this, thread);
                try {
                    // if it's still set after we've registered...
                    if (allAreSet(connectionStatus, CONN_FULL)) {
                        parkNanos(this, duration);
                    }
                } finally {
                    if (nextThread != null) {
                        unpark(nextThread);
                    }
                }
                if (thread.isInterrupted()) {
                    throw new InterruptedIOException();
                }
                val = connectionStatus;
                duration -= (now = System.nanoTime()) - then;
                then = now;
                if (duration <= 0L) {
                    return;
                }
            }
            SelectorUtils.await(worker.getXnio(), channel, SelectionKey.OP_ACCEPT, duration, TimeUnit.NANOSECONDS);
            if (allAreClear(val = connectionStatus, CONN_FULL)) {
                return;
            }
            duration -= (now = System.nanoTime()) - then;
            then = now;
            if (duration <= 0L) {
                return;
            }
        }
    }

    void migrateTo(final NioXnioWorker worker) throws ClosedChannelException {
        boolean ok = false;
        final WorkerThread acceptThread = worker.choose(true);
        try {
            final List<NioHandle<NioTcpServer>> handles = acceptHandles;
            for (int i = 0; i < handles.size(); i++) {
                NioHandle<NioTcpServer> oldHandle = handles.get(i);
                oldHandle.cancelKey();
                handles.set(i, acceptThread.addChannel(channel, typed(), 0, acceptSetter));
            }
            ok = true;
        } finally {
            if (! ok) {
                IoUtils.safeClose(this);
            } else {
                super.migrateTo(worker);
            }
        }
    }
}
