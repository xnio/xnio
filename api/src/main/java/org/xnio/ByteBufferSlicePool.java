/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.xnio;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * A buffer pooled allocator.  This pool uses a series of buffer regions to back the
 * returned pooled buffers.  When the buffer is no longer needed, it should be freed back into the pool; failure
 * to do so will cause the corresponding buffer area to be unavailable until the buffer is garbage-collected.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ByteBufferSlicePool implements Pool<ByteBuffer> {

    private final Set<Ref> refSet = Collections.synchronizedSet(new HashSet<Ref>());
    private final Queue<Slice> sliceQueue = new ConcurrentLinkedQueue<Slice>();
    private final BufferAllocator<ByteBuffer> allocator;
    private final int bufferSize;
    private final int buffersPerRegion;

    /**
     * Construct a new instance.
     *
     * @param allocator the buffer allocator to use
     * @param bufferSize the size of each buffer
     * @param maxRegionSize the maximum region size for each backing buffer
     */
    public ByteBufferSlicePool(final BufferAllocator<ByteBuffer> allocator, final int bufferSize, final int maxRegionSize) {
        if (bufferSize <= 0) {
            throw new IllegalArgumentException("Buffer size must be greater than zero");
        }
        if (maxRegionSize < bufferSize) {
            throw new IllegalArgumentException("Maximum region size must be greater than or equal to the buffer size");
        }
        buffersPerRegion = maxRegionSize / bufferSize;
        this.bufferSize = bufferSize;
        this.allocator = allocator;
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
        final Queue<Slice> sliceQueue = this.sliceQueue;
        final Slice slice = sliceQueue.poll();
        if (slice == null) {
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
        if (slice == null) {
            throw new PoolDepletedException("Pool is empty");
        }
        return new PooledByteBuffer(slice, slice.slice());
    }

    private void doFree(Slice region) {
        sliceQueue.add(region);
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
                throw new IllegalStateException();
            }
            return buffer;
        }

        protected void finalize() throws Throwable {
            discard();
            super.finalize();
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
