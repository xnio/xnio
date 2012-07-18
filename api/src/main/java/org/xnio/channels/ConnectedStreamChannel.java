
package org.xnio.channels;

import org.xnio.ChannelListener;

/**
 * A stream channel that is a connection between a local and remote endpoint.
 */
public interface ConnectedStreamChannel extends StreamChannel, ConnectedChannel {

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends ConnectedStreamChannel> getReadSetter();

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends ConnectedStreamChannel> getWriteSetter();

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends ConnectedStreamChannel> getCloseSetter();
}
