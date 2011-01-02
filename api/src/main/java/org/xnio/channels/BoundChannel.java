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

package org.xnio.channels;

import java.net.SocketAddress;
import org.xnio.ChannelListener;

/**
 * A channel that is bound to a local address.
 */
public interface BoundChannel extends CloseableChannel {
    /**
     * Get the local address that this channel is bound to.
     *
     * @return the local address
     */
    SocketAddress getLocalAddress();

    /**
     * Get the local address of a given type, or {@code null} if the address is not of that
     * type.
     *
     * @param type the address type class
     * @param <A> the address type
     * @return the local address, or {@code null} if unknown
     */
    <A extends SocketAddress> A getLocalAddress(Class<A> type);

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends BoundChannel> getCloseSetter();
}
