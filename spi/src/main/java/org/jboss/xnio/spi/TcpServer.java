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

import java.net.SocketAddress;
import org.jboss.xnio.IoHandlerFactory;
import org.jboss.xnio.channels.ConnectedStreamChannel;
import org.jboss.xnio.channels.Configurable;

/**
 * A configurable TCP server.
 */
public interface TcpServer extends ExecutorUser, Lifecycle, Configurable {
    /**
     * Set the handler factory which will be used to create handlers for incoming connections.
     *
     * @param handlerFactory the handler factory
     */
    void setHandlerFactory(IoHandlerFactory<? super ConnectedStreamChannel<SocketAddress>> handlerFactory);

    /**
     * Set the socket keepalive parameter.
     *
     * @param keepAlive {@code true} to enable TCP keepalive
     */
    void setKeepAlive(boolean keepAlive);

    /**
     * Set the OOB-inline socket parameter.
     *
     * @param oobInline {@code true} to enable inline OOB messages
     */
    void setOobInline(boolean oobInline);

    /**
     * Set the socket receive buffer size.
     *
     * @param receiveBufferSize the receive buffer size
     */
    void setReceiveBufferSize(int receiveBufferSize);

    /**
     * Set the reuse address socket parameter.
     *
     * @param reuseAddress {@code true} to enable address reuse
     */
    void setReuseAddress(boolean reuseAddress);

    /**
     * Set the TCP-no-delay socket parameter.
     *
     * @param tcpNoDelay {@code true} to enable TCP-no-delay
     */
    void setTcpNoDelay(boolean tcpNoDelay);

    /**
     * Set the socket backlog parameters.
     *
     * @param backlog the socket backlog
     */
    void setBacklog(int backlog);

    /**
     * Set the bind addresses to use for this server.
     *
     * @param bindAddresses the bind addresses
     */
    void setBindAddresses(SocketAddress[] bindAddresses);
}
