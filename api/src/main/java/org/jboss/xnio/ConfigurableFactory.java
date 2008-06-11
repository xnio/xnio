package org.jboss.xnio;

import org.jboss.xnio.channels.Configurable;

/**
 * A factory which produces an instance based on a configuration.  Once the {@code create} method is called, the instance
 * may no longer be reconfigured.
 *
 * @param <T> the subject type
 */
public interface ConfigurableFactory<T> extends Configurable {
    /**
     * Create the instance based on the configuration.
     *
     * @return the instance
     */
    T create();
}
