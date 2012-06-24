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
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import org.xnio.Bits;
import org.xnio.Buffers;
import org.xnio.channels.Channels;
import org.xnio.channels.ConcurrentStreamChannelAccessException;
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
    @SuppressWarnings("unused")
    private volatile int flags;
    private volatile long timeout;

    private static final AtomicIntegerFieldUpdater<BufferedChannelInputStream> flagsUpdater = AtomicIntegerFieldUpdater.newUpdater(BufferedChannelInputStream.class, "flags");

    private static final int FLAG_EOF = 2;
    private static final int FLAG_ENTERED = 1;

    /**
     * Construct a new instance.
     *
     * @param channel the channel to wrap
     * @param bufferSize the size of the internal buffer
     */
    public BufferedChannelInputStream(final StreamSourceChannel channel, final int bufferSize) {
        if (channel == null) {
            throw new IllegalArgumentException("channel is null");
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
            throw new IllegalArgumentException("channel is null");
        }
        if (unit == null) {
            throw new IllegalArgumentException("unit is null");
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
        final long calcTimeout = unit.toNanos(timeout);
        this.timeout = timeout == 0L ? 0L : calcTimeout < 1L ? 1L : calcTimeout;
    }

    private boolean enter() {
        int old = flags;
        do {
            if (Bits.allAreSet(old, FLAG_ENTERED)) {
                throw new ConcurrentStreamChannelAccessException();
            }
        } while (! flagsUpdater.compareAndSet(this, old, old | FLAG_ENTERED));
        return Bits.allAreSet(old, FLAG_EOF);
    }

    private void exit(boolean setEof) {
        int oldFlags, newFlags;
        do {
            oldFlags = flags;
            newFlags = oldFlags &~ FLAG_ENTERED;
            if (setEof) {
                newFlags |= FLAG_EOF;
            }
        } while (! flagsUpdater.compareAndSet(this, oldFlags, newFlags));
    }

    /**
     * Get the read timeout.
     *
     * @param unit the time unit
     * @return the timeout in the given unit
     */
    public long getReadTimeout(TimeUnit unit) {
        return unit.convert(timeout, TimeUnit.NANOSECONDS);
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
        final long calcTimeout = unit.toNanos(timeout);
        this.timeout = timeout == 0L ? 0L : calcTimeout < 1L ? 1L : calcTimeout;
    }

    /**
     * Read a byte, blocking if necessary.
     *
     * @return the byte read, or -1 if the end of the stream has been reached
     * @throws IOException if an I/O error occurs
     */
    public int read() throws IOException {
        boolean eof = enter();
        try {
            final StreamSourceChannel channel = this.channel;
            final ByteBuffer buffer = this.buffer;
            // try buffer first
            if (buffer.hasRemaining()) {
                return buffer.get() & 0xff;
            }
            if (eof) {
                return -1;
            }
            // fill buffer
            int res;
            long timeout;
            long start = System.nanoTime();
            long elapsed = 0L;
            for (;;) {
                buffer.clear();
                try {
                    res = channel.read(buffer);
                } finally {
                    buffer.flip();
                }
                if (res == -1) {
                    eof = true;
                    return -1;
                }
                if (res > 0) {
                    return buffer.get() & 0xff;
                }
                timeout = this.timeout;
                if (timeout == 0L) {
                    channel.awaitReadable();
                } else if (timeout < elapsed) {
                    throw new ReadTimeoutException("Read timed out");
                } else {
                    channel.awaitReadable(timeout - elapsed, TimeUnit.NANOSECONDS);
                }
                elapsed = System.nanoTime() - start;
            }
        } finally {
            exit(eof);
        }
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
        boolean eof = enter();
        try {
            int total = 0;
            // empty buffer
            final ByteBuffer buffer = this.buffer;
            final ByteBuffer userBuffer = ByteBuffer.wrap(b, off, len);
            if (buffer.hasRemaining()) {
                total += Buffers.copy(userBuffer, buffer);
                // either the user buffer is full, or the source buffer is empty
                if (! userBuffer.hasRemaining()) {
                    return total;
                }
            }
            // at this point the buffer is guaranteed to be empty
            assert ! buffer.hasRemaining();
            assert userBuffer.hasRemaining();
            if (eof) return total == 0 ? -1 : total;
            // read the rest directly into the user buffer
            final StreamSourceChannel channel = this.channel;
            long timeout;
            long start = System.nanoTime();
            long elapsed = 0L;
            int res;
            for (;;) {
                res = channel.read(userBuffer);
                if (res == -1) {
                    eof = true;
                    return total == 0 ? -1 : total;
                }
                total += res;
                if (total > 0) {
                    return total;
                }
                timeout = this.timeout;
                try {
                    if (timeout == 0L) {
                        channel.awaitReadable();
                    } else if (timeout < elapsed) {
                        throw new ReadTimeoutException("Read timed out");
                    } else {
                        channel.awaitReadable(timeout - elapsed, TimeUnit.NANOSECONDS);
                    }
                } catch (InterruptedIOException e) {
                    e.bytesTransferred = total;
                    throw e;
                }
                elapsed = System.nanoTime() - start;
            }
        } finally {
            exit(eof);
        }
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
        boolean eof = enter();
        try {
            // if we don't do this, InterruptedIOException might not be able to report a correct result
            n = Math.min(n, (long)Integer.MAX_VALUE);
            long total = 0L;
            final ByteBuffer buffer = this.buffer;
            if (buffer.hasRemaining()) {
                final int cnt = (int) min(buffer.remaining(), n);
                Buffers.skip(buffer, cnt);
                total += cnt;
                n -= cnt;
                assert n == 0L || ! buffer.hasRemaining();
                if (n == 0L) {
                    return total;
                }
            }
            assert ! buffer.hasRemaining();
            if (eof) {
                return total;
            }
            long timeout;
            long start = System.nanoTime();
            long elapsed = 0L;
            ByteBuffer tmp = null;
            for (;;) {
                if ((total += Channels.drain(channel, n)) == 0L) {
                    // the channel may be at EOF; fill the buffer to be sure.
                    if (tmp == null) {
                        tmp = ByteBuffer.allocate(1);
                    }
                    tmp.clear();
                    int res = channel.read(tmp);
                    if (res == -1) {
                        return total;
                    } else if (res == 1) {
                        total ++;
                    }
                    timeout = this.timeout;
                    try {
                        if (timeout == 0L) {
                            channel.awaitReadable();
                        } else if (timeout < elapsed) {
                            throw new ReadTimeoutException("Read timed out");
                        } else {
                            channel.awaitReadable(timeout - elapsed, TimeUnit.NANOSECONDS);
                        }
                    } catch (InterruptedIOException e) {
                        assert total < (long) Integer.MAX_VALUE;
                        e.bytesTransferred = (int) total;
                        throw e;
                    }
                    elapsed = System.nanoTime() - start;
                } else {
                    return total;
                }
            }
        } finally {
            exit(eof);
        }
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
        boolean eof = enter();
        try {
            final ByteBuffer buffer = this.buffer;
            final int rem = buffer.remaining();
            if (rem > 0 || eof) {
                return rem;
            }
            buffer.clear();
            try {
                channel.read(buffer);
            } catch (IOException e) {
                throw e;
            } finally {
                buffer.flip();
            }
            return buffer.remaining();
        } finally {
            exit(eof);
        }
    }

    /**
     * Close the stream.  Shuts down the channel's read side.
     *
     * @throws IOException if an I/O error occurs
     */
    public void close() throws IOException {
        enter();
        try {
            buffer.clear().flip();
            channel.shutdownReads();
        } finally {
            exit(true);
        }
    }
}
