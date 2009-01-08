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

import java.io.Serializable;

/**
 * An option that may be applied to a channel.
 *
 * @apiviz.exclude
 */
public interface ChannelOption<T> extends Serializable {
    /**
     * Get the name of this option.
     *
     * @return the name
     */
    String getName();

    /**
     * Get the concrete type of the value for this option.
     *
     * @return the type
     */
    Class<T> getType();

    /**
     * Determine whether this channel option is equal to another.  If the argument is not a {@code ChannelOption}, {@code false}
     * will be returned.  Two options are equal when they have the same name and are applicable to the same type.
     *
     * @param other the other channel option
     * @return {@code true} if they are equal
     */
    boolean equals(Object other);

    /**
     * The hash code for this option.  It should always be equal to {@code 31 * name.hashCode() + type.hashCode()}.
     *
     * @return the hash code
     */
    int hashCode();

    /**
     * Return a human-readable string form of this option.
     *
     * @return the name of the option
     */
    String toString();

    /**
     * Get the value of the given string as the value type of this channel option, if possible.
     *
     * @param string the string representation of the value
     * @return the value
     * @throws IllegalArgumentException if the value is not valid for this option
     */
    T valueOf(String string) throws IllegalArgumentException;
}
