/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2009 Red Hat, Inc. and/or its affiliates.
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
import java.util.Iterator;
import java.util.Arrays;
import java.util.Collection;
import java.util.AbstractList;
import java.util.List;
import java.util.RandomAccess;

import static org.xnio._private.Messages.msg;

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
        for (int i = 0, length = realValues.length; i < length; i++) {
            if (realValues[i] == null) {
                throw msg.nullArrayIndex("option", i);
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
        return values.length == 0;
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
