/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * An array set updater which updates an array-based set.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class ArraySetUpdater<C, E> {
    private final AtomicReferenceFieldUpdater<C, E[]> fieldUpdater;
    private final E[] empty;

    @SuppressWarnings("unchecked")
    ArraySetUpdater(final AtomicReferenceFieldUpdater<C, E[]> fieldUpdater, Class<E> elementType) {
        this.fieldUpdater = fieldUpdater;
        empty = (E[]) Array.newInstance(elementType, 0);
    }

    public static <C, E> ArraySetUpdater<C, E> create(AtomicReferenceFieldUpdater<C, E[]> fieldUpdater, Class<E> elementType) {
        return new ArraySetUpdater<C, E>(fieldUpdater, elementType);
    }

    private static <E> E[] grow(E[] orig, E newItem) {
        final int origLen = orig.length;
        E[] copy = Arrays.copyOf(orig, origLen + 1);
        copy[origLen] = newItem;
        return copy;
    }

    private E[] remove(E[] orig, int idx) {
        final int len = orig.length;
        if (len == 1) {
            return empty;
        } else if (idx == len - 1) {
            return Arrays.copyOf(orig, idx);
        }
        E[] copy = Arrays.copyOf(empty, idx);
        if (idx > 0) {
            System.arraycopy(orig, 0, copy, 0, idx);
        }
        if (idx < len - 1) {
            System.arraycopy(orig, idx + 1, copy, idx, len - 1 - idx);
        }
        return copy;
    }

    public boolean add(C instance, E newElement) {
        for (;;) {
            final E[] old = fieldUpdater.get(instance);
            for (E item : old) {
                if (item == newElement) return false;
            }
            if (fieldUpdater.compareAndSet(instance, old, grow(old, newElement))) return true;
        }
    }

    public boolean addAndCheckNull(final C instance, final E newElement) {
        for (;;) {
            final E[] old = fieldUpdater.get(instance);
            if (old == null) {
                return true;
            }
            for (E item : old) {
                if (item == newElement) return false;
            }
            if (fieldUpdater.compareAndSet(instance, old, grow(old, newElement))) return false;
        }
    }

    public boolean remove(C instance, E oldElement) {
        final E[] old = fieldUpdater.get(instance);
        if (old == null) {
            return false;
        }
        O: for (;;) {
            for (int idx = 0; idx < old.length; idx++) {
                if (old[idx] == oldElement) {
                    if (fieldUpdater.compareAndSet(instance, old, remove(old, idx))) {
                        return true;
                    } else {
                        continue O;
                    }
                }
            }
            return false;
        }
    }

    public E[] getAndSet(final C instance, final E[] newValue) {
        return fieldUpdater.getAndSet(instance, newValue);
    }
}
