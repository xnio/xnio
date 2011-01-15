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
import org.xnio.ReadChannelThread;
import org.xnio.WriteChannelThread;

/**
 * An abstract wrapped channel.
 *
 * @param <C> the channel type implemented by this class
 * @param <W> the channel type being wrapped by this class
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class TranslatingSuspendableChannel<C extends SuspendableChannel, W extends SuspendableChannel> implements SuspendableChannel {
    protected final W channel;

    private final ChannelListener.SimpleSetter<C> readSetter = new ChannelListener.SimpleSetter<C>();
    private final ChannelListener.SimpleSetter<C> writeSetter = new ChannelListener.SimpleSetter<C>();
    private final ChannelListener.SimpleSetter<C> closeSetter = new ChannelListener.SimpleSetter<C>();

    private boolean readsRequested;
    private boolean writesRequested;

    private final Runnable readListenerCommand = new Runnable() {
        public void run() {
            ChannelListeners.invokeChannelListener(channel, readListener);
        }
    };
    private final ChannelListener<W> readListener = new ChannelListener<W>() {
        public void handleEvent(final W channel) {
            final ChannelListener<? super C> listener = readSetter.get();
            if (listener == null) {
                synchronized (getReadLock()) {
                    channel.suspendReads();
                    readsRequested = false;
                }
                return;
            }
            //noinspection unchecked
            ChannelListeners.<C>invokeChannelListener((C) TranslatingSuspendableChannel.this, listener);
            synchronized (getReadLock()) {
                if (readsRequested) {
                    if (isReadable()) {
                        channel.getReadThread().execute(readListenerCommand);
                    }
                }
            }
        }
    };

    private final Runnable writeListenerCommand = new Runnable() {
        public void run() {
            ChannelListeners.invokeChannelListener(channel, writeListener);
        }
    };
    private final ChannelListener<W> writeListener = new ChannelListener<W>() {
        public void handleEvent(final W channel) {
            final ChannelListener<? super C> listener = writeSetter.get();
            if (listener == null) {
                synchronized (getWriteLock()) {
                    channel.suspendWrites();
                    writesRequested = false;
                }
                return;
            }
            //noinspection unchecked
            ChannelListeners.<C>invokeChannelListener((C) TranslatingSuspendableChannel.this, listener);
            synchronized (getReadLock()) {
                if (writesRequested) {
                    if (isReadable()) {
                        channel.getWriteThread().execute(writeListenerCommand);
                    }
                }
            }
        }
    };

    /**
     * Construct a new instance.
     *
     * @param channel the channel being wrapped
     */
    @SuppressWarnings( { "unchecked" })
    protected TranslatingSuspendableChannel(final W channel) {
        this.channel = channel;
        final ChannelListener.Setter<W> readSetter = (ChannelListener.Setter<W>) channel.getReadSetter();
        readSetter.set(readListener);
        final ChannelListener.Setter<W> writeSetter = (ChannelListener.Setter<W>) channel.getWriteSetter();
        writeSetter.set(writeListener);
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
            channel.resumeReads();
            if (isReadable()) {
                getReadThread().execute(readListenerCommand);
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
            channel.resumeWrites();
            if (isWritable()) {
                getWriteThread().execute(writeListenerCommand);
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

    /** {@inheritDoc} */
    public void setReadThread(final ReadChannelThread thread) throws IllegalArgumentException {
        channel.setReadThread(thread);
    }

    /** {@inheritDoc} */
    public ReadChannelThread getReadThread() {
        return channel.getReadThread();
    }

    /** {@inheritDoc} */
    public void setWriteThread(final WriteChannelThread thread) throws IllegalArgumentException {
        channel.setWriteThread(thread);
    }

    /** {@inheritDoc} */
    public WriteChannelThread getWriteThread() {
        return channel.getWriteThread();
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
            if (isReadable()) {
                return;
            }
        }
        channel.awaitReadable();
    }

    /** {@inheritDoc} */
    public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
        synchronized (getReadLock()) {
            if (isReadable()) {
                return;
            }
        }
        channel.awaitReadable(time, timeUnit);
    }

    /** {@inheritDoc} */
    public void awaitWritable() throws IOException {
        synchronized (getWriteLock()) {
            if (isWritable()) {
                return;
            }
        }
        channel.awaitWritable();
    }

    /** {@inheritDoc} */
    public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
        synchronized (getWriteLock()) {
            if (isWritable()) {
                return;
            }
        }
        channel.awaitWritable(time, timeUnit);
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

    /**
     * Determine whether this channel is known to be readable.  If {@code true}, and reads are enabled,
     * the read listener will be invoked until one or the other conditions changes.  If {@code false}, the
     * read listener will only be invoked when the underlying channel is readable.  Called under the read lock.
     *
     * @return {@code true} if this channel is readable
     */
    protected abstract boolean isReadable();

    /**
     * Get the object to use as a read lock.  Ensures that suspend/resume reads is atomic with respect to listener
     * invocation.
     *
     * @return the read lock
     */
    protected abstract Object getReadLock();

    /**
     * Determine whether this channel is known to be writable.  If {@code true}, and writes are enabled,
     * the write listener will be invoked until one or the other conditions changes.  If {@code false}, the
     * write listener will only be invoked when the underlying channel is writable.  Called under the write lock.
     *
     * @return {@code true} if this channel is writable
     */
    protected abstract boolean isWritable();

    /**
     * Get the object to use as a write lock.  Ensures that suspend/resume writes is atomic with respect to listener
     * invocation.
     *
     * @return the write lock
     */
    protected abstract Object getWriteLock();
}
