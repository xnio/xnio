/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2012 Red Hat, Inc. and/or its affiliates, and individual
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
