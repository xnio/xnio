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

package org.jboss.xnio.nio;

import org.jboss.xnio.channels.ChannelOption;
import org.jboss.xnio.channels.UnsupportedOptionException;
import org.jboss.xnio.channels.Configurable;
import java.io.IOException;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

/**
 *
 */
public abstract class AbstractConfigurable implements Configurable {

    private final Map<ChannelOption<?>, Object> optionMap = new HashMap<ChannelOption<?>, Object>();
    private final Set<ChannelOption<?>> validOptions;

    protected AbstractConfigurable(final Set<ChannelOption<?>> validOptions) {
        this.validOptions = validOptions;
    }

    public Set<ChannelOption<?>> getOptions() {
        return optionMap.keySet();
    }

    public <T> T getOption(final ChannelOption<T> option) throws UnsupportedOptionException, IOException {
        if (validOptions.contains(option)) {
            return option.getType().cast(optionMap.get(option));
        } else {
            throw badOption(option);
        }
    }

    public <T> AbstractConfigurable setOption(final ChannelOption<T> option, final T value) throws IllegalArgumentException, IOException {
        if (validOptions.contains(option)) {
            optionMap.put(option, option.getType().cast(value));
        } else {
            throw badOption(option);
        }
        return this;
    }

    private static UnsupportedOptionException badOption(final ChannelOption<?> option) {
        return new UnsupportedOptionException("Option " + option + " is unsupported");
    }
}
