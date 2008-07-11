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
import java.io.IOException;

/**
 * A suspendable writable channel.  This type of channel is associated with a handler which can suspend and resume
 * writes as needed.
 */
public interface SuspendableWriteChannel extends Channel, Configurable {
    /**
     * Suspend further writes on this channel.  The {@link org.jboss.xnio.IoHandler#handleWritable(java.nio.channels.Channel)} method will not
     * be called until writes are resumed.
     */
    void suspendWrites();

    /**
     * Resume writes on this channel.  The {@link org.jboss.xnio.IoHandler#handleWritable(java.nio.channels.Channel)} method will be
     * called as soon as there is space in the channel's transmit buffer.
     */
    void resumeWrites();

    /**
     * Indicate that writing is complete for this channel.  Further attempts to write after shutdown will result in an
     * exception.
     *
     * @throws java.io.IOException if an I/O error occurs
     */
    void shutdownWrites() throws IOException;
}
