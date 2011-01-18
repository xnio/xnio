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

package org.xnio;

import java.nio.channels.Channel;

/**
 * A channel source.  Instances of this interface are used to create a channel and associate it with a listener.  Example
 * uses are to establish a TCP connection (as a client), open a serial port, etc.
 *
 * @param <T> the type of channel
 */
public interface ChannelSource<T extends Channel> {

    /**
     * Open a channel.
     *
     * @param openListener the listener which will be notified when the channel is open
     * @return the future result of this operation
     */
    IoFuture<T> open(ChannelListener<? super T> openListener);
}
