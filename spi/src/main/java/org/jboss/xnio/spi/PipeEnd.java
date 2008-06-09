package org.jboss.xnio.spi;

import org.jboss.xnio.IoHandler;
import java.nio.channels.Channel;

/**
 * One end of a pipe.
 *
 * @param <C> the pipe channel type
 */
public interface PipeEnd<C extends Channel> extends ExecutorUser {
    /**
     * Set the handler for this end of the pipe.
     *
     * @param handler the handler to use
     */
    void setHandler(IoHandler<? super C> handler); 
}
