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

package org.xnio.channels;

import java.net.SocketAddress;

/**
 * A buffer for source and destination addresses.
 * 
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class SocketAddressBuffer {
    private SocketAddress sourceAddress;
    private SocketAddress destinationAddress;

    /**
     * Construct a new instance.
     */
    public SocketAddressBuffer() {
    }

    /**
     * Get the source address.
     *
     * @return the source address, or {@code null} if not set
     */
    public SocketAddress getSourceAddress() {
        return sourceAddress;
    }

    /**
     * Get the source address.
     *
     * @param type the address type to get
     * @return the source address, or {@code null} if not set
     */
    public <A extends SocketAddress> A getSourceAddress(Class<A> type) {
        return type.isInstance(sourceAddress) ? type.cast(sourceAddress) : null;
    }

    /**
     * Set the source address.
     *
     * @param sourceAddress the source address, or {@code null} to clear
     */
    public void setSourceAddress(final SocketAddress sourceAddress) {
        this.sourceAddress = sourceAddress;
    }

    /**
     * Get the destination address.
     *
     * @return the destination address, or {@code null} if not set
     */
    public SocketAddress getDestinationAddress() {
        return destinationAddress;
    }

    /**
     * Get the destination address.
     *
     * @param type the address type to get
     * @return the destination address, or {@code null} if not set
     */
    public <A extends SocketAddress> A getDestinationAddress(Class<A> type) {
        return type.isInstance(destinationAddress) ? type.cast(destinationAddress) : null;
    }

    /**
     * Set the destination address.
     *
     * @param destinationAddress the destination address, or {@code null} to clear
     */
    public void setDestinationAddress(final SocketAddress destinationAddress) {
        this.destinationAddress = destinationAddress;
    }

    /**
     * Clear both addresses in the buffer.
     */
    public void clear() {
        sourceAddress = null;
        destinationAddress = null;
    }
}
