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

import java.nio.channels.GatheringByteChannel;
import java.nio.ByteBuffer;
import java.io.IOException;
import java.io.Flushable;
import java.util.concurrent.TimeUnit;
import org.xnio.Buffers;

/**
 * A blocking wrapper for a {@code StreamChannel}.  Write operations will block until some data may be transferred.
 * Once any amount of data is written, the operation will return.  If a write timeout is specified, then the write methods
 * will throw a {@link WriteTimeoutException} if the timeout expires without writing any data.
 */
public class BlockingWritableByteChannel implements GatheringByteChannel, Flushable {
    private final StreamSinkChannel delegate;
    private volatile long writeTimeout;

    /**
     * Construct a new instance.
     *
     * @param delegate the channel to forward I/O operations to
     */
    public BlockingWritableByteChannel(final StreamSinkChannel delegate) {
        this.delegate = delegate;
    }

    /**
     * Construct a new instance.
     *
     * @param delegate the channel to forward I/O operations to
     * @param writeTimeout the write timeout
     * @param writeTimeoutUnit the write timeout unit
     */
    public BlockingWritableByteChannel(final StreamSinkChannel delegate, final long writeTimeout, final TimeUnit writeTimeoutUnit) {
        if (writeTimeout < 0L) {
            throw msg.parameterOutOfRange("writeTimeout");
        }
        this.delegate = delegate;
        final long calcTimeout = writeTimeoutUnit.toNanos(writeTimeout);
        this.writeTimeout = writeTimeout == 0L ? 0L : calcTimeout < 1L ? 1L : calcTimeout;
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
     * Perform a blocking, gathering write operation.
     *
     * @param srcs the source buffers
     * @param offset the offset into the destination buffer array
     * @param length the number of buffers to write from
     * @return the number of bytes actually written (will be greater than zero)
     * @throws IOException if an I/O error occurs
     */
    public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        if (!Buffers.hasRemaining(srcs, offset, length)) {
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
