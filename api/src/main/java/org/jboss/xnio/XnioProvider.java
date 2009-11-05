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
 * A descriptor for automatically-discovered XNIO provider type.  Since instances of this interface are
 * constructed automatically, implementing classes should have a no-arg constructor.
 * <p>
 * To add an automatically-discovered XNIO provider type, create a file called {@code "META-INF/services/org.jboss.xnio.XnioProvider"}
 * and populate it with the names of classes that implement this interface.
 *
 * @see java.util.ServiceLoader
 */
public interface XnioProvider {

    /**
     * Get the name of this provider's type.
     *
     * @return the name
     */
    String getName();

    /**
     * Get a new provider instance.
     *
     * @param configuration the configuration to use
     * @return the new provider instance
     */
    Xnio getNewInstance(XnioConfiguration configuration);
}
