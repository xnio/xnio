package org.jboss.xnio.channels;

/**
 * A stream channel that is a connection between a local and remote endpoint.
 *
 * @param <A> the type of address associated with this channel
 */
public interface ConnectedStreamChannel<A> extends StreamChannel, ConnectedChannel<A> {
}
