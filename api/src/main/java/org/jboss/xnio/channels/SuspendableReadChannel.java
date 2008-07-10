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

/**
 * A suspendable readable channel.  This type of channel is associated with a handler which can suspend and resume
 * reads as needed.
 */
public interface SuspendableReadChannel extends Channel, Configurable {
    /**
     * Suspend further reads on this channel.  The {@link org.jboss.xnio.IoHandler#handleReadable(java.nio.channels.Channel)} method will not
     * be called until reads are resumed.
     */
    void suspendReads();

    /**
     * Resume reads on this channel.  The {@link org.jboss.xnio.IoHandler#handleReadable(java.nio.channels.Channel)} method will be
     * called as soon as there is data available to be read.
     */
    void resumeReads();
}
