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

import java.io.IOException;
import java.nio.ByteBuffer;
import org.jboss.xnio.ChannelListener;

/**
 * A channel that can send messages.
 */
public interface WritableMessageChannel extends SuspendableWriteChannel, Configurable {
    /**
     * Send a complete message.
     *
     * @param buffer the message to send
     * @return the result of the send operation
     * @throws IOException if an I/O error occurs
     */
    Result send(ByteBuffer buffer) throws IOException;

    /**
     * Send a complete message.
     *
     * @param buffers the buffers holding the message to send
     * @return the result of the send operation
     * @throws IOException if an I/O error occurs
     */
    Result send(ByteBuffer[] buffers) throws IOException;

    /**
     * Send a complete message.
     *
     * @param buffers the buffers holding the message to send
     * @param offs the offset into the buffer array of the first buffer
     * @param len the number of buffers that contain data to send
     * @return the result of the send operation
     * @throws IOException if an I/O error occurs
     */
    Result send(ByteBuffer[] buffers, int offs, int len) throws IOException;

    /**
     * Flush any waiting partial message.
     *
     * @return {@code true} if the message was flushed, or {@code false} if the result would block
     * @throws IOException if an I/O error occurs
     */
    boolean flush() throws IOException;

    /**
     * @see Result#OK
     */
    Result OK = Result.OK;
    /**
     * @see Result#PARTIAL
     */
    Result PARTIAL = Result.PARTIAL;
    /**
     * @see Result#NOT_SENT
     */
    Result NOT_SENT = Result.NOT_SENT;

    /**
     * The result of a send operation.
     */
    enum Result {

        /**
         * The message was accepted for delivery.
         */
        OK,
        /**
         * The message was accepted, however it was not fully delivered; a subsequent successful send or call to {@link WritableMessageChannel#flush()}
         * is necessary to ensure the message is written.
         */
        PARTIAL,
        /**
         * The message was not send because the operation would block.
         */
        NOT_SENT,
    }

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends WritableMessageChannel> getWriteSetter();

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends WritableMessageChannel> getCloseSetter();
}
