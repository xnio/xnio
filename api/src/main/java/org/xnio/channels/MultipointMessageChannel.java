
package org.xnio.channels;

import org.xnio.ChannelListener;

/**
 * A point-to-multipoint message channel.  This type of channel is capable of sending to and receiving from multiple
 * peer endpoints; as such, the incoming and outgoing messages are each associated with a peer address.
 */
public interface MultipointMessageChannel extends ReadableMultipointMessageChannel, WritableMultipointMessageChannel, SuspendableChannel {

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends MultipointMessageChannel> getReadSetter();

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends MultipointMessageChannel> getCloseSetter();

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends MultipointMessageChannel> getWriteSetter();
}
