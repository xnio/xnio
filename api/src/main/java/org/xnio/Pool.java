
package org.xnio;

import java.nio.ByteBuffer;

/**
 * A generic pooled resource manager.
 *
 * @param <T> the resource type
 *
 * @apiviz.landmark
 *
 * @deprecated See {@link ByteBufferPool}.
 */
public interface Pool<T> {

    /**
     * Allocate a resource from the pool.
     *
     * @return the resource
     */
    Pooled<T> allocate();

    /**
     * A compatibility pool which maps to {@link ByteBufferPool#MEDIUM_HEAP}.
     */
    Pool<ByteBuffer> HEAP = new Pool<ByteBuffer>() {
        public Pooled<ByteBuffer> allocate() {
            return Buffers.globalPooledWrapper(ByteBufferPool.MEDIUM_HEAP.allocate());
        }
    };

    /**
     * A compatibility pool which maps to {@link ByteBufferPool#MEDIUM_DIRECT}.
     */
    Pool<ByteBuffer> DIRECT = new Pool<ByteBuffer>() {
        public Pooled<ByteBuffer> allocate() {
            return Buffers.globalPooledWrapper(ByteBufferPool.MEDIUM_DIRECT.allocate());
        }
    };
}
