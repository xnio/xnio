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

import org.jboss.xnio.channels.UdpChannel;
import org.jboss.xnio.channels.MultipointDatagramChannel;
import org.jboss.xnio.channels.UnsupportedOptionException;
import org.jboss.xnio.channels.ChannelOption;
import org.jboss.xnio.IoHandler;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.MulticastSocket;
import java.net.InetSocketAddress;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

/**
 *
 */
public final class BioMulticastChannelImpl extends BioDatagramChannelImpl implements UdpChannel {
    private final MulticastSocket multicastSocket;

    @SuppressWarnings({"unchecked"})
    protected BioMulticastChannelImpl(final int sendBufSize, final int recvBufSize, final Executor handlerExecutor, final IoHandler<? super UdpChannel> handler, final MulticastSocket multicastSocket) {
        super(sendBufSize, recvBufSize, handlerExecutor, (IoHandler<? super MultipointDatagramChannel<SocketAddress>>) handler, multicastSocket);
        this.multicastSocket = multicastSocket;
    }

    public Key join(final InetAddress group, final NetworkInterface iface) throws IOException {
        return new BioKey(iface, group);
    }

    public Key join(final InetAddress group, final NetworkInterface iface, final InetAddress source) throws IOException {
        throw new UnsupportedOperationException("source filtering not supported");
    }

    private static final Map<String, Class<?>> OPTIONS;

    static {
        final Map<String, Class<?>> options = new HashMap<String, Class<?>>(BioDatagramChannelImpl.OPTIONS);
        options.put(ChannelOption.MULTICAST_TTL, Integer.class);
        OPTIONS = Collections.unmodifiableMap(options);
    }

    public Object getOption(final String name) throws UnsupportedOptionException, IOException {
        if (! OPTIONS.containsKey(name)) {
            throw new UnsupportedOptionException("Option not supported: " + name);
        }
        if (ChannelOption.MULTICAST_TTL.equals(name)) {
            return Integer.valueOf(multicastSocket.getTimeToLive());
        } else {
            return super.getOption(name);
        }
    }

    public Map<String, Class<?>> getOptions() {
        return OPTIONS;
    }

    public UdpChannel setOption(final String name, final Object value) throws IllegalArgumentException, IOException {
        if (! OPTIONS.containsKey(name)) {
            throw new UnsupportedOptionException("Option not supported: " + name);
        }
        if (ChannelOption.MULTICAST_TTL.equals(name)) {
            multicastSocket.setTimeToLive(((Integer)value).intValue());
            return this;
        } else {
            return super.setOption(name, value);
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
            return BioMulticastChannelImpl.this;
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
