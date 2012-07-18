
package org.xnio.channels;

import java.nio.channels.Channel;

/**
 * A wrapped channel.
 *
 * @param <C> the wrapped channel type
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface WrappedChannel<C extends Channel> {

    /**
     * Get the channel which is wrapped by this object.
     *
     * @return the wrapped channel
     */
    C getChannel();
}
