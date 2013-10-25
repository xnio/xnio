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
 * A channel that can send messages.
 */
public interface WritableMessageChannel extends SuspendableWriteChannel, Configurable {
    /**
     * Send a complete message.
     *
     * @param buffer the message to send
     * @return the result of the send operation; {@code true} if the message was sent, or {@code false} if it would block
     * @throws IOException if an I/O error occurs
     */
    boolean send(ByteBuffer buffer) throws IOException;

    /**
     * Send a complete message.
     *
     * @param buffers the buffers holding the message to send
     * @return the result of the send operation; {@code true} if the message was sent, or {@code false} if it would block
     * @throws IOException if an I/O error occurs
     */
    boolean send(ByteBuffer[] buffers) throws IOException;

    /**
     * Send a complete message.
     *
     * @param buffers the buffers holding the message to send
     * @param offs the offset into the buffer array of the first buffer
     * @param len the number of buffers that contain data to send
     * @return the result of the send operation; {@code true} if the message was sent, or {@code false} if it would block
     * @throws IOException if an I/O error occurs
     */
    boolean send(ByteBuffer[] buffers, int offs, int len) throws IOException;

    /**
     * Send a complete message. If the message was successfully sent the channel with have its writes shutdown.
     *
     * @param buffer the message to send
     * @return the result of the send operation; {@code true} if the message was sent, or {@code false} if it would block
     * @throws IOException if an I/O error occurs
     */
    boolean sendFinal(ByteBuffer buffer) throws IOException;

    /**
     * Send a complete message. If the message was successfully sent the channel with have its writes shutdown.
     *
     * @param buffers the buffers holding the message to send
     * @return the result of the send operation; {@code true} if the message was sent, or {@code false} if it would block
     * @throws IOException if an I/O error occurs
     */
    boolean sendFinal(ByteBuffer[] buffers) throws IOException;

    /**
     * Send a complete message. If the message was successfully sent the channel with have its writes shutdown.
     *
     * @param buffers the buffers holding the message to send
     * @param offs the offset into the buffer array of the first buffer
     * @param len the number of buffers that contain data to send
     * @return the result of the send operation; {@code true} if the message was sent, or {@code false} if it would block
     * @throws IOException if an I/O error occurs
     */
    boolean sendFinal(ByteBuffer[] buffers, int offs, int len) throws IOException;

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends WritableMessageChannel> getWriteSetter();

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends WritableMessageChannel> getCloseSetter();
}
