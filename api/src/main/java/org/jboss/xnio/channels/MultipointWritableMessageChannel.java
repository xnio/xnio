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
import java.io.IOException;
import org.jboss.xnio.ChannelListener;

/**
 * The writable side of a multipoint message channel.
 *
 * @see org.jboss.xnio.channels.MultipointMessageChannel
 * @param <A> the type of address associated with this channel
 */
public interface MultipointWritableMessageChannel<A> extends SuspendableWriteChannel {

    /**
     * Send a buffer to a destination.
     *
     * @param target the destination
     * @param buffer the data to send
     * @return {@code true} if the message was sent, or {@code false} if the channel is not currently writable
     * @throws IOException if an I/O error occurs
     */
    boolean send(A target, ByteBuffer buffer) throws IOException;

    /**
     * Send a message with data from multiple buffers to a destination.
     *
     * @param target the destination
     * @param buffers the data to send
     * @return {@code true} if the message was sent, or {@code false} if the channel is not currently writable
     * @throws IOException if an I/O error occurs
     */
    boolean send(A target, ByteBuffer[] buffers) throws IOException;

    /**
     * Send a message with data from multiple buffers to a destination.
     *
     * @param target the destination
     * @param buffers the data to send
     * @param offset the offset into the {@code buffers} array
     * @param length the number of buffers to read from
     * @return {@code true} if the message was sent, or {@code false} if the channel is not currently writable
     * @throws IOException if an I/O error occurs
     */
    boolean send(A target, ByteBuffer[] buffers, int offset, int length) throws IOException;
}
