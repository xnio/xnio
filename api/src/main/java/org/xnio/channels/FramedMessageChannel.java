/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, JBoss Inc., and individual contributors as indicated
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

package org.xnio.channels;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import org.jboss.logging.Logger;
import org.xnio.Buffers;
import org.xnio.Pooled;

/**
 * A connected message channel providing a SASL-style framing layer over a stream channel where each message is prepended
 * by a four-byte length field.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public class FramedMessageChannel extends TranslatingSuspendableChannel<ConnectedMessageChannel, ConnectedStreamChannel> implements ConnectedMessageChannel {

    private static final Logger log = Logger.getLogger("org.xnio.channels.framed");

    private final Pooled<ByteBuffer> receiveBuffer;
    private final Pooled<ByteBuffer> transmitBuffer;
    private boolean readsDone;
    private boolean writesDone;

    /**
     * Construct a new instance.
     *
     * @param channel the channel to wrap
     * @param receiveBuffer the receive buffer (should be direct)
     * @param transmitBuffer the send buffer (should be direct)
     */
    public FramedMessageChannel(final ConnectedStreamChannel channel, final ByteBuffer receiveBuffer, final ByteBuffer transmitBuffer) {
        super(channel);
        this.receiveBuffer = Buffers.pooledWrapper(receiveBuffer);
        this.transmitBuffer = Buffers.pooledWrapper(transmitBuffer);
        log.tracef("Created new framed message channel around %s, receive buffer %s, transmit buffer %s", channel, receiveBuffer, transmitBuffer);
    }

    /**
     * Construct a new instance.
     *
     * @param channel the channel to wrap
     * @param receiveBuffer the receive buffer (should be direct)
     * @param transmitBuffer the send buffer (should be direct)
     */
    public FramedMessageChannel(final ConnectedStreamChannel channel, final Pooled<ByteBuffer> receiveBuffer, final Pooled<ByteBuffer> transmitBuffer) {
        super(channel);
        this.receiveBuffer = receiveBuffer;
        this.transmitBuffer = transmitBuffer;
        log.tracef("Created new framed message channel around %s, receive buffer %s, transmit buffer %s", channel, receiveBuffer, transmitBuffer);
    }

    /** {@inheritDoc} */
    protected Readiness isReadable() {
        if (readsDone) return Readiness.NEVER;
        final ByteBuffer buffer = receiveBuffer.getResource();
        final int size = buffer.position();
        return size >= 4 && size >= buffer.getInt(0) + 4 ? Readiness.ALWAYS : Readiness.OKAY;
    }

    /** {@inheritDoc} */
    protected Object getReadLock() {
        return receiveBuffer;
    }

    /** {@inheritDoc} */
    protected Readiness isWritable() {
        return writesDone ? Readiness.NEVER : Readiness.OKAY;
    }

    /** {@inheritDoc} */
    protected Object getWriteLock() {
        return transmitBuffer;
    }

    /** {@inheritDoc} */
    public int receive(final ByteBuffer buffer) throws IOException {
        synchronized (receiveBuffer) {
            if (readsDone) {
                return -1;
            }
            final ByteBuffer receiveBuffer = this.receiveBuffer.getResource();
            int res;
            final ConnectedStreamChannel channel = (ConnectedStreamChannel) this.channel;
            do {
                res = channel.read(receiveBuffer);
            } while (res > 0);
            if (receiveBuffer.position() < 4) {
                if (res == -1) {
                    receiveBuffer.clear();
                }
                // must be <= 0
                return res;
            }
            receiveBuffer.flip();
            try {
                final int length = receiveBuffer.getInt();
                if (length < 0 || length > receiveBuffer.capacity() - 4) {
                    Buffers.unget(receiveBuffer, 4);
                    throw new IOException("Received an invalid message length of " + length);
                }
                if (receiveBuffer.remaining() < length) {
                    if (res == -1) {
                        receiveBuffer.clear();
                    } else {
                        Buffers.unget(receiveBuffer, 4);
                    }
                    // must be <= 0
                    return res;
                }
                if (buffer.hasRemaining()) {
                    return Buffers.copy(buffer, Buffers.slice(receiveBuffer, length));
                } else {
                    Buffers.skip(receiveBuffer, length);
                    return 0;
                }
            } finally {
                receiveBuffer.compact();
            }
        }
    }

    /** {@inheritDoc} */
    public long receive(final ByteBuffer[] buffers) throws IOException {
        return receive(buffers, 0, buffers.length);
    }

    /** {@inheritDoc} */
    public long receive(final ByteBuffer[] buffers, final int offs, final int len) throws IOException {
        synchronized (receiveBuffer) {
            if (readsDone) {
                return -1;
            }
            final ByteBuffer receiveBuffer = this.receiveBuffer.getResource();
            int res;
            final ConnectedStreamChannel channel = (ConnectedStreamChannel) this.channel;
            do {
                res = channel.read(receiveBuffer);
            } while (res > 0);
            if (receiveBuffer.remaining() < 4) {
                if (res == -1) {
                    receiveBuffer.clear();
                }
                return res;
            }
            final int length = receiveBuffer.getInt();
            if (receiveBuffer.remaining() < length) {
                if (res == -1) {
                    receiveBuffer.clear();
                } else {
                    Buffers.unget(receiveBuffer, 4);
                }
                return res;
            }
            receiveBuffer.flip();
            try {
                if (Buffers.hasRemaining(buffers)) {
                    return Buffers.copy(buffers, offs, len, Buffers.slice(receiveBuffer, length));
                } else {
                    Buffers.skip(receiveBuffer, length);
                    return 0;
                }
            } finally {
                receiveBuffer.compact();
            }
        }
    }

    /** {@inheritDoc} */
    public void shutdownReads() throws IOException {
        synchronized (receiveBuffer) {
            if (readsDone) return;
            final ByteBuffer receiveBuffer = this.receiveBuffer.getResource();
            receiveBuffer.clear();
        }
        super.shutdownReads();
    }


    /** {@inheritDoc} */
    public boolean send(final ByteBuffer buffer) throws IOException {
        synchronized (transmitBuffer) {
            if (writesDone) {
                throw new EOFException("Writes have been shut down");
            }
            final ByteBuffer transmitBuffer = this.transmitBuffer.getResource();
            if (buffer.remaining() > transmitBuffer.capacity() - 4) {
                throw new IOException("Transmitted message is too large");
            }
            final int remaining = buffer.remaining();
            if (transmitBuffer.remaining() < 4 + remaining && ! doFlush()) {
                return false;
            }
            // todo check max transmit size
            transmitBuffer.putInt(remaining);
            transmitBuffer.put(buffer);
            doFlush();
            return true;
        }
    }

    /** {@inheritDoc} */
    public boolean send(final ByteBuffer[] buffers) throws IOException {
        return send(buffers, 0, buffers.length);
    }

    /** {@inheritDoc} */
    public boolean send(final ByteBuffer[] buffers, final int offs, final int len) throws IOException {
        synchronized (transmitBuffer) {
            if (writesDone) {
                throw new EOFException("Writes have been shut down");
            }
            final ByteBuffer transmitBuffer = this.transmitBuffer.getResource();
            if (Buffers.remaining(buffers) > transmitBuffer.capacity() - 4L) {
                throw new IOException("Transmitted message is too large");
            }
            final long remaining = Buffers.remaining(buffers);
            if (transmitBuffer.remaining() < 4 + remaining && ! doFlush()) {
                return false;
            }
            // todo check max transmit size
            transmitBuffer.putInt((int) remaining);
            Buffers.copy(transmitBuffer, buffers, offs, len);
            doFlush();
            return true;
        }
    }

    /** {@inheritDoc} */
    public boolean shutdownWrites() throws IOException {
        synchronized (transmitBuffer) {
            return writesDone || doFlush() && super.shutdownWrites() && (writesDone = true);
        }
    }

    /** {@inheritDoc} */
    public boolean flush() throws IOException {
        synchronized (transmitBuffer) {
            return writesDone || doFlush();
        }
    }

    private boolean doFlush() throws IOException {
        final ByteBuffer buffer = transmitBuffer.getResource();
        buffer.flip();
        try {
            while (buffer.hasRemaining()) {
                final int res = channel.write(buffer);
                if (res == 0) {
                    return false;
                }
            }
            return channel.flush();
        } finally {
            buffer.compact();
        }
    }

    /** {@inheritDoc} */
    public void close() throws IOException {
        synchronized (transmitBuffer) {
            if (! writesDone) {
                writesDone = true;
                transmitBuffer.getResource().clear();
                transmitBuffer.free();
            }
        }
        synchronized (receiveBuffer) {
            if (! readsDone) {
                readsDone = true;
                receiveBuffer.getResource().clear();
                receiveBuffer.free();
            }
        }
        super.close();
    }

    /** {@inheritDoc} */
    public SocketAddress getPeerAddress() {
        return channel.getPeerAddress();
    }

    /** {@inheritDoc} */
    public <A extends SocketAddress> A getPeerAddress(final Class<A> type) {
        return channel.getPeerAddress(type);
    }

    /** {@inheritDoc} */
    public SocketAddress getLocalAddress() {
        return channel.getLocalAddress();
    }

    /** {@inheritDoc} */
    public <A extends SocketAddress> A getLocalAddress(final Class<A> type) {
        return channel.getLocalAddress(type);
    }

    /**
     * Get the underlying channel.
     *
     * @return the underlying channel
     */
    public ConnectedStreamChannel getChannel() {
        return channel;
    }
}
