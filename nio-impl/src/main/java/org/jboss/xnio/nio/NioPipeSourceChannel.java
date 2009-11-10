/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc., and individual contributors as indicated
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

package org.jboss.xnio.nio;

import java.io.IOException;
import java.io.Closeable;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.Pipe;
import java.nio.channels.SelectionKey;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import org.jboss.xnio.IoUtils;
import org.jboss.xnio.Option;
import org.jboss.xnio.ChannelListener;
import org.jboss.xnio.channels.StreamSourceChannel;
import org.jboss.xnio.channels.UnsupportedOptionException;

/**
 *
 */
final class NioPipeSourceChannel implements StreamSourceChannel {

    private final Pipe.SourceChannel channel;
    private final NioHandle handle;
    private final NioXnio nioXnio;
    private final AtomicBoolean callFlag = new AtomicBoolean(false);
    private final Closeable mbeanHandle;

    private volatile ChannelListener<? super StreamSourceChannel> readListener = null;
    private volatile ChannelListener<? super StreamSourceChannel> closeListener = null;

    private static final AtomicReferenceFieldUpdater<NioPipeSourceChannel, ChannelListener> readListenerUpdater = AtomicReferenceFieldUpdater.newUpdater(NioPipeSourceChannel.class, ChannelListener.class, "readListener");
    private static final AtomicReferenceFieldUpdater<NioPipeSourceChannel, ChannelListener> closeListenerUpdater = AtomicReferenceFieldUpdater.newUpdater(NioPipeSourceChannel.class, ChannelListener.class, "closeListener");

    private final ChannelListener.Setter<StreamSourceChannel> readSetter = IoUtils.getSetter(this, readListenerUpdater);
    private final ChannelListener.Setter<StreamSourceChannel> closeSetter = IoUtils.getSetter(this, closeListenerUpdater);

    NioPipeSourceChannel(final Pipe.SourceChannel channel, final NioXnio nioXnio, final Closeable mbeanHandle) throws IOException {
        this.channel = channel;
        this.nioXnio = nioXnio;
        this.mbeanHandle = mbeanHandle;
        handle = nioXnio.addReadHandler(channel, new Handler());
    }

    public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        return target.transferFrom(channel, position, count);
    }

    public ChannelListener.Setter<StreamSourceChannel> getReadSetter() {
        return readSetter;
    }

    public ChannelListener.Setter<StreamSourceChannel> getCloseSetter() {
        return closeSetter;
    }

    public int read(final ByteBuffer dst) throws IOException {
        int ret = channel.read(dst);
        return ret;
    }

    public long read(final ByteBuffer[] dsts) throws IOException {
        long ret = channel.read(dsts);
        return ret;
    }

    public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        long ret = channel.read(dsts, offset, length);
        return ret;
    }

    public boolean isOpen() {
        return channel.isOpen();
    }

    public void close() throws IOException {
        if (! callFlag.getAndSet(true)) {
            nioXnio.removeManaged(this);
            IoUtils.safeClose(mbeanHandle);
            IoUtils.<StreamSourceChannel>invokeChannelListener(this, closeListener);
            channel.close();
        }
    }

    public void suspendReads() {
        try {
            handle.suspend();
        } catch (CancelledKeyException ex) {
            // ignore
        }
    }

    public void resumeReads() {
        try {
            handle.resume(SelectionKey.OP_READ);
        } catch (CancelledKeyException ex) {
            // ignore
        }
    }

    public void shutdownReads() throws IOException {
        channel.close();
    }

    public void awaitReadable() throws IOException {
        SelectorUtils.await(nioXnio, channel, SelectionKey.OP_READ);
    }

    public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
        SelectorUtils.await(nioXnio, channel, SelectionKey.OP_READ, time, timeUnit);
    }

    public boolean supportsOption(final Option<?> option) {
        return false;
    }

    public <T> T getOption(final Option<T> option) throws UnsupportedOptionException, IOException {
        return null;
    }

    public <T> NioPipeSourceChannel setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        return this;
    }

    private final class Handler implements Runnable {
        public void run() {
            IoUtils.<StreamSourceChannel>invokeChannelListener(NioPipeSourceChannel.this, readListener);
        }
    }

    @Override
    public String toString() {
        return String.format("pipe source channel (NIO) <%s>", Integer.toString(hashCode(), 16));
    }
}
