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

import org.jboss.xnio.ChannelListener;

/**
 * A point-to-multipoint message channel.  This type of channel is capable of sending to and receiving from multiple
 * peer endpoints; as such, the incoming and outgoing messages are each associated with a peer address.
 *
 * @param <A> the type of address associated with this channel
 */
public interface MultipointMessageChannel<A> extends MultipointReadableMessageChannel<A>, MultipointWritableMessageChannel<A>, SuspendableChannel {

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends MultipointMessageChannel<A>> getReadSetter();

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends MultipointMessageChannel<A>> getCloseSetter();

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends MultipointMessageChannel<A>> getWriteSetter();
}
