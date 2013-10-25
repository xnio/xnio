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

package org.xnio.conduits;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;

import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.Channels;
import org.xnio.channels.StreamSourceChannel;

/**
 * A stream sink conduit which discards all data written to it.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class NullStreamSinkConduit implements StreamSinkConduit {
    private final XnioWorker worker;
    private final XnioIoThread writeThread;
    private WriteReadyHandler writeReadyHandler;
    private boolean shutdown;
    private boolean resumed;

    /**
     * Construct a new instance.
     *
     * @param writeThread the write thread for this conduit
     */
    public NullStreamSinkConduit(final XnioIoThread writeThread) {
        this.worker = writeThread.getWorker();
        this.writeThread = writeThread;
    }

    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        return Channels.drain(src, position, count);
    }

    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        throughBuffer.limit(0);
        return Channels.drain(source, count);
    }

    public int write(final ByteBuffer src) throws IOException {
        try {
            return src.remaining();
        } finally {
            src.position(src.limit());
        }
    }

    public long write(final ByteBuffer[] srcs, final int offs, final int len) throws IOException {
        long t = 0L;
        for (int i = 0; i < len; i++) {
            t += write(srcs[i + offs]);
        }
        return t;
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

    public boolean isWriteShutdown() {
        return shutdown;
    }

    public void suspendWrites() {
        resumed = false;
    }

    public void resumeWrites() {
        resumed = true;
        final WriteReadyHandler handler = writeReadyHandler;
        writeThread.execute(new WriteReadyHandler.ReadyTask(handler));
    }

    public void wakeupWrites() {
        resumeWrites();
    }

    public boolean isWriteResumed() {
        return resumed;
    }

    public void awaitWritable() throws IOException {
    }

    public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
    }

    public XnioIoThread getWriteThread() {
        return writeThread;
    }

    public void setWriteReadyHandler(final WriteReadyHandler handler) {
        writeReadyHandler = handler;
    }

    public void truncateWrites() throws IOException {
        terminateWrites();
    }

    public void terminateWrites() throws IOException {
        if (! shutdown) {
            shutdown = true;
            writeReadyHandler.terminated();
        }
    }

    public XnioWorker getWorker() {
        return worker;
    }
}
