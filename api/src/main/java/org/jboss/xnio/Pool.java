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

/**
 * A generic pooled resource manager.
 *
 * @param <T> the resource type
 *
 * @apiviz.landmark
 */
public interface Pool<T> {

    /**
     * Allocate a resource from the pool, or {@code null} if none are available.
     *
     * @return the resource, or {@code null} if none are available
     */
    T allocate();

    /**
     * Free a pooled resource.
     *
     * @param resource the resource to re-pool
     * @throws IllegalArgumentException if the given resource does not belong in this pool
     */
    void free(T resource) throws IllegalArgumentException;

    /**
     * Discard a "damaged" resource.  Calling this method indicates that the object in question will not be used
     * any longer, however it should not be returned to the pool.
     *
     * @param resource the resource to discard
     */
    void discard(T resource);
}
