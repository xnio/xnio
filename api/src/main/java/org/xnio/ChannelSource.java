
package org.xnio;

import java.nio.channels.Channel;

/**
 * A channel source.  Instances of this interface are used to create a channel and associate it with a listener.  Example
 * uses are to establish a TCP connection (as a client), open a serial port, etc.
 *
 * @param <T> the type of channel
 */
public interface ChannelSource<T extends Channel> {

    /**
     * Open a channel.
     *
     * @param openListener the listener which will be notified when the channel is open
     * @return the future result of this operation
     */
    IoFuture<T> open(ChannelListener<? super T> openListener);
}
