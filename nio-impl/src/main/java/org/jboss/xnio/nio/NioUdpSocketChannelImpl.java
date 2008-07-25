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
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jboss.xnio.IoHandler;
import org.jboss.xnio.channels.ChannelOption;
import org.jboss.xnio.channels.Configurable;
import org.jboss.xnio.channels.MultipointReadResult;
import org.jboss.xnio.channels.UdpChannel;
import org.jboss.xnio.channels.UnsupportedOptionException;
import org.jboss.xnio.nio.HandlerUtils;

/**
 *
 */
public final class NioUdpSocketChannelImpl implements UdpChannel {

    private final DatagramChannel datagramChannel;
    private final NioHandle readHandle;
    private final NioHandle writeHandle;
    private final IoHandler<? super UdpChannel> handler;

    private final AtomicBoolean callFlag = new AtomicBoolean(false);
    private final NioProvider nioProvider;

    public NioUdpSocketChannelImpl(final NioProvider nioProvider, final DatagramChannel datagramChannel, final IoHandler<? super UdpChannel> handler) throws IOException {
        this.nioProvider = nioProvider;
        readHandle = nioProvider.addReadHandler(datagramChannel, new ReadHandler());
        writeHandle = nioProvider.addWriteHandler(datagramChannel, new WriteHandler());
        this.datagramChannel = datagramChannel;
        this.handler = handler;
    }

    public SocketAddress getLocalAddress() {
        return datagramChannel.socket().getLocalSocketAddress();
    }

    public MultipointReadResult<SocketAddress> receive(final ByteBuffer buffer) throws IOException {
        final SocketAddress sourceAddress = datagramChannel.receive(buffer);
        return sourceAddress == null ? null : new MultipointReadResult<SocketAddress>() {
            public SocketAddress getSourceAddress() {
                return sourceAddress;
            }

            public SocketAddress getDestinationAddress() {
                return null;
            }
        };
    }

    public boolean isOpen() {
        return datagramChannel.isOpen();
    }

    public void close() throws IOException {
        try {
            datagramChannel.close();
        } finally {
            nioProvider.removeChannel(this);
            readHandle.cancelKey();
            writeHandle.cancelKey();
            if (!callFlag.getAndSet(true)) {
                HandlerUtils.<UdpChannel>handleClosed(handler, this);
            }
        }
    }

    public boolean send(final SocketAddress target, final ByteBuffer buffer) throws IOException {
        return datagramChannel.send(buffer, target) != 0;
    }

    public boolean send(final SocketAddress target, final ByteBuffer[] dsts) throws IOException {
        return send(target, dsts, 0, dsts.length);
    }

    public boolean send(final SocketAddress target, final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        // todo - gather not supported in NIO.1 so we have to fake it...
        long total = 0L;
        for (int i = 0; i < length; i++) {
            total += (long) dsts[offset + i].remaining();
        }
        if (total > (long)Integer.MAX_VALUE) {
            throw new IOException("Source data is too large");
        }
        ByteBuffer buf = ByteBuffer.allocate((int)total);
        for (int i = 0; i < length; i++) {
            buf.put(dsts[offset + i]);
        }
        buf.flip();
        return send(target, buf);
    }

    public void suspendReads() {
        try {
            readHandle.getSelectionKey().interestOps(0).selector().wakeup();
        } catch (CancelledKeyException ex) {
            // ignore
        }
    }

    public void suspendWrites() {
        try {
            writeHandle.getSelectionKey().interestOps(0).selector().wakeup();
        } catch (CancelledKeyException ex) {
            // ignore
        }
    }

    public void resumeReads() {
        try {
            readHandle.getSelectionKey().interestOps(SelectionKey.OP_READ).selector().wakeup();
        } catch (CancelledKeyException ex) {
            // ignore
        }
    }

    public void resumeWrites() {
        try {
            writeHandle.getSelectionKey().interestOps(SelectionKey.OP_WRITE).selector().wakeup();
        } catch (CancelledKeyException ex) {
            // ignore
        }
    }

    public void shutdownReads() throws IOException {
        throw new UnsupportedOperationException("Shutdown reads");
    }

    public void shutdownWrites() throws IOException {
        throw new UnsupportedOperationException("Shutdown writes");
    }

    public Key join(final InetAddress group, final NetworkInterface iface) throws IOException {
        throw new UnsupportedOperationException("Multicast join");
    }

    public Key join(final InetAddress group, final NetworkInterface iface, final InetAddress source) throws IOException {
        throw new UnsupportedOperationException("Multicast join");
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

    public IoHandler<? super UdpChannel> getHandler() {
        return handler;
    }

    public final class ReadHandler implements Runnable {
        public void run() {
            HandlerUtils.<UdpChannel>handleReadable(handler, NioUdpSocketChannelImpl.this);
        }
    }

    public final class WriteHandler implements Runnable {
        public void run() {
            HandlerUtils.<UdpChannel>handleWritable(handler, NioUdpSocketChannelImpl.this);
        }
    }

    public String toString() {
        return String.format("UDP socket channel (NIO) <%s>", Integer.toString(hashCode(), 16));
    }
}
