
package org.xnio.channels;

import org.xnio.ChannelListener;

/**
 * A TLS-encapsulated connected stream channel.
 */
public interface ConnectedSslStreamChannel extends ConnectedStreamChannel, SslChannel {

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends ConnectedSslStreamChannel> getReadSetter();

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends ConnectedSslStreamChannel> getWriteSetter();

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends ConnectedSslStreamChannel> getCloseSetter();
}
