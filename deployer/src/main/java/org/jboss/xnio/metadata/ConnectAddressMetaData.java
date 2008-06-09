package org.jboss.xnio.metadata;

import org.jboss.xnio.ConnectionAddress;

import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAttribute;

import java.net.SocketAddress;
import java.net.InetSocketAddress;

/**
 *
 */
@XmlType(name = "connect-address", namespace = "urn:jboss:io:1.0")
public final class ConnectAddressMetaData {
    private String address;
    private int port;
    private String bindAddress;
    private int bindPort;

    public String getAddress() {
        return address;
    }

    @XmlAttribute
    public void setAddress(final String address) {
        this.address = address;
    }

    public int getPort() {
        return port;
    }

    @XmlAttribute
    public void setPort(final int port) {
        this.port = port;
    }

    public String getBindAddress() {
        return bindAddress;
    }

    @XmlAttribute(name = "bind-address")
    public void setBindAddress(final String bindAddress) {
        this.bindAddress = bindAddress;
    }

    public int getBindPort() {
        return bindPort;
    }

    @XmlAttribute(name = "bind-port")
    public void setBindPort(final int bindPort) {
        this.bindPort = bindPort;
    }

    public ConnectionAddress<SocketAddress> getConnectionAddress() {
        SocketAddress connectSocketAddress;
        if (address == null) {
            connectSocketAddress = new InetSocketAddress(port);
        } else {
            connectSocketAddress = new InetSocketAddress(address, port);
        }
        SocketAddress bindSocketAddress;
        if (bindAddress == null) {
            bindSocketAddress = new InetSocketAddress(bindPort);
        } else {
            bindSocketAddress = new InetSocketAddress(bindAddress, bindPort);
        }
        return new ConnectionAddress<SocketAddress>(bindSocketAddress, connectSocketAddress);
    }
}
