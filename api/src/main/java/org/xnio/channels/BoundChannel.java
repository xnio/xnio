
package org.xnio.channels;

import java.net.SocketAddress;
import org.xnio.ChannelListener;

/**
 * A channel that is bound to a local address.
 */
public interface BoundChannel extends CloseableChannel {
    /**
     * Get the local address that this channel is bound to.
     *
     * @return the local address
     */
    SocketAddress getLocalAddress();

    /**
     * Get the local address of a given type, or {@code null} if the address is not of that
     * type.
     *
     * @param type the address type class
     * @param <A> the address type
     * @return the local address, or {@code null} if unknown
     */
    <A extends SocketAddress> A getLocalAddress(Class<A> type);

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends BoundChannel> getCloseSetter();
}
