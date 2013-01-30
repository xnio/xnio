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

package org.xnio.conduits;

import java.nio.channels.Channel;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.channels.CloseListenerSettable;

/**
 * The base ready handler type, which can forward termination requests as well as notifications of termination
 * completion.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface TerminateHandler {

    /**
     * Force the front-end channel to close, in response to XNIO worker shutdown.
     */
    void forceTermination();

    /**
     * Indicate that a previous shutdown request has successfully resulted in termination.
     */
    void terminated();

    /**
     * A terminate handler which calls a channel listener on termination notification.
     *
     * @param <C> the channel type
     */
    class ChannelListenerHandler<C extends Channel & CloseListenerSettable<C>> implements TerminateHandler {
        private final C channel;

        /**
         * Construct a new instance.
         *
         * @param channel the channel to wrap
         */
        public ChannelListenerHandler(final C channel) {
            this.channel = channel;
        }

        public void forceTermination() {
            IoUtils.safeClose(channel);
        }

        public void terminated() {
            ChannelListeners.invokeChannelListener(channel, channel.getCloseListener());
        }
    }

    /**
     * A runnable task which invokes the {@link TerminateHandler#terminated()} method of the given handler.
     */
    class ReadyTask implements Runnable {

        private final TerminateHandler handler;

        /**
         * Construct a new instance.
         *
         * @param handler the handler to run
         */
        public ReadyTask(final TerminateHandler handler) {
            this.handler = handler;
        }

        public void run() {
            handler.terminated();
        }
    }
}
