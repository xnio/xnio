package org.jboss.xnio;

import org.jboss.xnio.channels.ConnectedChannel;

/**
 * A connector.  Instances of this interface are used to connect to arbitrary peers.
 *
 * @param <A> the address type
 * @param <T> the type of channel
 */
public interface Connector<A, T extends ConnectedChannel<A>> {
    /**
     * Establish a connection to a destination.
     *
     * @param dest the destination address
     * @param handler the handler for this connection
     * @return the future result of this operation
     */
    IoFuture<T> connectTo(A dest, IoHandler<? super T> handler);

    /**
     * Establish a connection to a destination using an explicit source.
     *
     * @param src the source address
     * @param dest the destination address
     * @param handler the handler for this connection
     * @return the future result of this operation
     */
    IoFuture<T> connectTo(A src, A dest, IoHandler<? super T> handler);
}
