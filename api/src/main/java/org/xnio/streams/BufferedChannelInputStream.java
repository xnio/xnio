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

import java.io.InputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import static java.lang.Math.min;

import java.util.concurrent.TimeUnit;
import org.xnio.Buffers;
import org.xnio.channels.Channels;
import org.xnio.channels.ReadTimeoutException;
import org.xnio.channels.StreamSourceChannel;

/**
 * An input stream which reads from a stream source channel with a buffer.  In addition, the
 * {@link #available()} method can be used to determine whether the next read will or will not block.
 *
 * @apiviz.exclude
 *
 * @since 2.1
 */
public class BufferedChannelInputStream extends InputStream {
    private final StreamSourceChannel channel;
    private final ByteBuffer buffer;
    private volatile boolean closed;
    private volatile long timeout;

    /**
     * Construct a new instance.
     *
     * @param channel the channel to wrap
     * @param bufferSize the size of the internal buffer
     */
    public BufferedChannelInputStream(final StreamSourceChannel channel, final int bufferSize) {
        if (channel == null) {
            throw new NullPointerException("channel is null");
        }
        if (bufferSize < 1) {
            throw new IllegalArgumentException("Buffer size must be at least one byte");
        }
        this.channel = channel;
        buffer = ByteBuffer.allocate(bufferSize);
        buffer.limit(0);
    }

    /**
     * Construct a new instance.
     *
     * @param channel the channel to wrap
     * @param bufferSize the size of the internal buffer
     * @param timeout the initial read timeout, or O for none
     * @param unit the time unit for the read timeout
     */
    public BufferedChannelInputStream(final StreamSourceChannel channel, final int bufferSize, final long timeout, final TimeUnit unit) {
        if (channel == null) {
            throw new NullPointerException("channel is null");
        }
        if (unit == null) {
            throw new NullPointerException("unit is null");
        }
        if (bufferSize < 1) {
            throw new IllegalArgumentException("Buffer size must be at least one byte");
        }
        if (timeout < 0L) {
            throw new IllegalArgumentException("Negative timeout");
        }
        this.channel = channel;
        buffer = ByteBuffer.allocate(bufferSize);
        buffer.limit(0);
        final long calcTimeout = unit.toMillis(timeout);
        this.timeout = timeout == 0L ? 0L : calcTimeout < 1L ? 1L : calcTimeout;
    }

    /**
     * Get the read timeout.
     *
     * @param unit the time unit
     * @return the timeout in the given unit
     */
    public long getReadTimeout(TimeUnit unit) {
        return unit.convert(timeout, TimeUnit.MILLISECONDS);
    }

    /**
     * Set the read timeout.  Does not affect read operations in progress.
     *
     * @param timeout the read timeout, or 0 for none
     * @param unit the time unit
     */
    public void setReadTimeout(long timeout, TimeUnit unit) {
        if (timeout < 0L) {
            throw new IllegalArgumentException("Negative timeout");
        }
        final long calcTimeout = unit.toMillis(timeout);
        this.timeout = timeout == 0L ? 0L : calcTimeout < 1L ? 1L : calcTimeout;
    }

    /**
     * Read a byte, blocking if necessary.
     *
     * @return the byte read, or -1 if the end of the stream has been reached
     * @throws IOException if an I/O error occurs
     */
    public int read() throws IOException {
        if (closed) return -1;
        final ByteBuffer buffer = this.buffer;
        final StreamSourceChannel channel = this.channel;
        final long timeout = this.timeout;
        if (timeout == 0L) {
            while (! buffer.hasRemaining()) {
                buffer.clear();
                final int res = Channels.readBlocking(channel, buffer);
                if (res == -1) {
                    return -1;
                }
                buffer.flip();
            }
        } else {
            if (! buffer.hasRemaining()) {
                long now = System.currentTimeMillis();
                final long deadline = timeout - now;
                do {
                    buffer.clear();
                    if (deadline <= now) {
                        throw new ReadTimeoutException("Read timed out");
                    }
                    final int res = Channels.readBlocking(channel, buffer, deadline - now, TimeUnit.MILLISECONDS);
                    if (res == -1) {
                        return -1;
                    }
                    buffer.flip();
                } while (! buffer.hasRemaining());
            }
        }
        return buffer.get() & 0xff;
    }

    /**
     * Read bytes into an array.
     *
     * @param b the destination array
     * @param off the offset into the array at which bytes should be filled
     * @param len the number of bytes to fill
     * @return the number of bytes read, or -1 if the end of the stream has been reached
     * @throws IOException if an I/O error occurs
     */
    public int read(final byte[] b, int off, int len) throws IOException {
        if (len < 1) {
            return 0;
        }
        int total = 0;
        final ByteBuffer buffer = this.buffer;
        if (buffer.hasRemaining()) {
            final int cnt = min(buffer.remaining(), len);
            buffer.get(b, off, len);
            total += cnt;
            off += cnt;
            len -= cnt;
        }
        if (closed) return -1;
        final StreamSourceChannel channel = this.channel;
        final long timeout = this.timeout;
        try {
            if (timeout == 0L) {
                while (len > 0) {
                    final ByteBuffer dst = ByteBuffer.wrap(b, off, len);
                    int res = total > 0 ? channel.read(dst) : Channels.readBlocking(channel, dst);
                    if (res == -1) {
                        return total == 0 ? -1 : total;
                    }
                    total += res;
                    if (res == 0) {
                        break;
                    }
                }
            } else {
                while (len > 0) {
                    final ByteBuffer dst = ByteBuffer.wrap(b, off, len);
                    int res;
                    if (total > 0) {
                        res = channel.read(dst);
                    } else {
                        res = Channels.readBlocking(channel, dst, timeout, TimeUnit.MILLISECONDS);
                        if (res == 0) {
                            throw new ReadTimeoutException("Read timed out");
                        }
                    }
                    if (res == -1) {
                        return total == 0 ? -1 : total;
                    }
                    total += res;
                    if (res == 0) {
                        break;
                    }
                }
            }
        } catch (InterruptedIOException e) {
            e.bytesTransferred = total;
            throw e;
        }
        return total;
    }

    /**
     * Skip bytes in the stream.
     *
     * @param n the number of bytes to skip
     * @return the number of bytes skipped (0 if the end of stream has been reached)
     * @throws IOException if an I/O error occurs
     */
    public long skip(long n) throws IOException {
        if (n < 1L) {
            return 0L;
        }
        long total = 0L;
        final ByteBuffer buffer = this.buffer;
        if (buffer.hasRemaining()) {
            final int cnt = (int) min(buffer.remaining(), n);
            Buffers.skip(buffer, cnt);
            total += cnt;
            n -= cnt;
        }
        if (closed) {
            return total;
        }
        final StreamSourceChannel channel = this.channel;
        if (n > 0L) {
            // Buffer was cleared
            try {
                while (n > 0L) {
                    buffer.clear();
                    int res = total > 0L ? channel.read(buffer) : Channels.readBlocking(channel, buffer);
                    if (res <= 0) {
                        return total;
                    }
                    total += (long) res;
                }
            } finally {
                buffer.position(0).limit(0);
            }
        }
        return total;
    }

    /**
     * Return the number of bytes available to read, or 0 if a subsequent {@code read()} operation would block.  If
     * a 0 is returned, the channel's {@link org.xnio.channels.SuspendableReadChannel#resumeReads() resumeReads()} method may be used
     * to register for read-readiness.
     *
     * @return the number of ready bytes, or 0 for none
     * @throws IOException if an I/O error occurs
     */
    public int available() throws IOException {
        final ByteBuffer buffer = this.buffer;
        final int rem = buffer.remaining();
        if (rem > 0 || closed) {
            return rem;
        }
        buffer.clear();
        try {
            channel.read(buffer);
        } catch (IOException e) {
            buffer.limit(0);
            throw e;
        }
        buffer.flip();
        return buffer.remaining();
    }

    /**
     * Close the stream.  Shuts down the channel's read side.
     *
     * @throws IOException if an I/O error occurs
     */
    public void close() throws IOException {
        closed = true;
        channel.shutdownReads();
    }
}
