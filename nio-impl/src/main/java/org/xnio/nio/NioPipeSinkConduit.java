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

package org.xnio.nio;

import static org.xnio.nio.Log.log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.Pipe;
import java.nio.channels.SelectionKey;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import org.xnio.Xnio;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.channels.WriteTimeoutException;
import org.xnio.conduits.Conduits;
import org.xnio.conduits.StreamSinkConduit;
import org.xnio.conduits.WriteReadyHandler;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class NioPipeSinkConduit extends NioHandle implements StreamSinkConduit {
    private final Pipe.SinkChannel sinkChannel;
    private final NioPipeStreamConnection connection;
    private WriteReadyHandler writeReadyHandler;

    @SuppressWarnings("unused")
    private volatile int writeTimeout;
    private long lastWrite;

    @SuppressWarnings("rawtypes")
    private static final AtomicIntegerFieldUpdater<NioPipeSinkConduit> writeTimeoutUpdater = AtomicIntegerFieldUpdater.newUpdater(NioPipeSinkConduit.class, "writeTimeout");

    NioPipeSinkConduit(final WorkerThread workerThread, final SelectionKey selectionKey, final NioPipeStreamConnection connection) {
        super(workerThread, selectionKey);
        this.connection = connection;
        this.sinkChannel = (Pipe.SinkChannel) selectionKey.channel();
    }

    void handleReady(int ops) {
        try {
            writeReadyHandler.writeReady();
        } catch (Throwable ignored) {
        }
    }

    public XnioWorker getWorker() {
        return getWorkerThread().getWorker();
    }

    void forceTermination() {
        final WriteReadyHandler write = writeReadyHandler;
        if (write != null) write.forceTermination();
    }

    void terminated() {
        final WriteReadyHandler write = writeReadyHandler;
        if (write != null) write.terminated();
    }

    // Write methods

    int getAndSetWriteTimeout(int newVal) {
        return writeTimeoutUpdater.getAndSet(this, newVal);
    }

    int getWriteTimeout() {
        return writeTimeout;
    }

    private void checkWriteTimeout(final boolean xfer) throws WriteTimeoutException {
        int timeout = writeTimeout;
        if (timeout > 0) {
            if (xfer) {
                lastWrite = System.nanoTime();
            } else {
                long lastWrite = this.lastWrite;
                if (lastWrite > 0L && ((System.nanoTime() - lastWrite) / 1000000L) > (long) timeout) {
                    throw log.writeTimeout();
                }
            }
        }
    }

    public final long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        long res = src.transferTo(position, count, sinkChannel);
        checkWriteTimeout(res > 0L);
        return res;
    }

    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        return Conduits.transfer(source, count, throughBuffer, this);
    }

    public int write(final ByteBuffer src) throws IOException {
        int res = sinkChannel.write(src);
        checkWriteTimeout(res > 0);
        return res;
    }

    public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        if (length == 1) {
            return write(srcs[offset]);
        }
        long res = sinkChannel.write(srcs, offset, length);
        checkWriteTimeout(res > 0L);
        return res;
    }

    @Override
    public int writeFinal(ByteBuffer src) throws IOException {
        return Conduits.writeFinalBasic(this, src);
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs, int offset, int length) throws IOException {
        return Conduits.writeFinalBasic(this, srcs, offset, length);
    }

    public boolean flush() throws IOException {
        return true;
    }

    public void terminateWrites() throws IOException {
        if (connection.writeClosed()) try {
            sinkChannel.close();
        } catch (ClosedChannelException ignored) {
        } finally {
            writeTerminated();
        }
    }

    public void truncateWrites() throws IOException {
        terminateWrites();
    }

    void writeTerminated() {
        final WriteReadyHandler writeReadyHandler = this.writeReadyHandler;
        if (writeReadyHandler != null) try {
            writeReadyHandler.terminated();
        } catch (Throwable ignored) {}
    }

    public boolean isWriteShutdown() {
        return connection.isWriteShutdown();
    }

    public void resumeWrites() {
        resume(SelectionKey.OP_WRITE);
    }

    public void suspendWrites() {
        suspend(SelectionKey.OP_WRITE);
    }

    public void wakeupWrites() {
        wakeup(SelectionKey.OP_WRITE);
    }

    public boolean isWriteResumed() {
        return isResumed(SelectionKey.OP_WRITE);
    }

    public void awaitWritable() throws IOException {
        Xnio.checkBlockingAllowed();
        SelectorUtils.await((NioXnio)getWorker().getXnio(), sinkChannel, SelectionKey.OP_WRITE);
    }

    public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
        Xnio.checkBlockingAllowed();
        SelectorUtils.await((NioXnio)getWorker().getXnio(), sinkChannel, SelectionKey.OP_WRITE, time, timeUnit);
    }

    public XnioIoThread getWriteThread() {
        return getWorkerThread();
    }

    public void setWriteReadyHandler(final WriteReadyHandler handler) {
        writeReadyHandler = handler;
    }
}
