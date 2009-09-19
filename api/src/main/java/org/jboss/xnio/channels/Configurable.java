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

import java.io.IOException;
import org.jboss.xnio.Option;

/**
 * A channel that has parameters that may be configured while the channel is open.
 *
 * @apiviz.exclude
 */
public interface Configurable {

    /**
     * Determine whether an option is supported on this channel.
     *
     * @param option the option
     * @return {@code true} if it is supported
     */
    boolean supportsOption(Option<?> option);

    /**
     * Get the value of a channel option.
     *
     * @param <T> the type of the option value
     * @param option the option to get
     * @return the value of the option, or {@code null} if it is not set
     * @throws IOException if an I/O error occurred when reading the option
     */
    <T> T getOption(Option<T> option) throws IOException;

    /**
     * Set an option for this channel.  Unsupported options are ignored.
     *
     * @param <T> the type of the option value
     * @param option the option to set
     * @param value the value of the option to set
     * @return this channel
     * @throws IllegalArgumentException if the value is not acceptable for this option
     * @throws IOException if an I/O error occured when modifying the option
     */
    <T> Configurable setOption(Option<T> option, T value) throws IllegalArgumentException, IOException;
}
