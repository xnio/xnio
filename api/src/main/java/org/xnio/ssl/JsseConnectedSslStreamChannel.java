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
import org.xnio.Option;
import org.xnio.Options;
import org.xnio.Pool;
import org.xnio.Pooled;
import org.xnio.XnioWorker;
import org.xnio.channels.Channels;
import org.xnio.channels.ConnectedSslStreamChannel;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.channels.TranslatingSuspendableChannel;

/**
 * An SSL stream channel implementation based on {@link SSLEngine}.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
final class JsseConnectedSslStreamChannel extends TranslatingSuspendableChannel<ConnectedSslStreamChannel, ConnectedStreamChannel> implements ConnectedSslStreamChannel {

    private static final Logger log = Logger.getLogger("org.xnio.ssl");

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

    private volatile boolean tls;
    /** Writes need an unwrap (read) to proceed.  Set from write thread, clear from read thread. */
    private volatile boolean writeNeedsUnwrap;
    /** Reads need a wrap (write) to proceed.  Set from read thread, clear from write thread. */
    private volatile boolean readNeedsWrap;
    /** Indicates a write has been suspended because of writeNeedsUnwrap and a previously disabled read has been forced to resume */
    private volatile boolean forcedResumeReads;
    /** Indicates a read has been suspended because of readNeedsWrap and a previously disabled write has been forced to resume */
    private volatile boolean forcedResumeWrites;

    /**
     * Construct a new instance.
     *
     * @param channel the channel being wrapped
     * @param engine the SSL engine to use
     * @param propagateClose {@code true} to propagate read/write shutdown and channel close to the underlying channel, {@code false} otherwise
     * @param socketBufferPool the socket buffer pool
     * @param applicationBufferPool the application buffer pool
     * @param startTls {@code true} to run in STARTTLS mode, {@code false} to run in regular SSL mode
     */
    JsseConnectedSslStreamChannel(final ConnectedStreamChannel channel, final SSLEngine engine, final boolean propagateClose, final Pool<ByteBuffer> socketBufferPool, final Pool<ByteBuffer> applicationBufferPool, final boolean startTls) {
        super(channel);
        if (channel == null) {
            throw new IllegalArgumentException("channel is null");
        }
        if (engine == null) {
            throw new IllegalArgumentException("engine is null");
        }
        tls = ! startTls;
        this.engine = engine;
        this.propagateClose = propagateClose;
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

    public void resumeReads() {
        if (forcedResumeReads) {
            revertForcedResumeReads();
        }
        super.resumeReads();
    }

    public void resumeWrites() {
        if (forcedResumeWrites) {
            revertForcedResumeWrites();
        }
        super.resumeWrites();
    }

    @Override
    protected void handleReadable(final ConnectedStreamChannel channel) {
        if (forcedResumeReads) {
            revertForcedResumeReads();
        }
        boolean read;
        do {
            super.handleReadable(channel);
            // if there is data in readBuffer, call read listener again
            synchronized(getReadLock()) {
                read = readBuffer.getResource().position() > 0 && readBuffer.getResource().hasRemaining() && isReadResumed();
            }
        } while (read);
    }

    @Override
    protected void handleWritable(final ConnectedStreamChannel channel) {
        if (forcedResumeWrites) {
            revertForcedResumeWrites();
        }
        super.handleWritable(channel);
    }

    @Override
    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        if (tls) {
            return src.transferTo(position, count, Channels.wrapByteChannel(this));
        } else {
            return channel.transferFrom(src, position, count);
        }
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
            run = run && (handleHandshake(result, true) || !writeNeedsUnwrap);
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
            run = run && (handleHandshake(result, true) || (!writeNeedsUnwrap && src.hasRemaining()));
        }
        while (run);
        return bytesConsumed;
    }

    private SSLEngineResult wrap(final ByteBuffer[] srcs, final int offset, final int length, final ByteBuffer dest) throws SSLException {
        log.tracef("Wrapping %s into %s", srcs, dest);
        return engine.wrap(srcs, offset, length, dest);
    }

    private SSLEngineResult wrap(final ByteBuffer src, final ByteBuffer dest) throws SSLException {
        log.tracef("Wrapping %s into %s", src, dest);
        return engine.wrap(src, dest);
    }

    private boolean handleWrapResult(SSLEngineResult result, boolean closeExpected) throws IOException {
        assert Thread.holdsLock(getWriteLock());
        assert ! Thread.holdsLock(getReadLock());
        log.tracef(new Throwable(), "Wrap result is %s", result);
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

    private final void markWriteNeedsUnwrap() {
        if (!writeNeedsUnwrap) {
            writeNeedsUnwrap = true;
            forceResumeReads();
        }
    }

    private final void clearWriteNeedsUnwrap() {
        if (writeNeedsUnwrap) {
            writeNeedsUnwrap = false;
            resumeWritesIfRequested();
            if (forcedResumeReads) {
                revertForcedResumeReads();
            }
        }
    }

    private final void forceResumeReads() {
        // if is not read resumed, resume it
        synchronized (getReadLock()) {
            if (!isReadResumed()) {
                resumeReads();
                forcedResumeReads = true;
            }
        }
        if (forcedResumeReads) {
            suspendWrites();
        }
    }

    private final void revertForcedResumeReads() {
        // if is not read resumed, resume it
        forcedResumeReads = false;
        suspendReads();
        if (!isWriteResumed()) {
            resumeWrites();
        }
    }

    private final void clearReadNeedsWrap() {
        if (readNeedsWrap) {
            readNeedsWrap = false;
            resumeReadsIfRequested();
            if (forcedResumeWrites) {
                revertForcedResumeWrites();
            }
        }
    }

    private final void markReadNeedsWrap() {
        if (!readNeedsWrap) {
            readNeedsWrap = true;
            forceResumeWrites();
        }
    }

    private final void forceResumeWrites() {
        // if is not write resumed, resume it
        synchronized (getWriteLock()) {
            if (!isWriteResumed()) {
                resumeWrites();
                forcedResumeWrites = true;
            }
        }
        if (forcedResumeWrites) {
            suspendReads();
        }
    }

    private final void revertForcedResumeWrites() {
        // if is not write resumed, resume it
        forcedResumeWrites = false;
        suspendWrites();
        if (!isReadResumed()) {
            resumeReads();
        }
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
        // FIXME when called by shutdown Writes, current thread already holds the lock
        //assert ! Thread.holdsLock(getWriteLock());
        boolean newResult = false;
        for (;;) {
            switch (result.getHandshakeStatus()) {
                case FINISHED: {
                    clearReadNeedsWrap();
                    clearWriteNeedsUnwrap();
                    // Operation can continue immediately
                    return true;
                }
                case NOT_HANDSHAKING: {
                    // Operation can continue immediately
                    clearReadNeedsWrap();
                    clearWriteNeedsUnwrap();
                    return false;
                }
                case NEED_WRAP: {
                    // clear writeNeedsUnwrap
                    clearWriteNeedsUnwrap();
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
                                markReadNeedsWrap();
                                resumeWritesIfRequestedAndSuspendReads();
                                return false;
                            }
                            newResult = true;
                            continue;
                        }
                    }
                    // if flushed, and given caller is reading, tell it to continue only if read needs wrap is 0
                    if (flushed) {
                        return !readNeedsWrap;
                    } else {
                        // else... oops, there is unflushed data, and handshake status is NEED_WRAP
                        // update readNeedsUnwrapUpdater to 1
                        markReadNeedsWrap();
                        // tell read caller to break read loop
                        return false;
                    }
                }
                case NEED_UNWRAP: {
                    // clear readNeedsWrap
                    clearReadNeedsWrap();
                    markWriteNeedsUnwrap();
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
                                clearWriteNeedsUnwrap();
                                continue;
                            }
                            // no point in proceeding, we're stuck until the user reads anyway
                            markWriteNeedsUnwrap();
                            resumeReadsIfRequestedAndSuspendWrites();
                            return false;
                        }
                    }
                    continue;
                }
                case NEED_TASK: {
                    Runnable task;
                    while ((task = engine.getDelegatedTask()) != null) {
                        task.run();
                    }
                    // caller should try to wrap/unwrap again
                    return true;
                }
                default:
                    throw new IOException("Unexpected handshake status: " + result.getHandshakeStatus());
            }
        }
    }

    private SSLEngineResult unwrap(final ByteBuffer buffer, final ByteBuffer unwrappedBuffer) throws IOException, SSLException {
        if (!buffer.hasRemaining()) {
            buffer.compact();
            channel.read(buffer);
            buffer.flip();
        }
        log.tracef("Unwrapping %s into %s", buffer, unwrappedBuffer);
        return engine.unwrap(buffer, unwrappedBuffer);
    }

    @Override
    public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        if (tls) {
            return target.transferFrom(Channels.wrapByteChannel(this), position, count);
        } else {
            return channel.transferTo(position, count, target);
        }
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
            return total == 0L ? -1L : total;
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
        log.tracef(new Throwable(), "Unwrap result is %s", result);
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
    }

    @Override
    public SSLSession getSslSession() {
        return tls ? engine.getSession() : null;
    }

    @Override
    protected Readiness isReadable() {
        synchronized(getReadLock()) {
            return readNeedsWrap? Readiness.NEVER: Readiness.OKAY;
        }
    }

    @Override
    protected Object getReadLock() {
        return receiveBuffer;
    }

    @Override
    protected Readiness isWritable() {
        synchronized(getWriteLock()) {
            return writeNeedsUnwrap? Readiness.NEVER: Readiness.OKAY;
        }
    }

    @Override
    protected Object getWriteLock() {
        return sendBuffer;
    }

    @Override
    public void shutdownReads() throws IOException {
        if (! tls) {
            channel.shutdownReads();
            return;
        }
        if (propagateClose) {
            super.shutdownReads();
        }
        synchronized(getReadLock()) {
            engine.closeInbound();
        }
        write(Buffers.EMPTY_BYTE_BUFFER, true);
        flush();
    }

    @Override
    public boolean shutdownWrites() throws IOException {
        if (! tls) {
            return channel.shutdownWrites();
        }
        synchronized(getWriteLock()) {
            if (doFlush()) {
                engine.closeOutbound();
                final ByteBuffer buffer = sendBuffer.getResource();
                SSLEngineResult result;
                do {
                    handleWrapResult(result = wrap(Buffers.EMPTY_BYTE_BUFFER, buffer), true);
                } while (handleHandshake(result, true));
                handleWrapResult(result = wrap(Buffers.EMPTY_BYTE_BUFFER, buffer), true);
                if (result.getHandshakeStatus() != HandshakeStatus.NOT_HANDSHAKING) {
                    return false;
                }
                if (doFlush() && engine.isOutboundDone() && (!propagateClose || super.shutdownWrites())) {
                    suspendWrites();
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean flush() throws IOException {
        if (! tls) {
            return channel.flush();
        }
        synchronized (getWriteLock()) {
            return doFlush();
        }
    }

    public XnioWorker getWorker() {
        return channel.getWorker();
    }

    private boolean doFlush() throws IOException {
        assert Thread.holdsLock(getWriteLock());
        assert ! Thread.holdsLock(getReadLock());
        final ByteBuffer buffer = sendBuffer.getResource();
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
        return true;
    }
}
