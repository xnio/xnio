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
import java.util.concurrent.ConcurrentLinkedQueue;

import org.wildfly.common.Assert;
import org.wildfly.common.cpu.CacheInfo;
import org.wildfly.common.function.ExceptionBiConsumer;
import org.wildfly.common.function.ExceptionBiFunction;
import org.wildfly.common.function.ExceptionConsumer;
import org.wildfly.common.function.ExceptionFunction;
import org.wildfly.common.function.ExceptionRunnable;
import org.wildfly.common.function.ExceptionSupplier;
import org.wildfly.common.function.Functions;

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
    private final ThreadLocal<Cache> threadLocalCache = ThreadLocal.withInitial(this::getDefaultCache);
    private final ByteBufferPool.Cache defaultCache = new DefaultCache();
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
        return threadLocalCache.get().allocate();
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
        for (int i = 0; i < len; i ++) {
            array[offs + i] = allocate();
        }
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
                if (size == MEDIUM_SIZE) {
                    MEDIUM_DIRECT.doFree(buffer);
                } else if (size == SMALL_SIZE) {
                    SMALL_DIRECT.doFree(buffer);
                } else if (size == LARGE_SIZE) {
                    LARGE_DIRECT.doFree(buffer);
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

    /**
     * Flush thread-local caches.  This is useful when a long blocking operation is being performed, wherein it is
     * unlikely that buffers will be used; calling this method makes any cached buffers available to other threads.
     */
    public void flushCaches() {
        threadLocalCache.get().flush();
    }

    /**
     * Flush all thread-local caches for all buffer sizes.  This is useful when a long blocking operation is being performed, wherein it is
     * unlikely that buffers will be used; calling this method makes any cached buffers available to other threads.
     */
    public static void flushAllCaches() {
        SMALL_HEAP.flushCaches();
        MEDIUM_HEAP.flushCaches();
        LARGE_HEAP.flushCaches();
        SMALL_DIRECT.flushCaches();
        MEDIUM_DIRECT.flushCaches();
        LARGE_DIRECT.flushCaches();
    }

    /**
     * Perform the given operation with the addition of a buffer cache of the given size.  When this method returns,
     * any cached free buffers will be returned to the next-higher cache or the global pool.  If a cache size of 0
     * is given, the action is simply run directly.
     *
     * @param <T> the type of the first parameter
     * @param <U> the type of the second parameter
     * @param <E> the exception type thrown by the operation
     * @param cacheSize the cache size to run under
     * @param consumer the action to run
     * @param param1 the first parameter to pass to the action
     * @param param2 the second parameter to pass to the action
     * @throws E if the nested action threw an exception
     */
    public <T, U, E extends Exception> void acceptWithCacheEx(int cacheSize, ExceptionBiConsumer<T, U, E> consumer, T param1, U param2) throws E {
        Assert.checkMinimumParameter("cacheSize", 0, cacheSize);
        Assert.checkNotNullParam("consumer", consumer);
        final ThreadLocal<Cache> threadLocalCache = this.threadLocalCache;
        final Cache parent = threadLocalCache.get();
        final Cache cache;
        if (cacheSize == 0) {
            consumer.accept(param1, param2);
            return;
        } else if (cacheSize <= 64) {
            if (cacheSize == 1) {
                cache = new OneCache(parent);
            } else if (cacheSize == 2) {
                cache = new TwoCache(parent);
            } else {
                cache = new MultiCache(parent, cacheSize);
            }
            threadLocalCache.set(cache);
            try {
                consumer.accept(param1, param2);
                return;
            } finally {
                threadLocalCache.set(parent);
                cache.destroy();
            }
        } else {
            cache = new MultiCache(parent, 64);
            threadLocalCache.set(cache);
            try {
                acceptWithCacheEx(cacheSize - 64, consumer, param1, param2);
                return;
            } finally {
                cache.destroy();
            }
        }
    }

    /**
     * Perform the given operation with the addition of a buffer cache of the given size.  When this method returns,
     * any cached free buffers will be returned to the next-higher cache or the global pool.  If a cache size of 0
     * is given, the action is simply run directly.
     *
     * @param <T> the type of the parameter
     * @param <E> the exception type thrown by the operation
     * @param cacheSize the cache size to run under
     * @param consumer the action to run
     * @param param the parameter to pass to the action
     * @throws E if the nested action threw an exception
     */
    public <T, E extends Exception> void acceptWithCacheEx(int cacheSize, ExceptionConsumer<T, E> consumer, T param) throws E {
        Assert.checkNotNullParam("consumer", consumer);
        acceptWithCacheEx(cacheSize, Functions.exceptionConsumerBiConsumer(), consumer, param);
    }

    /**
     * Perform the given operation with the addition of a buffer cache of the given size.  When this method returns,
     * any cached free buffers will be returned to the next-higher cache or the global pool.  If a cache size of 0
     * is given, the action is simply run directly.
     *
     * @param <E> the exception type thrown by the operation
     * @param cacheSize the cache size to run under
     * @param runnable the action to run
     * @throws E if the nested action threw an exception
     */
    public <E extends Exception> void runWithCacheEx(int cacheSize, ExceptionRunnable<E> runnable) throws E {
        Assert.checkNotNullParam("runnable", runnable);
        acceptWithCacheEx(cacheSize, Functions.exceptionRunnableConsumer(), runnable);
    }

    /**
     * Perform the given operation with the addition of a buffer cache of the given size.  When this method returns,
     * any cached free buffers will be returned to the next-higher cache or the global pool.  If a cache size of 0
     * is given, the action is simply run directly.
     *
     * @param cacheSize the cache size to run under
     * @param runnable the action to run
     */
    public void runWithCache(int cacheSize, Runnable runnable) {
        Assert.checkNotNullParam("runnable", runnable);
        // todo: fix with wildfly-common 1.4
        acceptWithCacheEx(cacheSize, Runnable::run, runnable);
    }

    /**
     * Perform the given operation with the addition of a buffer cache of the given size.  When this method returns,
     * any cached free buffers will be returned to the next-higher cache or the global pool.  If a cache size of 0
     * is given, the action is simply run directly.
     *
     * @param <T> the type of the first parameter
     * @param <U> the type of the second parameter
     * @param <R> the return type of the operation
     * @param <E> the exception type thrown by the operation
     * @param cacheSize the cache size to run under
     * @param function the action to run
     * @param param1 the first parameter to pass to the action
     * @param param2 the second parameter to pass to the action
     * @return the result of the action
     * @throws E if the nested action threw an exception
     */
    public <T, U, R, E extends Exception> R applyWithCacheEx(int cacheSize, ExceptionBiFunction<T, U, R, E> function, T param1, U param2) throws E {
        Assert.checkMinimumParameter("cacheSize", 0, cacheSize);
        Assert.checkNotNullParam("function", function);
        final ThreadLocal<Cache> threadLocalCache = this.threadLocalCache;
        final Cache parent = threadLocalCache.get();
        final Cache cache;
        if (cacheSize == 0) {
            return function.apply(param1, param2);
        } else if (cacheSize <= 64) {
            if (cacheSize == 1) {
                cache = new OneCache(parent);
            } else if (cacheSize == 2) {
                cache = new TwoCache(parent);
            } else {
                cache = new MultiCache(parent, cacheSize);
            }
            threadLocalCache.set(cache);
            try {
                return function.apply(param1, param2);
            } finally {
                threadLocalCache.set(parent);
                cache.destroy();
            }
        } else {
            cache = new MultiCache(parent, 64);
            threadLocalCache.set(cache);
            try {
                return applyWithCacheEx(cacheSize - 64, function, param1, param2);
            } finally {
                cache.destroy();
            }
        }
    }

    /**
     * Perform the given operation with the addition of a buffer cache of the given size.  When this method returns,
     * any cached free buffers will be returned to the next-higher cache or the global pool.  If a cache size of 0
     * is given, the action is simply run directly.
     *
     * @param <T> the type of the parameter
     * @param <R> the return type of the operation
     * @param <E> the exception type thrown by the operation
     * @param cacheSize the cache size to run under
     * @param function the action to run
     * @param param the parameter to pass to the action
     * @return the result of the action
     * @throws E if the nested action threw an exception
     */
    public <T, R, E extends Exception> R applyWithCacheEx(int cacheSize, ExceptionFunction<T, R, E> function, T param) throws E {
        return applyWithCacheEx(cacheSize, Functions.exceptionFunctionBiFunction(), function, param);
    }

    /**
     * Perform the given operation with the addition of a buffer cache of the given size.  When this method returns,
     * any cached free buffers will be returned to the next-higher cache or the global pool.  If a cache size of 0
     * is given, the action is simply run directly.
     *
     * @param <R> the return type of the operation
     * @param <E> the exception type thrown by the operation
     * @param cacheSize the cache size to run under
     * @param supplier the action to run
     * @return the result of the action
     * @throws E if the nested action threw an exception
     */
    public <R, E extends Exception> R getWithCacheEx(int cacheSize, ExceptionSupplier<R, E> supplier) throws E {
        return applyWithCacheEx(cacheSize, Functions.exceptionSupplierFunction(), supplier);
    }

    // private

    Cache getDefaultCache() {
        return defaultCache;
    }

    ConcurrentLinkedQueue<ByteBuffer> getMasterQueue() {
        return masterQueue;
    }

    private ByteBuffer allocateMaster() {
        ByteBuffer byteBuffer = masterQueue.poll();
        if (byteBuffer == null) {
            byteBuffer = createBuffer();
        }
        return byteBuffer;
    }

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
                synchronized (this) {
                    // avoid a storm of mass-population by only allowing one thread to split a parent buffer at a time
                    ByteBuffer appearing = getMasterQueue().poll();
                    if (appearing != null) {
                        return appearing;
                    }
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
            }
        };
    }

    abstract ByteBuffer createBuffer();

    final void freeMaster(ByteBuffer buffer) {
        masterQueue.add(buffer);
    }

    final void doFree(final ByteBuffer buffer) {
        assert buffer.capacity() == size;
        assert buffer.isDirect() == direct;
        buffer.clear();
        threadLocalCache.get().free(buffer);
    }

    interface Cache {
        void free(ByteBuffer bb);

        void flushBuffer(ByteBuffer bb);

        ByteBuffer allocate();

        void destroy();

        void flush();
    }

    static final class OneCache implements Cache {
        private final Cache parent;
        private ByteBuffer buffer;

        OneCache(final Cache parent) {
            this.parent = parent;
        }

        public void free(final ByteBuffer bb) {
            if (buffer == null) {
                buffer = bb;
            } else {
                parent.free(bb);
            }
        }

        public void flushBuffer(final ByteBuffer bb) {
            parent.flushBuffer(bb);
        }

        public ByteBuffer allocate() {
            if (buffer != null) try {
                return buffer;
            } finally {
                buffer = null;
            } else {
                return parent.allocate();
            }
        }

        public void destroy() {
            final ByteBuffer buffer = this.buffer;
            if (buffer != null) {
                this.buffer = null;
                parent.free(buffer);
            }
        }

        public void flush() {
            final ByteBuffer buffer = this.buffer;
            if (buffer != null) {
                this.buffer = null;
                flushBuffer(buffer);
            }
            parent.flush();
        }
    }

    static final class TwoCache implements Cache {
        private final Cache parent;
        private ByteBuffer buffer1;
        private ByteBuffer buffer2;

        TwoCache(final Cache parent) {
            this.parent = parent;
        }

        public void free(final ByteBuffer bb) {
            if (buffer1 == null) {
                buffer1 = bb;
            } else if (buffer2 == null) {
                buffer2 = bb;
            } else {
                parent.free(bb);
            }
        }

        public void flushBuffer(final ByteBuffer bb) {
            parent.flushBuffer(bb);
        }

        public ByteBuffer allocate() {
            if (buffer1 != null) try {
                return buffer1;
            } finally {
                buffer1 = null;
            } else if (buffer2 != null) try {
                return buffer2;
            } finally {
                buffer2 = null;
            } else {
                return parent.allocate();
            }
        }

        public void destroy() {
            final Cache parent = this.parent;
            final ByteBuffer buffer1 = this.buffer1;
            if (buffer1 != null) {
                parent.free(buffer1);
                this.buffer1 = null;
            }
            final ByteBuffer buffer2 = this.buffer2;
            if (buffer2 != null) {
                parent.free(buffer2);
                this.buffer2 = null;
            }
        }

        public void flush() {
            final ByteBuffer buffer1 = this.buffer1;
            if (buffer1 != null) {
                flushBuffer(buffer1);
                this.buffer1 = null;
            }
            final ByteBuffer buffer2 = this.buffer2;
            if (buffer2 != null) {
                flushBuffer(buffer2);
                this.buffer2 = null;
            }
            parent.flush();
        }
    }

    static final class MultiCache implements Cache {
        private final Cache parent;
        private final ByteBuffer[] cache;
        private final long mask;
        private long availableBits;

        MultiCache(final Cache parent, final int size) {
            this.parent = parent;
            assert 0 < size && size <= 64;
            cache = new ByteBuffer[size];
            mask = availableBits = size == 64 ? ~0L : (1L << size) - 1;
        }

        public void free(final ByteBuffer bb) {
            long posn = Long.lowestOneBit(~availableBits & mask);
            if (posn != 0L) {
                int bit = Long.numberOfTrailingZeros(posn);
                // mark available
                availableBits |= posn;
                cache[bit] = bb;
            } else {
                // full
                parent.free(bb);
            }
        }

        public void flushBuffer(final ByteBuffer bb) {
            parent.flushBuffer(bb);
        }

        public ByteBuffer allocate() {
            long posn = Long.lowestOneBit(availableBits);
            if (posn != 0L) {
                int bit = Long.numberOfTrailingZeros(posn);
                availableBits &= ~posn;
                try {
                    return cache[bit];
                } finally {
                    cache[bit] = null;
                }
            } else {
                // empty
                return parent.allocate();
            }
        }

        public void destroy() {
            final ByteBuffer[] cache = this.cache;
            final Cache parent = this.parent;
            long bits = ~availableBits & mask;
            try {
                while (bits != 0L) {
                    long posn = Long.lowestOneBit(bits);
                    int bit = Long.numberOfTrailingZeros(posn);
                    parent.free(cache[bit]);
                    bits &= ~posn;
                    cache[bit] = null;
                }
            } finally {
                // should be 0, but maintain a consistent state in case a free failed
                availableBits = bits;
            }
        }

        public void flush() {
            final ByteBuffer[] cache = this.cache;
            final Cache parent = this.parent;
            long bits = ~availableBits & mask;
            try {
                while (bits != 0L) {
                    long posn = Long.lowestOneBit(bits);
                    int bit = Long.numberOfTrailingZeros(posn);
                    flushBuffer(cache[bit]);
                    bits &= ~posn;
                    cache[bit] = null;
                }
            } finally {
                // should be 0, but maintain a consistent state in case a free failed
                availableBits = bits;
            }
            parent.flush();
        }
    }

    final class DefaultCache implements Cache {
        public void free(final ByteBuffer bb) {
            freeMaster(bb);
        }

        public ByteBuffer allocate() {
            return allocateMaster();
        }

        public void flushBuffer(final ByteBuffer bb) {
            free(bb);
        }

        public void destroy() {
            // no operation
        }

        public void flush() {
            // no operation
        }
    }
}
