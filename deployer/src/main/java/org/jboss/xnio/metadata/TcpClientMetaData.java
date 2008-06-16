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

import org.jboss.beans.metadata.spi.BeanMetaData;
import org.jboss.beans.metadata.spi.builder.BeanMetaDataBuilder;
import org.jboss.xnio.ConnectionAddress;
import org.jboss.xnio.Client;
import java.io.Serializable;

import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlTransient;

/**
 *
 */
@XmlType(name = "tcp-client", namespace = "urn:jboss:io:1.0")
public final class TcpClientMetaData implements IoMetaData, Serializable {

    private static final long serialVersionUID = 2101881740511543307L;

    private NamedBeanMetaData tcpConnectorBean;
    private TcpConnectorMetaData tcpConnectorMetaData;
    private ConnectAddressMetaData[] connectAddresses = new ConnectAddressMetaData[0];
    private String name;

    public NamedBeanMetaData getTcpConnectorBean() {
        return tcpConnectorBean;
    }

    @XmlElement(name = "tcp-connector-bean", namespace = "urn:jboss:io:1.0")
    public void setTcpConnectorBean(final NamedBeanMetaData tcpConnectorBean) {
        this.tcpConnectorBean = tcpConnectorBean;
    }

    public TcpConnectorMetaData getTcpConnectorMetaData() {
        return tcpConnectorMetaData;
    }

    @XmlElement(name = "tcp-connector", namespace = "urn:jboss:io:1.0")
    public void setTcpConnectorMetaData(final TcpConnectorMetaData tcpConnectorMetaData) {
        this.tcpConnectorMetaData = tcpConnectorMetaData;
    }

    public ConnectAddressMetaData[] getConnectAddresses() {
        return connectAddresses;
    }

    @XmlElement(name = "connect-address", namespace = "urn:jboss:io:1.0")
    public void setConnectAddresses(final ConnectAddressMetaData[] connectAddresses) {
        this.connectAddresses = connectAddresses;
    }

    public String getName() {
        return name;
    }

    @XmlAttribute(name = "name")
    public void setName(final String name) {
        this.name = name;
    }

    @XmlTransient
    public BeanMetaData getBeanMetaData(final NamedBeanMetaData defaultExecutorBean, final BeanMetaData nioCoreBean) {
        BeanMetaDataBuilder builder = BeanMetaDataBuilder.createBuilder(name, Client.class.getName());
        if (tcpConnectorBean != null) builder.addPropertyMetaData("connector", builder.createInject(tcpConnectorBean.getName()));
        if (tcpConnectorMetaData != null) builder.addPropertyMetaData("connector", tcpConnectorMetaData.getBeanMetaData(defaultExecutorBean, nioCoreBean));
        final int addressCount = connectAddresses.length;
        ConnectionAddress[] connectionAddresses = new ConnectionAddress[addressCount];
        for (int i = 0; i < addressCount; i ++) {
            connectionAddresses[i] = connectAddresses[i].getConnectionAddress();
        }
        builder.addPropertyMetaData("addresses", connectionAddresses);
        return builder.getBeanMetaData();
    }
}