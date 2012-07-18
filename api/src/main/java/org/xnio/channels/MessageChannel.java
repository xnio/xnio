
package org.xnio.channels;

import org.xnio.ChannelListener;

/**
 * A channel that sends and receives whole messages.
 */
public interface MessageChannel extends ReadableMessageChannel, WritableMessageChannel, SuspendableChannel {

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends MessageChannel> getReadSetter();

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends MessageChannel> getCloseSetter();

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends MessageChannel> getWriteSetter();
}
