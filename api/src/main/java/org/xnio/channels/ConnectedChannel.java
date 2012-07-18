
package org.xnio.channels;

import java.net.SocketAddress;
import org.xnio.ChannelListener;

/**
 * A channel that has a local and peer endpoint address.
 */
public interface ConnectedChannel extends BoundChannel {
    /**
     * Get the peer address of this channel.
     *
     * @return the peer address
     */
    SocketAddress getPeerAddress();

    /**
     * Get the peer address of a given type, or {@code null} if the address is not of that
     * type.
     *
     * @param type the address type class
     * @return the peer address, or {@code null} if unknown
     */
    <A extends SocketAddress> A getPeerAddress(Class<A> type);

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends ConnectedChannel> getCloseSetter();
}
