/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, JBoss Inc., and individual contributors as indicated
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

import java.io.Closeable;
import java.io.IOException;
import org.xnio.channels.CloseableChannel;

/**
 * A channel thread.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface ChannelThread<C extends CloseableChannel> extends Closeable {

    /**
     * Get the approximate load on this thread, in channels.
     *
     * @return the approximate load
     */
    int getLoad();

    /** {@inheritDoc} */
    void close() throws IOException;

    /**
     * Add a channel to this channel thread.  This method may be called from any thread at any time during
     * the life of this thread.
     *
     * @param channel the channel to add
     * @throws IOException if an error occurs while adding the channel to this thread
     * @throws IllegalArgumentException if the channel belongs to an incompatible provider
     * @throws ClassCastException if the channel is of the wrong type
     */
    void addChannel(C channel) throws IOException, IllegalArgumentException, ClassCastException;
}
