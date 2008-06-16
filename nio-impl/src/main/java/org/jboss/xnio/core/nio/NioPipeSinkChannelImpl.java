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

package org.jboss.xnio.core.nio;

import java.nio.channels.Pipe;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.ByteBuffer;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Map;
import java.util.Collections;
import org.jboss.xnio.channels.StreamSinkChannel;
import org.jboss.xnio.channels.UnsupportedOptionException;
import org.jboss.xnio.channels.Configurable;
import org.jboss.xnio.IoHandler;
import org.jboss.xnio.spi.SpiUtils;
import org.jboss.xnio.log.Logger;

/**
 *
 */
public final class NioPipeSinkChannelImpl implements StreamSinkChannel {
    private static final Logger log = Logger.getLogger(NioPipeSinkChannelImpl.class);

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

    public Object getOption(final String name) throws UnsupportedOptionException, IOException {
        throw new UnsupportedOptionException("No options supported");
    }

    public Map<String, Class<?>> getOptions() {
        return Collections.emptyMap();
    }

    public Configurable setOption(final String name, final Object value) throws IllegalArgumentException, IOException {
        throw new UnsupportedOptionException("No options supported");
    }

    private final class Handler implements Runnable {
        public void run() {
            SpiUtils.<StreamSinkChannel>handleWritable(handler, NioPipeSinkChannelImpl.this);
        }
    }
}