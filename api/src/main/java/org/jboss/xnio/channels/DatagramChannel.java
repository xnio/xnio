package org.jboss.xnio.channels;

import java.nio.ByteBuffer;
import java.io.IOException;

/**
 * A channel that sends and receives datagrams.  A datagram channel is a message channel that is suspendable, handled,
 * and is connected between two peers.
 *
 * @param <A> the type of address associated with this channel
 */
public interface DatagramChannel<A> extends MessageChannel, SuspendableChannel, ConnectedChannel<A>, Configurable {
}
