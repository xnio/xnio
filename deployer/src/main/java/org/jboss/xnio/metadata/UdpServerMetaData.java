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
import org.jboss.xnio.spi.UdpServer;
import java.util.List;
import java.util.ArrayList;
import java.net.SocketAddress;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAttribute;

/**
 *
 */
@XmlType(name = "upd-server", namespace = "urn:jboss:io:1.0")
public final class UdpServerMetaData implements IoMetaData {
    private List<BindAddressMetaData> bindAddresses = arrayList();
    private NamedBeanMetaData handlerFactoryBean;
    private NamedBeanMetaData executorBean;
    private Integer receiveBufferSize;
    private Boolean reuseAddress;
    private Integer sendBufferSize;
    private Integer trafficClass;
    private Boolean broadcast;
    private Boolean multicast;
    private String name;

    private static <T> List<T> arrayList() {
        return new ArrayList<T>();
    }

    public List<BindAddressMetaData> getBindAddresses() {
        return bindAddresses;
    }

    @XmlElement(name = "bind-address", namespace = "urn:jboss:io:1.0")
    public void setBindAddresses(final List<BindAddressMetaData> bindAddresses) {
        this.bindAddresses = bindAddresses;
    }

    public NamedBeanMetaData getHandlerFactoryBean() {
        return handlerFactoryBean;
    }

    @XmlElement(name = "handler-factory-bean", namespace = "urn:jboss:io:1.0")
    public void setHandlerFactoryBean(final NamedBeanMetaData handlerFactoryBean) {
        this.handlerFactoryBean = handlerFactoryBean;
    }

    public NamedBeanMetaData getExecutorBean() {
        return executorBean;
    }

    @XmlElement(name = "executor-bean", namespace = "urn:jboss:io:1.0")
    public void setExecutorBean(final NamedBeanMetaData executorBean) {
        this.executorBean = executorBean;
    }

    public Integer getReceiveBufferSize() {
        return receiveBufferSize;
    }

    @XmlAttribute(name = "receive-buffer-size")
    public void setReceiveBufferSize(final Integer receiveBufferSize) {
        this.receiveBufferSize = receiveBufferSize;
    }

    public Boolean getReuseAddress() {
        return reuseAddress;
    }

    @XmlAttribute(name = "reuse-address")
    public void setReuseAddress(final Boolean reuseAddress) {
        this.reuseAddress = reuseAddress;
    }

    public Integer getSendBufferSize() {
        return sendBufferSize;
    }

    @XmlAttribute(name = "send-buffer-size")
    public void setSendBufferSize(final Integer sendBufferSize) {
        this.sendBufferSize = sendBufferSize;
    }

    public Integer getTrafficClass() {
        return trafficClass;
    }

    @XmlAttribute(name = "traffic-class")
    public void setTrafficClass(final Integer trafficClass) {
        this.trafficClass = trafficClass;
    }

    public Boolean getBroadcast() {
        return broadcast;
    }

    @XmlAttribute(name = "broadcast")
    public void setBroadcast(final Boolean broadcast) {
        this.broadcast = broadcast;
    }

    public Boolean getMulticast() {
        return multicast;
    }

    @XmlAttribute(name = "multicast")
    public void setMulticast(final Boolean multicast) {
        this.multicast = multicast;
    }

    public String getName() {
        return name;
    }

    @XmlAttribute(name = "name")
    public void setName(final String name) {
        this.name = name;
    }

    public BeanMetaData getBeanMetaData(final NamedBeanMetaData defaultExecutorBean, final BeanMetaData providerBean) {
        final BeanMetaDataBuilder builder;
        builder = BeanMetaDataBuilder.createBuilder(name, UdpServer.class.getName());
        builder.setFactory(providerBean);
        if (multicast != null && multicast.booleanValue()) {
            builder.setFactoryMethod("createUdpServer");
        } else {
            builder.setFactoryMethod("createMulticastUdpServer");
        }
        final int bindCount = bindAddresses.size();
        SocketAddress[] addresses = new SocketAddress[bindCount];
        int i = 0;
        for (BindAddressMetaData metaData : bindAddresses) {
            addresses[i++] = metaData.getSocketAddress();
        }
        builder.addPropertyMetaData("bindAddresses", addresses);
        builder.addPropertyMetaData("handlerFactory", builder.createInject(handlerFactoryBean.getName()));
        if (executorBean != null) builder.addPropertyMetaData("executor", builder.createInject(executorBean.getName()));
        if (receiveBufferSize != null) builder.addPropertyMetaData("receiveBufferSize", receiveBufferSize);
        if (reuseAddress != null) builder.addPropertyMetaData("reuseAddress", reuseAddress);
        if (sendBufferSize != null) builder.addPropertyMetaData("sendBufferSize", sendBufferSize);
        if (trafficClass != null) builder.addPropertyMetaData("trafficClass", trafficClass);
        if (broadcast != null) builder.addPropertyMetaData("broadcast", broadcast);
        return builder.getBeanMetaData();
    }
}
