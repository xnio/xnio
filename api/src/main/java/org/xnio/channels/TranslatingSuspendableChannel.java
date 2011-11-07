/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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

package org.xnio.channels;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.Option;
import org.xnio.XnioExecutor;
import org.xnio.XnioWorker;

/**
 * An abstract wrapped channel.
 *
 * @param <C> the channel type implemented by this class
 * @param <W> the channel type being wrapped by this class
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@SuppressWarnings("unused")
public abstract class TranslatingSuspendableChannel<C extends SuspendableChannel, W extends SuspendableChannel> implements SuspendableChannel, WrappedChannel<W> {

    /**
     * The wrapped channel.
     */
    protected final W channel;

    private final ChannelListener.SimpleSetter<C> readSetter = new ChannelListener.SimpleSetter<C>();
    private final ChannelListener.SimpleSetter<C> writeSetter = new ChannelListener.SimpleSetter<C>();
    private final ChannelListener.SimpleSetter<C> closeSetter = new ChannelListener.SimpleSetter<C>();

    private boolean readsRequested;
    private boolean writesRequested;

    /**
     * The readiness of this channel implementation.
     */
    protected enum Readiness {
        /**
         * The channel is ready, regardless of the underlying channel status.
         */
        ALWAYS,
        /**
         * The channel is ready if the underlying channel is ready.
         */
        OKAY,
        /**
         * The channel is not ready even if the underlying channel is.
         */
        NEVER,
    }

    private final Runnable readListenerCommand = new Runnable() {
        public void run() {
            boolean doReads;
            do {
                synchronized (getReadLock()) {
                    if (isReadable() == Readiness.NEVER) {
                        return;
                    }
                }
                ChannelListeners.invokeChannelListener(channel, readListener);
                synchronized (getReadLock()) {
                    final Readiness readiness = isReadable();
                    doReads = readsRequested && readiness == Readiness.ALWAYS;
                    if (readiness == Readiness.NEVER) {
                        channel.suspendReads();
                    }
                }
            } while (doReads);
        }

        public String toString() {
            return "Read listener command for " + TranslatingSuspendableChannel.this;
        }
    };
    private final ChannelListener<W> readListener = new ChannelListener<W>() {
        public void handleEvent(final W channel) {
            handleReadable(channel);
        }

        public String toString() {
            return "Read listener for " + TranslatingSuspendableChannel.this;
        }
    };

    private final Runnable writeListenerCommand = new Runnable() {
        public void run() {
            boolean doWrites;
            do {
                ChannelListeners.invokeChannelListener(channel, writeListener);
                synchronized (getWriteLock()) {
                    final Readiness readiness = isWritable();
                    doWrites = writesRequested && readiness == Readiness.ALWAYS;
                    if (readiness == Readiness.NEVER) {
                        channel.suspendWrites();
                    }
                }
            } while (doWrites);
        }

        public String toString() {
            return "Write listener command for " + TranslatingSuspendableChannel.this;
        }
    };
    private final ChannelListener<W> writeListener = new ChannelListener<W>() {
        public void handleEvent(final W channel) {
            handleWritable(channel);
        }

        public String toString() {
            return "Write listener for " + TranslatingSuspendableChannel.this;
        }
    };
    private final ChannelListener<W> closeListener = new ChannelListener<W>() {
        public void handleEvent(final W channel) {
            ChannelListeners.<C>invokeChannelListener(thisTyped(), closeSetter.get());
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
    @SuppressWarnings("unchecked")
    protected TranslatingSuspendableChannel(final W channel) {
        if (channel == null) {
            throw new IllegalArgumentException("channel is null");
        }
        this.channel = channel;
        final ChannelListener.Setter<? extends W> readSetter = (ChannelListener.Setter<? extends W>) channel.getReadSetter();
        readSetter.set(readListener);
        final ChannelListener.Setter<? extends W> writeSetter = (ChannelListener.Setter<? extends W>) channel.getWriteSetter();
        writeSetter.set(writeListener);
        final ChannelListener.Setter<? extends W> setter = (ChannelListener.Setter<? extends W>) channel.getCloseSetter();
        setter.set(closeListener);
    }

    /**
     * Called when the underlying channel is readable.
     *
     * @param channel the underlying channel
     */
    protected void handleReadable(final W channel) {
        final ChannelListener<? super C> listener = readSetter.get();
        if (listener == null) {
            synchronized (getReadLock()) {
                channel.suspendReads();
                readsRequested = false;
            }
            return;
        }
        ChannelListeners.<C>invokeChannelListener(thisTyped(), listener);
    }

    /**
     * Called when the underlying channel is writable.
     *
     * @param channel the underlying channel
     */
    protected void handleWritable(final W channel) {
        final ChannelListener<? super C> listener = writeSetter.get();
        if (listener == null) {
            synchronized (getWriteLock()) {
                channel.suspendWrites();
                writesRequested = false;
            }
            return;
        }
        ChannelListeners.<C>invokeChannelListener(thisTyped(), listener);
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

    /** {@inheritDoc} */
    public ChannelListener.Setter<C> getCloseSetter() {
        return closeSetter;
    }

    /** {@inheritDoc} */
    public ChannelListener.Setter<C> getReadSetter() {
        return readSetter;
    }

    /** {@inheritDoc} */
    public ChannelListener.Setter<C> getWriteSetter() {
        return writeSetter;
    }

    /** {@inheritDoc} */
    public void suspendReads() {
        synchronized (getReadLock()) {
            readsRequested = false;
            channel.suspendReads();
        }
    }

    /** {@inheritDoc} */
    public void resumeReads() {
        synchronized (getReadLock()) {
            readsRequested = true;
            switch (isReadable()) {
                case NEVER: channel.suspendReads(); break;
                case OKAY: channel.resumeReads(); break;
                case ALWAYS: channel.wakeupReads(); break;
            }
        }
    }

    public boolean isReadResumed() {
        synchronized (getReadLock()) {
            return readsRequested;
        }
    }

    /** {@inheritDoc} */
    public void wakeupReads() {
        resumeReads();
        channel.wakeupReads();
    }

    public void wakeupReadsIfRequested() {
        synchronized (getReadLock()) {
            if (readsRequested) {
                channel.wakeupReads();
            }
        }
    }

    /**
     * Resume reads if the user has requested so.
     */
    protected void resumeReadsIfRequested() {
        synchronized (getReadLock()) {
            if (readsRequested) {
                channel.resumeReads();
            }
        }
    }

    /**
     * Resume reads if the user has requested so.
     */
    protected void resumeReadsIfRequestedAndSuspendWrites() {
        synchronized (getReadLock()) {
            if (readsRequested) {
                channel.resumeReads();
                channel.suspendWrites();
            }
        }
    }

    /** {@inheritDoc} */
    public void suspendWrites() {
        synchronized (getWriteLock()) {
            writesRequested = false;
            channel.suspendWrites();
        }
    }

    /** {@inheritDoc} */
    public void resumeWrites() {
        synchronized (getWriteLock()) {
            writesRequested = true;
            switch (isWritable()) {
                case NEVER: channel.suspendWrites(); break;
                case OKAY: channel.resumeWrites(); break;
                case ALWAYS: channel.wakeupWrites(); break;
            }
        }
    }

    public boolean isWriteResumed() {
        synchronized (getWriteLock()) {
            return writesRequested;
        }
    }

    /** {@inheritDoc} */
    public void wakeupWrites() {
        resumeWrites();
        channel.wakeupWrites();
    }

    public void wakeupWritesIfRequested() {
        synchronized (getWriteLock()) {
            if (writesRequested) {
                channel.wakeupWrites();
            }
        }
    }

    /**
     * Resume writes if the user has requested so.
     */
    protected void resumeWritesIfRequested() {
        synchronized (getWriteLock()) {
            if (writesRequested) {
                channel.resumeWrites();
            }
        }
    }

    protected void resumeWritesIfRequestedAndSuspendReads() {
        synchronized (getWriteLock()) {
            if (writesRequested) {
                channel.resumeWrites();
                channel.suspendReads();
            }
        }
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
     * Base implementation which delegates the flush request to the channel.  Subclasses should override this
     * method as appropriate.
     *
     * @return {@code true} if the flush completed, or {@code false} if the operation would block
     * @throws IOException if an error occurs
     */
    public boolean flush() throws IOException {
        return channel.flush();
    }

    /**
     * Base implementation method which simply delegates the shutdown request to the delegate channel.  Subclasses may
     * override this method and call up to this method as appropriate.
     *
     * @throws IOException if an I/O error occurs
     */
    public void shutdownReads() throws IOException {
        channel.shutdownReads();
    }

    /**
     * Base implementation method which simply delegates the shutdown request to the delegate channel.  Subclasses may
     * override this method and call up to this method as appropriate.
     *
     * @return {@code true} if the channel was shut down, or {@code false} if the operation would block
     * @throws IOException if an I/O error occurs
     */
    public boolean shutdownWrites() throws IOException {
        return channel.shutdownWrites();
    }

    /** {@inheritDoc} */
    public void awaitReadable() throws IOException {
        synchronized (getReadLock()) {
            if (isReadable() == Readiness.ALWAYS) {
                return;
            }
        }
        channel.awaitReadable();
    }

    /** {@inheritDoc} */
    public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
        synchronized (getReadLock()) {
            if (isReadable() == Readiness.ALWAYS) {
                return;
            }
        }
        channel.awaitReadable(time, timeUnit);
    }

    public XnioExecutor getReadThread() {
        return channel.getReadThread();
    }

    /** {@inheritDoc} */
    public void awaitWritable() throws IOException {
        synchronized (getWriteLock()) {
            if (isWritable() == Readiness.ALWAYS) {
                return;
            }
        }
        channel.awaitWritable();
    }

    /** {@inheritDoc} */
    public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
        synchronized (getWriteLock()) {
            if (isWritable() == Readiness.ALWAYS) {
                return;
            }
        }
        channel.awaitWritable(time, timeUnit);
    }

    public XnioExecutor getWriteThread() {
        return channel.getWriteThread();
    }

    /**
     * Base channel close implementation.  Delegates to the delegate channel by default.
     *
     * @throws IOException if an I/O error occurs
     */
    public void close() throws IOException {
        channel.close();
    }

    /** {@inheritDoc} */
    public boolean isOpen() {
        return channel.isOpen();
    }

    /** {@inheritDoc} */
    public W getChannel() {
        return channel;
    }

    /**
     * Determine whether this channel is known to be (or to not be) readable.  Called under the read lock.
     *
     * @return the readiness of this channel
     */
    protected abstract Readiness isReadable();

    /**
     * Get the object to use as a read lock.  Ensures that suspend/resume reads is atomic with respect to listener
     * invocation.
     *
     * @return the read lock
     */
    protected abstract Object getReadLock();

    /**
     * Determine whether this channel is known to be (or to not be) writable.  Called under the write lock.
     *
     * @return the readiness of this channel
     */
    protected abstract Readiness isWritable();

    /**
     * Get the object to use as a write lock.  Ensures that suspend/resume writes is atomic with respect to listener
     * invocation.
     *
     * @return the write lock
     */
    protected abstract Object getWriteLock();

    /** {@inheritDoc} */
    public XnioWorker getWorker() {
        return channel.getWorker();
    }

    /** {@inheritDoc} */
    public String toString() {
        return getClass().getName() + " around " + channel;
    }
}
