/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc.


package org.xnio.nio;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.xnio.Option;
import org.xnio.Options;
import org.xnio.channels.MulticastMessageChannel;
import org.xnio.channels.UnsupportedOptionException;

/**
 *
 */
class BioMulticastUdpChannel extends BioDatagramUdpChannel implements MulticastMessageChannel {
    private final MulticastSocket multicastSocket;

    BioMulticastUdpChannel(final NioXnioWorker worker, final int sendBufSize, final int recvBufSize, final MulticastSocket multicastSocket, final WorkerThread readThread, final WorkerThread writeThread) {
        super(worker, sendBufSize, recvBufSize, multicastSocket, readThread, writeThread);
        this.multicastSocket = multicastSocket;
    }

    public Key join(final InetAddress group, final NetworkInterface iface) throws IOException {
        return new BioKey(iface, group);
    }

    public Key join(final InetAddress group, final NetworkInterface iface, final InetAddress source) throws IOException {
        throw new UnsupportedOperationException("source filtering not supported");
    }

    private static final Set<Option<?>> OPTIONS = Option.setBuilder()
            .add(Options.MULTICAST_TTL)
            .create();

    public boolean supportsOption(final Option<?> option) {
        return OPTIONS.contains(option) || super.supportsOption(option);
    }

    @SuppressWarnings({"unchecked"})
    public <T> T getOption(final Option<T> option) throws UnsupportedOptionException, IOException {
        if (Options.MULTICAST_TTL.equals(option)) {
            return (T) Integer.valueOf(multicastSocket.getTimeToLive());
        } else {
            return super.getOption(option);
        }
    }

    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        final Object old;
        if (Options.MULTICAST_TTL.equals(option)) {
            old = Integer.valueOf(multicastSocket.getTimeToLive());
            multicastSocket.setTimeToLive(((Integer)value).intValue());
        } else {
            return super.setOption(option, value);
        }
        return option.cast(old);
    }

    private final class BioKey implements Key {

        private final AtomicBoolean openFlag = new AtomicBoolean(true);
        private final NetworkInterface networkInterface;
        private final InetAddress group;

        private BioKey(final NetworkInterface networkInterface, final InetAddress group) throws IOException {
            this.networkInterface = networkInterface;
            this.group = group;
            multicastSocket.joinGroup(new InetSocketAddress(group, 0), networkInterface);
        }

        public Key block(final InetAddress source) throws IOException {
            throw new UnsupportedOperationException("source filtering not supported");
        }

        public Key unblock(final InetAddress source) throws IOException {
            // no operation
            return this;
        }

        public MulticastMessageChannel getChannel() {
            return BioMulticastUdpChannel.this;
        }

        public InetAddress getGroup() {
            return group;
        }

        public NetworkInterface getNetworkInterface() {
            return networkInterface;
        }

        public InetAddress getSourceAddress() {
            return null;
        }

        public boolean isOpen() {
            return openFlag.get();
        }

        public void close() throws IOException {
            if (openFlag.getAndSet(false)) {
                multicastSocket.leaveGroup(new InetSocketAddress(group, 0), networkInterface);
            }
        }
    }
}
