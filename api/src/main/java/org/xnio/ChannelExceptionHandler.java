
package org.xnio;

import java.io.IOException;
import java.nio.channels.Channel;
import java.util.EventListener;

/**
 * An exception handler for utility channel listeners.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface ChannelExceptionHandler<T extends Channel> extends EventListener {

    /**
     * Handle an exception on the channel.
     *
     * @param channel the channel
     * @param exception the exception
     */
    void handleException(T channel, IOException exception);
}
