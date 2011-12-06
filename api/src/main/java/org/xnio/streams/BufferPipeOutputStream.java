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

import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

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
