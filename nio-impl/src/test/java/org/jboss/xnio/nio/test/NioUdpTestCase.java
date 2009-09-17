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

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import junit.framework.TestCase;
import org.jboss.xnio.Buffers;
import org.jboss.xnio.IoHandler;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.Xnio;
import org.jboss.xnio.CloseableExecutor;
import org.jboss.xnio.UdpServer;
import org.jboss.xnio.OptionMap;
import org.jboss.xnio.log.Logger;
import org.jboss.xnio.channels.MultipointReadResult;
import org.jboss.xnio.channels.UdpChannel;
import org.jboss.xnio.channels.CommonOptions;
import org.jboss.xnio.nio.NioXnio;
import org.jboss.xnio.nio.NioXnioConfiguration;
import org.jboss.xnio.test.support.LoggingHelper;
import org.jboss.xnio.test.support.TestThreadFactory;

/**
 *
 */
public final class NioUdpTestCase extends TestCase {
    private static final int SERVER_PORT = 12345;
    private static final InetSocketAddress SERVER_SOCKET_ADDRESS;
    private static final InetSocketAddress CLIENT_SOCKET_ADDRESS;

    private static final Logger log = Logger.getLogger("TEST");

    private final TestThreadFactory threadFactory = new TestThreadFactory();

    static {
        LoggingHelper.init();
        try {
            SERVER_SOCKET_ADDRESS = new InetSocketAddress(Inet4Address.getByAddress(new byte[] {127, 0, 0, 1}), SERVER_PORT);
            CLIENT_SOCKET_ADDRESS = new InetSocketAddress(Inet4Address.getByAddress(new byte[] {127, 0, 0, 1}), 0);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    private synchronized void doServerSideTest(final boolean multicast, final IoHandler<UdpChannel> handler, final Runnable body) throws IOException {
        final CloseableExecutor closeableExecutor = IoUtils.closeableExecutor(new ThreadPoolExecutor(5, 5, 50L, TimeUnit.MILLISECONDS, new SynchronousQueue<Runnable>(), threadFactory), 5L, TimeUnit.SECONDS);
        try {
            final NioXnioConfiguration config = new NioXnioConfiguration();
            config.setSelectorThreadFactory(threadFactory);
            config.setExecutor(closeableExecutor);
            final Xnio xnio = NioXnio.create(config);
            try {
                doServerSidePart(multicast, handler, body, xnio);
                xnio.close();
            } finally {
                IoUtils.safeClose(xnio);
            }
        } finally {
            IoUtils.safeClose(closeableExecutor);
        }
    }

    private void doServerSidePart(final boolean multicast, final IoHandler<UdpChannel> handler, final Runnable body, final Xnio xnio) throws IOException {
        final InetSocketAddress bindAddress = SERVER_SOCKET_ADDRESS;
        doPart(multicast, handler, body, bindAddress, xnio);
    }

    private void doClientSidePart(final boolean multicast, final IoHandler<UdpChannel> handler, final Runnable body, final Xnio xnio) throws IOException {
        final InetSocketAddress bindAddress = CLIENT_SOCKET_ADDRESS;
        doPart(multicast, handler, body, bindAddress, xnio);
    }

    private synchronized void doPart(final boolean multicast, final IoHandler<UdpChannel> handler, final Runnable body, final InetSocketAddress bindAddress, final Xnio xnio) throws IOException {
        final UdpServer server = xnio.createUdpServer(IoUtils.singletonHandlerFactory(new CatchingHandler<UdpChannel>(handler, threadFactory)),
                OptionMap.builder().add(CommonOptions.MULTICAST, Boolean.valueOf(multicast)).getMap());
        server.bind(bindAddress).await();
        try {
            body.run();
            server.close();
        } finally {
            IoUtils.safeClose(server);
        }
    }

    private synchronized void doClientServerSide(final boolean clientMulticast, final boolean serverMulticast, final IoHandler<UdpChannel> serverHandler, final IoHandler<UdpChannel> clientHandler, final Runnable body) throws IOException {
        final CloseableExecutor closeableExecutor = IoUtils.closeableExecutor(new ThreadPoolExecutor(5, 5, 50L, TimeUnit.MILLISECONDS, new SynchronousQueue<Runnable>(), threadFactory), 5L, TimeUnit.SECONDS);
        try {
            final NioXnioConfiguration config = new NioXnioConfiguration();
            config.setSelectorThreadFactory(threadFactory);
            config.setExecutor(closeableExecutor);
            final Xnio xnio = NioXnio.create(config);
            try {
                doServerSidePart(serverMulticast, serverHandler, new Runnable() {
                    public void run() {
                        try {
                            doClientSidePart(clientMulticast, clientHandler, body, xnio);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }, xnio);
                xnio.close();
            } finally {
                IoUtils.safeClose(xnio);
            }
        } finally {
            IoUtils.safeClose(closeableExecutor);
        }
    }

    private void doServerCreate(boolean multicast) throws Exception {
        final AtomicBoolean openedOk = new AtomicBoolean(false);
        final AtomicBoolean closedOk = new AtomicBoolean(false);
        doServerSideTest(multicast, new IoHandler<UdpChannel>() {
            public void handleOpened(final UdpChannel channel) {
                log.info("In handleOpened for %s", channel);
                openedOk.set(true);
            }

            public void handleReadable(final UdpChannel channel) {
                log.info("In handleReadable for %s", channel);
            }

            public void handleWritable(final UdpChannel channel) {
                log.info("In handleWritable for %s", channel);
            }

            public void handleClosed(final UdpChannel channel) {
                log.info("In handleClosed for %s", channel);
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
        log.info("Test: testServerCreate");
        doServerCreate(false);
        threadFactory.await();
    }

    public void testServerCreateMulticast() throws Exception {
        log.info("Test: testServerCreateMulticast");
        doServerCreate(true);
        threadFactory.await();
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
                log.info("In handleOpened for %s", channel);
                channel.resumeReads();
                startLatch.countDown();
            }

            public void handleReadable(final UdpChannel channel) {
                log.info("In handleReadable for %s", channel);
                try {
                    final ByteBuffer buffer = ByteBuffer.allocate(50);
                    final MultipointReadResult<SocketAddress> result = channel.receive(buffer);
                    if (result == null) {
                        log.info("Whoops, spurious read notification for %s", channel);
                        channel.resumeReads();
                        return;
                    }
                    try {
                        final byte[] testPayload = new byte[payload.length];
                        Buffers.flip(buffer).get(testPayload);
                        log.info("We received the packet on %s", channel);
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
                log.info("In handleWritable for %s", channel);
            }

            public void handleClosed(final UdpChannel channel) {
                log.info("In handleClosed for %s", channel);
            }
        }, new IoHandler<UdpChannel>() {
            public void handleOpened(final UdpChannel channel) {
                log.info("In handleOpened for %s", channel);
                try {
                    // wait until server is ready
                    assertTrue(startLatch.await(500L, TimeUnit.MILLISECONDS));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                channel.resumeWrites();
            }

            public void handleReadable(final UdpChannel channel) {
                log.info("In handleReadable for %s", channel);
            }

            public void handleWritable(final UdpChannel channel) {
                log.info("In handleWritable for %s", channel);
                try {
                    if (clientOK.get()) {
                        log.info("Extra writable notification on %s (?!)", channel);
                    } else if (! channel.send(SERVER_SOCKET_ADDRESS, ByteBuffer.wrap(payload))) {
                        log.info("Whoops, spurious write notification for %s", channel);
                        channel.resumeWrites();
                    } else {
                        log.info("We sent the packet on %s", channel);
                        assertTrue(receivedLatch.await(500L, TimeUnit.MILLISECONDS));
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
                log.info("In handleClosed for %s", channel);
            }
        }, new Runnable() {
            public void run() {
                try {
                    assertTrue(doneLatch.await(500L, TimeUnit.MILLISECONDS));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        assertTrue(clientOK.get());
        assertTrue(serverOK.get());
    }

    public void testClientToServerTransmitNioToNio() throws Exception {
        log.info("Test: testClientToServerTransmitNioToNio");
        doClientToServerTransmitTest(false, false);
        threadFactory.await();
    }

    public void testClientToServerTransmitBioToNio() throws Exception {
        log.info("Test: testClientToServerTransmitBioToNio");
        doClientToServerTransmitTest(true, false);
        threadFactory.await();
    }

    public void testClientToServerTransmitNioToBio() throws Exception {
        log.info("Test: testClientToServerTransmitNioToBio");
        doClientToServerTransmitTest(false, true);
        threadFactory.await();
    }

    public void testClientToServerTransmitBioToBio() throws Exception {
        log.info("Test: testClientToServerTransmitBioToBio");
        doClientToServerTransmitTest(true, true);
        threadFactory.await();
    }
    
    //TODO public void testJmxUdpProperties() throws Exception {}
    
}
