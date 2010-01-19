/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, JBoss Inc., and individual contributors as indicated
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
import org.jboss.xnio.channels.BoundChannel;
import org.jboss.xnio.channels.SslTcpChannel;

/**
 *
 */
public interface SslTcpConnector extends Connector<InetSocketAddress, SslTcpChannel>{

    /**
     * Establish a connection to an SSL TCP server.
     *
     * @param destination the destination address
     * @param openListener the listener which will be notified when the channel is open, or {@code null} for none
     * @param bindListener the listener which will be notified when the channel is bound, or {@code null} for none
     * @return the future result of this operation
     */
    IoFuture<SslTcpChannel> connectTo(InetSocketAddress destination, ChannelListener<? super SslTcpChannel> openListener, ChannelListener<? super BoundChannel<InetSocketAddress>> bindListener);

    /**
     * Create a channel source that always connects to the given SSL TCP server.
     *
     * @param destination the destination to connect to
     * @return the channel source
     */
    ChannelSource<SslTcpChannel> createChannelSource(InetSocketAddress destination);
}
