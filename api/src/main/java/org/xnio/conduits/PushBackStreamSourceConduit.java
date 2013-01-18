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
import java.util.concurrent.TimeUnit;
import org.xnio.Buffers;
import org.xnio.Pooled;
import org.xnio.channels.StreamSinkChannel;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class PushBackStreamSourceConduit extends AbstractStreamSourceConduit<StreamSourceConduit> implements StreamSourceConduit {
    private StreamSourceConduit current = next;
    private boolean shutdown;

    public PushBackStreamSourceConduit(final StreamSourceConduit next) {
        super(next);
    }

    public void resumeReads() {
        current.resumeReads();
    }

    public int read(final ByteBuffer dst) throws IOException {
        return current.read(dst);
    }

    public long read(final ByteBuffer[] dsts, final int offs, final int len) throws IOException {
        return current.read(dsts, offs, len);
    }

    public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        return current.transferTo(position, count, target);
    }

    public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
        return current.transferTo(count, throughBuffer, target);
    }

    public void awaitReadable() throws IOException {
        current.awaitReadable();
    }

    public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
        current.awaitReadable(time, timeUnit);
    }

    public void terminateReads() throws IOException {
        shutdown = true;
        current.terminateReads();
    }

    public void setReadReadyHandler(final ReadReadyHandler handler) {
        current.setReadReadyHandler(handler);
    }

    public void pushBack(Pooled<ByteBuffer> pooledBuffer) {
        if (pooledBuffer == null) {
            return;
        }
        if (shutdown || ! pooledBuffer.getResource().hasRemaining()) {
            pooledBuffer.free();
        } else {
            current = new BufferConduit(current, pooledBuffer);
        }
    }

    class BufferConduit extends AbstractStreamSourceConduit<StreamSourceConduit> implements StreamSourceConduit {

        private final Pooled<ByteBuffer> pooledBuffer;

        BufferConduit(final StreamSourceConduit next, final Pooled<ByteBuffer> pooledBuffer) {
            super(next);
            this.pooledBuffer = pooledBuffer;
        }

        public void resumeReads() {
            next.wakeupReads();
        }

        public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
            // readable
        }

        public void awaitReadable() throws IOException {
            // readable
        }

        public int read(final ByteBuffer dst) throws IOException {
            int cnt;
            if (! dst.hasRemaining()) {
                return 0;
            }
            final StreamSourceConduit next = this.next;
            try {
                final ByteBuffer src = pooledBuffer.getResource();
                cnt = Buffers.copy(dst, src);
                if (src.hasRemaining()) {
                    return cnt;
                }
                current = next;
                pooledBuffer.free();
                if (cnt > 0 && next == PushBackStreamSourceConduit.this.next) {
                    // don't hit the main channel until the user wants to
                    return cnt;
                }
            } catch (IllegalStateException ignored) {
                current = next;
                cnt = 0;
            }
            final int res = next.read(dst);
            return res > 0 ? res + cnt : cnt > 0 ? cnt : res;
        }

        public long read(final ByteBuffer[] dsts, final int offs, final int len) throws IOException {
            long cnt;
            final StreamSourceConduit next = this.next;
            try {
                final ByteBuffer src = pooledBuffer.getResource();
                cnt = Buffers.copy(dsts, offs, len, src);
                if (src.hasRemaining()) {
                    return cnt;
                }
                current = next;
                pooledBuffer.free();
                if (cnt > 0L && next == PushBackStreamSourceConduit.this.next) {
                    // don't hit the main channel until the user wants to
                    return cnt;
                }
            } catch (IllegalStateException ignored) {
                current = next;
                cnt = 0;
            }
            final long res = next.read(dsts, offs, len);
            return res > 0 ? res + cnt : cnt > 0 ? cnt : res;
        }

        public long transferTo(long position, long count, final FileChannel target) throws IOException {
            long cnt;
            final ByteBuffer src;
            try {
                src = pooledBuffer.getResource();
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
                        current = next;
                        pooledBuffer.free();
                    } else {
                        return cnt;
                    }
                    position += cnt;
                    count -= cnt;
                }
            } catch (IllegalStateException ignored) {
                current = next;
                cnt = 0L;
            }
            return cnt + next.transferTo(position, count, target);
        }

        public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
            long cnt;
            throughBuffer.clear();
            final ByteBuffer src;
            try {
                src = pooledBuffer.getResource();
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
                        current = next;
                        pooledBuffer.free();
                    } else {
                        return cnt;
                    }
                }
            } catch (IllegalStateException ignored) {
                current = next;
                cnt = 0L;
            }
            final long res = next.transferTo(count - cnt, throughBuffer, target);
            return res > 0L ? cnt + res : cnt > 0L ? cnt : res;
        }
    }
}
