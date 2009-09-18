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
 * A channel that can receive messages.  Such a channel receives whole messages only; the messages are stored
 * in pre-filled and pre-sized buffers.
 */
public interface ReadableAllocatedMessageChannel extends SuspendableReadChannel {

    /**
     * Receive a message.  The returned buffer's position is 0, the mark is not set, the limit is the size of the
     * received message, and the capacity is some value greater than or equal to the limit.  If the request would
     * block, {@link #WOULD_BLOCK} is returned.  If the channel is closed from a read direction, {@link #EOF} is returned.
     * If an oversized message was received, the special {@link #GIANT} marker is returned, followed by the truncated
     * message.  If a zero-length message is received, {@link #EMPTY} is returned.
     *
     * @return a buffer containing the received message
     * @throws java.io.IOException if an I/O error occurs
     */
    ByteBuffer receive() throws IOException;

    /**
     * The special marker for end-of-file.
     */
    ByteBuffer EOF = ByteBuffer.allocate(0);

    /**
     * The special marker indicating that the receive request would block.
     */
    ByteBuffer WOULD_BLOCK = ByteBuffer.allocate(0);

    /**
     * The special marker indicating that the input buffer was overrun.  The next receive will return the first
     * part of the received message.
     */
    ByteBuffer GIANT = ByteBuffer.allocate(0);

    /**
     * The special marker indicating that the input message was shorter than its declared length.  The next receive
     * will return the message in a buffer whose limit is the actual length, and whose capacity was the expected length.
     * If the original size would have made the message a giant, the capacity will be set to no greater than the maximum
     * message size.
     */
    ByteBuffer RUNT = ByteBuffer.allocate(0);

    /**
     * The special marker indicating an empty message.  This buffer can be used as a regular buffer as well, so it can
     * be tested for either by identity comparison, or by checking the remaining size which will always be zero.
     */
    ByteBuffer EMPTY = ByteBuffer.allocate(0);

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends ReadableAllocatedMessageChannel> getReadSetter();
}
