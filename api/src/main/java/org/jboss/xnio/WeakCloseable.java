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
import java.lang.ref.WeakReference;

/**
 * A {@code Closeable} that holds a weak reference to another {@code Closeable}, which can be used to close the other
 * without creating a strong reference to it.
 *
 * @apiviz.exclude
 */
public final class WeakCloseable implements Closeable {
    private final WeakReference<Closeable> resource;

    /**
     * Construct a new instance.
     *
     * @param resource the target resource
     */
    public WeakCloseable(final WeakReference<Closeable> resource) {
        this.resource = resource;
    }

    /** {@inheritDoc} In addition, if the resource has already been garbage collected, this operation has no effect. */
    public void close() throws IOException {
        final Closeable closeable = resource.get();
        if (closeable != null) {
            closeable.close();
        }
    }
}
