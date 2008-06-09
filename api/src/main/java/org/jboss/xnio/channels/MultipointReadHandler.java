package org.jboss.xnio.channels;

/**
 * A user handler which is used to receive address information about an incoming multipoint message.
 *
 * @param <A> the type of address associated with this channel
 */
public interface MultipointReadHandler<A> {
    /**
     * Receive information about a multipoint read.
     *
     * @param sourceAddress the source (remote) address of the message, or {@code null} if unknown
     * @param destAddress the destination (local) address of the message, or {@code null} if unknown
     */
    void handle(A sourceAddress, A destAddress);
}
