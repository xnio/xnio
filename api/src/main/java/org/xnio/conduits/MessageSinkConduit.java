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

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * A message sink conduit.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface MessageSinkConduit extends SinkConduit {

    /**
     * Send a complete message.
     *
     * @param src the message to send
     * @return the result of the send operation; {@code true} if the message was sent, or {@code false} if it would block
     * @throws IOException if an I/O error occurs
     */
    boolean send(ByteBuffer src) throws IOException;

    /**
     * Send a complete message.
     *
     * @param srcs the buffers holding the message to send
     * @param offs the offset into the buffer array of the first buffer
     * @param len the number of buffers that contain data to send
     * @return the result of the send operation; {@code true} if the message was sent, or {@code false} if it would block
     * @throws IOException if an I/O error occurs
     */
    boolean send(ByteBuffer[] srcs, int offs, int len) throws IOException;


    /**
     * Send a complete message. If the message is successfully sent then the sink will have its writes terminated.
     *
     * @param src the message to send
     * @return the result of the send operation; {@code true} if the message was sent, or {@code false} if it would block
     * @throws IOException if an I/O error occurs
     */
    boolean sendFinal(ByteBuffer src) throws IOException;

    /**
     * Send a complete message. If the message is successfully sent then the sink will have its writes terminated.
     *
     * @param srcs the buffers holding the message to send
     * @param offs the offset into the buffer array of the first buffer
     * @param len the number of buffers that contain data to send
     * @return the result of the send operation; {@code true} if the message was sent, or {@code false} if it would block
     * @throws IOException if an I/O error occurs
     */
    boolean sendFinal(ByteBuffer[] srcs, int offs, int len) throws IOException;
}
