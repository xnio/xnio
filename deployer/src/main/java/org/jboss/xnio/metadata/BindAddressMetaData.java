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
