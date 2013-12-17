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
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.Pipe;
import java.nio.channels.SelectionKey;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import org.xnio.Xnio;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.ReadTimeoutException;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.conduits.Conduits;
import org.xnio.conduits.ReadReadyHandler;
import org.xnio.conduits.StreamSourceConduit;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class NioPipeSourceConduit extends NioHandle implements StreamSourceConduit {

    private final Pipe.SourceChannel sourceChannel;
    private final NioPipeStreamConnection connection;
    private ReadReadyHandler readReadyHandler;

    @SuppressWarnings("unused")
    private volatile int readTimeout;
    private long lastRead;

    @SuppressWarnings("rawtypes")
    private static final AtomicIntegerFieldUpdater<NioPipeSourceConduit> readTimeoutUpdater = AtomicIntegerFieldUpdater.newUpdater(NioPipeSourceConduit.class, "readTimeout");

    NioPipeSourceConduit(final WorkerThread workerThread, final SelectionKey selectionKey, final NioPipeStreamConnection connection) {
        super(workerThread, selectionKey);
        this.connection = connection;
        this.sourceChannel = (Pipe.SourceChannel) selectionKey.channel();
    }

    void handleReady(int ops) {
        try {
            readReadyHandler.readReady();
        } catch (CancelledKeyException ignored) {}
    }

    public XnioWorker getWorker() {
        return getWorkerThread().getWorker();
    }

    void forceTermination() {
        final ReadReadyHandler read = readReadyHandler;
        if (read != null) read.forceTermination();
    }

    void terminated() {
        final ReadReadyHandler read = readReadyHandler;
        if (read != null) read.terminated();
    }

    // Read methods

    int getAndSetReadTimeout(int newVal) {
        return readTimeoutUpdater.getAndSet(this, newVal);
    }

    int getReadTimeout() {
        return readTimeout;
    }

    private void checkReadTimeout(final boolean xfer) throws ReadTimeoutException {
        int timeout = readTimeout;
        if (timeout > 0) {
            if (xfer) {
                lastRead = System.nanoTime();
            } else {
                long lastRead = this.lastRead;
                if (lastRead > 0L && ((System.nanoTime() - lastRead) / 1000000L) > (long) timeout) {
                    throw log.readTimeout();
                }
            }
        }
    }

    public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        long res = target.transferFrom(sourceChannel, position, count);
        checkReadTimeout(res > 0L);
        return res;
    }

    public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
        return Conduits.transfer(this, count, throughBuffer, target);
    }

    public int read(final ByteBuffer dst) throws IOException {
        int res;
        try {
            res = sourceChannel.read(dst);
        } catch (ClosedChannelException e) {
            return -1;
        }
        if (res != -1) checkReadTimeout(res > 0);
        return res;
    }

    public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        if (length == 1) {
            return read(dsts[offset]);
        }
        long res;
        try {
            res = sourceChannel.read(dsts, offset, length);
        } catch (ClosedChannelException e) {
            return -1L;
        }
        if (res != -1L) checkReadTimeout(res > 0L);
        return res;
    }

    public void terminateReads() throws IOException {
        if (connection.readClosed()) try {
            sourceChannel.close();
        } catch (ClosedChannelException ignored) {
        } finally {
            readTerminated();
        }
    }

    void readTerminated() {
        final ReadReadyHandler readReadyHandler = this.readReadyHandler;
        if (readReadyHandler != null) try {
            readReadyHandler.terminated();
        } catch (Throwable ignored) {}
    }

    public boolean isReadShutdown() {
        return connection.isReadShutdown();
    }

    public void resumeReads() {
        resume(SelectionKey.OP_READ);
    }

    public void suspendReads() {
        suspend(SelectionKey.OP_READ);
    }

    public void wakeupReads() {
        wakeup(SelectionKey.OP_READ);
    }

    public boolean isReadResumed() {
        return isResumed(SelectionKey.OP_READ);
    }

    public void awaitReadable() throws IOException {
        Xnio.checkBlockingAllowed();
        SelectorUtils.await((NioXnio)getWorker().getXnio(), sourceChannel, SelectionKey.OP_READ);
    }

    public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
        Xnio.checkBlockingAllowed();
        SelectorUtils.await((NioXnio)getWorker().getXnio(), sourceChannel, SelectionKey.OP_READ, time, timeUnit);
    }

    public XnioIoThread getReadThread() {
        return getWorkerThread();
    }

    public void setReadReadyHandler(final ReadReadyHandler handler) {
        this.readReadyHandler = handler;
    }
}
