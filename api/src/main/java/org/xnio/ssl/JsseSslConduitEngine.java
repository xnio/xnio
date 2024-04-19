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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.security.cert.CertPathBuilderException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import static java.lang.Thread.currentThread;
import static java.util.concurrent.locks.LockSupport.park;
import static java.util.concurrent.locks.LockSupport.parkNanos;
import static java.util.concurrent.locks.LockSupport.unpark;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSession;

import org.jboss.logging.Logger;

import org.xnio.Buffers;
import org.xnio.ByteBufferPool;
import org.xnio.conduits.StreamSinkConduit;
import org.xnio.conduits.StreamSourceConduit;

import static org.xnio.Bits.allAreClear;
import static org.xnio.Bits.allAreSet;
import static org.xnio.Bits.anyAreSet;
import static org.xnio.Bits.intBitMask;
import static org.xnio._private.Messages.msg;

/**
 * {@link SSLEngine} wrapper, used by Jsse SSL conduits.
 *
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 */
final class JsseSslConduitEngine {

    private static final Logger log = Logger.getLogger("org.xnio.conduits");
    private static final String FQCN = JsseSslConduitEngine.class.getName();

    // read-side
    private static final int NEED_WRAP              = 1 << 0x00; // conduit cannot be read due to pending wrap
    private static final int READ_SHUT_DOWN         = 1 << 0x01; // user shut down reads
    private static final int BUFFER_UNDERFLOW         = 1 << 0x02; // even though there is data in the buffer there is not enough to form a complete packet
    @SuppressWarnings("unused")
    private static final int READ_FLAGS             = intBitMask(0x00, 0x0F);
    // write-side
    private static final int NEED_UNWRAP            = 1 << 0x10; // conduit cannot be written to due to pending unwrap
    private static final int WRITE_SHUT_DOWN        = 1 << 0x11; // user requested shut down of writes
    private static final int WRITE_COMPLETE         = 1 << 0x12; // flush acknowledged full write shutdown

    private static final int FIRST_HANDSHAKE          = 1 << 0x16; // first handshake has not been performed
    private static final int ENGINE_CLOSED          = 1 << 0x17;  // engine is fully closed
     // engine is fully closed
    @SuppressWarnings("unused")
    private static final int WRITE_FLAGS            = intBitMask(0x10, 0x1F);
    // empty buffer
    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);

    // final fields
    /** The SSL engine. */
    private final SSLEngine engine;
    /** The buffer into which incoming SSL data is written. */
    private final ByteBuffer receiveBuffer;
    /** The buffer from which outbound SSL data is sent. */
    private final ByteBuffer sendBuffer;
    /** An expanded non-final buffer from which outbound SSL data is sent when
     * large fragments handling is enabled in the underlying SSL Engine. When
     * this happens, we need a specific buffer with expanded capacity. */
    private  ByteBuffer expandedSendBuffer;
    /** The buffer into which inbound clear data is written. */
    private final ByteBuffer readBuffer;

    // the next conduits
    private final StreamSinkConduit sinkConduit;
    private final StreamSourceConduit sourceConduit;
    // the connection
    private final JsseSslStreamConnection connection;

    // state
    private volatile int state;
    private static final AtomicIntegerFieldUpdater<JsseSslConduitEngine> stateUpdater = AtomicIntegerFieldUpdater.newUpdater(JsseSslConduitEngine.class, "state");
    // waiters
    @SuppressWarnings("unused")
    private volatile Thread readWaiter;
    @SuppressWarnings("unused")
    private volatile Thread writeWaiter;
    private static final AtomicReferenceFieldUpdater<JsseSslConduitEngine, Thread> readWaiterUpdater = AtomicReferenceFieldUpdater.newUpdater(JsseSslConduitEngine.class, Thread.class, "readWaiter");
    private static final AtomicReferenceFieldUpdater<JsseSslConduitEngine, Thread> writeWaiterUpdater = AtomicReferenceFieldUpdater.newUpdater(JsseSslConduitEngine.class, Thread.class, "writeWaiter");

    /**
     * Construct a new instance.
     *
     * @param connection            the ssl connection associated with this engine 
     * @param sinkConduit           the sink channel to use for write operations
     * @param sourceConduit         the source channel to use for read operations
     * @param engine                the SSL engine to use
     * @param socketBufferPool      the socket buffer pool
     * @param applicationBufferPool the application buffer pool
     */
    JsseSslConduitEngine(final JsseSslStreamConnection connection, final StreamSinkConduit sinkConduit, final StreamSourceConduit sourceConduit, final SSLEngine engine, final ByteBufferPool socketBufferPool, final ByteBufferPool applicationBufferPool) {
        if (connection == null) {
            throw msg.nullParameter("connection");
        }
        if (sinkConduit == null) {
            throw msg.nullParameter("sinkConduit");
        }
        if (sourceConduit == null) {
            throw msg.nullParameter("sourceConduit");
        }
        if (engine == null) {
            throw msg.nullParameter("engine");
        }
        if (socketBufferPool == null) {
            throw msg.nullParameter("socketBufferPool");
        }
        if (applicationBufferPool == null) {
            throw msg.nullParameter("applicationBufferPool");
        }
        this.connection = connection;
        this.sinkConduit = sinkConduit;
        this.sourceConduit = sourceConduit;
        this.engine = engine;
        this.state = FIRST_HANDSHAKE;
        final SSLSession session = engine.getSession();
        final int packetBufferSize = session.getPacketBufferSize();
        boolean ok = false;
        receiveBuffer = socketBufferPool.allocate();
        try {
            receiveBuffer.flip();
            sendBuffer = socketBufferPool.allocate();
            try {
                if (receiveBuffer.capacity() < packetBufferSize || sendBuffer.capacity() < packetBufferSize) {
                    // create expanded send buffer
                    expandedSendBuffer = ByteBuffer.allocate(packetBufferSize);
                }
                readBuffer = applicationBufferPool.allocate();
                ok = true;
            } finally {
                if (! ok) ByteBufferPool.free(sendBuffer);
            }
        } finally {
            if (! ok) ByteBufferPool.free(receiveBuffer);
        }
    }

    /**
     * Begins handshake.
     * 
     * @throws IOException if an I/O error occurs
     */
    public void beginHandshake() throws IOException {
        engine.beginHandshake();
    }

    /**
     * Returns the engine's session.
     */
    public SSLSession getSession() {
        return engine.getSession();
    }

    /**
     * Attempt to wrap the bytes in {@code src}. The wrapped bytes can be later retrieved by calling
     * {@link #getWrappedBuffer()}.
     * <p>
     * If the engine is performing handshake during this request, not all bytes could be wrapped. In this case, a later
     * call can be performed to attempt to wrap more bytes.
     * <p>
     * This method must not be invoked inside the {@link #getWrapLock() wrap lock}, or else unexpected behavior
     * could occur.

     * @param src  the bytes to be wrapped
     * @return     the amount of wrapped bytes.
     *  
     * @throws IOException if an IO exception occurs during wrapping
     */
    public int wrap(final ByteBuffer src) throws IOException {
        return wrap(src, false);
    }

    /**
     * Attempt to wrap the bytes in {@code srcs}. The wrapped bytes can be later retrieved by calling
     * {@link #getWrappedBuffer()}.
     * <p>
     * If the engine is performing handshake during this request, not all bytes could be wrapped. In this case, a later
     * call can be performed to attempt to wrap more bytes.
     * <p>
     * This method must not be invoked inside the {@link #getWrapLock() wrap lock}, or else unexpected behavior
     * could occur.
     *  
     * @param srcs   contains the bytes to be wrapped
     * @param offset offset
     * @param length length
     * @return       the amount of wrapped bytes. 
     * 
     * @throws IOException if an IO exception occurs during wrapping
     */
    public long wrap(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        assert ! Thread.holdsLock(getWrapLock());
        assert ! Thread.holdsLock(getUnwrapLock());
        if (length < 1) {
            return 0L;
        }
        if (allAreSet(state, WRITE_COMPLETE)) { // atempted write after shutdown, this is 
            // a workaround for a bug found in SSLEngine
            throw new ClosedChannelException();
        }
        long bytesConsumed = 0;
        boolean run;
        try {
            do {
                final SSLEngineResult result;
                synchronized (getWrapLock()) {
                    run = handleWrapResult(result = engineWrap(srcs, offset, length, getSendBuffer()), false);
                    bytesConsumed += (long) result.bytesConsumed();
                }
                // handshake will tell us whether to keep the loop
                run = run && (handleHandshake(result, true) || (!isUnwrapNeeded() && Buffers.hasRemaining(srcs, offset, length)));
            } while (run);
        } catch (SSLHandshakeException e) {
            try {
                synchronized (getWrapLock()) {
                    engine.wrap(EMPTY_BUFFER, getSendBuffer());
                    doFlush();
                }
            } catch (IOException ignore) {}
            throw e;
        }
        return bytesConsumed;
    }

    /**
     * Returns the buffer that contains the wrapped data.
     * <p>
     * Retrieval and manipulation of this buffer should always be protected by the {@link #getWrapLock() wrap lock}.
     * 
     * @return the buffer containing wrapped bytes
     */
    public ByteBuffer getWrappedBuffer() {
        assert Thread.holdsLock(getWrapLock());
        assert ! Thread.holdsLock(getUnwrapLock());
        return allAreSet(stateUpdater.get(this), ENGINE_CLOSED)? Buffers.EMPTY_BYTE_BUFFER: getSendBuffer();
    }

    /**
     * Returns the wrap lock, that must be used whenever the {@link #getWrappedBuffer() wrapped buffer} is being
     * accessed.
     * <p>
     * This lock is also used internally by wrapping operations, thus guaranteeing safe concurrent execution of
     * both wrap and unwrap operations, specially during handshake handling.
     * 
     * @return lock for protecting access to the unwrapped buffer
     */
    public Object getWrapLock() {
        return sendBuffer;
    }

    /**
     * Wrap operation used internally.
     *
     * @param src             contains the bytes to be wrapped
     * @param isCloseExpected indicates if close is expected, information that is used to perform special handling
     *                        when closing the engine
     * @return                the amount of resulting wrapped bytes
     * 
     * @throws IOException if an IO exception occurs during wrapping
     */
    private int wrap(final ByteBuffer src, boolean isCloseExpected) throws IOException {
        assert ! Thread.holdsLock(getWrapLock());
        assert ! Thread.holdsLock(getUnwrapLock());
        if (allAreSet(state, WRITE_COMPLETE)) { // attempted write after shutdown, this is
            // a workaround for a bug found in SSLEngine
            throw new ClosedChannelException();
        }
        clearFlags(FIRST_HANDSHAKE);
        int bytesConsumed = 0;
        boolean run;
        try {
            do {
                final SSLEngineResult result;
                synchronized (getWrapLock()) {
                    run = handleWrapResult(result = engineWrap(src, getSendBuffer()), isCloseExpected);
                    bytesConsumed += result.bytesConsumed();
                }
                // handshake will tell us whether to keep the loop
                run = run && bytesConsumed == 0 && (handleHandshake(result, true) || (!isUnwrapNeeded() && src.hasRemaining()));
            } while (run);
        } catch (SSLHandshakeException e) {
            try {
                synchronized (getWrapLock()) {
                    engine.wrap(EMPTY_BUFFER, getSendBuffer());
                    doFlush();
                }
            } catch (IOException ignore) {}
                Throwable cause = e.getCause();

                while (cause!=null && !cause.equals(cause.getCause())){
                    if(cause instanceof CertPathBuilderException){
                        throw msg.wrapJDKException(e.getMessage(),e);
                    }
                    cause=cause.getCause();
                }
                throw e;
        }
        return bytesConsumed;
    }

    /**
     * Invoke inner SSL engine to wrap.
     */
    private SSLEngineResult engineWrap(final ByteBuffer[] srcs, final int offset, final int length, final ByteBuffer dest) throws SSLException {
        assert Thread.holdsLock(getWrapLock());
        assert ! Thread.holdsLock(getUnwrapLock());
        log.logf(FQCN, Logger.Level.TRACE, null, "Wrapping %s into %s", srcs, dest);
        try {
            return engine.wrap(srcs, offset, length, dest);
        } catch (SSLHandshakeException e) {
            try {
                engine.wrap(srcs, offset, length, dest);
                doFlush();
            } catch (IOException ignore) {}
            throw e;
        }
    }

    /**
     * Invoke inner SSL engine to wrap.
     */
    private SSLEngineResult engineWrap(final ByteBuffer src, final ByteBuffer dest) throws SSLException {
        assert Thread.holdsLock(getWrapLock());
        assert ! Thread.holdsLock(getUnwrapLock());
        log.logf(FQCN, Logger.Level.TRACE, null, "Wrapping %s into %s", src, dest);
        return engine.wrap(src, dest);
    }

    /**
     * Handles the wrap result, indicating if caller should perform a new attempt to wrap.
     * 
     * @param result        the wrap result
     * @param closeExpected is a closed engine result expected
     * @return              {@code true} if a new attempt to wrap should be made
     * 
     * @throws IOException if an IO exception occurs
     */
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
                final ByteBuffer buffer = getSendBuffer();
                if (buffer.position() == 0) {
                    final int bufferSize = engine.getSession().getPacketBufferSize();
                    if (buffer.capacity() < bufferSize) {
                        msg.expandedSslBufferEnabled(bufferSize);
                        expandedSendBuffer = ByteBuffer.allocate(bufferSize);
                    } else throw msg.wrongBufferExpansion();
                } else {
                    // there's some data in there, so send it first
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
                throw msg.unexpectedWrapResult(result.getStatus());
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
                    connection.handleHandshakeFinished();
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
                    final ByteBuffer buffer = getSendBuffer();
                    // else, trigger a write call
                    // Needs wrap, so we wrap (if possible)...
                    synchronized (getWrapLock()) {
                        // given caller is reading, tell it to continue only if we can move away from  NEED_WRAP
                        // and flush any wrapped data we may have left
                        if (doFlush()) {
                            if (result.getStatus() == SSLEngineResult.Status.CLOSED) {
                                return false;
                            }
                            if (!handleWrapResult(result = engineWrap(Buffers.EMPTY_BYTE_BUFFER, buffer), true) || !doFlush()) {
                                needWrap();
                                return false;
                            }
                            // success, clear need wrap and handle the new handshake status
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
                    final ByteBuffer buffer = receiveBuffer;
                    final ByteBuffer unwrappedBuffer = readBuffer;
                    // FIXME this if block is a workaround for a bug in SSLEngine
                   if (result.getHandshakeStatus() == HandshakeStatus.NEED_UNWRAP && engine.isOutboundDone()) {
                        synchronized (getUnwrapLock()) {
                            buffer.compact();
                            sourceConduit.read(buffer);
                            buffer.flip();
                            if (buffer.hasRemaining() && sourceConduit.isReadResumed()) {
                                sourceConduit.wakeupReads();
                            }
                            return false;
                        }
                    }
                    synchronized (getUnwrapLock()) {
                        // attempt to unwrap
                        int unwrapResult = handleUnwrapResult(result = engineUnwrap(buffer, unwrappedBuffer));
                        if (buffer.hasRemaining() && sourceConduit.isReadResumed()) {
                            sourceConduit.wakeupReads();
                        }
                        if (unwrapResult >= 0) {
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
                            if (!allAreSet(state, READ_SHUT_DOWN)) {
                                // connection has been closed by peer prior to handshake finished
                                throw new ClosedChannelException();
                            }
                            return false;
                        }
                    }
                    continue;
                }
                case NEED_TASK: {
                    Runnable task;
                    synchronized (engine) {
                        // run the tasks needed for handshaking
                        while ((task = engine.getDelegatedTask()) != null) {
                            try {
                                task.run();
                            } catch (Exception e) {
                                throw new IOException(e);
                            }
                        }
                    }
                    // caller should try to wrap/unwrap again
                    return true;
                }
                default:
                    throw msg.unexpectedHandshakeStatus(result.getHandshakeStatus());
            }
        }
    }

    /**
     * Unwraps the bytes contained in {@link #getUnwrapBuffer()}, copying the resulting unwrapped bytes into
     * {@code dst}.
     * <p>
     * If the engine is performing handshake during this request, not all bytes could be unwrapped. In this case, a
     * later call can be performed to attempt to unwrap more bytes.
     * <p>
     * This method must not be invoked inside the {@link #getUnwrapLock() unwrap lock}, or else unexpected behavior
     * could occur.
     * 
     * @param dst          where the resulting unwrapped bytes will be copied to
     * @return             the amount of resulting unwrapped bytes
     * 
     * @throws IOException if an IO exception occurs during unwrapping
     */
    public int unwrap(final ByteBuffer dst) throws IOException {
        return (int) unwrap(new ByteBuffer[]{dst}, 0, 1);
    }

    private int failureCount = 0;

    /**
     * Unwraps the bytes contained in {@link #getUnwrapBuffer()}, copying the resulting unwrapped bytes into
     * {@code dsts}.
     * <p>
     * If the engine is performing handshake during this request, not all bytes could be unwrapped. In this case, a
     * later call can be performed to attempt to unwrap more bytes.
     * <p>
     * This method must not be invoked inside the {@link #getUnwrapLock() unwrap lock}, or else unexpected behavior
     * could occur.
     * 
     * @param dsts          where the resulting unwrapped bytes will be copied to
     * @param offset        offset
     * @param length        length
     * @return              the amount of resulting unwrapped bytes
     * 
     * @throws IOException if an IO exception occurs during unwrapping
     */
    public long unwrap(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        assert ! Thread.holdsLock(getUnwrapLock());
        assert ! Thread.holdsLock(getWrapLock());
        if (dsts.length == 0 || length == 0 || isClosed()) {
            return 0L;
        }
        clearFlags(FIRST_HANDSHAKE | BUFFER_UNDERFLOW);
        final ByteBuffer buffer = receiveBuffer;
        final ByteBuffer unwrappedBuffer = readBuffer;
        long total = 0;
        SSLEngineResult result;
        synchronized(getUnwrapLock()) {
            if (unwrappedBuffer.position() > 0) {
                total += (long) copyUnwrappedData(dsts, offset, length, unwrappedBuffer);
            }
        }
        int res = 0;
        try {
            do {
                synchronized (getUnwrapLock()) {
                    if (! Buffers.hasRemaining(dsts, offset, length)) {
                        if (unwrappedBuffer.hasRemaining() && sourceConduit.isReadResumed()) {
                            sourceConduit.wakeupReads();
                        }
                        return total;
                    }
                    res = handleUnwrapResult(result = engineUnwrap(buffer, unwrappedBuffer));
                    if (unwrappedBuffer.position() > 0) { // test the position of the buffer instead of the
                        // the amount of produced bytes, because in a concurrent scenario, during this loop,
                        // another thread could read more bytes as a side effect of a need unwrap
                        total += (long) copyUnwrappedData(dsts, offset, length, unwrappedBuffer);
                    }
                }
            } while ((handleHandshake(result, false) || res > 0));
        } catch (SSLHandshakeException e) {
            try {
                synchronized (getWrapLock()) {
                    engine.wrap(EMPTY_BUFFER, getSendBuffer());
                    doFlush();
                }
            } catch (IOException ignore) {}
            throw e;
        }
        if (total == 0L) {
            if (res == -1) {
                return -1L;
            }
        }
        if(res == 0 && result.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
            int old;
            do {
                old = state;
            } while(!stateUpdater.compareAndSet(this, old, old | BUFFER_UNDERFLOW));
        }
        return total;
    }

    /**
     * Returns the buffer that contains the data to be unwrapped.
     * <p>
     * Retrieval and manipulation of this buffer should always be protected by the {@link #getUnwrapLock() unwrap lock}.
     * 
     * @return the buffer containing bytes to be unwrapped
     */
    public ByteBuffer getUnwrapBuffer() {
        assert Thread.holdsLock(getUnwrapLock());
        assert ! Thread.holdsLock(getWrapLock());
        return receiveBuffer;
    }

    /**
     * Returns the unwrap lock, that must be used whenever the {@link #getUnwrapBuffer() unwrap buffer} is being
     * accessed.
     * <p>
     * This lock is also used internally by unwrapping operations, thus guaranteeing safe concurrent execution of
     * both wrap and unwrap operations, specially during handshake handling.
     * 
     * @return lock for protecting access to the unwrap buffer
     */
    public Object getUnwrapLock() {
        return receiveBuffer;
    }

    /**
     * Invoke inner SSL engine to unwrap.
     */
    private SSLEngineResult engineUnwrap(final ByteBuffer buffer, final ByteBuffer unwrappedBuffer) throws IOException {
        assert Thread.holdsLock(getUnwrapLock());
        if (!buffer.hasRemaining()) {
            buffer.compact();
            sourceConduit.read(buffer);
            buffer.flip();
        }
        log.logf(FQCN, Logger.Level.TRACE, null, "Unwrapping %s into %s", buffer, unwrappedBuffer);
        try {
            return engine.unwrap(buffer, unwrappedBuffer);
        } catch (SSLHandshakeException e) {
            // see XNIO-406 (TODO once the JDK bug is solved, remove this catch block)
            // ugly hack. I tried to check for buffer has remaining in previous if block (after read attempt), such as
            // if (!buffer.hasRemaining() && !engine.isInboundDone() && !engine.isOutboundDone() && engine.getDelegatedTask() == null) {
            //      return new SSLEngineResult(...)
            // but that breaks other tests (for some known reasons), so it appears that the only workaround for now is catching the exception
            if (e.getMessage().startsWith("Insufficient buffer remaining for AEAD cipher fragment (2).")) {
                return new SSLEngineResult(SSLEngineResult.Status.BUFFER_UNDERFLOW, HandshakeStatus.NEED_UNWRAP,0, 0);
            }
            throw e;
        }
    }

    /**
     * Copy unwrapped data from {@code unwrappedBuffer} into {@code dsts}.
     * 
     * @param dsts            destine of copy
     * @param offset          offset
     * @param length          length
     * @param unwrappedBuffer source from where byte will be copied
     * @return                the amount of copied bytes
     */
    private int copyUnwrappedData(final ByteBuffer[] dsts, final int offset, final int length, ByteBuffer unwrappedBuffer) {
        assert Thread.holdsLock(getUnwrapLock());
        unwrappedBuffer.flip();
        try {
            return Buffers.copy(dsts, offset, length, unwrappedBuffer);
        } finally {
            unwrappedBuffer.compact();
        }
    }

    /**
     * Handles the unwrap result, indicating how many bytes have been consumed.
     * 
     * @param result        the unwrap result
     * @return              the amount of bytes consumed by unwrap. If the engine is closed and no bytes were consumed,
     *                      returns {@code -1}.
     * 
     * @throws IOException if an IO exception occurs
     */
    private int handleUnwrapResult(final SSLEngineResult result) throws IOException {
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
                final ByteBuffer buffer = receiveBuffer;
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
                // if unwrap processed any data, it should return bytes consumed instead of -1
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
                throw msg.unexpectedUnwrapResult(result.getStatus());
            }
        }
    }

    /**
     * Flush any data needed for handshaking into the {@link #getWrappedBuffer() wrapped buffer}.
     * <p>
     * The flushed data can later be retrieved by reading the buffer. If the engine is closed, this method can be used
     * to flush all data associated to engine close messages.
     * 
     * @return {@code true} if there is no left data to be flushed; {@code false} if for some reason the engine was
     *         unable to flush all data
     *
     * @throws IOException if an IO exception occurs during attempt to flush
     */
    public boolean flush() throws IOException {
        int oldState, newState;
        oldState = stateUpdater.get(this);
        if (allAreSet(oldState, WRITE_COMPLETE)) {
            return true;
        }
        synchronized (getWrapLock()) {
            if (allAreSet(oldState, WRITE_SHUT_DOWN)) {
                if (!wrapCloseMessage()) {
                    return false;
                }
            } else {
                return true;
            }
        }
        // conclude write
        newState = oldState | WRITE_COMPLETE;
        while (! stateUpdater.compareAndSet(this, oldState, newState)) {
            oldState = stateUpdater.get(this);
            if (allAreSet(oldState, WRITE_COMPLETE)) {
                return true;
            }
            newState = oldState | WRITE_COMPLETE;
        }
        // close the engine if read is shut down
        if (allAreSet(oldState, READ_SHUT_DOWN)) {
            closeEngine();
        }
        return true;
    }

    /**
     * Attempt to finish wrapping close handshake bytes.
     * 
     * @return              {@code true} only if all bytes concerning close handshake messages have been wrapped.
     * @throws IOException if an unexpected IO exception occurs
     */
    private boolean wrapCloseMessage() throws IOException {
        assert ! Thread.holdsLock(getUnwrapLock());
        assert Thread.holdsLock(getWrapLock());
        if (sinkConduit.isWriteShutdown()) {
            return true;
        }
        if (!engine.isOutboundDone() || !engine.isInboundDone()) {
            SSLEngineResult result;
            do {
                if (!handleWrapResult(result = engineWrap(Buffers.EMPTY_BYTE_BUFFER, getSendBuffer()), true)) {
                    return false;
                }
            } while (handleHandshake(result, true) && (result.getHandshakeStatus() != HandshakeStatus.NEED_UNWRAP || !engine.isOutboundDone()));
            handleWrapResult(result = engineWrap(Buffers.EMPTY_BYTE_BUFFER, getSendBuffer()), true);
            if (!engine.isOutboundDone() || (result.getHandshakeStatus() != HandshakeStatus.NOT_HANDSHAKING &&
                    result.getHandshakeStatus() != HandshakeStatus.NEED_UNWRAP)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Flushes all data in the wrapped bytes buffer.
     * 
     * @return  {@code true} if all data available has been flushed
     * 
     * @throws IOException if an unexpected IO exception occurs
     */
    private boolean doFlush() throws IOException {
        assert Thread.holdsLock(getWrapLock());
        assert ! Thread.holdsLock(getUnwrapLock());
        final ByteBuffer buffer;
        buffer = getSendBuffer();
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

    /**
     * Closes this engine for both inbound and outbound, clearing the buffers.
     * 
     * @throws IOException
     */
    private void closeEngine() throws IOException {
        int old = setFlags(ENGINE_CLOSED);
        // idempotent
        if (allAreSet(old, ENGINE_CLOSED)) {
            return;
        }
        try {
            synchronized(getWrapLock()) {
                if (! doFlush()) {
                    throw msg.unflushedData();
                }
            }
        } finally {
            sourceConduit.terminateReads();
            sinkConduit.terminateWrites();
            connection.readClosed();
            connection.writeClosed();
            ByteBufferPool.free(readBuffer);
            ByteBufferPool.free(receiveBuffer);
            ByteBufferPool.free(sendBuffer);
        }
    }

    /**
     * Signals that no outbound data will be sent.
     * 
     * @throws IOException if an IO exception occurs
     */
    public void closeOutbound() throws IOException {
        if (isOutboundClosed()) //idempotent
            return;
        int old = setFlags(WRITE_SHUT_DOWN);
        boolean sentCloseMessage = true;
        try {
            if (allAreClear(old, WRITE_SHUT_DOWN)) {
                engine.closeOutbound();
                synchronized (getWrapLock()) {
                    sentCloseMessage = wrapCloseMessage() && flush();
                }
            }
            if (!allAreClear(old, READ_SHUT_DOWN) && sentCloseMessage) {
                closeEngine();
            }
        } catch (Exception e) {
            //if there is an exception on close we immediately close the engine to make sure buffers are freed
            try {
                closeEngine();
            } catch (Exception closeEngineException) {
                msg.failedToCloseSSLEngine(e, closeEngineException);
            }
            if(e instanceof IOException) {
                throw (IOException) e;
            } else {
                throw (RuntimeException) e;
            }
        }
    }

    /**
     * Closes the engine for both inbound and outbound connection, and discards this engine's internal
     * resources immediately without handshaking if this engine has not been used.
     *
     * @throws IOException
     */
    void close() throws IOException {
        try {
            closeInbound();
        } catch (Throwable t) {
            try {
                closeOutbound();
            } catch (Throwable t2) {
                t2.addSuppressed(t);
                throw t2;
            }
            throw t;
        }
        closeOutbound();
    }

    /**
     * Indicates if outbound is closed.
     * 
     * @throws IOException if an IO exception occurs
     */
    public boolean isOutboundClosed() {
        return allAreSet(stateUpdater.get(this), WRITE_SHUT_DOWN);
    }

    /**
     * Block until this engine can wrap messages.
     * <p>
     * The engine may be unable to wrap if a handshake message needs to be unwrapped first. In this case, this thread
     * will be awakened as soon as the message is made available for reading by the internal source conduit. 
     * 
     * @throws IOException if an IO exception occurs during await
     */
    public void awaitCanWrap() throws IOException {
        int oldState = state;
        if (anyAreSet(oldState, WRITE_SHUT_DOWN) || !allAreSet(oldState, NEED_UNWRAP)) {
            return;
        }
        final Thread thread = currentThread();
        final Thread next = writeWaiterUpdater.getAndSet(this, thread);
        try {
            if (anyAreSet(oldState = state, WRITE_SHUT_DOWN)) {
                return;
            }
            if (allAreSet(oldState, NEED_UNWRAP)) {
                unwrap(Buffers.EMPTY_BYTE_BUFFER);
            }
            park(this);
            if (thread.isInterrupted()) {
                throw msg.interruptedIO();
            }
        } finally {
            // always unpark because we cannot know if our awaken was spurious
            if (next != null) unpark(next);
        }
    }

    /**
     * Block until this engine can wrap messages.
     * <p>
     * The engine may be unable to wrap if a handshake message needs to be unwrapped first. In this case, this thread
     * will be awakened as soon as the message is made available for reading by the internal source conduit. 
     * 
     * @param time     timeout for blocking
     * @param timeUnit timeout unit
     * @throws IOException if an IO exception occurs during await
     */
    public void awaitCanWrap(long time, TimeUnit timeUnit) throws IOException {
        int oldState = state;
        if (anyAreSet(oldState, WRITE_SHUT_DOWN) || !allAreSet(oldState, NEED_UNWRAP)) {
            return;
        }
        final Thread thread = currentThread();
        final Thread next = writeWaiterUpdater.getAndSet(this, thread);
        long duration = timeUnit.toNanos(time);
        try {
            if (anyAreSet(oldState = state, WRITE_SHUT_DOWN)) {
                return;
            }
            if (allAreSet(oldState, NEED_UNWRAP)) {
                unwrap(Buffers.EMPTY_BYTE_BUFFER);
            }
            parkNanos(this, duration);
            if (thread.isInterrupted()) {
                throw msg.interruptedIO();
            }
        } finally {
            // always unpark because we cannot know if our awaken was spurious
            if (next != null) unpark(next);
        }
    }

    /**
     * Signals that no inbound data will be read
     * 
     * @throws IOException if an IO exception occurs
     */
    public void closeInbound() throws IOException {
        int old = setFlags(READ_SHUT_DOWN);
        boolean sentCloseMessage = true;
        try {
            if (allAreSet(old, WRITE_SHUT_DOWN) && !allAreSet(old, WRITE_COMPLETE)) {
                synchronized (getWrapLock()) {
                    sentCloseMessage = wrapCloseMessage() && flush();
                }
            }
            if (allAreSet(old, WRITE_COMPLETE) && sentCloseMessage) {
                closeEngine();
            }
        } catch (Exception e) {
            //if there is an exception on close we immediately close the engine to make sure buffers are freed
            closeEngine();
            if(e instanceof IOException) {
                throw (IOException)e;
            } else {
                throw (RuntimeException)e;
            }
        }
    }

    /**
     * Indicates if inbound is closed.
     * 
     * @throws IOException if an IO exception occurs
     */
    public boolean isInboundClosed() {
        return allAreSet(state, READ_SHUT_DOWN);
    }

    /**
     * Indicates if engine is closed.
     * 
     */
    public boolean isClosed() {
        return allAreSet(state, ENGINE_CLOSED);
    }

    /**
     * Block until this engine can unwrap messages. 
     * 
     * @throws IOException if an IO exception occurs during await
     */
    public void awaitCanUnwrap() throws IOException {
        int oldState = state;
        if (anyAreSet(oldState, READ_SHUT_DOWN) || ! anyAreSet(oldState, NEED_WRAP)) {
            return;
        }
        final Thread thread = currentThread();
        final Thread next = readWaiterUpdater.getAndSet(this, thread);
        try {
            if (anyAreSet(oldState = state, READ_SHUT_DOWN)) {
                return;
            }
            if (allAreSet(oldState, NEED_WRAP)) {
                wrap(Buffers.EMPTY_BYTE_BUFFER);
            }
            park(this);
            if (thread.isInterrupted()) {
                throw msg.interruptedIO();
            }
        } finally {
            // always unpark because we cannot know if our awaken was spurious
            if (next != null) unpark(next);
        }
    }

    /**
     * Block until this engine can unwrap messages. 
     * 
     * @param time     timeout for blocking
     * @param timeUnit timeout unit
     * @throws IOException if an IO exception occurs during await
     */
    public void awaitCanUnwrap(long time, TimeUnit timeUnit) throws IOException {
        int oldState = state;
        if (anyAreSet(oldState, READ_SHUT_DOWN) || ! anyAreSet(oldState, NEED_WRAP)) {
            return;
        }
        final Thread thread = currentThread();
        final Thread next = readWaiterUpdater.getAndSet(this, thread);
        long duration = timeUnit.toNanos(time);
        try {
            if (anyAreSet(oldState = state,  READ_SHUT_DOWN)) {
                return;
            }
            if (allAreSet(oldState, NEED_WRAP)) {
                wrap(Buffers.EMPTY_BYTE_BUFFER);
            }
            parkNanos(this, duration);
            if (thread.isInterrupted()) {
                throw msg.interruptedIO();
            }
        } finally {
            // always unpark because we cannot know if our awaken was spurious
            if (next != null) unpark(next);
        }
    }

    public boolean isFirstHandshake() {
        return allAreSet(state, FIRST_HANDSHAKE);
    }

    SSLEngine getEngine() {
        return engine;
    }

    /**
     * Indicate that the engine will not be able unwrap before a successful wrap is performed.
     */
    private void needWrap() {
        setFlags(NEED_WRAP);
    }

    /**
     * Indicate if the engine can unwrap.
     */
    private boolean isWrapNeeded() {
        return allAreSet(state, NEED_WRAP);
    }

    /**
     * Indicate that the engine no longer requires a successful wrap to proceed with unwrap operations.
     */
    private void clearNeedWrap() {
        clearFlags(NEED_WRAP);
    }

    /**
     * Indicate that the engine will not be able wrap before a successful unwrap is performed.
     */
    private void needUnwrap() {
        setFlags(NEED_UNWRAP);
    }

    /**
     * Indicate if the engine can wrap.
     */
    private boolean isUnwrapNeeded() {
        return allAreSet(state, NEED_UNWRAP);
    }

    /**
     * Indicates that even though there is data available there is not enough to form a complete packet
     */
    private boolean isUnderflow() {
        return allAreSet(state, BUFFER_UNDERFLOW);
    }

    /**
     * Indicate that the engine no longer requires a successful unwrap to proceed with wrap operations.
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

    public boolean isDataAvailable() {
        synchronized (getUnwrapLock()) {
            try {
                return readBuffer.position() > 0 || (receiveBuffer.hasRemaining() && !isUnderflow());
            } catch (IllegalStateException ignored) {
                return false;
            }
        }
    }

    private final ByteBuffer getSendBuffer() {
        return expandedSendBuffer != null? expandedSendBuffer : sendBuffer;
    }
}
