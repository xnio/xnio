/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, JBoss Inc.


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
import org.xnio.IoUtils;
import org.xnio.Option;
import org.xnio.Options;
import org.xnio.XnioExecutor;
import org.xnio.XnioWorker;
import org.xnio.channels.ReadTimeoutException;
import org.xnio.channels.StreamChannel;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.channels.WriteTimeoutException;

import static org.xnio.ChannelListener.SimpleSetter;
import static org.xnio.nio.Log.log;

abstract class AbstractNioStreamChannel<C extends AbstractNioStreamChannel<C>> extends AbstractNioChannel<C> implements StreamChannel {

    private static final String FQCN = AbstractNioStreamChannel.class.getName();

    private volatile NioHandle<C> readHandle;
    private volatile NioHandle<C> writeHandle;

    private volatile int readTimeout = 0;
    private volatile int writeTimeout = 0;

    private volatile long lastRead;
    private volatile long lastWrite;

    private final SimpleSetter<C> readSetter = new SimpleSetter<C>();
    private final SimpleSetter<C> writeSetter = new SimpleSetter<C>();

    private static final AtomicIntegerFieldUpdater<AbstractNioStreamChannel> readTimeoutUpdater = AtomicIntegerFieldUpdater.newUpdater(AbstractNioStreamChannel.class, "readTimeout");
    private static final AtomicIntegerFieldUpdater<AbstractNioStreamChannel> writeTimeoutUpdater = AtomicIntegerFieldUpdater.newUpdater(AbstractNioStreamChannel.class, "writeTimeout");

    AbstractNioStreamChannel(final NioXnioWorker worker) throws ClosedChannelException {
        super(worker);
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
    // Setters

    public final ChannelListener.Setter<? extends C> getReadSetter() {
        return readSetter;
    }

    public final ChannelListener.Setter<? extends C> getWriteSetter() {
        return writeSetter;
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
            if (timeout > 0 && ((System.nanoTime() - lastRead) / 1000000L) > (long) timeout) {
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
            if (timeout > 0 && ((System.nanoTime() - lastWrite) / 1000000L) > (long) timeout) {
                throw new WriteTimeoutException("Write timed out");
            }
        }
        return res;
    }

    public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
        return IoUtils.transfer(this, count, throughBuffer, target);
    }

    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        return IoUtils.transfer(source, count, throughBuffer, this);
    }

    // No flush action, by default

    public boolean flush() throws IOException {
        return true;
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

    // Write methods

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

    // Utils for subclasses

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

    void migrateTo(final NioXnioWorker worker) throws ClosedChannelException {
        boolean ok = false;
        final WorkerThread writeThread = worker.choose(true);
        final WorkerThread readThread = worker.choose(false);
        final NioHandle<C> newWriteHandle = writeThread.addChannel((AbstractSelectableChannel) this.getWriteChannel(), typed(), 0, writeSetter);
        try {
            final NioHandle<C> newReadHandle = readThread.addChannel((AbstractSelectableChannel) this.getReadChannel(), typed(), 0, readSetter);
            try {
                cancelReadKey();
                cancelWriteKey();
                ok = true;
            } finally {
                if (ok) {
                    readHandle = newReadHandle;
                    writeHandle = newWriteHandle;
                    super.migrateTo(worker);
                }
            }
        } finally {
            if (! ok) {
                newWriteHandle.cancelKey();
            }
        }
    }
}
