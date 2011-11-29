/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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
package org.xnio.ssl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import org.junit.Test;
import org.xnio.FileAccess;
import org.xnio.LocalSocketAddress;
import org.xnio.OptionMap;
import org.xnio.Options;

/**
 * Asserts that the SSL channel delegates some operations such as getLocalAddress to the underlying connected channel.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
public class ChannelDelegationTestCase extends AbstractJsseConnectedSslStreamChannelTest {

    @Test
    public void getLocalAddress() {
        SocketAddress address = new LocalSocketAddress("here");
        connectedChannelMock.setLocalAddress(address);
        assertSame(address, sslChannel.getLocalAddress());
        address = new InetSocketAddress(100);
        connectedChannelMock.setLocalAddress(address);
        assertSame(address, connectedChannelMock.getLocalAddress());
    }

    @Test
    public void getTypedLocalAddress() {
        SocketAddress address = new LocalSocketAddress("here");
        connectedChannelMock.setLocalAddress(address);
        assertSame(address, sslChannel.getLocalAddress(LocalSocketAddress.class));
        assertSame(address, sslChannel.getLocalAddress(SocketAddress.class));
        assertNull(sslChannel.getLocalAddress(InetSocketAddress.class));
        address = new InetSocketAddress(1009);
        connectedChannelMock.setLocalAddress(address);
        assertSame(address, sslChannel.getLocalAddress(InetSocketAddress.class));
        assertSame(address, sslChannel.getLocalAddress(SocketAddress.class));
        assertNull(sslChannel.getLocalAddress(LocalSocketAddress.class));
    }

    @Test
    public void getPeerAddress() {
        SocketAddress address = new LocalSocketAddress("there");
        connectedChannelMock.setPeerAddress(address);
        assertSame(address, sslChannel.getPeerAddress());
        address = new InetSocketAddress(10);
        connectedChannelMock.setPeerAddress(address);
        assertSame(address, connectedChannelMock.getPeerAddress());
    }

    @Test
    public void getTypedPeerAddress() {
        SocketAddress address = new LocalSocketAddress("there");
        connectedChannelMock.setPeerAddress(address);
        assertSame(address, sslChannel.getPeerAddress(LocalSocketAddress.class));
        assertSame(address, sslChannel.getPeerAddress(SocketAddress.class));
        assertNull(sslChannel.getPeerAddress(InetSocketAddress.class));
        address = new InetSocketAddress(1009);
        connectedChannelMock.setPeerAddress(address);
        assertSame(address, sslChannel.getPeerAddress(InetSocketAddress.class));
        assertSame(address, sslChannel.getPeerAddress(SocketAddress.class));
        assertNull(sslChannel.getPeerAddress(LocalSocketAddress.class));
    }

    @Test
    public void getOption() throws IOException {
        connectedChannelMock.setOptionMap(OptionMap.create(Options.SSL_ENABLED, Boolean.TRUE, Options.MAX_INBOUND_MESSAGE_SIZE, Integer.valueOf(1000)));
        assertSame(Boolean.TRUE, sslChannel.getOption(Options.SSL_ENABLED));
        assertEquals(1000, sslChannel.getOption(Options.MAX_INBOUND_MESSAGE_SIZE).intValue());
        assertNull(sslChannel.getOption(Options.READ_TIMEOUT));

        connectedChannelMock.setOptionMap(OptionMap.create(Options.ALLOW_BLOCKING, Boolean.TRUE));
        assertSame(Boolean.TRUE, sslChannel.getOption(Options.ALLOW_BLOCKING));
        assertNull(sslChannel.getOption(Options.SSL_ENABLED));
    }

    @Test
    public void supportsOption() throws IOException {
        connectedChannelMock.setOptionMap(OptionMap.create(Options.FILE_ACCESS, FileAccess.READ_ONLY, Options.CLOSE_ABORT, Boolean.FALSE));
        assertTrue(sslChannel.supportsOption(Options.FILE_ACCESS));
        assertTrue(sslChannel.supportsOption(Options.CLOSE_ABORT));
        assertFalse(sslChannel.supportsOption(Options.SSL_ENABLED));

        connectedChannelMock.setOptionMap(OptionMap.create(Options.BROADCAST, Boolean.TRUE));
        assertTrue(sslChannel.supportsOption(Options.BROADCAST));
        assertFalse(sslChannel.supportsOption(Options.IP_TRAFFIC_CLASS));

        assertTrue(sslChannel.supportsOption(Options.SECURE));
    }
}
