/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2013 Red Hat, Inc. and/or its affiliates.
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

package org.xnio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;
import java.util.zip.Deflater;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class DeflateStreamSinkChannel implements StreamSinkChannel {

    private static final byte[] NO_BYTES = new byte[0];
    private final StreamSinkChannel delegate;
    private final Deflater deflater;
    private final ByteBuffer outBuffer;

    private ChannelListener<? super DeflateStreamSinkChannel> writeListener;
    private ChannelListener<? super DeflateStreamSinkChannel> closeListener;

    DeflateStreamSinkChannel(final StreamSinkChannel delegate, final Deflater deflater) {
        this.delegate = delegate;
        this.deflater = deflater;
        delegate.getWriteSetter().set(new ChannelListener<StreamSinkChannel>() {
            public void handleEvent(final StreamSinkChannel channel) {
                ChannelListeners.invokeChannelListener(DeflateStreamSinkChannel.this, writeListener);
            }
        });
        delegate.getCloseSetter().set(new ChannelListener<StreamSinkChannel>() {
            public void handleEvent(final StreamSinkChannel channel) {
                ChannelListeners.invokeChannelListener(DeflateStreamSinkChannel.this, closeListener);
            }
        });
        outBuffer = ByteBuffer.allocate(16384);
    }

    public boolean isOpen() {
        return delegate.isOpen();
    }

    public void setWriteListener(final ChannelListener<? super DeflateStreamSinkChannel> writeListener) {
        this.writeListener = writeListener;
    }

    public void setCloseListener(final ChannelListener<? super DeflateStreamSinkChannel> closeListener) {
        this.closeListener = closeListener;
    }

    public ChannelListener.Setter<? extends StreamSinkChannel> getWriteSetter() {
        return new ChannelListener.Setter<StreamSinkChannel>() {
            public void set(final ChannelListener<? super StreamSinkChannel> listener) {
                setWriteListener(listener);
            }
        };
    }

    public ChannelListener.Setter<? extends StreamSinkChannel> getCloseSetter() {
        return new ChannelListener.Setter<StreamSinkChannel>() {
            public void set(final ChannelListener<? super StreamSinkChannel> listener) {
                setCloseListener(listener);
            }
        };
    }

    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        return src.transferTo(position, count, this);
    }

    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        return IoUtils.transfer(source, count, throughBuffer, this);
    }

    public int write(final ByteBuffer src) throws IOException {
        final ByteBuffer outBuffer = this.outBuffer;
        final byte[] outArray = outBuffer.array();
        final Deflater deflater = this.deflater;
        assert outBuffer.arrayOffset() == 0;
        int cnt = 0;
        int rem;
        int c1, t;
        int pos;
        while ((rem = src.remaining()) > 0) {
            if (! outBuffer.hasRemaining()) {
                outBuffer.flip();
                try {
                    if (delegate.write(outBuffer) == 0) {
                        return cnt;
                    }
                } finally {
                    outBuffer.compact();
                }
            }
            pos = src.position();
            if (src.hasArray()) {
                final byte[] array = src.array();
                final int arrayOffset = src.arrayOffset();
                deflater.setInput(array, arrayOffset + pos, rem);
                c1 = deflater.getTotalIn();
                final int dc = deflater.deflate(outArray, outBuffer.position(), outBuffer.remaining());
                outBuffer.position(outBuffer.position() + dc);
                t = deflater.getTotalIn() - c1;
                src.position(pos + t);
                cnt += t;
            } else {
                final byte[] bytes = Buffers.take(src);
                deflater.setInput(bytes);
                c1 = deflater.getTotalIn();
                final int dc = deflater.deflate(outArray, outBuffer.position(), outBuffer.remaining());
                outBuffer.position(outBuffer.position() + dc);
                t = deflater.getTotalIn() - c1;
                src.position(pos + t);
                cnt += t;
            }
        }
        return cnt;
    }

    public long write(final ByteBuffer[] srcs) throws IOException {
        return write(srcs, 0, srcs.length);
    }

    public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        final ByteBuffer outBuffer = this.outBuffer;
        final byte[] outArray = outBuffer.array();
        final Deflater deflater = this.deflater;
        assert outBuffer.arrayOffset() == 0;
        long cnt = 0;
        int rem;
        int c1, t;
        int pos;
        for (int i = 0; i < length; i ++) {
            final ByteBuffer src = srcs[i + offset];
            while ((rem = src.remaining()) > 0) {
                if (! outBuffer.hasRemaining()) {
                    outBuffer.flip();
                    try {
                        if (delegate.write(outBuffer) == 0) {
                            return cnt;
                        }
                    } finally {
                        outBuffer.compact();
                    }
                }
                pos = src.position();
                if (src.hasArray()) {
                    final byte[] array = src.array();
                    final int arrayOffset = src.arrayOffset();
                    deflater.setInput(array, arrayOffset + pos, rem);
                    c1 = deflater.getTotalIn();
                    final int dc = deflater.deflate(outArray, outBuffer.position(), outBuffer.remaining());
                    outBuffer.position(outBuffer.position() + dc);
                    t = deflater.getTotalIn() - c1;
                    src.position(pos + t);
                    cnt += t;
                } else {
                    final byte[] bytes = Buffers.take(src);
                    deflater.setInput(bytes);
                    c1 = deflater.getTotalIn();
                    final int dc = deflater.deflate(outArray, outBuffer.position(), outBuffer.remaining());
                    outBuffer.position(outBuffer.position() + dc);
                    t = deflater.getTotalIn() - c1;
                    src.position(pos + t);
                    cnt += t;
                }
            }
        }
        return cnt;
    }

    public boolean flush() throws IOException {
        final ByteBuffer outBuffer = this.outBuffer;
        final byte[] outArray = outBuffer.array();
        final Deflater deflater = this.deflater;
        final StreamSinkChannel delegate = this.delegate;
        assert outBuffer.arrayOffset() == 0;
        int res;
        int rem;
        int pos;
        deflater.setInput(NO_BYTES);
        for (;;) {
            rem = outBuffer.remaining();
            pos = outBuffer.position();
            res = deflater.deflate(outArray, pos, rem, Deflater.SYNC_FLUSH);
            if (pos == 0 && res == rem) {
                // hmm, we're stuck (shouldn't be possible)
                throw new IOException("Cannot flush due to insufficient buffer space");
            } else {
                if (res > 0) {
                    outBuffer.flip();
                    try {
                        if (delegate.write(outBuffer) == 0) {
                            return false;
                        }
                    } finally {
                        outBuffer.compact();
                    }
                } else if (deflater.needsInput() && pos == 0) {
                    if (deflater.finished()) {
                        // idempotent
                        delegate.shutdownWrites();
                    }
                    return delegate.flush();
                } else {
                    throw new IOException("Deflater doesn't need input, but won't produce output");
                }
            }
        }
    }

    public void suspendWrites() {
        delegate.suspendWrites();
    }

    public void resumeWrites() {
        delegate.resumeWrites();
    }

    public boolean isWriteResumed() {
        return delegate.isWriteResumed();
    }

    public void wakeupWrites() {
        delegate.wakeupWrites();
    }

    public void shutdownWrites() throws IOException {
        deflater.finish();
    }

    public void awaitWritable() throws IOException {
        delegate.awaitWritable();
    }

    public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
        delegate.awaitWritable(time, timeUnit);
    }

    public XnioExecutor getWriteThread() {
        return delegate.getWriteThread();
    }

    public XnioWorker getWorker() {
        return delegate.getWorker();
    }

    public void close() throws IOException {
        deflater.finish();
        delegate.close();
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
}
