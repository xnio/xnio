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

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import org.xnio.Buffers;

/**
 * A connected message channel providing a SASL-style framing layer over a stream channel where each message is prepended
 * by a four-byte length field.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class FramedMessageChannel extends TranslatingSuspendableChannel<ConnectedMessageChannel, ConnectedStreamChannel> implements ConnectedMessageChannel {

    private final ByteBuffer receiveBuffer;
    private final ByteBuffer transmitBuffer;

    /**
     * Construct a new instance.
     *
     * @param channel the channel to wrap
     * @param receiveBuffer the receive buffer (should be direct)
     * @param transmitBuffer the send buffer (should be direct)
     */
    public FramedMessageChannel(final ConnectedStreamChannel channel, final ByteBuffer receiveBuffer, final ByteBuffer transmitBuffer) {
        super(channel);
        this.receiveBuffer = receiveBuffer;
        this.transmitBuffer = transmitBuffer;
    }

    /** {@inheritDoc} */
    protected boolean isReadable() {
        final ByteBuffer buffer = receiveBuffer;
        final int remaining = buffer.remaining();
        return remaining >= 4 && remaining >= buffer.getInt(0);
    }

    /** {@inheritDoc} */
    protected Object getReadLock() {
        return receiveBuffer;
    }

    /** {@inheritDoc} */
    protected boolean isWritable() {
        return receiveBuffer.position() == 0;
    }

    /** {@inheritDoc} */
    protected Object getWriteLock() {
        return transmitBuffer;
    }

    /** {@inheritDoc} */
    public int receive(final ByteBuffer buffer) throws IOException {
        final ByteBuffer receiveBuffer = this.receiveBuffer;
        synchronized (receiveBuffer) {
            int res;
            final ConnectedStreamChannel channel = (ConnectedStreamChannel) this.channel;
            while ((res = channel.read(receiveBuffer)) > 0) {}
            if (receiveBuffer.remaining() < 4) {
                return res;
            }
            final int length = receiveBuffer.getInt();
            if (receiveBuffer.remaining() < length) {
                Buffers.unget(receiveBuffer, 4);
                return res;
            }
            try {
                return Buffers.copy(buffer, Buffers.slice(receiveBuffer, length));
            } finally {
                if (receiveBuffer.remaining() == 0) {
                    receiveBuffer.clear();
                }
            }
        }
    }

    /** {@inheritDoc} */
    public long receive(final ByteBuffer[] buffers) throws IOException {
        return receive(buffers, 0, buffers.length);
    }

    /** {@inheritDoc} */
    public long receive(final ByteBuffer[] buffers, final int offs, final int len) throws IOException {
        final ByteBuffer receiveBuffer = this.receiveBuffer;
        synchronized (receiveBuffer) {
            int res;
            while ((res = channel.read(receiveBuffer)) > 0) {}
            if (receiveBuffer.remaining() < 4) {
                return res;
            }
            final int length = receiveBuffer.getInt();
            if (receiveBuffer.remaining() < length) {
                Buffers.unget(receiveBuffer, 4);
                return res;
            }
            try {
                return Buffers.copy(buffers, offs, len, Buffers.slice(receiveBuffer, length));
            } finally {
                if (receiveBuffer.remaining() == 0) {
                    receiveBuffer.clear();
                }
            }
        }
    }

    /** {@inheritDoc} */
    public void shutdownReads() throws IOException {
        final ByteBuffer receiveBuffer = this.receiveBuffer;
        synchronized (receiveBuffer) {
            receiveBuffer.clear();
        }
        super.shutdownReads();
    }


    /** {@inheritDoc} */
    public boolean send(final ByteBuffer buffer) throws IOException {
        final ByteBuffer transmitBuffer = this.transmitBuffer;
        synchronized (transmitBuffer) {
            final int remaining = buffer.remaining();
            if (transmitBuffer.remaining() < 4 + remaining) {
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
        final ByteBuffer transmitBuffer = this.transmitBuffer;
        synchronized (transmitBuffer) {
            final long remaining = Buffers.remaining(buffers);
            if (transmitBuffer.remaining() < 4 + remaining) {
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
            return doFlush() && super.shutdownWrites();
        }
    }

    /** {@inheritDoc} */
    public boolean flush() throws IOException {
        synchronized (transmitBuffer) {
            return ! doFlush();
        }
    }

    private boolean doFlush() throws IOException {
        final ByteBuffer buffer = transmitBuffer;
        while (buffer.hasRemaining()) {
            final int res = channel.write(buffer);
            if (res == 0) {
                return true;
            }
        }
        buffer.clear();
        return false;
    }

    /** {@inheritDoc} */
    public void close() throws IOException {
        synchronized (transmitBuffer) {
            transmitBuffer.clear();
        }
        synchronized (receiveBuffer) {
            receiveBuffer.clear();
        }
        super.close();
    }

    public SocketAddress getPeerAddress() {
        return channel.getPeerAddress();
    }

    public <A extends SocketAddress> A getPeerAddress(final Class<A> type) {
        return channel.getPeerAddress(type);
    }

    public SocketAddress getLocalAddress() {
        return channel.getLocalAddress();
    }

    public <A extends SocketAddress> A getLocalAddress(final Class<A> type) {
        return channel.getLocalAddress(type);
    }
}
