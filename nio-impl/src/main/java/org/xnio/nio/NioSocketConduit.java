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
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import org.xnio.Bits;
import org.xnio.Xnio;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.ReadTimeoutException;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.channels.WriteTimeoutException;
import org.xnio.conduits.Conduits;
import org.xnio.conduits.ReadReadyHandler;
import org.xnio.conduits.StreamSinkConduit;
import org.xnio.conduits.StreamSourceConduit;
import org.xnio.conduits.WriteReadyHandler;

final class NioSocketConduit extends NioHandle implements StreamSourceConduit, StreamSinkConduit {
    private final SocketChannel socketChannel;
    private final NioSocketStreamConnection connection;
    private ReadReadyHandler readReadyHandler;
    private WriteReadyHandler writeReadyHandler;

    @SuppressWarnings("unused")
    private volatile int readTimeout;
    private long lastRead;

    @SuppressWarnings("rawtypes")
    private static final AtomicIntegerFieldUpdater<NioSocketConduit> readTimeoutUpdater = AtomicIntegerFieldUpdater.newUpdater(NioSocketConduit.class, "readTimeout");

    @SuppressWarnings("unused")
    private volatile int writeTimeout;
    private long lastWrite;

    @SuppressWarnings("rawtypes")
    private static final AtomicIntegerFieldUpdater<NioSocketConduit> writeTimeoutUpdater = AtomicIntegerFieldUpdater.newUpdater(NioSocketConduit.class, "writeTimeout");

    NioSocketConduit(final WorkerThread workerThread, final SelectionKey selectionKey, final NioSocketStreamConnection connection) {
        super(workerThread, selectionKey);
        this.connection = connection;
        this.socketChannel = (SocketChannel) selectionKey.channel();
    }

    void handleReady(int ops) {
        try {
            if (ops == 0) {
                // the dreaded bug
                final SelectionKey key = getSelectionKey();
                final int interestOps = key.interestOps();
                if (interestOps != 0) {
                    ops = interestOps;
                } else {
                    // urp
                    forceTermination();
                    return;
                }
            }
            if (Bits.allAreSet(ops, SelectionKey.OP_READ)) try {
                if (isReadShutdown()) suspendReads();
                readReadyHandler.readReady();
            } catch (Throwable ignored) {
            }
            if (Bits.allAreSet(ops, SelectionKey.OP_WRITE)) try {
                if (isWriteShutdown()) suspendWrites();
                else
                    writeReadyHandler.writeReady();
            } catch (Throwable ignored) {
            }
        } catch (CancelledKeyException ignored) {}
    }

    public XnioWorker getWorker() {
        return getWorkerThread().getWorker();
    }

    void forceTermination() {
        final ReadReadyHandler read = readReadyHandler;
        if (read != null) read.forceTermination();
        final WriteReadyHandler write = writeReadyHandler;
        if (write != null) write.forceTermination();
    }

    void terminated() {
        final ReadReadyHandler read = readReadyHandler;
        if (read != null) read.terminated();
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
                long lastRead = this.lastWrite;
                if (lastRead > 0L && ((System.nanoTime() - lastRead) / 1000000L) > (long) timeout) {
                    throw log.writeTimeout();
                }
            }
        }
    }

    public final long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        long res = src.transferTo(position, count, socketChannel);
        checkWriteTimeout(res > 0L);
        return res;
    }

    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        return Conduits.transfer(source, count, throughBuffer, this);
    }

    public int write(final ByteBuffer src) throws IOException {
        int res = socketChannel.write(src);
        checkWriteTimeout(res > 0);
        return res;
    }

    public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        if (length == 1) {
            return write(srcs[offset]);
        }
        long res = socketChannel.write(srcs, offset, length);
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
            if (getSelectionKey().isValid()) {
                suspend(SelectionKey.OP_WRITE);
            }
            if (socketChannel.isOpen()) try {
                socketChannel.socket().shutdownOutput();
            } catch (SocketException ignored) {
                // IBM incorrectly throws this exception on ENOTCONN; it's probably less harmful just to swallow it
            }
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
        if (isWriteShutdown()) {
            return;
        }
        SelectorUtils.await((NioXnio) getWorker().getXnio(), socketChannel, SelectionKey.OP_WRITE);
    }

    public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
        Xnio.checkBlockingAllowed();
        if (isWriteShutdown()) {
            return;
        }
        SelectorUtils.await((NioXnio) getWorker().getXnio(), socketChannel, SelectionKey.OP_WRITE, time, timeUnit);
    }

    public XnioIoThread getWriteThread() {
        return getWorkerThread();
    }

    public void setWriteReadyHandler(final WriteReadyHandler handler) {
        writeReadyHandler = handler;
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
        long res = target.transferFrom(socketChannel, position, count);
        checkReadTimeout(res > 0L);
        return res;
    }

    public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
        return Conduits.transfer(this, count, throughBuffer, target);
    }

    public int read(final ByteBuffer dst) throws IOException {
        int res;
        try {
            res = socketChannel.read(dst);
        } catch (ClosedChannelException e) {
            return -1;
        }
        if (res != -1) checkReadTimeout(res > 0);
        else terminateReads();
        return res;
    }

    public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        if (length == 1) {
            return read(dsts[offset]);
        }
        long res;
        try {
            res = socketChannel.read(dsts, offset, length);
        } catch (ClosedChannelException e) {
            return -1L;
        }
        if (res != -1L) checkReadTimeout(res > 0L);
        else terminateReads();
        return res;
    }

    public void terminateReads() throws IOException {
        if (connection.readClosed()) try {
            if (getSelectionKey().isValid()) {
                suspend(SelectionKey.OP_READ);
            }
            if (socketChannel.isOpen()) try {
                socketChannel.socket().shutdownInput();
            } catch (SocketException ignored) {
                // IBM incorrectly throws this exception on ENOTCONN; it's probably less harmful just to swallow it
            }
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
        SelectorUtils.await((NioXnio) getWorker().getXnio(), socketChannel, SelectionKey.OP_READ);
    }

    public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
        Xnio.checkBlockingAllowed();
        SelectorUtils.await((NioXnio) getWorker().getXnio(), socketChannel, SelectionKey.OP_READ, time, timeUnit);
    }

    public XnioIoThread getReadThread() {
        return getWorkerThread();
    }

    public void setReadReadyHandler(final ReadReadyHandler handler) {
        this.readReadyHandler = handler;
    }

    SocketChannel getSocketChannel() {
        return socketChannel;
    }
}
