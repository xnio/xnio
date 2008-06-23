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

package org.jboss.xnio;

import java.nio.channels.Channel;

/**
 * A channel I/O handler.  Implementations of this interface handle traffic over a {@code Channel}.
 *
 * @param <T> the type of channel that the handler can handle
 */
public interface IoHandler<T extends Channel> {

    /**
     * Handle channel open.  This method is called exactly once per channel.  When a channel is opened, both reads and
     * writes are suspended initially, and must be resumed manually.  If this method fails by throwing an exception,
     * the channel open is aborted and the underlying channel is terminated without invoking the {@link #handleClosed(java.nio.channels.Channel)}
     * method.
     *
     * @param channel the channel that was opened
     */
    void handleOpened(T channel);

    /**
     * Handle channel readability.  Called when the channel may be read from.  Further read notifications from the
     * channel are automatically suspended.
     *
     * @param channel the channel that is readable
     */
    void handleReadable(T channel);

    /**
     * Handle channel writability.  Called when the channel may be read from.  Further read notifications from the
     * channel are automatically suspended.
     *
     * @param channel the channel that is readable
     */
    void handleWritable(T channel);

    /**
     * Handle channel close.  This method is called exactly once when the channel is closed.  If the channel's
     * {@link java.nio.channels.Channel#close()} method is called again, this method is not invoked.
     *
     * @param channel the channel that was closed
     */
    void handleClosed(T channel);
}
