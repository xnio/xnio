package org.jboss.xnio.metadata;

import java.net.SocketAddress;
import java.net.InetSocketAddress;

import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAttribute;

/**
 *
 */
@XmlType(name = "bind-address", namespace = "urn:jboss:io:1.0")
public final class BindAddressMetaData {
    private String address;
    private int port;

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

    public SocketAddress getSocketAddress() {
        if (address == null) {
            return new InetSocketAddress(port);
        } else {
            return new InetSocketAddress(address, port);
        }
    }
}
