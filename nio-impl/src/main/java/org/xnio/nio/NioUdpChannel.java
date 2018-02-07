/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.xnio.nio;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.MembershipKey;
import java.nio.channels.SelectionKey;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.xnio.Buffers;
import org.xnio.Option;
import org.xnio.ChannelListener;
import org.xnio.Options;
import org.xnio.XnioExecutor;
import org.xnio.channels.MulticastMessageChannel;
import org.xnio.channels.ReadListenerSettable;
import org.xnio.channels.SocketAddressBuffer;
import org.xnio.channels.UnsupportedOptionException;
import org.xnio.channels.WriteListenerSettable;

import static org.xnio.Xnio.NIO2;
import static org.xnio.nio.Log.log;
import static org.xnio.nio.Log.udpServerChannelLog;

/**
 *
 */
class NioUdpChannel extends AbstractNioChannel<NioUdpChannel> implements MulticastMessageChannel, ReadListenerSettable<NioUdpChannel>, WriteListenerSettable<NioUdpChannel> {

    private final NioUdpChannelHandle handle;

    private ChannelListener<? super NioUdpChannel> readListener;
    private ChannelListener<? super NioUdpChannel> writeListener;

    private final DatagramChannel datagramChannel;

    private final AtomicBoolean callFlag = new AtomicBoolean(false);

    NioUdpChannel(final NioXnioWorker worker, final DatagramChannel datagramChannel) throws ClosedChannelException {
        super(worker);
        this.datagramChannel = datagramChannel;
        final WorkerThread workerThread = worker.chooseThread();
        final SelectionKey key = workerThread.registerChannel(datagramChannel);
        handle = new NioUdpChannelHandle(workerThread, key, this);
        key.attach(handle);
    }

    public SocketAddress getLocalAddress() {
        return datagramChannel.socket().getLocalSocketAddress();
    }

    public <A extends SocketAddress> A getLocalAddress(final Class<A> type) {
        return type.isInstance(getLocalAddress()) ? type.cast(getLocalAddress()) : null;
    }

    public int receiveFrom(final SocketAddressBuffer addressBuffer, final ByteBuffer buffer) throws IOException {
        final int o = buffer.remaining();
        final SocketAddress sourceAddress;
        try {
            sourceAddress = datagramChannel.receive(buffer);
        } catch (ClosedChannelException e) {
            return -1;
        }
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
        final ByteBuffer buffer = ByteBuffer.allocate(o);
        final SocketAddress sourceAddress;
        try {
            sourceAddress = datagramChannel.receive(buffer);
        } catch (ClosedChannelException e) {
            return -1L;
        }
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
            throw log.bufferTooLarge();
        }
        final ByteBuffer buffer = ByteBuffer.allocate((int) o);
        Buffers.copy(buffer, buffers, offset, length);
        buffer.flip();
        return datagramChannel.send(buffer, target) != 0;
    }

    public ChannelListener<? super NioUdpChannel> getReadListener() {
        return readListener;
    }

    public void setReadListener(final ChannelListener<? super NioUdpChannel> readListener) {
        this.readListener = readListener;
    }

    public ChannelListener<? super NioUdpChannel> getWriteListener() {
        return writeListener;
    }

    public void setWriteListener(final ChannelListener<? super NioUdpChannel> writeListener) {
        this.writeListener = writeListener;
    }

    public ChannelListener.Setter<NioUdpChannel> getReadSetter() {
        return new ReadListenerSettable.Setter<NioUdpChannel>(this);
    }

    public ChannelListener.Setter<NioUdpChannel> getWriteSetter() {
        return new WriteListenerSettable.Setter<NioUdpChannel>(this);
    }

    public boolean flush() throws IOException {
        return true;
    }

    public boolean isOpen() {
        return datagramChannel.isOpen();
    }

    public void close() throws IOException {
        if (!callFlag.getAndSet(true)) {
            udpServerChannelLog.tracef("Closing %s", this);
            try { cancelKeys(); } catch (Throwable ignored) {}
            try {
                datagramChannel.close();
            } finally {
                invokeCloseHandler();
            }
        }
    }

    private void cancelKeys() {
        try { handle.cancelKey(false); } catch (Throwable ignored) {}
    }

    public void suspendReads() {
        handle.suspend(SelectionKey.OP_READ);
    }

    public void suspendWrites() {
        handle.suspend(SelectionKey.OP_WRITE);
    }

    public void resumeReads() {
        handle.resume(SelectionKey.OP_READ);
    }

    public void resumeWrites() {
        handle.resume(SelectionKey.OP_WRITE);
    }

    public boolean isReadResumed() {
        return handle.isResumed(SelectionKey.OP_READ);
    }

    public boolean isWriteResumed() {
        return handle.isResumed(SelectionKey.OP_WRITE);
    }

    public void wakeupReads() {
        handle.wakeup(SelectionKey.OP_READ);
    }

    public void wakeupWrites() {
        handle.wakeup(SelectionKey.OP_WRITE);
    }

    public void shutdownReads() throws IOException {
        throw log.unsupported("shutdownReads");
    }

    public void shutdownWrites() throws IOException {
        throw log.unsupported("shutdownWrites");
    }

    public void awaitReadable() throws IOException {
        SelectorUtils.await(worker.getXnio(), datagramChannel, SelectionKey.OP_READ);
    }

    public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
        SelectorUtils.await(worker.getXnio(), datagramChannel, SelectionKey.OP_READ, time, timeUnit);
    }

    @Deprecated
    public XnioExecutor getReadThread() {
        return getIoThread();
    }

    public void awaitWritable() throws IOException {
        SelectorUtils.await(worker.getXnio(), datagramChannel, SelectionKey.OP_WRITE);
    }

    public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
        SelectorUtils.await(worker.getXnio(), datagramChannel, SelectionKey.OP_WRITE, time, timeUnit);
    }

    @Deprecated
    public XnioExecutor getWriteThread() {
        return getIoThread();
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
            .add(Options.MULTICAST_TTL)
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
        } else if (option == Options.MULTICAST_TTL) {
            return option.cast(channel.getOption(StandardSocketOptions.IP_MULTICAST_TTL));
        } else {
            return null;
        }
    }

    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        final DatagramChannel channel = datagramChannel;
        final DatagramSocket socket = channel.socket();
        final Object old;
        if (option == Options.RECEIVE_BUFFER) {
            old = Integer.valueOf(socket.getReceiveBufferSize());
            int newValue = Options.RECEIVE_BUFFER.cast(value, Integer.valueOf(DEFAULT_BUFFER_SIZE)).intValue();
            if (newValue < 1) {
                throw log.optionOutOfRange("RECEIVE_BUFFER");
            }
            socket.setReceiveBufferSize(newValue);
        } else if (option == Options.SEND_BUFFER) {
            old = Integer.valueOf(socket.getSendBufferSize());
            int newValue = Options.SEND_BUFFER.cast(value, Integer.valueOf(DEFAULT_BUFFER_SIZE)).intValue();
            if (newValue < 1) {
                throw log.optionOutOfRange("SEND_BUFFER");
            }
            socket.setSendBufferSize(newValue);
        } else if (option == Options.IP_TRAFFIC_CLASS) {
            old = Integer.valueOf(socket.getTrafficClass());
            socket.setTrafficClass(Options.IP_TRAFFIC_CLASS.cast(value, Integer.valueOf(0)).intValue());
        } else if (option == Options.BROADCAST) {
            old = Boolean.valueOf(socket.getBroadcast());
            socket.setBroadcast(Options.BROADCAST.cast(value, Boolean.FALSE).booleanValue());
        } else if (option == Options.MULTICAST_TTL) {
            old = option.cast(channel.getOption(StandardSocketOptions.IP_MULTICAST_TTL));
            channel.setOption(StandardSocketOptions.IP_MULTICAST_TTL, (Integer) value);
        } else {
            return null;
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
}
