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
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.jboss.xnio.Option;
import org.jboss.xnio.channels.CommonOptions;
import org.jboss.xnio.channels.UdpChannel;
import org.jboss.xnio.channels.UnsupportedOptionException;

/**
 *
 */
class BioMulticastUdpChannel extends BioDatagramUdpChannel implements UdpChannel {
    private final MulticastSocket multicastSocket;

    @SuppressWarnings({"unchecked"})
    BioMulticastUdpChannel(final int sendBufSize, final int recvBufSize, final Executor handlerExecutor, final MulticastSocket multicastSocket, final AtomicLong globalBytesRead, final AtomicLong globalBytesWritten, final AtomicLong globalMessagesRead, final AtomicLong globalMessagesWritten) {
        super(sendBufSize, recvBufSize, handlerExecutor, multicastSocket, globalBytesRead, globalBytesWritten, globalMessagesRead, globalMessagesWritten);
        this.multicastSocket = multicastSocket;
    }

    public Key join(final InetAddress group, final NetworkInterface iface) throws IOException {
        return new BioKey(iface, group);
    }

    public Key join(final InetAddress group, final NetworkInterface iface, final InetAddress source) throws IOException {
        throw new UnsupportedOperationException("source filtering not supported");
    }

    private static final Set<Option<?>> OPTIONS = Option.setBuilder()
            .add(CommonOptions.MULTICAST_TTL)
            .create();

    public boolean supportsOption(final Option<?> option) {
        return OPTIONS.contains(option) || super.supportsOption(option);
    }

    @SuppressWarnings({"unchecked"})
    public <T> T getOption(final Option<T> option) throws UnsupportedOptionException, IOException {
        if (CommonOptions.MULTICAST_TTL.equals(option)) {
            return (T) Integer.valueOf(multicastSocket.getTimeToLive());
        } else {
            return super.getOption(option);
        }
    }

    public <T> BioMulticastUdpChannel setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        if (CommonOptions.MULTICAST_TTL.equals(option)) {
            multicastSocket.setTimeToLive(((Integer)value).intValue());
            return this;
        } else {
            super.setOption(option, value);
            return this;
        }
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

        public UdpChannel getChannel() {
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
