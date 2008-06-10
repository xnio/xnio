package org.jboss.xnio.channels;

/**
 * A suspendable readable channel.  This type of channel is associated with a handler which can suspend and resume
 * reads as needed.
 */
public interface SuspendableReadChannel extends Configurable {
    /**
     * Suspend further reads on this channel.  The {@link org.jboss.xnio.IoHandler#handleReadable(java.nio.channels.Channel)} method will not
     * be called until reads are resumed.
     */
    void suspendReads();

    /**
     * Resume reads on this channel.  The {@link org.jboss.xnio.IoHandler#handleReadable(java.nio.channels.Channel)} method will be
     * called as soon as there is data available to be read.
     */
    void resumeReads();
}
