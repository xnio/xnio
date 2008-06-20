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

package org.jboss.xnio.spi;

import org.jboss.xnio.IoHandlerFactory;
import org.jboss.xnio.channels.UdpChannel;
import org.jboss.xnio.channels.Configurable;
import java.net.SocketAddress;

/**
 * A configurable UDP server.
 */
public interface UdpServerService extends ExecutorUser, Lifecycle, Configurable {
    /**
     * Set the handler factory which will be used to create handlers for each bind address.
     *
     * @param handlerFactory the handler factory
     */
    void setHandlerFactory(IoHandlerFactory<? super UdpChannel> handlerFactory);

    /**
     * Set the bind addresses for this server.
     *
     * @param bindAddresses the list of bind addresses
     */
    void setBindAddresses(SocketAddress[] bindAddresses);

    /**
     * Set the receive buffer size socket parameter.
     *
     * @param receiveBufferSize the receive buffer size
     */
    void setReceiveBufferSize(int receiveBufferSize);

    /**
     * Set the reuse-address socket parameter.
     *
     * @param reuseAddress {@code true} to enable the reuse-address socket parameter
     */
    void setReuseAddress(boolean reuseAddress);

    /**
     * Set the send buffer size socket parameter.
     *
     * @param sendBufferSize the send buffer size
     */
    void setSendBufferSize(int sendBufferSize);

    /**
     * Set the traffic class socket parameter.
     *
     * @param trafficClass the traffic class
     */
    void setTrafficClass(int trafficClass);

    /**
     * Configure whether this socket is sensitive to broadcasts.
     *
     * @param broadcast {@code true} to enable reception of broadcast packets
     */
    void setBroadcast(boolean broadcast);
}
