package org.jboss.xnio.channels;

import java.nio.ByteBuffer;
import java.io.IOException;

/**
 * A multipoint datagram channel.  A multipoint datagram channel is a suspendable multipoint message channel.
 *
 * @param <A> the type of address associated with this channel
 */
public interface MultipointDatagramChannel<A> extends MultipointMessageChannel<A>, SuspendableChannel {
}
