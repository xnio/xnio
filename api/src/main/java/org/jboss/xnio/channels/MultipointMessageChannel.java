package org.jboss.xnio.channels;

/**
 * A point-to-multipoint message channel.  This type of channel is capable of sending to and receiving from multiple
 * peer endpoints; as such, the incoming and outgoing messages are each associated with a peer address.
 *
 * @param <A> the type of address associated with this channel
 */
public interface MultipointMessageChannel<A> extends MultipointReadableMessageChannel<A>, MultipointWritableMessageChannel<A>, BoundChannel<A> {
}
