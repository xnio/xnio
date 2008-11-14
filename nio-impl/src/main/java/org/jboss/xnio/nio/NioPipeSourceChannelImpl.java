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
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.Pipe;
import java.nio.channels.SelectionKey;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.xnio.IoHandler;
import org.jboss.xnio.channels.ChannelOption;
import org.jboss.xnio.channels.Configurable;
import org.jboss.xnio.channels.StreamSourceChannel;
import org.jboss.xnio.channels.UnsupportedOptionException;
import org.jboss.xnio.log.Logger;
import org.jboss.xnio.management.MBeanUtils;
import org.jboss.xnio.management.PipeSourceChannel;

/**
 *
 */
public final class NioPipeSourceChannelImpl implements StreamSourceChannel {
    private static final Logger log = Logger.getLogger("org.jboss.xnio.nio.pipe.source-channel");

    private final Pipe.SourceChannel channel;
    private final NioHandle handle;
    private final NioProvider nioProvider;
    private final IoHandler<? super StreamSourceChannel> handler;
    private final AtomicBoolean callFlag = new AtomicBoolean(false);
    private final PipeSourceChannel mBeanCounters;

    public NioPipeSourceChannelImpl(final Pipe.SourceChannel channel, final IoHandler<? super StreamSourceChannel> handler, final NioProvider nioProvider) throws IOException {
        this.channel = channel;
        this.handler = handler;
        this.nioProvider = nioProvider;
        handle = nioProvider.addReadHandler(channel, new Handler());
        mBeanCounters = new PipeSourceChannel(this);
    }

    public int read(final ByteBuffer dst) throws IOException {
        int read = channel.read(dst);
        mBeanCounters.bytesRead(read);
        return read;
    }

    public long read(final ByteBuffer[] dsts) throws IOException {
        long read = channel.read(dsts);
        mBeanCounters.bytesRead(read);
        return read;
    }

    public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        long read = channel.read(dsts, offset, length);
        mBeanCounters.bytesRead(read);
        return read;
    }

    public boolean isOpen() {
        return channel.isOpen();
    }

    public void close() throws IOException {
        try {
            channel.close();
        } finally {
            nioProvider.removeChannel(this);
            handle.cancelKey();
            if (! callFlag.getAndSet(true)) {
                HandlerUtils.<StreamSourceChannel>handleClosed(handler, this);
            }
            MBeanUtils.unregisterMBean(mBeanCounters.getObjectName());
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
        SelectorUtils.await(SelectionKey.OP_READ, channel);
    }

    public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
        SelectorUtils.await(SelectionKey.OP_READ, channel, time, timeUnit);
    }

    public <T> T getOption(final ChannelOption<T> option) throws UnsupportedOptionException, IOException {
        throw new UnsupportedOptionException("No options supported");
    }

    public <T> Configurable setOption(final ChannelOption<T> option, final T value) throws IllegalArgumentException, IOException {
        throw new UnsupportedOptionException("No options supported");
    }

    public Set<ChannelOption<?>> getOptions() {
        return Collections.emptySet();
    }

    private final class Handler implements Runnable {
        public void run() {
            HandlerUtils.<StreamSourceChannel>handleReadable(handler, NioPipeSourceChannelImpl.this);
        }
    }

    @Override
    public String toString() {
        return String.format("pipe source channel (NIO) <%s>", Integer.toString(hashCode(), 16));
    }
}
