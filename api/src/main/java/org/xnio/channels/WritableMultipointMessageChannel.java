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

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.io.IOException;
import org.xnio.ChannelListener;

/**
 * The writable side of a multipoint message channel.
 *
 * @see MultipointMessageChannel
 */
public interface WritableMultipointMessageChannel extends SuspendableWriteChannel {

    /**
     * Send a buffer to a destination.
     *
     * @param target the destination
     * @param buffer the data to send
     * @return {@code true} if the message was sent, or {@code false} if the channel is not currently writable
     * @throws IOException if an I/O error occurs
     */
    boolean sendTo(SocketAddress target, ByteBuffer buffer) throws IOException;

    /**
     * Send a message with data from multiple buffers to a destination.
     *
     * @param target the destination
     * @param buffers the data to send
     * @return {@code true} if the message was sent, or {@code false} if the channel is not currently writable
     * @throws IOException if an I/O error occurs
     */
    boolean sendTo(SocketAddress target, ByteBuffer[] buffers) throws IOException;

    /**
     * Send a message with data from multiple buffers to a destination.
     *
     * @param target the destination
     * @param buffers the data to send
     * @param offset the offset into the {@code buffers} array
     * @param length the number of buffers to read from
     * @return {@code true} if the message was sent, or {@code false} if the channel is not currently writable
     * @throws IOException if an I/O error occurs
     */
    boolean sendTo(SocketAddress target, ByteBuffer[] buffers, int offset, int length) throws IOException;

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends WritableMultipointMessageChannel> getWriteSetter();

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends WritableMultipointMessageChannel> getCloseSetter();
}
