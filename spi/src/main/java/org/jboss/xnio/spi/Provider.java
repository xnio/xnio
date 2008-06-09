package org.jboss.xnio.spi;

/**
 * An XNIO provider.
 */
public interface Provider extends ExecutorUser, Lifecycle {
    /**
     * Create a TCP server instance.
     *
     * @return a TCP server
     */
    TcpServer createTcpServer();

    /**
     * Create a TCP connector instance.
     *
     * @return a TCP connector
     */
    TcpConnector createTcpConnector();

    /**
     * Create a UDP server instance.
     *
     * @return a UDP server
     */
    UdpServer createUdpServer();

    /**
     * Create a multicast-capable UDP server instance.
     *
     * @return a UDP server
     */
    UdpServer createMulticastUdpServer();

    /**
     * Create a pipe instance.
     *
     * @return a pipe
     */
    Pipe createPipe();

    /**
     * Create a one-way pipe instance.
     *
     * @return a one-way pipe
     */
    OneWayPipe createOneWayPipe();
}
