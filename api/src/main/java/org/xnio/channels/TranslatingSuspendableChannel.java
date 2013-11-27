/*
 * JBoss, Home of Professional Open Source
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

package org.xnio.channels;

import static java.lang.Thread.currentThread;
import static java.util.concurrent.locks.LockSupport.park;
import static java.util.concurrent.locks.LockSupport.parkNanos;
import static java.util.concurrent.locks.LockSupport.unpark;
import static org.xnio.Bits.allAreClear;
import static org.xnio.Bits.allAreSet;
import static org.xnio.Bits.anyAreSet;
import static org.xnio.Bits.intBitMask;
import static org.xnio._private.Messages.msg;

import java.io.IOException;
import java.nio.channels.Channel;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Option;
import org.xnio.XnioExecutor;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;

/**
 * An abstract wrapped channel.
 *
 * @param <C> the channel type implemented by this class
 * @param <W> the channel type being wrapped by this class
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 *
 * @deprecated This class is deprecated; use conduits instead.
 */
@Deprecated
public abstract class TranslatingSuspendableChannel<C extends SuspendableChannel, W extends SuspendableChannel> implements SuspendableChannel, WrappedChannel<W>, ReadListenerSettable<C>, WriteListenerSettable<C>, CloseListenerSettable<C> {

    /**
     * The wrapped channel.
     */
    protected final W channel;

    private ChannelListener<? super C> readListener;
    private ChannelListener<? super C> writeListener;
    private ChannelListener<? super C> closeListener;

    @SuppressWarnings("unused")
    private volatile int state;
    @SuppressWarnings("unused")
    private volatile Thread readWaiter;
    @SuppressWarnings("unused")
    private volatile Thread writeWaiter;

    @SuppressWarnings("rawtypes")
    private static final AtomicIntegerFieldUpdater<TranslatingSuspendableChannel> stateUpdater = AtomicIntegerFieldUpdater.newUpdater(TranslatingSuspendableChannel.class, "state");

    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<TranslatingSuspendableChannel, Thread> readWaiterUpdater = AtomicReferenceFieldUpdater.newUpdater(TranslatingSuspendableChannel.class, Thread.class, "readWaiter");
    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<TranslatingSuspendableChannel, Thread> writeWaiterUpdater = AtomicReferenceFieldUpdater.newUpdater(TranslatingSuspendableChannel.class, Thread.class, "writeWaiter");

    // read-side

    private static final int READ_REQUESTED         = 1 << 0x00; // user wants to be notified on read
    private static final int READ_REQUIRES_WRITE    = 1 << 0x01; // channel cannot be read due to pending write
    private static final int READ_READY             = 1 << 0x02; // channel is always ready to be read
    private static final int READ_SHUT_DOWN         = 1 << 0x03; // user shut down reads

    private static final int READ_REQUIRES_EXT      = intBitMask(0x0B, 0x0F); // channel cannot be read until external event completes, up to 31 events
    private static final int READ_SINGLE_EXT        = 1 << 0x0B; // one external event count value

    @SuppressWarnings("unused")
    private static final int READ_FLAGS             = intBitMask(0x00, 0x0F);

    // write-side

    private static final int WRITE_REQUESTED        = 1 << 0x10;
    private static final int WRITE_REQUIRES_READ    = 1 << 0x11;
    private static final int WRITE_READY            = 1 << 0x12;
    private static final int WRITE_SHUT_DOWN        = 1 << 0x13; // user requested shut down of writes
    private static final int WRITE_COMPLETE         = 1 << 0x14; // flush acknowledged full write shutdown

    private static final int WRITE_REQUIRES_EXT     = intBitMask(0x1B, 0x1F); // up to 32 events
    private static final int WRITE_SINGLE_EXT       = 1 << 0x1B;

    @SuppressWarnings("unused")
    private static final int WRITE_FLAGS            = intBitMask(0x10, 0x1F);

    private final ChannelListener<Channel> delegateReadListener = new ChannelListener<Channel>() {
        public void handleEvent(final Channel channel) {
            handleReadable();
        }

        public String toString() {
            return "Read listener for " + TranslatingSuspendableChannel.this;
        }
    };

    private final ChannelListener<Channel> delegateWriteListener = new ChannelListener<Channel>() {
        public void handleEvent(final Channel channel) {
            handleWritable();
        }

        public String toString() {
            return "Write listener for " + TranslatingSuspendableChannel.this;
        }
    };

    private final ChannelListener<Channel> delegateCloseListener = new ChannelListener<Channel>() {
        public void handleEvent(final Channel channel) {
            IoUtils.safeClose(TranslatingSuspendableChannel.this);
        }

        public String toString() {
            return "Close listener for " + TranslatingSuspendableChannel.this;
        }
    };

    /**
     * Construct a new instance.
     *
     * @param channel the channel being wrapped
     */
    protected TranslatingSuspendableChannel(final W channel) {
        if (channel == null) {
            throw msg.nullParameter("channel");
        }
        this.channel = channel;
        channel.getReadSetter().set(delegateReadListener);
        channel.getWriteSetter().set(delegateWriteListener);
        channel.getCloseSetter().set(delegateCloseListener);
    }

    /**
     * Called when the underlying channel is readable.
     */
    protected void handleReadable() {
        int oldState;
        oldState = clearFlags(WRITE_REQUIRES_READ);
        if (allAreSet(oldState, WRITE_REQUIRES_READ)) {
            unparkWriteWaiters();
            if (allAreSet(oldState, WRITE_REQUESTED)) {
                channel.wakeupWrites();
            }
        }
        if (allAreClear(oldState, READ_READY) && anyAreSet(oldState, READ_REQUIRES_WRITE | READ_REQUIRES_EXT)) {
            channel.suspendReads();
            oldState = state;
            if (anyAreSet(oldState, READ_READY) || allAreClear(oldState, READ_REQUIRES_WRITE | READ_REQUIRES_EXT)) {
                // undo
                channel.resumeReads();
            } else {
                return;
            }
        }
        do {
            if (anyAreSet(oldState, READ_SHUT_DOWN)) {
                channel.suspendReads();
                return;
            }
            if (allAreClear(oldState, READ_REQUESTED)) {
                channel.suspendReads();
                oldState = state;
                if (allAreSet(oldState, READ_REQUESTED)) {
                    // undo
                    channel.resumeReads();
                } else {
                    return;
                }
            }
            unparkReadWaiters();
            final ChannelListener<? super C> listener = readListener;
            if (listener == null) {
                // damage control
                oldState = clearFlag(READ_REQUESTED | WRITE_REQUIRES_READ) & ~READ_REQUESTED;
            } else {
                ChannelListeners.invokeChannelListener(thisTyped(), listener);
                oldState = clearFlags(WRITE_REQUIRES_READ);
            }
            if (allAreSet(oldState, WRITE_REQUIRES_READ)) {
                unparkWriteWaiters();
                // race is OK
                channel.wakeupWrites();
            }
        } while (allAreSet(oldState, READ_READY));
    }

    /**
     * Called when the underlying channel is writable.
     */
    protected void handleWritable() {
        int oldState;
        oldState = clearFlags(READ_REQUIRES_WRITE);
        if (allAreSet(oldState, READ_REQUIRES_WRITE)) {
            unparkReadWaiters();
            if (allAreSet(oldState, READ_REQUESTED)) {
                channel.wakeupReads();
            }
        }
        if (allAreClear(oldState, WRITE_READY) && anyAreSet(oldState, WRITE_REQUIRES_READ | WRITE_REQUIRES_EXT)) {
            channel.suspendWrites();
            oldState = state;
            if (anyAreSet(oldState, WRITE_READY) || allAreClear(oldState, WRITE_REQUIRES_READ | WRITE_REQUIRES_EXT)) {
                // undo
                channel.resumeWrites();
            } else {
                return;
            }
        }
        do {
            if (anyAreSet(oldState, WRITE_COMPLETE)) {
                channel.suspendWrites();
                return;
            }
            if (allAreClear(oldState, WRITE_REQUESTED)) {
                channel.suspendWrites();
                oldState = state;
                if (allAreSet(oldState, WRITE_REQUESTED)) {
                    // undo
                    channel.resumeWrites();
                } else {
                    return;
                }
            }
            unparkWriteWaiters();
            final ChannelListener<? super C> listener = writeListener;
            if (listener == null) {
                // damage control
                oldState = clearFlags(WRITE_REQUESTED | READ_REQUIRES_WRITE) & ~WRITE_REQUESTED;
            } else {
                ChannelListeners.invokeChannelListener(thisTyped(), listener);
                oldState = clearFlags(READ_REQUIRES_WRITE);
            }
            if (allAreSet(oldState, READ_REQUIRES_WRITE)) {
                unparkReadWaiters();
                // race is OK
                channel.wakeupReads();
            }
        } while (allAreSet(oldState, WRITE_READY));
    }

    /**
     * Called when the underlying channel is closed.
     *
     */
    protected void handleClosed() {
        ChannelListeners.invokeChannelListener(thisTyped(), closeListener);
    }

    // --- read ---

    /**
     * Indicate that the channel is definitely immediately readable, regardless of the underlying channel state.
     */
    protected void setReadReady() {
        int oldState = setFlags(READ_READY);
        unparkReadWaiters();
        if (allAreSet(oldState, READ_READY)) {
            // idempotent
            return;
        }
        if (allAreSet(oldState, READ_REQUESTED) && anyAreSet(oldState, READ_REQUIRES_EXT | READ_REQUIRES_WRITE)) {
            // wakeup is required to proceed
            channel.wakeupReads();
        }
    }

    /**
     * Indicate that the channel is no longer definitely immediately readable.
     */
    protected void clearReadReady() {
        int oldState = clearFlags(READ_READY);
        if (allAreClear(oldState, READ_READY)) {
            // idempotent
            return;
        }
        if (!allAreClear(oldState, READ_REQUESTED) && !anyAreSet(oldState, READ_REQUIRES_EXT | READ_REQUIRES_WRITE)) {
            // we can read again when the underlying channel is ready
            channel.resumeReads();
        }
    }

    /**
     * Indicate that the channel will not be readable until the write handler is called.
     */
    protected void setReadRequiresWrite() {
        int oldState = setFlags(READ_REQUIRES_WRITE);
        if (allAreSet(oldState, READ_REQUIRES_WRITE)) {
            // not the first caller
            return;
        }
        if (allAreClear(oldState, READ_READY | READ_REQUIRES_EXT)) {
            // read cannot proceed until write does
            channel.resumeWrites();
        }
    }

    /**
     * Indicate if the channel is not readable until the write handler is called.
     */
    protected boolean readRequiresWrite() {
        return allAreSet(state, READ_REQUIRES_WRITE);
    }

    /**
     * Indicate that the channel no longer requires writability for reads to proceed.
     */
    protected void clearReadRequiresWrite() {
        int oldState = clearFlags(READ_REQUIRES_WRITE);
        if (allAreClear(oldState, READ_REQUIRES_WRITE)) {
            // idempotent
            return;
        }
        if (allAreClear(oldState, READ_REQUIRES_EXT) && allAreSet(oldState, READ_REQUESTED)) {
            if (allAreSet(oldState, READ_READY)) {
                channel.wakeupReads();
            } else {
                channel.resumeReads();
            }
        }
    }

    /**
     * Indicate that read requires an external task to complete.
     *
     * @return {@code true} if the flag was set, {@code false} if too many tasks are already outstanding
     */
    protected boolean tryAddReadRequiresExternal() {
        int oldState = addFlag(READ_REQUIRES_EXT, READ_SINGLE_EXT);
        return (oldState & READ_REQUIRES_EXT) != READ_REQUIRES_EXT;
    }

    /**
     * Indicate that one external read task was completed.  This method should be called once for every time
     * that {@link #tryAddReadRequiresExternal()} returned {@code true}.
     */
    protected void removeReadRequiresExternal() {
        clearFlag(READ_SINGLE_EXT);
    }

    /**
     * Set the channel read shut down flag.
     *
     * @return {@code true} if the channel has fully closed due to this call, {@code false} otherwise
     */
    protected boolean setReadShutDown() {
        return (setFlags(READ_SHUT_DOWN) & (READ_SHUT_DOWN | WRITE_SHUT_DOWN)) == WRITE_SHUT_DOWN;
    }

    // --- write ---

    /**
     * Indicate that the channel is definitely immediately writable, regardless of the underlying channel state.
     */
    protected void setWriteReady() {
        int oldState = setFlags(WRITE_READY);
        unparkWriteWaiters();
        if (allAreSet(oldState, WRITE_READY)) {
            // idempotent
            return;
        }
        if (allAreSet(oldState, WRITE_REQUESTED) && anyAreSet(oldState, WRITE_REQUIRES_EXT | WRITE_REQUIRES_READ)) {
            // wakeup is required to proceed
            channel.wakeupWrites();
        }
    }

    /**
     * Indicate that the channel is no longer definitely immediately writable.
     */
    protected void clearWriteReady() {
        int oldState = clearFlags(WRITE_READY);
        if (allAreClear(oldState, WRITE_READY)) {
            // idempotent
            return;
        }
        if (!allAreClear(oldState, WRITE_REQUESTED) && ! anyAreSet(oldState, WRITE_REQUIRES_EXT | WRITE_REQUIRES_READ)) {
            // we can write again when the underlying channel is ready
            channel.resumeWrites();
        }
    }

    /**
     * Indicate that the channel will not be writable until the read handler is called.
     */
    protected void setWriteRequiresRead() {
        int oldState = setFlags(WRITE_REQUIRES_READ);
        if (allAreSet(oldState, WRITE_REQUIRES_READ)) {
            // not the first caller
            return;
        }
        if (allAreClear(oldState, WRITE_READY | WRITE_REQUIRES_EXT)) {
            // write cannot proceed until read does
            channel.resumeReads();
        }
    }

    /**
     * Indicate if the channel is not writable until the read handler is called.
     */
    protected boolean writeRequiresRead() {
        return allAreSet(state, WRITE_REQUIRES_READ);
    }

    /**
     * Indicate that the channel no longer requires writability for writes to proceed.
     */
    protected void clearWriteRequiresRead() {
        int oldState = clearFlags(WRITE_REQUIRES_READ);
        if (allAreClear(oldState, WRITE_REQUIRES_READ)) {
            // idempotent
            return;
        }
        if (allAreClear(oldState, WRITE_REQUIRES_EXT) && allAreSet(oldState, WRITE_REQUESTED)) {
            if (allAreSet(oldState, WRITE_READY)) {
                channel.wakeupWrites();
            } else {
                channel.resumeWrites();
            }
        }
    }

    /**
     * Indicate that write requires an external task to complete.
     *
     * @return {@code true} if the flag was set, {@code false} if too many tasks are already outstanding
     */
    protected boolean tryAddWriteRequiresExternal() {
        int oldState = addFlag(WRITE_REQUIRES_EXT, WRITE_SINGLE_EXT);
        return (oldState & WRITE_REQUIRES_EXT) != WRITE_REQUIRES_EXT;
    }

    /**
     * Indicate that one external write task was completed.  This method should be called once for every time
     * that {@link #tryAddWriteRequiresExternal()} returned {@code true}.
     */
    protected void removeWriteRequiresExternal() {
        clearFlag(WRITE_SINGLE_EXT);
    }

    /**
     * Set the channel write shut down flag.
     *
     * @return {@code true} if the channel has fully closed due to this call, {@code false} otherwise
     */
    protected boolean setWriteShutDown() {
        return (setFlags(WRITE_SHUT_DOWN) & (WRITE_SHUT_DOWN | READ_SHUT_DOWN)) == READ_SHUT_DOWN;
    }

    // --- read & write ---

    /**
     * Set both the channel read and write shut down flags.
     *
     * @return {@code true} if the channel has fully closed (for the first time) due to this call, {@code false} otherwise
     */
    protected boolean setClosed() {
        return (setFlags(READ_SHUT_DOWN | WRITE_SHUT_DOWN) & (READ_SHUT_DOWN | WRITE_SHUT_DOWN)) != (READ_SHUT_DOWN | WRITE_SHUT_DOWN);
    }

    /**
     * Get this channel, cast to the implemented channel type.
     *
     * @return {@code this}
     */
    @SuppressWarnings("unchecked")
    protected final C thisTyped() {
        return (C) this;
    }

    public void setReadListener(final ChannelListener<? super C> readListener) {
        this.readListener = readListener;
    }

    public ChannelListener<? super C> getReadListener() {
        return readListener;
    }

    public void setWriteListener(final ChannelListener<? super C> writeListener) {
        this.writeListener = writeListener;
    }

    public ChannelListener<? super C> getWriteListener() {
        return writeListener;
    }

    public void setCloseListener(final ChannelListener<? super C> closeListener) {
        this.closeListener = closeListener;
    }

    public ChannelListener<? super C> getCloseListener() {
        return closeListener;
    }

    /** {@inheritDoc} */
    public ChannelListener.Setter<C> getCloseSetter() {
        return new CloseListenerSettable.Setter<C>(this);
    }

    /** {@inheritDoc} */
    public ChannelListener.Setter<C> getReadSetter() {
        return new ReadListenerSettable.Setter<C>(this) ;
    }

    /** {@inheritDoc} */
    public ChannelListener.Setter<C> getWriteSetter() {
        return new WriteListenerSettable.Setter<C>(this);
    }

    /** {@inheritDoc} */
    public void suspendReads() {
        clearFlags(READ_REQUESTED);
        // let the read/write handler actually suspend, to avoid awkward race conditions
    }

    /** {@inheritDoc} */
    public void resumeReads() {
        final int oldState = setFlags(READ_REQUESTED);
        if (anyAreSet(oldState, READ_REQUESTED | READ_SHUT_DOWN)) {
            // idempotent or shut down, either way
            return;
        }
        if (allAreSet(oldState, READ_READY)) {
            // reads are known to be ready so trigger listener right away
            channel.wakeupReads();
            return;
        }
        if (allAreClear(oldState, READ_REQUIRES_EXT)) {
            if (allAreSet(oldState, READ_REQUIRES_WRITE)) {
                channel.resumeWrites();
            } else {
                channel.resumeReads();
            }
        }
    }

    public boolean isReadResumed() {
        return allAreSet(state, READ_REQUESTED);
    }

    /** {@inheritDoc} */
    public void wakeupReads() {
        if (anyAreSet(state, READ_SHUT_DOWN)) {
            return;
        }
        setFlags(READ_REQUESTED);
        channel.wakeupReads();
    }

    /** {@inheritDoc} */
    public void suspendWrites() {
        clearFlags(WRITE_REQUESTED);
        // let the read/write handler actually suspend, to avoid awkward race conditions
    }

    /** {@inheritDoc} */
    public void resumeWrites() {
        final int oldState = setFlags(WRITE_REQUESTED);
        if (anyAreSet(oldState, WRITE_REQUESTED | WRITE_COMPLETE)) {
            // idempotent or shut down, either way
            return;
        }
        if (allAreSet(oldState, WRITE_READY)) {
            // reads are known to be ready so trigger listener right away
            channel.wakeupWrites();
            return;
        }
        if (allAreClear(oldState, WRITE_REQUIRES_EXT)) {
            if (allAreSet(oldState, WRITE_REQUIRES_READ)) {
                channel.resumeReads();
            } else {
                channel.resumeWrites();
            }
        }
    }

    public boolean isWriteResumed() {
        return allAreSet(state, WRITE_REQUESTED);
    }

    /** {@inheritDoc} */
    public void wakeupWrites() {
        if (anyAreSet(state, WRITE_SHUT_DOWN)) {
            return;
        }
        setFlags(WRITE_REQUESTED);
        channel.wakeupWrites();
        unparkWriteWaiters();
    }

    /** {@inheritDoc} */
    public boolean supportsOption(final Option<?> option) {
        return channel.supportsOption(option);
    }

    /** {@inheritDoc} */
    public <T> T getOption(final Option<T> option) throws IOException {
        return channel.getOption(option);
    }

    /** {@inheritDoc} */
    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        return channel.setOption(option, value);
    }

    /**
     * Perform channel flush.  To change the action taken to flush, subclasses should override {@link #flushAction(boolean)}.
     *
     * @return {@code true} if the flush completed, or {@code false} if the operation would block
     * @throws IOException if an error occurs
     */
    public final boolean flush() throws IOException {
        int oldState, newState;
        oldState = stateUpdater.get(this);
        if (allAreSet(oldState, WRITE_COMPLETE)) {
            return channel.flush();
        }
        final boolean shutDown = allAreSet(oldState, WRITE_SHUT_DOWN);
        if (! flushAction(shutDown)) {
            return false;
        }
        if (! shutDown) {
            return true;
        }
        newState = oldState | WRITE_COMPLETE;
        while (! stateUpdater.compareAndSet(this, oldState, newState)) {
            oldState = stateUpdater.get(this);
            if (allAreSet(oldState, WRITE_COMPLETE)) {
                return channel.flush();
            }
            newState = oldState | WRITE_COMPLETE;
        }
        final boolean readShutDown = allAreSet(oldState, READ_SHUT_DOWN);
        try {
            shutdownWritesComplete(readShutDown);
        } finally {
            if (readShutDown) ChannelListeners.invokeChannelListener(thisTyped(), closeListener);
        }
        return channel.flush();
    }

    /**
     * The action to perform when the channel is flushed.  By default, this method delegates to the underlying channel.
     * If the {@code shutDown} parameter is set, and this method returns {@code true}, the underlying channel will be
     * shut down and this method will never be called again (future calls to {@link #flush()} will flush the underlying
     * channel until it returns {@code true}).
     *
     * @param shutDown {@code true} if the channel's write side has been shut down, {@code false} otherwise
     * @return {@code true} if the flush succeeded, {@code false} if it would block
     * @throws IOException if an error occurs
     */
    protected boolean flushAction(final boolean shutDown) throws IOException {
        return channel.flush();
    }

    /**
     * Notification that the channel has successfully flushed after having shut down writes.  The underlying
     * channel may not yet be fully flushed at this time.
     *
     * @param readShutDown {@code true} if the read side was already shut down, {@code false} otherwise
     * @throws IOException if an error occurs
     */
    protected void shutdownWritesComplete(final boolean readShutDown) throws IOException {
    }

    /**
     * Perform the read shutdown action if it hasn't been performed already.
     *
     * @throws IOException if an I/O error occurs
     */
    public void shutdownReads() throws IOException {
        int old = setFlags(READ_SHUT_DOWN);
        if (allAreClear(old, READ_SHUT_DOWN)) {
            final boolean writeComplete = allAreSet(old, WRITE_COMPLETE);
            try {
                shutdownReadsAction(writeComplete);
            } finally {
                if (writeComplete) {
                    ChannelListeners.invokeChannelListener(thisTyped(), closeListener);
                }
            }
        }
    }

    /**
     * The action to perform when reads are shut down.  By default, this method delegates to the underlying channel.
     *
     * @throws IOException if an error occurs
     * @param writeComplete
     */
    protected void shutdownReadsAction(final boolean writeComplete) throws IOException {
        channel.shutdownReads();
    }

    /**
     * Determine whether the channel is shut down for reads.
     *
     * @return whether the channel is shut down for reads
     */
    protected boolean isReadShutDown() {
        return allAreSet(state, READ_SHUT_DOWN);
    }

    /**
     * Perform the write shutdown action if it hasn't been performed already.
     *
     * @throws IOException if an I/O error occurs
     */
    public void shutdownWrites() throws IOException {
        int old = setFlags(WRITE_SHUT_DOWN);
        if (allAreClear(old, WRITE_SHUT_DOWN)) {
            shutdownWritesAction();
        }
    }

    /**
     * The action to perform when writes are requested to be shut down.  By default, this method delegates to the
     * underlying channel.
     *
     * @throws IOException if an error occurs
     */
    protected void shutdownWritesAction() throws IOException {
        channel.shutdownWrites();
    }

    /**
     * Determine whether the channel is shut down for writes.
     *
     * @return whether the channel is shut down for writes
     */
    protected boolean isWriteShutDown() {
        return allAreSet(state, WRITE_SHUT_DOWN);
    }

    protected boolean isWriteComplete() {
        return allAreSet(state, WRITE_COMPLETE);
    }

    /** {@inheritDoc} */
    public void awaitReadable() throws IOException {
        int oldState = state;
        if (anyAreSet(oldState, READ_READY | READ_SHUT_DOWN)) {
            return;
        }
        final Thread thread = currentThread();
        final Thread next = readWaiterUpdater.getAndSet(this, thread);
        try {
            if (anyAreSet(oldState = state, READ_READY | READ_SHUT_DOWN)) {
                return;
            }
            if (allAreSet(oldState, READ_REQUIRES_WRITE)) {
                channel.resumeWrites();
            } else {
                channel.resumeReads();
            }
            park(this);
            if (thread.isInterrupted()) {
                throw msg.interruptedIO();
            }
        } finally {
            // always unpark because we cannot know if our awaken was spurious
            if (next != null) unpark(next);
        }
    }

    /** {@inheritDoc} */
    public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
        int oldState = state;
        if (anyAreSet(oldState, READ_READY | READ_SHUT_DOWN)) {
            return;
        }
        final Thread thread = currentThread();
        final Thread next = readWaiterUpdater.getAndSet(this, thread);
        long duration = timeUnit.toNanos(time);
        try {
            if (anyAreSet(oldState = state, READ_READY | READ_SHUT_DOWN)) {
                return;
            }
            if (allAreSet(oldState, READ_REQUIRES_WRITE)) {
                channel.resumeWrites();
            } else {
                channel.resumeReads();
            }
            parkNanos(this, duration);
            if (thread.isInterrupted()) {
                throw msg.interruptedIO();
            }
        } finally {
            // always unpark because we cannot know if our awaken was spurious
            if (next != null) unpark(next);
        }
    }

    @Deprecated
    public XnioExecutor getReadThread() {
        return channel.getReadThread();
    }

    /** {@inheritDoc} */
    public void awaitWritable() throws IOException {
        int oldState = state;
        if (anyAreSet(oldState, WRITE_READY | WRITE_SHUT_DOWN)) {
            return;
        }
        final Thread thread = currentThread();
        final Thread next = writeWaiterUpdater.getAndSet(this, thread);
        try {
            if (anyAreSet(oldState = state, WRITE_READY | WRITE_SHUT_DOWN)) {
                return;
            }
            if (allAreSet(oldState, WRITE_REQUIRES_READ)) {
                channel.resumeReads();
            } else {
                channel.resumeWrites();
            }
            park(this);
            if (thread.isInterrupted()) {
                throw msg.interruptedIO();
            }
        } finally {
            // always unpark because we cannot know if our awaken was spurious
            if (next != null) unpark(next);
        }
    }

    /** {@inheritDoc} */
    public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
        int oldState = state;
        if (anyAreSet(oldState, WRITE_READY | WRITE_SHUT_DOWN)) {
            return;
        }
        final Thread thread = currentThread();
        final Thread next = writeWaiterUpdater.getAndSet(this, thread);
        long duration = timeUnit.toNanos(time);
        try {
            if (anyAreSet(oldState = state, WRITE_READY | WRITE_SHUT_DOWN)) {
                return;
            }
            if (allAreSet(oldState, WRITE_REQUIRES_READ)) {
                channel.resumeReads();
            } else {
                channel.resumeWrites();
            }
            parkNanos(this, duration);
            if (thread.isInterrupted()) {
                throw msg.interruptedIO();
            }
        } finally {
            // always unpark because we cannot know if our awaken was spurious
            if (next != null) unpark(next);
        }
    }

    private void unparkReadWaiters() {
        final Thread waiter = readWaiterUpdater.getAndSet(this, null);
        if (waiter != null) {
            unpark(waiter);
        }
    }

    private void unparkWriteWaiters() {
        final Thread waiter = writeWaiterUpdater.getAndSet(this, null);
        if (waiter != null) {
            unpark(waiter);
        }
    }

    @Deprecated
    public XnioExecutor getWriteThread() {
        return channel.getWriteThread();
    }

    /**
     * Close this channel.  This method is idempotent.
     *
     * @throws IOException if an I/O error occurs
     */
    public void close() throws IOException {
        int old = setFlags(READ_SHUT_DOWN | WRITE_SHUT_DOWN | WRITE_COMPLETE);
        final boolean readShutDown = allAreSet(old, READ_SHUT_DOWN), writeShutDown = allAreSet(old, WRITE_COMPLETE);
        if (! (readShutDown && writeShutDown)) try {
            closeAction(readShutDown, writeShutDown);
        } finally {
            ChannelListeners.invokeChannelListener(thisTyped(), closeListener);
        }
    }

    /**
     * The action to perform when the channel is closed via the {@link #close()} method.  By default, the underlying
     * channel is closed.
     *
     * @param readShutDown if reads were previously shut down
     * @param writeShutDown if writes were previously shut down
     * @throws IOException if an error occurs
     */
    protected void closeAction(final boolean readShutDown, final boolean writeShutDown) throws IOException {
        channel.close();
    }

    /** {@inheritDoc} */
    public boolean isOpen() {
        return ! allAreSet(state, READ_SHUT_DOWN | WRITE_COMPLETE);
    }

    /** {@inheritDoc} */
    public W getChannel() {
        return channel;
    }

    /** {@inheritDoc} */
    public XnioWorker getWorker() {
        return channel.getWorker();
    }

    /** {@inheritDoc} */
    public XnioIoThread getIoThread() {
        return channel.getIoThread();
    }

    /** {@inheritDoc} */
    public String toString() {
        return getClass().getName() + " around " + channel;
    }

    // state operations

    private int setFlags(int flags) {
        int oldState;
        do {
            oldState = state;
            if ((oldState & flags) == flags) {
                return oldState;
            }
        } while (! stateUpdater.compareAndSet(this, oldState, oldState | flags));
        return oldState;
    }

    private int clearFlags(int flags) {
        int oldState;
        do {
            oldState = state;
            if ((oldState & flags) == 0) {
                return oldState;
            }
        } while (! stateUpdater.compareAndSet(this, oldState, oldState & ~flags));
        return oldState;
    }

    private int addFlag(final int mask, final int count) {
        int oldState;
        do {
            oldState = state;
            if ((oldState & mask) == mask) {
                return oldState;
            }
        } while (! stateUpdater.compareAndSet(this, oldState, oldState + count));
        return oldState;
    }

    private int clearFlag(final int count) {
        return stateUpdater.getAndAdd(this, -count);
    }
}
