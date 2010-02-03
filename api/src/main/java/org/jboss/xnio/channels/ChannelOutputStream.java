/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc., and individual contributors as indicated
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

package org.jboss.xnio.channels;

import java.io.OutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

/**
 * An output stream which writes to a stream sink channel.  All write operations are directly
 * performed upon the channel, so for optimal performance, a buffering output stream should be
 * used to wrap this class.
 *
 * @apiviz.exclude
 * 
 * @since 1.2
 */
public class ChannelOutputStream extends OutputStream {

    protected final StreamSinkChannel channel;
    protected volatile boolean closed;
    protected volatile long timeout;

    /**
     * Construct a new instance.  No write timeout is configured.
     *
     * @param channel the channel to wrap
     */
    public ChannelOutputStream(final StreamSinkChannel channel) {
        this.channel = channel;
    }

    /**
     * Construct a new instance.
     *
     * @param channel the channel to wrap
     * @param timeout the write timeout
     * @param unit the write timeout units
     */
    public ChannelOutputStream(final StreamSinkChannel channel, final long timeout, final TimeUnit unit) {
        if (timeout < 0L) {
            throw new IllegalArgumentException("Negative timeout");
        }
        this.channel = channel;
        final long calcTimeout = unit.toMillis(timeout);
        this.timeout = timeout == 0L ? 0L : calcTimeout < 1L ? 1L : calcTimeout;
    }

    private static IOException closed() {
        return new IOException("The output stream is closed");
    }

    /**
     * Get the write timeout.
     *
     * @param unit the time unit
     * @return the timeout in the given unit
     */
    public long getWriteTimeout(TimeUnit unit) {
        return unit.convert(timeout, TimeUnit.MILLISECONDS);
    }

    /**
     * Set the write timeout.  Does not affect write operations in progress.
     *
     * @param timeout the write timeout, or 0 for none
     * @param unit the time unit
     */
    public void setWriteTimeout(long timeout, TimeUnit unit) {
        if (timeout < 0L) {
            throw new IllegalArgumentException("Negative timeout");
        }
        final long calcTimeout = unit.toMillis(timeout);
        this.timeout = timeout == 0L ? 0L : calcTimeout < 1L ? 1L : calcTimeout;
    }

    /** {@inheritDoc} */
    public void write(final int b) throws IOException {
        if (closed) throw closed();
        final ByteBuffer buffer = ByteBuffer.wrap(new byte[] { (byte) b });
        final long timeout = this.timeout;
        if (timeout == 0L) {
            while (channel.write(buffer) == 0) {
                channel.awaitWritable();
                if (closed) throw closed();
            }
        } else {
            long now = System.currentTimeMillis();
            final long deadline = now + timeout;
            while (channel.write(buffer) == 0) {
                if (now >= deadline) {
                    throw new WriteTimeoutException("Write timed out");
                }
                channel.awaitWritable(deadline - now, TimeUnit.MILLISECONDS);
                if (closed) throw closed();
                now = System.currentTimeMillis();
            }
        }
    }

    /** {@inheritDoc} */
    public void write(final byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    /** {@inheritDoc} */
    public void write(final byte[] b, final int off, final int len) throws IOException {
        if (closed) throw closed();
        final ByteBuffer buffer = ByteBuffer.wrap(b, off, len);
        final long timeout = this.timeout;
        if (timeout == 0L) {
            while (buffer.hasRemaining()) {
                while (channel.write(buffer) == 0) {
                    try {
                        channel.awaitWritable();
                    } catch (InterruptedIOException e) {
                        e.bytesTransferred = buffer.position();
                        throw e;
                    }
                    if (closed) throw closed();
                }
            }
        } else {
            long now = System.currentTimeMillis();
            final long deadline = now + timeout;
            while (buffer.hasRemaining()) {
                while (channel.write(buffer) == 0) {
                    try {
                        if (now >= deadline) {
                            throw new WriteTimeoutException("Write timed out");
                        }
                        channel.awaitWritable(deadline - now, TimeUnit.MILLISECONDS);
                    } catch (InterruptedIOException e) {
                        e.bytesTransferred = buffer.position();
                        throw e;
                    }
                    if (closed) throw closed();
                }
            }
        }
    }

    /** {@inheritDoc} */
    public void flush() throws IOException {
        Channels.flushBlocking(channel);
    }

    /** {@inheritDoc} */
    public void close() throws IOException {
        closed = true;
        Channels.shutdownWritesBlocking(channel);
    }
}
