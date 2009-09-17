/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, JBoss Inc., and individual contributors as indicated
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

package org.jboss.xnio;

import java.util.AbstractSet;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Collections;
import java.util.Collection;
import java.io.Serializable;

/**
 * An immutable set of some enumeration type.  Used to build immutable sets of flags for flag options.
 *
 * @param <E> the element type
 */
public final class FlagSet<E extends Enum<E>> extends AbstractSet<E> implements Serializable {

    private final Class<E> type;
    private final EnumSet<E> values;
    private static final long serialVersionUID = 4155828678034140336L;

    private FlagSet(final Class<E> type, final EnumSet<E> values) {
        this.type = type;
        this.values = values;
    }

    /**
     * Create a flag set that is a copy of a given collection.
     *
     * @param elementType the element type
     * @param original the original flag collection
     * @param <E> the element type
     * @return the flag set
     */
    public static <E extends Enum<E>> FlagSet<E> copyOf(Class<E> elementType, Collection<E> original) {
        return new FlagSet<E>(elementType, EnumSet.copyOf(original));
    }

    /**
     * Create an empty flag set of a given type.
     *
     * @param elementType the element type
     * @param <E> the element type
     * @return the flag set
     */
    public static <E extends Enum<E>> FlagSet<E> noneOf(Class<E> elementType) {
        return new FlagSet<E>(elementType, EnumSet.noneOf(elementType));
    }

    /**
     * Create a full flag set of a given type.
     *
     * @param elementType the element type
     * @param <E> the element type
     * @return the flag set
     */
    public static <E extends Enum<E>> FlagSet<E> allOf(Class<E> elementType) {
        return new FlagSet<E>(elementType, EnumSet.allOf(elementType));
    }

    /**
     * Create a flag set of the given elements.
     *
     * @param elements the elements
     * @param <E> the element type
     * @return the flag set
     */
    @SuppressWarnings({ "unchecked" })
    public static <E extends Enum<E>> FlagSet<E> of(E... elements) {
        if (elements.length == 0) {
            throw new IllegalArgumentException("Empty elements array");
        }
        Class elementType = elements[0].getClass();
        while (elementType.getSuperclass() != Enum.class) elementType = elementType.getSuperclass();
        return new FlagSet<E>((Class<E>)elementType, EnumSet.<E>of(elements[0], elements));
    }

    /**
     * Get the element type for this flag set.
     *
     * @return the element type
     */
    public Class<E> getElementType() {
        return type;
    }

    /**
     * Cast this flag set to a flag set of the given element type.
     *
     * @param type the element type
     * @param <N> the element type
     * @return this flag set
     * @throws ClassCastException if the elements of this flag set are not of the given type
     */
    @SuppressWarnings({ "unchecked" })
    public <N extends Enum<N>> FlagSet<N> cast(Class<N> type) throws ClassCastException {
        this.type.asSubclass(type);
        return (FlagSet<N>) this;
    }

    /**
     * Determine if this flag set contains the given value.
     *
     * @param o the value
     * @return {@code true} if the value is within this set
     */
    public boolean contains(final Object o) {
        return values.contains(o);
    }

    /**
     * Get an iterator over this flag set.
     *
     * @return an iterator
     */
    public Iterator<E> iterator() {
        return Collections.unmodifiableSet(values).iterator();
    }

    /**
     * Get the number of elements in this flag set.
     *
     * @return the number of elements
     */
    public int size() {
        return values.size();
    }
}
