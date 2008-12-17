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
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;

/**
 *
 */
@XmlType(name = "udp-server")
public final class UdpServerMetaData extends AbstractConfigurableMetaData implements Serializable {

    private static final long serialVersionUID = -5985650641647590499L;

    private List<InetSocketAddressMetaData> bindAddresses = arrayList();
    private NamedBeanMetaData handlerFactoryBean;
    private NamedBeanMetaData executorBean;
    private Boolean broadcast;
    private Boolean multicast;
    private String name;

    private static <T> List<T> arrayList() {
        return new ArrayList<T>();
    }

    public List<InetSocketAddressMetaData> getBindAddresses() {
        return bindAddresses;
    }

    @XmlElement(name = "bind-address")
    public void setBindAddresses(final List<InetSocketAddressMetaData> bindAddresses) {
        this.bindAddresses = bindAddresses;
    }

    public NamedBeanMetaData getHandlerFactoryBean() {
        return handlerFactoryBean;
    }

    @XmlElement(name = "handler-factory-bean", required = true)
    public void setHandlerFactoryBean(final NamedBeanMetaData handlerFactoryBean) {
        this.handlerFactoryBean = handlerFactoryBean;
    }

    public NamedBeanMetaData getExecutorBean() {
        return executorBean;
    }

    @XmlElement(name = "executor-bean")
    public void setExecutorBean(final NamedBeanMetaData executorBean) {
        this.executorBean = executorBean;
    }

    public Boolean getBroadcast() {
        return broadcast;
    }

    @XmlElement(name = "broadcast")
    public void setBroadcast(final Boolean broadcast) {
        this.broadcast = broadcast;
    }

    public Boolean getMulticast() {
        return multicast;
    }

    @XmlElement(name = "multicast")
    public void setMulticast(final Boolean multicast) {
        this.multicast = multicast;
    }

    public String getName() {
        return name;
    }

    @XmlAttribute(name = "name", required = true)
    public void setName(final String name) {
        this.name = name;
    }
}
