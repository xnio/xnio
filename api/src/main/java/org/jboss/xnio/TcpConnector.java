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

package org.jboss.xnio;

import java.net.InetSocketAddress;
import org.jboss.xnio.channels.TcpChannel;

/**
 * A connector specifically for connecting to TCP servers.
 *
 * @apiviz.landmark
 */
public interface TcpConnector extends Connector<InetSocketAddress, TcpChannel> {

    /**
     * Establish a connection to a TCP server.
     *
     * @param dest the destination address
     * @param handler the handler which will be notified when the channel is open
     * @return the future result of this operation
     */
    FutureConnection<InetSocketAddress, TcpChannel> connectTo(InetSocketAddress dest, ChannelListener<? super TcpChannel> handler);

    /**
     * Establish a connection to a TCP server.
     *
     * @param src the source address
     * @param dest the destination address
     * @param handler the handler which will be notified when the channel is open
     * @return the future result of this operation
     */
    FutureConnection<InetSocketAddress, TcpChannel> connectTo(InetSocketAddress src, InetSocketAddress dest, ChannelListener<? super TcpChannel> handler);

    /**
     * Create a client that always connects to the given TCP server.
     *
     * @param dest the destination to connect to
     * @return the client
     */
    TcpChannelSource createChannelSource(InetSocketAddress dest);

    /**
     * Create a client that always connects to the given TCP server from the given source address.
     *
     * @param src the source to connect from
     * @param dest the destination to connect to
     * @return the client
     */
    TcpChannelSource createChannelSource(InetSocketAddress src, InetSocketAddress dest);
}
