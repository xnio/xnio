/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008 Red Hat, Inc. and/or its affiliates.
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

import java.io.InputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import org.xnio.Bits;
import org.xnio.channels.Channels;
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
            throw msg.nullParameter("channel");
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
            throw msg.nullParameter("channel");
        }
        if (timeoutUnit == null) {
            throw msg.nullParameter("timeoutUnit");
        }
        if (timeout < 0L) {
            throw msg.parameterOutOfRange("timeout");
        }
        this.channel = channel;
        final long calcTimeout = timeoutUnit.toNanos(timeout);
        this.timeout = timeout == 0L ? 0L : calcTimeout < 1L ? 1L : calcTimeout;
    }

    private boolean enter() {
        int old = flags;
        do {
            if (Bits.allAreSet(old, FLAG_ENTERED)) {
                throw msg.concurrentAccess();
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
        if (unit == null) {
            throw msg.nullParameter("unit");
        }
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
            throw msg.parameterOutOfRange("timeout");
        }
        if (unit == null) {
            throw msg.nullParameter("unit");
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
                        throw msg.readTimeout();
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
                        throw msg.readTimeout();
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
            long res;
            for (;;) {
                if (n == 0L) return total;
                res = Channels.drain(channel, n);
                if (res == -1) {
                    return total;
                } else if (res == 0) {
                    timeout = this.timeout;
                    try {
                        if (timeout == 0L) {
                            channel.awaitReadable();
                        } else if (timeout < elapsed) {
                            throw msg.readTimeout();
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
                    total += res;
                    n -= res;
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
