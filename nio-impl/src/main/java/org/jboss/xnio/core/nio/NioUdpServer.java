package org.jboss.xnio.core.nio;

import java.net.SocketAddress;
import java.net.DatagramSocket;
import java.nio.channels.DatagramChannel;
import java.io.IOException;
import java.util.concurrent.Executor;
import org.jboss.xnio.channels.MulticastDatagramChannel;
import org.jboss.xnio.IoHandlerFactory;
import org.jboss.xnio.spi.UdpServer;
import org.jboss.xnio.spi.Lifecycle;

/**
 *
 */
public final class NioUdpServer implements Lifecycle, UdpServer {

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

    public void create() throws Exception {
        if (nioProvider == null) {
            throw new NullPointerException("nioCore is null");
        }
        if (handlerFactory == null) {
            throw new NullPointerException("handlerFactory is null");
        }
    }

    public void start() throws IOException {
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
            channels[i] = new NioUdpSocketChannelImpl(nioProvider, datagramChannel, handlerFactory.createHandler());
        }
        for (int i = 0; i < bindCount; i++) {
            try {
                datagramChannels[i].socket().bind(bindAddresses[i]);
            } catch (IOException ex) {
                for (int j = 0; j < bindCount; j ++) {
                    if (datagramChannels[j] != null) try {
                        datagramChannels[j].close();
                    } catch (Throwable t) {
                        // todo log
                    }
                }
                channels = null;
                throw ex;
            }
        }
    }

    public void stop() {
        for (NioUdpSocketChannelImpl channel : channels) {
            if (channel != null) try {
                channel.close();
            } catch (Throwable t) {
                // todo log
            }
        }
        channels = null;
    }

    public void destroy() throws Exception {
    }
}
