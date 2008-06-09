package org.jboss.xnio.spi;

import org.jboss.xnio.channels.StreamSourceChannel;
import org.jboss.xnio.channels.StreamSinkChannel;

/**
 *
 */
public interface OneWayPipe extends ExecutorUser, Lifecycle {
    PipeEnd<StreamSourceChannel> getSourceEnd();

    PipeEnd<StreamSinkChannel> getSinkEnd();
}