
package org.xnio;

import java.nio.channels.Channel;
import org.xnio.channels.BoundChannel;

/**
 * A channel destination.  This is the inverse of {@code ChannelSource}; it is used to accept a single connection from a remote
 * peer.
 *
 * @param <T> the channel type
 *
 * @since 1.2
 */
public interface ChannelDestination<T extends Channel> {
    /**
     * Accept a connection.  The bind listener will be called when the channel is bound; the open listener will be called
     * when the connection is accepted.  It is not guaranteed that the open listener will be called after the bind listener.
     *
     * @param openListener the listener which will be notified when the channel is open, or {@code null} for none
     * @param bindListener the listener which will be notified when the channel is bound locally, or {@code null} for none
     * @return the future connection
     */
    IoFuture<? extends T> accept(ChannelListener<? super T> openListener, ChannelListener<? super BoundChannel> bindListener);
}
