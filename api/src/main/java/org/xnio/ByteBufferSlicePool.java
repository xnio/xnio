/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2010 Red Hat, Inc. and/or its affiliates, and individual
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

package org.xnio;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.nio.ByteBuffer;
import java.security.AccessController;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import static org.xnio._private.Messages.msg;

/**
 * A buffer pooled allocator.  This pool uses a series of buffer regions to back the
 * returned pooled buffers.  When the buffer is no longer needed, it should be freed back into the pool; failure
 * to do so will cause the corresponding buffer area to be unavailable until the buffer is garbage-collected.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ByteBufferSlicePool implements Pool<ByteBuffer> {

    private static final int LOCAL_LENGTH;

    static {
        String value = AccessController.doPrivileged(new ReadPropertyAction("xnio.bufferpool.threadlocal.size", "12"));
        int val;
        try {
            val = Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            val = 12;
        }
        LOCAL_LENGTH = val;
    }

    private final Set<Ref> refSet = Collections.synchronizedSet(new HashSet<Ref>());
    private final Queue<Slice> sliceQueue;
    private final BufferAllocator<ByteBuffer> allocator;
    private final int bufferSize;
    private final int buffersPerRegion;
    private final int threadLocalQueueSize;
    private final ThreadLocal<ArrayDeque<Slice>> localQueueHolder = new ThreadLocal<ArrayDeque<Slice>>() {
        protected ArrayDeque<Slice> initialValue() {
            //noinspection serial
            return new ArrayDeque<Slice>(threadLocalQueueSize) {

                /**
                 * This sucks but there's no other way to ensure these buffers are returned to the pool.
                 */
                protected void finalize() {
                    remove();
                }
            };
        }

        public void remove() {
            final ArrayDeque<Slice> deque = get();
            Slice slice = deque.poll();
            while (slice != null) {
                doFree(slice);
                slice = deque.poll();
            }
        }
    };

    /**
     * Construct a new instance.
     *
     * @param allocator the buffer allocator to use
     * @param bufferSize the size of each buffer
     * @param maxRegionSize the maximum region size for each backing buffer
     * @param threadLocalQueueSize the number of buffers to cache on each thread
     */
    public ByteBufferSlicePool(final BufferAllocator<ByteBuffer> allocator, final int bufferSize, final int maxRegionSize, final int threadLocalQueueSize) {
        if (bufferSize <= 0) {
            throw msg.parameterOutOfRange("bufferSize");
        }
        if (maxRegionSize < bufferSize) {
            throw msg.parameterOutOfRange("bufferSize");
        }
        buffersPerRegion = maxRegionSize / bufferSize;
        this.bufferSize = bufferSize;
        this.allocator = allocator;
        sliceQueue = new ConcurrentLinkedQueue<Slice>();
        this.threadLocalQueueSize = threadLocalQueueSize;
    }

    /**
     * Construct a new instance.
     *
     * @param allocator the buffer allocator to use
     * @param bufferSize the size of each buffer
     * @param maxRegionSize the maximum region size for each backing buffer
     */
    public ByteBufferSlicePool(final BufferAllocator<ByteBuffer> allocator, final int bufferSize, final int maxRegionSize) {
        this(allocator, bufferSize, maxRegionSize, LOCAL_LENGTH);
    }

    /**
     * Construct a new instance, using a direct buffer allocator.
     *
     * @param bufferSize the size of each buffer
     * @param maxRegionSize the maximum region size for each backing buffer
     */
    public ByteBufferSlicePool(final int bufferSize, final int maxRegionSize) {
        this(BufferAllocator.DIRECT_BYTE_BUFFER_ALLOCATOR, bufferSize, maxRegionSize);
    }

    /** {@inheritDoc} */
    public Pooled<ByteBuffer> allocate() {
        Slice slice = localQueueHolder.get().poll();
        if (slice != null) {
            return new PooledByteBuffer(slice, slice.slice());
        }
        final Queue<Slice> sliceQueue = this.sliceQueue;
        slice = sliceQueue.poll();
        if (slice != null) {
            return new PooledByteBuffer(slice, slice.slice());
        }
        synchronized (sliceQueue) {
            slice = sliceQueue.poll();
            if (slice != null) {
                return new PooledByteBuffer(slice, slice.slice());
            }
            final int bufferSize = this.bufferSize;
            final int buffersPerRegion = this.buffersPerRegion;
            final ByteBuffer region = allocator.allocate(buffersPerRegion * bufferSize);
            int idx = bufferSize;
            for (int i = 1; i < buffersPerRegion; i ++) {
                sliceQueue.add(new Slice(region, idx, bufferSize));
                idx += bufferSize;
            }
            final Slice newSlice = new Slice(region, 0, bufferSize);
            return new PooledByteBuffer(newSlice, newSlice.slice());
        }
    }

    private void doFree(Slice region) {
        final ArrayDeque<Slice> localQueue = localQueueHolder.get();
        if (localQueue.size() == LOCAL_LENGTH) {
            sliceQueue.add(region);
        } else {
            localQueue.add(region);
        }
    }

    private static final AtomicReferenceFieldUpdater<PooledByteBuffer, ByteBuffer> bufferUpdater = AtomicReferenceFieldUpdater.newUpdater(PooledByteBuffer.class, ByteBuffer.class, "buffer");

    private final class PooledByteBuffer implements Pooled<ByteBuffer> {
        private final Slice region;
        volatile ByteBuffer buffer;

        PooledByteBuffer(final Slice region, final ByteBuffer buffer) {
            this.region = region;
            this.buffer = buffer;
        }

        public void discard() {
            final ByteBuffer buffer = bufferUpdater.getAndSet(this, null);
            if (buffer != null) {
                // free when GC'd, no sooner
                refSet.add(new Ref(buffer, region));
            }
        }

        public void free() {
            if (bufferUpdater.getAndSet(this, null) != null) {
                // trust the user, repool the buffer
                doFree(region);
            }
        }

        public ByteBuffer getResource() {
            final ByteBuffer buffer = this.buffer;
            if (buffer == null) {
                throw msg.bufferFreed();
            }
            return buffer;
        }

        public String toString() {
            return "Pooled buffer " + buffer;
        }
    }

    private final class Slice {
        private final ByteBuffer parent;
        private final int start;
        private final int size;

        private Slice(final ByteBuffer parent, final int start, final int size) {
            this.parent = parent;
            this.start = start;
            this.size = size;
        }

        ByteBuffer slice() {
            return ((ByteBuffer)parent.duplicate().position(start).limit(start+size)).slice();
        }
    }

    private final class Ref extends PhantomReference<ByteBuffer> {
        private final Slice region;

        private Ref(final ByteBuffer referent, final Slice region) {
            super(referent, QueueThread.REFERENCE_QUEUE);
            this.region = region;
        }

        void free() {
            doFree(region);
            refSet.remove(this);
        }
    }

    private static final class QueueThread extends Thread {
        private static final ReferenceQueue<ByteBuffer> REFERENCE_QUEUE = new ReferenceQueue<ByteBuffer>();
        private static final QueueThread INSTANCE;

        static {
            INSTANCE = new QueueThread();
            INSTANCE.start();
        }

        private QueueThread() {
            setDaemon(true);
            setName("Buffer reclamation thread");
        }

        public void run() {
            for (;;) try {
                final Ref reference = (Ref) REFERENCE_QUEUE.remove();
                reference.free();
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }
}
