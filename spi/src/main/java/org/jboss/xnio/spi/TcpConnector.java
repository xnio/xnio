package org.jboss.xnio.spi;

import org.jboss.xnio.Connector;
import org.jboss.xnio.channels.ConnectedStreamChannel;
import org.jboss.xnio.channels.Configurable;
import java.net.SocketAddress;

/**
 * A TCP connector instance.
 */
public interface TcpConnector extends Connector<SocketAddress, ConnectedStreamChannel<SocketAddress>>, ExecutorUser, Lifecycle, Configurable {

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
     * Set the socket send buffer size.
     *
     * @param sendBufferSize the send buffer size
     */
    void setSendBufferSize(int sendBufferSize);

    /**
     * Set the TCP-no-delay socket parameter.
     *
     * @param tcpNoDelay {@code true} to enable TCP-no-delay
     */
    void setTcpNoDelay(boolean tcpNoDelay);

    /**
     * Set the connection timeout (in milliseconds).
     *
     * @param connectTimeout the connect timeout, in milliseconds
     */
    void setConnectTimeout(int connectTimeout);
}
