/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, JBoss Inc., and individual contributors as indicated
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

import java.io.InputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import static java.lang.Math.min;
import org.jboss.xnio.Buffers;

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
     * Read a byte, blocking if necessary.
     *
     * @return the byte read, or -1 if the end of the stream has been reached
     * @throws IOException if an I/O error occurs
     */
    public int read() throws IOException {
        if (closed) return -1;
        final ByteBuffer buffer = this.buffer;
        final StreamSourceChannel channel = this.channel;
        while (! buffer.hasRemaining()) {
            buffer.clear();
            final int res = Channels.readBlocking(channel, buffer);
            if (res == -1) {
                return -1;
            }
            buffer.flip();
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
     * a 0 is returned, the channel's {@link SuspendableReadChannel#resumeReads() resumeReads()} method may be used
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
