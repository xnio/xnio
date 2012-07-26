/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
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
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import org.jboss.logging.Logger;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.Option;
import org.xnio.Options;
import org.xnio.XnioExecutor;
import org.xnio.channels.ReadTimeoutException;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import static org.xnio.ChannelListener.SimpleSetter;
import static org.xnio.nio.Log.log;

abstract class AbstractNioStreamSourceChannel<C extends AbstractNioStreamSourceChannel<C>> extends AbstractNioChannel<C> implements StreamSourceChannel {

    private static final String FQCN = AbstractNioStreamSourceChannel.class.getName();

    private volatile NioHandle<C> readHandle;

    private volatile int readTimeout = 0;

    private volatile long lastRead;

    private final SimpleSetter<C> readSetter = new SimpleSetter<C>();

    private static final AtomicIntegerFieldUpdater<AbstractNioStreamSourceChannel> readTimeoutUpdater = AtomicIntegerFieldUpdater.newUpdater(AbstractNioStreamSourceChannel.class, "readTimeout");

    AbstractNioStreamSourceChannel(final NioXnioWorker worker) throws ClosedChannelException {
        super(worker);
    }

    void start() throws ClosedChannelException {
        final WorkerThread readThread = worker.chooseOptional(false);
        readHandle = readThread == null ? null : readThread.addChannel((AbstractSelectableChannel) getReadChannel(), typed(), 0, readSetter);
        lastRead = System.nanoTime();
    }

    protected abstract ScatteringByteChannel getReadChannel();

    // Setters

    public final ChannelListener.Setter<? extends C> getReadSetter() {
        return readSetter;
    }

    // Suspend/resume

    public final void suspendReads() {
        log.logf(FQCN, Logger.Level.TRACE, null, "Suspend reads on %s", this);
        final NioHandle<C> readHandle = this.readHandle;
        if (readHandle != null) readHandle.suspend();
    }

    public final void resumeReads() {
        log.logf(FQCN, Logger.Level.TRACE, null, "Resume reads on %s", this);
        final NioHandle<C> readHandle = this.readHandle;
        if (readHandle == null) {
            throw new IllegalArgumentException("No thread configured");
        }
        readHandle.resume(SelectionKey.OP_READ);
    }

    public boolean isReadResumed() {
        final NioHandle<C> readHandle = this.readHandle;
        return readHandle != null && readHandle.isResumed(SelectionKey.OP_READ);
    }

    public void wakeupReads() {
        log.logf(FQCN, Logger.Level.TRACE, null, "Wake up reads on %s", this);
        final NioHandle<C> readHandle = this.readHandle;
        if (readHandle == null) {
            throw new IllegalArgumentException("No thread configured");
        }
        readHandle.resume(SelectionKey.OP_READ);
        readHandle.execute();
    }

    // Await...

    public final void awaitReadable() throws IOException {
        SelectorUtils.await(worker.getXnio(), (SelectableChannel) getReadChannel(), SelectionKey.OP_READ);
    }

    public final void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
        SelectorUtils.await(worker.getXnio(), (SelectableChannel) getReadChannel(), SelectionKey.OP_READ, time, timeUnit);
    }

    public XnioExecutor getReadThread() {
        final NioHandle<C> handle = readHandle;
        return handle == null ? null : handle.getWorkerThread();
    }

    // Transfer bytes

    public final long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        long res = target.transferFrom(getReadChannel(), position, count);
        if (res > 0L) {
            lastRead = System.nanoTime();
        } else {
            int timeout = readTimeout;
            if (timeout > 0 && ((System.nanoTime() - lastRead) / 1000000L) > (long) timeout) {
                throw new ReadTimeoutException("Read timed out");
            }
        }
        return res;
    }

    public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
        return IoUtils.transfer(this, count, throughBuffer, target);
    }

    // Read methods

    public int read(final ByteBuffer dst) throws IOException {
        int res;
        try {
            res = getReadChannel().read(dst);
        } catch (ClosedChannelException e) {
            return -1;
        }
        if (res > 0) {
            lastRead = System.nanoTime();
        } else {
            int timeout = readTimeout;
            if (timeout > 0 && ((System.nanoTime() - lastRead) / 1000000L) > (long) timeout) {
                throw new ReadTimeoutException("Read timed out");
            }
        }
        return res;
    }

    public long read(final ByteBuffer[] dsts) throws IOException {
        return read(dsts, 0, dsts.length);
    }

    public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        if (length == 1) {
            return read(dsts[offset]);
        }
        long res;
        try {
            res = getReadChannel().read(dsts, offset, length);
        } catch (ClosedChannelException e) {
            return -1L;
        }
        if (res > 0L) {
            lastRead = System.nanoTime();
        } else {
            int timeout = readTimeout;
            if (timeout > 0 && ((System.nanoTime() - lastRead) / 1000000L) > (long) timeout) {
                throw new ReadTimeoutException("Read timed out");
            }
        }
        return res;
    }

    // Options

    private static final Set<Option<?>> OPTIONS = Option.setBuilder()
            .add(Options.READ_TIMEOUT)
            .create();

    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        if (option == Options.READ_TIMEOUT) {
            int newValue = Options.READ_TIMEOUT.cast(value).intValue();
            return option.cast(Integer.valueOf(readTimeoutUpdater.getAndSet(this, newValue)));
        } else {
            return null;
        }
    }

    public <T> T getOption(final Option<T> option) throws IOException {
        if (option == Options.READ_TIMEOUT) {
            return option.cast(Integer.valueOf(readTimeout));
        } else {
            return null;
        }
    }

    public boolean supportsOption(final Option<?> option) {
        return OPTIONS.contains(option);
    }

    // Utils for subclasses

    protected void cancelReadKey() {
        if (readHandle != null) {
            readHandle.cancelKey();
        }
    }

    NioHandle<C> getReadHandle() {
        return readHandle;
    }

    void migrateTo(final NioXnioWorker worker) throws ClosedChannelException {
        boolean ok = false;
        final WorkerThread readThread = worker.choose(false);
        final NioHandle<C> newReadHandle = readThread.addChannel((AbstractSelectableChannel) this.getReadChannel(), typed(), 0, readSetter);
        try {
            cancelReadKey();
            ok = true;
        } finally {
            if (! ok) {
                newReadHandle.cancelKey();
            } else {
                readHandle = newReadHandle;
                super.migrateTo(worker);
            }
        }
    }
}
