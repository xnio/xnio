/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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
import java.nio.channels.FileChannel;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import org.xnio.Pool;
import org.xnio.Pooled;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLSession;

/**
 * An SSL stream channel implementation based on {@link SSLEngine}.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class StandardConnectedSslStreamChannel extends TranslatingSuspendableChannel<ConnectedSslStreamChannel, ConnectedStreamChannel> implements ConnectedSslStreamChannel {

    // final fields

    /** The SSL engine. */
    private final SSLEngine engine;
    /** The close propagation flag. */
    private final boolean propagateClose;
    /** The buffer into which incoming SSL data is written. */
    private final Pooled<ByteBuffer> receiveBuffer;
    /** The buffer from which outbound SSL data is sent. */
    private final Pooled<ByteBuffer> sendBuffer;
    /** The buffer into which inbound clear data is written. */
    private final Pooled<ByteBuffer> readBuffer;

    // state

    /** Writes need an unwrap (read) to proceed.  Set from write lock, clear from read lock. */
    @SuppressWarnings("unused")
    private volatile int writeNeedsUnwrap;
    /** Reads need a wrap (write) to proceed.  Set from read lock, clear from write lock. */
    @SuppressWarnings("unused")
    private volatile int readNeedsWrap;

    private static final AtomicIntegerFieldUpdater<StandardConnectedSslStreamChannel> writeNeedsUnwrapUpdater = AtomicIntegerFieldUpdater.newUpdater(StandardConnectedSslStreamChannel.class, "writeNeedsUnwrap");
    private static final AtomicIntegerFieldUpdater<StandardConnectedSslStreamChannel> readNeedsUnwrapUpdater = AtomicIntegerFieldUpdater.newUpdater(StandardConnectedSslStreamChannel.class, "readNeedsUnwrap");

    /**
     * Construct a new instance.
     *
     * @param channel the channel being wrapped
     * @param engine the SSL engine to use
     * @param propagateClose {@code true} to propagate read/write shutdown and channel close to the underlying channel, {@code false} otherwise
     * @param socketBufferPool the socket buffer pool
     * @param applicationBufferPool the application buffer pool
     */
    StandardConnectedSslStreamChannel(final ConnectedStreamChannel channel, final SSLEngine engine, final boolean propagateClose, final Pool<ByteBuffer> socketBufferPool, final Pool<ByteBuffer> applicationBufferPool) {
        super(channel);
        if (channel == null) {
            throw new IllegalArgumentException("channel is null");
        }
        if (engine == null) {
            throw new IllegalArgumentException("engine is null");
        }
        this.engine = engine;
        this.propagateClose = propagateClose;
        final SSLSession session = engine.getSession();
        final int packetBufferSize = session.getPacketBufferSize();
        receiveBuffer = socketBufferPool.allocate();
        sendBuffer = socketBufferPool.allocate();
        if (receiveBuffer.getResource().capacity() < packetBufferSize || sendBuffer.getResource().capacity() < packetBufferSize) {
            throw new IllegalArgumentException("Socket buffer is too small");
        }
        final int applicationBufferSize = session.getApplicationBufferSize();
        readBuffer = applicationBufferPool.allocate();
        if (readBuffer.getResource().capacity() < applicationBufferSize) {
            throw new IllegalArgumentException("Application buffer is too small");
        }
    }

    /** {@inheritDoc} */
    public <A extends SocketAddress> A getLocalAddress(final Class<A> type) {
        return getChannel().getLocalAddress(type);
    }

    /** {@inheritDoc} */
    public SocketAddress getLocalAddress() {
        return getChannel().getLocalAddress();
    }

    /** {@inheritDoc} */
    public <A extends SocketAddress> A getPeerAddress(final Class<A> type) {
        return getChannel().getPeerAddress(type);
    }

    /** {@inheritDoc} */
    public SocketAddress getPeerAddress() {
        return getChannel().getPeerAddress();
    }

    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        return src.transferTo(position, count, this);
    }

    public int write(final ByteBuffer src) throws IOException {
        if (!src.hasRemaining()) {
            return 0;
        }
        synchronized (getWriteLock()) {
            final ByteBuffer buffer = sendBuffer.getResource();
            final SSLEngineResult result = engine.wrap(src, buffer);
            if (result.getHandshakeStatus() == )
                return result.bytesConsumed();
        }
    }

    public long write(final ByteBuffer[] srcs) throws IOException {
        return write(srcs, 0, srcs.length);
    }

    public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        return 0;
    }

    public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        return target.transferFrom(this, position, count);
    }

    public int read(final ByteBuffer dst) throws IOException {
        return 0;
    }

    public long read(final ByteBuffer[] dsts) throws IOException {
        return read(dsts, 0, dsts.length);
    }

    public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        return 0;
    }

    public void startHandshake() throws IOException {
        engine.beginHandshake();
    }

    public SSLSession getSslSession() {
        return engine.getSession();
    }

    protected Readiness isReadable() {
        return Readiness.OKAY;
    }

    protected Object getReadLock() {
        return receiveBuffer;
    }

    protected Readiness isWritable() {
        return Readiness.OKAY;
    }

    protected Object getWriteLock() {
        return sendBuffer;
    }
}
