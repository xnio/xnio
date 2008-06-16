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

package org.jboss.xnio;

import java.io.EOFException;
import java.io.IOException;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Streams utility methods.
 */
public final class Streams {
    private Streams() {
    }

    /**
     * Get an object sink that appends to a collection.
     *
     * @param target the collection to append to
     * @param <T> the target object type
     * @return an object sink
     */
    public static <T> ObjectSink<T> getCollectionObjectSink(Collection<T> target) {
        return new CollectionObjectSink<T>(target);
    }

    /**
     * Get an object source that reads from an iterator.
     *
     * @param iterator the iterator to read from
     * @param <T> the target object type
     * @return the object source
     */
    public static <T> ObjectSource<T> getIteratorObjectSource(Iterator<T> iterator) {
        return new IteratorObjectSource<T>(iterator);
    }

    /**
     * Get an object source that reads from an enumeration.
     *
     * @param enumeration the enumeration to read from
     * @param <T> the target object type
     * @return the object source
     */
    public static <T> ObjectSource<T> getEnumerationObjectSource(Enumeration<T> enumeration) {
        return new EnumerationObjectSource<T>(enumeration);
    }

    private static final class CollectionObjectSink<T> implements ObjectSink<T> {
        private final Collection<T> target;

        public CollectionObjectSink(final Collection<T> target) {
            this.target = target;
        }

        public void accept(final T instance) throws IOException {
            target.add(instance);
        }

        public void flush() throws IOException {
        }

        public void close() throws IOException {
        }
    }

    private static final class IteratorObjectSource<T> implements ObjectSource<T> {
        private final Iterator<T> src;

        public IteratorObjectSource(final Iterator<T> src) {
            this.src = src;
        }

        public boolean hasNext() throws IOException {
            return src.hasNext();
        }

        public T next() throws IOException {
            try {
                return src.next();
            } catch (NoSuchElementException ex) {
                EOFException eex = new EOFException("Iteration past end of iterator");
                eex.setStackTrace(ex.getStackTrace());
                throw eex;
            }
        }

        public void close() throws IOException {
            //empty
        }
    }

    private static final class EnumerationObjectSource<T> implements ObjectSource<T> {
        private final Enumeration<T> src;

        public EnumerationObjectSource(final Enumeration<T> src) {
            this.src = src;
        }

        public boolean hasNext() throws IOException {
            return src.hasMoreElements();
        }

        public T next() throws IOException {
            try {
                return src.nextElement();
            } catch (NoSuchElementException ex) {
                EOFException eex = new EOFException("Read past end of enumeration");
                eex.setStackTrace(ex.getStackTrace());
                throw eex;
            }
        }

        public void close() throws IOException {
            // empty
        }
    }
}
