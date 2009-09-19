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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import org.jboss.xnio.IoUtils;
import org.jboss.xnio.Option;
import org.jboss.xnio.ChannelListener;
import org.jboss.xnio.channels.StreamSinkChannel;

/**
 *
 */
final class NioPipeSinkChannel implements StreamSinkChannel {

    private final Pipe.SinkChannel channel;
    private final NioHandle handle;
    private final NioXnio nioXnio;
    private final AtomicBoolean callFlag = new AtomicBoolean(false);
    private final AtomicLong bytes;
    private final AtomicLong messages;
    private final Closeable mbeanHandle;

    private volatile ChannelListener<? super StreamSinkChannel> writeListener = null;
    private volatile ChannelListener<? super StreamSinkChannel> closeListener = null;

    private static final AtomicReferenceFieldUpdater<NioPipeSinkChannel, ChannelListener> writeListenerUpdater = AtomicReferenceFieldUpdater.newUpdater(NioPipeSinkChannel.class, ChannelListener.class, "writeListener");
    private static final AtomicReferenceFieldUpdater<NioPipeSinkChannel, ChannelListener> closeListenerUpdater = AtomicReferenceFieldUpdater.newUpdater(NioPipeSinkChannel.class, ChannelListener.class, "closeListener");

    private final ChannelListener.Setter<StreamSinkChannel> writeSetter = IoUtils.getSetter(this, writeListenerUpdater);
    private final ChannelListener.Setter<StreamSinkChannel> closeSetter = IoUtils.getSetter(this, closeListenerUpdater);

    NioPipeSinkChannel(final Pipe.SinkChannel channel, final NioXnio nioXnio, final AtomicLong bytes, final AtomicLong messages, final Closeable mbeanHandle) throws IOException {
        this.channel = channel;
        this.nioXnio = nioXnio;
        this.bytes = bytes;
        this.messages = messages;
        this.mbeanHandle = mbeanHandle;
        handle = nioXnio.addWriteHandler(channel, new Handler());
    }

    public ChannelListener.Setter<StreamSinkChannel> getWriteSetter() {
        return writeSetter;
    }

    public ChannelListener.Setter<StreamSinkChannel> getCloseSetter() {
        return closeSetter;
    }

    public int write(final ByteBuffer dst) throws IOException {
        int ret = channel.write(dst);
        if (ret > 0) {
            bytes.addAndGet((long) ret);
            messages.incrementAndGet();
        }
        return ret;
    }

    public long write(final ByteBuffer[] dsts) throws IOException {
        long ret = channel.write(dsts);
        if (ret > 0) {
            bytes.addAndGet(ret);
            messages.incrementAndGet();
        }
        return ret;
    }

    public long write(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        long ret = channel.write(dsts, offset, length);
        if (ret > 0) {
            bytes.addAndGet(ret);
            messages.incrementAndGet();
        }
        return ret;
    }

    public boolean isOpen() {
        return channel.isOpen();
    }

    public void close() throws IOException {
        if (! callFlag.getAndSet(true)) {
            nioXnio.removeManaged(this);
            IoUtils.safeClose(mbeanHandle);
            IoUtils.<StreamSinkChannel>invokeChannelListener(this, closeListener);
            channel.close();
        }
    }

    public void suspendWrites() {
        try {
            handle.suspend();
        } catch (CancelledKeyException ex) {
            // ignore
        }
    }

    public void resumeWrites() {
        try {
            handle.resume(SelectionKey.OP_WRITE);
        } catch (CancelledKeyException ex) {
            // ignore
        }
    }

    public void shutdownWrites() throws IOException {
        channel.close();
    }

    public void awaitWritable() throws IOException {
        SelectorUtils.await(nioXnio, channel, SelectionKey.OP_WRITE);
    }

    public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
        SelectorUtils.await(nioXnio, channel, SelectionKey.OP_WRITE, time, timeUnit);
    }

    public boolean supportsOption(final Option<?> option) {
        return false;
    }

    public <T> T getOption(final Option<T> option) throws IOException {
        return null;
    }

    public <T> NioPipeSinkChannel setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        return this;
    }

    private final class Handler implements Runnable {
        public void run() {
            IoUtils.<StreamSinkChannel>invokeChannelListener(NioPipeSinkChannel.this, writeListener);
        }
    }

    @Override
    public String toString() {
        return String.format("pipe sink channel (NIO) <%s>", Integer.toString(hashCode(), 16));
    }
}