/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2013 Red Hat, Inc. and/or its affiliates, and individual
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

package org.xnio.ssl;

import static org.xnio.Bits.allAreSet;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;

import org.jboss.logging.Logger;
import org.xnio.Buffers;
import org.xnio.Pool;
import org.xnio.Pooled;
import org.xnio.conduits.StreamSinkConduit;
import org.xnio.conduits.StreamSourceConduit;

/**
 * An SSL conduit implementation based on {@link SSLEngine}.
 *
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
final class SslConduitEngine {

    private static final Logger log = Logger.getLogger("org.xnio.conduits");
    private static final String FQCN = SslConduitEngine.class.getName();

    // read-side
    private static final int NEED_WRAP    = 1 << 0x00; // conduit cannot be read due to pending wrap
    // write-side
    private static final int NEED_UNWRAP    = 1 << 0x01; // conduit cannot be written to due to pending unwrap

    // final fields

    /** The SSL engine. */
    private final SSLEngine engine;
    /** The buffer into which incoming SSL data is written. */
    private final Pooled<ByteBuffer> receiveBuffer;
    /** The buffer from which outbound SSL data is sent. */
    private final Pooled<ByteBuffer> sendBuffer;
    /** The buffer into which inbound clear data is written. */
    private final Pooled<ByteBuffer> readBuffer;

    // the next conduits
    private final StreamSinkConduit sinkConduit;
    private final StreamSourceConduit sourceConduit;

    // state
    private volatile short state;
    private static final AtomicIntegerFieldUpdater<SslConduitEngine> stateUpdater = AtomicIntegerFieldUpdater.newUpdater(SslConduitEngine.class, "state");

    /**
     * Construct a new instance.
     *
     * @param sinkConduit the sink channel to use for write operations
     * @param sourceConduit the source channel to use for read operations
     * @param engine the SSL engine to use
     * @param socketBufferPool the socket buffer pool
     * @param applicationBufferPool the application buffer pool
     */
    SslConduitEngine(final StreamSinkConduit sinkConduit, final StreamSourceConduit sourceConduit, final SSLEngine engine, final Pool<ByteBuffer> socketBufferPool, final Pool<ByteBuffer> applicationBufferPool) {
        if (engine == null) {
            throw new IllegalArgumentException("engine is null");
        }
        this.sinkConduit = sinkConduit;
        this.sourceConduit = sourceConduit;
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
    }

    private long bytesConsumed = 0;

    public long resetBytesConsumed() {
        try {
            return bytesConsumed;
        } finally {
            bytesConsumed = 0;
        }
    }

    public ByteBuffer getWrappedBuffer() {
        return sendBuffer.getResource();
    }

    public boolean wrap(final ByteBuffer src) throws IOException {
        return wrap(src, false);
    }

    public boolean wrap(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        if (length < 1) {
            return false;
        }
        final ByteBuffer buffer = sendBuffer.getResource();
        final boolean wrapAgain;
        final SSLEngineResult result;
        synchronized (getWrapLock()) {
            wrapAgain = handleWrapResult(result = engineWrap(srcs, offset, length, buffer), false);
            bytesConsumed += (long) result.bytesConsumed();
        }
        // handshake will tell us whether to keep the loop
        return wrapAgain && (handleHandshake(result, true) || (!isUnwrapNeeded() && Buffers.hasRemaining(srcs, offset, length)));
    }

    private boolean wrap(final ByteBuffer src, boolean isCloseExpected) throws IOException {
        final ByteBuffer buffer = sendBuffer.getResource();
        final boolean wrapAgain;
        final SSLEngineResult result;
        synchronized (getWrapLock()) {
            wrapAgain = handleWrapResult(result = engineWrap(src, buffer), isCloseExpected);
            bytesConsumed += result.bytesConsumed();
        }
        // handshake will tell us whether to keep the loop
        return wrapAgain && (handleHandshake(result, true) || (!isUnwrapNeeded() && src.hasRemaining()));
    }

    private SSLEngineResult engineWrap(final ByteBuffer[] srcs, final int offset, final int length, final ByteBuffer dest) throws SSLException {
        log.logf(FQCN, Logger.Level.TRACE, null, "Wrapping %s into %s", srcs, dest);
        return engine.wrap(srcs, offset, length, dest);
    }

    private SSLEngineResult engineWrap(final ByteBuffer src, final ByteBuffer dest) throws SSLException {
        log.logf(FQCN, Logger.Level.TRACE, null, "Wrapping %s into %s", src, dest);
        return engine.wrap(src, dest);
    }

    private boolean handleWrapResult(SSLEngineResult result, boolean closeExpected) throws IOException {
        assert Thread.holdsLock(getWrapLock());
        assert ! Thread.holdsLock(getUnwrapLock());
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
                    return true;
                }
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
        assert ! Thread.holdsLock(getUnwrapLock());
        // if read needs wrap, the only possible reason is that something went wrong with flushing, try to flush now
        if (isWrapNeeded()) {
            synchronized(getWrapLock()) {
                if (doFlush()) {
                    clearNeedWrap();
                }
            }
        }
        boolean newResult = false;
        for (;;) {
            switch (result.getHandshakeStatus()) {
                case FINISHED: {
                    clearNeedUnwrap();
                    // Operation can continue immediately
                    return true;
                }
                case NOT_HANDSHAKING: {
                    // Operation can continue immediately
                    clearNeedUnwrap();
                    return false;
                }
                case NEED_WRAP: {
                    // clear writeRequiresRead
                    clearNeedUnwrap();
                    // if write, let caller do the wrap
                    if (write) {
                        return true;
                    }
                    final ByteBuffer buffer = sendBuffer.getResource();
                    // else, trigger a write call
                    // Needs wrap, so we wrap (if possible)...
                    synchronized (getWrapLock()) {
                        // if flushed, and given caller is reading, tell it to continue only if read needs wrap is 0
                        if (doFlush()) {
                            if (!handleWrapResult(result = engineWrap(Buffers.EMPTY_BYTE_BUFFER, buffer), true) || !doFlush()) {
                                needWrap();
                                return false;
                            }
                            newResult = true;
                            clearNeedWrap();
                            continue;
                        }
                        assert !isUnwrapNeeded();
                        // else... oops, there is unflushed data, and handshake status is NEED_WRAP
                        needWrap();
                        // tell read caller to break read loop
                        return false;
                    }
                }
                case NEED_UNWRAP: {
                    // if read, let caller do the unwrap
                    if (! write) {
                        return newResult;
                    }
                    synchronized(getWrapLock()) {
                        // there could be unflushed data from a previous wrap, make sure everything is flushed at this point
                        doFlush();
                    }
                    final ByteBuffer buffer = receiveBuffer.getResource();
                    final ByteBuffer unwrappedBuffer = readBuffer.getResource();
                    synchronized (getUnwrapLock()) {
                        int unwrapResult = handleUnwrapResult(result = engineUnwrap(buffer, unwrappedBuffer));
                        if (unwrapResult >= 0) { // FIXME what if the unwrap return buffer overflow???
                            // have we made some progress?
                            if(result.getHandshakeStatus() != HandshakeStatus.NEED_UNWRAP || result.bytesConsumed() > 0) {
                                clearNeedUnwrap();
                                continue;
                            }
                            assert !isWrapNeeded();
                            // no point in proceeding, we're stuck until the user reads anyway
                            needUnwrap();
                            return false;
                        } else if (unwrapResult == -1 && result.getHandshakeStatus() == HandshakeStatus.NEED_UNWRAP) {
                            // connection has been closed by peer prior to handshake finished
                            closeOutbound();
                            closeInbound();
                            throw new ClosedChannelException(); // FIXME throw this exception?
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

    private SSLEngineResult engineUnwrap(final ByteBuffer buffer, final ByteBuffer unwrappedBuffer) throws IOException {
        if (!buffer.hasRemaining()) {
            buffer.compact();
            sourceConduit.read(buffer);
            buffer.flip();
        }
        log.logf(FQCN, Logger.Level.TRACE, null, "Unwrapping %s into %s", buffer, unwrappedBuffer);
        return engine.unwrap(buffer, unwrappedBuffer);
    }

    public ByteBuffer getUnwrapBuffer() {
        return receiveBuffer.getResource();
    }

    public int unwrap(final ByteBuffer dst) throws IOException {
        return (int) unwrap (new ByteBuffer[]{dst}, 0, 1);
    }

    public long unwrap(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        if (dsts.length == 0 || length == 0) {
            return 0L;
        }
        final ByteBuffer buffer = receiveBuffer.getResource();
        // TODO what do we do when we are out of space at unwrappedBuffer?
        final ByteBuffer unwrappedBuffer = readBuffer.getResource();
        long total = 0;
        SSLEngineResult result;
        synchronized(getUnwrapLock()) {
            if (unwrappedBuffer.position() > 0 && unwrappedBuffer.hasRemaining()) {
                total += (long) copyUnwrappedData(dsts, offset, length, unwrappedBuffer);
            }
        }
        int res = 0;
        do {
            synchronized (getUnwrapLock()) {
                if (! Buffers.hasRemaining(dsts, offset, length)) {
                    return total;
                }
                res = handleUnwrapResult(result = engineUnwrap(buffer, unwrappedBuffer));
                total += (long) copyUnwrappedData(dsts, offset, length, unwrappedBuffer);
            }
        } while (handleHandshake(result, false) || res > 0);
        if (total == 0L) {
            if (res == -1) {
                return -1L;
            }
        }
        return total;
    }

    private int copyUnwrappedData(final ByteBuffer[] dsts, final int offset, final int length, ByteBuffer unwrappedBuffer) {
        assert Thread.holdsLock(getUnwrapLock());
        unwrappedBuffer.flip();
        try {
            return Buffers.copy(dsts, offset, length, unwrappedBuffer);
        } finally {
            unwrappedBuffer.compact();
        }
    }

    private int handleUnwrapResult(final SSLEngineResult result) throws IOException {
        assert ! Thread.holdsLock(getWrapLock());
        assert Thread.holdsLock(getUnwrapLock());
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
                synchronized (getUnwrapLock()) {
                    buffer.compact();
                    try {
                        return sourceConduit.read(buffer);
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

    private Object getUnwrapLock() {
        return receiveBuffer;
    }

    private Object getWrapLock() {
        return sendBuffer;
    }

    private boolean doFlush() throws IOException {
        return doFlush(false);
    }

    private boolean doFlush(final boolean shutdown) throws IOException {
        assert Thread.holdsLock(getWrapLock());
        assert ! Thread.holdsLock(getUnwrapLock());
        if (sinkConduit.isWriteShutdown()) {
            return true;
        }
        final ByteBuffer buffer = sendBuffer.getResource();
        if (shutdown && (!engine.isOutboundDone() || !engine.isInboundDone())) {
            SSLEngineResult result;
            do {
                if (!handleWrapResult(result = engineWrap(Buffers.EMPTY_BYTE_BUFFER, buffer), true)) {
                    return false;
                }
            } while (handleHandshake(result, true));
            handleWrapResult(result = engineWrap(Buffers.EMPTY_BYTE_BUFFER, buffer), true);
            if (result.getHandshakeStatus() != HandshakeStatus.NOT_HANDSHAKING || !engine.isOutboundDone()) {
                return false;
            }
        }
        buffer.flip();
        try {
            while (buffer.hasRemaining()) {
                final int res = sinkConduit.write(buffer);
                if (res == 0) {
                    return false;
                }
            }
        } finally {
            buffer.compact();
        }
        return sinkConduit.flush();
    }

    public void closeOutbound() throws IOException {
        try {
            engine.closeOutbound();
            doFlush(true);
        } finally {
            sendBuffer.free();
        }
    }

    public void awaitWritable() throws IOException {
        // TODO
    }

    public void awaitWritable(long time, TimeUnit timeUnit) throws IOException {
        // TODO
    }

    public boolean flush() throws IOException {
        return doFlush(false);
    }

    public void closeInbound() throws IOException {
        try {
            synchronized (getUnwrapLock()) {
                if(!engine.isInboundDone()) {
                    engine.closeInbound();
                }
            }
            wrap(Buffers.EMPTY_BYTE_BUFFER, true);
            flush();
        } finally {
            readBuffer.free();
            receiveBuffer.free();
        }
    }

    public void awaitReadable() throws IOException {
        // TODO
    }

    public void awaitReadable(long time, TimeUnit timeUnit) throws IOException {
        // TODO
    }

    /**
     * Indicate that the channel will not be readable until the write handler is called.
     */
    private void needWrap() {
        setFlags(NEED_WRAP);
    }

    /**
     * Indicate if the channel is not readable until the write handler is called.
     */
    private boolean isWrapNeeded() {
        return allAreSet(state, NEED_WRAP);
    }

    /**
     * Indicate that the channel no longer requires writability for reads to proceed.
     */
    private void clearNeedWrap() {
        clearFlags(NEED_WRAP);
    }

    /**
     * Indicate that the channel will not be writable until the read handler is called.
     */
    private void needUnwrap() {
        setFlags(NEED_UNWRAP);
    }

    /**
     * Indicate if the channel is not writable until the read handler is called.
     */
    private boolean isUnwrapNeeded() {
        return allAreSet(state, NEED_UNWRAP);
    }

    /**
     * Indicate that the conduit no longer requires writability for writes to proceed.
     */
    private void clearNeedUnwrap() {
        clearFlags(NEED_UNWRAP);
    }

    private int setFlags(int flags) {
        int oldState;
        do {
            oldState = state;
            if ((oldState & flags) == flags) {
                return oldState;
            }
        } while (! stateUpdater.compareAndSet(this, oldState, oldState | flags));
        return oldState;
    }

    private int clearFlags(int flags) {
        int oldState;
        do {
            oldState = state;
            if ((oldState & flags) == 0) {
                return oldState;
            }
        } while (! stateUpdater.compareAndSet(this, oldState, oldState & ~flags));
        return oldState;
    }
}
