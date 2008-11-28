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

package org.jboss.xnio.nio;

import org.jboss.xnio.IoHandlerFactory;
import org.jboss.xnio.channels.UdpChannel;
import java.util.concurrent.Executor;
import java.net.SocketAddress;

/**
 *
 */
public final class NioUdpServerConfig {
    private NioXnio xnio;
    private Executor executor;
    private IoHandlerFactory<? super UdpChannel> handlerFactory;
    private SocketAddress[] initialAddresses;
    private Boolean reuseAddresses;
    private Integer receiveBuffer;
    private Integer sendBuffer;
    private Integer trafficClass;
    private Boolean broadcast;

    public NioXnio getXnio() {
        return xnio;
    }

    public void setXnio(final NioXnio xnio) {
        this.xnio = xnio;
    }

    public Executor getExecutor() {
        return executor;
    }

    public void setExecutor(final Executor executor) {
        this.executor = executor;
    }

    public IoHandlerFactory<? super UdpChannel> getHandlerFactory() {
        return handlerFactory;
    }

    public void setHandlerFactory(final IoHandlerFactory<? super UdpChannel> handlerFactory) {
        this.handlerFactory = handlerFactory;
    }

    public SocketAddress[] getInitialAddresses() {
        return initialAddresses;
    }

    public void setInitialAddresses(final SocketAddress[] initialAddresses) {
        this.initialAddresses = initialAddresses;
    }

    public Boolean getReuseAddresses() {
        return reuseAddresses;
    }

    public void setReuseAddresses(final Boolean reuseAddresses) {
        this.reuseAddresses = reuseAddresses;
    }

    public Integer getReceiveBuffer() {
        return receiveBuffer;
    }

    public void setReceiveBuffer(final Integer receiveBuffer) {
        this.receiveBuffer = receiveBuffer;
    }

    public Integer getSendBuffer() {
        return sendBuffer;
    }

    public void setSendBuffer(final Integer sendBuffer) {
        this.sendBuffer = sendBuffer;
    }

    public Integer getTrafficClass() {
        return trafficClass;
    }

    public void setTrafficClass(final Integer trafficClass) {
        this.trafficClass = trafficClass;
    }

    public Boolean getBroadcast() {
        return broadcast;
    }

    public void setBroadcast(final Boolean broadcast) {
        this.broadcast = broadcast;
    }
}