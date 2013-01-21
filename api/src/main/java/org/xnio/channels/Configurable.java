/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.xnio.channels;

import java.io.IOException;
import org.xnio.Option;

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
     * @return the previous option value, if any
     * @throws IllegalArgumentException if the value is not acceptable for this option
     * @throws IOException if an I/O error occurred when modifying the option
     */
    <T> T setOption(Option<T> option, T value) throws IllegalArgumentException, IOException;

    /**
     * An empty configurable instance.
     */
    Configurable EMPTY = new Configurable() {
        public boolean supportsOption(final Option<?> option) {
            return false;
        }

        public <T> T getOption(final Option<T> option) throws IOException {
            return null;
        }

        public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
            return null;
        }
    };
}
