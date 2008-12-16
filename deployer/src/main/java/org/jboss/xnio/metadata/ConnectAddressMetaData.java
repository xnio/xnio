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

package org.jboss.xnio.metadata;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import org.jboss.xnio.ConnectionAddress;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;

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
