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

import java.nio.ByteBuffer;
import java.io.IOException;
import org.xnio.ChannelListener;

/**
 * The readable side of a multipoint message channel.
 */
public interface ReadableMultipointMessageChannel extends SuspendableReadChannel {
    /**
     * Receive a message via this channel.
     *
     * If a message is immediately available, then the datagram is written into the given buffer and the source
     * and destination addresses (if available) read into the address buffer.  If there is no message immediately available,
     * this method will return 0.
     *
     * @param addressBuffer the address buffer into which the source and destination addresses should be written ({@code null} to discard that information)
     * @param buffer the buffer into which data should be read
     * @return the size of the received message, 0 if no message is available, and -1 if the message channel has reached an end-of-file condition
     * @throws IOException if an I/O error occurs
     */
    int receiveFrom(SocketAddressBuffer addressBuffer, ByteBuffer buffer) throws IOException;

    /**
     * Receive a message.
     *
     * If a message is immediately available, then the datagram is written into the given buffers in a "scatter" fashion and the source
     * and destination addresses (if available) read into the address buffer.  If there is no message immediately available,
     * this method will return 0.
     *
     * @param addressBuffer the address buffer into which the source and destination addresses should be written ({@code null} to discard that information)
     * @param buffers the buffers that will hold the message
     * @return the size of the received message, 0 if no message is available, and -1 if the message channel has reached an end-of-file condition
     * @throws IOException if an I/O error occurs
     */
    long receiveFrom(SocketAddressBuffer addressBuffer, ByteBuffer[] buffers) throws IOException;

    /**
     * Receive a message.
     *
     * If a message is immediately available, then the datagram is written into the given buffers in a "scatter" fashion and the source
     * and destination addresses (if available) read into the address buffer.  If there is no message immediately available,
     * this method will return 0.
     *
     * @param addressBuffer the address buffer into which the source and destination addresses should be written ({@code null} to discard that information)
     * @param buffers the buffers that will hold the message
     * @param offs the offset into the array of buffers of the first buffer to read into
     * @param len the number of buffers to fill
     * @return the size of the received message, 0 if no message is available, and -1 if the message channel has reached an end-of-file condition
     * @throws IOException if an I/O error occurs
     */
    long receiveFrom(SocketAddressBuffer addressBuffer, ByteBuffer[] buffers, int offs, int len) throws IOException;

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends ReadableMultipointMessageChannel> getReadSetter();

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends ReadableMultipointMessageChannel> getCloseSetter();
}
