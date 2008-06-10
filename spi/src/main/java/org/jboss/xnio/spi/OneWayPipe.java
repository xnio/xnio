package org.jboss.xnio.spi;

import org.jboss.xnio.channels.StreamSourceChannel;
import org.jboss.xnio.channels.StreamSinkChannel;

/**
 * A one-way pipe connection between a source handler and a sink handler.
 */
public interface OneWayPipe extends ExecutorUser, Lifecycle {
    /**
     * Get the source end of the pipe.
     *
     * @return the source end
     */
    PipeEnd<StreamSourceChannel> getSourceEnd();

    /**
     * Get the sink end of the pipe.
     *
     * @return the sink end
     */
    PipeEnd<StreamSinkChannel> getSinkEnd();
}