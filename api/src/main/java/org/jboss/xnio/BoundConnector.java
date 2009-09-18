/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, JBoss Inc., and individual contributors as indicated
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

import java.nio.channels.Channel;

/**
 * A bound connector.  Instances of this interface are used to connect to arbitrary peers.
 *
 * @param <A> the address type
 * @param <T> the type of channel
 */
public interface BoundConnector<A, T extends Channel> {

    /**
     * Establish a connection to a destination.
     *
     * @param dest the destination address
     * @param handler the handler which will be notified when the channel is open
     * @return the future result of this operation
     */
    FutureConnection<A, T> connectTo(A dest, ChannelListener<? super T> handler);

    /**
     * Create a client that always connects to the given destination.
     *
     * @param dest the destination to connect to
     * @return the client
     */
    ChannelSource<T> createChannelSource(A dest);
}
