/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.xnio.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import org.jboss.logging.Logger;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.Option;
import org.xnio.Options;
import org.xnio.XnioExecutor;
import org.xnio.XnioWorker;
import org.xnio.channels.ReadTimeoutException;
import org.xnio.channels.StreamChannel;
import org.xnio.channels.WriteTimeoutException;

import static org.xnio.ChannelListener.SimpleSetter;
import static org.xnio.nio.Log.log;

abstract class AbstractNioStreamChannel<C extends AbstractNioStreamChannel<C>> implements StreamChannel {

    private static final String FQCN = AbstractNioStreamChannel.class.getName();
    private final NioXnioWorker worker;

    private volatile NioHandle<C> readHandle;
    private volatile NioHandle<C> writeHandle;

    private volatile int readTimeout = 0;
    private volatile int writeTimeout = 0;

    private volatile long lastRead;
    private volatile long lastWrite;

    private final SimpleSetter<C> readSetter = new SimpleSetter<C>();
    private final SimpleSetter<C> writeSetter = new SimpleSetter<C>();
    private final SimpleSetter<C> closeSetter = new SimpleSetter<C>();

    private static final AtomicIntegerFieldUpdater<AbstractNioStreamChannel> readTimeoutUpdater = AtomicIntegerFieldUpdater.newUpdater(AbstractNioStreamChannel.class, "readTimeout");
    private static final AtomicIntegerFieldUpdater<AbstractNioStreamChannel> writeTimeoutUpdater = AtomicIntegerFieldUpdater.newUpdater(AbstractNioStreamChannel.class, "writeTimeout");

    AbstractNioStreamChannel(final NioXnioWorker worker) throws ClosedChannelException {
        this.worker = worker;
    }

    void start() throws ClosedChannelException {
        final WorkerThread readThread = worker.chooseOptional(false);
        final WorkerThread writeThread = worker.chooseOptional(true);
        readHandle = readThread == null ? null : readThread.addChannel((AbstractSelectableChannel) getReadChannel(), typed(), 0, readSetter);
        writeHandle = writeThread == null ? null : writeThread.addChannel((AbstractSelectableChannel) getWriteChannel(), typed(), 0, writeSetter);
        lastRead = lastWrite = System.nanoTime();
    }

    protected abstract ScatteringByteChannel getReadChannel();
    protected abstract GatheringByteChannel getWriteChannel();

    // Basic

    public XnioWorker getWorker() {
        return worker;
    }

    // Setters

    public final ChannelListener.Setter<? extends C> getReadSetter() {
        return readSetter;
    }

    public final ChannelListener.Setter<? extends C> getWriteSetter() {
        return writeSetter;
    }

    public final ChannelListener.Setter<? extends C> getCloseSetter() {
        return closeSetter;
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
        resumeReads();
        final NioHandle<C> readHandle = this.readHandle;
        if (readHandle == null) {
            throw new IllegalArgumentException("No thread configured");
        }
        readHandle.execute();
    }

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
        resumeWrites();
        final NioHandle<C> writeHandle = this.writeHandle;
        if (writeHandle == null) {
            throw new IllegalArgumentException("No thread configured");
        }
        writeHandle.execute();
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

    public final long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        long res = target.transferFrom(getReadChannel(), position, count);
        if (res > 0L) {
            lastRead = System.nanoTime();
        } else {
            int timeout = readTimeout;
            if (timeout > 0 && ((lastRead - System.nanoTime()) / 1000000L) > (long) timeout) {
                throw new ReadTimeoutException("Read timed out");
            }
        }
        return res;
    }

    public final long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        long res = src.transferTo(position, count, getWriteChannel());
        if (res > 0L) {
            lastWrite = System.nanoTime();
        } else {
            int timeout = writeTimeout;
            if (timeout > 0 && ((lastWrite - System.nanoTime()) / 1000000L) > (long) timeout) {
                throw new WriteTimeoutException("Write timed out");
            }
        }
        return res;
    }

    // No flush action, by default

    public boolean flush() throws IOException {
        return true;
    }

    // Read methods

    public int read(final ByteBuffer dst) throws IOException {
        int res = getReadChannel().read(dst);
        if (res > 0) {
            lastRead = System.nanoTime();
        } else {
            int timeout = readTimeout;
            if (timeout > 0 && ((lastRead - System.nanoTime()) / 1000000L) > (long) timeout) {
                throw new ReadTimeoutException("Read timed out");
            }
        }
        return res;
    }

    public long read(final ByteBuffer[] dsts) throws IOException {
        long res = getReadChannel().read(dsts);
        if (res > 0L) {
            lastRead = System.nanoTime();
        } else {
            int timeout = readTimeout;
            if (timeout > 0 && ((lastRead - System.nanoTime()) / 1000000L) > (long) timeout) {
                throw new ReadTimeoutException("Read timed out");
            }
        }
        return res;
    }

    public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        long res = getReadChannel().read(dsts, offset, length);
        if (res > 0L) {
            lastRead = System.nanoTime();
        } else {
            int timeout = readTimeout;
            if (timeout > 0 && ((lastRead - System.nanoTime()) / 1000000L) > (long) timeout) {
                throw new ReadTimeoutException("Read timed out");
            }
        }
        return res;
    }

    // Write methods

    public int write(final ByteBuffer src) throws IOException {
        int res = getWriteChannel().write(src);
        if (res > 0L) {
            lastWrite = System.nanoTime();
        } else {
            int timeout = writeTimeout;
            if (timeout > 0 && ((lastWrite - System.nanoTime()) / 1000000L) > (long) timeout) {
                throw new WriteTimeoutException("Write timed out");
            }
        }
        return res;
    }

    public long write(final ByteBuffer[] srcs) throws IOException {
        long res = getWriteChannel().write(srcs);
        if (res > 0L) {
            lastWrite = System.nanoTime();
        } else {
            int timeout = writeTimeout;
            if (timeout > 0 && ((lastWrite - System.nanoTime()) / 1000000L) > (long) timeout) {
                throw new WriteTimeoutException("Write timed out");
            }
        }
        return res;
    }

    public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        long res = getWriteChannel().write(srcs, offset, length);
        if (res > 0L) {
            lastWrite = System.nanoTime();
        } else {
            int timeout = writeTimeout;
            if (timeout > 0 && ((lastWrite - System.nanoTime()) / 1000000L) > (long) timeout) {
                throw new WriteTimeoutException("Write timed out");
            }
        }
        return res;
    }

    // Options

    private static final Set<Option<?>> OPTIONS = Option.setBuilder()
            .add(Options.READ_TIMEOUT)
            .add(Options.WRITE_TIMEOUT)
            .create();

    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        if (option == Options.READ_TIMEOUT) {
            int newValue = Options.READ_TIMEOUT.cast(value).intValue();
            return option.cast(Integer.valueOf(readTimeoutUpdater.getAndSet(this, newValue)));
        } else if (option == Options.WRITE_TIMEOUT) {
            int newValue = Options.WRITE_TIMEOUT.cast(value).intValue();
            return option.cast(Integer.valueOf(writeTimeoutUpdater.getAndSet(this, newValue)));
        } else {
            return null;
        }
    }

    public <T> T getOption(final Option<T> option) throws IOException {
        if (option == Options.READ_TIMEOUT) {
            return option.cast(Integer.valueOf(readTimeout));
        } else if (option == Options.WRITE_TIMEOUT) {
            return option.cast(Integer.valueOf(writeTimeout));
        } else {
            return null;
        }
    }

    public boolean supportsOption(final Option<?> option) {
        return OPTIONS.contains(option);
    }

    // Type-safety stuff

    @SuppressWarnings("unchecked")
    private C typed() {
        return (C) this;
    }

    // Utils for subclasses

    protected void invokeCloseHandler() {
        ChannelListeners.invokeChannelListener(typed(), closeSetter.get());
    }

    protected void cancelWriteKey() {
        if (writeHandle != null) {
            writeHandle.cancelKey();
        }
    }

    protected void cancelReadKey() {
        if (readHandle != null) {
            readHandle.cancelKey();
        }
    }

    NioHandle<C> getReadHandle() {
        return readHandle;
    }

    NioHandle<C> getWriteHandle() {
        return writeHandle;
    }
}
