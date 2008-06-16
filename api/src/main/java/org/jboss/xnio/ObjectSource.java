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

import java.io.Closeable;
import java.io.IOException;

/**
 * A streaming source for objects.
 *
 * @param <T> the type of object that this {@code ObjectSource} produces
 */
public interface ObjectSource<T> extends Closeable {

    /**
     * Indicate whether there are more objects to retrieve.  If this method returns {@code true}, an object is
     * guaranteed to be available.  If this method returns {@code false}, there is no object available; however, an
     * object may become available at a later time, depending on the implementation.
     * <p/>
     * If this method returns {@code true}, it will continue to return {@code true} on every subsequent invocation until
     * the next object is pulled using the {@code next()} method, or until the object source is closed.
     *
     * @return {@code true} if there are more objects in this stream
     */
    boolean hasNext() throws IOException;

    /**
     * Get the next object in the stream.
     *
     * @return the next object
     *
     * @throws IOException if the stream can no longer be read
     */
    T next() throws IOException;
}
