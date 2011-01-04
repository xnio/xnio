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
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.WriteChannelThread;
import org.xnio.channels.StreamSinkChannel;

abstract class AbstractNioStreamSinkChannel<C extends AbstractNioStreamSinkChannel<C>> implements StreamSinkChannel {
    private final NioXnio nioXnio;

    @SuppressWarnings( { "unused" })
    private volatile NioHandle<AbstractNioStreamSinkChannel> writeHandle;

    @SuppressWarnings( { "unchecked" })
    private static final AtomicReferenceFieldUpdater<AbstractNioStreamSinkChannel, NioHandle> writeHandleUpdater = (AtomicReferenceFieldUpdater<AbstractNioStreamSinkChannel, NioHandle>) AtomicReferenceFieldUpdater.newUpdater(AbstractNioStreamSinkChannel.class, NioHandle.class, "writeHandle");

    private final NioSetter<C> writeSetter = new NioSetter<C>();
    private final NioSetter<C> closeSetter = new NioSetter<C>();

    AbstractNioStreamSinkChannel(final NioXnio xnio) {
        nioXnio = xnio;
    }

    protected abstract GatheringByteChannel getWriteChannel();

    // Setters

    public final ChannelListener.Setter<? extends C> getWriteSetter() {
        return writeSetter;
    }

    public final ChannelListener.Setter<? extends C> getCloseSetter() {
        return closeSetter;
    }

    // Suspend/resume

    public final void suspendWrites() {
        final NioHandle<AbstractNioStreamSinkChannel> writeHandle = this.writeHandle;
        if (writeHandle != null) writeHandle.resume(0);
    }

    public final void resumeWrites() {
        final NioHandle<AbstractNioStreamSinkChannel> writeHandle = this.writeHandle;
        if (writeHandle != null) writeHandle.resume(SelectionKey.OP_WRITE);
    }

    // Await...

    public final void awaitWritable() throws IOException {
        SelectorUtils.await(nioXnio, (SelectableChannel) getWriteChannel(), SelectionKey.OP_WRITE);
    }

    public final void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
        SelectorUtils.await(nioXnio, (SelectableChannel) getWriteChannel(), SelectionKey.OP_WRITE, time, timeUnit);
    }

    // Change thread

    public final void setWriteThread(final WriteChannelThread thread) throws IllegalArgumentException {
        try {
            final NioHandle<C> newHandle = thread == null ? null : ((NioWriteChannelThread) thread).addChannel((AbstractSelectableChannel) getWriteChannel(), typed(), SelectionKey.OP_WRITE, writeSetter, false);
            final NioHandle<C> oldValue = getAndSetWrite(newHandle);
            if (oldValue != null) {
                oldValue.cancelKey();
            }
        } catch (ClosedChannelException e) {
            // do nothing
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("Thread belongs to the wrong provider");
        }
    }

    // Transfer bytes

    public final long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        return src.transferTo(position, count, getWriteChannel());
    }

    // No flush action, by default

    public boolean flush() throws IOException {
        return true;
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

    @SuppressWarnings( { "unchecked" })
    private C typed() {
        return (C) this;
    }

    @SuppressWarnings( { "unchecked" })
    private NioHandle<C> getAndSetWrite(final NioHandle<C> newHandle) {
        return writeHandleUpdater.getAndSet(this, newHandle);
    }

    // Utils for subclasses

    protected void invokeCloseHandler() {
        ChannelListeners.invokeChannelListener(typed(), closeSetter.get());
    }
}
