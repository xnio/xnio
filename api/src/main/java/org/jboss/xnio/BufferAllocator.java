package org.jboss.xnio;

import java.nio.Buffer;

/**
 * An allocator for buffers.
 *
 * @param <T> the type of buffer that is managed by this allocator
 */
public interface BufferAllocator<T extends Buffer> {
    /**
     * Allocate a buffer.  If not enough buffers are available to be allocated, return {@code null}.
     *
     * @return a buffer, or {@code null} if none are available
     */
    T allocate();

    /**
     * Free a previously allocated buffer.
     *
     * @param buffer the buffer to free
     */
    void free(T buffer);
}
