/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2011 Red Hat, Inc. and/or its affiliates, and individual
 * contributors as indicated by the @author tags.
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

package org.xnio;

import java.io.Serializable;

import static org.xnio._private.Messages.msg;

/**
 * An immutable property represented by a key and value.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public final class Property implements Serializable {

    private static final long serialVersionUID = -4958518978461712277L;

    private final String key;
    private final Object value;

    private Property(final String key, final Object value) {
        if (key == null)
            throw msg.nullParameter("key");

        if (value == null)
            throw msg.nullParameter("value");

        this.key = key;
        this.value = value;
    }

    private Property(final String key, final String value) {
        this(key, (Object) value);
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
    public Object getValue() {
        return value;
    }

    /**
     * Get the value of this key/value Property.
     *
     * @return the value.
     */
    public String getValue$$bridger() {
        // If the value is not a String we want a ClassCastException to indicate the error.
       return (String) value;
    }

    /**
     * Get the {@link String} representation of this property.
     *
     * @return the {@link String} representation of this property.
     */
    @Override
    public String toString() {
        return "(" + key + "=>" + value.toString() + ")";
    }

    /**
     * Get the hash code for this Property.
     *
     * @return the hash code.
     */
    @Override
    public int hashCode() {
        return key.hashCode() * 7 + value.hashCode();
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
    public static Property of(final String key, final Object value) {
        return new Property(key, value);
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
