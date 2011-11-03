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
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.MembershipKey;
import java.nio.channels.SelectionKey;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.logging.Logger;
import org.xnio.Buffers;
import org.xnio.ChannelListeners;
import org.xnio.Option;
import org.xnio.ChannelListener;
import org.xnio.Options;
import org.xnio.XnioWorker;
import org.xnio.channels.MulticastMessageChannel;
import org.xnio.channels.SocketAddressBuffer;
import org.xnio.channels.UnsupportedOptionException;

import static org.xnio.ChannelListener.SimpleSetter;
import static org.xnio.nio.Log.log;

/**
 *
 */
class NioUdpChannel implements MulticastMessageChannel {

    private static final Logger log = Logger.getLogger("org.xnio.nio.udp.server.channel");

    private final NioXnioWorker worker;

    private final NioHandle<NioUdpChannel> readHandle;
    private final NioHandle<NioUdpChannel> writeHandle;

    private final SimpleSetter<NioUdpChannel> readSetter = new SimpleSetter<NioUdpChannel>();
    private final SimpleSetter<NioUdpChannel> writeSetter = new SimpleSetter<NioUdpChannel>();
    private final SimpleSetter<NioUdpChannel> closeSetter = new SimpleSetter<NioUdpChannel>();

    private final DatagramChannel datagramChannel;

    private final AtomicBoolean callFlag = new AtomicBoolean(false);

    NioUdpChannel(final NioXnioWorker worker, final DatagramChannel datagramChannel) throws ClosedChannelException {
        this.worker = worker;
        final WorkerThread readThread = worker.chooseOptional(false);
        final WorkerThread writeThread = worker.chooseOptional(true);
        readHandle = readThread == null ? null : readThread.addChannel(datagramChannel, this, 0, readSetter);
        writeHandle = writeThread == null ? null : writeThread.addChannel(datagramChannel, this, 0, writeSetter);
        this.datagramChannel = datagramChannel;
    }

    public SocketAddress getLocalAddress() {
        return datagramChannel.socket().getLocalSocketAddress();
    }

    public <A extends SocketAddress> A getLocalAddress(final Class<A> type) {
        return type.isInstance(getLocalAddress()) ? type.cast(getLocalAddress()) : null;
    }

    public int receiveFrom(final SocketAddressBuffer addressBuffer, final ByteBuffer buffer) throws IOException {
        final int o = buffer.remaining();
        final SocketAddress sourceAddress = datagramChannel.receive(buffer);
        if (sourceAddress == null) {
            return 0;
        } else {
            final int t = o - buffer.remaining();
            if (addressBuffer != null) {
                addressBuffer.setSourceAddress(sourceAddress);
                addressBuffer.setDestinationAddress(null);
            }
            return t;
        }
    }

    public long receiveFrom(final SocketAddressBuffer addressBuffer, final ByteBuffer[] buffers) throws IOException {
        return receiveFrom(addressBuffer, buffers, 0, buffers.length);
    }

    public long receiveFrom(final SocketAddressBuffer addressBuffer, final ByteBuffer[] buffers, final int offs, final int len) throws IOException {
        if (len == 0) {
            return 0L;
        }
        if (len == 1) {
            return receiveFrom(addressBuffer, buffers[offs]);
        }
        final int o = (int) Math.min(Buffers.remaining(buffers, offs, len), 65536L);
        final ByteBuffer buffer = ByteBuffer.allocate((int) o);
        final SocketAddress sourceAddress = datagramChannel.receive(buffer);
        if (sourceAddress == null) {
            return 0L;
        } else {
            final int t = o - buffer.remaining();
            buffer.flip();
            Buffers.copy(buffers, offs, len, buffer);
            if (addressBuffer != null) {
                addressBuffer.setSourceAddress(sourceAddress);
                addressBuffer.setDestinationAddress(null);
            }
            return t;
        }
    }

    public boolean sendTo(final SocketAddress target, final ByteBuffer buffer) throws IOException {
        return datagramChannel.send(buffer, target) != 0;
    }

    public boolean sendTo(final SocketAddress target, final ByteBuffer[] buffers) throws IOException {
        return sendTo(target, buffers, 0, buffers.length);
    }

    public boolean sendTo(final SocketAddress target, final ByteBuffer[] buffers, final int offset, final int length) throws IOException {
        if (length == 0) {
            return false;
        }
        if (length == 1) {
            return sendTo(target, buffers[offset]);
        }
        final long o = Buffers.remaining(buffers, offset, length);
        if (o > 65535L) {
            // there will never be enough room
            throw new IllegalArgumentException("Too may bytes written");
        }
        final ByteBuffer buffer = ByteBuffer.allocate((int) o);
        Buffers.copy(buffer, buffers, offset, length);
        buffer.flip();
        return datagramChannel.send(buffer, target) != 0;
    }

    public ChannelListener.Setter<NioUdpChannel> getReadSetter() {
        return readSetter;
    }

    public ChannelListener.Setter<NioUdpChannel> getWriteSetter() {
        return writeSetter;
    }

    public ChannelListener.Setter<NioUdpChannel> getCloseSetter() {
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
            log.tracef("Closing %s", this);
            try {
                datagramChannel.close();
            } finally {
                cancelKeys();
                ChannelListeners.<NioUdpChannel>invokeChannelListener(this, closeSetter.get());
            }
        }
    }

    private void cancelKeys() {
        if (readHandle != null) {
            readHandle.cancelKey();
        }
        if (writeHandle != null) {
            writeHandle.cancelKey();
        }
    }

    public void suspendReads() {
        final NioHandle<NioUdpChannel> handle = readHandle;
        if (handle != null) try {
            handle.suspend();
        } catch (CancelledKeyException ex) {
            // ignore
        }
    }

    public void suspendWrites() {
        final NioHandle<NioUdpChannel> handle = writeHandle;
        if (handle != null) try {
            handle.suspend();
        } catch (CancelledKeyException ex) {
            // ignore
        }
    }

    public void resumeReads() {
        final NioHandle<NioUdpChannel> handle = readHandle;
        if (handle == null) {
            throw new IllegalArgumentException("No read thread configured");
        }
        try {
            handle.resume(SelectionKey.OP_READ);
        } catch (CancelledKeyException ex) {
            // ignore
        }
    }

    public void resumeWrites() {
        final NioHandle<NioUdpChannel> handle = writeHandle;
        if (handle == null) {
            throw new IllegalArgumentException("No read thread configured");
        }
        try {
            handle.resume(SelectionKey.OP_WRITE);
        } catch (CancelledKeyException ex) {
            // ignore
        }
    }

    public boolean isReadResumed() {
        final NioHandle<NioUdpChannel> handle = readHandle;
        return handle != null && handle.isResumed(SelectionKey.OP_READ);
    }

    public boolean isWriteResumed() {
        final NioHandle<NioUdpChannel> handle = writeHandle;
        return handle != null && handle.isResumed(SelectionKey.OP_WRITE);
    }

    public void wakeupReads() {
        final NioHandle<NioUdpChannel> readHandle = this.readHandle;
        if (readHandle == null) {
            throw new IllegalArgumentException("No thread configured");
        }
        readHandle.execute();
    }

    public void wakeupWrites() {
        final NioHandle<NioUdpChannel> writeHandle = this.writeHandle;
        if (writeHandle == null) {
            throw new IllegalArgumentException("No thread configured");
        }
        writeHandle.execute();
    }

    public void shutdownReads() throws IOException {
        throw new UnsupportedOperationException("Shutdown reads");
    }

    public boolean shutdownWrites() throws IOException {
        throw new UnsupportedOperationException("Shutdown writes");
    }

    public void awaitReadable() throws IOException {
        SelectorUtils.await(worker.getXnio(), datagramChannel, SelectionKey.OP_READ);
    }

    public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
        SelectorUtils.await(worker.getXnio(), datagramChannel, SelectionKey.OP_READ, time, timeUnit);
    }

    public void awaitWritable() throws IOException {
        SelectorUtils.await(worker.getXnio(), datagramChannel, SelectionKey.OP_WRITE);
    }

    public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
        SelectorUtils.await(worker.getXnio(), datagramChannel, SelectionKey.OP_WRITE, time, timeUnit);
    }

    public Key join(final InetAddress group, final NetworkInterface iface) throws IOException {
        return new NioKey(datagramChannel.join(group, iface));
    }

    public Key join(final InetAddress group, final NetworkInterface iface, final InetAddress source) throws IOException {
        return new NioKey(datagramChannel.join(group, iface, source));
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
        final DatagramChannel channel = datagramChannel;
        final DatagramSocket socket = channel.socket();
        if (option == Options.RECEIVE_BUFFER) {
            return option.cast(Integer.valueOf(socket.getReceiveBufferSize()));
        } else if (option == Options.SEND_BUFFER) {
            return option.cast(Integer.valueOf(socket.getSendBufferSize()));
        } else if (option == Options.BROADCAST) {
            return option.cast(Boolean.valueOf(socket.getBroadcast()));
        } else if (option == Options.IP_TRAFFIC_CLASS) {
            return option.cast(Integer.valueOf(socket.getTrafficClass()));
        } else {
            if (NioXnio.NIO2) {
                if (option == Options.MULTICAST_TTL) {
                    return option.cast(channel.getOption(StandardSocketOptions.IP_MULTICAST_TTL));
                } else {
                    return null;
                }
            } else {
                return null;
            }
        }
    }

    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        final DatagramChannel channel = datagramChannel;
        final DatagramSocket socket = channel.socket();
        final Object old;
        if (option == Options.RECEIVE_BUFFER) {
            old = Integer.valueOf(socket.getReceiveBufferSize());
            socket.setReceiveBufferSize(((Integer) value).intValue());
        } else if (option == Options.SEND_BUFFER) {
            old = Integer.valueOf(socket.getSendBufferSize());
            socket.setSendBufferSize(((Integer) value).intValue());
        } else if (option == Options.IP_TRAFFIC_CLASS) {
            old = Integer.valueOf(socket.getTrafficClass());
            socket.setTrafficClass(((Integer) value).intValue());
        } else if (option == Options.BROADCAST) {
            old = Boolean.valueOf(socket.getBroadcast());
            socket.setBroadcast(((Boolean) value).booleanValue());
        } else {
            if (NioXnio.NIO2) {
                if (option == Options.MULTICAST_TTL) {
                    old = option.cast(channel.getOption(StandardSocketOptions.IP_MULTICAST_TTL));
                    channel.setOption(StandardSocketOptions.IP_MULTICAST_TTL, (Integer) value);
                } else {
                    return null;
                }
            } else {
                return null;
            }
        }
        return option.cast(old);
    }

    @Override
    public String toString() {
        return String.format("UDP socket channel (NIO) <%h>", this);
    }

    class NioKey implements Key {

        private final MembershipKey key;

        NioKey(final MembershipKey key) {
            this.key = key;
        }

        public Key block(final InetAddress source) throws IOException, UnsupportedOperationException, IllegalStateException, IllegalArgumentException {
            key.block(source);
            return this;
        }

        public Key unblock(final InetAddress source) throws IOException, IllegalStateException, UnsupportedOperationException {
            key.unblock(source);
            return this;
        }

        public MulticastMessageChannel getChannel() {
            return NioUdpChannel.this;
        }

        public InetAddress getGroup() {
            return key.group();
        }

        public NetworkInterface getNetworkInterface() {
            return key.networkInterface();
        }

        public InetAddress getSourceAddress() {
            return key.sourceAddress();
        }

        public boolean isOpen() {
            return key.isValid();
        }

        public void close() throws IOException {
            key.drop();
        }
    }

    public XnioWorker getWorker() {
        return worker;
    }
}
