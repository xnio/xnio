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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import org.xnio.IoUtils;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.channels.WriteTimeoutException;
import org.xnio.conduits.StreamSinkConduit;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
abstract class AbstractNioStreamSinkConduit<N extends AbstractSelectableChannel & GatheringByteChannel> extends AbstractNioSinkConduit<N, AbstractNioStreamConnection> implements StreamSinkConduit {

    @SuppressWarnings("unused")
    private volatile int writeTimeout;
    private long lastWrite;

    @SuppressWarnings("rawtypes")
    private static final AtomicIntegerFieldUpdater<AbstractNioStreamSinkConduit> writeTimeoutUpdater = AtomicIntegerFieldUpdater.newUpdater(AbstractNioStreamSinkConduit.class, "writeTimeout");

    protected AbstractNioStreamSinkConduit(final AbstractNioStreamConnection connection, final SelectionKey selectionKey, final WorkerThread workerThread) {
        super(connection, selectionKey, workerThread);
        assert selectionKey.channel() instanceof GatheringByteChannel;
    }

    boolean tryClose() {
        return getConnection().writeClosed();
    }

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
                    throw new WriteTimeoutException("Write timed out");
                }
            }
        }
    }

    // Transfer bytes

    public final long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        long res = src.transferTo(position, count, getChannel());
        checkWriteTimeout(res > 0L);
        return res;
    }

    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        return IoUtils.transfer(source, count, throughBuffer, getChannel());
    }

    // Write methods

    public int write(final ByteBuffer src) throws IOException {
        int res = getChannel().write(src);
        checkWriteTimeout(res > 0);
        return res;
    }

    public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        if (length == 1) {
            return write(srcs[offset]);
        }
        long res = getChannel().write(srcs, offset, length);
        checkWriteTimeout(res > 0L);
        return res;
    }
}
