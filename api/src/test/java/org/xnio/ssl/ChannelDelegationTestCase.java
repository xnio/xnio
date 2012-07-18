/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2011 Red Hat, Inc. and/or its affiliates, and individual
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

package org.xnio.ssl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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
    public void getWorker() {
        assertSame(sslChannel.getWorker(), connectedChannelMock.getWorker());
    }
    
    @Test
    public void getSslSession() {
        assertNotNull(sslChannel.getSslSession());
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
