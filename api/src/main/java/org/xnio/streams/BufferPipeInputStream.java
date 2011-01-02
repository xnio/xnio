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

package org.xnio.streams;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Semaphore;

/**
 * An {@code InputStream} implementation which is populated asynchronously with {@link ByteBuffer} instances.
 */
public class BufferPipeInputStream extends InputStream {
    private final Queue<Entry> queue;
    private final InputHandler inputHandler;

    // protected by "queue"
    private boolean eof;
    private IOException failure;

    private static final class Entry {
        private final ByteBuffer buffer;
        private final BufferReturn bufferReturn;

        private Entry(final ByteBuffer buffer, final BufferReturn bufferReturn) {
            this.buffer = buffer;
            this.bufferReturn = bufferReturn;
        }
    }

    /**
     * Construct a new instance.  The given {@code inputHandler} will
     * be invoked after each buffer is fully read and when the stream is closed.
     *
     * @param inputHandler the input events handler
     */
    public BufferPipeInputStream(final InputHandler inputHandler) {
        this.inputHandler = inputHandler;
        queue = new ArrayDeque<Entry>();
    }

    /**
     * Push a buffer into the queue.  There is no mechanism to limit the number of pushed buffers; if such a mechanism
     * is desired, it must be implemented externally, for example maybe using a {@link Semaphore}.
     *
     * @param buffer the buffer from which more data should be read
     */
    public void push(final ByteBuffer buffer) {
        synchronized (this) {
            if (!eof && failure == null) {
                queue.add(new Entry(buffer, null));
                notifyAll();
            }
        }
    }

    /**
     * Push a buffer into the queue.  There is no mechanism to limit the number of pushed buffers; if such a mechanism
     * is desired, it must be implemented externally, for example maybe using a {@link Semaphore}.
     *
     * @param buffer the buffer from which more data should be read
     * @param bufferReturn the buffer return to send this buffer to when it is exhausted
     */
    public void push(final ByteBuffer buffer, final BufferReturn bufferReturn) {
        synchronized (this) {
            if (!eof && failure == null) {
                queue.add(new Entry(buffer, bufferReturn));
                notifyAll();
            } else {
                bufferReturn.returnBuffer(buffer);
            }
        }
    }

    /**
     * Push the EOF condition into the queue.  After this method is called, no further buffers may be pushed into this
     * instance.
     */
    public void pushEof() {
        synchronized (this) {
            eof = true;
            notifyAll();
        }
    }

    /**
     * Push an exception condition into the queue.  After this method is called, no further buffers may be pushed into this
     * instance.
     *
     * @param e the exception to push
     */
    public void pushException(IOException e) {
        synchronized (this) {
            if (! eof) {
                failure = e;
                notifyAll();
            }
        }
    }

    /** {@inheritDoc} */
    public int read() throws IOException {
        final Queue<Entry> queue = this.queue;
        synchronized (this) {
            while (queue.isEmpty()) {
                if (eof) {
                    return -1;
                }
                checkFailure();
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new InterruptedIOException("Interrupted on read()");
                }
            }
            final Entry entry = queue.peek();
            final ByteBuffer buf = entry.buffer;
            final BufferReturn bufferReturn = entry.bufferReturn;
            final int v = buf.get() & 0xff;
            if (buf.remaining() == 0) {
                if (bufferReturn != null) {
                    bufferReturn.returnBuffer(buf);
                }
                queue.poll();
                try {
                    inputHandler.acknowledge();
                } catch (IOException e) {
                    // no operation!
                }
            }
            return v;
        }
    }

    private void clearQueue() {
        synchronized (this) {
            Entry entry;
            while ((entry = queue.poll()) != null) {
                final ByteBuffer buffer = entry.buffer;
                final BufferReturn ret = entry.bufferReturn;
                if (ret != null) {
                    ret.returnBuffer(buffer);
                }
            }
        }
    }

    /** {@inheritDoc} */
    public int read(final byte[] b, int off, int len) throws IOException {
        if (len == 0) {
            return 0;
        }
        final Queue<Entry> queue = this.queue;
        synchronized (this) {
            while (queue.isEmpty()) {
                if (eof) {
                    return -1;
                }
                checkFailure();
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new InterruptedIOException("Interrupted on read()");
                }
            }
            int total = 0;
            while (len > 0) {
                final Entry entry = queue.peek();
                if (entry == null) {
                    break;
                }
                final ByteBuffer buffer = entry.buffer;
                final BufferReturn bufferReturn = entry.bufferReturn;
                final int byteCnt = Math.min(buffer.remaining(), len);
                buffer.get(b, off, byteCnt);
                off += byteCnt;
                total += byteCnt;
                len -= byteCnt;
                if (buffer.remaining() == 0) {
                    if (bufferReturn != null) {
                        bufferReturn.returnBuffer(buffer);
                    }
                    queue.poll();
                    try {
                        inputHandler.acknowledge();
                    } catch (IOException e) {
                        // no operation!
                    }
                }
            }
            return total;
        }
    }

    /** {@inheritDoc} */
    public int available() throws IOException {
        synchronized (this) {
            int total = 0;
            for (Entry entry : queue) {
                total += entry.buffer.remaining();
                if (total < 0) {
                    return Integer.MAX_VALUE;
                }
            }
            return total;
        }
    }

    public long skip(long qty) throws IOException {
        final Queue<Entry> queue = this.queue;
        synchronized (this) {
            while (queue.isEmpty()) {
                if (eof) {
                    return 0L;
                }
                checkFailure();
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new InterruptedIOException("Interrupted on read()");
                }
            }
            long skipped = 0L;
            while (qty > 0L) {
                final Entry entry = queue.peek();
                if (entry == null) {
                    break;
                }
                final ByteBuffer buffer = entry.buffer;
                final BufferReturn bufferReturn = entry.bufferReturn;
                final int byteCnt = Math.min(buffer.remaining(), (int) Math.max((long)Integer.MAX_VALUE, qty));
                buffer.position(buffer.position() + byteCnt);
                skipped += byteCnt;
                qty -= byteCnt;
                if (buffer.remaining() == 0) {
                    queue.poll();
                    if (bufferReturn != null) {
                        bufferReturn.returnBuffer(buffer);
                    }
                    try {
                        inputHandler.acknowledge();
                    } catch (IOException e) {
                        // no operation!
                    }
                }
            }
            return skipped;
        }
    }

    /** {@inheritDoc} */
    public void close() throws IOException {
        synchronized (this) {
            if (! eof) {
                clearQueue();
                eof = true;
                notifyAll();
                inputHandler.close();
            }
        }
    }

    private void checkFailure() throws IOException {
        final IOException failure = this.failure;
        if (failure != null) {
            failure.fillInStackTrace();
            try {
                throw failure;
            } finally {
                eof = true;
                clearQueue();
                notifyAll();
                this.failure = null;
            }
        }
    }

    /**
     * A handler for events relating to the consumption of data from a {@link BufferPipeInputStream} instance.
     */
    public interface InputHandler extends Closeable {

        /**
         * Acknowledges the successful processing of an input buffer.  Though this method may throw an exception,
         * it is not acted upon.
         *
         * @throws IOException if an I/O error occurs sending the acknowledgement
         */
        void acknowledge() throws IOException;

        /**
         * Signifies that the user of the enclosing {@link BufferPipeInputStream} has called the {@code close()} method
         * explicitly.  Any thrown exception is propagated up to the caller of {@link BufferPipeInputStream#close() NioByteInput.close()}.
         *
         * @throws IOException if an I/O error occurs
         */
        void close() throws IOException;
    }

    /**
     * A handler for returning buffers which are have been exhausted.
     */
    public interface BufferReturn {

        /**
         * Accept a returned buffer.
         *
         * @param buffer the buffer
         */
        void returnBuffer(ByteBuffer buffer);
    }
}
