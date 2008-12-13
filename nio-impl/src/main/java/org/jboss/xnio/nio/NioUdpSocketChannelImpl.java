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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.jboss.xnio.IoHandler;
import org.jboss.xnio.log.Logger;
import org.jboss.xnio.channels.ChannelOption;
import org.jboss.xnio.channels.Configurable;
import org.jboss.xnio.channels.MultipointReadResult;
import org.jboss.xnio.channels.UdpChannel;
import org.jboss.xnio.channels.UnsupportedOptionException;

/**
 *
 */
public class NioUdpSocketChannelImpl implements UdpChannel {

    private static final Logger log = Logger.getLogger("org.jboss.xnio.nio.udp.server.channel");

    private final DatagramChannel datagramChannel;
    private final NioHandle readHandle;
    private final NioHandle writeHandle;
    private final IoHandler<? super UdpChannel> handler;

    private final AtomicBoolean callFlag = new AtomicBoolean(false);
    private final NioXnio nioXnio;
    private final AtomicLong globalBytesRead;
    private final AtomicLong globalBytesWritten;
    private final AtomicLong globalMessagesRead;
    private final AtomicLong globalMessagesWritten;
    final AtomicLong bytesRead = new AtomicLong();
    final AtomicLong bytesWritten = new AtomicLong();
    final AtomicLong messagesRead = new AtomicLong();
    final AtomicLong messagesWritten = new AtomicLong();

    NioUdpSocketChannelImpl(final NioXnio nioXnio, final DatagramChannel datagramChannel, final IoHandler<? super UdpChannel> handler, final Executor executor, final AtomicLong globalBytesRead, final AtomicLong globalBytesWritten, final AtomicLong globalMessagesRead, final AtomicLong globalMessagesWritten) throws IOException {
        this.nioXnio = nioXnio;
        this.globalBytesRead = globalBytesRead;
        this.globalBytesWritten = globalBytesWritten;
        this.globalMessagesRead = globalMessagesRead;
        this.globalMessagesWritten = globalMessagesWritten;
        if (executor != null) {
            readHandle = nioXnio.addReadHandler(datagramChannel, new ReadHandler(), executor);
            writeHandle = nioXnio.addWriteHandler(datagramChannel, new WriteHandler(), executor);
        } else {
            readHandle = nioXnio.addReadHandler(datagramChannel, new ReadHandler());
            writeHandle = nioXnio.addWriteHandler(datagramChannel, new WriteHandler());
        }
        this.datagramChannel = datagramChannel;
        this.handler = handler;
    }

    public SocketAddress getLocalAddress() {
        return datagramChannel.socket().getLocalSocketAddress();
    }

    public MultipointReadResult<SocketAddress> receive(final ByteBuffer buffer) throws IOException {
        final int o = buffer.remaining();
        final SocketAddress sourceAddress = datagramChannel.receive(buffer);
        if (sourceAddress == null) {
            return null;
        } else {
            final int t = o - buffer.remaining();
            globalMessagesRead.incrementAndGet();
            messagesRead.incrementAndGet();
            globalBytesRead.addAndGet((long) t);
            bytesRead.addAndGet((long) t);
            return new MultipointReadResult<SocketAddress>() {
                public SocketAddress getSourceAddress() {
                    return sourceAddress;
                }

                public SocketAddress getDestinationAddress() {
                    return null;
                }
            };
        }
    }

    public boolean isOpen() {
        return datagramChannel.isOpen();
    }

    public void close() throws IOException {
        if (!callFlag.getAndSet(true)) {
            log.trace("Closing %s", this);
            try {
                datagramChannel.close();
            } finally {
                nioXnio.removeManaged(this);
                HandlerUtils.<UdpChannel>handleClosed(handler, this);
            }
        }
    }

    public boolean send(final SocketAddress target, final ByteBuffer buffer) throws IOException {
        int ret = datagramChannel.send(buffer, target);
        if (ret != 0) {
            globalMessagesWritten.incrementAndGet();
            messagesWritten.incrementAndGet();
            globalBytesWritten.addAndGet((long) ret);
            bytesWritten.addAndGet((long) ret);
            return true;
        } else {
            return false;
        }
    }

    public boolean send(final SocketAddress target, final ByteBuffer[] dsts) throws IOException {
        return send(target, dsts, 0, dsts.length);
    }

    public boolean send(final SocketAddress target, final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        // todo - gather not supported in NIO.1 so we have to fake it...
        long total = 0L;
        for (int i = 0; i < length; i++) {
            total += dsts[offset + i].remaining();
        }
        if (total > Integer.MAX_VALUE) {
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
            readHandle.suspend();
        } catch (CancelledKeyException ex) {
            // ignore
        }
    }

    public void suspendWrites() {
        try {
            writeHandle.suspend();
        } catch (CancelledKeyException ex) {
            // ignore
        }
    }

    public void resumeReads() {
        try {
            readHandle.resume(SelectionKey.OP_READ);
        } catch (CancelledKeyException ex) {
            // ignore
        }
    }

    public void resumeWrites() {
        try {
            writeHandle.resume(SelectionKey.OP_WRITE);
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

    public void awaitReadable() throws IOException {
        SelectorUtils.await(nioXnio, datagramChannel, SelectionKey.OP_READ);
    }

    public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
        SelectorUtils.await(nioXnio, datagramChannel, SelectionKey.OP_READ, time, timeUnit);
    }

    public void awaitWritable() throws IOException {
        SelectorUtils.await(nioXnio, datagramChannel, SelectionKey.OP_WRITE);
    }

    public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
        SelectorUtils.await(nioXnio, datagramChannel, SelectionKey.OP_WRITE, time, timeUnit);
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

    @Override
    public String toString() {
        return String.format("UDP socket channel (NIO) <%s> @ %s", Integer.toString(hashCode(), 16), getLocalAddress());
    }
}
