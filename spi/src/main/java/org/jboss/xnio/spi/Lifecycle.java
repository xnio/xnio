package org.jboss.xnio.spi;

import java.io.IOException;

/**
 * A bean with a lifecycle.  It is important to call the lifecycle methods in order to ensure proper startup and shutdown
 * of each bean.  Furthermore, appropriate care must be taken to ensure proper synchronization.  Invoking of these methods
 * is best left up to a container.
 * <p/>
 * Implementors shall use these methods to perform setup operations and start and stop each XNIO component implementation.
 * No synchronization is necessary for these methods as locking and visibility will be taken care of by the container.
 */
public interface Lifecycle {

    /**
     * Start hook.  Indicates that all dependent objects are started and may be accessed, and that this object should
     * also be started.
     *
     * @throws IOException if an error occurs
     */
    void start() throws IOException;

    /**
     * Stop hook.  Shut down the object instance.
     *
     * @throws IOException if an error occurs
     */
    void stop() throws IOException;
}