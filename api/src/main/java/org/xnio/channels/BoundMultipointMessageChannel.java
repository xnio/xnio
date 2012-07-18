
package org.xnio.channels;

import org.xnio.ChannelListener;

/**
 * A multipoint datagram channel.  A multipoint datagram channel is a bound multipoint message channel.
 */
public interface BoundMultipointMessageChannel extends MultipointMessageChannel, BoundChannel {

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends BoundMultipointMessageChannel> getReadSetter();

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends BoundMultipointMessageChannel> getCloseSetter();

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends BoundMultipointMessageChannel> getWriteSetter();
}
