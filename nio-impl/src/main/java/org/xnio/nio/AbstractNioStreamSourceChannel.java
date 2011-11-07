/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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
import org.xnio.channels.StreamSourceChannel;

import static org.xnio.ChannelListener.SimpleSetter;
import static org.xnio.nio.Log.log;

abstract class AbstractNioStreamSourceChannel<C extends AbstractNioStreamSourceChannel<C>> implements StreamSourceChannel {

    private static final String FQCN = AbstractNioStreamSourceChannel.class.getName();
    private final NioXnioWorker worker;

    private volatile NioHandle<C> readHandle;

    private volatile int readTimeout = 0;

    private final SimpleSetter<C> readSetter = new SimpleSetter<C>();
    private final SimpleSetter<C> closeSetter = new SimpleSetter<C>();

    private static final AtomicIntegerFieldUpdater<AbstractNioStreamSourceChannel> readTimeoutUpdater = AtomicIntegerFieldUpdater.newUpdater(AbstractNioStreamSourceChannel.class, "readTimeout");

    AbstractNioStreamSourceChannel(final NioXnioWorker worker) throws ClosedChannelException {
        this.worker = worker;
    }

    void start() throws ClosedChannelException {
        final WorkerThread readThread = worker.chooseOptional(false);
        readHandle = readThread == null ? null : readThread.addChannel((AbstractSelectableChannel) getReadChannel(), typed(), 0, readSetter);
    }

    protected abstract ScatteringByteChannel getReadChannel();

    // Basic

    public XnioWorker getWorker() {
        return worker;
    }

    // Setters

    public final ChannelListener.Setter<? extends C> getReadSetter() {
        return readSetter;
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
        return target.transferFrom(getReadChannel(), position, count);
    }

    // No flush action, by default

    public boolean flush() throws IOException {
        return true;
    }

    // Read methods

    public int read(final ByteBuffer dst) throws IOException {
        return getReadChannel().read(dst);
    }

    public long read(final ByteBuffer[] dsts) throws IOException {
        return getReadChannel().read(dsts);
    }

    public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        return getReadChannel().read(dsts, offset, length);
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

    // Type-safety stuff

    @SuppressWarnings("unchecked")
    private C typed() {
        return (C) this;
    }

    // Utils for subclasses

    protected void invokeCloseHandler() {
        ChannelListeners.invokeChannelListener(typed(), closeSetter.get());
    }

    protected void cancelReadKey() {
        if (readHandle != null) {
            readHandle.cancelKey();
        }
    }

    NioHandle<C> getReadHandle() {
        return readHandle;
    }
}
