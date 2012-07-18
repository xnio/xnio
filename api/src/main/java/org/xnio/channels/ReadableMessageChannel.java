/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008 Red Hat, Inc. and/or its affiliates.
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
import java.nio.ByteBuffer;
import org.xnio.ChannelListener;

/**
 * A channel that can receive messages.  Such a channel receives whole messages only.
 */
public interface ReadableMessageChannel extends SuspendableReadChannel, Configurable {
    /**
     * Receive a message.
     *
     * @param buffer the buffer that will hold the message
     * @return the size of the received message, 0 if no message is available, and -1 if the message channel has reached an end-of-file condition
     * @throws IOException if an I/O error occurs
     */
    int receive(ByteBuffer buffer) throws IOException;

    /**
     * Receive a message.
     *
     * @param buffers the buffers that will hold the message
     * @return the size of the received message, 0 if no message is available, and -1 if the message channel has reached an end-of-file condition
     * @throws IOException if an I/O error occurs
     */
    long receive(ByteBuffer[] buffers) throws IOException;

    /**
     * Receive a message.
     *
     * @param buffers the buffers that will hold the message
     * @param offs the offset into the array of buffers of the first buffer to read into
     * @param len the number of buffers to fill
     * @return the size of the received message, 0 if no message is available, and -1 if the message channel has reached an end-of-file condition
     * @throws IOException if an I/O error occurs
     */
    long receive(ByteBuffer[] buffers, int offs, int len) throws IOException;

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends ReadableMessageChannel> getReadSetter();

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends ReadableMessageChannel> getCloseSetter();
}
