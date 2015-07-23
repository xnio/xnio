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
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.channels.StreamChannel;

/**
 * Tests a pair of connected TCP stream channels (client/server).
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 */
@SuppressWarnings("deprecation")
public class TcpChannelTestCase extends AbstractNioStreamChannelTest {

    protected SocketAddress bindAddress;
    protected ConnectedStreamChannel channel = null;
    protected ConnectedStreamChannel serverChannel = null;
    protected AcceptingChannel<? extends ConnectedStreamChannel> server;

    @Before
    public void createServer() throws IOException {
        bindAddress = new InetSocketAddress(Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 }), 12345);
        final ChannelListener<AcceptingChannel<ConnectedStreamChannel>> acceptingChannelListener = new TestChannelListener<AcceptingChannel<ConnectedStreamChannel>>();;
        server = worker.createStreamServer(
                bindAddress, acceptingChannelListener, OptionMap.EMPTY);
        assertNotNull(server);
    }

    @After
    public void closeServer() throws IOException {
        server.close();
    }

    @Override
    protected synchronized void initChannels(XnioWorker xnioWorker, OptionMap optionMap, TestChannelListener<StreamChannel> channelListener,
            TestChannelListener<StreamChannel> serverChannelListener) throws IOException { 

        if (channel != null) {
            channel.close();
            serverChannel.close();
        }
        final IoFuture<ConnectedStreamChannel> connectedStreamChannel = xnioWorker.connectStream(bindAddress, null, optionMap);
        final FutureResult<ConnectedStreamChannel> accepted = new FutureResult<ConnectedStreamChannel>(xnioWorker);
        server.getAcceptThread().execute(new Runnable() {
            public void run() {
                try {
                    accepted.setResult(server.accept());
                } catch (IOException e) {
                    accepted.setException(e);
                }
            }
        });
        serverChannel = accepted.getIoFuture().get();
        channel = connectedStreamChannel.get();
        assertNotNull(serverChannel);
        assertNotNull(channel);
        channelListener.handleEvent(channel);
        serverChannelListener.handleEvent(serverChannel);
    }

    @Test
    public void optionSetup() throws IOException {
        initChannels();
        final Option<?>[] unsupportedOptions = OptionHelper.getNotSupportedOptions(Options.CLOSE_ABORT,
                Options.IP_TRAFFIC_CLASS, Options.KEEP_ALIVE, Options.READ_TIMEOUT, Options.RECEIVE_BUFFER,
                Options.RECEIVE_BUFFER, Options.SEND_BUFFER, Options.TCP_NODELAY, Options.TCP_OOB_INLINE,
                Options.WRITE_TIMEOUT);
        for (Option<?> option: unsupportedOptions) {
            assertFalse("Channel supports " + option, channel.supportsOption(option));
            assertNull("Expected null value for option " + option + " but got " + channel.getOption(option) + " instead",
                    channel.getOption(option));
        }

        assertTrue(channel.supportsOption(Options.CLOSE_ABORT));
        assertFalse(channel.getOption(Options.CLOSE_ABORT));
        assertTrue(channel.supportsOption(Options.IP_TRAFFIC_CLASS));
        assertEquals(0, (int) channel.getOption(Options.IP_TRAFFIC_CLASS));
        assertTrue(channel.supportsOption(Options.KEEP_ALIVE));
        assertFalse(channel.getOption(Options.KEEP_ALIVE));
        assertTrue(channel.supportsOption(Options.READ_TIMEOUT));
        assertEquals(0, (int) channel.getOption(Options.READ_TIMEOUT));
        assertTrue(channel.supportsOption(Options.RECEIVE_BUFFER));
        assertTrue(channel.getOption(Options.RECEIVE_BUFFER) > 0);
        assertTrue(channel.supportsOption(Options.SEND_BUFFER));
        assertTrue(channel.getOption(Options.SEND_BUFFER) > 0);
        assertTrue(channel.supportsOption(Options.TCP_NODELAY));
        assertNotNull(channel.getOption(Options.TCP_NODELAY));
        assertTrue(channel.supportsOption(Options.TCP_OOB_INLINE));
        assertFalse(channel.getOption(Options.TCP_OOB_INLINE));
        assertTrue(channel.supportsOption(Options.WRITE_TIMEOUT));
        assertEquals(0, (int) channel.getOption(Options.WRITE_TIMEOUT));

        channel.setOption(Options.CLOSE_ABORT, true);
        channel.setOption(Options.IP_TRAFFIC_CLASS, 5);
        channel.setOption(Options.KEEP_ALIVE, true);
        channel.setOption(Options.READ_TIMEOUT, 234095747);
        channel.setOption(Options.RECEIVE_BUFFER, 5000);
        channel.setOption(Options.SEND_BUFFER, 3000);
        channel.setOption(Options.TCP_NODELAY, true);
        channel.setOption(Options.TCP_OOB_INLINE, true);
        channel.setOption(Options.WRITE_TIMEOUT, 1301093);
        assertNull("Unexpected option value: " + channel.getOption(Options.MAX_INBOUND_MESSAGE_SIZE), channel.setOption(Options.MAX_INBOUND_MESSAGE_SIZE, 50000));// unsupported

        assertTrue(channel.getOption(Options.CLOSE_ABORT));
        assertTrue(channel.getOption(Options.IP_TRAFFIC_CLASS) >= 0);// it is okay that 5 is not returned
        // 5 value will only be set if the channels' family equals StandardProtocolFamily.INET
        assertTrue(channel.getOption(Options.KEEP_ALIVE));
        assertEquals(234095747, (int) channel.getOption(Options.READ_TIMEOUT));
        assertTrue(channel.getOption(Options.RECEIVE_BUFFER) > 0);
        assertTrue(channel.getOption(Options.SEND_BUFFER) >= 3000);
        assertTrue(channel.getOption(Options.TCP_NODELAY));
        assertTrue(channel.getOption(Options.TCP_OOB_INLINE));
        assertEquals(1301093, (int) channel.getOption(Options.WRITE_TIMEOUT));
        assertTrue(channel.getOption(Options.CLOSE_ABORT));
        assertNull(channel.getOption(Options.MAX_INBOUND_MESSAGE_SIZE));// unsupported

        assertTrue(channel.setOption(Options.CLOSE_ABORT, false));
        assertTrue(channel.setOption(Options.IP_TRAFFIC_CLASS, 30) >= 0);
        assertTrue(channel.setOption(Options.KEEP_ALIVE, false));
        assertEquals(234095747, (int) channel.setOption(Options.READ_TIMEOUT, 1290455));
        assertTrue(channel.setOption(Options.RECEIVE_BUFFER, 3000) >= 5000);
        assertTrue(channel.setOption(Options.SEND_BUFFER, 5000) >= 3000);
        assertTrue(channel.setOption(Options.TCP_NODELAY, false));
        assertTrue(channel.setOption(Options.TCP_OOB_INLINE, false));
        assertEquals(1301093, (int) channel.setOption(Options.WRITE_TIMEOUT, 293265));

        assertFalse(channel.getOption(Options.CLOSE_ABORT));
        assertTrue(channel.getOption(Options.IP_TRAFFIC_CLASS) >= 0);
        assertFalse(channel.getOption(Options.KEEP_ALIVE));
        assertEquals(1290455, (int) channel.getOption(Options.READ_TIMEOUT));
        assertTrue(channel.getOption(Options.RECEIVE_BUFFER) > 0);
        assertEquals(5000, (int) channel.getOption(Options.SEND_BUFFER));
        assertFalse(channel.getOption(Options.TCP_NODELAY));
        assertFalse(channel.getOption(Options.TCP_OOB_INLINE));
        assertEquals(293265, (int) channel.getOption(Options.WRITE_TIMEOUT));

        assertFalse(channel.setOption(Options.CLOSE_ABORT, null));
        assertFalse(channel.setOption(Options.KEEP_ALIVE, null));
        assertEquals(1290455, (int) channel.setOption(Options.READ_TIMEOUT, null));
        assertFalse(channel.setOption(Options.TCP_NODELAY, null));
        assertFalse(channel.setOption(Options.TCP_OOB_INLINE, null));
        assertEquals(293265, (int) channel.setOption(Options.WRITE_TIMEOUT, null));

        assertFalse(channel.getOption(Options.CLOSE_ABORT));
//        assertEquals(0, (int) channel.getOption(Options.IP_TRAFFIC_CLASS)); this check conflicts with line 157...
        assertFalse(channel.getOption(Options.KEEP_ALIVE));
        assertEquals(0, (int) channel.getOption(Options.READ_TIMEOUT));
        assertTrue(channel.getOption(Options.RECEIVE_BUFFER) > 0);
        assertTrue(channel.getOption(Options.SEND_BUFFER) > 0);
        assertNotNull(channel.getOption(Options.TCP_NODELAY));
        assertFalse(channel.getOption(Options.TCP_OOB_INLINE));
        assertEquals(0, (int) channel.getOption(Options.WRITE_TIMEOUT));
    }

    @Test
    public void channelAddress() throws IOException {
        initChannels();
        assertEquals(bindAddress, channel.getPeerAddress());
        assertEquals(bindAddress, channel.getPeerAddress(InetSocketAddress.class));
        assertNull(channel.getPeerAddress(LocalSocketAddress.class));

        final SocketAddress clientAddress = channel.getLocalAddress();
        assertNotNull(clientAddress);
        assertEquals(clientAddress, channel.getLocalAddress((InetSocketAddress.class)));
        assertNull(channel.getLocalAddress((LocalSocketAddress.class)));

        assertNotNull(channel.toString());
    }

}
