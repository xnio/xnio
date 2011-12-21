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

package org.xnio.ssl;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;

import org.jboss.logging.Logger;
import org.xnio.Buffers;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Option;
import org.xnio.Options;
import org.xnio.Pool;
import org.xnio.Pooled;
import org.xnio.XnioWorker;
import org.xnio.channels.ConnectedSslStreamChannel;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.channels.TranslatingSuspendableChannel;

/**
 * An SSL stream channel implementation based on {@link SSLEngine}.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
final class JsseConnectedSslStreamChannel extends TranslatingSuspendableChannel<ConnectedSslStreamChannel, ConnectedStreamChannel> implements ConnectedSslStreamChannel {

    private static final Logger log = Logger.getLogger("org.xnio.ssl");
    private static final String FQCN = JsseConnectedSslStreamChannel.class.getName();

    // final fields

    /** The SSL engine. */
    private final SSLEngine engine;
    /** The buffer into which incoming SSL data is written. */
    private final Pooled<ByteBuffer> receiveBuffer;
    /** The buffer from which outbound SSL data is sent. */
    private final Pooled<ByteBuffer> sendBuffer;
    /** The buffer into which inbound clear data is written. */
    private final Pooled<ByteBuffer> readBuffer;

    // state

    private volatile boolean tls;
    /**
     * Indicates this channel has not started to handle handshake yet and, hence, it must enforce handshake kick off
     * by forcing one of the read/write listeners to run. See the calls made to setReadReady and setWriteReady by
     * the constructor.
     */
    private boolean firstHandshake = false;

    /**
     * Callback for notification of a handshake being finished.
     */
    private final ChannelListener.SimpleSetter<ConnectedSslStreamChannel> handshakeSetter = new ChannelListener.SimpleSetter<ConnectedSslStreamChannel>();

    /**
     * Construct a new instance.
     *
     * @param channel the channel being wrapped
     * @param engine the SSL engine to use
     * @param socketBufferPool the socket buffer pool
     * @param applicationBufferPool the application buffer pool
     * @param startTls {@code true} to run in STARTTLS mode, {@code false} to run in regular SSL mode
     */
    JsseConnectedSslStreamChannel(final ConnectedStreamChannel channel, final SSLEngine engine, final Pool<ByteBuffer> socketBufferPool, final Pool<ByteBuffer> applicationBufferPool, final boolean startTls) {
        super(channel);
        if (channel == null) {
            throw new IllegalArgumentException("channel is null");
        }
        if (engine == null) {
            throw new IllegalArgumentException("engine is null");
        }
        tls = ! startTls;
        this.engine = engine;
        final SSLSession session = engine.getSession();
        final int packetBufferSize = session.getPacketBufferSize();
        boolean ok = false;
        receiveBuffer = socketBufferPool.allocate();
        try {
            receiveBuffer.getResource().flip();
            sendBuffer = socketBufferPool.allocate();
            try {
                if (receiveBuffer.getResource().capacity() < packetBufferSize || sendBuffer.getResource().capacity() < packetBufferSize) {
                    throw new IllegalArgumentException("Socket buffer is too small (" + receiveBuffer.getResource().capacity() + "). Expected capacity is " + packetBufferSize);
                }
                final int applicationBufferSize = session.getApplicationBufferSize();
                readBuffer = applicationBufferPool.allocate();
                try {
                    if (readBuffer.getResource().capacity() < applicationBufferSize) {
                        throw new IllegalArgumentException("Application buffer is too small");
                    }
                    ok = true;
                } finally {
                    if (! ok) readBuffer.free();
                }
            } finally {
                if (! ok) sendBuffer.free();
            }
        } finally {
            if (! ok) receiveBuffer.free();
        }
        // enforce handshake kick off by forcing one of the read/write listeners to run
        firstHandshake = true;
        if (tls) {
            setReadReady();
            setWriteReady();
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

    public <T> T getOption(final Option<T> option) throws IOException {
        return option == Options.SECURE ? option.cast(Boolean.valueOf(tls)) : super.getOption(option);
    }

    public boolean supportsOption(final Option<?> option) {
        return option == Options.SECURE || super.supportsOption(option);
    }

    /** {@inheritDoc} */
    @Override
    public ChannelListener.Setter<ConnectedSslStreamChannel> getHandshakeSetter() {
        return handshakeSetter;
    }

    @Override
    protected void handleReadable() {
        boolean read;
        do {
            super.handleReadable();
            // if there is data in readBuffer, call read listener again
            synchronized(getReadLock()) {
                read = !isReadShutDown() && readBuffer.getResource().position() > 0 && readBuffer.getResource().hasRemaining() && isReadResumed();
            }
        } while (read);
    }

    protected void handleHandshakeFinished() {
        final ChannelListener<? super ConnectedSslStreamChannel> listener = handshakeSetter.get();
        if (listener == null) {
            return;
        }
        ChannelListeners.<ConnectedSslStreamChannel>invokeChannelListener(this, listener);
    }

    @Override
    public int write(final ByteBuffer src) throws IOException {
        if (tls) {
            return write(src, false);
        } else {
            return channel.write(src);
        }
    }

    @Override
    public long write(final ByteBuffer[] srcs) throws IOException {
        return write(srcs, 0, srcs.length);
    }

    @Override
    public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        if (!tls) {
            return channel.write(srcs, offset, length);
        }
        if (length < 1) {
            return 0L;
        }
        final ByteBuffer buffer = sendBuffer.getResource();
        long bytesConsumed = 0;
        boolean run;
        do {
            final SSLEngineResult result;
            synchronized (getWriteLock()) {
                run = handleWrapResult(result = wrap(srcs, offset, length, buffer), false);
                bytesConsumed += (long) result.bytesConsumed();
            }
            // handshake will tell us whether to keep the loop
            run = run && (handleHandshake(result, true) || (!writeRequiresRead() && Buffers.hasRemaining(srcs, offset, length)));
        } while (run);
        return bytesConsumed;
    }

    private int write(final ByteBuffer src, boolean isCloseExpected) throws IOException {
        final ByteBuffer buffer = sendBuffer.getResource();
        int bytesConsumed = 0;
        boolean run;
        do {
            final SSLEngineResult result;
            synchronized (getWriteLock()) {
                run = handleWrapResult(result = wrap(src, buffer), isCloseExpected);
                bytesConsumed += result.bytesConsumed();
            }
            // handshake will tell us whether to keep the loop
            run = run && (handleHandshake(result, true) || (!writeRequiresRead() && src.hasRemaining()));
        }
        while (run);
        return bytesConsumed;
    }

    private SSLEngineResult wrap(final ByteBuffer[] srcs, final int offset, final int length, final ByteBuffer dest) throws SSLException {
        log.logf(FQCN, Logger.Level.TRACE, null, "Wrapping %s into %s", srcs, dest);
        return engine.wrap(srcs, offset, length, dest);
    }

    private SSLEngineResult wrap(final ByteBuffer src, final ByteBuffer dest) throws SSLException {
        log.logf(FQCN, Logger.Level.TRACE, null, "Wrapping %s into %s", src, dest);
        return engine.wrap(src, dest);
    }

    private boolean handleWrapResult(SSLEngineResult result, boolean closeExpected) throws IOException {
        assert Thread.holdsLock(getWriteLock());
        assert ! Thread.holdsLock(getReadLock());
        log.logf(FQCN, Logger.Level.TRACE, null, "Wrap result is %s", result);
        switch (result.getStatus()) {
            case BUFFER_UNDERFLOW: {
                assert result.bytesConsumed() == 0;
                assert result.bytesProduced() == 0;
                // should not be possible but just to be safe...
                break;
            }
            case BUFFER_OVERFLOW: {
                assert result.bytesConsumed() == 0;
                assert result.bytesProduced() == 0;
                final ByteBuffer buffer = sendBuffer.getResource();
                if (buffer.position() == 0) {
                    throw new IOException("SSLEngine required a bigger send buffer but our buffer was already big enough");
                } else {
                    // there's some data in there, so send it first
                    buffer.flip();
                    try {
                        while (buffer.hasRemaining()) {
                            final int res = channel.write(buffer);
                            if (res == 0) {
                                return false;
                            }
                        }
                    } finally {
                        buffer.compact();
                    }
                }
                break;
            }
            case CLOSED: {
                if (! closeExpected) {
                    // attempted write after shutdown
                    throw new ClosedChannelException();
                }
                // else treat as OK
                // fall thru!!!
            }
            case OK: {
                if (result.bytesConsumed() == 0) {
                    if (result.bytesProduced() > 0) {
                        if (! doFlush()) {
                            return false;
                        }
                    }
                }
                break;
            }
            default: {
                throw new IllegalStateException("Unexpected wrap result status: " + result.getStatus());
            }
        }
        return true;
    }

    /**
     * Handle handshake process, after a wrap or an unwrap operation.
     * 
     * @param result the wrap/unwrap result
     * @param write  if {@code true}, indicates caller executed a {@code wrap} operation; if {@code false}, indicates
     *               caller executed an {@code unwrap} operation
     * @return       {@code true} to indicate that caller should rerun the previous wrap or unwrap operation, hence
     *               producing a new result; {@code false} to indicate otherwise
     *
     * @throws IOException if an IO error occurs during handshake handling
     */
    private boolean handleHandshake(SSLEngineResult result, boolean write) throws IOException {
        assert ! Thread.holdsLock(getReadLock());
        // reset the flag for "first attempt to handle handshake"
        if (firstHandshake) {
            clearReadReady();
            clearWriteReady();
            firstHandshake = false;
        }
        // if read needs wrap, the only possible reason is that something went wrong with flushing, try to flush now
        if (readRequiresWrite()) {
            synchronized(getWriteLock()) {
                if (doFlush()) {
                    clearReadRequiresWrite();
                }
            }
        }
        // FIXME when called by shutdown Writes, current thread already holds the lock
        //assert ! Thread.holdsLock(getWriteLock());
        boolean newResult = false;
        for (;;) {
            switch (result.getHandshakeStatus()) {
                case FINISHED: {
                    clearWriteRequiresRead();
                    handleHandshakeFinished();
                    // Operation can continue immediately
                    return true;
                }
                case NOT_HANDSHAKING: {
                    // Operation can continue immediately
                    clearWriteRequiresRead();
                    return false;
                }
                case NEED_WRAP: {
                    // clear writeRequiresRead
                    clearWriteRequiresRead();
                    // if write, let caller do the wrap
                    if (write) {
                        return true;
                    }
                    final ByteBuffer buffer = sendBuffer.getResource();
                    // else, trigger a write call
                    boolean flushed = true;
                    // Needs wrap, so we wrap (if possible)...
                    synchronized (getWriteLock()) {
                        if (flushed = doFlush()) {
                            if (!handleWrapResult(result = wrap(Buffers.EMPTY_BYTE_BUFFER, buffer), true)) {
                                setReadRequiresWrite();
                                return false;
                            }
                            newResult = true;
                            continue;
                        }
                    }
                    // if flushed, and given caller is reading, tell it to continue only if read needs wrap is 0
                    if (flushed) {
                        return !readRequiresWrite();
                    } else {
                        // else... oops, there is unflushed data, and handshake status is NEED_WRAP
                        // update readNeedsUnwrapUpdater to 1
                        setReadRequiresWrite();
                        // tell read caller to break read loop
                        return false;
                    }
                }
                case NEED_UNWRAP: {
                    // if read, let caller do the unwrap
                    if (! write) {
                        return newResult;
                    }
                    final ByteBuffer buffer = receiveBuffer.getResource();
                    synchronized (getReadLock()) {
                        final ByteBuffer unwrappedBuffer = readBuffer.getResource();
                        if (handleUnwrapResult(result = unwrap(buffer, unwrappedBuffer)) >= 0) { // FIXME what if the unwrap return buffer overflow???
                            // have we made some progress?
                            if(result.getHandshakeStatus() != HandshakeStatus.NEED_UNWRAP || result.bytesConsumed() > 0) {
                                if (result.bytesProduced() > 0 || buffer.hasRemaining()) {
                                    super.setReadReady();
                                }
                                clearWriteRequiresRead();
                                continue;
                            }
                            if (!readRequiresWrite()) {
                                // no point in proceeding, we're stuck until the user reads anyway
                                setWriteRequiresRead();
                                return false;
                            }
                        }
                    }
                    continue;
                }
                case NEED_TASK: {
                    Runnable task;
                    while ((task = engine.getDelegatedTask()) != null) {
                        try {
                            task.run();
                        } catch (Exception e) {
                            throw new IOException(e);
                        }
                    }
                    // caller should try to wrap/unwrap again
                    return true;
                }
                default:
                    throw new IOException("Unexpected handshake status: " + result.getHandshakeStatus());
            }
        }
    }

    private SSLEngineResult unwrap(final ByteBuffer buffer, final ByteBuffer unwrappedBuffer) throws IOException {
        if (!buffer.hasRemaining()) {
            buffer.compact();
            channel.read(buffer);
            buffer.flip();
        }
        log.logf(FQCN, Logger.Level.TRACE, null, "Unwrapping %s into %s", buffer, unwrappedBuffer);
        return engine.unwrap(buffer, unwrappedBuffer);
    }

    @Override
    public int read(final ByteBuffer dst) throws IOException {
        if (tls) {
            return (int) read(new ByteBuffer[] {dst}, 0, 1);
        } else {
            return channel.read(dst);
        }
    }

    @Override
    public long read(final ByteBuffer[] dsts) throws IOException {
        return read(dsts, 0, dsts.length);
    }

    @Override
    public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        if (! tls) {
            return channel.read(dsts, offset, length);
        }
        if (dsts.length == 0 || length == 0) {
            return 0L;
        }
        final ByteBuffer buffer = receiveBuffer.getResource();
        // TODO what do we do when we are out of space at unwrappedBuffer?
        final ByteBuffer unwrappedBuffer = readBuffer.getResource();
        long total = 0;
        SSLEngineResult result;
        synchronized(getReadLock()) {
            if (unwrappedBuffer.position() > 0 && unwrappedBuffer.hasRemaining()) {
                total += (long) copyUnwrappedData(dsts, offset, length, unwrappedBuffer);
            }
        }
        int res = 0;
        do {
            synchronized (getReadLock()) {
                if (! Buffers.hasRemaining(dsts, offset, length)) {
                    return total;
                }
                res = handleUnwrapResult(result = unwrap(buffer, unwrappedBuffer));
                total += (long) copyUnwrappedData(dsts, offset, length, unwrappedBuffer);
            }
        } while (handleHandshake(result, false) || res > 0);
        if (res == -1) {
            clearReadReady();
            return total == 0L ? -1L : total;
        }
        if (unwrappedBuffer.position() == 0 && !buffer.hasRemaining()) {
            clearReadReady();
        }
        return total;
    }

    private int copyUnwrappedData(final ByteBuffer[] dsts, final int offset, final int length, ByteBuffer unwrappedBuffer) {
        assert Thread.holdsLock(getReadLock());
        unwrappedBuffer.flip();
        try {
            return Buffers.copy(dsts, offset, length, unwrappedBuffer);
        } finally {
            unwrappedBuffer.compact();
        }
    }

    private int handleUnwrapResult(final SSLEngineResult result) throws IOException {
        // FIXME assert ! Thread.holdsLock(getWriteLock());
        assert Thread.holdsLock(getReadLock());
        log.logf(FQCN, Logger.Level.TRACE, null, "Unwrap result is %s", result);
        switch (result.getStatus()) {
            case BUFFER_OVERFLOW: {
                assert result.bytesConsumed() == 0;
                assert result.bytesProduced() == 0;
                // not enough space in destination buffer; caller should flush & retry
                return 0;
            }
            case BUFFER_UNDERFLOW: {
                assert result.bytesConsumed() == 0;
                assert result.bytesProduced() == 0;
                // fill the rest of the buffer, then retry!
                final ByteBuffer buffer = receiveBuffer.getResource();
                synchronized (getReadLock()) {
                    buffer.compact();
                    try {
                        return channel.read(buffer);
                    } finally {
                        buffer.flip();
                    }
                }
            }
            case CLOSED: {
                // if unwrap processed any data, it should return bytes produced instead of -1
                if (result.bytesConsumed() > 0) {
                    return result.bytesConsumed();
                }
                return -1;
            }
            case OK: {
                // continue
                return result.bytesConsumed();
            }
            default: {
                throw new IOException("Unexpected unwrap result status: " + result.getStatus());
            }
        }
    }

    @Override
    public void startHandshake() throws IOException {
        tls = true;
        engine.beginHandshake();
        setReadReady();
        setWriteReady();
    }

    @Override
    public SSLSession getSslSession() {
        return tls ? engine.getSession() : null;
    }

    protected Object getReadLock() {
        return receiveBuffer;
    }

    protected Object getWriteLock() {
        return sendBuffer;
    }

    protected void shutdownReadsAction(final boolean writeComplete) throws IOException {
        if (! tls) {
            channel.shutdownReads();
            return;
        }
        channel.shutdownReads();
        if (!isWriteShutDown()) {
            synchronized (getReadLock()) {
                engine.closeInbound();
            }
            write(Buffers.EMPTY_BYTE_BUFFER, true);
            flush();
        }
        if (writeComplete) {
            closeAction(true, true);
        }
    }

    protected void shutdownWritesAction() throws IOException {
        if (! tls) {
            channel.shutdownWrites();
            return;
        }
        engine.closeOutbound();
    }

    protected void shutdownWritesComplete(final boolean readShutDown) throws IOException {
        try {
            channel.shutdownWrites();
            suspendWrites();
        } finally {
            sendBuffer.free();
        }
        if (readShutDown) {
            closeAction(true, true);
        }
    }

    protected boolean flushAction(final boolean shutDown) throws IOException {
        if (! tls) {
            return channel.flush();
        }
        synchronized (getWriteLock()) {
            return doFlush(shutDown);
        }
    }

    public XnioWorker getWorker() {
        return channel.getWorker();
    }

    private boolean doFlush() throws IOException {
        return doFlush(false);
    }

    private boolean doFlush(final boolean shutdown) throws IOException {
        assert Thread.holdsLock(getWriteLock());
        assert ! Thread.holdsLock(getReadLock());
        if (isWriteComplete()) {
            return true;
        }
        final ByteBuffer buffer = sendBuffer.getResource();
        if (shutdown && (!engine.isOutboundDone() || !engine.isInboundDone())) {
            SSLEngineResult result;
            do {
                if (!handleWrapResult(result = wrap(Buffers.EMPTY_BYTE_BUFFER, buffer), true)) {
                    return false;
                }
            } while (handleHandshake(result, true));
            handleWrapResult(result = wrap(Buffers.EMPTY_BYTE_BUFFER, buffer), true);
            if (result.getHandshakeStatus() != HandshakeStatus.NOT_HANDSHAKING || !engine.isOutboundDone()) {
                return false;
            }
        }
        buffer.flip();
        try {
            while (buffer.hasRemaining()) {
                final int res = channel.write(buffer);
                if (res == 0) {
                    return false;
                }
            }
        } finally {
            buffer.compact();
        }
        return channel.flush();
    }

    protected void closeAction(final boolean readShutDown, final boolean writeShutDown) throws IOException {
        try {
            if (!readShutDown && !engine.isInboundDone()) {
                engine.closeInbound();
            }
            if (! writeShutDown) {
                engine.closeOutbound();
            }
            synchronized(getWriteLock()) {
                if (! doFlush()) {
                    throw new IOException("Unsent data truncated");
                }
            }
            channel.close();
        } finally {
            readBuffer.free();
            receiveBuffer.free();
            sendBuffer.free();
            IoUtils.safeClose(channel);
        }
    }

    @Override
    public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
        return IoUtils.transfer(this, count, throughBuffer, target);
    }

    @Override
    public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        if (tls) {
            return target.transferFrom(this, position, count);
        } else {
            return channel.transferTo(position, count, target);
        }
    }

    @Override
    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        return IoUtils.transfer(source, count, throughBuffer, this);
    }

    @Override
    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        if (tls) {
            return src.transferTo(position, count, this);
        } else {
            return channel.transferFrom(src, position, count);
        }
    }
}
