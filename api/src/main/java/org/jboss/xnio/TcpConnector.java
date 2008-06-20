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

import java.net.SocketAddress;
import org.jboss.xnio.channels.TcpChannel;

/**
 * A connector specifically for connecting to TCP servers.
 */
public interface TcpConnector extends Connector<SocketAddress, TcpChannel> {

    /**
     * Establish a connection to a TCP server.
     *
     * @param dest the destination address
     * @param handler the handler for this connection
     * @return the future result of this operation
     */
    IoFuture<TcpChannel> connectTo(final SocketAddress dest, final IoHandler<? super TcpChannel> handler);

    /**
     * Establish a connection to a TCP server.
     *
     * @param src the source address
     * @param dest the destination address
     * @param handler the handler for this connection
     * @return the future result of this operation
     */
    IoFuture<TcpChannel> connectTo(final SocketAddress src, final SocketAddress dest, final IoHandler<? super TcpChannel> handler);

    /**
     * Create a client that always connects to the given TCP server.
     *
     * @param dest the destination to connect to
     * @return the client
     */
    TcpClient createClient(final SocketAddress dest);

    /**
     * Create a client that always connects to the given TCP from the given source address.
     *
     * @param src the source to connect from
     * @param dest the destination to connect to
     * @return the client
     */
    TcpClient createClient(final SocketAddress src, final SocketAddress dest);
}
