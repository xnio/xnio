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

import java.nio.channels.Channel;
import java.nio.ByteBuffer;
import java.io.IOException;

/**
 * A channel that can receive messages.  Such a channel receives whole messages only; the messages are stored
 * in pre-filled and pre-sized buffers.
 */
public interface ReadableAllocatedMessageChannel extends Channel {
    /**
     * Receive a message.  The returned buffer's position is 0, the mark is not set, the limit is the size of the
     * received message, and the capacity is some value greater than or equal to the limit.  If the request would
     * block, an empty buffer is returned.  If the channel is closed from a read direction, {@code null} is returned.
     *
     * @return a buffer containing the received message, or {@code null} if the channel is at EOF
     * @throws java.io.IOException if an I/O error occurs
     */
    ByteBuffer receive() throws IOException;
}
