package org.jboss.xnio.channels;

/**
 * The result of a multipoint message read.
 *
 * @param <A> the type of address associated with this read result
 */
public interface MultipointReadResult<A> {
    /**
     * Get the source address of the message, if available.
     *
     * @return the source address, or {@code null} if it is unknown
     */
    A getSourceAddress();

    /**
     * Get the destination address of the message, if available.
     *
     * @return the destination address, or {@code null} if it is unknown
     */
    A getDestinationAddress();
}
