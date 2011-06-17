/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, JBoss Inc., and individual contributors as indicated
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

/**
 * A resource which is pooled.
 *
 * @param <T> the pooled resource type
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface Pooled<T> {

    /**
     * Discard this resource.  Any backing resources corresponding to this pooled resource will be rendered unavailable
     * until the pooled resource has been garbage-collected.
     */
    void discard();

    /**
     * Free this resource for immediate re-use.  The resource must not be accessed again after
     * calling this method; if it is possible that an instance is still in use, you must call {@link #discard()} instead.
     */
    void free();

    /**
     * Get the pooled resource.
     *
     * @return the pooled resource
     * @throws IllegalStateException if the resource has been freed or discarded already
     */
    T getResource() throws IllegalStateException;
}
