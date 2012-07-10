/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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
 * A base class for a channel listener which performs an action and then calls a delegate listener.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class DelegatingChannelListener<T extends Channel> implements ChannelListener<T> {
    private final ChannelListener<? super T> next;

    /**
     * Construct a new instance.  The next listener must be for the same type as, or a supertype of, the channel
     * type handleable by this listener.
     *
     * @param next the next listener
     */
    protected DelegatingChannelListener(final ChannelListener<? super T> next) {
        this.next = next;
    }

    /**
     * Call the next listener.  Does not throw exceptions.
     *
     * @param channel the channel to pass to the next listener
     */
    protected void callNext(T channel) {
        ChannelListeners.invokeChannelListener(channel, next);
    }
}
