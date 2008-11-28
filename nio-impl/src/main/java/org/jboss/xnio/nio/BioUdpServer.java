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
import java.net.MulticastSocket;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.Executor;
import java.nio.channels.ClosedChannelException;
import org.jboss.xnio.IoHandler;
import org.jboss.xnio.IoHandlerFactory;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.channels.ChannelOption;
import org.jboss.xnio.channels.CommonOptions;
import org.jboss.xnio.channels.Configurable;
import org.jboss.xnio.channels.UdpChannel;
import org.jboss.xnio.channels.UnsupportedOptionException;
import org.jboss.xnio.channels.BoundServer;
import org.jboss.xnio.log.Logger;

/**
 *
 */
public final class BioUdpServer implements BoundServer<SocketAddress, UdpChannel> {
    private static final Logger log = Logger.getLogger("org.jboss.xnio.nio.udp.bio-server");

    private final IoHandlerFactory<? super UdpChannel> handlerFactory;
    private final Executor executor;

    private final Object lock = new Object();
    private final Set<BioMulticastChannelImpl> boundChannels = new LinkedHashSet<BioMulticastChannelImpl>();

    private boolean closed;

    private Boolean reuseAddress;
    private Integer receiveBufferSize;
    private Integer sendBufferSize;
    private Integer trafficClass;
    private Boolean broadcast;

    BioUdpServer(final BioUdpServerConfig config) {
        synchronized (lock) {
            handlerFactory = config.getHandlerFactory();
            executor = config.getExecutor();
            reuseAddress = config.getReuseAddresses();
            receiveBufferSize = config.getReceiveBuffer();
            sendBufferSize = config.getSendBuffer();
            trafficClass = config.getTrafficClass();
            broadcast = config.getBroadcast();
        }
    }

    static BioUdpServer create(final BioUdpServerConfig config) {
        return new BioUdpServer(config);
    }

    protected static final Set<ChannelOption<?>> OPTIONS;

    static {
        final Set<ChannelOption<?>> options = new HashSet<ChannelOption<?>>();
        options.add(CommonOptions.RECEIVE_BUFFER);
        options.add(CommonOptions.REUSE_ADDRESSES);
        options.add(CommonOptions.SEND_BUFFER);
        options.add(CommonOptions.IP_TRAFFIC_CLASS);
        options.add(CommonOptions.BROADCAST);
        OPTIONS = Collections.unmodifiableSet(options);
    }

    public <T> T getOption(final ChannelOption<T> option) throws UnsupportedOptionException, IOException {
        if (CommonOptions.RECEIVE_BUFFER.equals(option)) {
            return option.getType().cast(receiveBufferSize);
        } else if (CommonOptions.REUSE_ADDRESSES.equals(option)) {
            return option.getType().cast(reuseAddress);
        } else if (CommonOptions.SEND_BUFFER.equals(option)) {
            return option.getType().cast(sendBufferSize);
        } else if (CommonOptions.IP_TRAFFIC_CLASS.equals(option)) {
            return option.getType().cast(trafficClass);
        } else if (CommonOptions.BROADCAST.equals(option)) {
            return option.getType().cast(broadcast);
        } else {
            throw new UnsupportedOptionException("Option not supported: " + option);
        }
    }

    public Set<ChannelOption<?>> getOptions() {
        return OPTIONS;
    }

    public <T> Configurable setOption(final ChannelOption<T> option, final T value) throws IllegalArgumentException, IOException {
        if (! OPTIONS.contains(option)) {
            throw new UnsupportedOptionException("Option not supported: " + option);
        }
        if (CommonOptions.RECEIVE_BUFFER.equals(option)) {
            receiveBufferSize = CommonOptions.RECEIVE_BUFFER.getType().cast(value);
            return this;
        } else if (CommonOptions.REUSE_ADDRESSES.equals(option)) {
            reuseAddress = CommonOptions.REUSE_ADDRESSES.getType().cast(value);
            return this;
        } else if (CommonOptions.SEND_BUFFER.equals(option)) {
            sendBufferSize = CommonOptions.SEND_BUFFER.getType().cast(value);
            return this;
        } else if (CommonOptions.IP_TRAFFIC_CLASS.equals(option)) {
            trafficClass = CommonOptions.IP_TRAFFIC_CLASS.getType().cast(value);
            return this;
        } else if (CommonOptions.BROADCAST.equals(option)) {
            broadcast = CommonOptions.BROADCAST.getType().cast(value);
            return this;
        } else {
            throw new IllegalStateException("Failed to set supported option: " + option);
        }
    }

    public Collection<UdpChannel> getChannels() {
        synchronized (lock) {
            return new ArrayList<UdpChannel>(boundChannels);
        }
    }

    public UdpChannel bind(final SocketAddress address) throws IOException {
        synchronized (lock) {
            if (closed) {
                throw new ClosedChannelException();
            }
            MulticastSocket socket = new MulticastSocket(address);
            if (broadcast != null) socket.setBroadcast(broadcast.booleanValue());
            if (receiveBufferSize != null) socket.setReceiveBufferSize(receiveBufferSize.intValue());
            if (sendBufferSize != null) socket.setSendBufferSize(sendBufferSize.intValue());
            if (reuseAddress != null) socket.setReuseAddress(reuseAddress.booleanValue());
            if (trafficClass != null) socket.setTrafficClass(trafficClass.intValue());
            //noinspection unchecked
            final IoHandler<? super UdpChannel> handler = handlerFactory.createHandler();
            final BioMulticastChannelImpl channel = new BioMulticastChannelImpl(socket.getSendBufferSize(), socket.getReceiveBufferSize(), executor, handler, socket);
            boundChannels.add(channel);
            return channel;
        }
    }

    public void close() throws IOException {
        synchronized (lock) {
            if (! closed) {
                log.trace("Closing %s", this);
                closed = true;
                final Iterator<BioMulticastChannelImpl> it = boundChannels.iterator();
                while (it.hasNext()) {
                    IoUtils.safeClose(it.next());
                    it.remove();
                }
            }
        }
    }
}
