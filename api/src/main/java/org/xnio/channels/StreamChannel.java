
package org.xnio.channels;

import org.xnio.ChannelListener;

/**
 * A stream channel.  This type of channel represents a stream of bytes flowing in two directions.
 *
 * @apiviz.landmark
 */
public interface StreamChannel extends SuspendableChannel, StreamSinkChannel, StreamSourceChannel, ByteChannel {

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends StreamChannel> getReadSetter();

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends StreamChannel> getWriteSetter();

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends StreamChannel> getCloseSetter();
}
