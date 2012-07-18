/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2011 Red Hat, Inc. and/or its affiliates, and individual
 * contributors as indicated by the @author tags.
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

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

import org.jboss.logging.Logger;
import org.xnio.Buffers;
import org.xnio.IoUtils;
import org.xnio.Pooled;

/**
 * A connected message channel providing a SASL-style framing layer over a stream channel where each message is prepended
 * by a four-byte length field.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@SuppressWarnings("unused")
public class FramedMessageChannel extends TranslatingSuspendableChannel<ConnectedMessageChannel, ConnectedStreamChannel> implements ConnectedMessageChannel {

    private static final Logger log = Logger.getLogger("org.xnio.channels.framed");

    private final Pooled<ByteBuffer> receiveBuffer;
    private final Pooled<ByteBuffer> transmitBuffer;

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
    public int receive(final ByteBuffer buffer) throws IOException {
        synchronized (receiveBuffer) {
            if (isReadShutDown()) {
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
                log.tracef("Did not read a length");
                clearReadReady();
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
                    log.tracef("Did not read enough bytes for a full message");
                    clearReadReady();
                    // must be <= 0
                    return res;
                }
                if (buffer.hasRemaining()) {
                    log.tracef("Copying message from %s into %s", receiveBuffer, buffer);
                    return Buffers.copy(buffer, Buffers.slice(receiveBuffer, length));
                } else {
                    log.tracef("Not copying message from %s into full buffer %s", receiveBuffer, buffer);
                    Buffers.skip(receiveBuffer, length);
                    return 0;
                }
            } finally {
                if (res != -1) {
                    receiveBuffer.compact();
                    if (receiveBuffer.position() >= 4 && receiveBuffer.position() >= 4 + receiveBuffer.getInt(0)) {
                        // there's another packet ready to go
                        setReadReady();
                    }
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
        synchronized (receiveBuffer) {
            if (isReadShutDown()) {
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
                log.tracef("Did not read a length");
                clearReadReady();
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
                    log.tracef("Did not read enough bytes for a full message");
                    clearReadReady();
                    // must be <= 0
                    return res;
                }
                if (Buffers.hasRemaining(buffers)) {
                    log.tracef("Copying message from %s into multiple buffers", receiveBuffer);
                    return Buffers.copy(buffers, offs, len, Buffers.slice(receiveBuffer, length));
                } else {
                    log.tracef("Not copying message from %s into multiple full buffers", receiveBuffer);
                    Buffers.skip(receiveBuffer, length);
                    return 0;
                }
            } finally {
                if (res != -1) {
                    receiveBuffer.compact();
                    if (receiveBuffer.position() >= 4 && receiveBuffer.position() >= 4 + receiveBuffer.getInt(0)) {
                        // there's another packet ready to go
                        setReadReady();
                    }
                }
            }
        }
    }

    protected void shutdownReadsAction(final boolean writeComplete) throws IOException {
        synchronized (receiveBuffer) {
            log.tracef("Shutting down reads on %s", this);
            try {
                receiveBuffer.getResource().clear();
            } catch (Throwable t) {
            }
            try {
                receiveBuffer.free();
            } catch (Throwable t) {
            }
        }
        channel.shutdownReads();
    }

    /** {@inheritDoc} */
    public boolean send(final ByteBuffer buffer) throws IOException {
        synchronized (transmitBuffer) {
            if (isWriteShutDown()) {
                throw new EOFException("Writes have been shut down");
            }
            if (!buffer.hasRemaining()) {
                return true;
            }
            final ByteBuffer transmitBuffer = this.transmitBuffer.getResource();
            final int remaining = buffer.remaining();
            if (remaining > transmitBuffer.capacity() - 4) {
                throw new IOException("Transmitted message is too large");
            }
            log.tracef("Accepting %s into %s", buffer, transmitBuffer);
            if (transmitBuffer.remaining() < 4 + remaining && ! doFlushBuffer()) {
                log.tracef("Insufficient room to accept %s into %s", buffer, transmitBuffer);
                return false;
            }
            transmitBuffer.putInt(remaining);
            transmitBuffer.put(buffer);
            log.tracef("Accepted a message into %s", transmitBuffer);
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
            if (isWriteShutDown()) {
                throw new EOFException("Writes have been shut down");
            }
            if (!Buffers.hasRemaining(buffers, offs, len)) {
                return true;
            }
            final ByteBuffer transmitBuffer = this.transmitBuffer.getResource();
            final long remaining = Buffers.remaining(buffers, offs, len);
            if (remaining > transmitBuffer.capacity() - 4L) {
                throw new IOException("Transmitted message is too large");
            }
            log.tracef("Accepting multiple buffers into %s", transmitBuffer);
            if (transmitBuffer.remaining() < 4 + remaining && ! doFlushBuffer()) {
                log.tracef("Insufficient room to accept multiple buffers into %s", transmitBuffer);
                return false;
            }
            transmitBuffer.putInt((int) remaining);
            Buffers.copy(transmitBuffer, buffers, offs, len);
            log.tracef("Accepted a message into %s", transmitBuffer);
            doFlush();
            return true;
        }
    }

    protected boolean flushAction(final boolean shutDown) throws IOException {
        synchronized (transmitBuffer) {
            return (shutDown || doFlushBuffer()) && channel.flush();
        }
    }

    protected void shutdownWritesComplete(final boolean readShutDown) throws IOException {
        synchronized (transmitBuffer) {
            log.tracef("Finished shutting down writes on %s", this);
            try {
                transmitBuffer.free();
            } catch (Throwable t) {}
        }
        channel.shutdownWrites();
    }

    private boolean doFlushBuffer() throws IOException {
        assert Thread.holdsLock(transmitBuffer);
        final ByteBuffer buffer = transmitBuffer.getResource();
        buffer.flip();
        try {
            while (buffer.hasRemaining()) {
                final int res = channel.write(buffer);
                if (res == 0) {
                    log.tracef("Did not fully flush %s", this);
                    return false;
                }
            }
            log.tracef("Fully flushed %s", this);
            return true;
        } finally {
            buffer.compact();
        }
    }

    private boolean doFlush() throws IOException {
        return doFlushBuffer() && channel.flush();
    }

    protected void closeAction(final boolean readShutDown, final boolean writeShutDown) throws IOException {
        boolean error = false;
        if (! writeShutDown) {
            synchronized (transmitBuffer) {
                try {
                    if (! doFlush()) error = true;
                } catch (Throwable t) {
                    error = true;
                }
                try {
                    transmitBuffer.free();
                } catch (Throwable t) {
                }
            }
        }
        if (! readShutDown) {
            synchronized (receiveBuffer) {
                try {
                    receiveBuffer.free();
                } catch (Throwable t) {
                }
            }
        }
        try {
            if (error) throw new IOException("Unflushed data truncated");
            channel.close();
        } finally {
            IoUtils.safeClose(channel);
        }
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
