package org.jboss.xnio;

import java.nio.channels.Channel;

/**
 * A channel I/O handler.  Implementations of this interface handle traffic over a {@code Channel}.
 *
 * @param <T> the type of channel that the handler can handle
 */
public interface IoHandler<T extends Channel> {

    /**
     * Handle channel open.  This method is called exactly once per channel.  When a channel is opened, both reads and
     * writes are suspended initially.
     *
     * @param channel the channel that was opened
     */
    void handleOpened(T channel);

    /**
     * Handle channel readability.  Called when the channel may be read from.  Further read notifications from the
     * channel are automatically suspended.
     *
     * @param channel the channel that is readable
     */
    void handleReadable(T channel);

    /**
     * Handle channel writability.  Called when the channel may be read from.  Further read notifications from the
     * channel are automatically suspended.
     *
     * @param channel the channel that is readable
     */
    void handleWritable(T channel);

    /**
     * Handle channel close.  This method is called exactly once when the channel is closed.  If the channel's
     * {@link java.nio.channels.Channel#close()} method is called again, this method is not invoked.
     *
     * @param channel the channel that was closed
     */
    void handleClose(T channel);
}
