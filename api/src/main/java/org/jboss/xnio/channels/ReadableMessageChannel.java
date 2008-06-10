package org.jboss.xnio.channels;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;

/**
 * A channel that can receive messages.  Such a channel receives whole messages only.
 */
public interface ReadableMessageChannel extends Channel {
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
    int receive(ByteBuffer[] buffers) throws IOException;

    /**
     * Receive a message.
     *
     * @param buffers the buffers that will hold the message
     * @param offs the offset into the array of buffers of the first buffer to read into
     * @param len the number of buffers to fill
     * @return the size of the received message, 0 if no message is available, and -1 if the message channel has reached an end-of-file condition
     * @throws IOException if an I/O error occurs
     */
    int receive(ByteBuffer[] buffers, int offs, int len) throws IOException;
}
