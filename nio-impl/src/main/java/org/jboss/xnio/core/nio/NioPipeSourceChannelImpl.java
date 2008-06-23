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
import org.jboss.xnio.channels.StreamSourceChannel;
import org.jboss.xnio.channels.UnsupportedOptionException;
import org.jboss.xnio.channels.Configurable;
import org.jboss.xnio.IoHandler;
import org.jboss.xnio.spi.SpiUtils;
import org.jboss.xnio.log.Logger;

/**
 *
 */
public final class NioPipeSourceChannelImpl implements StreamSourceChannel {
    private static final Logger log = Logger.getLogger(NioPipeSourceChannelImpl.class);

    private final Pipe.SourceChannel channel;
    private final NioHandle handle;
    private final NioProvider nioProvider;
    private final IoHandler<? super StreamSourceChannel> handler;
    private final AtomicBoolean callFlag = new AtomicBoolean(false);

    public NioPipeSourceChannelImpl(final Pipe.SourceChannel channel, final IoHandler<? super StreamSourceChannel> handler, final NioProvider nioProvider) throws IOException {
        this.channel = channel;
        this.handler = handler;
        this.nioProvider = nioProvider;
        handle = nioProvider.addReadHandler(channel, new Handler());
    }

    public int read(final ByteBuffer dst) throws IOException {
        return channel.read(dst);
    }

    public long read(final ByteBuffer[] dsts) throws IOException {
        return channel.read(dsts);
    }

    public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        return channel.read(dsts, offset, length);
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
                SpiUtils.<StreamSourceChannel>handleClosed(handler, this);
            }
        }
    }

    public void suspendReads() {
        try {
            handle.getSelectionKey().interestOps(0).selector().wakeup();
        } catch (CancelledKeyException ex) {
            // ignore
        }
    }

    public void resumeReads() {
        try {
            handle.getSelectionKey().interestOps(SelectionKey.OP_READ).selector().wakeup();
        } catch (CancelledKeyException ex) {
            // ignore
        }
    }

    public void shutdownReads() throws IOException {
        channel.close();
    }

    public Object getOption(final String name) throws UnsupportedOptionException, IOException {
        throw new UnsupportedOptionException("No options supported");
    }

    public Map<String, Class<?>> getOptions() {
        return Collections.emptyMap();
    }

    public StreamSourceChannel setOption(final String name, final Object value) throws IllegalArgumentException, IOException {
        throw new UnsupportedOptionException("No options supported");
    }

    private final class Handler implements Runnable {
        public void run() {
            SpiUtils.<StreamSourceChannel>handleReadable(handler, NioPipeSourceChannelImpl.this); 
        }
    }
}
