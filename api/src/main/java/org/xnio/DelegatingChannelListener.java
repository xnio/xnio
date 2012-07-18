/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2012 Red Hat, Inc. and/or its affiliates, and individual
 * contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
