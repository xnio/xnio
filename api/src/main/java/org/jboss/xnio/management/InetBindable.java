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

package org.jboss.xnio.management;

import java.net.SocketAddress;
import java.io.IOException;

/**
 * A managed object which may be bound and unbound to a socket address.
 *
 * @since 1.2
 */
public interface InetBindable {

    /**
     * Bind the entity to a socket address.
     *
     * @param address the address
     * @throws IOException if an error occurs
     */
    void bind(SocketAddress address) throws IOException;

    /**
     * Bind the entity to a host and port.
     *
     * @param hostName the host name
     * @param port the port number
     * @throws IOException if an error occurs
     */
    void bind(String hostName, int port) throws IOException;

    /**
     * Unbind the entity from a socket address.
     *
     * @param address the address
     * @throws IOException if an error occurs
     */
    void unbind(SocketAddress address) throws IOException;

    /**
     * Unbind the entity from a host and port.
     *
     * @param hostName the host name
     * @param port the port number
     * @throws IOException if an error occurs
     */
    void unbind(String hostName, int port) throws IOException;
}
