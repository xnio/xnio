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

import java.io.OutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import org.xnio.Bits;
import org.xnio.channels.StreamSinkChannel;

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
    @SuppressWarnings("unused")
    private volatile int flags;
    private volatile long timeout;

    private static final AtomicIntegerFieldUpdater<ChannelOutputStream> flagsUpdater = AtomicIntegerFieldUpdater.newUpdater(ChannelOutputStream.class, "flags");

    private static final int FLAG_CLOSED = 2;
    private static final int FLAG_ENTERED = 1;

    /**
     * Construct a new instance.  No write timeout is configured.
     *
     * @param channel the channel to wrap
     */
    public ChannelOutputStream(final StreamSinkChannel channel) {
        if (channel == null) {
            throw msg.nullParameter("channel");
        }
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
        if (channel == null) {
            throw msg.nullParameter("channel");
        }
        if (unit == null) {
            throw msg.nullParameter("unit");
        }
        if (timeout < 0L) {
            throw msg.parameterOutOfRange("timeout");
        }
        this.channel = channel;
        final long calcTimeout = unit.toNanos(timeout);
        this.timeout = timeout == 0L ? 0L : calcTimeout < 1L ? 1L : calcTimeout;
    }

    private boolean enter() {
        int old = flags;
        do {
            if (Bits.allAreSet(old, FLAG_ENTERED)) {
                throw msg.concurrentAccess();
            }
        } while (! flagsUpdater.compareAndSet(this, old, old | FLAG_ENTERED));
        return Bits.allAreSet(old, FLAG_CLOSED);
    }

    private void exit(boolean setEof) {
        int oldFlags, newFlags;
        do {
            oldFlags = flags;
            newFlags = oldFlags &~ FLAG_ENTERED;
            if (setEof) {
                newFlags |= FLAG_CLOSED;
            }
        } while (! flagsUpdater.compareAndSet(this, oldFlags, newFlags));
    }

    /**
     * Get the write timeout.
     *
     * @param unit the time unit
     * @return the timeout in the given unit
     */
    public long getWriteTimeout(TimeUnit unit) {
        if (unit == null) {
            throw msg.nullParameter("unit");
        }
        return unit.convert(timeout, TimeUnit.NANOSECONDS);
    }

    /**
     * Set the write timeout.  Does not affect write operations in progress.
     *
     * @param timeout the write timeout, or 0 for none
     * @param unit the time unit
     */
    public void setWriteTimeout(long timeout, TimeUnit unit) {
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
    public void write(final int b) throws IOException {
        boolean closed = enter();
        try {
            if (closed) throw msg.streamClosed();
            final StreamSinkChannel channel = this.channel;
            final ByteBuffer buffer = ByteBuffer.wrap(new byte[] { (byte) b });
            int res = channel.write(buffer);
            if (res == 0) {
                long timeout;
                long start = System.nanoTime();
                long elapsed = 0L;
                do {
                    timeout = this.timeout;
                    if (timeout == 0L) {
                        channel.awaitWritable();
                    } else if (timeout < elapsed) {
                        throw msg.writeTimeout();
                    } else {
                        channel.awaitWritable(timeout - elapsed, TimeUnit.NANOSECONDS);
                    }
                    elapsed = System.nanoTime() - start;
                    res = channel.write(buffer);
                } while (res == 0);
            }
        } finally {
            exit(closed);
        }
    }

    /** {@inheritDoc} */
    public void write(final byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    /** {@inheritDoc} */
    public void write(final byte[] b, final int off, final int len) throws IOException {
        if (len < 1) {
            return;
        }
        boolean closed = enter();
        try {
            if (closed) throw msg.streamClosed();
            final StreamSinkChannel channel = this.channel;
            final ByteBuffer buffer = ByteBuffer.wrap(b, off, len);
            int res;
            while (buffer.hasRemaining()) {
                res = channel.write(buffer);
                if (res == 0) {
                    long timeout;
                    long start = System.nanoTime();
                    long elapsed = 0L;
                    do {
                        timeout = this.timeout;
                        try {
                            if (timeout == 0L) {
                                channel.awaitWritable();
                            } else if (timeout < elapsed) {
                                throw msg.writeTimeout();
                            } else {
                                channel.awaitWritable(timeout - elapsed, TimeUnit.NANOSECONDS);
                            }
                        } catch (InterruptedIOException e) {
                            e.bytesTransferred = buffer.position() - off;
                            throw e;
                        }
                        elapsed = System.nanoTime() - start;
                        res = channel.write(buffer);
                    } while (res == 0);
                }
            }
        } finally {
            exit(closed);
        }
    }

    /** {@inheritDoc} */
    public void flush() throws IOException {
        final boolean closed = enter();
        try {
            final StreamSinkChannel channel = this.channel;
            if (! channel.flush()) {
                long timeout;
                long start = System.nanoTime();
                long elapsed = 0L;
                do {
                    timeout = this.timeout;
                    if (timeout == 0L) {
                        channel.awaitWritable();
                    } else if (timeout < elapsed) {
                        throw msg.writeTimeout();
                    } else {
                        channel.awaitWritable(timeout - elapsed, TimeUnit.NANOSECONDS);
                    }
                    elapsed = System.nanoTime() - start;
                } while (! channel.flush());
            }
        } finally {
            exit(closed);
        }
    }

    /** {@inheritDoc} */
    public void close() throws IOException {
        final boolean closed = enter();
        try {
            if (closed) return;
            final StreamSinkChannel channel = this.channel;
            channel.shutdownWrites();
            if (! channel.flush()) {
                long timeout;
                long start = System.nanoTime();
                long elapsed = 0L;
                do {
                    timeout = this.timeout;
                    if (timeout == 0L) {
                        channel.awaitWritable();
                    } else if (timeout < elapsed) {
                        throw msg.writeTimeout();
                    } else {
                        channel.awaitWritable(timeout - elapsed, TimeUnit.NANOSECONDS);
                    }
                    elapsed = System.nanoTime() - start;
                } while (! channel.flush());
            }
        } finally {
            exit(true);
        }
    }
}
