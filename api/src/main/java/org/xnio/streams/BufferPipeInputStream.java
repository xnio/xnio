/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2010 Red Hat, Inc. and/or its affiliates.
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

package org.xnio.streams;

import static org.xnio._private.Messages.msg;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Semaphore;
import org.xnio.Buffers;
import org.xnio.Pooled;
import org.xnio.Xnio;

/**
 * An {@code InputStream} implementation which is populated asynchronously with {@link ByteBuffer} instances.
 */
public class BufferPipeInputStream extends InputStream {
    private final Queue<Pooled<ByteBuffer>> queue;
    private final InputHandler inputHandler;

    // protected by "this"
    private boolean eof;
    private IOException failure;

    /**
     * Construct a new instance.  The given {@code inputHandler} will
     * be invoked after each buffer is fully read and when the stream is closed.
     *
     * @param inputHandler the input events handler
     */
    public BufferPipeInputStream(final InputHandler inputHandler) {
        this.inputHandler = inputHandler;
        queue = new ArrayDeque<Pooled<ByteBuffer>>();
    }

    /**
     * Push a buffer into the queue.  There is no mechanism to limit the number of pushed buffers; if such a mechanism
     * is desired, it must be implemented externally, for example maybe using a {@link Semaphore}.
     *
     * @param buffer the buffer from which more data should be read
     */
    public void push(final ByteBuffer buffer) {
        synchronized (this) {
            if (buffer.hasRemaining() && !eof && failure == null) {
                queue.add(Buffers.pooledWrapper(buffer));
                notifyAll();
            }
        }
    }

    /**
     * Push a buffer into the queue.  There is no mechanism to limit the number of pushed buffers; if such a mechanism
     * is desired, it must be implemented externally, for example maybe using a {@link Semaphore}.
     *
     * @param pooledBuffer the buffer from which more data should be read
     */
    public void push(final Pooled<ByteBuffer> pooledBuffer) {
        synchronized (this) {
            if (pooledBuffer.getResource().hasRemaining() && !eof && failure == null) {
                queue.add(pooledBuffer);
                notifyAll();
            } else {
                pooledBuffer.free();
            }
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
    
    /** {@inheritDoc} */
    public int read() throws IOException {
        final Queue<Pooled<ByteBuffer>> queue = this.queue;
        synchronized (this) {
            while (queue.isEmpty()) {
                if (eof) {
                    return -1;
                }
                checkFailure();
                Xnio.checkBlockingAllowed();
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw msg.interruptedIO();
                }
            }
            final Pooled<ByteBuffer> entry = queue.peek();
            final ByteBuffer buf = entry.getResource();
            final int v = buf.get() & 0xff;
            if (buf.remaining() == 0) {
                queue.poll();
                try {
                    inputHandler.acknowledge(entry);
                } catch (IOException e) {
                    // no operation!
                } finally {
                    entry.free();
                }
            }
            return v;
        }
    }

    private void clearQueue() {
        synchronized (this) {
            Pooled<ByteBuffer> entry;
            while ((entry = queue.poll()) != null) {
                entry.free();
            }
        }
    }

    /** {@inheritDoc} */
    public int read(final byte[] b, int off, int len) throws IOException {
        if (len == 0) {
            return 0;
        }
        final Queue<Pooled<ByteBuffer>> queue = this.queue;
        synchronized (this) {
            while (queue.isEmpty()) {
                if (eof) {
                    return -1;
                }
                checkFailure();
                Xnio.checkBlockingAllowed();
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw msg.interruptedIO();
                }
            }
            int total = 0;
            while (len > 0) {
                final Pooled<ByteBuffer> entry = queue.peek();
                if (entry == null) {
                    break;
                }
                final ByteBuffer buffer = entry.getResource();
                final int byteCnt = Math.min(buffer.remaining(), len);
                buffer.get(b, off, byteCnt);
                off += byteCnt;
                total += byteCnt;
                len -= byteCnt;
                if (buffer.remaining() == 0) {
                    queue.poll();
                    try {
                        inputHandler.acknowledge(entry);
                    } catch (IOException e) {
                        // no operation!
                    } finally {
                        entry.free();
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
            for (Pooled<ByteBuffer> entry : queue) {
                total += entry.getResource().remaining();
                if (total < 0) {
                    return Integer.MAX_VALUE;
                }
            }
            return total;
        }
    }

    public long skip(long qty) throws IOException {
        final Queue<Pooled<ByteBuffer>> queue = this.queue;
        synchronized (this) {
            while (queue.isEmpty()) {
                if (eof) {
                    return 0L;
                }
                checkFailure();
                Xnio.checkBlockingAllowed();
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw msg.interruptedIO();
                }
            }
            long skipped = 0L;
            while (qty > 0L) {
                final Pooled<ByteBuffer> entry = queue.peek();
                if (entry == null) {
                    break;
                }
                final ByteBuffer buffer = entry.getResource();
                final int byteCnt = (int) Math.min(buffer.remaining(), Math.max((long)Integer.MAX_VALUE, qty));
                buffer.position(buffer.position() + byteCnt);
                skipped += byteCnt;
                qty -= byteCnt;
                if (buffer.remaining() == 0) {
                    queue.poll();
                    try {
                        inputHandler.acknowledge(entry);
                    } catch (IOException e) {
                        // no operation!
                    } finally {
                        entry.free();
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
                failure = null;
                notifyAll();
                inputHandler.close();
            }
        }
    }

    private void checkFailure() throws IOException {
        assert Thread.holdsLock(this);
        final IOException failure = this.failure;
        if (failure != null) {
            failure.fillInStackTrace();
            try {
                throw failure;
            } finally {
                clearQueue();
                notifyAll();
            }
        }
    }

    /**
     * A handler for events relating to the consumption of data from a {@link BufferPipeInputStream} instance.
     */
    public interface InputHandler extends Closeable {

        /**
         * Acknowledges the successful processing of an input buffer.  Though this method may throw an exception,
         * it is not acted upon.  The acknowledged resource is passed in, with its position set to the number of
         * bytes consumed.
         *
         * @param pooled the pooled resource which was consumed
         * @throws IOException if an I/O error occurs sending the acknowledgement
         */
        void acknowledge(Pooled<ByteBuffer> pooled) throws IOException;

        /**
         * Signifies that the user of the enclosing {@link BufferPipeInputStream} has called the {@code close()} method
         * explicitly.  Any thrown exception is propagated up to the caller of {@link BufferPipeInputStream#close() NioByteInput.close()}.
         *
         * @throws IOException if an I/O error occurs
         */
        void close() throws IOException;
    }
}
