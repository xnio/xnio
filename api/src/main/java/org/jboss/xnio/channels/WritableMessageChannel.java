package org.jboss.xnio.channels;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * A channel that can send messages.
 */
public interface WritableMessageChannel extends ConfigurableChannel {
    /**
     * Send a complete message.
     *
     * @param buffer the message to send
     * @return {@code true} if the message was sent, or {@code false} if there was insufficient room in the send buffer
     * @throws IOException if an I/O error occurs
     */
    boolean send(ByteBuffer buffer) throws IOException;

    /**
     * Send a complete message.
     *
     * @param buffers the buffers holding the message to send
     * @return {@code true} if the message was sent, or {@code false} if there was insufficient room in the send buffer
     * @throws IOException if an I/O error occurs
     */
    boolean send(ByteBuffer[] buffers) throws IOException;

    /**
     * Send a complete message.
     *
     * @param buffers the buffers holding the message to send
     * @param offs the offset into the buffer array of the first buffer
     * @param len the number of buffers that contain data to send
     * @return {@code true} if the message was sent, or {@code false} if there was insufficient room in the send buffer
     * @throws IOException if an I/O error occurs
     */
    boolean send(ByteBuffer[] buffers, int offs, int len) throws IOException;
}
