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
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.ReadChannelThread;
import org.xnio.WriteChannelThread;
import org.xnio.channels.StreamChannel;

abstract class AbstractNioStreamChannel<C extends AbstractNioStreamChannel<C>> implements StreamChannel {
    private final NioXnio nioXnio;

    @SuppressWarnings({ "unused", "unchecked" })
    private volatile NioHandle<AbstractNioStreamChannel> readHandle;
    @SuppressWarnings({ "unused", "unchecked" })
    private volatile NioHandle<AbstractNioStreamChannel> writeHandle;

    @SuppressWarnings( { "unchecked" })
    private static final AtomicReferenceFieldUpdater<AbstractNioStreamChannel, NioHandle> readHandleUpdater = (AtomicReferenceFieldUpdater<AbstractNioStreamChannel, NioHandle>) AtomicReferenceFieldUpdater.newUpdater(AbstractNioStreamChannel.class, NioHandle.class, "readHandle");
    @SuppressWarnings( { "unchecked" })
    private static final AtomicReferenceFieldUpdater<AbstractNioStreamChannel, NioHandle> writeHandleUpdater = (AtomicReferenceFieldUpdater<AbstractNioStreamChannel, NioHandle>) AtomicReferenceFieldUpdater.newUpdater(AbstractNioStreamChannel.class, NioHandle.class, "writeHandle");

    private final NioSetter<C> readSetter = new NioSetter<C>();
    private final NioSetter<C> writeSetter = new NioSetter<C>();
    private final NioSetter<C> closeSetter = new NioSetter<C>();

    AbstractNioStreamChannel(final NioXnio xnio) {
        nioXnio = xnio;
    }

    protected abstract ScatteringByteChannel getReadChannel();
    protected abstract GatheringByteChannel getWriteChannel();

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
        final NioHandle<AbstractNioStreamChannel> readHandle = this.readHandle;
        if (readHandle != null) readHandle.suspend();
    }

    public final void resumeReads() {
        Log.log.tracef("Resume reads on %s", this);
        @SuppressWarnings("unchecked")
        final NioHandle<AbstractNioStreamChannel> readHandle = this.readHandle;
        if (readHandle != null) readHandle.resume(SelectionKey.OP_READ);
    }

    public final void suspendWrites() {
        Log.log.tracef("Suspend writes on %s", this);
        @SuppressWarnings("unchecked")
        final NioHandle<AbstractNioStreamChannel> writeHandle = this.writeHandle;
        if (writeHandle != null) writeHandle.resume(0);
    }

    public final void resumeWrites() {
        Log.log.tracef("Resume writes on %s", this);
        @SuppressWarnings("unchecked")
        final NioHandle<AbstractNioStreamChannel> writeHandle = this.writeHandle;
        if (writeHandle != null) writeHandle.resume(SelectionKey.OP_WRITE);
    }

    // Await...

    public final void awaitReadable() throws IOException {
        SelectorUtils.await(nioXnio, (SelectableChannel) getReadChannel(), SelectionKey.OP_READ);
    }

    public final void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
        SelectorUtils.await(nioXnio, (SelectableChannel) getReadChannel(), SelectionKey.OP_READ, time, timeUnit);
    }

    public final void awaitWritable() throws IOException {
        SelectorUtils.await(nioXnio, (SelectableChannel) getWriteChannel(), SelectionKey.OP_WRITE);
    }

    public final void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
        SelectorUtils.await(nioXnio, (SelectableChannel) getWriteChannel(), SelectionKey.OP_WRITE, time, timeUnit);
    }

    // Change thread

    public final void setReadThread(final ReadChannelThread thread) throws IllegalArgumentException {
        try {
            final NioHandle<C> newHandle = thread == null ? null : ((AbstractNioChannelThread) thread).addChannel((AbstractSelectableChannel) getReadChannel(), typed(), 0, readSetter);
            final NioHandle<C> oldValue = getAndSetRead(newHandle);
            if (oldValue != null && (newHandle == null || oldValue.getSelectionKey() != newHandle.getSelectionKey())) {
                oldValue.cancelKey();
            }
        } catch (ClosedChannelException e) {
            // do nothing
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("Thread belongs to the wrong provider");
        }
    }

    @SuppressWarnings("unchecked")
    public ReadChannelThread getReadThread() {
        final NioHandle<C> handle = readHandleUpdater.get(this);
        return handle == null ? null : (ReadChannelThread) handle.getChannelThread();
    }

    public final void setWriteThread(final WriteChannelThread thread) throws IllegalArgumentException {
        try {
            final NioHandle<C> newHandle = thread == null ? null : ((AbstractNioChannelThread) thread).addChannel((AbstractSelectableChannel) getWriteChannel(), typed(), 0, writeSetter);
            final NioHandle<C> oldValue = getAndSetWrite(newHandle);
            if (oldValue != null && (newHandle == null || oldValue.getSelectionKey() != newHandle.getSelectionKey())) {
                oldValue.cancelKey();
            }
        } catch (ClosedChannelException e) {
            // do nothing
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("Thread belongs to the wrong provider");
        }
    }

    @SuppressWarnings("unchecked")
    public WriteChannelThread getWriteThread() {
        final NioHandle<C> handle = writeHandleUpdater.get(this);
        return handle == null ? null : (WriteChannelThread) handle.getChannelThread();
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

    @SuppressWarnings("unchecked")
    private NioHandle<C> getAndSetRead(final NioHandle<C> newHandle) {
        return readHandleUpdater.getAndSet(this, newHandle);
    }

    @SuppressWarnings("unchecked")
    private NioHandle<C> getAndSetWrite(final NioHandle<C> newHandle) {
        return writeHandleUpdater.getAndSet(this, newHandle);
    }

    // Utils for subclasses

    protected void invokeCloseHandler() {
        ChannelListeners.invokeChannelListener(typed(), closeSetter.get());
    }

    protected void cancelWriteKey() {
        @SuppressWarnings("unchecked")
        final NioHandle writeHandle = writeHandleUpdater.getAndSet(this, null);
        if (writeHandle != null) {
            writeHandle.cancelKey();
        }
    }

    protected void cancelReadKey() {
        @SuppressWarnings("unchecked")
        final NioHandle readHandle = readHandleUpdater.getAndSet(this, null);
        if (readHandle != null) {
            readHandle.cancelKey();
        }
    }
}
