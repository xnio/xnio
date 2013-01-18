/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2013 Red Hat, Inc. and/or its affiliates.
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

package org.xnio.channels;

import java.nio.channels.Channel;
import org.xnio.ChannelListener;

/**
 * An object which supports directly setting the read listener may implement this interface.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface ReadListenerSettable<C extends Channel> {
    /**
     * Set the read listener.
     *
     * @param listener the read listener
     */
    void setReadListener(ChannelListener<? super C> listener);

    /**
     * Get the read listener.
     *
     * @return the read listener
     */
    ChannelListener<? super C> getReadListener();

    /**
     * A channel listener setter implementation which delegates to the appropriate setter method.
     *
     * @param <C> the channel type
     */
    class Setter<C extends Channel> implements ChannelListener.Setter<C> {
        private final ReadListenerSettable<C> settable;

        /**
         * Construct a new instance.
         *
         * @param settable the settable to delegate to
         */
        public Setter(final ReadListenerSettable<C> settable) {
            this.settable = settable;
        }

        public void set(final ChannelListener<? super C> listener) {
            settable.setReadListener(listener);
        }
    }
}
