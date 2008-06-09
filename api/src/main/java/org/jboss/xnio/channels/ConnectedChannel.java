package org.jboss.xnio.channels;

/**
 * A channel that has a local and peer endpoint address.
 *
 * @param <A> the type of address associated with this channel
 */
public interface ConnectedChannel<A> extends BoundChannel<A> {
    /**
     * Get the peer address of this channel.
     *
     * @return the peer address
     */
    A getPeerAddress();
}
