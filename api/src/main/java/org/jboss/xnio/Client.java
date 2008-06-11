package org.jboss.xnio;

import java.nio.channels.Channel;

/**
 * A client.  Instances of this interface are used to establish a connection with a known remote endpoint.
 *
 * @param <T> the type of channel
 */
public interface Client<T extends Channel> {
    /**
     * Establish a connection.
     *
     * @param handler the handler for this connection
     * @return the future result of this operation
     */
    IoFuture<T> connect(IoHandler<? super T> handler);
}
