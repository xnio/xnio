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

import java.util.List;
import java.util.Collections;

import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

/**
 *
 */
@XmlType(name = "configurable")
public abstract class AbstractConfigurableMetaData {
    private Integer backlog;
    private Boolean broadcast;
    private Boolean closeAbort;
    private Integer ipTrafficClass;
    private Integer ipTos;
    private Boolean keepAlive;
    private Boolean manageConnections;
    private Integer multicastTtl;
    private Boolean oobInline;
    private Integer receiveBufferSize;
    private Boolean reuseAddress;
    private Integer sendBufferSize;
    private Boolean tcpNoDelay;
    private List<OptionMetaData> optionMetaDataList = Collections.emptyList();

    public Integer getBacklog() {
        return backlog;
    }

    @XmlAttribute(name = "backlog")
    public void setBacklog(final Integer backlog) {
        this.backlog = backlog;
    }

    public Boolean getBroadcast() {
        return broadcast;
    }

    @XmlAttribute(name = "broadcast")
    public void setBroadcast(final Boolean broadcast) {
        this.broadcast = broadcast;
    }

    public Boolean getCloseAbort() {
        return closeAbort;
    }

    @XmlAttribute(name = "close-abort")
    public void setCloseAbort(final Boolean closeAbort) {
        this.closeAbort = closeAbort;
    }

    public Integer getIpTrafficClass() {
        return ipTrafficClass;
    }

    @XmlAttribute(name = "ip-traffic-class")
    public void setIpTrafficClass(final Integer ipTrafficClass) {
        this.ipTrafficClass = ipTrafficClass;
    }

    public Integer getIpTos() {
        return ipTos;
    }

    @XmlAttribute(name = "ip-tos")
    public void setIpTos(final Integer ipTos) {
        this.ipTos = ipTos;
    }

    public Boolean getKeepAlive() {
        return keepAlive;
    }

    @XmlAttribute(name = "keep-alive")
    public void setKeepAlive(final Boolean keepAlive) {
        this.keepAlive = keepAlive;
    }

    public Boolean getManageConnections() {
        return manageConnections;
    }

    @XmlAttribute(name = "manage-connections")
    public void setManageConnections(final Boolean manageConnections) {
        this.manageConnections = manageConnections;
    }

    public Integer getMulticastTtl() {
        return multicastTtl;
    }

    @XmlAttribute(name = "multicast-ttl")
    public void setMulticastTtl(final Integer multicastTtl) {
        this.multicastTtl = multicastTtl;
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

    public List<OptionMetaData> getOptionMetaDataList() {
        return optionMetaDataList;
    }

    @XmlElement(name = "option")
    public void setOptionMetaDataList(final List<OptionMetaData> optionMetaDataList) {
        this.optionMetaDataList = optionMetaDataList;
    }
}
