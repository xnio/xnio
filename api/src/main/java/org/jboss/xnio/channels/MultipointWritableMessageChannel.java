package org.jboss.xnio.channels;

import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.io.IOException;

/**
 * The writable side of a multipoint message channel.
 *
 * @see org.jboss.xnio.channels.MultipointMessageChannel
 * @param <A> the type of address associated with this channel
 */
public interface MultipointWritableMessageChannel<A> extends Channel {

    /**
     * Send a buffer to a destination.
     *
     * @param target the destination
     * @param buffer the data to send
     * @return {@code true} if the message was sent, or {@code false} if the channel is not currently writable
     * @throws IOException if an I/O error occurs
     */
    boolean send(A target, ByteBuffer buffer) throws IOException;

    /**
     * Send a message with data from multiple buffers to a destination.
     *
     * @param target the destination
     * @param buffers the data to send
     * @return {@code true} if the message was sent, or {@code false} if the channel is not currently writable
     * @throws IOException if an I/O error occurs
     */
    boolean send(A target, ByteBuffer[] buffers) throws IOException;

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
    boolean send(A target, ByteBuffer[] buffers, int offset, int length) throws IOException;
}
