package org.jboss.xnio.channels;

import java.nio.ByteBuffer;
import java.io.IOException;

/**
 * The readable side of a multipoint message channel.
 *
 * @see org.jboss.xnio.channels.MultipointMessageChannel
 * @param <A> the type of address associated with this channel
 */
public interface MultipointReadableMessageChannel<A> extends ConfigurableChannel {
    /**
     * Receive a message via this channel.
     *
     * If a message is immediately available, then the datagram is written into the given buffer and the source
     * and destination addresses (if available) are given to the handler.  If there is no message immediately available,
     * this method will return {@code false}.
     *
     * @param buffer the buffer into which data should be read
     * @param readHandler the handler to invoke with the reception details, or {@code null} for none
     *
     * @return {@code true} if a message was found and processed
     * @throws IOException if an I/O error occurs
     */
    boolean receive(ByteBuffer buffer, MultipointReadHandler<A> readHandler) throws IOException;
}
