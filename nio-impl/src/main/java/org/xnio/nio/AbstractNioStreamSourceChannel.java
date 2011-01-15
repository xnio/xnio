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
import java.nio.channels.ScatteringByteChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.ReadChannelThread;
import org.xnio.channels.StreamSourceChannel;

abstract class AbstractNioStreamSourceChannel<C extends AbstractNioStreamSourceChannel<C>> implements StreamSourceChannel {
    private final NioXnio nioXnio;

    @SuppressWarnings( { "unused" })
    private volatile NioHandle<AbstractNioStreamSourceChannel> readHandle;

    @SuppressWarnings( { "unchecked" })
    private static final AtomicReferenceFieldUpdater<AbstractNioStreamSourceChannel, NioHandle> readHandleUpdater = (AtomicReferenceFieldUpdater<AbstractNioStreamSourceChannel, NioHandle>) AtomicReferenceFieldUpdater.newUpdater(AbstractNioStreamSourceChannel.class, NioHandle.class, "readHandle");

    private final NioSetter<C> readSetter = new NioSetter<C>();
    private final NioSetter<C> closeSetter = new NioSetter<C>();

    AbstractNioStreamSourceChannel(final NioXnio xnio) {
        nioXnio = xnio;
    }

    protected abstract ScatteringByteChannel getReadChannel();

    // Setters

    public final ChannelListener.Setter<? extends C> getReadSetter() {
        return readSetter;
    }

    public final ChannelListener.Setter<? extends C> getCloseSetter() {
        return closeSetter;
    }

    // Suspend/resume

    public final void suspendReads() {
        final NioHandle<AbstractNioStreamSourceChannel> readHandle = this.readHandle;
        if (readHandle != null) readHandle.suspend();
    }

    public final void resumeReads() {
        final NioHandle<AbstractNioStreamSourceChannel> readHandle = this.readHandle;
        if (readHandle != null) readHandle.resume(SelectionKey.OP_READ);
    }


    // Await...

    public final void awaitReadable() throws IOException {
        SelectorUtils.await(nioXnio, (SelectableChannel) getReadChannel(), SelectionKey.OP_READ);
    }

    public final void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
        SelectorUtils.await(nioXnio, (SelectableChannel) getReadChannel(), SelectionKey.OP_READ, time, timeUnit);
    }

    // Change thread

    public final void setReadThread(final ReadChannelThread thread) throws IllegalArgumentException {
        try {
            final NioHandle<C> newHandle = thread == null ? null : ((NioReadChannelThread) thread).addChannel((AbstractSelectableChannel) getReadChannel(), typed(), SelectionKey.OP_READ, readSetter);
            final NioHandle<C> oldValue = getAndSetRead(newHandle);
            if (oldValue != null) {
                oldValue.cancelKey();
            }
        } catch (ClosedChannelException e) {
            // do nothing
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("Thread belongs to the wrong provider");
        }
    }

    @SuppressWarnings( { "unchecked" })
    public ReadChannelThread getReadThread() {
        final NioHandle<C> handle = readHandleUpdater.get(this);
        return (ReadChannelThread) handle.getChannelThread();
    }

    // Transfer bytes

    public final long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        return target.transferFrom(getReadChannel(), position, count);
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

    // Type-safety stuff

    @SuppressWarnings( { "unchecked" })
    private C typed() {
        return (C) this;
    }

    @SuppressWarnings( { "unchecked" })
    private NioHandle<C> getAndSetRead(final NioHandle<C> newHandle) {
        return readHandleUpdater.getAndSet(this, newHandle);
    }

    // Utils for subclasses

    protected void invokeCloseHandler() {
        ChannelListeners.invokeChannelListener(typed(), closeSetter.get());
    }
}
