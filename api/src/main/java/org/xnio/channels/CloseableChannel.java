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

package org.xnio.channels;

import java.io.IOException;
import java.nio.channels.InterruptibleChannel;
import org.xnio.ChannelListener;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;

/**
 * A channel which is closeable.  A listener may be registered which is triggered (only once) on channel close.
 *
 * @since 2.0
 */
public interface CloseableChannel extends InterruptibleChannel, Configurable {
    /**
     * Get the setter which can be used to change the close listener for this channel.  If the channel is already
     * closed, then the listener will not be called.
     *
     * @return the setter
     */
    ChannelListener.Setter<? extends CloseableChannel> getCloseSetter();

    /**
     * Get the worker for this channel.
     *
     * @return the worker
     */
    XnioWorker getWorker();

    /**
     * Get the I/O thread associated with this channel.
     *
     * @return the I/O thread associated with this channel
     */
    XnioIoThread getIoThread();

    /**
     * Close this channel.  When a channel is closed, its close listener is invoked.  Invoking this method
     * more than once has no additional effect.
     *
     * @throws IOException if the close failed
     */
    void close() throws IOException;
}
