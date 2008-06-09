package org.jboss.xnio.spi;

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
     * Post-create hook.  Indicates that dependencies are injected, but may not yet be used.
     *
     * @throws Exception if an error occurs
     */
    void create() throws Exception;

    /**
     * Start hook.  Indicates that all dependent objects are started and may be accessed, and that this object should
     * also be started.
     *
     * @throws Exception if an error occurs
     */
    void start() throws Exception;

    /**
     * Stop hook.  Shut down the object instance.
     *
     * @throws Exception if an error occurs
     */
    void stop() throws Exception;

    /**
     * Destroy hook.  Clean up any resources.
     *
     * @throws Exception if an error occurs
     */
    void destroy() throws Exception;
}