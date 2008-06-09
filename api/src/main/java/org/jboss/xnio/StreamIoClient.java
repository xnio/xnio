package org.jboss.xnio;

import org.jboss.xnio.channels.StreamChannel;

/**
 * A stream client.  Instances of this interface are used to establish a connection with a known remote endpoint.
 *
 * @param <T> the type of channel
 */
public interface StreamIoClient<T extends StreamChannel> {
    /**
     * Establish a connection.
     *
     * @param handler the handler for this connection
     * @return the future result of this operation
     */
    IoFuture<T> connect(IoHandler<? super T> handler);
}
