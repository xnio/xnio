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
import java.nio.channels.GatheringByteChannel;
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
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.channels.WriteTimeoutException;

import static org.xnio.ChannelListener.SimpleSetter;
import static org.xnio.nio.Log.log;

abstract class AbstractNioStreamSinkChannel<C extends AbstractNioStreamSinkChannel<C>> extends AbstractNioChannel<C> implements StreamSinkChannel {

    private static final String FQCN = AbstractNioStreamSinkChannel.class.getName();

    private volatile NioHandle<C> writeHandle;

    private volatile int writeTimeout = 0;

    private volatile long lastWrite;

    private final SimpleSetter<C> writeSetter = new SimpleSetter<C>();

    private static final AtomicIntegerFieldUpdater<AbstractNioStreamSinkChannel> writeTimeoutUpdater = AtomicIntegerFieldUpdater.newUpdater(AbstractNioStreamSinkChannel.class, "writeTimeout");

    AbstractNioStreamSinkChannel(final NioXnioWorker worker) throws ClosedChannelException {
        super(worker);
    }

    void start() throws ClosedChannelException {
        final WorkerThread writeThread = worker.chooseOptional(true);
        writeHandle = writeThread == null ? null : writeThread.addChannel((AbstractSelectableChannel) getWriteChannel(), typed(), 0, writeSetter);
        lastWrite = System.nanoTime();
    }

    protected abstract GatheringByteChannel getWriteChannel();

    // Setters

    public final ChannelListener.Setter<? extends C> getWriteSetter() {
        return writeSetter;
    }

    // Suspend/resume

    public final void suspendWrites() {
        log.logf(FQCN, Logger.Level.TRACE, null, "Suspend writes on %s", this);
        @SuppressWarnings("unchecked")
        final NioHandle<C> writeHandle = this.writeHandle;
        if (writeHandle != null) writeHandle.resume(0);
    }

    public final void resumeWrites() {
        log.logf(FQCN, Logger.Level.TRACE, null, "Resume writes on %s", this);
        @SuppressWarnings("unchecked")
        final NioHandle<C> writeHandle = this.writeHandle;
        if (writeHandle == null) {
            throw new IllegalArgumentException("No thread configured");
        }
        writeHandle.resume(SelectionKey.OP_WRITE);
    }

    public boolean isWriteResumed() {
        final NioHandle<C> writeHandle = this.writeHandle;
        return writeHandle != null && writeHandle.isResumed(SelectionKey.OP_WRITE);
    }

    public void wakeupWrites() {
        log.logf(FQCN, Logger.Level.TRACE, null, "Wake up writes on %s", this);
        final NioHandle<C> writeHandle = this.writeHandle;
        if (writeHandle == null) {
            throw new IllegalArgumentException("No thread configured");
        }
        writeHandle.resume(SelectionKey.OP_WRITE);
        writeHandle.execute();
    }

    // Await...

    public final void awaitWritable() throws IOException {
        SelectorUtils.await(worker.getXnio(), (SelectableChannel) getWriteChannel(), SelectionKey.OP_WRITE);
    }

    public final void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
        SelectorUtils.await(worker.getXnio(), (SelectableChannel) getWriteChannel(), SelectionKey.OP_WRITE, time, timeUnit);
    }

    public XnioExecutor getWriteThread() {
        final NioHandle<C> handle = writeHandle;
        return handle == null ? null : handle.getWorkerThread();
    }

    // Transfer bytes

    public final long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        long res = src.transferTo(position, count, getWriteChannel());
        if (res > 0L) {
            lastWrite = System.nanoTime();
        } else {
            int timeout = writeTimeout;
            if (timeout > 0 && ((System.nanoTime() - lastWrite) / 1000000L) > (long) timeout) {
                throw new WriteTimeoutException("Write timed out");
            }
        }
        return res;
    }

    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        return IoUtils.transfer(source, count, throughBuffer, this);
    }

    // No flush action, by default

    public boolean flush() throws IOException {
        return true;
    }

    // Read methods

    public int write(final ByteBuffer src) throws IOException {
        int res = getWriteChannel().write(src);
        if (res > 0L) {
            lastWrite = System.nanoTime();
        } else {
            int timeout = writeTimeout;
            if (timeout > 0 && ((System.nanoTime() - lastWrite) / 1000000L) > (long) timeout) {
                throw new WriteTimeoutException("Write timed out");
            }
        }
        return res;
    }

    public long write(final ByteBuffer[] srcs) throws IOException {
        return write(srcs, 0, srcs.length);
    }

    public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        if (length == 1) {
            return write(srcs[offset]);
        }
        long res = getWriteChannel().write(srcs, offset, length);
        if (res > 0L) {
            lastWrite = System.nanoTime();
        } else {
            int timeout = writeTimeout;
            if (timeout > 0 && ((System.nanoTime() - lastWrite) / 1000000L) > (long) timeout) {
                throw new WriteTimeoutException("Write timed out");
            }
        }
        return res;
    }

    // Options

    private static final Set<Option<?>> OPTIONS = Option.setBuilder()
            .add(Options.WRITE_TIMEOUT)
            .create();

    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        if (option == Options.WRITE_TIMEOUT) {
            int newValue = Options.WRITE_TIMEOUT.cast(value).intValue();
            return option.cast(Integer.valueOf(writeTimeoutUpdater.getAndSet(this, newValue)));
        } else {
            return null;
        }
    }

    public <T> T getOption(final Option<T> option) throws IOException {
        if (option == Options.WRITE_TIMEOUT) {
            return option.cast(Integer.valueOf(writeTimeout));
        } else {
            return null;
        }
    }

    public boolean supportsOption(final Option<?> option) {
        return OPTIONS.contains(option);
    }

    // Utils for subclasses

    protected void cancelWriteKey() {
        if (writeHandle != null) {
            writeHandle.cancelKey();
        }
    }

    void migrateTo(final NioXnioWorker worker) throws ClosedChannelException {
        boolean ok = false;
        final WorkerThread writeThread = worker.chooseOptional(true);
        final NioHandle<C> newWriteHandle = writeThread == null? null: writeThread.addChannel((AbstractSelectableChannel) this.getWriteChannel(), typed(), 0, writeSetter);
        try {
            cancelWriteKey();
            ok = true;
        } finally {
            if (ok) {
                writeHandle = newWriteHandle;
                super.migrateTo(worker);
            }
            else if (newWriteHandle != null) {
                newWriteHandle.cancelKey();
            }
        }
    }
}
