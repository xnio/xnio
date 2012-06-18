/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import org.junit.Test;
import org.xnio.LocalSocketAddress;

/**
 * Test for {@link SocketAddressBuffer}.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 *
 */
public class SocketAddressBufferTestCase {

    @Test
    public void test() {
        final SocketAddressBuffer buffer = new SocketAddressBuffer();
        final SocketAddress sourceAddress = new InetSocketAddress(10);
        final SocketAddress destinationAddress = new InetSocketAddress("farfarhost", 15);

        assertNull(buffer.getSourceAddress());
        assertNull(buffer.getSourceAddress(InetSocketAddress.class));
        assertNull(buffer.getSourceAddress(LocalSocketAddress.class));

        buffer.setSourceAddress(sourceAddress);

        assertSame(sourceAddress, buffer.getSourceAddress());
        assertSame(sourceAddress, buffer.getSourceAddress(InetSocketAddress.class));
        assertNull(buffer.getSourceAddress(LocalSocketAddress.class));

        assertNull(buffer.getDestinationAddress());
        assertNull(buffer.getDestinationAddress(InetSocketAddress.class));
        assertNull(buffer.getDestinationAddress(LocalSocketAddress.class));
        buffer.setDestinationAddress(destinationAddress);
        assertSame(destinationAddress, buffer.getDestinationAddress());
        assertSame(destinationAddress, buffer.getDestinationAddress(InetSocketAddress.class));
        assertNull(buffer.getDestinationAddress(LocalSocketAddress.class));

        buffer.clear();
        assertNull(buffer.getSourceAddress());
        assertNull(buffer.getSourceAddress(InetSocketAddress.class));
        assertNull(buffer.getSourceAddress(LocalSocketAddress.class));
        assertNull(buffer.getDestinationAddress());
        assertNull(buffer.getDestinationAddress(InetSocketAddress.class));
        assertNull(buffer.getDestinationAddress(LocalSocketAddress.class));
    }
}
