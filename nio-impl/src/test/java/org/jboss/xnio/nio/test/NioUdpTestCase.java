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

package org.jboss.xnio.nio.test;

import junit.framework.TestCase;
import org.jboss.xnio.spi.Lifecycle;
import org.jboss.xnio.spi.Provider;
import org.jboss.xnio.spi.UdpServerService;
import org.jboss.xnio.nio.NioProvider;
import org.jboss.xnio.channels.UdpChannel;
import org.jboss.xnio.channels.MultipointReadResult;
import org.jboss.xnio.IoHandlerFactory;
import org.jboss.xnio.IoHandler;
import org.jboss.xnio.Buffers;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.test.support.LoggingHelper;
import java.net.SocketAddress;
import java.net.InetSocketAddress;
import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.Arrays;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 *
 */
public final class NioUdpTestCase extends TestCase {
    private static final int SERVER_PORT = 12345;
    private static final InetSocketAddress SERVER_SOCKET_ADDRESS;

    static {
        LoggingHelper.init();
        try {
            SERVER_SOCKET_ADDRESS = new InetSocketAddress(Inet4Address.getByAddress(new byte[] {127, 0, 0, 1}), SERVER_PORT);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    private synchronized void start(Lifecycle lifecycle) throws IOException {
        lifecycle.start();
    }

    private synchronized void stop(Lifecycle lifecycle) throws IOException {
        lifecycle.stop();
    }

    private void safeStop(Lifecycle lifecycle) {
        try {
            stop(lifecycle);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private void doServerSideTest(final boolean multicast, final IoHandler<UdpChannel> handler, final Runnable body) throws IOException {
        final Provider nioProvider = new NioProvider();
        start(nioProvider);
        try {
            doServerSidePart(multicast, handler, body, nioProvider);
            stop(nioProvider);
        } finally {
            safeStop(nioProvider);
        }
    }

    private void doServerSidePart(final boolean multicast, final IoHandler<UdpChannel> handler, final Runnable body, final Provider nioProvider) throws IOException {
        final InetSocketAddress bindAddress = SERVER_SOCKET_ADDRESS;
        doPart(multicast, handler, body, nioProvider, bindAddress);
    }

    private void doClientSidePart(final boolean multicast, final IoHandler<UdpChannel> handler, final Runnable body, final Provider nioProvider) throws IOException {
        final InetSocketAddress bindAddress = new InetSocketAddress(Inet4Address.getByAddress(new byte[] { 0, 0, 0, 0 }), 0);
        doPart(multicast, handler, body, nioProvider, bindAddress);
    }

    private void doPart(final boolean multicast, final IoHandler<UdpChannel> handler, final Runnable body, final Provider nioProvider, final InetSocketAddress bindAddress) throws IOException {
        final UdpServerService serverService = multicast ? nioProvider.createMulticastUdpServer() : nioProvider.createUdpServer();
        serverService.setBindAddresses(new SocketAddress[] { bindAddress });
        serverService.setHandlerFactory(new IoHandlerFactory<UdpChannel>() {
            public IoHandler<? super UdpChannel> createHandler() {
                return handler;
            }
        });
        start(serverService);
        try {
            body.run();
            stop(serverService);
        } finally {
            safeStop(serverService);
        }
    }

    private void doClientServerSide(final boolean clientMulticast, final boolean serverMulticast, final IoHandler<UdpChannel> serverHandler, final IoHandler<UdpChannel> clientHandler, final Runnable body) throws IOException {
        final Provider nioProvider = new NioProvider();
        start(nioProvider);
        try {
            doServerSidePart(serverMulticast, serverHandler, new Runnable() {
                public void run() {
                    try {
                        doClientSidePart(clientMulticast, clientHandler, body, nioProvider);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }, nioProvider);
            stop(nioProvider);
        } finally {
            safeStop(nioProvider);
        }
    }

    private void doServerCreate(boolean multicast) throws Exception {
        final AtomicBoolean openedOk = new AtomicBoolean(false);
        final AtomicBoolean closedOk = new AtomicBoolean(false);
        doServerSideTest(multicast, new IoHandler<UdpChannel>() {
            public void handleOpened(final UdpChannel channel) {
                openedOk.set(true);
            }

            public void handleReadable(final UdpChannel channel) {
            }

            public void handleWritable(final UdpChannel channel) {
            }

            public void handleClosed(final UdpChannel channel) {
                closedOk.set(true);
            }
        }, new Runnable() {
            public void run() {
            }
        });
        assertTrue(openedOk.get());
        assertTrue(closedOk.get());
    }

    public void testServerCreate() throws Exception {
        doServerCreate(false);
    }

    public void testServerCreateMulticast() throws Exception {
        doServerCreate(true);
    }

    private void doClientToServerTransmitTest(boolean clientMulticast, boolean serverMulticast) throws Exception {
        final AtomicBoolean clientOK = new AtomicBoolean(false);
        final AtomicBoolean serverOK = new AtomicBoolean(false);
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch receivedLatch = new CountDownLatch(1);
        final CountDownLatch doneLatch = new CountDownLatch(2);
        final byte[] payload = new byte[] { 10, 5, 15, 10, 100, -128, 30, 0, 0 };
        doClientServerSide(clientMulticast, serverMulticast, new IoHandler<UdpChannel>() {
            public void handleOpened(final UdpChannel channel) {
                channel.resumeReads();
                startLatch.countDown();
            }

            public void handleReadable(final UdpChannel channel) {
                try {
                    final ByteBuffer buffer = ByteBuffer.allocate(50);
                    final MultipointReadResult<SocketAddress> result = channel.receive(buffer);
                    if (result == null) {
                        channel.resumeReads();
                        return;
                    }
                    try {
                        final byte[] testPayload = new byte[payload.length];
                        Buffers.flip(buffer).get(testPayload);
                        assertTrue(Arrays.equals(testPayload, payload));
                        assertFalse(buffer.hasRemaining());
                        assertNotNull(result.getSourceAddress());
                        try {
                            channel.close();
                            serverOK.set(true);
                        } finally {
                            IoUtils.safeClose(channel);
                        }
                    } finally {
                        receivedLatch.countDown();
                        doneLatch.countDown();
                    }
                } catch (IOException e) {
                    IoUtils.safeClose(channel);
                    throw new RuntimeException(e);
                }
            }

            public void handleWritable(final UdpChannel channel) {
            }

            public void handleClosed(final UdpChannel channel) {
            }
        }, new IoHandler<UdpChannel>() {
            public void handleOpened(final UdpChannel channel) {
                try {
                    // wait until server is ready
                    assertTrue(startLatch.await(1500L, TimeUnit.MILLISECONDS));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                channel.resumeWrites();
            }

            public void handleReadable(final UdpChannel channel) {
            }

            public void handleWritable(final UdpChannel channel) {
                try {
                    if (! channel.send(SERVER_SOCKET_ADDRESS, ByteBuffer.wrap(payload))) {
                        channel.resumeWrites();
                    } else {
                        assertTrue(receivedLatch.await(1500L, TimeUnit.MILLISECONDS));
                        channel.close();
                        clientOK.set(true);
                        doneLatch.countDown();
                    }
                } catch (IOException e) {
                    IoUtils.safeClose(channel);
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            public void handleClosed(final UdpChannel channel) {
            }
        }, new Runnable() {
            public void run() {
                try {
                    assertTrue(doneLatch.await(1500L, TimeUnit.MILLISECONDS));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        assertTrue(clientOK.get());
        assertTrue(serverOK.get());
    }

    public void testClientToServerTransmitNioToNio() throws Exception {
        doClientToServerTransmitTest(false, false);
    }

    public void testClientToServerTransmitBioToNio() throws Exception {
        doClientToServerTransmitTest(true, false);
    }

    public void testClientToServerTransmitNioToBio() throws Exception {
        doClientToServerTransmitTest(false, true);
    }

    public void testClientToServerTransmitBioToBio() throws Exception {
        doClientToServerTransmitTest(true, true);
    }
}
