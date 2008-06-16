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

import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.io.IOException;

/**
 * The readable side of a multipoint message channel.
 *
 * @see org.jboss.xnio.channels.MultipointMessageChannel
 * @param <A> the type of address associated with this channel
 */
public interface MultipointReadableMessageChannel<A> extends Channel {
    /**
     * Receive a message via this channel.
     *
     * If a message is immediately available, then the datagram is written into the given buffer and the source
     * and destination addresses (if available) are returned.  If there is no message immediately available,
     * this method will return {@code null}.
     *
     * @param buffer the buffer into which data should be read
     *
     * @return a result instance if a message was found and processed, or {@code null} if the operation would block
     * @throws IOException if an I/O error occurs
     */
    MultipointReadResult<A> receive(ByteBuffer buffer) throws IOException;
}
