/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2010 Red Hat, Inc. and/or its affiliates, and individual
 * contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
