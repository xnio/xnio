/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

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
