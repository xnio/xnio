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

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.nio.channels.DatagramChannel;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import org.jboss.xnio.IoHandlerFactory;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.channels.ChannelOption;
import org.jboss.xnio.channels.CommonOptions;
import org.jboss.xnio.channels.Configurable;
import org.jboss.xnio.channels.UdpChannel;
import org.jboss.xnio.channels.UnsupportedOptionException;
import org.jboss.xnio.log.Logger;
import org.jboss.xnio.spi.Lifecycle;
import org.jboss.xnio.spi.SpiUtils;
import org.jboss.xnio.spi.UdpServerService;

/**
 *
 */
public final class NioUdpServer implements Lifecycle, UdpServerService {

    private static Logger log = Logger.getLogger(NioUdpServer.class);

    private NioProvider nioProvider;
    private IoHandlerFactory<? super UdpChannel> handlerFactory;
    private NioUdpSocketChannelImpl[] channels = new NioUdpSocketChannelImpl[0];
    private SocketAddress[] bindAddresses = new SocketAddress[0];
    private Executor executor;

    private int receiveBufferSize = -1;
    private boolean reuseAddress = false;
    private int sendBufferSize = -1;
    private int trafficClass = -1;
    private boolean broadcast = false;

    public int getReceiveBufferSize() {
        return receiveBufferSize;
    }

    public void setReceiveBufferSize(final int receiveBufferSize) {
        this.receiveBufferSize = receiveBufferSize;
    }

    public boolean isReuseAddress() {
        return reuseAddress;
    }

    public void setReuseAddress(final boolean reuseAddress) {
        this.reuseAddress = reuseAddress;
    }

    public int getSendBufferSize() {
        return sendBufferSize;
    }

    public void setSendBufferSize(final int sendBufferSize) {
        this.sendBufferSize = sendBufferSize;
    }

    public int getTrafficClass() {
        return trafficClass;
    }

    public void setTrafficClass(final int trafficClass) {
        this.trafficClass = trafficClass;
    }

    public boolean isBroadcast() {
        return broadcast;
    }

    public void setBroadcast(final boolean broadcast) {
        this.broadcast = broadcast;
    }

    public SocketAddress[] getBindAddresses() {
        return bindAddresses;
    }

    public void setBindAddresses(final SocketAddress[] bindAddresses) {
        this.bindAddresses = bindAddresses;
    }

    public NioProvider getNioProvider() {
        return nioProvider;
    }

    public void setNioProvider(final NioProvider nioProvider) {
        this.nioProvider = nioProvider;
    }

    public IoHandlerFactory<? super UdpChannel> getHandlerFactory() {
        return handlerFactory;
    }

    public void setHandlerFactory(final IoHandlerFactory<? super UdpChannel> handlerFactory) {
        this.handlerFactory = handlerFactory;
    }

    public Executor getExecutor() {
        return executor;
    }

    public void setExecutor(final Executor executor) {
        this.executor = executor;
    }

    public void start() throws IOException {
        if (nioProvider == null) {
            throw new NullPointerException("nioProvider is null");
        }
        if (handlerFactory == null) {
            throw new NullPointerException("handlerFactory is null");
        }
        final int bindCount = bindAddresses.length;
        final DatagramChannel[] datagramChannels = new DatagramChannel[bindCount];
        channels = new NioUdpSocketChannelImpl[bindCount];
        for (int i = 0; i < bindCount; i++) {
            final DatagramChannel datagramChannel = DatagramChannel.open();
            datagramChannel.configureBlocking(false);
            final DatagramSocket socket = datagramChannel.socket();
            socket.setBroadcast(broadcast);
            if (receiveBufferSize != -1) socket.setReceiveBufferSize(receiveBufferSize);
            socket.setReuseAddress(reuseAddress);
            if (sendBufferSize != -1) socket.setSendBufferSize(sendBufferSize);
            if (trafficClass != -1) socket.setTrafficClass(trafficClass);
            datagramChannels[i] = datagramChannel;
            //noinspection unchecked
            channels[i] = new NioUdpSocketChannelImpl(nioProvider, datagramChannel, handlerFactory.createHandler());
        }
        boolean ok = false;
        try {
            for (int i = 0; i < bindCount; i++) {
                try {
                    datagramChannels[i].socket().bind(bindAddresses[i]);
                } catch (IOException ex) {
                    log.error("Unable to bind to %s: %s", bindAddresses[i], ex);
                    IoUtils.safeClose(datagramChannels[i]);
                    continue;
                }
                final NioUdpSocketChannelImpl channel = channels[i];
                if (! SpiUtils.<UdpChannel>handleOpened(channel.getHandler(), channel)) {
                    IoUtils.safeClose(datagramChannels[i]);
                }
                nioProvider.addChannel(channel);
            }
            ok = true;
        } finally {
            if (! ok) {
                for (int j = 0; j < bindCount; j ++) {
                    IoUtils.safeClose(datagramChannels[j]);
                    if (channels[j] != null) {
                        nioProvider.removeChannel(channels[j]);
                    }
                }
                channels = null;
            }
        }
    }

    public void stop() {
        if (channels != null) {
            for (NioUdpSocketChannelImpl channel : channels) {
                IoUtils.safeClose(channel);
            }
            channels = null;
        }
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

    @SuppressWarnings({"unchecked"})
    public <T> T getOption(final ChannelOption<T> option) throws UnsupportedOptionException, IOException {
        if (! OPTIONS.contains(option)) {
            throw new UnsupportedOptionException("Option not supported: " + option);
        }
        if (CommonOptions.RECEIVE_BUFFER.equals(option)) {
            final int v = receiveBufferSize;
            return v == -1 ? null : (T) Integer.valueOf(v);
        } else if (CommonOptions.REUSE_ADDRESSES.equals(option)) {
            return (T) Boolean.valueOf(reuseAddress);
        } else if (CommonOptions.SEND_BUFFER.equals(option)) {
            final int v = sendBufferSize;
            return v == -1 ? null : (T) Integer.valueOf(v);
        } else if (CommonOptions.IP_TRAFFIC_CLASS.equals(option)) {
            final int v = trafficClass;
            return v == -1 ? null : (T) Integer.valueOf(v);
        } else if (CommonOptions.BROADCAST.equals(option)) {
            return (T) Boolean.valueOf(broadcast);
        } else {
            throw new IllegalStateException("Failed to get supported option: " + option);
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
            setReceiveBufferSize(((Integer)value).intValue());
            return this;
        } else if (CommonOptions.REUSE_ADDRESSES.equals(option)) {
            setReuseAddress(((Boolean)value).booleanValue());
            return this;
        } else if (CommonOptions.SEND_BUFFER.equals(option)) {
            setSendBufferSize(((Integer)value).intValue());
            return this;
        } else if (CommonOptions.IP_TRAFFIC_CLASS.equals(option)) {
            setTrafficClass(((Integer)value).intValue());
            return this;
        } else if (CommonOptions.BROADCAST.equals(option)) {
            setBroadcast(((Boolean)value).booleanValue());
            return this;
        } else {
            throw new IllegalStateException("Failed to set supported option: " + option);
        }
    }

    public String toString() {
        return String.format("UDP server (NIO) <%s>", Integer.toString(hashCode(), 16));
    }
}
