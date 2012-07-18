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

import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import org.xnio.BrokenPipeException;
import org.xnio.Buffers;
import org.xnio.Pooled;

/**
 * An {@code OutputStream} implementation which writes out {@code ByteBuffer}s to a consumer.
 */
public class BufferPipeOutputStream extends OutputStream {

    // internal buffer
    private Pooled<ByteBuffer> buffer;
    // indicates this stream is closed
    private boolean closed;

    private final BufferWriter bufferWriterTask;

    /**
     * Construct a new instance.  The internal buffers will have a capacity of {@code bufferSize}.  The
     * given {@code bufferWriterTask} will be called to send buffers, flush the output stream, and handle the
     * end-of-file condition.
     *
     * @param bufferWriterTask the writer task
     * @throws IOException if an error occurs while initializing the stream
     */
    public BufferPipeOutputStream(final BufferWriter bufferWriterTask) throws IOException {
        this.bufferWriterTask = bufferWriterTask;
        synchronized (this) {
            buffer = bufferWriterTask.getBuffer(true);
        }
    }

    private static IOException closed() {
        return new IOException("Stream is closed");
    }

    private void checkClosed() throws IOException {
        assert Thread.holdsLock(this);
        if (closed) {
            throw closed();
        }
    }

    private Pooled<ByteBuffer> getBuffer() throws IOException {
        assert Thread.holdsLock(this);
        final Pooled<ByteBuffer> buffer = this.buffer;
        if (buffer != null && buffer.getResource().hasRemaining()) {
            return buffer;
        } else {
            if (buffer != null) send(false);
            return this.buffer = bufferWriterTask.getBuffer(false);
        }
    }

    /** {@inheritDoc} */
    public void write(final int b) throws IOException {
        synchronized (this) {
            checkClosed();
            getBuffer().getResource().put((byte) b);
        }
    }

    /** {@inheritDoc} */
    public void write(final byte[] b, int off, int len) throws IOException {
        synchronized (this) {
            checkClosed();
            while (len > 0) {
                final ByteBuffer buffer = getBuffer().getResource();
                final int cnt = Math.min(len, buffer.remaining());
                buffer.put(b, off, cnt);
                len -= cnt;
                off += cnt;
            }
        }
    }

    // call with lock held
    private void send(boolean eof) throws IOException {
        assert Thread.holdsLock(this);
        assert !closed;
        final Pooled<ByteBuffer> pooledBuffer = buffer;
        final ByteBuffer buffer = pooledBuffer == null ? null : pooledBuffer.getResource();
        this.buffer =  null;
        if (buffer != null && buffer.position() > 0) {
            buffer.flip();
            send(pooledBuffer, eof);
        } else if (eof) {
            Pooled<ByteBuffer> pooledBuffer1 = getBuffer();
            final ByteBuffer buffer1 = pooledBuffer1.getResource();
            buffer1.flip();
            send(pooledBuffer1, eof);
        }
    }

    private void send(Pooled<ByteBuffer> buffer, boolean eof) throws IOException {
        assert Thread.holdsLock(this);
        try {
            bufferWriterTask.accept(buffer, eof);
        } catch (IOException e) {
            this.closed = true;
            throw e;
        }
    }

    /** {@inheritDoc} */
    public void flush() throws IOException {
        flush(false);
    }

    private void flush(boolean eof) throws IOException {
        synchronized (this) {
            if (closed) {
                return;
            }
            send(eof);
            try {
                bufferWriterTask.flush();
            } catch (IOException e) {
                closed = true;
                buffer = null;
                throw e;
            }
        }
    }

    /** {@inheritDoc} */
    public void close() throws IOException {
        synchronized (this) {
            if (closed) {
                return;
            }
            try {
                flush(true);
            } finally {
                closed = true;
            }
        }
    }

    /**
     * Break the pipe and return any filling pooled buffer.  Sets the stream to an EOF condition.  Callers to this
     * method should ensure that any threads blocked on {@link BufferWriter#accept(org.xnio.Pooled, boolean)} are
     * unblocked, preferably with a {@link BrokenPipeException}.
     *
     * @return the current pooled buffer, or {@code null} if none was pending
     */
    public Pooled<ByteBuffer> breakPipe() {
        synchronized (this) {
            if (closed) {
                return null;
            }
            closed = true;
            try {
                return buffer;
            } finally {
                buffer = null;
            }
        }
    }

    /**
     * A buffer writer for an {@link BufferPipeOutputStream}.
     */
    public interface BufferWriter extends Flushable {

        /**
         * Get a new buffer to be filled.  The new buffer may, for example, include a prepended header.  This method
         * may block until a buffer is available or until some other condition, such as flow control, is met.
         *
         * @param firstBuffer {@code true} if this is the first buffer in the stream, {@code false} otherwise
         * @return the new buffer
         * @throws IOException if an I/O error occurs
         */
        Pooled<ByteBuffer> getBuffer(boolean firstBuffer) throws IOException;

        /**
         * Accept a buffer.  If this is the last buffer that will be sent, the {@code eof} flag will be set to {@code true}.
         * This method should block until the entire buffer is consumed, or an error occurs.  This method may also block
         * until some other condition, such as flow control, is met.
         *
         * @param pooledBuffer the buffer to send
         * @param eof {@code true} if this is the last buffer which will be sent
         * @throws IOException if an I/O error occurs
         */
        void accept(Pooled<ByteBuffer> pooledBuffer, boolean eof) throws IOException;

        /**
         * Flushes this stream by writing any buffered output to the underlying stream.  This method should block until
         * the data is fully flushed.  This method may also block until some other condition, such as flow control, is
         * met.
         *
         * @throws IOException If an I/O error occurs
         */
        void flush() throws IOException;
    }
}
