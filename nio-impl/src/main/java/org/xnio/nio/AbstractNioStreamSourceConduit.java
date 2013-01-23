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
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import org.xnio.IoUtils;
import org.xnio.channels.ReadTimeoutException;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.conduits.StreamSourceConduit;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
abstract class AbstractNioStreamSourceConduit<N extends AbstractSelectableChannel & ScatteringByteChannel> extends AbstractNioSourceConduit<N, AbstractNioStreamConnection> implements StreamSourceConduit {

    @SuppressWarnings("unused")
    private volatile int readTimeout;
    private long lastRead;

    @SuppressWarnings("rawtypes")
    private static final AtomicIntegerFieldUpdater<AbstractNioStreamSourceConduit> readTimeoutUpdater = AtomicIntegerFieldUpdater.newUpdater(AbstractNioStreamSourceConduit.class, "readTimeout");

    protected AbstractNioStreamSourceConduit(final AbstractNioStreamConnection connection, final SelectionKey selectionKey, final WorkerThread workerThread) {
        super(connection, selectionKey, workerThread);
        assert selectionKey.channel() instanceof ScatteringByteChannel;
    }

    boolean tryClose() {
        return getConnection().readClosed();
    }

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
                    throw new ReadTimeoutException("Read timed out");
                }
            }
        }
    }

    public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        long res = target.transferFrom(getChannel(), position, count);
        checkReadTimeout(res > 0L);
        return res;
    }

    public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
        return IoUtils.transfer(getChannel(), count, throughBuffer, target);
    }

    public int read(final ByteBuffer dst) throws IOException {
        int res;
        try {
            res = getChannel().read(dst);
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
            res = getChannel().read(dsts, offset, length);
        } catch (ClosedChannelException e) {
            return -1L;
        }
        if (res != -1L) checkReadTimeout(res > 0L);
        return res;
    }
}
