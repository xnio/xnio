/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2013 Red Hat, Inc. and/or its affiliates.
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

import static java.lang.Math.max;
import static java.lang.Thread.currentThread;
import static org.xnio.Bits.*;
import static org.xnio._private.Messages.msg;

import java.io.EOFException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;

import org.xnio.Buffers;
import org.xnio.Pool;
import org.xnio.Pooled;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.ConduitReadableByteChannel;
import org.xnio.conduits.ConduitWritableByteChannel;
import org.xnio.conduits.Conduits;
import org.xnio.conduits.ReadReadyHandler;
import org.xnio.conduits.StreamSinkConduit;
import org.xnio.conduits.StreamSourceConduit;
import org.xnio.conduits.WriteReadyHandler;

final class JsseStreamConduit implements StreamSourceConduit, StreamSinkConduit, Runnable {

    private static final boolean TRACE_SSL = true;

    //================================================================
    //
    // Immutable state
    //
    //================================================================

    private final JsseSslConnection connection;
    private final SSLEngine engine;
    private final StreamSourceConduit sourceConduit;
    private final StreamSinkConduit sinkConduit;
    /** The buffer into which incoming SSL data is written. */
    private final Pooled<ByteBuffer> receiveBuffer;
    /** The buffer from which outbound SSL data is sent. */
    private final Pooled<ByteBuffer> sendBuffer;
    /** The buffer into which inbound clear data is written. */
    private final Pooled<ByteBuffer> readBuffer;

    //================================================================
    //
    // Mutable state
    //
    //================================================================

    // always inline tasks, for now
    private int state = FLAG_INLINE_TASKS;

    // tasks counter - protected by {@code this}
    private int tasks;

    private ReadReadyHandler readReadyHandler;
    private WriteReadyHandler writeReadyHandler;

    //================================================================
    //
    // Constructors
    //
    //================================================================

    JsseStreamConduit(final JsseSslConnection connection, final SSLEngine engine, final StreamSourceConduit sourceConduit, final StreamSinkConduit sinkConduit, final Pool<ByteBuffer> socketBufferPool, final Pool<ByteBuffer> applicationBufferPool) {
        Pooled<ByteBuffer> receiveBuffer;
        Pooled<ByteBuffer> sendBuffer;
        Pooled<ByteBuffer> readBuffer;
        boolean ok = false;
        final SSLSession session = engine.getSession();
        final int packetBufferSize = session.getPacketBufferSize();
        receiveBuffer = socketBufferPool.allocate();
        try {
            receiveBuffer.getResource().flip();
            sendBuffer = socketBufferPool.allocate();
            try {
                if (receiveBuffer.getResource().capacity() < packetBufferSize || sendBuffer.getResource().capacity() < packetBufferSize) {
                    throw msg.socketBufferTooSmall();
                }
                final int applicationBufferSize = session.getApplicationBufferSize();
                readBuffer = applicationBufferPool.allocate();
                try {
                    if (readBuffer.getResource().capacity() < applicationBufferSize) {
                        throw msg.appBufferTooSmall();
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
        this.receiveBuffer = receiveBuffer;
        this.sendBuffer = sendBuffer;
        this.readBuffer = readBuffer;
        receiveBuffer.getResource().clear().limit(0);
        if (sourceConduit.getReadThread() != sinkConduit.getWriteThread()) {
            throw new IllegalArgumentException("Source and sink thread mismatch");
        }
        this.connection = connection;
        this.engine = engine;
        this.sourceConduit = sourceConduit;
        this.sinkConduit = sinkConduit;
        sourceConduit.setReadReadyHandler(readReady);
        sinkConduit.setWriteReadyHandler(writeReady);
    }

    //================================================================
    //
    // Flags
    //
    //================================================================

    // global flags

    //                                               global-_write----_read----;
    /** TLS is enabled */
    private static final int FLAG_TLS              = 0b00001_000000000_00000000;
    /** Run tasks immediately rather than in the worker */
    private static final int FLAG_INLINE_TASKS     = 0b00010_000000000_00000000;
    /** Set when task is queued; cleared when it runs */
    private static final int FLAG_TASK_QUEUED      = 0b00100_000000000_00000000;
    /** Set when the engine needs the result of a delegated task to continue */
    private static final int FLAG_NEED_ENGINE_TASK = 0b01000_000000000_00000000;
    /** we need a flush to proceed with handshake */
    private static final int FLAG_FLUSH_NEEDED     = 0b10000_000000000_00000000;

    // read state flags

    /** shutdownReads() was called upstream */
    private static final int READ_FLAG_SHUTDOWN    = 0b00000_000000000_00000001;
    /** -1 was read */
    private static final int READ_FLAG_EOF         = 0b00000_000000000_00000010;
    /** resumeReads() called */
    private static final int READ_FLAG_RESUMED     = 0b00000_000000000_00000100;
    /** upstream resumeReads() called */
    private static final int READ_FLAG_UP_RESUMED  = 0b00000_000000000_00001000;
    /** wakeupReads() called */
    private static final int READ_FLAG_WAKEUP      = 0b00000_000000000_00010000;
    /** user read handler should be called if resumed */
    private static final int READ_FLAG_READY       = 0b00000_000000000_00100000;
    /** read needs wrap to proceed */
    private static final int READ_FLAG_NEEDS_WRITE = 0b00000_000000000_01000000;

    // write state flags

    /** shutdownWrites() was called */
    private static final int WRITE_FLAG_SHUTDOWN   = 0b00000_000000001_00000000;
    /** send buffer cleared for final wrap after shutdownWrites() */
    private static final int WRITE_FLAG_SHUTDOWN2  = 0b00000_000000010_00000000;
    /** wrapping was completed after shutdownWrites() was called */
    private static final int WRITE_FLAG_SHUTDOWN3  = 0b00000_000000100_00000000;
    /** flush() returned true after shutdown */
    private static final int WRITE_FLAG_FINISHED   = 0b00000_000001000_00000000;
    /** resumeWrites() called */
    private static final int WRITE_FLAG_RESUMED    = 0b00000_000010000_00000000;
    /** upstream resumeWrites() called */
    private static final int WRITE_FLAG_UP_RESUMED = 0b00000_000100000_00000000;
    /** wakeupWrites() called */
    private static final int WRITE_FLAG_WAKEUP     = 0b00000_001000000_00000000;
    /** user write handler should be called if resumed */
    private static final int WRITE_FLAG_READY      = 0b00000_010000000_00000000;
    /** write needs unwrap to proceed */
    private static final int WRITE_FLAG_NEEDS_READ = 0b00000_100000000_00000000;

    public String getStatus() {
        final StringBuilder b = new StringBuilder();
        b.append("General flags:");
        final int state = this.state;
        if (allAreSet(state, FLAG_TLS)) b.append(" TLS");
        if (allAreSet(state, FLAG_INLINE_TASKS)) b.append(" INLINE_TASKS");
        if (allAreSet(state, FLAG_TASK_QUEUED)) b.append(" TASK_QUEUED");
        if (allAreSet(state, FLAG_NEED_ENGINE_TASK)) b.append(" NEED_ENGINE_TASK");
        if (allAreSet(state, FLAG_FLUSH_NEEDED)) b.append(" FLUSH_NEEDED");
        b.append("\nRead flags:");
        if (allAreSet(state, READ_FLAG_SHUTDOWN)) b.append(" SHUTDOWN");
        if (allAreSet(state, READ_FLAG_EOF)) b.append(" EOF");
        if (allAreSet(state, READ_FLAG_RESUMED)) b.append(" RESUMED");
        if (allAreSet(state, READ_FLAG_UP_RESUMED)) b.append(" UP_RESUMED");
        if (allAreSet(state, READ_FLAG_WAKEUP)) b.append(" WAKEUP");
        if (allAreSet(state, READ_FLAG_READY)) b.append(" READY");
        if (allAreSet(state, READ_FLAG_NEEDS_WRITE)) b.append(" NEEDS_WRITE");
        b.append("\nWrite flags:");
        if (allAreSet(state, WRITE_FLAG_SHUTDOWN)) b.append(" SHUTDOWN");
        if (allAreSet(state, WRITE_FLAG_SHUTDOWN2)) b.append(" SHUTDOWN2");
        if (allAreSet(state, WRITE_FLAG_SHUTDOWN3)) b.append(" SHUTDOWN3");
        if (allAreSet(state, WRITE_FLAG_FINISHED)) b.append(" FINISHED");
        if (allAreSet(state, WRITE_FLAG_RESUMED)) b.append(" RESUMED");
        if (allAreSet(state, WRITE_FLAG_UP_RESUMED)) b.append(" UP_RESUMED");
        if (allAreSet(state, WRITE_FLAG_WAKEUP)) b.append(" WAKEUP");
        if (allAreSet(state, WRITE_FLAG_READY)) b.append(" READY");
        if (allAreSet(state, WRITE_FLAG_NEEDS_READ)) b.append(" NEEDS_READ");
        b.append('\n');
        return b.toString();
    }

    public String toString() {
        return String.format("JSSE Stream Conduit for %s, status:%n%s", connection, getStatus());
    }

    //================================================================
    //
    // Global API
    //
    //================================================================

    public XnioWorker getWorker() {
        return connection.getIoThread().getWorker();
    }

    public XnioIoThread getReadThread() {
        return connection.getIoThread();
    }

    public XnioIoThread getWriteThread() {
        return connection.getIoThread();
    }

    private final WriteReadyHandler writeReady = new WriteReadyHandler() {

        @Override
        public void forceTermination() {
            if (anyAreClear(state, WRITE_FLAG_FINISHED)) {
                state |= WRITE_FLAG_SHUTDOWN | WRITE_FLAG_SHUTDOWN2 | WRITE_FLAG_SHUTDOWN3 | WRITE_FLAG_FINISHED;
            } 
            final WriteReadyHandler writeReadyHandler = JsseStreamConduit.this.writeReadyHandler;
            if (writeReadyHandler != null) try {
                writeReadyHandler.forceTermination();
            } catch (Throwable ignored) {
            }
        }

        @Override
        public void terminated() {
            if (anyAreClear(state, WRITE_FLAG_FINISHED)) {
                state |= WRITE_FLAG_SHUTDOWN | WRITE_FLAG_SHUTDOWN2 | WRITE_FLAG_SHUTDOWN3 | WRITE_FLAG_FINISHED;
            } 
            final WriteReadyHandler writeReadyHandler = JsseStreamConduit.this.writeReadyHandler;
            if (writeReadyHandler != null) try {
                writeReadyHandler.terminated();
            } catch (Throwable ignored) {
            }
        }

        @Override
        public void writeReady() {
            JsseStreamConduit.this.writeReady();
        }
    };

    private final ReadReadyHandler readReady = new ReadReadyHandler() {

        @Override
        public void forceTermination() {
            if (anyAreClear(state, READ_FLAG_SHUTDOWN)) {
                state |= READ_FLAG_SHUTDOWN;
            }
            final ReadReadyHandler readReadyHandler = JsseStreamConduit.this.readReadyHandler;
            if (readReadyHandler != null) try {
                readReadyHandler.forceTermination();
            } catch (Throwable ignored) {
            }
        }

        @Override
        public void terminated() {
            if (anyAreClear(state, READ_FLAG_SHUTDOWN)) {
                state |= READ_FLAG_SHUTDOWN;
            }
            final ReadReadyHandler readReadyHandler = JsseStreamConduit.this.readReadyHandler;
            if (readReadyHandler != null) try {
                readReadyHandler.terminated();
            } catch (Throwable ignored) {
            }
        }

        @Override
        public void readReady() {
            JsseStreamConduit.this.readReady();
        }
    };

    // non-public

    void beginHandshake() throws IOException {
        final int state = this.state;
        if (anyAreSet(state, READ_FLAG_EOF | WRITE_FLAG_SHUTDOWN)) {
            throw new ClosedChannelException();
        }
        if (allAreClear(state, FLAG_TLS)) {
            this.state = state | FLAG_TLS;
        }
        engine.beginHandshake();
    }

    SSLSession getSslSession() {
        return allAreSet(state, FLAG_TLS) ? engine.getSession() : null;
    }

    SSLEngine getEngine() {
        return engine;
    }

    boolean isTls() {
        return allAreSet(state, FLAG_TLS);
    }

    boolean markTerminated() {
        readBuffer.free();
        receiveBuffer.free();
        sendBuffer.free();
        if (anyAreClear(state, READ_FLAG_SHUTDOWN | WRITE_FLAG_FINISHED)) {
            state |= READ_FLAG_SHUTDOWN | WRITE_FLAG_SHUTDOWN | WRITE_FLAG_SHUTDOWN2 | WRITE_FLAG_SHUTDOWN3 | WRITE_FLAG_FINISHED;
            return true;
        } else {
            return false;
        }
    }

    //================================================================
    //
    // Main run task
    //
    //================================================================

    public void run() {
        assert currentThread() == getWriteThread();
        int state = JsseStreamConduit.this.state & ~FLAG_TASK_QUEUED;
        boolean modify = false;
        try {
            // task(s)
            if (allAreSet(state, FLAG_NEED_ENGINE_TASK)) {
                throw new UnsupportedOperationException();
            }
            // write side
            if (anyAreSet(state, WRITE_FLAG_WAKEUP) || allAreSet(state, WRITE_FLAG_RESUMED | WRITE_FLAG_READY)) {
                final WriteReadyHandler writeReadyHandler = JsseStreamConduit.this.writeReadyHandler;
                if (allAreSet(state, WRITE_FLAG_WAKEUP)) {
                    state = state & ~WRITE_FLAG_WAKEUP | WRITE_FLAG_RESUMED;
                    modify = true;
                }
                if (writeReadyHandler != null) {
                    if (allAreSet(state, WRITE_FLAG_RESUMED)) {
                        try {
                            // save flags -------------------------------+
                            if (modify) {                             // |
                                modify = false;                       // |
                                JsseStreamConduit.this.state = state; // |
                            }                                         // |
                            writeReadyHandler.writeReady();           // |
                        } catch (Throwable ignored) {                 // |
                        } finally {                                   // |
                            // restore flags <---------------------------+
                            // it is OK if this is stale
                            state = JsseStreamConduit.this.state;
                        }
                        // Thread safety notice:
                        //---> We must not modify flags unless read and/or write is still resumed; otherwise, the user might
                        //     be doing something in another thread and we could end up overwriting each others' changes.
                        // level-triggering
                        if (allAreSet(state, WRITE_FLAG_RESUMED)) {
                            if (!allAreSet(state, WRITE_FLAG_READY) && allAreSet(state, WRITE_FLAG_NEEDS_READ) && allAreClear(state, READ_FLAG_UP_RESUMED)) {
                                state |= READ_FLAG_UP_RESUMED;
                                modify = true;
                                sourceConduit.resumeReads();
                            } else if (allAreClear(state, WRITE_FLAG_UP_RESUMED)) {
                                sinkConduit.resumeWrites();
                            }
                        }
                    } else {
                        if (allAreClear(state, READ_FLAG_NEEDS_WRITE | READ_FLAG_RESUMED) && allAreSet(state, WRITE_FLAG_UP_RESUMED)) {
                            state &= ~WRITE_FLAG_UP_RESUMED;
                            modify = true;
                            suspendWrites();
                        }
                    }
                } else {
                    // no handler, we should not be resumed
                    state &= ~WRITE_FLAG_RESUMED;
                    modify = true;
                    if (allAreClear(state, READ_FLAG_NEEDS_WRITE | READ_FLAG_RESUMED) && allAreSet(state, WRITE_FLAG_UP_RESUMED)) {
                        state &= ~WRITE_FLAG_UP_RESUMED;
                        modify = true;
                        suspendWrites();
                    }
                }
            }
            // read side
            if (anyAreSet(state, READ_FLAG_WAKEUP) || allAreSet(state, READ_FLAG_RESUMED | READ_FLAG_READY)) {
                final ReadReadyHandler readReadyHandler = JsseStreamConduit.this.readReadyHandler;
                if (allAreSet(state, READ_FLAG_WAKEUP)) {
                    state = state & ~READ_FLAG_WAKEUP | READ_FLAG_RESUMED;
                    modify = true;
                }
                if (readReadyHandler != null) {
                    if (allAreSet(state, READ_FLAG_RESUMED)) {
                        try {
                            // save flags -------------------------------+
                            if (modify) {                             // |
                                modify = false;                       // |
                                JsseStreamConduit.this.state = state; // |
                            }                                         // |
                            readReadyHandler.readReady();             // |
                        } catch (Throwable ignored) {                 // |
                        } finally {                                   // |
                            // restore flags <---------------------------+
                            // it is OK if this is stale
                            state = JsseStreamConduit.this.state;
                        }
                        // Thread safety notice:
                        //---> We must not modify flags unless read and/or write is still resumed; otherwise, the user might
                        //     be doing something in another thread and we could end up overwriting each others' changes.
                        // level-triggering
                        if (allAreSet(state, READ_FLAG_RESUMED)) {
                            if (allAreSet(state, READ_FLAG_READY)) {
                                if (allAreClear(state, FLAG_TASK_QUEUED)) {
                                    state |= FLAG_TASK_QUEUED;
                                    modify = true;
                                    getReadThread().execute(this);
                                }
                            } else if (allAreSet(state, READ_FLAG_NEEDS_WRITE) && allAreClear(state, WRITE_FLAG_UP_RESUMED)) {
                                state |= WRITE_FLAG_UP_RESUMED;
                                modify = true;
                                sinkConduit.resumeWrites();
                            } else if (allAreClear(state, READ_FLAG_UP_RESUMED)) {
                                sourceConduit.resumeReads();
                            }
                        }
                    } else {
                        if (allAreClear(state, WRITE_FLAG_NEEDS_READ | WRITE_FLAG_RESUMED) && allAreSet(state, READ_FLAG_UP_RESUMED)) {
                            state &= ~READ_FLAG_UP_RESUMED;
                            modify = true;
                            suspendReads();
                        }
                    }
                } else {
                    // no handler, we should not be resumed
                    state &= ~READ_FLAG_RESUMED;
                    modify = true;
                    if (allAreClear(state, WRITE_FLAG_NEEDS_READ | WRITE_FLAG_RESUMED) && allAreSet(state, READ_FLAG_UP_RESUMED)) {
                        state &= ~READ_FLAG_UP_RESUMED;
                        suspendReads();
                    }
                }
            }
        } finally {
            if (modify) {
                JsseStreamConduit.this.state = state;
            }
        }
    }

    //================================================================
    //
    // Ready handlers
    //
    //================================================================


    public void setWriteReadyHandler(final WriteReadyHandler handler) {
        this.writeReadyHandler = handler;
    }

    public void setReadReadyHandler(final ReadReadyHandler handler) {
        this.readReadyHandler = handler;
    }

    /**
     * Called by the upstream conduit when writes are ready.
     */
    public void writeReady() {
        int state = this.state;
        state |= WRITE_FLAG_READY;
        if (allAreSet(state, READ_FLAG_NEEDS_WRITE)) {
            state |= READ_FLAG_READY;
        }
        this.state = state;
        // avoid double-fire
        if (allAreClear(state, FLAG_TASK_QUEUED)) {
            run();
        }
    }

    /**
     * Called by the upstream conduit when reads are ready.
     */
    public void readReady() {
        int state = this.state;
        state |= READ_FLAG_READY;
        if (allAreSet(state, WRITE_FLAG_NEEDS_READ)) {
            state |= WRITE_FLAG_READY;
        }
        this.state = state;
        // avoid double-fire
        if (allAreClear(state, FLAG_TASK_QUEUED)) {
            run();
        }
    }

    //================================================================
    //
    // Resume state management
    //
    //================================================================

    // writes

    public void suspendWrites() {
        int state = this.state;
        try {
            if (allAreSet(state, WRITE_FLAG_RESUMED)) {
                state &= ~WRITE_FLAG_RESUMED;
                if (allAreSet(state, WRITE_FLAG_UP_RESUMED) && allAreClear(state, READ_FLAG_NEEDS_WRITE)) {
                    // upstream writes were resumed but now may be suspended
                    state &= ~WRITE_FLAG_UP_RESUMED;
                    sinkConduit.suspendWrites();
                }
                if (allAreSet(state, READ_FLAG_UP_RESUMED) && allAreClear(state, READ_FLAG_RESUMED)) {
                    // reads were likely resumed because of us; clear it
                    state &= ~READ_FLAG_UP_RESUMED;
                    sourceConduit.suspendReads();
                }
            }
        } finally {
            this.state = state;
        }
    }

    public void resumeWrites() {
        int state = this.state;
        if (allAreClear(state, WRITE_FLAG_RESUMED)) {
            if (allAreSet(state, WRITE_FLAG_FINISHED)) {
                // just re-call the handler one time
                wakeupWrites();
                return;
            }
            try {
                state |= WRITE_FLAG_RESUMED;
                if (allAreSet(state, WRITE_FLAG_READY)) {
                    if (allAreClear(state, FLAG_TASK_QUEUED)) {
                        state |= FLAG_TASK_QUEUED;
                        getReadThread().execute(this);
                    }
                } else if (allAreSet(state, WRITE_FLAG_NEEDS_READ) && allAreClear(state, READ_FLAG_UP_RESUMED)) {
                    // need to resume reads to make this happen
                    state |= READ_FLAG_UP_RESUMED;
                    sourceConduit.resumeReads();
                } else if (allAreClear(state, WRITE_FLAG_UP_RESUMED)) {
                    // upstream writes were not resumed
                    state |= WRITE_FLAG_UP_RESUMED;
                    sinkConduit.resumeWrites();
                }
            } finally {
                this.state = state;
            }
        }
    }

    public void wakeupWrites() {
        final int state = this.state;
        if (allAreClear(state, WRITE_FLAG_WAKEUP)) {
            if (allAreClear(state, FLAG_TASK_QUEUED)) {
                this.state = state | WRITE_FLAG_WAKEUP | FLAG_TASK_QUEUED;
                getReadThread().execute(this);
            } else {
                this.state = state | WRITE_FLAG_WAKEUP;
            }
        }
    }

    public void terminateWrites() throws IOException {
        int state = this.state;
        if (allAreClear(state, WRITE_FLAG_SHUTDOWN)) {
            this.state = state | WRITE_FLAG_SHUTDOWN;
            if (allAreSet(state, FLAG_TLS)) try {
                engine.closeOutbound();
                performIO(IO_GOAL_FLUSH, NO_BUFFERS, 0, 0, NO_BUFFERS, 0, 0);
            } catch (Throwable t) {
                this.state |= WRITE_FLAG_FINISHED;
                try {
                    sinkConduit.truncateWrites();
                } catch (Throwable t2) {
                    t.addSuppressed(t2);
                }
                throw t;
            } else {
                sinkConduit.terminateWrites();
            }
        }
    }

    public void truncateWrites() throws IOException {
        int state = this.state;
        if (allAreClear(state, WRITE_FLAG_SHUTDOWN)) {
            if (allAreSet(state, FLAG_TLS)) try {
                state |= WRITE_FLAG_SHUTDOWN | WRITE_FLAG_SHUTDOWN3 | WRITE_FLAG_FINISHED;
                try {
                    engine.closeOutbound();
                } catch (Throwable t) {
                    try {
                        sinkConduit.truncateWrites();
                    } catch (Throwable t2) {
                        t.addSuppressed(t2);
                    }
                    throw t;
                }
                sinkConduit.truncateWrites();
            } finally {
                this.state = state;
            } else {
                this.state = state | WRITE_FLAG_SHUTDOWN | WRITE_FLAG_SHUTDOWN3 | WRITE_FLAG_FINISHED;
                sinkConduit.truncateWrites();
            }
        }
    }

    // queries

    public boolean isWriteResumed() {
        return anyAreSet(state, WRITE_FLAG_RESUMED | WRITE_FLAG_WAKEUP);
    }

    public boolean isWriteShutdown() {
        return allAreSet(state, WRITE_FLAG_SHUTDOWN);
    }

    // await

    public void awaitWritable() throws IOException {
        int state = this.state;
        while (allAreSet(state, FLAG_NEED_ENGINE_TASK)) {
            synchronized (this) {
                while (tasks != 0) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new InterruptedIOException();
                    }
                }
                state &= ~FLAG_NEED_ENGINE_TASK;
                this.state = state;
            }
        }
        if (allAreClear(state, WRITE_FLAG_READY)) {
            if (allAreSet(state, WRITE_FLAG_NEEDS_READ)) {
                sourceConduit.awaitReadable();
            } else {
                sinkConduit.awaitWritable();
            }
        }
    }

    public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
        int state = this.state;
        long nanos = timeUnit.toNanos(time);
        while (allAreSet(state, FLAG_NEED_ENGINE_TASK)) {
            synchronized (this) {
                long start = System.nanoTime();
                while (tasks != 0) {
                    try {
                        if (nanos <= 0) {
                            return;
                        }
                        wait(nanos / 1_000_000, (int) (nanos % 1_000_000));
                        nanos -= -start + (start = System.nanoTime());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new InterruptedIOException();
                    }
                }
                state &= ~FLAG_NEED_ENGINE_TASK;
                this.state = state;
            }
        }
        if (allAreClear(state, WRITE_FLAG_READY)) {
            if (allAreSet(state, WRITE_FLAG_NEEDS_READ)) {
                sourceConduit.awaitReadable(nanos, TimeUnit.NANOSECONDS);
            } else {
                sinkConduit.awaitWritable(nanos, TimeUnit.NANOSECONDS);
            }
        }
    }

    // reads

    public void suspendReads() {
        int state = this.state;
        try {
            if (allAreSet(state, READ_FLAG_RESUMED)) {
                state &= ~READ_FLAG_RESUMED;
                if (allAreSet(state, READ_FLAG_UP_RESUMED) && allAreClear(state, WRITE_FLAG_NEEDS_READ)) {
                    // upstream reads were resumed but now may be suspended
                    state &= ~READ_FLAG_UP_RESUMED;
                    sourceConduit.suspendReads();
                }
                if (allAreSet(state, WRITE_FLAG_UP_RESUMED) && allAreClear(state, WRITE_FLAG_RESUMED)) {
                    // writes were likely resumed because of us; clear it
                    state &= ~WRITE_FLAG_UP_RESUMED;
                    sinkConduit.suspendWrites();
                }
            }
        } finally {
            this.state = state;
        }
    }

    public void resumeReads() {
        int state = this.state;
        if (allAreClear(state, READ_FLAG_RESUMED)) try {
            state |= READ_FLAG_RESUMED;
            // in the absence of a writer, we need to wake up the reader to make sure that handshake will take place
            // (the read listener could never be called if this is the side that is supposed to make the first wrap
            // for handshake; if that happens, this side of the connection would starve)
            if (allAreClear(state, WRITE_FLAG_RESUMED)) {
                state |= READ_FLAG_READY;
            }
            if (allAreSet(state, READ_FLAG_READY)) {
                if (allAreClear(state, FLAG_TASK_QUEUED)) {
                    state |= FLAG_TASK_QUEUED;
                    getReadThread().execute(this);
                }
            } else if (allAreSet(state, READ_FLAG_NEEDS_WRITE) && allAreClear(state, WRITE_FLAG_UP_RESUMED)) {
                // need to resume writes to make this happen
                state |= WRITE_FLAG_UP_RESUMED;
                sinkConduit.resumeWrites();
            } else if (allAreClear(state, READ_FLAG_UP_RESUMED)) {
                // upstream reads were not resumed
                state |= READ_FLAG_UP_RESUMED;
                sourceConduit.resumeReads();
            }
        } finally {
            this.state = state;
        }
    }

    public void wakeupReads() {
        final int state = this.state;
        if (allAreClear(state, READ_FLAG_WAKEUP)) {
            if (allAreClear(state, FLAG_TASK_QUEUED)) {
                this.state = state | READ_FLAG_WAKEUP | FLAG_TASK_QUEUED;
                getReadThread().execute(this);
            } else {
                this.state = state | READ_FLAG_WAKEUP;
            }
        }
    }

    public void terminateReads() throws IOException {
        int state = this.state;
        if (allAreClear(state, READ_FLAG_SHUTDOWN)) {
            if (allAreClear(state, FLAG_TLS)) {
                // never fired up TLS in the first place
                sourceConduit.terminateReads();
            } else {
                // indicate that the user doesn't want any more data
                this.state = state | READ_FLAG_SHUTDOWN;
                if (allAreClear(state, READ_FLAG_EOF)) {
                    performIO(IO_GOAL_FLUSH, NO_BUFFERS, 0, 0, NO_BUFFERS, 0, 0);
                    if (allAreSet(state,  WRITE_FLAG_NEEDS_READ)) {
                        if (allAreClear(state, READ_FLAG_EOF)) {
                            return;
                        }
                    }
                    if (!engine.isInboundDone()) {
                        engine.closeInbound();
                    }
                    performIO(IO_GOAL_READ, NO_BUFFERS, 0, 0, NO_BUFFERS, 0, 0);
                }
                if (allAreClear(this.state, READ_FLAG_EOF) || this.receiveBuffer.getResource().hasRemaining()) {
                    // potentially unread data :(
                    final EOFException exception = msg.connectionClosedEarly();
                    try {
                        sourceConduit.terminateReads();
                    } catch (IOException e) {
                        exception.addSuppressed(e);
                    }
                    throw exception;
                } else {
                    sourceConduit.terminateReads();
                }
            }
        }
    }

    // queries

    public boolean isReadResumed() {
        return anyAreSet(state, READ_FLAG_RESUMED | READ_FLAG_WAKEUP);
    }

    public boolean isReadShutdown() {
        return allAreSet(state, READ_FLAG_SHUTDOWN);
    }

    // await

    public void awaitReadable() throws IOException {
        int state = this.state;
        while (allAreSet(state, FLAG_NEED_ENGINE_TASK)) {
            synchronized (this) {
                while (tasks != 0) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new InterruptedIOException();
                    }
                }
                state &= ~FLAG_NEED_ENGINE_TASK;
                this.state = state;
            }
        }
        if (allAreClear(state, READ_FLAG_READY)) {
            if (allAreSet(state, READ_FLAG_NEEDS_WRITE)) {
                sinkConduit.awaitWritable();
            } else {
                sourceConduit.awaitReadable();
            }
        }
    }

    public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
        int state = this.state;
        long nanos = timeUnit.toNanos(time);
        while (allAreSet(state, FLAG_NEED_ENGINE_TASK)) {
            synchronized (this) {
                long start = System.nanoTime();
                while (tasks != 0) {
                    try {
                        if (nanos <= 0) {
                            return;
                        }
                        wait(nanos / 1_000_000, (int) (nanos % 1_000_000));
                        nanos -= -start + (start = System.nanoTime());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new InterruptedIOException();
                    }
                }
                state &= ~FLAG_NEED_ENGINE_TASK;
                this.state = state;
            }
        }
        if (allAreClear(state, READ_FLAG_READY)) {
            if (allAreSet(state, READ_FLAG_NEEDS_WRITE)) {
                sinkConduit.awaitWritable(nanos, TimeUnit.NANOSECONDS);
            } else {
                sourceConduit.awaitReadable(nanos, TimeUnit.NANOSECONDS);
            }
        }
    }

    //================================================================
    //
    // I/O methods - read
    //
    //================================================================

    public int read(final ByteBuffer dst) throws IOException {
        final int state = this.state;
        if (anyAreSet(state, READ_FLAG_SHUTDOWN)) {
            return -1;
        }
        if (anyAreSet(state, READ_FLAG_EOF)) {
            // read data
            if (readBuffer.getResource().position() > 0) {
                final ByteBuffer readBufferResource = readBuffer.getResource();
                readBufferResource.flip();
                try {
                    if (TRACE_SSL) msg.tracef("TLS copy unwrapped data from %s to %s", Buffers.debugString(readBufferResource), Buffers.debugString(dst));
                    return Buffers.copy(dst, readBufferResource);
                } finally {
                    readBufferResource.compact();
                }
            }
            return -1;
            
        } else if (allAreClear(state, FLAG_TLS)) {
            int res = sourceConduit.read(dst);
            if (res == 0) {
                if (allAreSet(state, READ_FLAG_READY)) {
                    this.state = state & ~READ_FLAG_READY;
                }
            } else if (res == -1) {
                this.state = state | READ_FLAG_EOF & ~READ_FLAG_READY;
            }
            return res;
        } else {
            // regular TLS time
            return (int) performIO(IO_GOAL_READ, NO_BUFFERS, 0, 0, new ByteBuffer[] { dst }, 0, 1);
        }
    }

    public long read(final ByteBuffer[] dsts, final int offs, final int len) throws IOException {
        final int state = this.state;
        if (anyAreSet(state, READ_FLAG_SHUTDOWN)) {
            return -1;
        } else if (anyAreSet(state, READ_FLAG_EOF)){
            if (readBuffer.getResource().position() > 0) {
                final ByteBuffer readBufferResource = readBuffer.getResource();
                readBufferResource.flip();
                try {
                    if (TRACE_SSL) msg.tracef("TLS copy unwrapped data from %s to %s", Buffers.debugString(readBufferResource), Buffers.debugString(dsts, offs, len));
                    return Buffers.copy(dsts, offs, len, readBufferResource);
                } finally {
                    readBufferResource.compact();
                }
            }
            return -1;
        } else if (allAreClear(state, FLAG_TLS)) {
            long res = sourceConduit.read(dsts, offs, len);
            if (res == 0) {
                if (allAreSet(state, READ_FLAG_READY)) {
                    this.state = state & ~READ_FLAG_READY;
                }
            } else if (res == -1) {
                this.state = state | READ_FLAG_EOF & ~READ_FLAG_READY;
            }
            return res;
        } else {
            // regular TLS time
            return performIO(IO_GOAL_READ, NO_BUFFERS, 0, 0, dsts, offs, len);
        }
    }

    public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        if (allAreClear(state, FLAG_TLS)) {
            return sourceConduit.transferTo(position, count, target);
        } else {
            return target.transferFrom(new ConduitReadableByteChannel(this), position, count);
        }
    }

    public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
        if (allAreClear(state, FLAG_TLS)) {
            return sourceConduit.transferTo(count, throughBuffer, target);
        } else {
            // todo - transfer via application read buffer
            return Conduits.transfer(this, count, throughBuffer, target);
        }
    }

    //================================================================
    //
    // I/O methods - write
    //
    //================================================================

    public int write(final ByteBuffer src) throws IOException {
        if (allAreSet(state, WRITE_FLAG_SHUTDOWN)) {
            throw new ClosedChannelException();
        }
        if (allAreClear(state, FLAG_TLS)) {
            return sinkConduit.write(src);
        } else {
            // this is *exactly* what SSLEngine does internally anyway
            return (int) write(new ByteBuffer[] { src }, 0, 1);
        }
    }

    public int writeFinal(final ByteBuffer src) throws IOException {
        if (allAreSet(state, WRITE_FLAG_SHUTDOWN)) {
            throw new ClosedChannelException();
        }
        if (allAreClear(state, FLAG_TLS)) {
            return sinkConduit.writeFinal(src);
        } else {
            // this is *exactly* what SSLEngine does internally anyway
            return (int) writeFinal(new ByteBuffer[] { src }, 0, 1);
        }
    }

    public long write(final ByteBuffer[] srcs, final int offs, final int len) throws IOException {
        if (allAreSet(state, WRITE_FLAG_SHUTDOWN)) {
            throw new ClosedChannelException();
        }
        if (allAreClear(state, FLAG_TLS)) {
            return sinkConduit.write(srcs, offs, len);
        } else {
            final long r1 = Buffers.remaining(srcs, offs, len);
            performIO(IO_GOAL_WRITE, srcs, offs, len, NO_BUFFERS, 0, 0);
            return (r1 - Buffers.remaining(srcs, offs, len));
        }
    }

    public long writeFinal(final ByteBuffer[] srcs, final int offs, final int len) throws IOException {
        if (allAreSet(state, WRITE_FLAG_SHUTDOWN)) {
            throw new ClosedChannelException();
        }
        if (allAreClear(state, FLAG_TLS)) {
            return sinkConduit.writeFinal(srcs, offs, len);
        } else {
            final long r1 = Buffers.remaining(srcs, offs, len);
            performIO(IO_GOAL_WRITE_FINAL, srcs, offs, len, NO_BUFFERS, 0, 0);
            return (r1 - Buffers.remaining(srcs, offs, len));
        }
    }

    public boolean flush() throws IOException {
        int state = this.state;
        if (allAreSet(state, WRITE_FLAG_FINISHED)) {
            return true;
        } else if (allAreSet(state, WRITE_FLAG_SHUTDOWN3)) {
            // just waiting for upstream flush
            if (sinkConduit.flush()) {
                this.state = state | WRITE_FLAG_FINISHED;
                return true;
            } else {
                return false;
            }
        } else if (allAreClear(state, FLAG_TLS)) {
            final boolean flushed = sinkConduit.flush();
            if (allAreSet(state, WRITE_FLAG_SHUTDOWN) && flushed) {
                this.state = state | WRITE_FLAG_SHUTDOWN2 | WRITE_FLAG_SHUTDOWN3 | WRITE_FLAG_FINISHED;
            }
            return flushed;
        } else if (allAreSet(state, WRITE_FLAG_SHUTDOWN)) {
            // waiting for final wrap, then upstream shutdown & flush
            return performIO(IO_GOAL_FLUSH, NO_BUFFERS, 0, 0, NO_BUFFERS, 0, 0) != 0L;
        } else {
            // regular flush
            return performIO(IO_GOAL_FLUSH, NO_BUFFERS, 0, 0, NO_BUFFERS, 0, 0) != 0L;
        }
    }

    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        if (allAreClear(state, FLAG_TLS)) {
            return sinkConduit.transferFrom(src, position, count);
        } else {
            return src.transferTo(position, count, new ConduitWritableByteChannel(this));
        }
    }

    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        if (allAreClear(state, FLAG_TLS)) {
            return sinkConduit.transferFrom(source, count, throughBuffer);
        } else {
            return Conduits.transfer(source, count, throughBuffer, this);
        }
    }

    //================================================================
    //
    // Main SSLEngine I/O loop
    //
    //================================================================

    private static final ByteBuffer[] NO_BUFFERS = new ByteBuffer[0];

    private static final int IO_GOAL_READ          = 0;
    private static final int IO_GOAL_WRITE         = 1;
    private static final int IO_GOAL_FLUSH         = 2;
    private static final int IO_GOAL_WRITE_FINAL   = 3;

    private static long actualIOResult(final long xfer, final int goal, final boolean flushed, final boolean eof) {
        final long result = goal == IO_GOAL_FLUSH && flushed ? 1L : goal == IO_GOAL_READ && eof && xfer == 0L ? -1L : xfer;
        if (TRACE_SSL) msg.tracef("returned TLS result %d", result);
        return result;
    }

    private static String decodeGoal(int goal) {
        switch (goal) {
            case 0: return "READ";
            case 1: return "WRITE";
            case 2: return "FLUSH";
            case 3: return "WRITE_FINAL";
            default: return "UNKNOWN(" + goal + ")";
        }
    }

    private long performIO(final int goal, final ByteBuffer[] srcs, final int srcOff, final int srcLen, final ByteBuffer[] dsts, final int dstOff, final int dstLen) throws IOException {
        if (TRACE_SSL) msg.tracef("performing TLS I/O operation, goal %s, src: %s, dst: %s", decodeGoal(goal), Buffers.debugString(srcs, srcOff, srcLen), Buffers.debugString(dsts, dstOff, dstLen));
        // one of these arrays is empty
        assert srcs == NO_BUFFERS || dsts == NO_BUFFERS;
        int state = this.state;
        // this contradiction should never occur
        assert ! allAreSet(state, READ_FLAG_NEEDS_WRITE | WRITE_FLAG_NEEDS_READ);
        if (allAreSet(state, FLAG_NEED_ENGINE_TASK)) {
            // can't do anything until the task is done
            return 0L;
        }
        final SSLEngine engine = this.engine;
        final ByteBuffer sendBuffer = this.sendBuffer.getResource();
        final ByteBuffer receiveBuffer = this.receiveBuffer.getResource();
        final ByteBuffer readBuffer = this.readBuffer.getResource();
        // unwrap into our read buffer if necessary to avoid underflow problems
        final ByteBuffer[] realDsts = Arrays.copyOfRange(dsts, dstOff, dstLen + 1);
        realDsts[dstLen] = readBuffer;

        long remaining = max(Buffers.remaining(srcs, srcOff, srcLen), Buffers.remaining(dsts, dstOff, dstLen));
        boolean wrap = goal == IO_GOAL_READ ? anyAreSet(state, READ_FLAG_NEEDS_WRITE | FLAG_FLUSH_NEEDED) : allAreSet(state, FLAG_FLUSH_NEEDED) || allAreClear(state, WRITE_FLAG_NEEDS_READ);
        boolean unwrap = !wrap;
        boolean flushed = false;
        boolean eof = false;
        boolean readBlocked = false;
        boolean writeBlocked = false;
        boolean copiedUnwrappedBytes = false;
        boolean wakeupReads = false;
        SSLEngineResult result;
        SSLEngineResult.HandshakeStatus handshakeStatus;
        int rv = 0;
        // amount of data moved to/from the user buffers (remember only zero or one of srcs/dsts can be given)
        long xfer = 0L;
        if (TRACE_SSL) msg.trace("TLS perform IO");
        try {
            for (;;) {
                if (TRACE_SSL) msg.trace("TLS begin IO operation");
                if (goal == IO_GOAL_READ && remaining > 0 && readBuffer.position() > 0) {
                    // read data
                    readBuffer.flip();
                    try {
                        if (TRACE_SSL) msg.tracef("TLS copy unwrapped data from %s to %s", Buffers.debugString(readBuffer), Buffers.debugString(dsts, dstOff, dstLen));
                        rv = Buffers.copy(dsts, dstOff, dstLen, readBuffer);
                    } finally {
                        readBuffer.compact();
                    }
                    if (rv > 0) {
                        copiedUnwrappedBytes = true;
                        xfer += rv;
                        if ((remaining -= rv) == 0L) {
                            return actualIOResult(xfer, goal, flushed, eof);
                        }
                    }
                }
                assert ! (wrap && unwrap);
                if (wrap) {
                    if (TRACE_SSL) msg.tracef("TLS wrap from %s to %s", Buffers.debugString(srcs, srcOff, srcLen), Buffers.debugString(sendBuffer));
                    result = engine.wrap(srcs, srcOff, srcLen, sendBuffer);
                    WRAP_RESULT: switch (result.getStatus()) {
                        case BUFFER_UNDERFLOW: {
                            assert result.bytesConsumed() == 0;
                            assert result.bytesProduced() == 0;
                            // move directly to handshake result
                            if (TRACE_SSL) msg.trace("TLS wrap operation UNDERFLOW");
                            break;
                        }
                        case BUFFER_OVERFLOW: {
                            assert result.bytesConsumed() == 0;
                            assert result.bytesProduced() == 0;
                            if (TRACE_SSL) msg.trace("TLS wrap operation OVERFLOW");
                            if (sendBuffer.position() == 0) {
                                // our buffer is empty, and definitely large enough, so just throw an exception
                                throw msg.wrongBufferExpansion();
                            } else {
                                // there's some data in there, so send it first
                                sendBuffer.flip();
                                try {
                                    while (sendBuffer.hasRemaining()) {
                                        if (TRACE_SSL) msg.tracef("TLS wrap operation send %s", Buffers.debugString(sendBuffer));
                                        final int res = sinkConduit.write(sendBuffer);
                                        if (res == 0) {
                                            writeBlocked = true;
                                            state &= ~WRITE_FLAG_READY;
                                            // not flushed
                                            assert goal != IO_GOAL_FLUSH || xfer == 0L;
                                            flushed = false;
                                            wrap = false;
                                            break WRAP_RESULT;
                                        }
                                    }
                                } finally {
                                    sendBuffer.compact();
                                }
                                if (goal == IO_GOAL_FLUSH || allAreSet(state, FLAG_FLUSH_NEEDED)) {
                                    if (flushed = sinkConduit.flush()) {
                                        state &= ~FLAG_FLUSH_NEEDED;
                                    }
                                }
                                if (goal == IO_GOAL_FLUSH && allAreSet(state, WRITE_FLAG_SHUTDOWN)) {
                                    state |= WRITE_FLAG_SHUTDOWN2;
                                }
                            }
                            // move to handshake result
                            break;
                        }
                        case CLOSED: {
                            if (TRACE_SSL) msg.trace("TLS wrap operation CLOSED");
                            if (allAreClear(state, WRITE_FLAG_SHUTDOWN) && result.bytesProduced() == 0) {
                                // attempted write after shutdown (should not be possible)
                                state &= ~(WRITE_FLAG_NEEDS_READ | READ_FLAG_NEEDS_WRITE);
                                state |= WRITE_FLAG_SHUTDOWN | WRITE_FLAG_SHUTDOWN2 | WRITE_FLAG_SHUTDOWN3 | WRITE_FLAG_FINISHED;
                                final ClosedChannelException exception = new ClosedChannelException();
                                try {
                                    sinkConduit.truncateWrites();
                                } catch (IOException e) {
                                    exception.addSuppressed(e);
                                }
                                throw exception;
                            }
                            if (allAreSet(state, WRITE_FLAG_SHUTDOWN2)) {
                                state |= WRITE_FLAG_SHUTDOWN3;
                            }
                            // else treat as OK
                            // fall thru!!!
                        }
                        case OK: {
                            if (TRACE_SSL) msg.tracef("TLS wrap operation OK consumed: %d produced: %d", result.bytesConsumed(), result.bytesProduced());
                            state &= ~(WRITE_FLAG_NEEDS_READ | READ_FLAG_NEEDS_WRITE);
                            final int consumed = result.bytesConsumed();
                            if (goal == IO_GOAL_READ) {
                                // sources should be empty
                                assert consumed == 0;
                                wrap = false;
                                unwrap = true;
                            } else {
                                if (consumed > 0 || remaining == 0) {
                                    // if consumed > 0 then remaining must also be 0
                                    assert remaining != 0 || consumed == 0;
                                    // we've returned some data, or else there's nothing to consume
                                    wrap = false;
                                }
                                xfer += consumed;
                                remaining -= consumed;
                            }
                            // try to send the generated bytes
                            sendBuffer.flip();
                            try {
                                flushed = false;
                                while (sendBuffer.hasRemaining()) {
                                    final int res = allAreSet(state, WRITE_FLAG_SHUTDOWN3) ? sinkConduit.writeFinal(sendBuffer) : sinkConduit.write(sendBuffer);
                                    if (res == 0) {
                                        // not flushed; probably can't wrap any more anyway
                                        writeBlocked = true;
                                        wrap = false;
                                        break;
                                    }
                                }
                            } finally {
                                sendBuffer.compact();
                            }
                            // make sure we *really* flushed
                            if (sendBuffer.position() == 0) {
                                if (goal == IO_GOAL_FLUSH || allAreSet(state, FLAG_FLUSH_NEEDED)) {
                                    if (flushed = sinkConduit.flush()) {
                                        state &= ~FLAG_FLUSH_NEEDED;
                                    }
                                }
                                if (allAreSet(state, WRITE_FLAG_SHUTDOWN)) {
                                    if (allAreClear(state, WRITE_FLAG_SHUTDOWN2)) {
                                        // send buffer should already be cleared, thanks to above write block
                                        assert sendBuffer.position() == 0;
                                        state |= WRITE_FLAG_SHUTDOWN2;
                                        if (result.getHandshakeStatus() == HandshakeStatus.NOT_HANDSHAKING) {
                                            // make sure we get to shutdown 3 right away if we are no longer handshaking
                                            state |= WRITE_FLAG_SHUTDOWN3;
                                        }
                                    }
                                    if (allAreSet(state, WRITE_FLAG_SHUTDOWN3)) {
                                        // the last wrap has occurred, and writes were shut down; we just need last flush
                                        if (goal == IO_GOAL_FLUSH || sinkConduit.flush()) {
                                            state |= WRITE_FLAG_FINISHED;
                                        }
                                        sinkConduit.terminateWrites();
                                    }
                                }
                            }
                            // move to handshake result
                            break;
                        }
                        default: {
                            throw msg.unexpectedWrapResult(result.getStatus());
                        }
                    }
                } else if (unwrap) {
                    if (TRACE_SSL) msg.tracef("TLS unwrap from %s to %s", Buffers.debugString(receiveBuffer), Buffers.debugString(realDsts, 0, dstLen + 1));
                    // use dstLen + 1 so that any leftovers are unwrapped into our read buffer to avoid underflow
                    // * offset is 0 because realDsts is a copyOfRange of the original dsts with one extra buf at the end
                    assert realDsts.length == 1 || realDsts[0] == dsts[dstOff];
                    assert realDsts[dstLen] == readBuffer;
                    // user-visible counts
                    final long preRem = Buffers.remaining(dsts, dstOff, dstLen);
                    result = engine.unwrap(receiveBuffer, realDsts, 0, dstLen + 1);
                    final long userProduced = preRem - Buffers.remaining(dsts, dstOff, dstLen);
                    switch (result.getStatus()) {
                        case BUFFER_OVERFLOW: {
                            assert result.bytesConsumed() == 0;
                            assert result.bytesProduced() == 0;
                            assert userProduced == 0;
                            if (TRACE_SSL) msg.trace("TLS unwrap operation OVERFLOW");
                            // not enough space in destination buffer; caller should consume the data they have first
                            if (!copiedUnwrappedBytes) { // realDsts is too small for message to unwrap 
                                return actualIOResult(xfer, goal, flushed, eof);
                            }
                            unwrap = false;
                            break;
                        }
                        case BUFFER_UNDERFLOW: {
                            assert result.bytesConsumed() == 0;
                            assert result.bytesProduced() == 0;
                            assert userProduced == 0;
                            if (TRACE_SSL) msg.trace("TLS unwrap operation UNDERFLOW");
                            // fill the rest of the buffer, then retry!
                            receiveBuffer.compact();
                            try {
                                int res;
                                res = sourceConduit.read(receiveBuffer);
                                if (TRACE_SSL) msg.tracef("TLS unwrap operation read %s", Buffers.debugString(receiveBuffer));
                                if (res == -1) {
                                    state &= ~READ_FLAG_READY;
                                    engine.closeInbound();
                                } else if (res == 0) {
                                    readBlocked = true;
                                    state &= ~READ_FLAG_READY;
                                    unwrap = false;
                                } else if (receiveBuffer.hasRemaining()) {
                                    do {
                                        // try more reads just in case
                                        res = sourceConduit.read(receiveBuffer);
                                    } while (res > 0 && receiveBuffer.hasRemaining());
                                    if (res == 0) {
                                        state &= ~READ_FLAG_READY;
                                    }
                                }
                            } finally {
                                receiveBuffer.flip();
                            }
                            // we should now be able to unwrap.
                            break;
                        }
                        case CLOSED: {
                            if (TRACE_SSL) msg.trace("TLS unwrap operation CLOSED");
                            state &= ~(WRITE_FLAG_NEEDS_READ | READ_FLAG_NEEDS_WRITE);
                            if (goal == IO_GOAL_READ) {
                                xfer += userProduced;
                                remaining -= userProduced;
                                // if we are performing any action that not read, we don't want to disable read handler yet
                                // (the handler needs to read -1 before that)
                                state = state & ~READ_FLAG_READY | READ_FLAG_EOF;
                            } else {
                                wakeupReads = true;
                            }
                            // if unwrap processed any data, it should return bytes produced instead of -1
                            eof = true;
                            unwrap = false;
                            if (goal == IO_GOAL_FLUSH) {
                                wrap = true;
                            }
                            break;
                        }
                        case OK: {
                            if (TRACE_SSL) msg.tracef("TLS unwrap operation OK consumed: %d produced: %d", result.bytesConsumed(), result.bytesProduced());
                            if (allAreClear(state, READ_FLAG_READY)) {
                                // make sure the caller keeps reading until the unwrapped data is consumed
                                state |= READ_FLAG_READY;
                            }
                            state &= ~(WRITE_FLAG_NEEDS_READ | READ_FLAG_NEEDS_WRITE);
                            if (goal == IO_GOAL_READ) {
                                xfer += userProduced;
                                remaining -= userProduced;
                            } else {
                                wrap = true;
                                unwrap = false;
                                if (result.bytesProduced() > 0) {
                                    wakeupReads = true;
                                }
                            }
                            // handshake result
                            break;
                        }
                        default: {
                            throw msg.unexpectedUnwrapResult(result.getStatus());
                        }
                    }
                } else {
                    // done
                    return actualIOResult(xfer, goal, flushed, eof);
                }
                // now handle handshake
                handshakeStatus = result.getHandshakeStatus();
                HS: for (;;) {
                    switch (handshakeStatus) {

                        case FINISHED: {
                            if (TRACE_SSL) msg.trace("TLS handshake FINISHED");
                            connection.invokeHandshakeListener();
                            // try original op again
                            break HS;
                        }
                        case NOT_HANDSHAKING: {
                            // move on to next operation until I/O blocks
                            break HS;
                        }
                        case NEED_TASK: {
                            if (TRACE_SSL) msg.trace("TLS handshake NEED_TASK");
                            if (xfer != 0L) {
                                // only queue a task if the user isn't going to retry an I/O op immediately after
                                return actualIOResult(xfer, goal, flushed, eof);
                            }
                            if (allAreSet(state, FLAG_INLINE_TASKS)) {
                                Runnable task;
                                for (; ; ) {
                                    task = engine.getDelegatedTask();
                                    if (task == null) {
                                        break;
                                    }
                                    try {
                                        task.run();
                                    } catch (Throwable cause) {
                                        throw new SSLException("Delegated task threw an exception", cause);
                                    }
                                }
                                // and that's that; loop again
                                handshakeStatus = engine.getHandshakeStatus();
                                // retry handshake evaluation
                                break;
                            } else {
                                state |= FLAG_NEED_ENGINE_TASK;
                                // await methods or the handler blob will take care of this
                                final ArrayList<Runnable> tasks = new ArrayList<>(4);
                                Runnable task;
                                for (;;) {
                                    task = engine.getDelegatedTask();
                                    if (task != null) {
                                        tasks.add(task);
                                    } else {
                                        break;
                                    }
                                }
                                final int size = tasks.size();
                                synchronized (JsseStreamConduit.this) {
                                    this.tasks = size;
                                }
                                // use indexes to avoid iterator creation (which does the same thing anyway)
                                //noinspection ForLoopReplaceableByForEach
                                for (int i = 0; i < size; i ++) {
                                    getWorker().execute(new TaskWrapper(tasks.get(i)));
                                }
                                return actualIOResult(xfer, goal, flushed, eof);
                            }
                        }
                        case NEED_WRAP: {
                            if (TRACE_SSL) msg.trace("TLS handshake NEED_WRAP");
                            state |= READ_FLAG_NEEDS_WRITE | FLAG_FLUSH_NEEDED;
                            if (writeBlocked) {
                                return actualIOResult(xfer, goal, flushed, eof);
                            }
                            wrap = true;
                            unwrap = false;
                            break HS;
                        }
                        case NEED_UNWRAP: {
                            if (TRACE_SSL) msg.trace("TLS handshake NEED_UNWRAP");
                            if (wrap && ! flushed && ! sinkConduit.flush()) {
                                // our wrap operation was probably actually a handshake msg, but we couldn't flush it
                                // we need to flush it to proceed else the other side may never send us a response
                                state |= FLAG_FLUSH_NEEDED;
                            }
                            state |= WRITE_FLAG_NEEDS_READ;
                            if (readBlocked) {
                                return actualIOResult(xfer, goal, flushed, eof);
                            }
                            wrap = false;
                            unwrap = true;
                            break HS;
                        }
                        default: {
                            throw msg.unexpectedHandshakeStatus(result.getHandshakeStatus());
                        }
                    }
                }
            }
        } finally {
            this.state = state;
            if (wakeupReads) {
                wakeupReads();
            }
        }
    }

    class TaskWrapper implements Runnable {
        private final Runnable task;

        TaskWrapper(final Runnable task) {
            this.task = task;
        }

        public void run() {
            try {
                task.run();
            } finally {
                synchronized (JsseStreamConduit.this) {
                    if (tasks -- == 1) JsseStreamConduit.this.notifyAll();
                }
            }
        }
    }
}
