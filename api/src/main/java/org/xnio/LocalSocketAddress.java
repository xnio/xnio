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

import java.net.SocketAddress;

/**
 * A socket address which is a local (UNIX domain) socket.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class LocalSocketAddress extends SocketAddress {

    private static final long serialVersionUID = -596342428809783686L;

    private final String name;

    /**
     * Construct a new instance.
     *
     * @param name the name of this socket address
     */
    public LocalSocketAddress(final String name) {
        this.name = name;
    }

    /**
     * Get the name (filesystem path) of this local socket address.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Get the string representation of this socket address (its name).
     *
     * @return the string representation
     */
    public String toString() {
        return getName();
    }
}
