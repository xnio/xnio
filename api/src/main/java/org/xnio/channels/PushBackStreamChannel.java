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
import org.xnio.Buffers;
import org.xnio.ByteBufferPool;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.Option;
import org.xnio.Pooled;
import org.xnio.XnioExecutor;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;

/**
 * A stream source channel which can have data pushed back into it.  Note that waiting readers will NOT be interrupted
 * when data is pushed back; therefore data should only be pushed back at points when no waiters are expected to exist.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class PushBackStreamChannel implements StreamSourceChannel, WrappedChannel<StreamSourceChannel> {

    private final StreamSourceChannel firstChannel;
    private StreamSourceChannel channel;

    private ChannelListener<? super PushBackStreamChannel> readListener;
    private ChannelListener<? super PushBackStreamChannel> closeListener;

    /**
     * Construct a new instance.
     *
     * @param channel the channel to wrap
     */
    public PushBackStreamChannel(final StreamSourceChannel channel) {
        this.channel = firstChannel = channel;
        firstChannel.getReadSetter().set(new ChannelListener<StreamSourceChannel>() {
            public void handleEvent(final StreamSourceChannel channel) {
                ChannelListeners.invokeChannelListener(PushBackStreamChannel.this, readListener);
            }
        });
        firstChannel.getCloseSetter().set(new ChannelListener<StreamSourceChannel>() {
            public void handleEvent(final StreamSourceChannel channel) {
                ChannelListeners.invokeChannelListener(PushBackStreamChannel.this, closeListener);
            }
        });
    }

    public void setReadListener(final ChannelListener<? super PushBackStreamChannel> readListener) {
        this.readListener = readListener;
    }

    public void setCloseListener(final ChannelListener<? super PushBackStreamChannel> closeListener) {
        this.closeListener = closeListener;
    }

    public ChannelListener.Setter<? extends PushBackStreamChannel> getReadSetter() {
        return new ChannelListener.Setter<PushBackStreamChannel>() {
            public void set(final ChannelListener<? super PushBackStreamChannel> listener) {
                setReadListener(listener);
            }
        };
    }

    public ChannelListener.Setter<? extends PushBackStreamChannel> getCloseSetter() {
        return new ChannelListener.Setter<PushBackStreamChannel>() {
            public void set(final ChannelListener<? super PushBackStreamChannel> listener) {
                setCloseListener(listener);
            }
        };
    }

    public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        final StreamSourceChannel channel = this.channel;
        if (channel == null) {
            return 0;
        }
        return channel.transferTo(position, count, target);
    }

    public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
        final StreamSourceChannel channel = this.channel;
        if (channel == null) {
            return -1L;
        }
        return channel.transferTo(count, throughBuffer, target);
    }

    public int read(final ByteBuffer dst) throws IOException {
        final StreamSourceChannel channel = this.channel;
        if (channel == null) {
            return -1;
        }
        return channel.read(dst);
    }

    public long read(final ByteBuffer[] dsts) throws IOException {
        final StreamSourceChannel channel = this.channel;
        if (channel == null) {
            return -1L;
        }
        return channel.read(dsts);
    }

    public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        final StreamSourceChannel channel = this.channel;
        if (channel == null) {
            return -1L;
        }
        return channel.read(dsts, offset, length);
    }

    /**
     * Re-queue the given pooled buffer into this channel.  This method transfers ownership of the given buffer
     * to this channel.  The buffer should be flipped for emptying.
     *
     * @param buffer the buffer to re-queue
     * @deprecated Use {@link #unget(ByteBuffer)} instead.
     */
    @Deprecated
    public void unget(Pooled<ByteBuffer> buffer) {
        StreamSourceChannel old;
        old = channel;
        if (old == null) {
            buffer.free();
            return;
        }
        channel = new BufferHolder(old, buffer);
    }

    /**
     * Re-queue the given pooled buffer into this channel. This method transfers ownership of the given buffer
     * to this channel. The buffer should be flipped for emptying.
     *
     * @param buffer the buffer to re-queue
     */
    public void unget(ByteBuffer buffer) {
        StreamSourceChannel old;
        old = channel;
        if (old == null) {
            ByteBufferPool.free(buffer);
            return;
        }
        channel = new BufferHolder(old, buffer);
    }

    public void suspendReads() {
        firstChannel.suspendReads();
    }

    public void resumeReads() {
        final StreamSourceChannel channel = this.channel;
        if (channel != null) {
            channel.resumeReads();
        }
    }

    public boolean isReadResumed() {
        return firstChannel.isReadResumed();
    }

    public void wakeupReads() {
        firstChannel.wakeupReads();
    }

    public void shutdownReads() throws IOException {
        final StreamSourceChannel old = channel;
        if (old != null) {
            channel = null;
            old.shutdownReads();
        }
    }

    public void awaitReadable() throws IOException {
        final StreamSourceChannel channel = this.channel;
        if (channel != null) {
            channel.awaitReadable();
        }
    }

    public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
        final StreamSourceChannel channel = this.channel;
        if (channel != null) {
            channel.awaitReadable(time, timeUnit);
        }
    }

    @Deprecated
    public XnioExecutor getReadThread() {
        return firstChannel.getReadThread();
    }

    public XnioIoThread getIoThread() {
        return firstChannel.getIoThread();
    }

    public XnioWorker getWorker() {
        return firstChannel.getWorker();
    }

    public boolean isOpen() {
        return firstChannel.isOpen();
    }

    public void close() throws IOException {
        final StreamSourceChannel old = channel;
        if (old != null) {
            channel = null;
            old.close();
        }
    }

    public boolean supportsOption(final Option<?> option) {
        return firstChannel.supportsOption(option);
    }

    public <T> T getOption(final Option<T> option) throws IOException {
        return firstChannel.getOption(option);
    }

    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        return firstChannel.setOption(option, value);
    }

    public StreamSourceChannel getChannel() {
        return firstChannel;
    }

    class BufferHolder implements StreamSourceChannel {
        private final StreamSourceChannel next;
        @Deprecated
        private final Pooled<ByteBuffer> buffer;
        private final ByteBuffer newBuffer;

        @Deprecated
        BufferHolder(final StreamSourceChannel next, final Pooled<ByteBuffer> buffer) {
            this.next = next;
            this.buffer = buffer;
            this.newBuffer = null;
        }

        BufferHolder(final StreamSourceChannel next, ByteBuffer buffer) {
            this.next = next;
            this.buffer = null;
            this.newBuffer = buffer;
        }

        public long transferTo(long position, long count, FileChannel target) throws IOException {
            long cnt;
            final ByteBuffer src;
            try {
                src = getBuffer();
                final int pos = src.position();
                final int rem = src.remaining();
                if (rem > count) try {
                    // partial empty of our buffer
                    src.limit(pos + (int) count);
                    return target.write(src, position);
                } finally {
                    src.limit(pos + rem);
                } else {
                    // full empty of our buffer
                    cnt = target.write(src, position);
                    if (cnt == rem) {
                        // we emptied our buffer
                        moveToNext();
                    } else {
                        return cnt;
                    }
                    position += cnt;
                    count -= cnt;
                }
            } catch (IllegalStateException ignored) {
                moveToNext();
                cnt = 0L;
            }
            return cnt + next.transferTo(position, count, target);
        }

        public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
            long cnt;
            throughBuffer.clear();
            final ByteBuffer src;
            try {
                src = getBuffer();
                final int pos = src.position();
                final int rem = src.remaining();
                if (rem > count) try {
                    // partial empty of our buffer
                    src.limit(pos + (int) count);
                    throughBuffer.limit(0);
                    return target.write(src);
                } finally {
                    src.limit(pos + rem);
                } else {
                    // full empty of our buffer
                    cnt = target.write(src);
                    if (cnt == rem) {
                        // we emptied our buffer
                        moveToNext();
                    } else {
                        return cnt;
                    }
                }
            } catch (IllegalStateException ignored) {
                moveToNext();
                cnt = 0L;
            }
            final long res = next.transferTo(count - cnt, throughBuffer, target);
            return res > 0L ? cnt + res : cnt > 0L ? cnt : res;
        }

        public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
            long cnt;
            try {
                final ByteBuffer src = getBuffer();
                cnt = Buffers.copy(dsts, offset, length, src);
                if (src.hasRemaining()) {
                    return cnt;
                }
                final StreamSourceChannel next = channel = this.next;
                freeBuffer();
                if (cnt > 0L && next == firstChannel) {
                    // don't hit the main channel until the user wants to
                    return cnt;
                }
            } catch (IllegalStateException ignored) {
                moveToNext();
                cnt = 0;
            }
            final long res = next.read(dsts, offset, length);
            return res > 0 ? res + cnt : cnt > 0 ? cnt : res;
        }

        public long read(final ByteBuffer[] dsts) throws IOException {
            return read(dsts, 0, dsts.length);
        }

        public int read(final ByteBuffer dst) throws IOException {
            int cnt;
            if (! dst.hasRemaining()) {
                return 0;
            }
            try {
                final ByteBuffer src = getBuffer();
                cnt = Buffers.copy(dst, src);
                if (src.hasRemaining()) {
                    return cnt;
                }
                final StreamSourceChannel next = moveToNext();
                if (cnt > 0 && next == firstChannel) {
                    // don't hit the main channel until the user wants to
                    return cnt;
                }
            } catch (IllegalStateException ignored) {
                moveToNext();
                cnt = 0;
            }
            final int res = next.read(dst);
            return res > 0 ? res + cnt : cnt > 0 ? cnt : res;
        }

        public void close() throws IOException {
            freeBuffer();
            next.close();
        }

        public void resumeReads() {
            // reads are always ready in this case
            firstChannel.wakeupReads();
        }

        public void shutdownReads() throws IOException {
            freeBuffer();
            next.shutdownReads();
        }

        public void awaitReadable() throws IOException {
            // return immediately
        }

        public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
            // return immediately
        }

        // unused methods

        public boolean isOpen() {
            throw new UnsupportedOperationException();
        }

        public ChannelListener.Setter<? extends StreamSourceChannel> getReadSetter() {
            throw new UnsupportedOperationException();
        }

        public ChannelListener.Setter<? extends StreamSourceChannel> getCloseSetter() {
            throw new UnsupportedOperationException();
        }

        public void suspendReads() {
            throw new UnsupportedOperationException();
        }

        public boolean isReadResumed() {
            throw new UnsupportedOperationException();
        }

        public void wakeupReads() {
            throw new UnsupportedOperationException();
        }

        @Deprecated
        public XnioExecutor getReadThread() {
            throw new UnsupportedOperationException();
        }

        public XnioIoThread getIoThread() {
            throw new UnsupportedOperationException();
        }

        public XnioWorker getWorker() {
            throw new UnsupportedOperationException();
        }

        public boolean supportsOption(final Option<?> option) {
            throw new UnsupportedOperationException();
        }

        public <T> T getOption(final Option<T> option) throws IOException {
            throw new UnsupportedOperationException();
        }

        public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
            throw new UnsupportedOperationException();
        }

        private ByteBuffer getBuffer() {
            return newBuffer != null ? newBuffer : buffer.getResource();
        }

        private void freeBuffer() {
            if (newBuffer != null) {
                ByteBufferPool.free(newBuffer);
            } else {
                buffer.free();
            }
        }

        private StreamSourceChannel moveToNext() {
            freeBuffer();
            return channel = next;
        }
    }
}
