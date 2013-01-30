/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
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
package org.xnio.nio.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.xnio.ChannelListener;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.LocalSocketAddress;
import org.xnio.Option;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.StreamConnection;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 * Tests a pair of connected TCP connections (client/server).
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 */
public class TcpConnectionTestCase extends AbstractStreamSinkSourceChannelTest<StreamSinkChannel, StreamSourceChannel> {

    protected SocketAddress bindAddress;
    protected StreamConnection connection = null;
    protected StreamConnection serverConnection = null;
    protected AcceptingChannel<? extends StreamConnection> server;

    @Before
    public void createServer() throws IOException {
        bindAddress = new InetSocketAddress(Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 }), 12345);
        final ChannelListener<AcceptingChannel<StreamConnection>> acceptingChannelListener = new TestChannelListener<AcceptingChannel<StreamConnection>>();;
        server = worker.createStreamConnectionServer(
                bindAddress, acceptingChannelListener, OptionMap.EMPTY);
        assertNotNull(server);
    }

    @After
    public void closeServer() throws IOException {
        server.close();
    }

    @Override
    protected synchronized void initChannels(XnioWorker xnioWorker, OptionMap optionMap, TestChannelListener<StreamSinkChannel> channelListener,
            TestChannelListener<StreamSourceChannel> serverChannelListener) throws IOException { 

        if (connection != null) {
            connection.close();
            serverConnection.close();
        }
        final IoFuture<StreamConnection> openedConnection = xnioWorker.openStreamConnection(bindAddress, null, optionMap);
        final FutureResult<StreamConnection> accepted = new FutureResult<StreamConnection>(xnioWorker);
        server.getIoThread().execute(new Runnable() {
            public void run() {
                try {
                    accepted.setResult(server.accept());
                } catch (IOException e) {
                    accepted.setException(e);
                }
            }
        });
        serverConnection = accepted.getIoFuture().get();
        connection = openedConnection.get();
        assertNotNull(serverConnection);
        assertNotNull(connection);
        channelListener.handleEvent(connection.getSinkChannel());
        serverChannelListener.handleEvent(serverConnection.getSourceChannel());
    }

    @Test
    public void optionSetup() throws IOException {
        initChannels();
        final Option<?>[] unsupportedOptions = OptionHelper.getNotSupportedOptions(Options.CLOSE_ABORT,
                Options.IP_TRAFFIC_CLASS, Options.KEEP_ALIVE, Options.READ_TIMEOUT, Options.RECEIVE_BUFFER,
                Options.RECEIVE_BUFFER, Options.SEND_BUFFER, Options.TCP_NODELAY, Options.TCP_OOB_INLINE,
                Options.WRITE_TIMEOUT);
        for (Option<?> option: unsupportedOptions) {
            assertFalse("Channel supports " + option, connection.supportsOption(option));
            assertNull("Expected null value for option " + option + " but got " + connection.getOption(option) + " instead",
                    connection.getOption(option));
        }

        assertTrue(connection.supportsOption(Options.CLOSE_ABORT));
        assertFalse(connection.getOption(Options.CLOSE_ABORT));
        assertTrue(connection.supportsOption(Options.IP_TRAFFIC_CLASS));
        assertEquals(0, (int) connection.getOption(Options.IP_TRAFFIC_CLASS));
        assertTrue(connection.supportsOption(Options.KEEP_ALIVE));
        assertFalse(connection.getOption(Options.KEEP_ALIVE));
        assertTrue(connection.supportsOption(Options.READ_TIMEOUT));
        assertEquals(0, (int) connection.getOption(Options.READ_TIMEOUT));
        assertTrue(connection.supportsOption(Options.RECEIVE_BUFFER));
        assertTrue(connection.getOption(Options.RECEIVE_BUFFER) > 0);
        assertTrue(connection.supportsOption(Options.SEND_BUFFER));
        assertTrue(connection.getOption(Options.SEND_BUFFER) > 0);
        assertTrue(connection.supportsOption(Options.TCP_NODELAY));
        assertNotNull(connection.getOption(Options.TCP_NODELAY));
        assertTrue(connection.supportsOption(Options.TCP_OOB_INLINE));
        assertFalse(connection.getOption(Options.TCP_OOB_INLINE));
        assertTrue(connection.supportsOption(Options.WRITE_TIMEOUT));
        assertEquals(0, (int) connection.getOption(Options.WRITE_TIMEOUT));

        connection.setOption(Options.CLOSE_ABORT, true);
        connection.setOption(Options.IP_TRAFFIC_CLASS, 5);
        connection.setOption(Options.KEEP_ALIVE, true);
        connection.setOption(Options.READ_TIMEOUT, 234095747);
        connection.setOption(Options.RECEIVE_BUFFER, 5000);
        connection.setOption(Options.SEND_BUFFER, 3000);
        connection.setOption(Options.TCP_NODELAY, true);
        connection.setOption(Options.TCP_OOB_INLINE, true);
        connection.setOption(Options.WRITE_TIMEOUT, 1301093);
        assertNull("Unexpected option value: " + connection.getOption(Options.MAX_INBOUND_MESSAGE_SIZE), connection.setOption(Options.MAX_INBOUND_MESSAGE_SIZE, 50000));// unsupported

        assertTrue(connection.getOption(Options.CLOSE_ABORT));
        assertTrue(connection.getOption(Options.IP_TRAFFIC_CLASS) >= 0);// it is okay that 5 is not returned
        // 5 value will only be set if the channels' family equals StandardProtocolFamily.INET
        assertTrue(connection.getOption(Options.KEEP_ALIVE));
        assertEquals(234095747, (int) connection.getOption(Options.READ_TIMEOUT));
        assertTrue(connection.getOption(Options.RECEIVE_BUFFER) > 0);
        assertTrue(connection.getOption(Options.SEND_BUFFER) >= 3000);
        assertTrue(connection.getOption(Options.TCP_NODELAY));
        assertTrue(connection.getOption(Options.TCP_OOB_INLINE));
        assertEquals(1301093, (int) connection.getOption(Options.WRITE_TIMEOUT));
        assertTrue(connection.getOption(Options.CLOSE_ABORT));
        assertNull(connection.getOption(Options.MAX_INBOUND_MESSAGE_SIZE));// unsupported

        assertTrue(connection.setOption(Options.CLOSE_ABORT, false));
        assertTrue(connection.setOption(Options.IP_TRAFFIC_CLASS, 30) >= 0);
        assertTrue(connection.setOption(Options.KEEP_ALIVE, false));
        assertEquals(234095747, (int) connection.setOption(Options.READ_TIMEOUT, 1290455));
        assertTrue(connection.setOption(Options.RECEIVE_BUFFER, 3000) >= 5000);
        assertTrue(connection.setOption(Options.SEND_BUFFER, 5000) >= 3000);
        assertTrue(connection.setOption(Options.TCP_NODELAY, false));
        assertTrue(connection.setOption(Options.TCP_OOB_INLINE, false));
        assertEquals(1301093, (int) connection.setOption(Options.WRITE_TIMEOUT, 293265));

        assertFalse(connection.getOption(Options.CLOSE_ABORT));
        assertTrue(connection.getOption(Options.IP_TRAFFIC_CLASS) >= 0);
        assertFalse(connection.getOption(Options.KEEP_ALIVE));
        assertEquals(1290455, (int) connection.getOption(Options.READ_TIMEOUT));
        assertTrue(connection.getOption(Options.RECEIVE_BUFFER) > 0);
        assertEquals(5000, (int) connection.getOption(Options.SEND_BUFFER));
        assertFalse(connection.getOption(Options.TCP_NODELAY));
        assertFalse(connection.getOption(Options.TCP_OOB_INLINE));
        assertEquals(293265, (int) connection.getOption(Options.WRITE_TIMEOUT));

        assertFalse(connection.setOption(Options.CLOSE_ABORT, null));
        assertFalse(connection.setOption(Options.KEEP_ALIVE, null));
        assertEquals(1290455, (int) connection.setOption(Options.READ_TIMEOUT, null));
        assertFalse(connection.setOption(Options.TCP_NODELAY, null));
        assertFalse(connection.setOption(Options.TCP_OOB_INLINE, null));
        assertEquals(293265, (int) connection.setOption(Options.WRITE_TIMEOUT, null));

        assertFalse(connection.getOption(Options.CLOSE_ABORT));
        assertEquals(0, (int) connection.getOption(Options.IP_TRAFFIC_CLASS));
        assertFalse(connection.getOption(Options.KEEP_ALIVE));
        assertEquals(0, (int) connection.getOption(Options.READ_TIMEOUT));
        assertTrue(connection.getOption(Options.RECEIVE_BUFFER) > 0);
        assertTrue(connection.getOption(Options.SEND_BUFFER) > 0);
        assertNotNull(connection.getOption(Options.TCP_NODELAY));
        assertFalse(connection.getOption(Options.TCP_OOB_INLINE));
        assertEquals(0, (int) connection.getOption(Options.WRITE_TIMEOUT));
    }

    @Test
    public void channelAddress() throws IOException {
        initChannels();
        assertEquals(bindAddress, connection.getPeerAddress());
        assertEquals(bindAddress, connection.getPeerAddress(InetSocketAddress.class));
        assertNull(connection.getPeerAddress(LocalSocketAddress.class));

        final SocketAddress clientAddress = connection.getLocalAddress();
        assertNotNull(clientAddress);
        assertEquals(clientAddress, connection.getLocalAddress((InetSocketAddress.class)));
        assertNull(connection.getLocalAddress((LocalSocketAddress.class)));

        assertNotNull(connection.toString());
    }

}
