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

package org.xnio.conduits;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import org.xnio.Buffers;
import org.xnio.Pooled;
import org.xnio.channels.StreamSourceChannel;

/**
 * A stream sink conduit that buffers output data.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class BufferedStreamSinkConduit extends AbstractStreamSinkConduit<StreamSinkConduit> {

    private final Pooled<ByteBuffer> pooledBuffer;
    private boolean terminate;

    /**
     * Construct a new instance.
     *
     * @param next the delegate conduit to set
     * @param pooledBuffer the pooled buffer to use
     */
    public BufferedStreamSinkConduit(final StreamSinkConduit next, final Pooled<ByteBuffer> pooledBuffer) {
        super(next);
        this.pooledBuffer = pooledBuffer;
    }

    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        return flushLocal() ? super.transferFrom(src, position, count) : 0L;
    }

    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        // todo: optimize to include our buffer in the copies
        if (flushLocal()) {
            return super.transferFrom(source, count, throughBuffer);
        } else {
            throughBuffer.limit(0);
            return 0L;
        }
    }

    public int write(final ByteBuffer src) throws IOException {
        try {
            final ByteBuffer buffer = pooledBuffer.getResource();
            final int pos = buffer.position();
            final int lim = buffer.limit();
            final int srcRem = src.remaining();
            final int ourRem = lim - pos;
            if (srcRem < ourRem) {
                buffer.put(src);
                return srcRem;
            } else if (buffer.position() == 0) {
                final int res = super.write(src);
                if (srcRem > res) {
                    final int cnt = Buffers.copy(buffer, src);
                    return res + cnt;
                } else {
                    return res;
                }
            } else {
                buffer.flip();
                try {
                    super.write(new ByteBuffer[] { buffer, src }, 0, 2);
                } finally {
                    buffer.compact();
                }
                if (src.hasRemaining()) {
                    Buffers.copy(buffer, src);
                }
                return srcRem - src.remaining();
            }
        } catch (IllegalStateException ignored) {
            throw new ClosedChannelException();
        }
    }

    public long write(final ByteBuffer[] srcs, final int offs, final int len) throws IOException {
        if (len == 0) {
            return 0L;
        } else if (len == 1) {
            return write(srcs[offs]);
        } else try {
            final ByteBuffer buffer = pooledBuffer.getResource();
            final int pos = buffer.position();
            final int lim = buffer.limit();
            final long srcRem = Buffers.remaining(srcs, offs, len);
            final int ourRem = lim - pos;
            if (srcRem < ourRem) {
                for (int i = 0; i < len; i++) {
                    buffer.put(srcs[i]);
                }
                return srcRem;
            } else if (buffer.position() == 0) {
                final long res = super.write(srcs, offs, len);
                if (srcRem > res) {
                    final int cnt = Buffers.copy(buffer, srcs, offs, len);
                    return res + cnt;
                } else {
                    return res;
                }
            } else {
                buffer.flip();
                try {
                    final ByteBuffer[] buffers;
                    if (offs > 0) {
                        buffers = Arrays.copyOfRange(srcs, offs - 1, offs + len);
                    } else {
                        buffers = new ByteBuffer[len + 1];
                        System.arraycopy(srcs, offs, buffers, 1, len);
                    }
                    buffers[0] = buffer;
                    super.write(buffers, 0, buffers.length);
                } finally {
                    buffer.compact();
                }
                Buffers.copy(buffer, srcs, offs, len);
                return srcRem - Buffers.remaining(srcs, offs, len);
            }
        } catch (IllegalStateException ignored) {
            throw new ClosedChannelException();
        }
    }

    private boolean flushLocal() throws IOException {
        try {
            final ByteBuffer buffer = pooledBuffer.getResource();
            if (buffer.position() > 0) {
                buffer.flip();
                try {
                    for (;;) {
                        super.write(buffer);
                        if (! buffer.hasRemaining()) {
                            if (terminate) {
                                pooledBuffer.free();
                            }
                            return true;
                        }
                    }
                } finally {
                    buffer.compact();
                }
            } else {
                return true;
            }
        } catch (IllegalStateException ignored) {
            return true;
        }
    }

    @Override
    public int writeFinal(ByteBuffer src) throws IOException {
        //todo: non-naive implementations of this
        return Conduits.writeFinalBasic(this, src);
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs, int offset, int length) throws IOException {
        //todo: non-naive implementations of this
        return Conduits.writeFinalBasic(this, srcs, offset, length);
    }

    public boolean flush() throws IOException {
        return flushLocal() && super.flush();
    }

    public void truncateWrites() throws IOException {
        pooledBuffer.free();
        super.truncateWrites();
    }

    public void terminateWrites() throws IOException {
        terminate = true;
        super.terminateWrites();
    }
}
