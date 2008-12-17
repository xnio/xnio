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

import java.io.Serializable;
import java.util.List;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;

/**
 *
 */
@XmlType(name = "tcp-client")
public final class TcpClientMetaData implements Serializable {

    private static final long serialVersionUID = 2101881740511543307L;

    private NamedBeanMetaData tcpConnectorBean;
    private TcpConnectorMetaData tcpConnectorMetaData;
    private List<DestinationMetaData> destinations;
    private String name;

    public NamedBeanMetaData getTcpConnectorBean() {
        return tcpConnectorBean;
    }

    @XmlElement(name = "tcp-connector-bean")
    public void setTcpConnectorBean(final NamedBeanMetaData tcpConnectorBean) {
        this.tcpConnectorBean = tcpConnectorBean;
    }

    public TcpConnectorMetaData getTcpConnectorMetaData() {
        return tcpConnectorMetaData;
    }

    @XmlElement(name = "tcp-connector")
    public void setTcpConnectorMetaData(final TcpConnectorMetaData tcpConnectorMetaData) {
        this.tcpConnectorMetaData = tcpConnectorMetaData;
    }

    public List<DestinationMetaData> getDestinations() {
        return destinations;
    }

    @XmlElement(name = "destination", required = true)
    public void setDestinations(final List<DestinationMetaData> destinations) {
        this.destinations = destinations;
    }

    public String getName() {
        return name;
    }

    @XmlAttribute(name = "name", required = true)
    public void setName(final String name) {
        this.name = name;
    }
}