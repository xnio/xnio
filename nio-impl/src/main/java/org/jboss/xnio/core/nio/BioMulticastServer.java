package org.jboss.xnio.core.nio;

import java.net.SocketAddress;
import java.net.MulticastSocket;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.IOException;
import org.jboss.xnio.channels.MulticastDatagramChannel;
import org.jboss.xnio.IoHandlerFactory;
import org.jboss.xnio.spi.UdpServer;
import org.jboss.xnio.spi.Lifecycle;

/**
 *
 */
public final class BioMulticastServer implements Lifecycle, UdpServer {
    private IoHandlerFactory<? super MulticastDatagramChannel> handlerFactory;
    private BioMulticastChannelImpl[] channels = new BioMulticastChannelImpl[0];
    private SocketAddress[] bindAddresses = new SocketAddress[0];

    private int receiveBufferSize = -1;
    private boolean reuseAddress = false;
    private int sendBufferSize = -1;
    private int trafficClass = -1;
    private boolean broadcast = false;
    private Executor executor;
    private ExecutorService executorService;

    public IoHandlerFactory<? super MulticastDatagramChannel> getHandlerFactory() {
        return handlerFactory;
    }

    public void setHandlerFactory(final IoHandlerFactory<? super MulticastDatagramChannel> handlerFactory) {
        this.handlerFactory = handlerFactory;
    }

    public BioMulticastChannelImpl[] getChannels() {
        return channels;
    }

    public void setChannels(final BioMulticastChannelImpl[] channels) {
        this.channels = channels;
    }

    public SocketAddress[] getBindAddresses() {
        return bindAddresses;
    }

    public void setBindAddresses(final SocketAddress[] bindAddresses) {
        this.bindAddresses = bindAddresses;
    }

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

    public Executor getExecutor() {
        return executor;
    }

    public void setExecutor(final Executor executor) {
        this.executor = executor;
    }

    public void create() {
        if (executor == null) {
            executor = executorService = Executors.newCachedThreadPool();
        }
        if (handlerFactory == null) {
            throw new NullPointerException("handlerFactory is null");
        }
    }

    @SuppressWarnings({"unchecked"})
    public void start() throws IOException {
        final int bindCount = bindAddresses.length;
        final MulticastSocket[] sockets = new MulticastSocket[bindCount];
        boolean ok = false;
        try {
            channels = new BioMulticastChannelImpl[bindCount];
            for (int i = 0; i < bindCount; i++) {
                MulticastSocket socket = new MulticastSocket(bindAddresses[i]);
                socket.setBroadcast(broadcast);
                if (receiveBufferSize != -1) socket.setReceiveBufferSize(receiveBufferSize);
                socket.setReuseAddress(reuseAddress);
                if (sendBufferSize != -1) socket.setSendBufferSize(sendBufferSize);
                if (trafficClass != -1) socket.setTrafficClass(trafficClass);
                sockets[i] = socket;
                channels[i] = new BioMulticastChannelImpl(sendBufferSize, receiveBufferSize, executor, handlerFactory.createHandler(), socket);
            }
            ok = true;
        } finally {
            if (! ok) {
                for (MulticastSocket socket : sockets) {
                    if (socket != null) try {
                        socket.close();
                    } catch (Throwable t) {
                        // todo log it @ trace
                    }
                }
            }
        }
    }

    public void stop() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    public void destroy() {
        if (executorService != null) {
            executor = executorService = null;
        }
    }
}
