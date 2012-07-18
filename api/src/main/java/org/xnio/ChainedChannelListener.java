
package org.xnio;

import java.nio.channels.Channel;

/**
 * A channel listener that chains calls to a number of other channel listeners.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ChainedChannelListener<T extends Channel> implements ChannelListener<T> {
    private final ChannelListener<? super T>[] listeners;

    /**
     * Construct a new instance.  The given array will be copied.
     *
     * @param listeners the listeners to chain to
     */
    public ChainedChannelListener(final ChannelListener<? super T>... listeners) {
        this.listeners = listeners.clone();
    }

    public void handleEvent(final T channel) {
        for (ChannelListener<? super T> listener : listeners) {
            ChannelListeners.invokeChannelListener(channel, listener);
        }
    }
}
