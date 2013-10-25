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

package org.xnio.channels;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
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
import static org.xnio.IoUtils.safeClose;
import static org.xnio._private.Messages.msg;

/**
 * A channel which writes a fixed amount of data.  A listener is called once the data has been written.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class FixedLengthStreamSinkChannel implements StreamSinkChannel, ProtectedWrappedChannel<StreamSinkChannel>, WriteListenerSettable<FixedLengthStreamSinkChannel>, CloseListenerSettable<FixedLengthStreamSinkChannel> {
    private final StreamSinkChannel delegate;
    private final Object guard;

    private final ChannelListener<? super FixedLengthStreamSinkChannel> finishListener;
    private ChannelListener<? super FixedLengthStreamSinkChannel> writeListener;
    private ChannelListener<? super FixedLengthStreamSinkChannel> closeListener;

    private int state;
    private long count;

    private static final int FLAG_CLOSE_REQUESTED = 1 << 0;
    private static final int FLAG_CLOSE_COMPLETE = 1 << 1;
    private static final int FLAG_CONFIGURABLE = 1 << 2;
    private static final int FLAG_PASS_CLOSE = 1 << 3;

    /**
     * Construct a new instance.
     *
     * @param delegate the delegate channel
     * @param contentLength the content length
     * @param configurable {@code true} if this instance should pass configuration to the delegate
     * @param propagateClose {@code true} if this instance should pass close to the delegate
     * @param finishListener the listener to call when the channel is closed or the length is reached
     * @param guard the guard object to use
     */
    public FixedLengthStreamSinkChannel(final StreamSinkChannel delegate, final long contentLength, final boolean configurable, final boolean propagateClose, final ChannelListener<? super FixedLengthStreamSinkChannel> finishListener, final Object guard) {
        if (contentLength < 0L) {
            throw msg.parameterOutOfRange("contentLength");
        }
        if (delegate == null) {
            throw msg.nullParameter("delegate");
        }
        this.guard = guard;
        this.delegate = delegate;
        this.finishListener = finishListener;
        state = (configurable ? FLAG_CONFIGURABLE : 0) | (propagateClose ? FLAG_PASS_CLOSE : 0);
        count = contentLength;
        delegate.getWriteSetter().set(new ChannelListener<StreamSinkChannel>() {
            public void handleEvent(final StreamSinkChannel channel) {
                ChannelListeners.invokeChannelListener(FixedLengthStreamSinkChannel.this, writeListener);
            }
        });
    }

    public void setWriteListener(final ChannelListener<? super FixedLengthStreamSinkChannel> listener) {
        this.writeListener = listener;
    }

    public ChannelListener<? super FixedLengthStreamSinkChannel> getWriteListener() {
        return writeListener;
    }

    public void setCloseListener(final ChannelListener<? super FixedLengthStreamSinkChannel> listener) {
        this.closeListener = listener;
    }

    public ChannelListener<? super FixedLengthStreamSinkChannel> getCloseListener() {
        return closeListener;
    }

    public ChannelListener.Setter<FixedLengthStreamSinkChannel> getWriteSetter() {
        return new WriteListenerSettable.Setter<FixedLengthStreamSinkChannel>(this);
    }

    public ChannelListener.Setter<FixedLengthStreamSinkChannel> getCloseSetter() {
        return new CloseListenerSettable.Setter<FixedLengthStreamSinkChannel>(this);
    }

    @Override
    public int writeFinal(ByteBuffer src) throws IOException {
        return write(src, true);
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs, int offset, int length) throws IOException {
        return write(srcs, offset, length, true);
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs) throws IOException {
        return write(srcs, 0, srcs.length, true);
    }

    public StreamSinkChannel getChannel(final Object guard) {
        final Object ourGuard = this.guard;
        if (ourGuard == null || guard == ourGuard) {
            return delegate;
        } else {
            return null;
        }
    }

    @Deprecated
    public XnioExecutor getWriteThread() {
        return delegate.getWriteThread();
    }

    public XnioIoThread getIoThread() {
        return delegate.getIoThread();
    }

    public XnioWorker getWorker() {
        return delegate.getWorker();
    }

    @Override
    public int write(final ByteBuffer src) throws IOException {
        return write(src, false);
    }

    private int write(final ByteBuffer src, final boolean finalWrite) throws IOException {
        if (allAreSet(state, FLAG_CLOSE_REQUESTED)) {
            throw new ClosedChannelException();
        }
        if (! src.hasRemaining()) {
            return 0;
        }
        int res = 0;
        final long remaining = count;
        if (remaining == 0L) {
            throw msg.fixedOverflow();
        }
        try {
            final int lim = src.limit();
            final int pos = src.position();
            if (lim - pos > remaining) {
                src.limit((int) (remaining - (long) pos));
                try {
                    return res = doWrite(src, finalWrite);
                } finally {
                    src.limit(lim);
                }
            } else {
                return res = doWrite(src, finalWrite);
            }
        } finally {
            count = remaining - res;
        }
    }

    public long write(final ByteBuffer[] srcs) throws IOException {
        return write(srcs, 0, srcs.length);
    }

    public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        return write(srcs, offset, length, false);
    }

    private long write(final ByteBuffer[] srcs, final int offset, final int length, boolean writeFinal) throws IOException {
        if (allAreSet(state, FLAG_CLOSE_REQUESTED)) {
            throw new ClosedChannelException();
        }
        if (length == 0) {
            return 0L;
        } else if (length == 1) {
            return write(srcs[offset]);
        }
        final long remaining = count;
        if (remaining == 0L) {
            throw msg.fixedOverflow();
        }
        long res = 0L;
        try {
            int lim;
            // The total amount of buffer space discovered so far.
            long t = 0L;
            for (int i = 0; i < length; i ++) {
                final ByteBuffer buffer = srcs[i + offset];
                // Grow the discovered buffer space by the remaining size of the current buffer.
                // We want to capture the limit so we calculate "remaining" ourselves.
                t += (lim = buffer.limit()) - buffer.position();
                if (t > remaining) {
                    // only read up to this point, and trim the last buffer by the number of extra bytes
                    buffer.limit(lim - (int) (t - (remaining)));
                    try {
                        return res = doWrite(srcs, offset, i + 1, writeFinal);
                    } finally {
                        // restore the original limit
                        buffer.limit(lim);
                    }
                }
            }
            if (t == 0L) {
                return 0L;
            }
            // the total buffer space is less than the remaining count.
            return res = doWrite(srcs, offset, length, writeFinal);
        } finally {
            count = remaining - res;
        }
    }

    private long doWrite(ByteBuffer[] srcs, int offset, int length, final boolean writeFinal) throws IOException {
        if(writeFinal) {
            return delegate.writeFinal(srcs, offset, length);
        }
        return delegate.write(srcs, offset, length);
    }

    private int doWrite(ByteBuffer src, boolean finalWrite) throws IOException {
        if(finalWrite) {
            return delegate.writeFinal(src);
        }
        return delegate.write(src);
    }

    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        if (allAreSet(state, FLAG_CLOSE_REQUESTED)) {
            throw new ClosedChannelException();
        }
        if (count == 0L) return 0L;
        final long remaining = this.count;
        if (remaining == 0L) {
            throw msg.fixedOverflow();
        }
        long res = 0L;
        try {
            return res = delegate.transferFrom(src, position, min(count, remaining));
        } finally {
            this.count = remaining - res;
        }
    }

    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        if (allAreSet(state, FLAG_CLOSE_REQUESTED)) {
            throw new ClosedChannelException();
        }
        if (count == 0L) return 0L;
        final long remaining = this.count;
        if (remaining == 0L) {
            throw msg.fixedOverflow();
        }
        long res = 0L;
        try {
            return res = delegate.transferFrom(source, min(count, remaining), throughBuffer);
        } finally {
            this.count = remaining - res;
        }
    }

    public boolean flush() throws IOException {
        int state = this.state;
        if (anyAreSet(state, FLAG_CLOSE_COMPLETE)) {
            return true;
        }
        boolean flushed = false;
        try {
            return flushed = delegate.flush();
        } finally {
            if (flushed && allAreSet(state, FLAG_CLOSE_REQUESTED)) {
                this.state = state | FLAG_CLOSE_COMPLETE;
                callFinish();
                callClosed();
                if (count != 0) {
                    throw msg.fixedUnderflow(count);
                }
            }
        }
    }

    public void suspendWrites() {
        if (allAreClear(state, FLAG_CLOSE_COMPLETE)) {
            delegate.suspendWrites();
        }
    }

    public void resumeWrites() {
        if (allAreClear(state, FLAG_CLOSE_COMPLETE)) {
            delegate.resumeWrites();
        }
    }

    public boolean isWriteResumed() {
        return allAreClear(state, FLAG_CLOSE_COMPLETE) && delegate.isWriteResumed();
    }

    public void wakeupWrites() {
        if (allAreClear(state, FLAG_CLOSE_COMPLETE)) {
            delegate.wakeupWrites();
        }
    }

    public void shutdownWrites() throws IOException {
        final int state = this.state;
        if (allAreSet(state, FLAG_CLOSE_REQUESTED)) {
            return; // idempotent
        }
        this.state = state | FLAG_CLOSE_REQUESTED;
        if (allAreSet(state, FLAG_PASS_CLOSE)) {
            delegate.shutdownWrites();
        }
    }

    public void awaitWritable() throws IOException {
        delegate.awaitWritable();
    }

    public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
        delegate.awaitWritable(time, timeUnit);
    }

    public boolean isOpen() {
        return allAreClear(state, FLAG_CLOSE_REQUESTED);
    }

    public void close() throws IOException {
        final int state = this.state;
        if (allAreSet(state, FLAG_CLOSE_COMPLETE)) {
            return; // idempotent
        }
        this.state = state | FLAG_CLOSE_REQUESTED | FLAG_CLOSE_COMPLETE;
        try {
            final long count = this.count;
            if (count != 0) {
                if (allAreSet(state, FLAG_PASS_CLOSE)) {
                    safeClose(delegate);
                }
                throw msg.fixedUnderflow(count);
            }
            if (allAreSet(state, FLAG_PASS_CLOSE)) {
                delegate.close();
            }
        } finally {
            callClosed();
            callFinish();
        }
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

    /**
     * Get the number of remaining bytes in this fixed length channel.
     *
     * @return the number of remaining bytes
     */
    public long getRemaining() {
        return count;
    }

    private void callFinish() {
        ChannelListeners.invokeChannelListener(this, finishListener);
    }

    private void callClosed() {
        ChannelListeners.invokeChannelListener(this, closeListener);
    }
}
