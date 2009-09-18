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

package org.jboss.xnio.channels;

import java.util.Collection;
import org.jboss.xnio.IoFuture;
import org.jboss.xnio.ChannelListener;

/**
 * A server that is bound to a local address, and which may accept connections.
 *
 * @param <A> the type of address associated with this server
 * @param <T> the channel type
 * @since 1.2
 */
public interface BoundServer<A, T extends BoundChannel<A>> extends CloseableChannel {
    /**
     * Get the channels representing the individual bound servers.  The collection is a snapshot view of the bound
     * channels; modifications to the collection are not allowed.  However the channels within the collection are
     * live references to the bindings that exist at the time this method is called; these channels may be closed
     * to unbind the channel.
     *
     * @return the channels
     */
    Collection<T> getChannels();

    /**
     * Add a binding.  The returned channel may be used to close the binding.
     *
     * @param address the address to bind to
     * @return a future channel representing the binding
     */
    IoFuture<T> bind(A address);

    /**
     * Get the open handler setter for this channel.  If open events are ignored,
     * the channel will be immediately closed upon accept.
     *
     * @return the listener setter
     */
    ChannelListener.Setter<? extends BoundServer<A, T>> getOpenSetter();
}
