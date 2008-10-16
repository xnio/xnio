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

/**
 * An immutable pair of addresses that define the two endpoints of a connection.
 *
 * @param <A> the address type
 * @apiviz.exclude
 */
public final class ConnectionAddress<A> {
    private final A localAddress;
    private final A remoteAddress;

    /**
     * Construct a new instance.
     *
     * @param localAddress the local address
     * @param remoteAddress the remote address
     */
    public ConnectionAddress(final A localAddress, final A remoteAddress) {
        if (localAddress == null) {
            throw new NullPointerException("localAddress is null");
        }
        if (remoteAddress == null) {
            throw new NullPointerException("remoteAddress is null");
        }
        this.localAddress = localAddress;
        this.remoteAddress = remoteAddress;
    }

    /**
     * Get the local address.
     *
     * @return the local address
     */
    public final A getLocalAddress() {
        return localAddress;
    }

    /**
     * Get the remote address.
     *
     * @return the remote address
     */
    public final A getRemoteAddress() {
        return remoteAddress;
    }
}
