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

import java.nio.channels.Channel;
import org.jboss.xnio.channels.BoundChannel;

/**
 * An acceptor.  This is the inverse of {@code Connector}; it is used to accept a single connection from a remote
 * peer.
 *
 * @param <A> the address type
 * @param <T> the channel type
 * 
 * @since 1.2
 */
public interface Acceptor<A, T extends Channel> {
    /**
     * Accept a connection at a destination address.  If a wildcard address is specified, then a destination address
     * is chosen in a manner specific to the OS and/or channel type.
     *
     * @param dest the destination address
     * @param openListener the listener which will be notified when the channel is open, or {@code null} for none
     * @param bindListener the listener which will be notified when the acceptor is bound, or {@code null} for none
     * @return the future connection
     */
    IoFuture<T> acceptTo(A dest, ChannelListener<? super T> openListener, ChannelListener<? super BoundChannel<A>> bindListener);

    /**
     * Create a channel destination for this acceptor, which always uses a specific destination address.  If a wildcard
     * address is specified, then a destination address is chosen in a manner specific to the OS and/or channel type for
     * each accept operation.
     *
     * @param dest the destination address
     * @return a channel destination instance
     */
    ChannelDestination<A, T> createChannelDestination(A dest);
}
