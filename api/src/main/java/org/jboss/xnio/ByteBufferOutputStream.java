package org.jboss.xnio;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

/**
 * An output stream that allocates and outputs byte buffers as needed.
 */
public final class ByteBufferOutputStream extends OutputStream {
    private ByteBuffer current;
    private final BufferAllocator<ByteBuffer> bufferAllocator;
    private final ObjectSink<ByteBuffer> bufferSink;
    private long count = 0;
    private final long writeLimit;

    /**
     * Create a byte buffer output stream instance.
     *
     * @param bufferAllocator the buffer allocator
     * @param bufferSink the receiver for filled buffers
     * @param writeLimit the upper limit on the number of bytes that may be written
     */
    public ByteBufferOutputStream(final BufferAllocator<ByteBuffer> bufferAllocator, final ObjectSink<ByteBuffer> bufferSink, long writeLimit) {
        this.bufferAllocator = bufferAllocator;
        this.writeLimit = writeLimit;
        this.bufferSink = bufferSink;
    }

    public void write(final byte bytes[], int offs, int len) throws IOException {
        if ((long)len > (writeLimit - count)) {
            throw new BufferOverflowException();
        }
        while (len > 0) {
            if (current == null) {
                current = bufferAllocator.allocate();
            } else if (! current.hasRemaining()) {
                final ByteBuffer current = this.current;
                this.current = null;
                bufferSink.accept(current);
                this.current = bufferAllocator.allocate();
            }
            final int size = Math.min(len, current.remaining());
            current.put(bytes, offs, size);
            offs += size; len -= size; count += (long)size;
        }
    }

    public void write(final int data) throws IOException {
        if (count >= writeLimit) {
            throw new BufferOverflowException();
        }
        if (! current.hasRemaining()) {
            final ByteBuffer current = this.current;
            this.current = null;
            bufferSink.accept(current);
            this.current = bufferAllocator.allocate();
        }
        current.put((byte)data);
        count ++;
    }

    public void flush() throws IOException {
        final ByteBuffer current = this.current;
        this.current = null;
        bufferSink.accept(current);
    }

    public void close() throws IOException {
        flush();
    }

    /**
     * Get the number of bytes written.
     *
     * @return the number of bytes
     */
    public long getSize() {
        return count;
    }

    /**
     * Get the number of bytes that may yet be written.
     *
     * @return the number of bytes
     */
    public long getRemaining() {
        return writeLimit - count;
    }
}
