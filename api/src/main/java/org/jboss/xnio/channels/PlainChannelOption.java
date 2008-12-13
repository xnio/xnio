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

/**
 * A plain channel option implementation.  Use to easily define channel options.
 */
public class PlainChannelOption<T> implements ChannelOption<T> {
    private static final long serialVersionUID = -8427767721179764175L;

    private final String name;
    private final Class<T> type;

    /**
     * Basic constructor.
     *
     * @param name the option name; must not be {@code null}
     * @param type the option type; must not be {@code null}
     */
    public PlainChannelOption(final String name, final Class<T> type) {
        if (name == null) {
            throw new NullPointerException("name is null");
        }
        if (type == null) {
            throw new NullPointerException("type is null");
        }
        this.name = name;
        this.type = type;
    }

    /**
     * Convenience factory method.  Create a channel option.  Infers type from context.
     *
     * @param name the option name
     * @param type the value type
     * @return the new option
     */
    public static <T> ChannelOption<T> createOption(String name, Class<T> type) {
        return new PlainChannelOption<T>(name, type);
    }

    /**
     * Check equality with another object.  Two options are equal if the name and the type are both equal.
     *
     * @param o the other object
     * @return {@code true} if the the other object is equal to this one
     */
    public boolean equals(final Object o) {
        if (o instanceof ChannelOption) {
            ChannelOption other = (ChannelOption) o;
            return this == other || name.equals(other.getName()) && type.equals(other.getType());
        } else {
            return false;
        }
    }

    /**
     * Calculate the hashCode.  See {@link ChannelOption#hashCode()}.
     *
     * @return the hash code
     */
    public int hashCode() {
        return 31 * name.hashCode() + type.hashCode();
    }

    /**
     * Get the option name.
     *
     * @return the option name
     */
    public String getName() {
        return name;
    }

    /**
     * Get option type.
     *
     * @return the option type
     */
    public Class<T> getType() {
        return type;
    }

    /**
     * Get a simple string representation of this option.
     *
     * @return a representation of this option
     */
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("Option \"").append(name).append("\", type ").append(type.getName());
        builder.append(" (").append(super.toString()).append(')');
        return builder.toString();
    }

    /** {@inheritDoc} */
    public T valueOf(final String string) throws IllegalArgumentException {
        final Class<T> type = this.type;
        if (type == Boolean.class) {
            return type.cast(Boolean.valueOf(string));
        } else if (type == Integer.class) {
            return type.cast(Integer.valueOf(string));
        } else if (type == String.class) {
            return type.cast(string);
        } else {
            throw new IllegalArgumentException("Unknown type " + type.getName());
        }
    }
}
