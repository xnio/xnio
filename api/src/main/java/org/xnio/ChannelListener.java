/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, JBoss Inc., and individual contributors as indicated
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
import java.util.EventListener;

/**
 * A listener for channel events.  Possible channel events include: channel readable, channel writable, channel
 * opened, channel closed, channel bound, channel unbound.
 *
 * @param <T> the channel type
 *
 * @since 2.0
 */
public interface ChannelListener<T extends Channel> extends EventListener {

    /**
     * Handle the event on this channel.
     *
     * @param channel the channel event
     */
    void handleEvent(T channel);

    /**
     * A setter for a channel listener.  The indirection is necessary
     * because while covariance is supported on return types, contravariance is not supported on parameters.
     *
     * @param <T> the channel type
     *
     * @since 2.0
     */
    interface Setter<T extends Channel> {

        /**
         * Set the listener, or {@code null} to ignore the associated event type.
         *
         * @param listener the new listener
         */
        void set(ChannelListener<? super T> listener);
    }
}
