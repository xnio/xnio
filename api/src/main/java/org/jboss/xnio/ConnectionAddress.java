package org.jboss.xnio;

/**
 * An immutable pair of addresses that define the two endpoints of a connection.
 *
 * @param <A> the address type
 */
public final class ConnectionAddress<A> {
    private final A localAddress;
    private final A remoteAddress;

    /**
     * Construct a new instance.
     *
     * @param localAddress the local address
     * @param remoteAddress the remote address
     */
    public ConnectionAddress(final A localAddress, final A remoteAddress) {
        if (localAddress == null) {
            throw new NullPointerException("localAddress is null");
        }
        if (remoteAddress == null) {
            throw new NullPointerException("remoteAddress is null");
        }
        this.localAddress = localAddress;
        this.remoteAddress = remoteAddress;
    }

    /**
     * Get the local address.
     *
     * @return the local address
     */
    public final A getLocalAddress() {
        return localAddress;
    }

    /**
     * Get the remote address.
     *
     * @return the remote address
     */
    public final A getRemoteAddress() {
        return remoteAddress;
    }
}
