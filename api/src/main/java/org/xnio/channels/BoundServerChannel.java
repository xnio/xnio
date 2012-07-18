
package org.xnio.channels;

import java.net.SocketAddress;
import java.util.Collection;
import org.xnio.IoFuture;
import org.xnio.ChannelListener;

/**
 * A server that is bound to one or more local addresses.
 *
 * @since 1.2
 */
public interface BoundServerChannel extends CloseableChannel {
    /**
     * Get the channels representing the individual bound servers.  The collection is a snapshot view of the bound
     * channels; modifications to the collection are not allowed.  However the channels within the collection are
     * live references to the bindings that exist at the time this method is called; these channels may be closed
     * to unbind the channel.
     *
     * @return the channels
     */
    Collection<? extends BoundChannel> getChannels();

    /**
     * Add a binding.  The returned channel may be used to close the binding.
     *
     * @param address the address to bind to
     * @return a future channel representing the binding
     */
    IoFuture<? extends BoundChannel> bind(SocketAddress address);

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends BoundServerChannel> getCloseSetter();
}
