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

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;

/**
 *
 */
@XmlType(name = "tcp-connector", namespace = "urn:jboss:io:1.0")
public final class TcpConnectorMetaData implements Serializable {

    private static final long serialVersionUID = 2101881740511543307L;

    private NamedBeanMetaData executorBean;
    private Boolean keepAlive;
    private Boolean oobInline;
    private Integer receiveBufferSize;
    private Boolean reuseAddress;
    private Integer sendBufferSize;
    private Boolean tcpNoDelay;
    private Integer connectTimeout;
    private String name;

    @XmlElement(name = "executor-bean", namespace = "urn:jboss:io:1.0")
    public void setExecutorBean(final NamedBeanMetaData executorBean) {
        this.executorBean = executorBean;
    }

    public Boolean getKeepAlive() {
        return keepAlive;
    }

    @XmlAttribute(name = "keep-alive")
    public void setKeepAlive(final Boolean keepAlive) {
        this.keepAlive = keepAlive;
    }

    public Boolean getOobInline() {
        return oobInline;
    }

    @XmlAttribute(name = "oob-inline")
    public void setOobInline(final Boolean oobInline) {
        this.oobInline = oobInline;
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

    public Boolean getTcpNoDelay() {
        return tcpNoDelay;
    }

    @XmlAttribute(name = "tcp-no-delay")
    public void setTcpNoDelay(final Boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
    }

    public Integer getConnectTimeout() {
        return connectTimeout;
    }

    @XmlAttribute(name = "connect-timeout")
    public void setConnectTimeout(final Integer connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public String getName() {
        return name;
    }

    @XmlAttribute(name = "name")
    public void setName(final String name) {
        this.name = name;
    }
}