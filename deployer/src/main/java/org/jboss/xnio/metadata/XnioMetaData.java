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

import org.jboss.beans.metadata.spi.BeanMetaDataFactory;
import org.jboss.beans.metadata.spi.BeanMetaData;
import org.jboss.xb.annotations.JBossXmlSchema;

import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlNsForm;
import javax.xml.bind.annotation.XmlAttribute;

import java.io.Serializable;
import java.util.List;
import java.util.ArrayList;

/**
 *
 */
@XmlType(name = "xnio", namespace = "urn:jboss:xnio:1.0")
@XmlRootElement(namespace = "urn:jboss:xnio:1.0", name = "xnio")
@JBossXmlSchema(namespace = "urn:jboss:xnio:1.0", attributeFormDefault = XmlNsForm.UNQUALIFIED, elementFormDefault = XmlNsForm.QUALIFIED)
public final class XnioMetaData implements BeanMetaDataFactory, Serializable {
    private static final long serialVersionUID = -1616974182990862225L;

    private String provider;
    private List<TcpServerMetaData> tcpServers = arrayList();
    private List<TcpConnectorMetaData> tcpConnectors = arrayList();
    private List<TcpClientMetaData> tcpClients = arrayList();
    private List<TcpConnectionMetaData> tcpConnections = arrayList();
    private List<UdpServerMetaData> udpServers = arrayList();
    private List<PipeMetaData> pipes = arrayList();

    private static <T> List<T> arrayList() {
        return new ArrayList<T>();
    }

    public String getProvider() {
        return provider;
    }

    @XmlAttribute
    public void setProvider(final String provider) {
        this.provider = provider;
    }

    public List<TcpServerMetaData> getTcpServers() {
        return tcpServers;
    }

    @XmlElement(name = "tcp-server", type = TcpServerMetaData.class)
    public void setTcpServers(final List<TcpServerMetaData> tcpServers) {
        this.tcpServers = tcpServers;
    }

    public List<TcpConnectorMetaData> getTcpConnectors() {
        return tcpConnectors;
    }

    @XmlElement(name = "tcp-connector", type = TcpConnectorMetaData.class)
    public void setTcpConnectors(final List<TcpConnectorMetaData> tcpConnectors) {
        this.tcpConnectors = tcpConnectors;
    }

    public List<TcpClientMetaData> getTcpClients() {
        return tcpClients;
    }

    @XmlElement(name = "tcp-client", type = TcpClientMetaData.class)
    public void setTcpClients(final List<TcpClientMetaData> tcpClients) {
        this.tcpClients = tcpClients;
    }

    public List<TcpConnectionMetaData> getTcpConnections() {
        return tcpConnections;
    }

    @XmlElement(name = "tcp-connection", type = TcpConnectionMetaData.class)
    public void setTcpConnections(final List<TcpConnectionMetaData> tcpConnections) {
        this.tcpConnections = tcpConnections;
    }

    public List<UdpServerMetaData> getUdpServers() {
        return udpServers;
    }

    @XmlElement(name = "udp-server", type = UdpServerMetaData.class)
    public void setUdpServers(final List<UdpServerMetaData> udpServers) {
        this.udpServers = udpServers;
    }

    public List<PipeMetaData> getPipes() {
        return pipes;
    }

    @XmlElement(name = "pipe", type = PipeMetaData.class)
    public void setPipes(final List<PipeMetaData> pipes) {
        this.pipes = pipes;
    }

    @XmlTransient
    public List<BeanMetaData> getBeans() {
        final List<BeanMetaData> list = arrayList();
        for (TcpServerMetaData tcpServer : tcpServers) {
            list.addAll(XnioMetaDataHelper.add(provider, tcpServer));
        }
        return list;
    }
}
