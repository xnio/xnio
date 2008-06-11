package org.jboss.xnio.channels;

import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.io.IOException;

/**
 * The readable side of a multipoint message channel.
 *
 * @see org.jboss.xnio.channels.MultipointMessageChannel
 * @param <A> the type of address associated with this channel
 */
public interface MultipointReadableMessageChannel<A> extends Channel {
    /**
     * Receive a message via this channel.
     *
     * If a message is immediately available, then the datagram is written into the given buffer and the source
     * and destination addresses (if available) are returned.  If there is no message immediately available,
     * this method will return {@code null}.
     *
     * @param buffer the buffer into which data should be read
     *
     * @return a result instance if a message was found and processed, or {@code null} if the operation would block
     * @throws IOException if an I/O error occurs
     */
    MultipointReadResult<A> receive(ByteBuffer buffer) throws IOException;
}
