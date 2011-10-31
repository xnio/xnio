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
import java.util.concurrent.TimeUnit;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamChannel;

import static org.xnio.ChannelListener.SimpleSetter;

abstract class AbstractNioStreamChannel<C extends AbstractNioStreamChannel<C>> implements StreamChannel {
    private final NioXnioWorker worker;

    private volatile NioHandle<C> readHandle;
    private volatile NioHandle<C> writeHandle;

    private final SimpleSetter<C> readSetter = new SimpleSetter<C>();
    private final SimpleSetter<C> writeSetter = new SimpleSetter<C>();
    private final SimpleSetter<C> closeSetter = new SimpleSetter<C>();

    AbstractNioStreamChannel(final NioXnioWorker worker) throws ClosedChannelException {
        this.worker = worker;
    }

    void start() throws ClosedChannelException {
        final WorkerThread readThread = worker.chooseOptional(false);
        final WorkerThread writeThread = worker.chooseOptional(true);
        readHandle = readThread == null ? null : readThread.addChannel((AbstractSelectableChannel) getReadChannel(), typed(), 0, readSetter);
        writeHandle = writeThread == null ? null : writeThread.addChannel((AbstractSelectableChannel) getWriteChannel(), typed(), 0, writeSetter);
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
        Log.log.tracef("Suspend reads on %s", this);
        @SuppressWarnings("unchecked")
        final NioHandle<C> readHandle = this.readHandle;
        if (readHandle != null) readHandle.suspend();
    }

    public final void resumeReads() {
        Log.log.tracef("Resume reads on %s", this);
        @SuppressWarnings("unchecked")
        final NioHandle<C> readHandle = this.readHandle;
        if (readHandle == null) {
            throw new IllegalArgumentException("No thread configured");
        }
        readHandle.resume(SelectionKey.OP_READ);
    }

    public final void suspendWrites() {
        Log.log.tracef("Suspend writes on %s", this);
        @SuppressWarnings("unchecked")
        final NioHandle<C> writeHandle = this.writeHandle;
        if (writeHandle != null) writeHandle.resume(0);
    }

    public final void resumeWrites() {
        Log.log.tracef("Resume writes on %s", this);
        @SuppressWarnings("unchecked")
        final NioHandle<C> writeHandle = this.writeHandle;
        if (writeHandle == null) {
            throw new IllegalArgumentException("No thread configured");
        }
        writeHandle.resume(SelectionKey.OP_WRITE);
    }

    // Await...

    public final void awaitReadable() throws IOException {
        SelectorUtils.await(worker.getXnio(), (SelectableChannel) getReadChannel(), SelectionKey.OP_READ);
    }

    public final void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
        SelectorUtils.await(worker.getXnio(), (SelectableChannel) getReadChannel(), SelectionKey.OP_READ, time, timeUnit);
    }

    public final void awaitWritable() throws IOException {
        SelectorUtils.await(worker.getXnio(), (SelectableChannel) getWriteChannel(), SelectionKey.OP_WRITE);
    }

    public final void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
        SelectorUtils.await(worker.getXnio(), (SelectableChannel) getWriteChannel(), SelectionKey.OP_WRITE, time, timeUnit);
    }

    // Transfer bytes

    public final long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        return target.transferFrom(getReadChannel(), position, count);
    }

    public final long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        return src.transferTo(position, count, getWriteChannel());
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

    // Write methods

    public int write(final ByteBuffer src) throws IOException {
        return getWriteChannel().write(src);
    }

    public long write(final ByteBuffer[] srcs) throws IOException {
        return getWriteChannel().write(srcs);
    }

    public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        return getWriteChannel().write(srcs, offset, length);
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
