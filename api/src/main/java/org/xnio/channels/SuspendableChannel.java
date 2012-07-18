
package org.xnio.channels;

import org.xnio.ChannelListener;

/**
 * A suspendable bidirectional channel.
 */
public interface SuspendableChannel extends CloseableChannel, SuspendableReadChannel, SuspendableWriteChannel {

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends SuspendableChannel> getCloseSetter();

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends SuspendableChannel> getReadSetter();

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends SuspendableChannel> getWriteSetter();
}
