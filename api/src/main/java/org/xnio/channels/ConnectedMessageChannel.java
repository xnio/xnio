
package org.xnio.channels;

import org.xnio.ChannelListener;

/**
 * A channel that sends and receives messages to a connected peer.
 */
public interface ConnectedMessageChannel extends MessageChannel, ConnectedChannel {

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends ConnectedMessageChannel> getReadSetter();

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends ConnectedMessageChannel> getCloseSetter();

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends ConnectedMessageChannel> getWriteSetter();
}
