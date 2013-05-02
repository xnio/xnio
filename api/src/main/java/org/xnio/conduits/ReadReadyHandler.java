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

import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.channels.CloseListenerSettable;
import org.xnio.channels.ReadListenerSettable;
import org.xnio.channels.SuspendableReadChannel;

/**
 * A conduit read-ready handler.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface ReadReadyHandler extends TerminateHandler {

    /**
     * Signify that reads are ready.
     */
    void readReady();

    /**
     * A read ready handler which calls channel listener(s).
     *
     * @param <C> the channel type
     */
    class ChannelListenerHandler<C extends SuspendableReadChannel & ReadListenerSettable<C> & CloseListenerSettable<C>> implements ReadReadyHandler {
        private final C channel;

        /**
         * Construct a new instance.
         *
         * @param channel the channel
         */
        public ChannelListenerHandler(final C channel) {
            this.channel = channel;
        }

        public void forceTermination() {
            IoUtils.safeClose(channel);
        }

        public void readReady() {
            final ChannelListener<? super C> readListener = channel.getReadListener();
            if (readListener == null) {
                channel.suspendReads();
            } else {
                ChannelListeners.invokeChannelListener(channel, readListener);
            }
        }

        public void terminated() {
            ChannelListeners.invokeChannelListener(channel, channel.getCloseListener());
        }
    }

    /**
     * A runnable task which invokes the {@link ReadReadyHandler#readReady()} method of the given handler.
     */
    class ReadyTask implements Runnable {

        private final ReadReadyHandler handler;

        /**
         * Construct a new instance.
         *
         * @param handler the handler to invoke
         */
        public ReadyTask(final ReadReadyHandler handler) {
            this.handler = handler;
        }

        public void run() {
            handler.readReady();
        }
    }
}
