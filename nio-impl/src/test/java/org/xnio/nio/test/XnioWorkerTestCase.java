/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2013 Red Hat, Inc. and/or its affiliates, and individual
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
package org.xnio.nio.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.Channel;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xnio.ChannelListener;
import org.xnio.IoFuture;
import org.xnio.LocalSocketAddress;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.StreamConnection;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.BoundChannel;
import org.xnio.channels.ConnectedChannel;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.channels.MulticastMessageChannel;

/**
 * Test for XnioWorker.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 *
 */
@SuppressWarnings("deprecation")
public class XnioWorkerTestCase {

    private static final int SERVER_PORT = 12345;
    private static SocketAddress bindAddress;
    private static XnioWorker worker;
    private static Xnio xnio;

    protected AcceptingChannel<? extends ConnectedStreamChannel> server;

    @BeforeClass
    public static void createWorker() throws IOException {
        xnio = Xnio.getInstance("nio", XnioWorkerTestCase.class.getClassLoader());
        bindAddress = new InetSocketAddress(Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 }), SERVER_PORT);
    }

    @AfterClass
    public static void destroyWorker() throws InterruptedException {
        if (worker != null && !worker.isShutdown()) {
            worker.shutdown();
            worker.awaitTermination(1L, TimeUnit.MINUTES);
        }
    }

    @Test
    public void illegalWorker() throws IOException {
        IllegalArgumentException expected = null;
        try {
            xnio.createWorker(OptionMap.create(Options.WORKER_IO_THREADS, -1));
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            xnio.createWorker(OptionMap.create(Options.STACK_SIZE, -10000l));
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    @Test
    public void createTcpStreamServerAndConnect() throws IOException {
        final XnioWorker xnioWorker = xnio.createWorker(OptionMap.create(Options.THREAD_DAEMON, true));
        final ChannelListener<AcceptingChannel<StreamConnection>> acceptingChannelListener = new TestChannelListener<AcceptingChannel<StreamConnection>>();;
        final AcceptingChannel<? extends StreamConnection> streamServer = xnioWorker.createStreamConnectionServer(
                bindAddress, acceptingChannelListener, OptionMap.create(Options.BROADCAST, true));
        assertNotNull(streamServer);
        try {
            assertEquals(bindAddress, streamServer.getLocalAddress());
            assertSame(xnioWorker, streamServer.getWorker());
            final TestChannelListener<StreamConnection> connectionListener1 = new TestChannelListener<StreamConnection>();
            final TestChannelListener<StreamConnection> connectionListener2 = new TestChannelListener<StreamConnection>();
            final TestChannelListener<BoundChannel> bindListener = new TestChannelListener<BoundChannel>();
            final IoFuture<StreamConnection> connection1 = xnioWorker.openStreamConnection(bindAddress, connectionListener1, OptionMap.create(Options.MAX_INBOUND_MESSAGE_SIZE, 50000, Options.WORKER_ESTABLISH_WRITING, true));
            final IoFuture<StreamConnection> connection2 = xnioWorker.openStreamConnection(bindAddress, connectionListener2, bindListener, OptionMap.create(Options.MAX_OUTBOUND_MESSAGE_SIZE, 50000));
            assertNotNull(connection1);
            assertNotNull(connection2);
            assertServerClientConnection(streamServer, bindListener, connection1.get(), connectionListener1, connection2.get(), connectionListener2);
        } finally {
            streamServer.close();
        }
    }

    private void assertServerClientConnection(AcceptingChannel<? extends ConnectedChannel> server, TestChannelListener<BoundChannel> bindListener, ConnectedChannel clientChannel1, final TestChannelListener<? extends ConnectedChannel> clientListener1, ConnectedChannel clientChannel2, final TestChannelListener<? extends ConnectedChannel> clientListener2) throws IOException {
        assertNotNull(server);
        assertNotNull(clientChannel1);
        assertNotNull(clientChannel2);
        try {
            
            assertTrue(clientListener1.isInvoked());
            assertSame(clientChannel1, clientListener1.getChannel());
            assertEquals(bindAddress, clientChannel1.getPeerAddress());

            assertTrue(clientListener2.isInvoked());
            assertSame(clientChannel2, clientListener2.getChannel());
            assertTrue(bindListener.isInvoked());
            final BoundChannel boundChannel = bindListener.getChannel();
            assertNotNull(boundChannel);
            assertEquals(clientChannel2.getLocalAddress(), boundChannel.getLocalAddress());
            assertEquals(clientChannel2.getLocalAddress(InetSocketAddress.class), boundChannel.getLocalAddress(InetSocketAddress.class));
            assertEquals(clientChannel2.getLocalAddress(LocalSocketAddress.class), boundChannel.getLocalAddress(LocalSocketAddress.class));
            assertSame(clientChannel2.getWorker(), boundChannel.getWorker());
            assertEquals(clientChannel2.getOption(Options.SEND_BUFFER), boundChannel.getOption(Options.SEND_BUFFER));
            clientChannel2.setOption(Options.SEND_BUFFER, 3000);
            assertEquals(3000, (int) boundChannel.getOption(Options.SEND_BUFFER));
            assertEquals(bindAddress, clientChannel2.getPeerAddress());
            assertTrue(boundChannel.supportsOption(Options.KEEP_ALIVE));
            assertFalse(boundChannel.supportsOption(Options.CONNECTION_LOW_WATER));
            assertNotNull(boundChannel.toString());
            
            final TestChannelListener<BoundChannel> boundChannelCloseListener = new TestChannelListener<BoundChannel>();
            boundChannel.getCloseSetter().set(boundChannelCloseListener);
            assertTrue(boundChannel.isOpen());
            assertTrue(clientChannel2.isOpen());
            assertFalse(boundChannelCloseListener.isInvokedYet());
            boundChannel.close();
            assertTrue(boundChannelCloseListener.isInvoked());
            assertFalse(boundChannel.isOpen());
            assertFalse(clientChannel2.isOpen());

        } finally {
            clientChannel1.close();
            clientChannel2.close();
        }
    }

    @Test
    public void createTcpStreamChannelServerAndConnect() throws IOException {
        final XnioWorker xnioWorker = xnio.createWorker(OptionMap.create(Options.THREAD_DAEMON, true));
        final ChannelListener<AcceptingChannel<ConnectedStreamChannel>> acceptingChannelListener = new TestChannelListener<AcceptingChannel<ConnectedStreamChannel>>();;
        final AcceptingChannel<? extends ConnectedStreamChannel> streamServer = xnioWorker.createStreamServer(
                bindAddress, acceptingChannelListener, OptionMap.create(Options.BROADCAST, true));
        assertNotNull(streamServer);
        try {
            assertEquals(bindAddress, streamServer.getLocalAddress());
            assertSame(xnioWorker, streamServer.getWorker());
            final TestChannelListener<ConnectedStreamChannel> channelListener1 = new TestChannelListener<ConnectedStreamChannel>();
            final TestChannelListener<ConnectedStreamChannel> channelListener2 = new TestChannelListener<ConnectedStreamChannel>();
            final TestChannelListener<BoundChannel> bindListener = new TestChannelListener<BoundChannel>();
            final IoFuture<ConnectedStreamChannel> connectedStreamChannel1 = xnioWorker.connectStream(bindAddress, channelListener1, OptionMap.create(Options.MAX_INBOUND_MESSAGE_SIZE, 50000, Options.WORKER_ESTABLISH_WRITING, true));
            final IoFuture<ConnectedStreamChannel> connectedStreamChannel2 = xnioWorker.connectStream(bindAddress, channelListener2, bindListener, OptionMap.create(Options.MAX_OUTBOUND_MESSAGE_SIZE, 50000));
            assertNotNull(connectedStreamChannel1);
            assertNotNull(connectedStreamChannel2);
    
            assertServerClientConnection(streamServer, bindListener, connectedStreamChannel1.get(), channelListener1, connectedStreamChannel2.get(), channelListener2);
        } finally {
            streamServer.close();
        }
    }

    @Test
    public void cancelOpenStreamConnection() throws IOException {
        final XnioWorker xnioWorker = xnio.createWorker(OptionMap.create(Options.THREAD_DAEMON, true));
        final ChannelListener<AcceptingChannel<StreamConnection>> acceptingChannelListener = new TestChannelListener<AcceptingChannel<StreamConnection>>();;
        final AcceptingChannel<? extends StreamConnection> streamServer = xnioWorker.createStreamConnectionServer(
                bindAddress, acceptingChannelListener, OptionMap.create(Options.BROADCAST, true));
        assertNotNull(streamServer);
        try {
            final TestChannelListener<StreamConnection> channelListener = new TestChannelListener<StreamConnection>();
            final TestChannelListener<BoundChannel> bindListener = new TestChannelListener<BoundChannel>();
            IoFuture<StreamConnection> connectedStreamChannel = null;
            do {
                if (connectedStreamChannel != null) {
                    connectedStreamChannel.await();
                    final IoFuture.Status status = connectedStreamChannel.getStatus();
                    if (status == IoFuture.Status.DONE) {
                        connectedStreamChannel.get().close();
                    }
                    channelListener.clear();
                }
                connectedStreamChannel = xnioWorker.openStreamConnection(bindAddress, channelListener, OptionMap.create(Options.MAX_INBOUND_MESSAGE_SIZE, 50000, Options.WORKER_ESTABLISH_WRITING, true)).cancel();
                connectedStreamChannel.cancel();
            } while (connectedStreamChannel.getStatus() != IoFuture.Status.CANCELLED);

            CancellationException expected = null;
            try {
                connectedStreamChannel.get();
            } catch (CancellationException e) {
                expected = e;
            }
            assertNotNull(expected);
            assertSame(IoFuture.Status.CANCELLED, connectedStreamChannel.getStatus());

            assertFalse(channelListener.isInvokedYet());
            assertFalse(bindListener.isInvokedYet());

            // make sure that the server is up and can accept more connections
            assertTrue(streamServer.isOpen());
            final IoFuture<StreamConnection> anotherChannel = xnioWorker.openStreamConnection(bindAddress, null, OptionMap.EMPTY);
            assertNotNull(anotherChannel.get());
            anotherChannel.get().close();
        } finally {
            streamServer.close();
        }
    }

    @Test
    public void cancelConnectStream() throws IOException {
        final XnioWorker xnioWorker = xnio.createWorker(OptionMap.create(Options.THREAD_DAEMON, true));
        final ChannelListener<AcceptingChannel<ConnectedStreamChannel>> acceptingChannelListener = new TestChannelListener<AcceptingChannel<ConnectedStreamChannel>>();;
        final AcceptingChannel<? extends ConnectedStreamChannel> streamServer = xnioWorker.createStreamServer(
                bindAddress, acceptingChannelListener, OptionMap.create(Options.BROADCAST, true));
        assertNotNull(streamServer);
        try {
            final TestChannelListener<ConnectedStreamChannel> channelListener = new TestChannelListener<ConnectedStreamChannel>();
            final TestChannelListener<BoundChannel> bindListener = new TestChannelListener<BoundChannel>();
            IoFuture<ConnectedStreamChannel> connectedStreamChannel = null;
            do {
                if (connectedStreamChannel != null) {
                    connectedStreamChannel.await();
                    final IoFuture.Status status = connectedStreamChannel.getStatus();
                    if (status == IoFuture.Status.DONE) {
                        connectedStreamChannel.get().close();
                    }
                    channelListener.clear();
                }
                connectedStreamChannel = xnioWorker.connectStream(bindAddress, channelListener, OptionMap.create(Options.MAX_INBOUND_MESSAGE_SIZE, 50000, Options.WORKER_ESTABLISH_WRITING, true)).cancel();
                connectedStreamChannel.cancel();
            } while (connectedStreamChannel.getStatus() != IoFuture.Status.CANCELLED);

            CancellationException expected = null;
            try {
                connectedStreamChannel.get();
            } catch (CancellationException e) {
                expected = e;
            }
            assertNotNull(expected);
            assertSame(IoFuture.Status.CANCELLED, connectedStreamChannel.getStatus());

            assertFalse(channelListener.isInvokedYet());
            assertFalse(bindListener.isInvokedYet());

            // make sure that the server is up and can accept more connections
            assertTrue(streamServer.isOpen());
            final IoFuture<ConnectedStreamChannel> anotherChannel = xnioWorker.connectStream(bindAddress, null, OptionMap.EMPTY);
            assertNotNull(anotherChannel.get());
            anotherChannel.get().close();
        } finally {
            streamServer.close();
        }
    }

    @Test
    public void createLocalStreamConnectionServer() throws IOException {
        final XnioWorker xnioWorker = xnio.createWorker(OptionMap.EMPTY);
        UnsupportedOperationException expected = null;
        try {
            xnioWorker.createStreamConnectionServer(new LocalSocketAddress("server"), null, OptionMap.EMPTY);
        } catch (UnsupportedOperationException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    @Test
    public void createLocalStreamServer() throws IOException {
        final XnioWorker xnioWorker = xnio.createWorker(OptionMap.EMPTY);
        UnsupportedOperationException expected = null;
        try {
            xnioWorker.createStreamServer(new LocalSocketAddress("server"), null, OptionMap.EMPTY);
        } catch (UnsupportedOperationException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    @Test
    public void openLocalStreamConnection() throws IOException {
        final XnioWorker xnioWorker = xnio.createWorker(OptionMap.EMPTY);
        UnsupportedOperationException expected = null;
        try {
            xnioWorker.openStreamConnection(new LocalSocketAddress("server for test"), null, OptionMap.EMPTY);
        } catch (UnsupportedOperationException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    @Test
    public void connectLocalStream() throws IOException {
        final XnioWorker xnioWorker = xnio.createWorker(OptionMap.EMPTY);
        UnsupportedOperationException expected = null;
        try {
            xnioWorker.connectStream(new LocalSocketAddress("server for test"), null, OptionMap.EMPTY);
        } catch (UnsupportedOperationException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    @Test
    public void acceptStreamConnection() throws IOException {
        final XnioWorker xnioWorker = xnio.createWorker(OptionMap.EMPTY);
        final InetSocketAddress bindAddress2 = new InetSocketAddress(Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 }), 23456);
        final TestChannelListener<StreamConnection> channelListener1 = new TestChannelListener<StreamConnection>();
        final TestChannelListener<StreamConnection> channelListener2 = new TestChannelListener<StreamConnection>();
        final TestChannelListener<BoundChannel> bindListener1 = new TestChannelListener<BoundChannel>();
        final TestChannelListener<BoundChannel> bindListener2 = new TestChannelListener<BoundChannel>();
        final OptionMap optionMap = OptionMap.create(Options.READ_TIMEOUT, 800000);
        final IoFuture<StreamConnection> channelFuture1 = xnioWorker.acceptStreamConnection(bindAddress, channelListener1, bindListener1, optionMap);
        final IoFuture<StreamConnection> channelFuture2 = xnioWorker.acceptStreamConnection(bindAddress2, channelListener2, bindListener2, optionMap);
        final IoFuture<StreamConnection> connectedStreamChannel1 = xnioWorker.openStreamConnection(bindAddress, null, OptionMap.EMPTY);
        final IoFuture<StreamConnection> connectedStreamChannel2 = xnioWorker.openStreamConnection(bindAddress2, null, OptionMap.EMPTY);
        assertNotNull(connectedStreamChannel1);
        assertNotNull(connectedStreamChannel2);
        assertNotNull(channelFuture1);
        assertNotNull(channelFuture2);

        final StreamConnection channel1 = channelFuture1.get();
        final StreamConnection channel2 = channelFuture2.get();
        assertNotNull(channel1);
        assertNotNull(channel2);
        assertAcceptedChannels(xnioWorker, channel1, channelListener1, bindListener1, bindAddress,
                connectedStreamChannel1.get().getLocalAddress(), channel2, channelListener2, bindListener2,
                bindAddress2, connectedStreamChannel2.get().getLocalAddress());
    }

    private void assertAcceptedChannels(XnioWorker worker, ConnectedChannel channel1, TestChannelListener<? extends ConnectedChannel> channelListener1,
            TestChannelListener<BoundChannel> bindListener1,  SocketAddress bindAddress1, SocketAddress peerAddress1, ConnectedChannel channel2,
            TestChannelListener<? extends ConnectedChannel> channelListener2, TestChannelListener<BoundChannel> bindListener2,
            SocketAddress bindAddress2, SocketAddress peerAddress2) throws IOException {
        try {
            assertTrue(channelListener1.isInvoked());
            assertSame(channel1, channelListener1.getChannel());
            assertTrue(bindListener1.isInvoked());
            assertEquals(peerAddress1, channel1.getPeerAddress());

            final BoundChannel boundChannel1 = bindListener1.getChannel();
            assertNotNull(boundChannel1);
            assertSame(worker, boundChannel1.getWorker());
            assertEquals(bindAddress, boundChannel1.getLocalAddress());
            assertNull(boundChannel1.getLocalAddress(LocalSocketAddress.class));
            assertNotNull(boundChannel1.getCloseSetter());
            assertFalse(boundChannel1.isOpen()); // expected
            assertFalse(boundChannel1.supportsOption(Options.KEEP_ALIVE));
            assertNull(boundChannel1.getOption(Options.KEEP_ALIVE));
            assertNull(boundChannel1.setOption(Options.KEEP_ALIVE, null));
            assertNull(boundChannel1.getOption(Options.KEEP_ALIVE));
            assertNotNull(boundChannel1.toString());

            assertTrue(channelListener2.isInvoked());
            assertSame(channel2, channelListener2.getChannel());
            assertTrue(bindListener2.isInvoked());
            assertEquals(peerAddress2, channel2.getPeerAddress());

            final BoundChannel boundChannel2 = bindListener2.getChannel();
            assertNotNull(boundChannel2);
            assertSame(worker, boundChannel2.getWorker());
            assertEquals(bindAddress2, boundChannel2.getLocalAddress());
            assertNull(boundChannel2.getLocalAddress(LocalSocketAddress.class));
            assertNotNull(boundChannel2.getCloseSetter());
            assertFalse(boundChannel2.isOpen()); // expected
            assertFalse(boundChannel2.supportsOption(Options.KEEP_ALIVE));
            assertNull(boundChannel2.getOption(Options.KEEP_ALIVE));
            assertNull(boundChannel2.setOption(Options.KEEP_ALIVE, null));
            assertNull(boundChannel2.getOption(Options.KEEP_ALIVE));
            assertNotNull(boundChannel2.toString());
        } finally {
            channel1.close();
            channel2.close();
        }
    }

    @Test
    public void acceptTcpStream() throws IOException {
        final XnioWorker xnioWorker = xnio.createWorker(OptionMap.EMPTY);
        final InetSocketAddress bindAddress2 = new InetSocketAddress(Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 }), 23456);
        final TestChannelListener<ConnectedStreamChannel> channelListener1 = new TestChannelListener<ConnectedStreamChannel>();
        final TestChannelListener<ConnectedStreamChannel> channelListener2 = new TestChannelListener<ConnectedStreamChannel>();
        final TestChannelListener<BoundChannel> bindListener1 = new TestChannelListener<BoundChannel>();
        final TestChannelListener<BoundChannel> bindListener2 = new TestChannelListener<BoundChannel>();
        final OptionMap optionMap = OptionMap.create(Options.READ_TIMEOUT, 800000);
        final IoFuture<ConnectedStreamChannel> channelFuture1 = xnioWorker.acceptStream(bindAddress, channelListener1, bindListener1, optionMap);
        final IoFuture<ConnectedStreamChannel> channelFuture2 = xnioWorker.acceptStream(bindAddress2, channelListener2, bindListener2, optionMap);
        final IoFuture<ConnectedStreamChannel> connectedStreamChannel1 = xnioWorker.connectStream(bindAddress, null, OptionMap.EMPTY);
        final IoFuture<ConnectedStreamChannel> connectedStreamChannel2 = xnioWorker.connectStream(bindAddress2, null, OptionMap.EMPTY);
        assertNotNull(connectedStreamChannel1);
        assertNotNull(connectedStreamChannel2);
        assertNotNull(channelFuture1);
        assertNotNull(channelFuture2);

        final ConnectedStreamChannel channel1 = channelFuture1.get();
        final ConnectedStreamChannel channel2 = channelFuture2.get();
        assertNotNull(channel1);
        assertNotNull(channel2);

        assertAcceptedChannels(xnioWorker, channel1, channelListener1, bindListener1, bindAddress,
                connectedStreamChannel1.get().getLocalAddress(), channel2, channelListener2, bindListener2,
                bindAddress2, connectedStreamChannel2.get().getLocalAddress());
    }

    @Test
    public void cancelAcceptStreamConnection() throws CancellationException, IOException {
        final XnioWorker xnioWorker = xnio.createWorker(OptionMap.EMPTY);
        final TestChannelListener<StreamConnection> channelListener = new TestChannelListener<StreamConnection>();
        final TestChannelListener<BoundChannel> bindListener = new TestChannelListener<BoundChannel>();
        IoFuture<StreamConnection> connectionFuture1 = xnioWorker.acceptStreamConnection(bindAddress, channelListener, bindListener, OptionMap.EMPTY);
        IoFuture<StreamConnection> connection2 = xnioWorker.openStreamConnection(bindAddress, null, OptionMap.EMPTY);

        assertNotNull(connectionFuture1);
        assertNotNull(connection2);

        connectionFuture1.cancel();

        // we were beaten, couldn't cancel the connection future
        while (connectionFuture1.getStatus() != IoFuture.Status.CANCELLED) {
            StreamConnection connection1 = connectionFuture1.get();
            assertNotNull(connection1);
            connection2.get().close();
            // try again once more
            connectionFuture1 = xnioWorker.acceptStreamConnection(bindAddress, channelListener, bindListener, OptionMap.EMPTY);
            connection2 = xnioWorker.openStreamConnection(bindAddress, null, OptionMap.EMPTY);
        }

        CancellationException expected = null;
        try {
            connectionFuture1.get();
        } catch (CancellationException e) {
            expected = e;
        }
        assertNotNull(expected);

        assertFalse(channelListener.isInvokedYet());
        assertFalse(channelListener.isInvokedYet());
        if (bindListener.isInvokedYet()) {
            BoundChannel boundChannel = bindListener.getChannel();
            assertNotNull(boundChannel);
            assertFalse(boundChannel.isOpen());
        }
    }

    @Test
    public void cancelAcceptTcpStream() throws CancellationException, IOException {
        final XnioWorker xnioWorker = xnio.createWorker(OptionMap.EMPTY);
        final TestChannelListener<ConnectedStreamChannel> channelListener = new TestChannelListener<ConnectedStreamChannel>();
        final TestChannelListener<BoundChannel> bindListener = new TestChannelListener<BoundChannel>();
        IoFuture<ConnectedStreamChannel> channelFuture = xnioWorker.acceptStream(bindAddress, channelListener, bindListener, OptionMap.EMPTY);
        IoFuture<ConnectedStreamChannel> connectedStreamChannel = xnioWorker.connectStream(bindAddress, null, OptionMap.EMPTY);

        assertNotNull(connectedStreamChannel);
        assertNotNull(channelFuture);

        channelFuture.cancel();

        // we were beaten, couldn't cancel the channel future
        while (channelFuture.getStatus() != IoFuture.Status.CANCELLED) {
            ConnectedStreamChannel channel = channelFuture.get();
            assertNotNull(channel);
            connectedStreamChannel.get().close();
            channel.close();
            // try again once more
            channelFuture = xnioWorker.acceptStream(bindAddress, channelListener, bindListener, OptionMap.EMPTY).cancel();
            connectedStreamChannel = xnioWorker.connectStream(bindAddress, null, OptionMap.EMPTY);
        }

        CancellationException expected = null;
        try {
            channelFuture.get();
        } catch (CancellationException e) {
            expected = e;
        }
        assertNotNull(expected);

        assertFalse(channelListener.isInvokedYet());
        if (bindListener.isInvokedYet()) {
            BoundChannel boundChannel = bindListener.getChannel();
            assertNotNull(boundChannel);
            assertFalse(boundChannel.isOpen());
        }
    }

    @Test
    public void acceptLocalStream() throws IOException {
        final XnioWorker xnioWorker = xnio.createWorker(OptionMap.EMPTY);
        UnsupportedOperationException expected = null;
        try {
            xnioWorker.acceptStream(new LocalSocketAddress("local address"), null, null, OptionMap.EMPTY);
        } catch (UnsupportedOperationException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            xnioWorker.acceptStreamConnection(new LocalSocketAddress("local address"), null, null, OptionMap.EMPTY);
        } catch (UnsupportedOperationException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    @Test
    public void connectTcpDatagram() throws IOException {
        final XnioWorker xnioWorker = xnio.createWorker(OptionMap.EMPTY);
        IllegalArgumentException expected = null;
        try {
            xnioWorker.connectDatagram(bindAddress, null, null, OptionMap.EMPTY);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    @Test
    public void connectLocalDatagram() throws IOException {
        final XnioWorker xnioWorker = xnio.createWorker(OptionMap.EMPTY);
        UnsupportedOperationException expected = null;
        try {
            xnioWorker.connectDatagram(new LocalSocketAddress("local"), null, null, OptionMap.EMPTY);
        } catch (UnsupportedOperationException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    @Test
    public void acceptDatagram() throws IOException {
        final XnioWorker xnioWorker = xnio.createWorker(OptionMap.EMPTY);
        IllegalArgumentException expected = null;
        try {
            xnioWorker.acceptDatagram(bindAddress, null, null, OptionMap.EMPTY);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    @Test
    public void acceptMessageConnection() throws IOException {
        final XnioWorker xnioWorker = xnio.createWorker(OptionMap.EMPTY);
        IllegalArgumentException expected = null;
        try {
            xnioWorker.acceptMessageConnection(bindAddress, null, null, OptionMap.EMPTY);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    @Test
    public void createUdpServer() throws IOException {
        final XnioWorker xnioWorker = xnio.createWorker(OptionMap.EMPTY);
        final InetSocketAddress address = new InetSocketAddress(0);
        final OptionMap optionMap = OptionMap.create(Options.MULTICAST, true, Options.SECURE, false);
        final MulticastMessageChannel channel = xnioWorker.createUdpServer(address, optionMap);
        assertNotNull(channel);
        try {
            // check address
            assertNotNull(channel.getLocalAddress());
        } finally {
            channel.close();
        }
    }

    @Test
    public void createUdpServerWithListener() throws IOException {
        final XnioWorker xnioWorker = xnio.createWorker(OptionMap.EMPTY);
        final InetSocketAddress address = new InetSocketAddress(0);
        final TestChannelListener<MulticastMessageChannel> listener = new TestChannelListener<MulticastMessageChannel>();
        final OptionMap optionMap = OptionMap.create(Options.MULTICAST, true, Options.SECURE, true);
        final MulticastMessageChannel channel = xnioWorker.createUdpServer(address, listener, optionMap);
        assertNotNull(channel);
        try {
            // check address
            assertNotNull(channel.getLocalAddress());
            // check listener
            assertTrue(listener.isInvoked());
            assertSame(channel, listener.getChannel());
        } finally {
            channel.close();
        }
    }

    @Test
    public void createFullDuplexPipe() throws IOException {
        final XnioWorker xnioWorker = xnio.createWorker(OptionMap.EMPTY);
        assertNotNull(xnioWorker.createFullDuplexPipe());
        assertNotNull(xnioWorker.createHalfDuplexPipe());
    }

    @Test
    public void shutdownNowWithTerminationTask() throws IOException, InterruptedException {
        final TerminationTask terminationTask = new TerminationTask();
        final XnioWorker xnioWorker = xnio.createWorker(Thread.currentThread().getThreadGroup(), OptionMap.EMPTY, terminationTask);
        assertFalse(xnioWorker.isShutdown());
        assertFalse(xnioWorker.isTerminated());
        xnioWorker.shutdownNow();
        assertTrue(xnioWorker.isShutdown());
        if (!xnioWorker.isTerminated()) {
            xnioWorker.awaitTermination();
        }
        assertTrue(terminationTask.isInvoked());
        assertTrue(xnioWorker.isShutdown());
        assertTrue(xnioWorker.isTerminated());

        xnioWorker.awaitTermination();
        // idempotent
        xnioWorker.shutdown();
        xnioWorker.shutdownNow();
    }

    @Test
    public void awaitTerminationWithMultipleAwaiters1() throws IOException, InterruptedException {
        final XnioWorker worker = xnio.createWorker(OptionMap.EMPTY);
        final TerminationAwaiter awaiter1 = new TerminationAwaiter(worker);
        final TerminationAwaiter awaiter2 = new TerminationAwaiter(worker, 10, TimeUnit.HOURS);
        final TerminationAwaiter awaiter3 = new TerminationAwaiter(worker, 10, TimeUnit.MILLISECONDS);
        final Thread thread1 = new Thread(awaiter1);
        final Thread thread2 = new Thread(awaiter2);
        final Thread thread3 = new Thread(awaiter3);
        thread1.start();
        thread2.start();
        thread3.start();

        thread1.join(20);
        thread2.join(20);
        thread3.join();
        assertFalse(awaiter3.isInterrupted());
        assertTrue(thread1.isAlive());
        assertTrue(thread2.isAlive());

        worker.shutdown();
        thread1.join();
        thread2.join();
        assertFalse(awaiter1.isInterrupted());
        assertFalse(awaiter2.isInterrupted());
    }

    @Test
    public void awaitTerminationWithMultipleAwaiters2() throws IOException, InterruptedException {
        final XnioWorker worker = xnio.createWorker(OptionMap.EMPTY);
        final TerminationAwaiter awaiter1 = new TerminationAwaiter(worker);
        final TerminationAwaiter awaiter2 = new TerminationAwaiter(worker, 10, TimeUnit.HOURS);
        final TerminationAwaiter awaiter3 = new TerminationAwaiter(worker, 300, TimeUnit.MILLISECONDS);
        final TerminationAwaiter awaiter4 = new TerminationAwaiter(worker, 10, TimeUnit.MILLISECONDS);
        final TerminationAwaiter awaiter5 = new TerminationAwaiter(worker, 100, TimeUnit.MILLISECONDS);
        final TerminationAwaiter awaiter6 = new TerminationAwaiter(worker, 10, TimeUnit.MINUTES);
        final TerminationAwaiter awaiter7 = new TerminationAwaiter(worker, 1, TimeUnit.MILLISECONDS);
        final Thread thread1 = new Thread(awaiter1);
        final Thread thread2 = new Thread(awaiter2);
        final Thread thread3 = new Thread(awaiter3);
        final Thread thread4 = new Thread(awaiter4);
        final Thread thread5 = new Thread(awaiter5);
        final Thread thread6 = new Thread(awaiter6);
        final Thread thread7 = new Thread(awaiter7);
        thread1.start();
        thread2.start();
        thread3.start();
        thread4.start();
        thread5.start();
        thread6.start();
        thread7.start();

        thread1.join(20);
        thread2.join(20);
        thread4.join();
        thread5.join();
        thread3.join();
        thread6.join(20);
        thread7.join();
        assertFalse(awaiter3.isInterrupted());
        assertFalse(awaiter4.isInterrupted());
        assertFalse(awaiter5.isInterrupted());
        assertFalse(awaiter7.isInterrupted());
        assertTrue(thread1.isAlive());
        assertTrue(thread2.isAlive());
        assertTrue(thread6.isAlive());

        worker.shutdown();
        thread1.join();
        thread2.join();
        thread6.join();
        assertFalse(awaiter1.isInterrupted());
        assertFalse(awaiter2.isInterrupted());
        assertFalse(awaiter6.isInterrupted());
    }

    @Test
    public void awaitTerminationWithMultipleAwaiters3() throws IOException, InterruptedException {
        final XnioWorker worker = xnio.createWorker(OptionMap.EMPTY);
        final TerminationAwaiter awaiter1 = new TerminationAwaiter(worker, 1, TimeUnit.NANOSECONDS);
        final TerminationAwaiter awaiter2 = new TerminationAwaiter(worker, 10, TimeUnit.HOURS);
        final TerminationAwaiter awaiter3 = new TerminationAwaiter(worker);
        final TerminationAwaiter awaiter4 = new TerminationAwaiter(worker, 3, TimeUnit.MILLISECONDS);
        final TerminationAwaiter awaiter5 = new TerminationAwaiter(worker);
        final Thread thread1 = new Thread(awaiter1);
        final Thread thread2 = new Thread(awaiter2);
        final Thread thread3 = new Thread(awaiter3);
        final Thread thread4 = new Thread(awaiter4);
        final Thread thread5 = new Thread(awaiter5);
        thread1.start();
        thread2.start();
        thread3.start();
        thread4.start();
        thread5.start();

        thread1.join();
        thread2.join(20);
        thread3.join(20);
        thread4.join();
        thread5.join(20);
        assertFalse(awaiter1.isInterrupted());
        assertFalse(awaiter4.isInterrupted());
        assertTrue(thread2.isAlive());
        assertTrue(thread3.isAlive());
        assertTrue(thread5.isAlive());

        worker.shutdown();
        thread2.join();
        thread3.join();
        thread5.join();
        assertFalse(awaiter2.isInterrupted());
        assertFalse(awaiter3.isInterrupted());
        assertFalse(awaiter5.isInterrupted());
    }

    @Test
    public void interruptedAwaiters() throws IOException, InterruptedException {
        final XnioWorker worker = xnio.createWorker(OptionMap.EMPTY);
        final TerminationAwaiter awaiter1 = new TerminationAwaiter(worker);
        final TerminationAwaiter awaiter2 = new TerminationAwaiter(worker, 3, TimeUnit.MINUTES);
        final Thread thread1 = new Thread(awaiter1);
        final Thread thread2 = new Thread(awaiter2);

        thread1.start();
        thread2.start();

        thread1.interrupt();
        thread2.interrupt();

        thread1.join();
        thread2.join();
        assertTrue(awaiter1.isInterrupted());
        assertTrue(awaiter2.isInterrupted());

        worker.shutdown();
    }

    @Test
    public void invalidAcceptStream() throws IOException {
        final XnioWorker worker = xnio.createWorker(OptionMap.create(Options.WORKER_READ_THREADS, 0, Options.WORKER_WRITE_THREADS, 0));
        assertNotNull(worker);

        IllegalArgumentException expected = null;
        try {
            worker.acceptStream(bindAddress, null, null, OptionMap.EMPTY);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            worker.acceptStream(bindAddress, null, null, OptionMap.create(Options.WORKER_ESTABLISH_WRITING, false));
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            worker.acceptStream(bindAddress, null, null, OptionMap.create(Options.WORKER_ESTABLISH_WRITING, false));
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    private static class TestChannelListener<C extends Channel> implements ChannelListener<C> {

        private boolean invoked = false;
        private C channel = null;
        private CountDownLatch latch = new CountDownLatch(1);

        @Override
        public void handleEvent(C c) {
            invoked = true;
            channel = c;
            latch.countDown();
        }

        public boolean isInvoked() {
            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return invoked;
        }

        public boolean isInvokedYet() {
            return invoked;
        }

        public C getChannel() {
            return channel;
        }

        public void clear() {
            invoked = false;
        }
    }

    private static class TerminationTask implements Runnable {

        private boolean invoked = false;
        private final CountDownLatch countDown = new CountDownLatch(1);

        @Override
        public void run() {
            invoked = true;
            countDown.countDown();
        }

        public boolean isInvoked() {
            try {
                countDown.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return invoked;
        }
    }

    private static class TerminationAwaiter implements Runnable {

        private final XnioWorker xnioWorker;
        private final long timeout;
        private final TimeUnit timeoutUnit;
        private boolean interrupted;

        public TerminationAwaiter (XnioWorker w, long t, TimeUnit tu) {
            xnioWorker = w;;
            timeout = t;
            timeoutUnit = tu;
        }

        public TerminationAwaiter(XnioWorker w) {
            this(w, -1, null);
        }

        public void run() {
            try {
                if (timeoutUnit == null) {
                    xnioWorker.awaitTermination();
                } else {
                    xnioWorker.awaitTermination(timeout, timeoutUnit);
                }
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }

        public boolean isInterrupted() {
            return interrupted;
        }
    }
}
