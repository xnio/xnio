package org.jboss.xnio.spi;

import java.util.concurrent.Executor;

/**
 * A configurable object that uses an executor.  In most cases, failing to specify an executor will cause a default
 * executor to be used instead.
 */
public interface ExecutorUser {
    /**
     * Set the executor to use.
     *
     * @param executor the executor
     */
    void setExecutor(Executor executor);
}
