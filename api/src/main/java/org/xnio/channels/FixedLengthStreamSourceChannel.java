/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2012 Red Hat, Inc. and/or its affiliates, and individual
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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.Option;
import org.xnio.XnioExecutor;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;

import static java.lang.Math.min;
import static org.xnio.Bits.*;
import static org.xnio._private.Messages.msg;

/**
 * A channel which reads data of a fixed length and calls a finish listener.  When the finish listener is called,
 * it should examine the result of {@link #getRemaining()} to see if more bytes were pending when the channel was
 * closed.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class FixedLengthStreamSourceChannel implements StreamSourceChannel, ProtectedWrappedChannel<StreamSourceChannel>, ReadListenerSettable<FixedLengthStreamSourceChannel>, CloseListenerSettable<FixedLengthStreamSourceChannel> {
    private final StreamSourceChannel delegate;
    private final Object guard;

    private final ChannelListener<? super FixedLengthStreamSourceChannel> finishListener;
    private ChannelListener<? super FixedLengthStreamSourceChannel> readListener;
    private ChannelListener<? super FixedLengthStreamSourceChannel> closeListener;

    private int state;
    private long remaining;

    private static final int FLAG_CLOSED = 1 << 0;
    private static final int FLAG_FINISHED = 1 << 1;
    private static final int FLAG_CONFIGURABLE = 1 << 2;
    private static final int FLAG_PASS_CLOSE = 1 << 3;

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
     * @param guard the guard object to use
     */
    public FixedLengthStreamSourceChannel(final StreamSourceChannel delegate, final long contentLength, final ChannelListener<? super FixedLengthStreamSourceChannel> finishListener, final Object guard) {
        this(delegate, contentLength, false, finishListener, guard);
    }

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
     * @param configurable {@code true} to allow options to pass through to the delegate, {@code false} otherwise
     * @param finishListener the listener to call once the stream is exhausted or closed
     * @param guard the guard object to use
     */
    public FixedLengthStreamSourceChannel(final StreamSourceChannel delegate, final long contentLength, final boolean configurable, final ChannelListener<? super FixedLengthStreamSourceChannel> finishListener, final Object guard) {
        this(delegate, contentLength, configurable, false, finishListener, guard);
    }

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
     * @param configurable {@code true} to allow options to pass through to the delegate, {@code false} otherwise
     * @param propagateClose {@code true} to propagate close/shutdown to the delegate, {@code false} otherwise
     * @param finishListener the listener to call once the stream is exhausted or closed
     * @param guard the guard object to use
     */
    public FixedLengthStreamSourceChannel(final StreamSourceChannel delegate, final long contentLength, final boolean configurable, final boolean propagateClose, final ChannelListener<? super FixedLengthStreamSourceChannel> finishListener, final Object guard) {
        this.guard = guard;
        this.finishListener = finishListener;
        if (contentLength < 0L) {
            throw msg.parameterOutOfRange("contentLength");
        }
        this.delegate = delegate;
        remaining = contentLength;
        delegate.getReadSetter().set(new ChannelListener<StreamSourceChannel>() {
            public void handleEvent(final StreamSourceChannel channel) {
                ChannelListeners.invokeChannelListener(FixedLengthStreamSourceChannel.this, readListener);
            }
        });
        state = (configurable ? FLAG_CONFIGURABLE : 0) | (propagateClose ? FLAG_PASS_CLOSE : 0);
    }

    public void setReadListener(final ChannelListener<? super FixedLengthStreamSourceChannel> readListener) {
        this.readListener = readListener;
    }

    public ChannelListener<? super FixedLengthStreamSourceChannel> getReadListener() {
        return readListener;
    }

    public void setCloseListener(final ChannelListener<? super FixedLengthStreamSourceChannel> closeListener) {
        this.closeListener = closeListener;
    }

    public ChannelListener<? super FixedLengthStreamSourceChannel> getCloseListener() {
        return closeListener;
    }

    public ChannelListener.Setter<FixedLengthStreamSourceChannel> getReadSetter() {
        return new ReadListenerSettable.Setter<FixedLengthStreamSourceChannel>(this);
    }

    public ChannelListener.Setter<FixedLengthStreamSourceChannel> getCloseSetter() {
        return new CloseListenerSettable.Setter<FixedLengthStreamSourceChannel>(this);
    }

    public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        final long remaining = this.remaining;
        if (anyAreSet(state, FLAG_CLOSED | FLAG_FINISHED) || remaining == 0L || count == 0L) {
            return 0L;
        }
        long res = 0L;
        try {
            return res = delegate.transferTo(position, min(count, remaining), target);
        } finally {
            if (res > 0L) {
                if ((this.remaining = remaining - res) == 0L) {
                    state |= FLAG_FINISHED;
                    callFinish();
                }
            }
        }
    }

    public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
        final long remaining = this.remaining;
        if (anyAreSet(state, FLAG_CLOSED | FLAG_FINISHED) || remaining == 0L) {
            return -1L;
        }
        if (count == 0L) {
            return 0L;
        }
        long res = 0L;
        try {
            return res = delegate.transferTo(min(count, remaining), throughBuffer, target);
        } finally {
            if (res > 0L) {
                if ((this.remaining = remaining - res) == 0L) {
                    state |= FLAG_FINISHED;
                    callFinish();
                }
            }
        }
    }

    public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        final long remaining = this.remaining;
        if (anyAreSet(state, FLAG_CLOSED | FLAG_FINISHED) || remaining == 0L) {
            return -1L;
        }
        if (length == 0) {
            return 0L;
        } else if (length == 1) {
            return read(dsts[offset]);
        }
        long res = 0L;
        try {
            int lim;
            // The total amount of buffer space discovered so far.
            long t = 0L;
            for (int i = 0; i < length; i ++) {
                final ByteBuffer buffer = dsts[i + offset];
                // Grow the discovered buffer space by the remaining size of the current buffer.
                // We want to capture the limit so we calculate "remaining" ourselves.
                t += (lim = buffer.limit()) - buffer.position();
                if (t > remaining) {
                    // only read up to this point, and trim the last buffer by the number of extra bytes
                    buffer.limit(lim - (int) (t - remaining));
                    try {
                        return res = delegate.read(dsts, offset, i + 1);
                    } finally {
                        // restore the original limit
                        buffer.limit(lim);
                    }
                }
            }
            // the total buffer space is less than the remaining count.
            return res = t == 0L ? 0L : delegate.read(dsts, offset, length);
        } finally {
            if (res > 0L) {
                if ((this.remaining = remaining - res) == 0L) {
                    state |= FLAG_FINISHED;
                    callFinish();
                }
            }
        }
    }

    public long read(final ByteBuffer[] dsts) throws IOException {
        return read(dsts, 0, dsts.length);
    }

    public int read(final ByteBuffer dst) throws IOException {
        final long remaining = this.remaining;
        if (anyAreSet(state, FLAG_CLOSED | FLAG_FINISHED) || remaining == 0L) {
            return -1;
        }
        int res = 0;
        try {
            final int lim = dst.limit();
            final int pos = dst.position();
            if (lim - pos > remaining) {
                dst.limit((int) (remaining - (long) pos));
                try {
                    return res = delegate.read(dst);
                } finally {
                    dst.limit(lim);
                }
            } else {
                return res = delegate.read(dst);
            }
        } finally {
            if (res > 0) {
                if ((this.remaining = remaining - res) == 0L) {
                    state |= FLAG_FINISHED;
                    callFinish();
                }
            }
        }
    }

    public void suspendReads() {
        if (allAreClear(state, FLAG_CLOSED | FLAG_FINISHED)) {
            delegate.suspendReads();
        }
    }

    public void resumeReads() {
        if (allAreClear(state, FLAG_CLOSED | FLAG_FINISHED)) {
            delegate.resumeReads();
        } else {
            delegate.getIoThread().execute(ChannelListeners.getChannelListenerTask(this, readListener));
        }
    }

    public boolean isReadResumed() {
        return allAreClear(state, FLAG_CLOSED | FLAG_FINISHED) && delegate.isReadResumed();
    }

    public void wakeupReads() {
        if (allAreClear(state, FLAG_CLOSED | FLAG_FINISHED)) {
            delegate.wakeupReads();
        } else {
            delegate.getIoThread().execute(ChannelListeners.getChannelListenerTask(this, readListener));
        }
    }

    public void shutdownReads() throws IOException {
        final int state = this.state;
        if (allAreClear(state, FLAG_CLOSED)) try {
            this.state = state | FLAG_CLOSED | FLAG_FINISHED;
            if (allAreSet(state, FLAG_PASS_CLOSE)) {
                delegate.shutdownReads();
            }
        } finally {
            if (allAreClear(state, FLAG_FINISHED)) callFinish();
            callClosed();
        }
    }

    public void awaitReadable() throws IOException {
        if (anyAreSet(state, FLAG_CLOSED | FLAG_FINISHED)) {
            return;
        }
        delegate.awaitReadable();
    }

    public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
        if (anyAreSet(state, FLAG_CLOSED | FLAG_FINISHED)) {
            return;
        }
        delegate.awaitReadable(time, timeUnit);
    }

    @Deprecated
    public XnioExecutor getReadThread() {
        return delegate.getReadThread();
    }

    public XnioIoThread getIoThread() {
        return delegate.getIoThread();
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
        return allAreSet(state, FLAG_CONFIGURABLE) && delegate.supportsOption(option);
    }

    public <T> T getOption(final Option<T> option) throws IOException {
        return allAreSet(state, FLAG_CONFIGURABLE) ? delegate.getOption(option) : null;
    }

    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        return allAreSet(state, FLAG_CONFIGURABLE) ? delegate.setOption(option, value) : null;
    }

    public StreamSourceChannel getChannel(final Object guard) {
        final Object ourGuard = this.guard;
        if (ourGuard == null || guard == ourGuard) {
            return delegate;
        } else {
            return null;
        }
    }

    /**
     * Get the number of remaining bytes.
     *
     * @return the number of remaining bytes
     */
    public long getRemaining() {
        return remaining;
    }

    private void callFinish() {
        ChannelListeners.invokeChannelListener(this, finishListener);
    }

    private void callClosed() {
        ChannelListeners.invokeChannelListener(this, closeListener);
    }
}
