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
@XmlType(name = "tcp-server")
public final class TcpServerMetaData extends AbstractConfigurableMetaData implements Serializable {

    private static final long serialVersionUID = 4318538124489300012L;

    private List<BindAddressMetaData> bindAddresses = arrayList();
    private NamedBeanMetaData handlerFactoryBean;
    private NamedBeanMetaData executorBean;
    private String name;

    private static <T> List<T> arrayList() {
        return new ArrayList<T>();
    }

    public List<BindAddressMetaData> getBindAddresses() {
        return bindAddresses;
    }

    @XmlElement(name = "bind-address", type = BindAddressMetaData.class)
    public void setBindAddresses(final List<BindAddressMetaData> bindAddresses) {
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

    @XmlElement(name = "executor-bean", namespace = "urn:jboss:io:1.0")
    public void setExecutorBean(final NamedBeanMetaData executorBean) {
        this.executorBean = executorBean;
    }

    public String getName() {
        return name;
    }

    @XmlAttribute(name = "name")
    public void setName(final String name) {
        this.name = name;
    }
}
