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

import org.xnio.ChannelListener;

/**
 * A multipoint datagram channel.  A multipoint datagram channel is a bound multipoint message channel.
 */
public interface BoundMultipointMessageChannel extends MultipointMessageChannel, BoundChannel {

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends BoundMultipointMessageChannel> getReadSetter();

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends BoundMultipointMessageChannel> getCloseSetter();

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends BoundMultipointMessageChannel> getWriteSetter();
}
