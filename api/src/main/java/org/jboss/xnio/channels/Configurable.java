package org.jboss.xnio.channels;

import java.util.Map;
import java.io.IOException;

/**
 * A channel that has parameters that may be configured while the channel is open.
 */
public interface Configurable {
    /**
     * Get the value of a channel option.
     *
     * @param name the name of the option
     * @return the value of the option
     * @throws UnsupportedOptionException if the option is not supported by this channel
     * @throws IOException if an I/O error occurred when reading the option
     */
    Object getOption(String name) throws UnsupportedOptionException, IOException;

    /**
     * Get the options that may be set on this channel.
     *
     * @return an unmodifiable map of options
     */
    Map<String, Class<?>> getOptions();

    /**
     * Set an option for this channel.
     *
     * @param name the name of the option to set
     * @param value the value of the option to set
     * @return this channel
     * @throws UnsupportedOptionException if the option is not supported by this channel
     * @throws IllegalArgumentException if the value is not acceptable for this option
     * @throws IOException if an I/O error occured when modifying the option
     */
    Configurable setOption(String name, Object value) throws IllegalArgumentException, IOException;
}
