package org.jboss.xnio.spi;

import org.jboss.xnio.channels.StreamChannel;

/**
 * A two-way pipe connection between two handlers.  Both ends of the pipe are considered equal, so they are called
 * "left" and "right" here.
 */
public interface Pipe extends ExecutorUser, Lifecycle {
    /**
     * Get the left end of the pipe.
     *
     * @return the left end
     */
    PipeEnd<StreamChannel> getLeftEnd();

    /**
     * Get the right end of the pipe.
     *
     * @return the right end
     */
    PipeEnd<StreamChannel> getRightEnd();
}
