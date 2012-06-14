/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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

package org.xnio.channels;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import org.xnio.Bits;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.Option;
import org.xnio.XnioExecutor;
import org.xnio.XnioWorker;

import static java.lang.Math.min;
import static org.xnio.Bits.*;

/**
 * A channel which reads data of a fixed length.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
/*
 * Implementation notes
 * --------------------
 * The {@code exhausted} flag is set once a method returns -1 and signifies that the read listener should no longer be
 * called.  The {@code finishListener} is called when remaining is reduced to 0 or when the channel is closed explicitly.
 * If there are 0 remaining bytes but {@code exhausted} has not yet been set, the channel is considered "ready" until
 * the EOF -1 value is read or the channel is closed.  Since this is a half-duplex channel, shutting down reads is
 * identical to closing the channel.
 */
public final class FixedLengthStreamSourceChannel implements StreamSourceChannel, WrappedChannel<StreamSourceChannel> {
    private final StreamSourceChannel delegate;

    private final ChannelListener<? super FixedLengthStreamSourceChannel> finishListener;
    private final ChannelListener.SimpleSetter<FixedLengthStreamSourceChannel> readSetter = new ChannelListener.SimpleSetter<FixedLengthStreamSourceChannel>();
    private final ChannelListener.SimpleSetter<FixedLengthStreamSourceChannel> closeSetter = new ChannelListener.SimpleSetter<FixedLengthStreamSourceChannel>();

    @SuppressWarnings("unused")
    private volatile long state;

    private static final long FLAG_ENTERED = 1L << 63L;
    private static final long FLAG_CLOSED = 1L << 62L;
    private static final long MASK_COUNT = Bits.longBitMask(0, 61);

    private static final AtomicLongFieldUpdater<FixedLengthStreamSourceChannel> stateUpdater = AtomicLongFieldUpdater.newUpdater(FixedLengthStreamSourceChannel.class, "state");

    /**
     * Construct a new instance.  The given listener is called once all the bytes are read from the stream
     * <b>or</b> the stream is closed.  This listener should cause the remaining data to be drained from the
     * underlying stream via the {@link #drain()} method if the underlying stream is to be reused.
     * <p>
     * Calling this constructor will replace the read listener of the underlying channel.  The listener should be
     * restored from the {@code finishListener} object.  The underlying stream should not be closed while this wrapper
     * stream is active.
     *
     * @param delegate the stream source channel to read from
     * @param contentLength the amount of content to read
     * @param finishListener the listener to call once the stream is exhausted or closed
     */
    public FixedLengthStreamSourceChannel(final StreamSourceChannel delegate, final long contentLength, final ChannelListener<? super FixedLengthStreamSourceChannel> finishListener) {
        this.finishListener = finishListener;
        if (contentLength < 0L) {
            throw new IllegalArgumentException("Content length must be greater than or equal to zero");
        } else if (contentLength > MASK_COUNT) {
            throw new IllegalArgumentException("Content length is too long");
        }
        this.delegate = delegate;
        stateUpdater.lazySet(this, contentLength);
        delegate.getReadSetter().set(new ChannelListener<StreamSourceChannel>() {
            public void handleEvent(final StreamSourceChannel channel) {
                ChannelListeners.invokeChannelListener(FixedLengthStreamSourceChannel.this, readSetter.get());
            }
        });
    }

    public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        long val = enter();
        if (allAreSet(val, FLAG_CLOSED | FLAG_ENTERED)) {
            return -1;
        }
        try {
            if (allAreSet(val, FLAG_CLOSED) || val == 0L || count == 0L) {
                return 0L;
            }
            final long res = delegate.transferTo(position, min(count, val), target);
            if ((val -= res) == 0) {
                delegate.getReadThread().execute(ChannelListeners.getChannelListenerTask(this, finishListener));
            }
            return res;
        } finally {
            exit(val);
        }
    }

    public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
        if (count == 0L) {
            return 0L;
        }
        long val = enter();
        if (allAreSet(val, FLAG_CLOSED | FLAG_ENTERED)) {
            return -1;
        }
        try {
            if (allAreSet(val, FLAG_CLOSED) || val == 0L) {
                return -1L;
            }
            final long res = delegate.transferTo(min(count, val), throughBuffer, target);
            if ((val -= res) == 0) {
                delegate.getReadThread().execute(ChannelListeners.getChannelListenerTask(this, finishListener));
            }
            return res;
        } finally {
            exit(val);
        }
    }

    public ChannelListener.Setter<? extends StreamSourceChannel> getReadSetter() {
        return readSetter;
    }

    public ChannelListener.Setter<? extends StreamSourceChannel> getCloseSetter() {
        return closeSetter;
    }

    public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        long val = enter();
        if (allAreSet(val, FLAG_CLOSED | FLAG_ENTERED)) {
            return -1;
        }
        try {
            if (allAreSet(val, FLAG_CLOSED) || val == 0L) {
                return -1L;
            }
            int lim;
            int pos;
            long t = 0L;
            for (int i = 0; i < length; i ++) {
                final ByteBuffer buffer = dsts[i + offset];
                t += (lim = buffer.limit()) - (pos = buffer.position());
                if (t > val) {
                    buffer.limit((int) (val - (long) pos));
                    try {
                        long res = delegate.read(dsts, offset, i + 1);
                        if (res > 0L) {
                            val -= res;
                        }
                        if (res == -1) {
                            throw new EOFException();
                        }
                        return res;
                    } finally {
                        buffer.limit(lim);
                    }
                }
            }
            long res = delegate.read(dsts, offset, length);
            if (res > 0) {
                if ((val -= res) == 0) {
                    delegate.getReadThread().execute(ChannelListeners.getChannelListenerTask(this, finishListener));
                }
            }
            return res;
        } finally {
            exit(val);
        }
    }

    public long read(final ByteBuffer[] dsts) throws IOException {
        return read(dsts, 0, dsts.length);
    }

    public int read(final ByteBuffer dst) throws IOException {
        long val = enter();
        if (allAreSet(val, FLAG_CLOSED | FLAG_ENTERED)) {
            return -1;
        }
        try {
            if (allAreSet(val, FLAG_CLOSED) || val == 0L) {
                return -1;
            }
            final int lim = dst.limit();
            final int pos = dst.position();
            if (lim - pos > val) {
                dst.limit((int) (val - (long) pos));
                try {
                    final int res = delegate.read(dst);
                    if (res > 0) {
                        val -= res;
                    }
                    return res;
                } finally {
                    dst.limit(lim);
                }
            } else {
                final int res = delegate.read(dst);
                if (res > 0) {
                    if ((val -= res) == 0) {
                        delegate.getReadThread().execute(ChannelListeners.getChannelListenerTask(this, finishListener));
                    }
                }
                return res;
            }
        } finally {
            exit(val);
        }
    }

    public void suspendReads() {
        long val = enter();
        if (allAreSet(val, FLAG_CLOSED | FLAG_ENTERED)) {
            return;
        }
        try {
            if (allAreSet(val, FLAG_CLOSED)) {
                return;
            }
            delegate.suspendReads();
        } finally {
            exit(val);
        }
    }

    public void resumeReads() {
        long val = enter();
        if (allAreSet(val, FLAG_CLOSED | FLAG_ENTERED)) {
            return;
        }
        try {
            if (allAreSet(val, FLAG_CLOSED)) {
                return;
            }
            if (val == 0L) {
                delegate.wakeupReads();
            } else {
                delegate.resumeReads();
            }
        } finally {
            exit(val);
        }
    }

    public boolean isReadResumed() {
        return allAreClear(state, FLAG_CLOSED) && delegate.isReadResumed();
    }

    public void wakeupReads() {
        long val = enter();
        if (allAreSet(val, FLAG_CLOSED | FLAG_ENTERED)) {
            return;
        }
        try {
            if (allAreSet(val, FLAG_CLOSED)) {
                return;
            }
            delegate.wakeupReads();
        } finally {
            exit(val);
        }
    }

    public void shutdownReads() throws IOException {
        long val;
        do {
            val = state;
            if (allAreSet(val, FLAG_CLOSED)) {
                return;
            }
        } while (! stateUpdater.compareAndSet(this, val, val | FLAG_CLOSED | FLAG_ENTERED));
        try {

        } finally {
            exit(val | FLAG_CLOSED);
        }
        // todo
        final XnioExecutor readThread = delegate.getReadThread();
        ChannelListener<? super FixedLengthStreamSourceChannel> listener = closeSetter.get();
        if (listener != null) readThread.execute(ChannelListeners.getChannelListenerTask(this, listener));
    }

    public void awaitReadable() throws IOException {
        final long val = state;
        if (allAreSet(val, FLAG_CLOSED) || val == 0L) {
            return;
        }
        delegate.awaitReadable();
    }

    public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
        final long val = state;
        if (allAreSet(val, FLAG_CLOSED) || val == 0L) {
            return;
        }
        delegate.awaitReadable(time, timeUnit);
    }

    public XnioExecutor getReadThread() {
        return delegate.getReadThread();
    }

    public XnioWorker getWorker() {
        return delegate.getWorker();
    }

    public boolean isOpen() {
        return allAreClear(state, FLAG_CLOSED);
    }

    public void close() throws IOException {
        shutdownReads();
    }

    public boolean supportsOption(final Option<?> option) {
        return delegate.supportsOption(option);
    }

    public <T> T getOption(final Option<T> option) throws IOException {
        return delegate.getOption(option);
    }

    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        return delegate.setOption(option, value);
    }

    public StreamSourceChannel getChannel() {
        return delegate;
    }

    private static final ByteBuffer drainBuffer = ByteBuffer.allocateDirect(8192);

    public boolean drain() throws IOException {
        long val = enter();
        try {
            if (allAreClear(val, FLAG_CLOSED)) {
                throw new IllegalStateException("Channel cannot be drained if it is not closed");
            }
            final ByteBuffer buffer = drainBuffer.duplicate();
            long remaining = val & MASK_COUNT;
            if (remaining > 0L) try {
                int res;
                res = delegate.read(buffer);
                if (res == 0) {
                    return false;
                }
                if (res == -1) {
                    remaining = 0L;
                    return true;
                }
                while ((remaining -= res) > 0L) {
                    buffer.clear();
                    res = delegate.read(buffer);
                    if (res == 0) {
                        return false;
                    }
                    if (res == -1) {
                        remaining = 0L;
                        return true;
                    }
                }
                return true;
            } finally {
                val = FLAG_CLOSED | remaining;
            } else {
                return true;
            }
        } finally {
            exit(val);
        }
    }

    private long enter() {
        long oldVal, newVal;
        do {
            oldVal = state;
            if (Bits.allAreSet(oldVal, FLAG_ENTERED) && Bits.allAreClear(oldVal, FLAG_CLOSED)) {
                throw new ConcurrentStreamChannelAccessException();
            }
            newVal = oldVal | FLAG_ENTERED;
        } while (! stateUpdater.weakCompareAndSet(this, oldVal, newVal));
        return oldVal;
    }

    private void exit(long newVal) {
        stateUpdater.lazySet(this, newVal);
    }
}
