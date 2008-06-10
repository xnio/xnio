package org.jboss.xnio.spi;

import org.jboss.xnio.IoHandlerFactory;
import org.jboss.xnio.channels.MulticastDatagramChannel;
import org.jboss.xnio.channels.Configurable;
import java.net.SocketAddress;

/**
 * A configurable UDP server.
 */
public interface UdpServer extends ExecutorUser, Lifecycle, Configurable {
    /**
     * Set the handler factory which will be used to create handlers for each bind address.
     *
     * @param handlerFactory the handler factory
     */
    void setHandlerFactory(IoHandlerFactory<? super MulticastDatagramChannel> handlerFactory);

    /**
     * Set the bind addresses for this server.
     *
     * @param bindAddresses the list of bind addresses
     */
    void setBindAddresses(SocketAddress[] bindAddresses);

    /**
     * Set the receive buffer size socket parameter.
     *
     * @param receiveBufferSize the receive buffer size
     */
    void setReceiveBufferSize(int receiveBufferSize);

    /**
     * Set the reuse-address socket parameter.
     *
     * @param reuseAddress {@code true} to enable the reuse-address socket parameter
     */
    void setReuseAddress(boolean reuseAddress);

    /**
     * Set the send buffer size socket parameter.
     *
     * @param sendBufferSize the send buffer size
     */
    void setSendBufferSize(int sendBufferSize);

    /**
     * Set the traffic class socket parameter.
     *
     * @param trafficClass the traffic class
     */
    void setTrafficClass(int trafficClass);

    /**
     * Configure whether this socket is sensitive to broadcasts.
     *
     * @param broadcast {@code true} to enable reception of broadcast packets
     */
    void setBroadcast(boolean broadcast);
}
