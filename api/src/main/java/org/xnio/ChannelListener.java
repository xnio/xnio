/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2009 Red Hat, Inc. and/or its affiliates.
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
import java.util.EventListener;
import org.jboss.logging.Logger;

import static org.xnio._private.Messages.listenerMsg;

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

    /**
     * A simple implementation of {@link Setter}.
     *
     * @param <T> the channel type
     *
     * @since 3.0
     */
    class SimpleSetter<T extends Channel> implements Setter<T> {

        private ChannelListener<? super T> channelListener;

        /** {@inheritDoc} */
        public void set(final ChannelListener<? super T> listener) {
            listenerMsg.logf(SimpleSetter.class.getName(), Logger.Level.TRACE, null, "Setting channel listener to %s", listener);
            channelListener = listener;
        }

        /**
         * Get the channel listener set on this setter.
         *
         * @return the channel listener
         */
        public ChannelListener<? super T> get() {
            return channelListener;
        }

        public String toString() {
            return "Simple channel listener setter (currently=" + channelListener + ")";
        }
    }
}
