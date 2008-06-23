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

import org.jboss.xnio.channels.StreamChannel;
import org.jboss.xnio.channels.UnsupportedOptionException;
import org.jboss.xnio.channels.Configurable;
import org.jboss.xnio.IoHandler;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.spi.SpiUtils;
import org.jboss.xnio.log.Logger;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Map;
import java.util.Collections;

/**
 *
 */
public final class NioPipeChannelImpl implements StreamChannel {
    public static final Logger log = Logger.getLogger(NioPipeChannelImpl.class);

    private final Pipe.SourceChannel sourceChannel;
    private final Pipe.SinkChannel sinkChannel;
    private final IoHandler<? super StreamChannel> handler;
    private final NioHandle sourceHandle;
    private final NioHandle sinkHandle;
    private final AtomicBoolean callFlag = new AtomicBoolean(false);
    private final NioProvider nioProvider;

    public NioPipeChannelImpl(final Pipe.SourceChannel sourceChannel, final Pipe.SinkChannel sinkChannel, final IoHandler<? super StreamChannel> handler, final NioProvider nioProvider) throws IOException {
        this.sourceChannel = sourceChannel;
        this.sinkChannel = sinkChannel;
        this.handler = handler;
        this.nioProvider = nioProvider;
        // todo leaking [this]
        sourceHandle = nioProvider.addReadHandler(sourceChannel, new ReadHandler());
        sinkHandle = nioProvider.addWriteHandler(sinkChannel, new WriteHandler());
    }

    public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        return sinkChannel.write(srcs, offset, length);
    }

    public long write(final ByteBuffer[] srcs) throws IOException {
        return sinkChannel.write(srcs);
    }

    public int write(final ByteBuffer src) throws IOException {
        return sinkChannel.write(src);
    }

    public boolean isOpen() {
        return sourceChannel.isOpen() && sinkChannel.isOpen();
    }

    public void close() throws IOException {
        // since we've got two channels, only rethrow a failure on the WRITE side, since that's the side that stands to lose data
        IoUtils.safeClose(sourceChannel);
        try {
            sinkChannel.close();
        } finally {
            nioProvider.removeChannel(this);
            if (callFlag.getAndSet(true) == false) {
                SpiUtils.<StreamChannel>handleClosed(handler, this);
            }
            sinkHandle.cancelKey();
            sourceHandle.cancelKey();
        }
    }

    public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        return sourceChannel.read(dsts, offset, length);
    }

    public long read(final ByteBuffer[] dsts) throws IOException {
        return sourceChannel.read(dsts);
    }

    public int read(final ByteBuffer dst) throws IOException {
        return sourceChannel.read(dst);
    }

    public void suspendReads() {
        try {
            sourceHandle.getSelectionKey().interestOps(0).selector().wakeup();
        } catch (CancelledKeyException ex) {
            // ignore
        }
    }

    public void suspendWrites() {
        try {
            sinkHandle.getSelectionKey().interestOps(0).selector().wakeup();
        } catch (CancelledKeyException ex) {
            // ignore
        }
    }

    public void resumeReads() {
        try {
            sourceHandle.getSelectionKey().interestOps(SelectionKey.OP_READ).selector().wakeup();
        } catch (CancelledKeyException ex) {
            // ignore
        }
    }

    public void resumeWrites() {
        try {
            sinkHandle.getSelectionKey().interestOps(SelectionKey.OP_WRITE).selector().wakeup();
        } catch (CancelledKeyException ex) {
            // ignore
        }
    }

    public void shutdownReads() throws IOException {
        sourceChannel.close();
    }

    public void shutdownWrites() throws IOException {
        sinkChannel.close();
    }

    public Object getOption(final String name) throws UnsupportedOptionException, IOException {
        throw new UnsupportedOptionException("No options supported");
    }

    public Map<String, Class<?>> getOptions() {
        return Collections.emptyMap();
    }

    public StreamChannel setOption(final String name, final Object value) throws IllegalArgumentException, IOException {
        throw new UnsupportedOptionException("No options supported");
    }

    private class ReadHandler implements Runnable {
        public void run() {
            IoHandler<? super StreamChannel> handler = NioPipeChannelImpl.this.handler;
            try {
                handler.handleReadable(NioPipeChannelImpl.this);
            } catch (Throwable t) {
                log.error(t, "Read handler threw an exception");
            }
        }
    }

    private class WriteHandler implements Runnable {
        public void run() {
            IoHandler<? super StreamChannel> handler = NioPipeChannelImpl.this.handler;
            try {
                handler.handleWritable(NioPipeChannelImpl.this);
            } catch (Throwable t) {
                log.error(t, "Write handler threw an exception");
            }
        }
    }
}
