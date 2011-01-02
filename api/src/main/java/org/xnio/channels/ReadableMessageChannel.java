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

import java.io.IOException;
import java.nio.ByteBuffer;
import org.xnio.ChannelListener;

/**
 * A channel that can receive messages.  Such a channel receives whole messages only.
 */
public interface ReadableMessageChannel extends SuspendableReadChannel, Configurable {
    /**
     * Receive a message.
     *
     * @param buffer the buffer that will hold the message
     * @return the size of the received message, 0 if no message is available, and -1 if the message channel has reached an end-of-file condition
     * @throws IOException if an I/O error occurs
     */
    int receive(ByteBuffer buffer) throws IOException;

    /**
     * Receive a message.
     *
     * @param buffers the buffers that will hold the message
     * @return the size of the received message, 0 if no message is available, and -1 if the message channel has reached an end-of-file condition
     * @throws IOException if an I/O error occurs
     */
    long receive(ByteBuffer[] buffers) throws IOException;

    /**
     * Receive a message.
     *
     * @param buffers the buffers that will hold the message
     * @param offs the offset into the array of buffers of the first buffer to read into
     * @param len the number of buffers to fill
     * @return the size of the received message, 0 if no message is available, and -1 if the message channel has reached an end-of-file condition
     * @throws IOException if an I/O error occurs
     */
    long receive(ByteBuffer[] buffers, int offs, int len) throws IOException;

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends ReadableMessageChannel> getReadSetter();

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends ReadableMessageChannel> getCloseSetter();
}
