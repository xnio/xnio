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
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLSession;

import org.xnio.Buffers;
import org.xnio.Pool;
import org.xnio.Pooled;

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
    private static final AtomicIntegerFieldUpdater<StandardConnectedSslStreamChannel> readNeedsWrapUpdater = AtomicIntegerFieldUpdater.newUpdater(StandardConnectedSslStreamChannel.class, "readNeedsWrap");

    /**
     * Construct a new instance.
     *
     * @param channel the channel being wrapped
     * @param engine the SSL engine to use
     * @param propagateClose {@code true} to propagate read/write shutdown and channel close to the underlying channel, {@code false} otherwise
     * @param socketBufferPool the socket buffer pool
     * @param applicationBufferPool the application buffer pool
     */
    public StandardConnectedSslStreamChannel(final ConnectedStreamChannel channel, final SSLEngine engine, final boolean propagateClose, final Pool<ByteBuffer> socketBufferPool, final Pool<ByteBuffer> applicationBufferPool) {
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
            throw new IllegalArgumentException("Socket buffer is too small. Expected capacity is " + packetBufferSize);
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
        final ByteBuffer buffer = sendBuffer.getResource();
        int bytesConsumed = 0;
        boolean wrap = true;;
        while(wrap) {
            final SSLEngineResult result;
            synchronized (getWriteLock()) {
                result = engine.wrap(src, buffer);
                bytesConsumed += result.bytesConsumed();
                switch(result.getStatus()) {
                    case BUFFER_OVERFLOW: {
                        if (buffer.position() == 0) {
                            throw new IOException("SSLEngine required a bigger send buffer but our buffer was already big enough");
                        } else {
                            // there's some data in there, so send it first
                            buffer.flip();
                            try {
                                while(buffer.hasRemaining()) {
                                    final int res = channel.write(buffer);
                                    if (res == 0) {
                                        return 0;
                                    }
                                }
                            } finally {
                                buffer.compact();
                            }
                        }
                        continue;
                    }
                    case CLOSED: {
                        // attempted write after shutdown
                        throw new ClosedChannelException();
                    }
                    case OK: {
                        if (result.bytesConsumed() == 0) {
                            wrap = false;
                        }
                        break;
                    }
                    default: {
                            throw new IllegalStateException("Unexpected wrap result status: " + result.getStatus()); 
                    }
                }
            }
            // handshake will tell us whether to keep the loop
            wrap = wrap || handleHandshake(result, true);
        }
        return bytesConsumed;
    }

    private boolean handleHandshake(SSLEngineResult result, boolean write) throws IOException {
        switch (result.getHandshakeStatus()) {
            case FINISHED: {
                readNeedsWrapUpdater.getAndSet(this, 0);
                writeNeedsUnwrapUpdater.getAndSet(this, 0);
                // fall thru!!
            }
            case NOT_HANDSHAKING:
                // cool, no handshake, we can tell caller it can continue
                return true;
            case NEED_WRAP: {
                // clear writeNeedsUnwrap
                writeNeedsUnwrapUpdater.getAndSet(this, 0);
                boolean flushed = true;
                if (result.bytesProduced() == 0 || (flushed = flush())) {
                    write(Buffers.EMPTY_BYTE_BUFFER);
                }
                // if flushed and write, tell caller to continue wrapping
                // if caller is reading, tell it to continue only if read needs unwrap is 0
                if (flushed) {
                    return write || readNeedsWrapUpdater.get(this) == 0;
                } else {
                    // else... oops, there is unflushed data, and handshake status is NEED_WRAP
                    synchronized(getReadLock()) {
                        // update readNeedsUnwrapUpdater to 1
                        readNeedsWrapUpdater.getAndSet(this, 1);
                    }
                    // tell caller to continue wrapping if it is writing; tell it to break read loop if it is reading
                    return !write;
                }
            }
            case NEED_UNWRAP: {
                // clear readNeedsWrap
                readNeedsWrapUpdater.getAndSet(this, 0);
                // any write operation cannot proceed if need_unwrap
                writeNeedsUnwrapUpdater.getAndSet(this, 1);
                // tell read caller to continue, and tell write caller to quit
                return !write;
            }
            case NEED_TASK: {
                Runnable task = engine.getDelegatedTask();
                while (task != null) {
                    task.run();
                    task = engine.getDelegatedTask();
                }
                // caller should try to wrap/unwrap again
                return true;
            }
            default:
                throw new IOException("Unexpected handshake status: " + result.getHandshakeStatus());
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
        return (int) read(new ByteBuffer[] {dst}, 0, 1);
    }

    public long read(final ByteBuffer[] dsts) throws IOException {
        return read(dsts, 0, dsts.length);
    }

    public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        if (dsts.length == 0 || length == 0) {
            return 0L;
        }

        final ByteBuffer buffer = receiveBuffer.getResource();
        final ByteBuffer unwrappedBuffer = readBuffer.getResource();
        int bytesProduced = 0;
        boolean unwrap = true;
        while (unwrap) {
            final SSLEngineResult result;
            synchronized (getReadLock()) {
                result = engine.unwrap(buffer, unwrappedBuffer);
                bytesProduced += result.bytesProduced();
                switch (result.getStatus()) {
                    case BUFFER_OVERFLOW: {
                        if (unwrappedBuffer.position() > 0) {
                            return copyUnwrappedData(dsts, offset, length, unwrappedBuffer, bytesProduced);
                        }
                        // read buffer too small!  dynamically resize & repeat...
                        final int appBufSize = engine.getSession().getApplicationBufferSize();
                        if (unwrappedBuffer.capacity() >= appBufSize) {
                            // the say the buf is too small, yet it's already at least their required size...?
                            throw new IOException("Unexpected/inexplicable buffer overflow from the SSL engine");
                        }
                        // try again with the bigger buffer...
                        continue;
                    }
                    case BUFFER_UNDERFLOW: {
                        if (buffer.position() == 0 && !buffer.hasRemaining()) {
                            // receive buffer is full but it's still not big enough, so grow it
                            final int pktBufSize = engine.getSession().getPacketBufferSize();
                            if (buffer.capacity() >= pktBufSize) {
                                // it's already the required size...
                                throw new IOException("Unexpected/inexplicable buffer underflow from the SSL engine");
                            }
                            continue;
                        }
                        // fill the rest of the buffer, then retry!
                        final int rres;
                        buffer.compact();
                        try {
                            rres = channel.read(buffer);
                        } finally {
                            buffer.flip();
                        }
                        if (rres == -1) {
                            // TCP stream EOF... give the ssl engine the bad news
                            engine.closeInbound();
                            write(Buffers.EMPTY_BYTE_BUFFER);
                            flush();
                            // continue
                        } else if (rres == 0) {
                            return copyUnwrappedData(dsts, offset, length, unwrappedBuffer, bytesProduced);
                        }
                        System.out.println("LOADED BYTES INTO RECEIVE BUFFER" + rres);
                        // else some data was received, so continue
                        continue;
                    }
                    case CLOSED: {
                        return bytesProduced == 0? -1: bytesProduced;
                    }
                    case OK:
                        break;
                    default: 
                        throw new IOException("Unexpected unwrap result status: " + result.getStatus());
                }
            }
            unwrap = handleHandshake(result, false);
        }
        synchronized (getReadLock()) {
            return copyUnwrappedData(dsts, offset, length, unwrappedBuffer, bytesProduced);
        }
    }

    public int copyUnwrappedData(final ByteBuffer[] dsts, final int offset, final int length, ByteBuffer unwrappedBuffer, int bytesProduced) {
        if (bytesProduced == 0) { // TODO move this check to Buffers.copy
            return 0;
        }
        unwrappedBuffer.flip();
        try {
            return Buffers.copy(dsts, offset, length, unwrappedBuffer);
        } finally {
            unwrappedBuffer.compact();
        }
    }

    public void startHandshake() throws IOException {
        engine.beginHandshake();
    }

    public SSLSession getSslSession() {
        return engine.getSession();
    }

    protected Readiness isReadable() {
        synchronized(getReadLock()) {
            return readNeedsWrapUpdater.get(this) > 0? Readiness.NEVER: Readiness.OKAY;
        }
    }

    protected Object getReadLock() {
        return receiveBuffer;
    }

    protected Readiness isWritable() {
        synchronized(getWriteLock()) {
            return writeNeedsUnwrapUpdater.get(this) > 0? Readiness.NEVER: Readiness.OKAY;
        }
    }

    protected Object getWriteLock() {
        return sendBuffer;
    }

    @Override
    public void shutdownReads() throws IOException {
        if (propagateClose) {
            super.shutdownReads();
        }
        synchronized(getReadLock()) {
            engine.closeInbound();
        }
        write(Buffers.EMPTY_BYTE_BUFFER);
        flush();
    }

    @Override
    public boolean shutdownWrites() throws IOException {
        synchronized(getWriteLock()) {
            if (flush()) {
                engine.closeOutbound();
                write(Buffers.EMPTY_BYTE_BUFFER);
                return flush() && engine.isOutboundDone() && (!propagateClose || super.shutdownWrites());
            } else {
                return false;
            }
        }
    }
}
