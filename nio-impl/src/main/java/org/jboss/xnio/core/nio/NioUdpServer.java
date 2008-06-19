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

import java.net.SocketAddress;
import java.net.DatagramSocket;
import java.nio.channels.DatagramChannel;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.Map;
import java.util.Collections;
import org.jboss.xnio.channels.MulticastDatagramChannel;
import org.jboss.xnio.channels.UnsupportedOptionException;
import org.jboss.xnio.channels.Configurable;
import org.jboss.xnio.IoHandlerFactory;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.spi.UdpServerService;
import org.jboss.xnio.spi.Lifecycle;
import org.jboss.xnio.spi.SpiUtils;

/**
 *
 */
public final class NioUdpServer implements Lifecycle, UdpServerService {

    private NioProvider nioProvider;
    private IoHandlerFactory<? super MulticastDatagramChannel> handlerFactory;
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

    public IoHandlerFactory<? super MulticastDatagramChannel> getHandlerFactory() {
        return handlerFactory;
    }

    public void setHandlerFactory(final IoHandlerFactory<? super MulticastDatagramChannel> handlerFactory) {
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
                    IoUtils.safeClose(datagramChannels[i]);
                    continue;
                }
                final NioUdpSocketChannelImpl channel = channels[i];
                if (! SpiUtils.<MulticastDatagramChannel>handleOpened(channel.getHandler(), channel)) {
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

    public Object getOption(final String name) throws UnsupportedOptionException, IOException {
        throw new UnsupportedOptionException("No options supported by this server type");
    }

    public Map<String, Class<?>> getOptions() {
        return Collections.emptyMap();
    }

    public Configurable setOption(final String name, final Object value) throws IllegalArgumentException, IOException {
        throw new UnsupportedOptionException("No options supported by this server type");
    }
}
