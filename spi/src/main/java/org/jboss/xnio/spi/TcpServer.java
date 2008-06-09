package org.jboss.xnio.spi;

import java.net.SocketAddress;
import org.jboss.xnio.IoHandlerFactory;
import org.jboss.xnio.channels.ConnectedStreamChannel;

/**
 * A configurable TCP server.
 */
public interface TcpServer extends ExecutorUser, Lifecycle {
    /**
     * Set the handler factory which will be used to create handlers for incoming connections.
     *
     * @param handlerFactory the handler factory
     */
    void setHandlerFactory(IoHandlerFactory<? super ConnectedStreamChannel<SocketAddress>> handlerFactory);

    /**
     * Set the socket keepalive parameter.
     *
     * @param keepAlive {@code true} to enable TCP keepalive
     */
    void setKeepAlive(boolean keepAlive);

    /**
     * Set the OOB-inline socket parameter.
     *
     * @param oobInline {@code true} to enable inline OOB messages
     */
    void setOobInline(boolean oobInline);

    /**
     * Set the socket receive buffer size.
     *
     * @param receiveBufferSize the receive buffer size
     */
    void setReceiveBufferSize(int receiveBufferSize);

    /**
     * Set the reuse address socket parameter.
     *
     * @param reuseAddress {@code true} to enable address reuse
     */
    void setReuseAddress(boolean reuseAddress);

    /**
     * Set the TCP-no-delay socket parameter.
     *
     * @param tcpNoDelay {@code true} to enable TCP-no-delay
     */
    void setTcpNoDelay(boolean tcpNoDelay);

    /**
     * Set the socket backlog parameters.
     *
     * @param backlog the socket backlog
     */
    void setBacklog(int backlog);

    /**
     * Set the bind addresses to use for this server.
     *
     * @param bindAddresses the bind addresses
     */
    void setBindAddresses(SocketAddress[] bindAddresses);
}
