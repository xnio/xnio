package org.jboss.xnio.channels;

/**
 * A suspendable writable channel.  This type of channel is associated with a handler which can suspend and resume
 * writes as needed.
 */
public interface SuspendableWriteChannel extends ConfigurableChannel {
    /**
     * Suspend further writes on this channel.  The {@link org.jboss.xnio.IoHandler#handleWritable(java.nio.channels.Channel)} method will not
     * be called until writes are resumed.
     */
    void suspendWrites();

    /**
     * Resume writes on this channel.  The {@link org.jboss.xnio.IoHandler#handleWritable(java.nio.channels.Channel)} method will be
     * called as soon as there is space in the channel's transmit buffer.
     */
    void resumeWrites();
}
