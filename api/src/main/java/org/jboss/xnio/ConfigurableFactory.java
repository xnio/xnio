package org.jboss.xnio;

import org.jboss.xnio.channels.Configurable;
import java.io.IOException;

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
     * @throws java.io.IOException if an error occurs starting the instance
     */
    T create() throws IOException;

    /**
     * {@inheritDoc}
     */
    ConfigurableFactory<T> setOption(final String name, final Object value) throws IllegalArgumentException, IOException;
}
