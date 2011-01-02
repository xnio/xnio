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

package org.xnio.nio;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import org.xnio.Option;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.Options;
import org.xnio.channels.MulticastMessageChannel;
import org.xnio.log.Logger;
import org.xnio.channels.Configurable;
import org.xnio.channels.UnsupportedOptionException;

/**
 *
 */
class NioUdpChannel implements MulticastMessageChannel {

    private static final Logger log = Logger.getLogger("org.xnio.nio.udp.server.channel");

    private final DatagramChannel datagramChannel;
    private final NioHandle readHandle;
    private final NioHandle writeHandle;

    private volatile ChannelListener<? super MulticastMessageChannel> readListener = null;
    private volatile ChannelListener<? super MulticastMessageChannel> writeListener = null;
    private volatile ChannelListener<? super MulticastMessageChannel> closeListener = null;

    private static final AtomicReferenceFieldUpdater<NioUdpChannel, ChannelListener> readListenerUpdater = AtomicReferenceFieldUpdater.newUpdater(NioUdpChannel.class, ChannelListener.class, "readListener");
    private static final AtomicReferenceFieldUpdater<NioUdpChannel, ChannelListener> writeListenerUpdater = AtomicReferenceFieldUpdater.newUpdater(NioUdpChannel.class, ChannelListener.class, "writeListener");
    private static final AtomicReferenceFieldUpdater<NioUdpChannel, ChannelListener> closeListenerUpdater = AtomicReferenceFieldUpdater.newUpdater(NioUdpChannel.class, ChannelListener.class, "closeListener");

    private final ChannelListener.Setter<MulticastMessageChannel> readSetter = IoUtils.getSetter(this, readListenerUpdater);
    private final ChannelListener.Setter<MulticastMessageChannel> writeSetter = IoUtils.getSetter(this, writeListenerUpdater);
    private final ChannelListener.Setter<MulticastMessageChannel> closeSetter = IoUtils.getSetter(this, closeListenerUpdater);

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

    NioUdpChannel(final NioXnio nioXnio, final DatagramChannel datagramChannel, final Executor executor, final AtomicLong globalBytesRead, final AtomicLong globalBytesWritten, final AtomicLong globalMessagesRead, final AtomicLong globalMessagesWritten) throws IOException {
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
    }

    public InetSocketAddress getLocalAddress() {
        return (InetSocketAddress) datagramChannel.socket().getLocalSocketAddress();
    }

    public MultipointReadResult<InetSocketAddress> receive(final ByteBuffer buffer) throws IOException {
        final int o = buffer.remaining();
        final InetSocketAddress sourceAddress = (InetSocketAddress) datagramChannel.receive(buffer);
        if (sourceAddress == null) {
            return null;
        } else {
            final int t = o - buffer.remaining();
            globalMessagesRead.incrementAndGet();
            messagesRead.incrementAndGet();
            globalBytesRead.addAndGet((long) t);
            bytesRead.addAndGet((long) t);
            return new MultipointReadResult<InetSocketAddress>() {
                public InetSocketAddress getSourceAddress() {
                    return sourceAddress;
                }

                public InetSocketAddress getDestinationAddress() {
                    return null;
                }
            };
        }
    }

    public ChannelListener.Setter<MulticastMessageChannel> getReadSetter() {
        return readSetter;
    }

    public ChannelListener.Setter<MulticastMessageChannel> getWriteSetter() {
        return writeSetter;
    }

    public ChannelListener.Setter<MulticastMessageChannel> getCloseSetter() {
        return closeSetter;
    }

    public boolean flush() throws IOException {
        return true;
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
                IoUtils.<MulticastMessageChannel>invokeChannelListener(this, closeListener);
            }
        }
    }

    public boolean send(final InetSocketAddress target, final ByteBuffer buffer) throws IOException {
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

    public boolean send(final InetSocketAddress target, final ByteBuffer[] dsts) throws IOException {
        return send(target, dsts, 0, dsts.length);
    }

    public boolean send(final InetSocketAddress target, final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
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

    public boolean shutdownWrites() throws IOException {
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

    private static final Set<Option<?>> OPTIONS = Option.setBuilder()
            .add(Options.BROADCAST)
            .add(Options.RECEIVE_BUFFER)
            .add(Options.SEND_BUFFER)
            .add(Options.IP_TRAFFIC_CLASS)
            .create();

    public boolean supportsOption(final Option<?> option) {
        return OPTIONS.contains(option);
    }

    public <T> T getOption(final Option<T> option) throws UnsupportedOptionException, IOException {
        final DatagramSocket socket = datagramChannel.socket();
        if (option == Options.RECEIVE_BUFFER) {
            return option.cast(Integer.valueOf(socket.getReceiveBufferSize()));
        } else if (option == Options.SEND_BUFFER) {
            return option.cast(Integer.valueOf(socket.getSendBufferSize()));
        } else if (option == Options.BROADCAST) {
            return option.cast(Boolean.valueOf(socket.getBroadcast()));
        } else if (option == Options.IP_TRAFFIC_CLASS) {
            return option.cast(Integer.valueOf(socket.getTrafficClass()));
        } else {
            return null;
        }
    }

    public <T> Configurable setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        final DatagramSocket socket = datagramChannel.socket();
        if (option == Options.RECEIVE_BUFFER) {
            socket.setReceiveBufferSize(((Integer) value).intValue());
        } else if (option == Options.SEND_BUFFER) {
            socket.setSendBufferSize(((Integer) value).intValue());
        } else if (option == Options.IP_TRAFFIC_CLASS) {
            socket.setTrafficClass(((Integer) value).intValue());
        } else if (option == Options.BROADCAST) {
            socket.setBroadcast(((Boolean) value).booleanValue());
        }
        return this;
    }

    public final class ReadHandler implements Runnable {
        public void run() {
            IoUtils.<MulticastMessageChannel>invokeChannelListener(NioUdpChannel.this, readListener);
        }
    }

    public final class WriteHandler implements Runnable {
        public void run() {
            IoUtils.<MulticastMessageChannel>invokeChannelListener(NioUdpChannel.this, writeListener);
        }
    }

    @Override
    public String toString() {
        return String.format("UDP socket channel (NIO) <%s> @ %s", Integer.toString(hashCode(), 16), getLocalAddress());
    }
}
