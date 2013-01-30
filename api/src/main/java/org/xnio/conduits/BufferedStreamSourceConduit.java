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
import java.nio.channels.FileChannel;
import java.util.Arrays;
import org.xnio.Buffers;
import org.xnio.Pooled;
import org.xnio.channels.StreamSinkChannel;

/**
 * A stream source conduit which buffers input.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class BufferedStreamSourceConduit extends AbstractStreamSourceConduit<StreamSourceConduit> {
    private final Pooled<ByteBuffer> pooledBuffer;

    /**
     * Construct a new instance.
     *
     * @param next the delegate conduit to set
     * @param pooledBuffer the buffer to use
     */
    public BufferedStreamSourceConduit(final StreamSourceConduit next, final Pooled<ByteBuffer> pooledBuffer) {
        super(next);
        this.pooledBuffer = pooledBuffer;
    }

    public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        try {
            final ByteBuffer buffer = pooledBuffer.getResource();
            final int lim = buffer.limit();
            final int pos = buffer.position();
            final int rem = lim - pos;
            if (rem > 0) {
                if ((long)rem > count) {
                    buffer.limit(pos + (int)count);
                    try {
                        return target.write(buffer, position);
                    } finally {
                        buffer.limit(lim);
                    }
                } else {
                    return target.write(buffer, position);
                }
            } else {
                return super.transferTo(position, count, target);
            }
        } catch (IllegalStateException ignored) {
            return 0L;
        }
    }

    public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
        try {
            final ByteBuffer buffer = pooledBuffer.getResource();
            int lim;
            int pos;
            int rem;
            lim = buffer.limit();
            pos = buffer.position();
            rem = lim - pos;
            throughBuffer.clear();
            int res;
            long t = 0L;
            while (rem > 0) {
                if ((long)rem > count) {
                    buffer.limit(pos + (int)count);
                    try {
                        t += res = target.write(buffer);
                    } finally {
                        buffer.limit(lim);
                    }
                } else {
                    t += res = target.write(buffer);
                }
                if (res == 0) {
                    return t;
                }
                pos = buffer.position();
                rem = lim - pos;
            }
            final long lres = Conduits.transfer(next, count, throughBuffer, target);
            if (lres > 0) t += lres;
            return t == 0L && lres == -1L ? -1L : t;
        } catch (IllegalStateException ignored) {
            return -1L;
        }
    }

    public int read(final ByteBuffer dst) throws IOException {
        try {
            final ByteBuffer buffer = pooledBuffer.getResource();
            final int lim = buffer.limit();
            final int pos = buffer.position();
            final int rem = lim - pos;
            if (rem > 0) {
                return Buffers.copy(dst, buffer);
            } else {
                final int dstRem = dst.remaining();
                buffer.clear();
                try {
                    final long rres = next.read(new ByteBuffer[] { dst, buffer }, 0, 2);
                    if (rres == -1L) {
                        return -1;
                    }
                } finally {
                    buffer.flip();
                }
                return dst.remaining() - dstRem;
            }
        } catch (IllegalStateException ignored) {
            return -1;
        }
    }

    public long read(final ByteBuffer[] dsts, final int offs, final int len) throws IOException {
        if (len == 0) {
            return 0L;
        } else if (len == 1) {
            return read(dsts[offs]);
        } else try {
            final ByteBuffer buffer = pooledBuffer.getResource();
            final int lim = buffer.limit();
            final int pos = buffer.position();
            final int rem = lim - pos;
            if (rem > 0) {
                return Buffers.copy(dsts, offs, len, buffer);
            } else {
                final long dstRem = Buffers.remaining(dsts, offs, len);
                buffer.clear();
                try {
                    final ByteBuffer[] buffers = Arrays.copyOfRange(dsts, offs, offs + len + 1);
                    buffers[buffers.length - 1] = buffer;
                    final long rres = next.read(buffers, 0, buffers.length);
                    if (rres == -1) {
                        return -1L;
                    }
                } finally {
                    buffer.flip();
                }
                return Buffers.remaining(dsts, offs, len) - dstRem;
            }
        } catch (IllegalStateException ignored) {
            return -1;
        }
    }

    public void terminateReads() throws IOException {
        pooledBuffer.free();
        super.terminateReads();
    }
}
