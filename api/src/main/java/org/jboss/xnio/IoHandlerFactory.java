package org.jboss.xnio;

import java.nio.channels.Channel;

/**
 * A factory for I/O handlers.  Instances of this interface produce handler instances for clients and servers that
 * deal with arbitrary numbers of connections.
 *
 * @param <T> the type of channel that the handler can handle
 */
public interface IoHandlerFactory<T extends Channel> {
    /**
     * Create a handler for a potential new channel.
     *
     * @return the handler
     */
    IoHandler<? super T> createHandler();
}
