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

package org.jboss.xnio.spi;

import java.io.IOException;

/**
 * A bean with a lifecycle.  It is important to call the lifecycle methods in order to ensure proper startup and shutdown
 * of each bean.  Furthermore, appropriate care must be taken to ensure proper synchronization.  Invoking of these methods
 * is best left up to a container.
 * <p/>
 * Implementors shall use these methods to perform setup operations and start and stop each XNIO component implementation.
 * No synchronization is necessary for these methods as locking and visibility will be taken care of by the container.
 */
public interface Lifecycle {

    /**
     * Start hook.  Indicates that all dependent objects are started and may be accessed, and that this object should
     * also be started.
     *
     * @throws IOException if an error occurs
     */
    void start() throws IOException;

    /**
     * Stop hook.  Shut down the object instance.
     *
     * @throws IOException if an error occurs
     */
    void stop() throws IOException;
}