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

import org.jboss.xnio.channels.BoundServer;
import org.jboss.xnio.channels.BoundChannel;
import org.jboss.xnio.channels.ConnectedChannel;

/**
 * A server which accepts connections.
 *
 * @param <A> the address type
 * @param <T> the bound channel type
 * @param <C> the accepted channel type
 */
public interface AcceptingServer<A, T extends BoundChannel<A>, C extends ConnectedChannel<A>> extends BoundServer<A, T> {

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends T> getBindSetter();

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends AcceptingServer<A, T, C>> getCloseSetter();

    /**
     * Get the connect handler setter for this server.  The handler will be called each time a connection is accepted.
     * If the handler is {@code null}, the channel will be immediately closed.
     *
     * @return the connect setter
     */
    ChannelListener.Setter<? extends C> getConnectSetter();
}
