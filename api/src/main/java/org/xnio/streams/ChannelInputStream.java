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

package org.xnio.streams;

import java.io.InputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import org.xnio.Bits;
import org.xnio.channels.Channels;
import org.xnio.channels.ConcurrentStreamChannelAccessException;
import org.xnio.channels.ReadTimeoutException;
import org.xnio.channels.StreamSourceChannel;

/**
 * An input stream which reads from a stream source channel.  All read operations are directly
 * performed upon the channel, so for optimal performance, a buffering input stream should be
 * used to wrap this class.
 *
 * @apiviz.exclude
 * 
 * @since 1.2
 */
public class ChannelInputStream extends InputStream {
    protected final StreamSourceChannel channel;
    @SuppressWarnings("unused")
    private volatile int flags;
    private volatile long timeout;

    private static final AtomicIntegerFieldUpdater<ChannelInputStream> flagsUpdater = AtomicIntegerFieldUpdater.newUpdater(ChannelInputStream.class, "flags");

    private static final int FLAG_EOF = 2;
    private static final int FLAG_ENTERED = 1;

    /**
     * Construct a new instance.  The stream will have no read timeout.
     *
     * @param channel the channel to wrap
     */
    public ChannelInputStream(final StreamSourceChannel channel) {
        if (channel == null) {
            throw new IllegalArgumentException("channel is null");
        }
        this.channel = channel;
    }

    /**
     * Construct a new instance.
     *
     * @param channel the channel to wrap
     * @param timeout the read timeout, or O for none
     * @param timeoutUnit the time unit for read timeouts
     */
    public ChannelInputStream(final StreamSourceChannel channel, final long timeout, final TimeUnit timeoutUnit) {
        if (channel == null) {
            throw new IllegalArgumentException("Null channel");
        }
        if (timeoutUnit == null) {
            throw new IllegalArgumentException("Null timeoutUnit");
        }
        if (timeout < 0L) {
            throw new IllegalArgumentException("Negative timeout");
        }
        this.channel = channel;
        final long calcTimeout = timeoutUnit.toNanos(timeout);
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

    /** {@inheritDoc} */
    public int read() throws IOException {
        boolean eof = enter();
        try {
            if (eof) return -1;
            final byte[] array = new byte[1];
            final ByteBuffer buffer = ByteBuffer.wrap(array);
            int res = channel.read(buffer);
            if (res == 0) {
                long timeout;
                long start = System.nanoTime();
                long elapsed = 0L;
                do {
                    timeout = this.timeout;
                    if (timeout == 0L) {
                        channel.awaitReadable();
                    } else if (timeout < elapsed) {
                        throw new ReadTimeoutException("Read timed out");
                    } else {
                        channel.awaitReadable(timeout - elapsed, TimeUnit.NANOSECONDS);
                    }
                    elapsed = System.nanoTime() - start;
                    res = channel.read(buffer);
                } while (res == 0);
            }
            return (eof = res == -1) ? -1 : array[0] & 0xff;
        } finally {
            exit(eof);
        }
    }

    /** {@inheritDoc} */
    public int read(final byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    /** {@inheritDoc} */
    public int read(final byte[] b, final int off, final int len) throws IOException {
        if (len < 1 || off+len > b.length) {
            return 0;
        }
        boolean eof = enter();
        try {
            if (eof) return -1;
            final ByteBuffer buffer = ByteBuffer.wrap(b, off, len);
            int res = channel.read(buffer);
            if (res == 0) {
                long timeout;
                long start = System.nanoTime();
                long elapsed = 0L;
                do {
                    timeout = this.timeout;
                    if (timeout == 0L) {
                        channel.awaitReadable();
                    } else if (timeout < elapsed) {
                        throw new ReadTimeoutException("Read timed out");
                    } else {
                        channel.awaitReadable(timeout - elapsed, TimeUnit.NANOSECONDS);
                    }
                    elapsed = System.nanoTime() - start;
                    res = channel.read(buffer);
                } while (res == 0);
            }
            return (eof = res == -1) ? -1 : buffer.position() - off;
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
            if (eof) return 0L;
            // if we don't do this, InterruptedIOException might not be able to report a correct result
            n = Math.min(n, (long)Integer.MAX_VALUE);
            long total = 0L;
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
                    } else {
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
                    }
                } else {
                    return total;
                }
            }
        } finally {
            exit(eof);
        }
    }

    /** {@inheritDoc} */
    public void close() throws IOException {
        enter();
        try {
            channel.shutdownReads();
        } finally {
            exit(true);
        }
    }
}
