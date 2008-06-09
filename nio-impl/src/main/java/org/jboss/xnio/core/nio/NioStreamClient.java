package org.jboss.xnio.core.nio;

import java.util.Random;
import org.jboss.xnio.StreamIoClient;
import org.jboss.xnio.StreamIoConnector;
import org.jboss.xnio.IoFuture;
import org.jboss.xnio.IoHandler;
import org.jboss.xnio.channels.ConnectedStreamChannel;
import org.jboss.xnio.ConnectionAddress;
import org.jboss.xnio.spi.Lifecycle;

/**
 *
 */
public final class NioStreamClient<A, T extends ConnectedStreamChannel<A>> implements Lifecycle, StreamIoClient<T> {

    private StreamIoConnector<A, T> connector;
    private ConnectionAddress<A>[] addresses;

    public StreamIoConnector<A, T> getConnector() {
        return connector;
    }

    public void setConnector(final StreamIoConnector<A, T> connector) {
        this.connector = connector;
    }

    public ConnectionAddress<A>[] getAddresses() {
        return addresses;
    }

    public void setAddresses(final ConnectionAddress<A>[] addresses) {
        this.addresses = addresses;
    }

    public void create() throws Exception {
        if (connector == null) {
            throw new NullPointerException("connector is null");
        }
        if (addresses == null) {
            throw new NullPointerException("addresses is null");
        }
    }

    public void start() throws Exception {
    }

    public void stop() throws Exception {
    }

    public void destroy() throws Exception {
    }

    public IoFuture<T> connect(final IoHandler<? super T> handler) {
        final int idx = addresses.length == 1 ? 0 : new Random().nextInt(addresses.length);
        final ConnectionAddress<A> connectionAddress = addresses[idx];
        return connector.connectTo(connectionAddress.getLocalAddress(), connectionAddress.getRemoteAddress(), handler);
    }
}
