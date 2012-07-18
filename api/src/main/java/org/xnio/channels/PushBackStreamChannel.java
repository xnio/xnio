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
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import org.xnio.Buffers;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.Option;
import org.xnio.Pooled;
import org.xnio.XnioExecutor;
import org.xnio.XnioWorker;

/**
 * A stream source channel which can have data pushed back into it.  Note that waiting readers will NOT be interrupted
 * when data is pushed back; therefore data should only be pushed back at points when no waiters are expected to exist.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public class PushBackStreamChannel implements StreamSourceChannel, WrappedChannel<StreamSourceChannel> {

    private final StreamSourceChannel firstChannel;
    private volatile StreamSourceChannel channel;
    @SuppressWarnings("unused")
    private volatile int flags;

    private final ChannelListener.Setter<PushBackStreamChannel> readSetter;
    private final ChannelListener.Setter<PushBackStreamChannel> closeSetter;

    private static final AtomicReferenceFieldUpdater<PushBackStreamChannel, StreamSourceChannel> channelUpdater = AtomicReferenceFieldUpdater.newUpdater(PushBackStreamChannel.class, StreamSourceChannel.class, "channel");
    private static final AtomicIntegerFieldUpdater<PushBackStreamChannel> flagsUpdater = AtomicIntegerFieldUpdater.newUpdater(PushBackStreamChannel.class, "flags");

    private static final int FLAG_ENTERED = 1;

    /**
     * Construct a new instance.
     *
     * @param channel the channel to wrap
     */
    public PushBackStreamChannel(final StreamSourceChannel channel) {
        this.channel = firstChannel = channel;
        readSetter = ChannelListeners.getDelegatingSetter(firstChannel.getReadSetter(), this);
        closeSetter = ChannelListeners.getDelegatingSetter(firstChannel.getCloseSetter(), this);
    }

    private void enter() {
        if (! flagsUpdater.compareAndSet(this, 0, FLAG_ENTERED)) {
            throw new ConcurrentStreamChannelAccessException();
        }
    }

    private void exit() {
        flagsUpdater.set(this, 0);
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
            return 0;
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
            return -1;
        }
        return channel.read(dsts);
    }

    public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        final StreamSourceChannel channel = this.channel;
        if (channel == null) {
            return -1;
        }
        return channel.read(dsts, offset, length);
    }

    /**
     * Re-queue the given pooled buffer into this channel.  This method transfers ownership of the given buffer
     * to this channel.  The buffer should be flipped for emptying.
     *
     * @param buffer the buffer to re-queue
     */
    public void unget(Pooled<ByteBuffer> buffer) {
        StreamSourceChannel old;
        do {
            old = channel;
            if (old == null) {
                buffer.free();
                return;
            }
        } while (! channelUpdater.compareAndSet(this, old, new BufferHolder(old, buffer)));
    }

    public ChannelListener.Setter<? extends PushBackStreamChannel> getReadSetter() {
        return readSetter;
    }

    public ChannelListener.Setter<? extends PushBackStreamChannel> getCloseSetter() {
        return closeSetter;
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
        final StreamSourceChannel old = channelUpdater.getAndSet(this, null);
        if (old != null) {
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

    public XnioExecutor getReadThread() {
        return firstChannel.getReadThread();
    }

    public XnioWorker getWorker() {
        return firstChannel.getWorker();
    }

    public boolean isOpen() {
        return firstChannel.isOpen();
    }

    public void close() throws IOException {
        final StreamSourceChannel old = channelUpdater.getAndSet(this, null);
        if (old != null) {
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
        private final Pooled<ByteBuffer> buffer;

        BufferHolder(final StreamSourceChannel next, final Pooled<ByteBuffer> buffer) {
            this.next = next;
            this.buffer = buffer;
        }

        public long transferTo(long position, long count, FileChannel target) throws IOException {
            long cnt;
            enter();
            try {
                final ByteBuffer src;
                try {
                    src = buffer.getResource();
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
                            channelUpdater.compareAndSet(PushBackStreamChannel.this, this, next);
                            buffer.free();
                        } else {
                            return cnt;
                        }
                        position += cnt;
                        count -= cnt;
                    }
                } catch (IllegalStateException ignored) {
                    channelUpdater.compareAndSet(PushBackStreamChannel.this, this, next);
                    cnt = 0L;
                }
            } finally {
                exit();
            }
            return cnt + next.transferTo(position, count, target);
        }

        public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
            long cnt;
            enter();
            try {
                throughBuffer.clear();
                final ByteBuffer src;
                try {
                    src = buffer.getResource();
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
                            channelUpdater.compareAndSet(PushBackStreamChannel.this, this, next);
                            buffer.free();
                        } else {
                            return cnt;
                        }
                    }
                } catch (IllegalStateException ignored) {
                    channelUpdater.compareAndSet(PushBackStreamChannel.this, this, next);
                    cnt = 0L;
                }
            } finally {
                exit();
            }
            final long res = next.transferTo(count - cnt, throughBuffer, target);
            return res > 0L ? cnt + res : cnt > 0L ? cnt : res;
        }

        public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
            long cnt;
            if (! Buffers.hasRemaining(dsts, offset, length)) {
                return 0L;
            }
            enter();
            try {
                final ByteBuffer src;
                try {
                    src = buffer.getResource();
                    cnt = Buffers.copy(dsts, offset, length, src);
                    if (src.hasRemaining()) {
                        return cnt;
                    }
                    channelUpdater.compareAndSet(PushBackStreamChannel.this, this, next);
                    buffer.free();
                } catch (IllegalStateException ignored) {
                    channelUpdater.compareAndSet(PushBackStreamChannel.this, this, next);
                    cnt = 0;
                }
            } finally {
                exit();
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
            enter();
            try {
                final ByteBuffer src;
                try {
                    src = buffer.getResource();
                    cnt = Buffers.copy(dst, src);
                    if (src.hasRemaining()) {
                        return cnt;
                    }
                    channelUpdater.compareAndSet(PushBackStreamChannel.this, this, next);
                    buffer.free();
                } catch (IllegalStateException ignored) {
                    channelUpdater.compareAndSet(PushBackStreamChannel.this, this, next);
                    cnt = 0;
                }
            } finally {
                exit();
            }
            final int res = next.read(dst);
            return res > 0 ? res + cnt : cnt > 0 ? cnt : res;
        }

        public void close() throws IOException {
            enter();
            try {
                buffer.free();
            } finally {
                exit();
            }
            next.close();
        }

        public void resumeReads() {
            // reads are always ready in this case
            firstChannel.wakeupReads();
        }

        public void shutdownReads() throws IOException {
            enter();
            try {
                buffer.free();
            } finally {
                exit();
            }
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

        public XnioExecutor getReadThread() {
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
    }
}
