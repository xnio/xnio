/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2009 Red Hat, Inc. and/or its affiliates.
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

package org.xnio.channels;

import static org.xnio._private.Messages.msg;

import java.nio.channels.ScatteringByteChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ByteChannel;
import java.nio.ByteBuffer;
import java.io.IOException;
import java.io.Flushable;
import java.util.concurrent.TimeUnit;
import org.xnio.Buffers;

/**
 * A blocking wrapper for a {@code StreamChannel}.  Read and write operations will block until some data may be transferred.
 * Once any amount of data is read or written, the operation will return.  If a read timeout is specified, then the read methods
 * will throw a {@link ReadTimeoutException} if the timeout expires without reading any data.  If a write timeout is specified, then the write methods
 * will throw a {@link WriteTimeoutException} if the timeout expires without writing any data.
 */
public class BlockingByteChannel implements ScatteringByteChannel, GatheringByteChannel, ByteChannel, Flushable {
    private final StreamChannel delegate;
    private volatile long readTimeout;
    private volatile long writeTimeout;

    /**
     * Construct a new instance.
     *
     * @param delegate the channel to forward I/O operations to
     */
    public BlockingByteChannel(final StreamChannel delegate) {
        this.delegate = delegate;
    }

    /**
     * Construct a new instance.
     *
     * @param delegate the channel to forward I/O operations to
     * @param timeout the read/write timeout
     * @param timeoutUnit the read/write timeout unit
     */
    public BlockingByteChannel(final StreamChannel delegate, final long timeout, final TimeUnit timeoutUnit) {
        this(delegate, timeout, timeoutUnit, timeout, timeoutUnit);
    }

    /**
     * Construct a new instance.
     *
     * @param delegate the channel to forward I/O operations to
     * @param readTimeout the read timeout
     * @param readTimeoutUnit the read timeout unit
     * @param writeTimeout the write timeout
     * @param writeTimeoutUnit the write timeout unit
     */
    public BlockingByteChannel(final StreamChannel delegate, final long readTimeout, final TimeUnit readTimeoutUnit, final long writeTimeout, final TimeUnit writeTimeoutUnit) {
        if (readTimeout < 0L) {
            throw msg.parameterOutOfRange("readTimeout");
        }
        if (writeTimeout < 0L) {
            throw msg.parameterOutOfRange("writeTimeout");
        }
        final long calcReadTimeout = readTimeoutUnit.toNanos(readTimeout);
        this.readTimeout = readTimeout == 0L ? 0L : calcReadTimeout < 1L ? 1L : calcReadTimeout;
        final long calcWriteTimeout = writeTimeoutUnit.toNanos(writeTimeout);
        this.writeTimeout = writeTimeout == 0L ? 0L : calcWriteTimeout < 1L ? 1L : calcWriteTimeout;
        this.delegate = delegate;
    }

    /**
     * Set the read timeout.
     *
     * @param readTimeout the read timeout
     * @param readTimeoutUnit the read timeout unit
     */
    public void setReadTimeout(long readTimeout, TimeUnit readTimeoutUnit) {
        if (readTimeout < 0L) {
            throw msg.parameterOutOfRange("readTimeout");
        }
        final long calcTimeout = readTimeoutUnit.toNanos(readTimeout);
        this.readTimeout = readTimeout == 0L ? 0L : calcTimeout < 1L ? 1L : calcTimeout;
    }

    /**
     * Set the write timeout.
     *
     * @param writeTimeout the write timeout
     * @param writeTimeoutUnit the write timeout unit
     */
    public void setWriteTimeout(long writeTimeout, TimeUnit writeTimeoutUnit) {
        if (writeTimeout < 0L) {
            throw msg.parameterOutOfRange("writeTimeout");
        }
        final long calcTimeout = writeTimeoutUnit.toNanos(writeTimeout);
        this.writeTimeout = writeTimeout == 0L ? 0L : calcTimeout < 1L ? 1L : calcTimeout;
    }

    /**
     * Perform a blocking, scattering read operation.
     *
     * @param dsts the destination buffers
     * @param offset the offset into the destination buffer array
     * @param length the number of buffers to read into
     * @return the number of bytes actually read (will be greater than zero)
     * @throws IOException if an I/O error occurs
     */
    public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        if (! Buffers.hasRemaining(dsts, offset, length)) {
            return 0L;
        }
        final StreamSourceChannel delegate = this.delegate;
        long res;
        if ((res = delegate.read(dsts, offset, length)) == 0L) {
            long start = System.nanoTime();
            long elapsed = 0L, readTimeout;
            do {
                readTimeout = this.readTimeout;
                if (readTimeout == 0L || readTimeout == Long.MAX_VALUE) {
                    delegate.awaitReadable();
                } else if (readTimeout <= elapsed) {
                    throw msg.readTimeout();
                } else {
                    delegate.awaitReadable(readTimeout - elapsed, TimeUnit.NANOSECONDS);
                }
                elapsed = System.nanoTime() - start;
            } while ((res = delegate.read(dsts, offset, length)) == 0L);
        }
        return res;
    }

    /**
     * Perform a blocking, scattering read operation.
     *
     * @param dsts the destination buffers
     * @return the number of bytes actually read (will be greater than zero)
     * @throws IOException if an I/O error occurs
     */
    public long read(final ByteBuffer[] dsts) throws IOException {
        return read(dsts, 0, dsts.length);
    }

    /**
     * Perform a blocking read operation.
     *
     * @param dst the destination buffer
     * @return the number of bytes actually read (will be greater than zero)
     * @throws IOException if an I/O error occurs
     */
    public int read(final ByteBuffer dst) throws IOException {
        if (! dst.hasRemaining()) {
            return 0;
        }
        final StreamSourceChannel delegate = this.delegate;
        int res;
        if ((res = delegate.read(dst)) == 0) {
            long start = System.nanoTime();
            long elapsed = 0L, readTimeout;
            do {
                readTimeout = this.readTimeout;
                if (readTimeout == 0L || readTimeout == Long.MAX_VALUE) {
                    delegate.awaitReadable();
                } else if (readTimeout <= elapsed) {
                    throw msg.readTimeout();
                } else {
                    delegate.awaitReadable(readTimeout - elapsed, TimeUnit.NANOSECONDS);
                }
                elapsed = System.nanoTime() - start;
            } while ((res = delegate.read(dst)) == 0);
        }
        return res;
    }

    /**
     * Perform a blocking, gathering write operation.
     *
     * @param srcs the source buffers
     * @param offset the offset into the destination buffer array
     * @param length the number of buffers to write from
     * @return the number of bytes actually written (will be greater than zero)
     * @throws IOException if an I/O error occurs
     */
    public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        if (! Buffers.hasRemaining(srcs, offset, length)) {
            return 0L;
        }
        final StreamSinkChannel delegate = this.delegate;
        long res;
        if ((res = delegate.write(srcs, offset, length)) == 0L) {
            long start = System.nanoTime();
            long elapsed = 0L, writeTimeout;
            do {
                writeTimeout = this.writeTimeout;
                if (writeTimeout == 0L || writeTimeout == Long.MAX_VALUE) {
                    delegate.awaitWritable();
                } else if (writeTimeout <= elapsed) {
                    throw msg.writeTimeout();
                } else {
                    delegate.awaitWritable(writeTimeout - elapsed, TimeUnit.NANOSECONDS);
                }
                elapsed = System.nanoTime() - start;
            } while ((res = delegate.write(srcs, offset, length)) == 0L);
        }
        return res;
    }

    /**
     * Perform a blocking, gathering write operation.
     *
     * @param srcs the source buffers
     * @return the number of bytes actually written (will be greater than zero)
     * @throws IOException if an I/O error occurs
     */
    public long write(final ByteBuffer[] srcs) throws IOException {
        return write(srcs, 0, srcs.length);
    }

    /**
     * Perform a blocking write operation.
     *
     * @param src the source buffer
     * @return the number of bytes actually written (will be greater than zero)
     * @throws IOException if an I/O error occurs
     */
    public int write(final ByteBuffer src) throws IOException {
        if (! src.hasRemaining()) {
            return 0;
        }
        final StreamSinkChannel delegate = this.delegate;
        int res;
        if ((res = delegate.write(src)) == 0L) {
            long start = System.nanoTime();
            long elapsed = 0L, writeTimeout;
            do {
                writeTimeout = this.writeTimeout;
                if (writeTimeout == 0L || writeTimeout == Long.MAX_VALUE) {
                    delegate.awaitWritable();
                } else if (writeTimeout <= elapsed) {
                    throw msg.writeTimeout();
                } else {
                    delegate.awaitWritable(writeTimeout - elapsed, TimeUnit.NANOSECONDS);
                }
                elapsed = System.nanoTime() - start;
            } while ((res = delegate.write(src)) == 0L);
        }
        return res;
    }

    /** {@inheritDoc} */
    public boolean isOpen() {
        return delegate.isOpen();
    }

    /** {@inheritDoc} */
    public void flush() throws IOException {
        final StreamSinkChannel delegate = this.delegate;
        if (! delegate.flush()) {
            long start = System.nanoTime();
            long elapsed = 0L, writeTimeout;
            do {
                writeTimeout = this.writeTimeout;
                if (writeTimeout == 0L || writeTimeout == Long.MAX_VALUE) {
                    delegate.awaitWritable();
                } else if (writeTimeout <= elapsed) {
                    throw msg.writeTimeout();
                } else {
                    delegate.awaitWritable(writeTimeout - elapsed, TimeUnit.NANOSECONDS);
                }
                elapsed = System.nanoTime() - start;
            } while (! delegate.flush());
        }
    }

    /** {@inheritDoc} */
    public void close() throws IOException {
        delegate.close();
    }
}
