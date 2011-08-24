/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, JBoss Inc., and individual contributors as indicated
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

package org.xnio;

import java.io.Serializable;

/**
 * An immutable property represented by a key and value.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public final class Property implements Serializable {

    private static final long serialVersionUID = -4958518978461712277L;

    private final String key;
    private final String value;
    private final int hashCode;

    private Property(final String key, final String value) {
        if (key == null)
            throw new IllegalArgumentException("key must not be null.");

        if (value == null)
            throw new IllegalArgumentException("value must not be null.");

        this.key = key;
        this.value = value;
        hashCode = key.hashCode() * 7 + value.hashCode();
    }

    /**
     * Get the key of this key/value Property.
     *
     * @return the key.
     */
    public String getKey() {
        return key;
    }

    /**
     * Get the value of this key/value Property.
     *
     * @return the value.
     */
    public String getValue() {
        return value;
    }

    /**
     * Get the hash code for this Property.
     *
     * @return the hash code.
     */
    @Override
    public int hashCode() {
        return hashCode;
    }

    /**
     * Determine if this Property equals another Property.
     *
     * @param obj the other Property to compare.
     * @return true if the two Properties are equal.
     */
    @Override
    public boolean equals(Object obj) {
        return obj instanceof Property && equals((Property) obj);
    }

    /**
     * Determine if this Property equals another Property.
     *
     * @param other the other Property to compare.
     * @return true if the two Properties are equal.
     */
    public boolean equals(Property other) {
        return key.equals(other.key) && value.equals(other.value);
    }

    /**
     * Create a new property for the specified key and value.
     *
     * @param key   the key for new Property
     * @param value the value for the new Property
     * @return the newly created Property
     */
    public static Property of(final String key, final String value) {
        return new Property(key, value);
    }

}
