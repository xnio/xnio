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
import java.util.concurrent.atomic.AtomicBoolean;
import org.jboss.xnio.IoHandler;
import org.jboss.xnio.channels.ChannelOption;
import org.jboss.xnio.channels.Configurable;
import org.jboss.xnio.channels.StreamSinkChannel;
import org.jboss.xnio.channels.UnsupportedOptionException;
import org.jboss.xnio.spi.SpiUtils;

/**
 *
 */
public final class NioPipeSinkChannelImpl implements StreamSinkChannel {

    private final Pipe.SinkChannel channel;
    private final NioHandle handle;
    private final NioProvider nioProvider;
    private final IoHandler<? super StreamSinkChannel> handler;
    private final AtomicBoolean callFlag = new AtomicBoolean(false);

    public NioPipeSinkChannelImpl(final Pipe.SinkChannel channel, final IoHandler<? super StreamSinkChannel> handler, final NioProvider nioProvider) throws IOException {
        this.channel = channel;
        this.handler = handler;
        this.nioProvider = nioProvider;
        handle = nioProvider.addWriteHandler(channel, new Handler());
    }

    public int write(final ByteBuffer dst) throws IOException {
        return channel.write(dst);
    }

    public long write(final ByteBuffer[] dsts) throws IOException {
        return channel.write(dsts);
    }

    public long write(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        return channel.write(dsts, offset, length);
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
                SpiUtils.<StreamSinkChannel>handleClosed(handler, this);
            }
        }
    }

    public void suspendWrites() {
        try {
            handle.getSelectionKey().interestOps(0).selector().wakeup();
        } catch (CancelledKeyException ex) {
            // ignore
        }
    }

    public void resumeWrites() {
        try {
            handle.getSelectionKey().interestOps(SelectionKey.OP_WRITE).selector().wakeup();
        } catch (CancelledKeyException ex) {
            // ignore
        }
    }

    public void shutdownWrites() throws IOException {
        channel.close();
    }

    public <T> T getOption(final ChannelOption<T> option) throws UnsupportedOptionException, IOException {
        throw new UnsupportedOptionException("No options supported");
    }

    public Set<ChannelOption<?>> getOptions() {
        return Collections.emptySet();
    }

    public <T> Configurable setOption(final ChannelOption<T> option, final T value) throws IllegalArgumentException, IOException {
        throw new UnsupportedOptionException("No options supported");
    }

    private final class Handler implements Runnable {
        public void run() {
            SpiUtils.<StreamSinkChannel>handleWritable(handler, NioPipeSinkChannelImpl.this);
        }
    }

    public String toString() {
        return String.format("pipe sink channel (NIO) <%s>", Integer.toString(hashCode(), 16));
    }
}