/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2015 Red Hat, Inc. and/or its affiliates.
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

import static java.lang.Math.max;

import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.wildfly.common.Assert;
import org.wildfly.common.cpu.CacheInfo;

/**
 * A fast source of pooled buffers.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class ByteBufferPool {

    private static final boolean sliceLargeBuffers;

    static {
        sliceLargeBuffers = Boolean.parseBoolean(System.getProperty("xnio.buffer.slice-large-buffers", "true"));
    }

    private final ConcurrentLinkedQueue<ByteBuffer> masterQueue = new ConcurrentLinkedQueue<>();
    private final LocalBufferCacheThreadLocal threadLocal = new LocalBufferCacheThreadLocal(this);
    private final int size;
    private final boolean direct;

    ByteBufferPool(final int size, final boolean direct) {
        assert Integer.bitCount(size) == 1;
        assert size >= 0x10;
        assert size <= 0x4000_0000;
        this.size = size;
        this.direct = direct;
    }

    // buffer pool size constants

    /**
     * The size of large buffers.
     */
    public static final int LARGE_SIZE = 0x100000;
    /**
     * The size of medium buffers.
     */
    public static final int MEDIUM_SIZE = 0x2000;
    /**
     * The size of small buffers.
     */
    public static final int SMALL_SIZE = 0x40;

    static final int LOCAL_QUEUE_SIZE = 0x10;

    static final int CACHE_LINE_SIZE = max(64, CacheInfo.getSmallestDataCacheLineSize());

    /**
     * The large direct buffer pool.  This pool produces buffers of {@link #LARGE_SIZE}.
     */
    public static final ByteBufferPool LARGE_DIRECT = create(LARGE_SIZE, true);
    /**
     * The medium direct buffer pool.  This pool produces buffers of {@link #MEDIUM_SIZE}.
     */
    public static final ByteBufferPool MEDIUM_DIRECT = sliceLargeBuffers ? subPool(LARGE_DIRECT, MEDIUM_SIZE) : create(MEDIUM_SIZE, true);
    /**
     * The small direct buffer pool.  This pool produces buffers of {@link #SMALL_SIZE}.
     */
    public static final ByteBufferPool SMALL_DIRECT = subPool(MEDIUM_DIRECT, SMALL_SIZE);
    /**
     * The large heap buffer pool.  This pool produces buffers of {@link #LARGE_SIZE}.
     */
    public static final ByteBufferPool LARGE_HEAP = create(LARGE_SIZE, false);
    /**
     * The medium heap buffer pool.  This pool produces buffers of {@link #MEDIUM_SIZE}.
     */
    public static final ByteBufferPool MEDIUM_HEAP = create(MEDIUM_SIZE, false);
    /**
     * The small heap buffer pool.  This pool produces buffers of {@link #SMALL_SIZE}.
     */
    public static final ByteBufferPool SMALL_HEAP = create(SMALL_SIZE, false);

    /**
     * A set of buffer pools for each size, which can either be {@link #DIRECT} or {@link #HEAP}.
     */
    public static final class Set {
        private final ByteBufferPool small, normal, large;

        Set(final ByteBufferPool small, final ByteBufferPool normal, final ByteBufferPool large) {
            this.small = small;
            this.normal = normal;
            this.large = large;
        }

        /**
         * Get the small buffer pool for this set.
         *
         * @return the small buffer pool for this set
         */
        public ByteBufferPool getSmall() {
            return small;
        }

        /**
         * Get the medium buffer pool for this set.
         *
         * @return the medium buffer pool for this set
         */
        public ByteBufferPool getNormal() {
            return normal;
        }

        /**
         * Get the large buffer pool for this set.
         *
         * @return the large buffer pool for this set
         */
        public ByteBufferPool getLarge() {
            return large;
        }

        /**
         * The direct buffer source set.
         */
        public static final Set DIRECT = new Set(SMALL_DIRECT, MEDIUM_DIRECT, LARGE_DIRECT);
        /**
         * The heap buffer source set.
         */
        public static final Set HEAP = new Set(SMALL_HEAP, MEDIUM_HEAP, LARGE_HEAP);
    }

    /**
     * Allocate a buffer from this source pool.  The buffer must be freed through the {@link #free(ByteBuffer)} method.
     *
     * @return the allocated buffer
     */
    public ByteBuffer allocate() {
        final LocalBufferCache localCache = threadLocal.get();
        ByteBuffer byteBuffer = localCache.queue.pollLast();
        if (byteBuffer == null) {
            ConcurrentLinkedQueue<ByteBuffer> masterQueue = this.masterQueue;
            byteBuffer = masterQueue.poll();
            if (byteBuffer == null) {
                byteBuffer = createBuffer();
            } else {
                localCache.outstanding ++;
            }
        }
        return byteBuffer;
    }

    /**
     * Bulk-allocate buffers from this pool.  The buffer must be freed through the {@link #free(ByteBuffer)} method.
     *
     * @param array the array of buffers to fill
     * @param offs the offset into the array to fill
     */
    public void allocate(ByteBuffer[] array, int offs) {
        allocate(array, offs, array.length - offs);
    }

    /**
     * Bulk-allocate buffers from this pool.  The buffer must be freed through the {@link #free(ByteBuffer)} method.
     *
     * @param array the array of buffers to fill
     * @param offs the offset into the array to fill
     * @param len the number of buffers to fill in the array
     */
    public void allocate(ByteBuffer[] array, int offs, int len) {
        Assert.checkNotNullParam("array", array);
        Assert.checkArrayBounds(array, offs, len);
        final LocalBufferCache localCache = threadLocal.get();
        int outstanding = localCache.outstanding;
        final ArrayDeque<ByteBuffer> queue = localCache.queue;
        ConcurrentLinkedQueue<ByteBuffer> masterQueue;
        ByteBuffer byteBuffer;
        for (int i = 0; i < len; i ++) {
            byteBuffer = queue.pollLast();
            if (byteBuffer == null) {
                masterQueue = this.masterQueue;
                byteBuffer = masterQueue.poll();
                if (byteBuffer == null) {
                    byteBuffer = createBuffer();
                } else {
                    outstanding ++;
                }
            }
            array[offs + i] = byteBuffer;
        }
        localCache.outstanding = outstanding;
    }

    /**
     * Free a buffer into its appropriate pool based on its size.  Care must be taken to avoid
     * returning a slice of a pooled buffer, since this could cause both the buffer and its slice
     * to be separately repooled, leading to likely data corruption.
     *
     * @param buffer the buffer to free
     */
    public static void free(ByteBuffer buffer) {
        Assert.checkNotNullParam("buffer", buffer);
        final int size = buffer.capacity();
        if (Integer.bitCount(size) == 1 && ! buffer.isReadOnly()) {
            if (buffer.isDirect()) {
                if (! (buffer instanceof MappedByteBuffer)) {
                    if (size == MEDIUM_SIZE) {
                        MEDIUM_DIRECT.doFree(buffer);
                    } else if (size == SMALL_SIZE) {
                        SMALL_DIRECT.doFree(buffer);
                    } else if (size == LARGE_SIZE) {
                        LARGE_DIRECT.doFree(buffer);
                    }
                }
            } else {
                if (size == MEDIUM_SIZE) {
                    MEDIUM_HEAP.doFree(buffer);
                } else if (size == SMALL_SIZE) {
                    SMALL_HEAP.doFree(buffer);
                } else if (size == LARGE_SIZE) {
                    LARGE_HEAP.doFree(buffer);
                }
            }
        }
    }

    /**
     * Bulk-free buffers from an array as with {@link #free(ByteBuffer)}.  The freed entries will be assigned to
     * {@code null}.
     *
     * @param array the buffer array
     * @param offs the offset into the array
     * @param len the number of buffers to free
     */
    public static void free(ByteBuffer[] array, int offs, int len) {
        Assert.checkArrayBounds(array, offs, len);
        for (int i = 0; i < len; i ++) {
            ByteBuffer buffer = array[offs + i];
            if (buffer == null) {
                continue;
            }
            final int size = buffer.capacity();
            if (Integer.bitCount(size) == 1 && ! buffer.isReadOnly()) {
                if (buffer.isDirect()) {
                    if (! (buffer instanceof MappedByteBuffer)) {
                        if (size == MEDIUM_SIZE) {
                            MEDIUM_DIRECT.doFree(buffer);
                        } else if (size == SMALL_SIZE) {
                            SMALL_DIRECT.doFree(buffer);
                        } else if (size == LARGE_SIZE) {
                            LARGE_DIRECT.doFree(buffer);
                        }
                    }
                } else {
                    if (size == MEDIUM_SIZE) {
                        MEDIUM_HEAP.doFree(buffer);
                    } else if (size == SMALL_SIZE) {
                        SMALL_HEAP.doFree(buffer);
                    } else if (size == LARGE_SIZE) {
                        LARGE_HEAP.doFree(buffer);
                    }
                }
            }
            array[offs + i] = null;
        }
    }

    /**
     * Free a buffer as with {@link #free(ByteBuffer)} except the buffer is first zeroed and cleared.
     *
     * @param buffer the buffer to free
     */
    public static void zeroAndFree(ByteBuffer buffer) {
        Buffers.zero(buffer);
        free(buffer);
    }

    /**
     * Determine if this source returns direct buffers.
     * @return {@code true} if the buffers are direct, {@code false} if they are heap
     */
    public boolean isDirect() {
        return direct;
    }

    /**
     * Get the size of buffers returned by this source.  The size will be a power of two.
     *
     * @return the size of buffers returned by this source
     */
    public int getSize() {
        return size;
    }

    // private

    static ByteBufferPool create(final int size, final boolean direct) {
        assert Integer.bitCount(size) == 1;
        assert size >= 0x10;
        assert size <= 0x4000_0000;
        return new ByteBufferPool(size, direct) {
            ByteBuffer createBuffer() {
                return isDirect() ? ByteBuffer.allocateDirect(getSize()) : ByteBuffer.allocate(getSize());
            }
        };
    }

    static ByteBufferPool subPool(final ByteBufferPool parent, final int size) {
        // must be a power of two, not too small, and smaller than the parent buffer source
        assert Integer.bitCount(size) == 1;
        assert Integer.bitCount(parent.getSize()) == 1;
        assert size >= 0x10;
        assert size < parent.getSize();
        // and thus..
        assert parent.getSize() % size == 0;
        return new ByteBufferPool(size, parent.isDirect()) {
            ByteBuffer createBuffer() {
                ByteBuffer parentBuffer = parent.allocate();
                final int size = getSize();
                ByteBuffer result = Buffers.slice(parentBuffer, size);
                while (parentBuffer.hasRemaining()) {
                    // avoid false sharing between buffers
                    if (size < CACHE_LINE_SIZE) {
                        Buffers.skip(parentBuffer, CACHE_LINE_SIZE - size);
                    }
                    super.doFree(Buffers.slice(parentBuffer, size));
                }
                return result;
            }
        };
    }

    abstract ByteBuffer createBuffer();

    final void doFree(final ByteBuffer buffer) {
        assert buffer.capacity() == size;
        assert buffer.isDirect() == direct;
        buffer.clear();
        final LocalBufferCache localCache = threadLocal.get();
        int oldVal = localCache.outstanding;
        if (oldVal >= LOCAL_QUEUE_SIZE || localCache.queue.size() == LOCAL_QUEUE_SIZE) {
            masterQueue.add(buffer);
        } else {
            localCache.outstanding = oldVal - 1;
            localCache.queue.add(buffer);
        }
    }

    ConcurrentLinkedQueue<ByteBuffer> getMasterQueue() {
        return masterQueue;
    }

    LocalBufferCacheThreadLocal getThreadLocal() {
        return threadLocal;
    }

    static class LocalBufferCacheThreadLocal extends ThreadLocal<LocalBufferCache> {

        final ByteBufferPool byteBufferPool;

        LocalBufferCacheThreadLocal(final ByteBufferPool byteBufferPool) {
            this.byteBufferPool = byteBufferPool;
        }

        protected LocalBufferCache initialValue() {
            return new LocalBufferCache(byteBufferPool);
        }

        public void remove() {
            get().empty();
        }
    }

    static class LocalBufferCache {

        final LocalBufferCacheThreadLocal bufferQueue;
        final ArrayDeque<ByteBuffer> queue = new ArrayDeque<>(LOCAL_QUEUE_SIZE);

        int outstanding;

        LocalBufferCache(final ByteBufferPool byteBufferPool) {
            bufferQueue = byteBufferPool.getThreadLocal();
        }

        protected void finalize() throws Throwable {
            empty();
        }

        void empty() {
            ArrayDeque<ByteBuffer> queue = this.queue;
            if (! queue.isEmpty()) {
                ConcurrentLinkedQueue<ByteBuffer> masterQueue = bufferQueue.byteBufferPool.getMasterQueue();
                do {
                    masterQueue.add(queue.poll());
                } while (! queue.isEmpty());
            }
        }
    }
}
