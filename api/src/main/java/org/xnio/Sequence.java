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

package org.xnio;

import java.io.Serializable;
import java.util.Iterator;
import java.util.Arrays;
import java.util.Collection;
import java.util.AbstractList;
import java.util.List;
import java.util.RandomAccess;

/**
 * An immutable sequence of elements.  Though this class implements {@link java.util.List}, it is in fact
 * immutable.
 *
 * @param <T> the element type
 */
public final class Sequence<T> extends AbstractList<T> implements List<T>, RandomAccess, Serializable {

    private static final long serialVersionUID = 3042164316147742903L;

    private final Object[] values;

    private static final Object[] empty = new Object[0];

    private Sequence(final Object[] values) {
        final Object[] realValues = values.clone();
        this.values = realValues;
        for (Object realValue : realValues) {
            if (realValue == null) {
                throw new NullPointerException("value member is null");
            }
        }
    }

    private static final Sequence<?> EMPTY = new Sequence(empty);

    /**
     * Return a sequence of the given members.
     *
     * @param members the members
     * @param <T> the element type
     * @return a sequence
     */
    public static <T> Sequence<T> of(T... members) {
        if (members.length == 0) {
            return empty();
        } else {
            return new Sequence<T>(members);
        }
    }

    /**
     * Return a sequence of the given members.
     *
     * @param members the members
     * @param <T> the element type
     * @return a sequence
     */
    public static <T> Sequence<T> of(Collection<T> members) {
        if (members instanceof Sequence) {
            return (Sequence<T>) members;
        }
        final Object[] objects = members.toArray();
        if (objects.length == 0) {
            return empty();
        }
        return new Sequence<T>(objects);
    }

    /**
     * Cast a sequence to a different type <b>if</b> all the contained elements are of the subtype.
     *
     * @param newType the class to cast to
     * @param <N> the new type
     * @return the typecast sequence
     * @throws ClassCastException if any elements could not be cast
     */
    @SuppressWarnings("unchecked")
    public <N> Sequence<N> cast(Class<N> newType) throws ClassCastException {
        for (Object value : values) {
            newType.cast(value);
        }
        return (Sequence<N>) this;
    }

    /**
     * Return an empty sequence.
     *
     * @param <T> the element type
     * @return the empty sequence
     */
    @SuppressWarnings("unchecked")
    public static <T> Sequence<T> empty() {
        return (Sequence<T>) EMPTY;
    }

    /**
     * Get an iterator over the elements of this sequence.
     *
     * @return an iterator over the elements of this sequence
     */
    @SuppressWarnings("unchecked")
    public Iterator<T> iterator() {
        return Arrays.<T>asList((T[]) values).iterator();
    }

    /**
     * Return the number of elements in this sequence.
     *
     * @return the number of elements
     */
    public int size() {
        return values.length;
    }

    /**
     * Determine whether this sequence is empty.
     *
     * @return {@code true} if the sequence has no elements
     */
    public boolean isEmpty() {
        return values.length != 0;
    }

    /**
     * Get a copy of the values array.
     *
     * @return a copy of the values array
     */
    public Object[] toArray() {
        return values.clone();
    }

    /**
     * Get the value at a certain index.
     *
     * @param index the index
     * @return the value
     */
    @SuppressWarnings("unchecked")
    public T get(final int index) {
        return (T) values[index];
    }

    /**
     * Determine whether this sequence is equal to another.
     *
     * @param other the other sequence
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(Object other) {
        return other instanceof Sequence && equals((Sequence<?>)other);
    }

    /**
     * Determine whether this sequence is equal to another.
     *
     * @param other the other sequence
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(Sequence<?> other) {
        return this == other || other != null && Arrays.equals(values, other.values);
    }

    /**
     * Get the hash code for this sequence.
     *
     * @return the hash code
     */
    public int hashCode() {
        return Arrays.hashCode(values);
    }
}
