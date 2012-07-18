
package org.xnio.channels;

import java.io.IOException;
import org.xnio.ChannelListener;

/**
 * A channel which can accept inbound connections from remote endpoints.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @param <C> the channel type
 */
public interface AcceptingChannel<C extends ConnectedChannel> extends BoundChannel, SimpleAcceptingChannel<C> {

    /** {@inheritDoc} */
    C accept() throws IOException;

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends AcceptingChannel<C>> getAcceptSetter();

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends AcceptingChannel<C>> getCloseSetter();
}
