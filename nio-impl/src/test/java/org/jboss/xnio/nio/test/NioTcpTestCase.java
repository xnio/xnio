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
import org.jboss.xnio.nio.NioProvider;
import org.jboss.xnio.nio.NioTcpServer;
import org.jboss.xnio.nio.NioTcpConnector;
import org.jboss.xnio.nio.NioTcpAcceptor;
import org.jboss.xnio.channels.ConnectedStreamChannel;
import org.jboss.xnio.channels.CommonOptions;
import org.jboss.xnio.channels.TcpChannel;
import org.jboss.xnio.IoFuture;
import org.jboss.xnio.IoHandler;
import org.jboss.xnio.IoHandlerFactory;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.FutureConnection;
import org.jboss.xnio.log.Logger;
import org.jboss.xnio.test.support.LoggingHelper;
import static org.jboss.xnio.Buffers.flip;
import static org.jboss.xnio.IoUtils.safeClose;
import static org.jboss.xnio.IoUtils.nullHandler;
import java.net.SocketAddress;
import java.net.InetSocketAddress;
import java.net.Inet4Address;
import java.nio.ByteBuffer;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 */
public final class NioTcpTestCase extends TestCase {
    static {
        LoggingHelper.init();
    }

    private static final Logger log = Logger.getLogger(NioTcpTestCase.class);

    private static final int SERVER_PORT = 12345;

    private void doConnectionTest(final Runnable body, final IoHandler<? super TcpChannel> clientHandler, final IoHandler<? super TcpChannel> serverHandler) throws Exception {
        synchronized (this) {
            final NioProvider provider = new NioProvider();
            provider.setConnectionSelectorThreads(2);
            provider.start();
            try {
                final NioTcpServer nioTcpServer = new NioTcpServer();
                nioTcpServer.setNioProvider(provider);
                nioTcpServer.setReuseAddress(true);
                nioTcpServer.setBindAddresses(new SocketAddress[] { new InetSocketAddress(Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 }), SERVER_PORT)});
                nioTcpServer.setHandlerFactory(new IoHandlerFactory<TcpChannel>() {
                    public IoHandler<? super TcpChannel> createHandler() {
                        return serverHandler;
                    }
                });
                nioTcpServer.start();
                try {
                    final NioTcpConnector nioTcpConnector = new NioTcpConnector();
                    nioTcpConnector.setNioProvider(provider);
                    nioTcpConnector.setConnectTimeout(10);
                    nioTcpConnector.start();
                    try {
                        final IoFuture<TcpChannel> ioFuture = nioTcpConnector.connectTo(new InetSocketAddress(Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 }), SERVER_PORT), clientHandler);
                        final TcpChannel channel = ioFuture.get();
                        try {
                            body.run();
                            channel.close();
                        } finally {
                            safeClose(channel);
                        }
                    } finally {
                        nioTcpConnector.stop();
                    }
                } finally {
                    nioTcpServer.stop();
                }
            } finally {
                provider.stop();
            }
        }
    }

    public void testTcpConnect() throws Exception {
        doConnectionTest(new Runnable() {
            public void run() {
            }
        }, nullHandler(), nullHandler());
    }

    public void testClientTcpClose() throws Exception {
        final CountDownLatch latch = new CountDownLatch(2);
        final AtomicBoolean clientOK = new AtomicBoolean(false);
        final AtomicBoolean serverOK = new AtomicBoolean(false);
        doConnectionTest(new Runnable() {
            public void run() {
                try {
                    assertTrue(latch.await(4200L, TimeUnit.MILLISECONDS));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }, new IoHandler<ConnectedStreamChannel<SocketAddress>>() {
            public void handleOpened(final ConnectedStreamChannel<SocketAddress> channel) {
                try {
                    channel.close();
                    clientOK.set(true);
                } catch (Throwable t) {
                    t.printStackTrace();
                    latch.countDown();
                    throw new RuntimeException(t);
                }
            }

            public void handleReadable(final ConnectedStreamChannel<SocketAddress> channel) {
            }

            public void handleWritable(final ConnectedStreamChannel<SocketAddress> channel) {
            }

            public void handleClosed(final ConnectedStreamChannel<SocketAddress> channel) {
                latch.countDown();
            }
        }, new IoHandler<ConnectedStreamChannel<SocketAddress>>() {
            public void handleOpened(final ConnectedStreamChannel<SocketAddress> channel) {
                try {
                    channel.resumeReads();
                } catch (Throwable t) {
                    t.printStackTrace();
                    try {
                        channel.close();
                    } catch (Throwable t2) {
                        t2.printStackTrace();
                        latch.countDown();
                        throw new RuntimeException(t);
                    }
                    throw new RuntimeException(t);
                }
            }

            public void handleReadable(final ConnectedStreamChannel<SocketAddress> channel) {
                try {
                    final int c = channel.read(ByteBuffer.allocate(100));
                    if (c == -1) {
                        serverOK.set(true);
                    }
                    channel.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            public void handleWritable(final ConnectedStreamChannel<SocketAddress> channel) {
            }

            public void handleClosed(final ConnectedStreamChannel<SocketAddress> channel) {
                latch.countDown();
            }
        });
        assertTrue(serverOK.get());
        assertTrue(clientOK.get());
    }

    public void testServerTcpClose() throws Exception {
        final CountDownLatch latch = new CountDownLatch(2);
        final AtomicBoolean clientOK = new AtomicBoolean(false);
        final AtomicBoolean serverOK = new AtomicBoolean(false);
        doConnectionTest(new Runnable() {
            public void run() {
                try {
                    assertTrue(latch.await(1200L, TimeUnit.MILLISECONDS));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }, new IoHandler<ConnectedStreamChannel<SocketAddress>>() {
            public void handleOpened(final ConnectedStreamChannel<SocketAddress> channel) {
                try {
                    channel.resumeReads();
                } catch (Throwable t) {
                    try {
                        channel.close();
                    } catch (Throwable t2) {
                        t.printStackTrace();
                        latch.countDown();
                        throw new RuntimeException(t);
                    }
                    throw new RuntimeException(t);
                }
            }

            public void handleReadable(final ConnectedStreamChannel<SocketAddress> channel) {
                try {
                    final int c = channel.read(ByteBuffer.allocate(100));
                    if (c == -1) {
                        clientOK.set(true);
                    }
                    channel.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            public void handleWritable(final ConnectedStreamChannel<SocketAddress> channel) {
            }

            public void handleClosed(final ConnectedStreamChannel<SocketAddress> channel) {
                latch.countDown();
            }
        }, new IoHandler<ConnectedStreamChannel<SocketAddress>>() {
            public void handleOpened(final ConnectedStreamChannel<SocketAddress> channel) {
                try {
                    channel.close();
                } catch (Throwable t) {
                    t.printStackTrace();
                    latch.countDown();
                    throw new RuntimeException(t);
                }
            }

            public void handleReadable(final ConnectedStreamChannel<SocketAddress> channel) {
            }

            public void handleWritable(final ConnectedStreamChannel<SocketAddress> channel) {
            }

            public void handleClosed(final ConnectedStreamChannel<SocketAddress> channel) {
                serverOK.set(true);
                latch.countDown();
            }
        });
        assertTrue(serverOK.get());
        assertTrue(clientOK.get());
    }

    public void testTwoWayTransfer() throws Exception {
        final CountDownLatch latch = new CountDownLatch(2);
        final AtomicInteger clientSent = new AtomicInteger(0);
        final AtomicInteger clientReceived = new AtomicInteger(0);
        final AtomicInteger serverSent = new AtomicInteger(0);
        final AtomicInteger serverReceived = new AtomicInteger(0);
        final AtomicBoolean delayClientStop = new AtomicBoolean();
        final AtomicBoolean delayServerStop = new AtomicBoolean();
        doConnectionTest(new Runnable() {
            public void run() {
                try {
                    assertTrue(latch.await(1200L, TimeUnit.MILLISECONDS));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }, new IoHandler<ConnectedStreamChannel<SocketAddress>>() {
            public void handleOpened(final ConnectedStreamChannel<SocketAddress> channel) {
                channel.resumeReads();
                channel.resumeWrites();
            }

            public void handleReadable(final ConnectedStreamChannel<SocketAddress> channel) {
                try {
                    final int c = channel.read(ByteBuffer.allocate(100));
                    if (c == -1) {
                        if (delayClientStop.getAndSet(true)) {
                            channel.close();
                        }
                    } else {
                        clientReceived.addAndGet(c);
                        channel.resumeReads();
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                    throw new RuntimeException(t);
                }
            }

            public void handleWritable(final ConnectedStreamChannel<SocketAddress> channel) {
                try {
                    final ByteBuffer buffer = ByteBuffer.allocate(100);
                    buffer.put("This Is A Test\r\n".getBytes("UTF-8"));
                    final int c = channel.write(flip(buffer));
                    if (clientSent.addAndGet(c) > 1000) {
                        channel.shutdownWrites();
                        if (delayClientStop.getAndSet(true)) {
                            channel.close();
                        }
                    } else {
                        channel.resumeWrites();
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                    throw new RuntimeException(t);
                }
            }

            public void handleClosed(final ConnectedStreamChannel<SocketAddress> channel) {
                latch.countDown();
            }
        }, new IoHandler<ConnectedStreamChannel<SocketAddress>>() {
            public void handleOpened(final ConnectedStreamChannel<SocketAddress> channel) {
                channel.resumeReads();
                channel.resumeWrites();
            }

            public void handleReadable(final ConnectedStreamChannel<SocketAddress> channel) {
                try {
                    final int c = channel.read(ByteBuffer.allocate(100));
                    if (c == -1) {
                        if (delayServerStop.getAndSet(true)) {
                            channel.close();
                        }
                    } else {
                        serverReceived.addAndGet(c);
                        channel.resumeReads();
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                    throw new RuntimeException(t);
                }
            }

            public void handleWritable(final ConnectedStreamChannel<SocketAddress> channel) {
                try {
                    final ByteBuffer buffer = ByteBuffer.allocate(100);
                    buffer.put("This Is A Test Gumma\r\n".getBytes("UTF-8"));
                    final int c = channel.write(flip(buffer));
                    if (serverSent.addAndGet(c) > 1000) {
                        channel.shutdownWrites();
                        if (delayServerStop.getAndSet(true)) {
                            channel.close();
                        }
                    } else {
                        channel.resumeWrites();
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                    throw new RuntimeException(t);
                }
            }

            public void handleClosed(final ConnectedStreamChannel<SocketAddress> channel) {
                latch.countDown();
            }
        });
        assertEquals(serverSent.get(), clientReceived.get());
        assertEquals(clientSent.get(), serverReceived.get());
    }

    //TODO public void testJmxTcpCounters() throws Exception {}
    
    public void testClientTcpNastyClose() throws Exception {
        final CountDownLatch latch = new CountDownLatch(2);
        final AtomicBoolean clientOK = new AtomicBoolean(false);
        final AtomicBoolean serverOK = new AtomicBoolean(false);
        doConnectionTest(new Runnable() {
            public void run() {
                try {
                    assertTrue(latch.await(4200L, TimeUnit.MILLISECONDS));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }, new IoHandler<ConnectedStreamChannel<SocketAddress>>() {
            public void handleOpened(final ConnectedStreamChannel<SocketAddress> channel) {
                try {
                    channel.setOption(CommonOptions.CLOSE_ABORT, Boolean.TRUE);
                    channel.close();
                    clientOK.set(true);
                } catch (Throwable t) {
                    t.printStackTrace();
                    latch.countDown();
                    throw new RuntimeException(t);
                }
            }

            public void handleReadable(final ConnectedStreamChannel<SocketAddress> channel) {
            }

            public void handleWritable(final ConnectedStreamChannel<SocketAddress> channel) {
            }

            public void handleClosed(final ConnectedStreamChannel<SocketAddress> channel) {
                latch.countDown();
            }
        }, new IoHandler<ConnectedStreamChannel<SocketAddress>>() {
            public void handleOpened(final ConnectedStreamChannel<SocketAddress> channel) {
                try {
                    channel.resumeReads();
                } catch (Throwable t) {
                    t.printStackTrace();
                    latch.countDown();
                    throw new RuntimeException(t);
                }
            }

            public void handleReadable(final ConnectedStreamChannel<SocketAddress> channel) {
                try {
                    channel.read(ByteBuffer.allocate(100));
                } catch (IOException e) {
                    if (e.getMessage().contains("reset")) {
                        serverOK.set(true);
                    } else {
                        throw new RuntimeException(e);
                    }
                } finally {
                    IoUtils.safeClose(channel);
                }
            }

            public void handleWritable(final ConnectedStreamChannel<SocketAddress> channel) {
            }

            public void handleClosed(final ConnectedStreamChannel<SocketAddress> channel) {
                latch.countDown();
            }
        });
        assertTrue(serverOK.get());
        assertTrue(clientOK.get());
    }

    public void testServerTcpNastyClose() throws Exception {
        final CountDownLatch latch = new CountDownLatch(2);
        final AtomicBoolean clientOK = new AtomicBoolean(false);
        final AtomicBoolean serverOK = new AtomicBoolean(false);
        final CountDownLatch serverLatch = new CountDownLatch(1);
        doConnectionTest(new Runnable() {
            public void run() {
                try {
                    assertTrue(latch.await(4200L, TimeUnit.MILLISECONDS));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }, new IoHandler<ConnectedStreamChannel<SocketAddress>>() {
            public void handleOpened(final ConnectedStreamChannel<SocketAddress> channel) {
                try {
                    log.info("Client opened");
                    serverLatch.countDown();
                    channel.resumeReads();
                } catch (Throwable t) {
                    t.printStackTrace();
                    try {
                        channel.close();
                    } catch (Throwable t2) {
                        t2.printStackTrace();
                        latch.countDown();
                        throw new RuntimeException(t);
                    }
                    throw new RuntimeException(t);
                }
            }

            public void handleReadable(final ConnectedStreamChannel<SocketAddress> channel) {
                try {
                    channel.read(ByteBuffer.allocate(100));
                    channel.close();
                } catch (IOException e) {
                    if (e.getMessage().contains("reset")) {
                        clientOK.set(true);
                    } else {
                        throw new RuntimeException(e);
                    }
                } finally {
                    IoUtils.safeClose(channel);
                }
            }

            public void handleWritable(final ConnectedStreamChannel<SocketAddress> channel) {
            }

            public void handleClosed(final ConnectedStreamChannel<SocketAddress> channel) {
                latch.countDown();
            }
        }, new IoHandler<ConnectedStreamChannel<SocketAddress>>() {
            public void handleOpened(final ConnectedStreamChannel<SocketAddress> channel) {
                try {
                    log.info("Server opened");
                    serverLatch.await(3000L, TimeUnit.MILLISECONDS);
                    channel.setOption(CommonOptions.CLOSE_ABORT, Boolean.TRUE);
                    channel.close();
                    serverOK.set(true);
                } catch (Throwable t) {
                    t.printStackTrace();
                    latch.countDown();
                    throw new RuntimeException(t);
                }
            }

            public void handleReadable(final ConnectedStreamChannel<SocketAddress> channel) {
            }

            public void handleWritable(final ConnectedStreamChannel<SocketAddress> channel) {
            }

            public void handleClosed(final ConnectedStreamChannel<SocketAddress> channel) {
                latch.countDown();
            }
        });
        assertTrue(serverOK.get());
        assertTrue(clientOK.get());
    }

    public void testAcceptor() throws Exception {
        final CountDownLatch readLatch = new CountDownLatch(2);
        final CountDownLatch closeLatch = new CountDownLatch(2);
        final AtomicBoolean clientOpened = new AtomicBoolean();
        final AtomicBoolean clientReadOnceOK = new AtomicBoolean();
        final AtomicBoolean clientReadDoneOK = new AtomicBoolean();
        final AtomicBoolean clientReadTooMuch = new AtomicBoolean();
        final AtomicBoolean clientWriteOK = new AtomicBoolean();
        final AtomicBoolean serverOpened = new AtomicBoolean();
        final AtomicBoolean serverReadOnceOK = new AtomicBoolean();
        final AtomicBoolean serverReadDoneOK = new AtomicBoolean();
        final AtomicBoolean serverReadTooMuch = new AtomicBoolean();
        final AtomicBoolean serverWriteOK = new AtomicBoolean();
        final byte[] bytes = "Ummagumma!".getBytes("UTF-8");
        synchronized (this) {
            final NioProvider provider = new NioProvider();
            provider.start();
            try {
                final NioTcpAcceptor nioTcpAcceptor = new NioTcpAcceptor();
                nioTcpAcceptor.setNioProvider(provider);
                nioTcpAcceptor.setReuseAddress(true);
                nioTcpAcceptor.start();
                final FutureConnection<SocketAddress,TcpChannel> futureConnection = nioTcpAcceptor.acceptTo(new InetSocketAddress(Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 }), 0), new IoHandler<TcpChannel>() {
                    private final ByteBuffer inboundBuf = ByteBuffer.allocate(512);
                    private int readCnt = 0;
                    private final ByteBuffer outboundBuf = ByteBuffer.wrap(bytes);

                    public void handleOpened(final TcpChannel channel) {
                        channel.resumeReads();
                        channel.resumeWrites();
                        serverOpened.set(true);
                    }

                    public void handleReadable(final TcpChannel channel) {
                        try {
                            final int res = channel.read(inboundBuf);
                            if (res == 0) {
                                channel.resumeReads();
                            } else if (res == -1) {
                                serverReadDoneOK.set(true);
                                readLatch.countDown();
                            } else {
                                final int ttl = readCnt += res;
                                if (ttl == bytes.length) {
                                    serverReadOnceOK.set(true);
                                } else if (ttl > bytes.length) {
                                    serverReadTooMuch.set(true);
                                    IoUtils.safeClose(channel);
                                    return;
                                }
                                channel.resumeReads();
                            }
                        } catch (IOException e) {
                            log.error(e, "Server read failed");
                            IoUtils.safeClose(channel);
                        }
                    }

                    public void handleWritable(final TcpChannel channel) {
                        try {
                            channel.write(outboundBuf);
                            if (! outboundBuf.hasRemaining()) {
                                serverWriteOK.set(true);
                                channel.shutdownWrites();
                            }
                        } catch (IOException e) {
                            log.error(e, "Server write failed");
                            IoUtils.safeClose(channel);
                        }
                    }

                    public void handleClosed(final TcpChannel channel) {
                        closeLatch.countDown();
                    }
                });
                final SocketAddress localAddress = futureConnection.getLocalAddress();
                System.out.println("Connecting to... " + localAddress);
                try {
                    final NioTcpConnector nioTcpConnector = new NioTcpConnector();
                    nioTcpConnector.setNioProvider(provider);
                    nioTcpConnector.setConnectTimeout(10);
                    nioTcpConnector.start();
                    try {
                        final IoFuture<TcpChannel> ioFuture = nioTcpConnector.connectTo(localAddress, new IoHandler<TcpChannel>() {
                            private final ByteBuffer inboundBuf = ByteBuffer.allocate(512);
                            private int readCnt = 0;
                            private final ByteBuffer outboundBuf = ByteBuffer.wrap(bytes);

                            public void handleOpened(final TcpChannel channel) {
                                channel.resumeReads();
                                channel.resumeWrites();
                                clientOpened.set(true);
                            }

                            public void handleReadable(final TcpChannel channel) {
                                try {
                                    final int res = channel.read(inboundBuf);
                                    if (res == 0) {
                                        channel.resumeReads();
                                    } else if (res == -1) {
                                        clientReadDoneOK.set(true);
                                        readLatch.countDown();
                                    } else {
                                        final int ttl = readCnt += res;
                                        if (ttl == bytes.length) {
                                            clientReadOnceOK.set(true);
                                        } else if (ttl > bytes.length) {
                                            clientReadTooMuch.set(true);
                                            IoUtils.safeClose(channel);
                                            return;
                                        }
                                        channel.resumeReads();
                                    }
                                } catch (IOException e) {
                                    log.error(e, "Client read failed");
                                    IoUtils.safeClose(channel);
                                }
                            }

                            public void handleWritable(final TcpChannel channel) {
                                try {
                                    channel.write(outboundBuf);
                                    if (! outboundBuf.hasRemaining()) {
                                        clientWriteOK.set(true);
                                        channel.shutdownWrites();
                                    }
                                } catch (IOException e) {
                                    log.error(e, "Client write failed");
                                    IoUtils.safeClose(channel);
                                }
                            }

                            public void handleClosed(final TcpChannel channel) {
                                closeLatch.countDown();
                            }
                        });
                        assertTrue("Read timed out", readLatch.await(500L, TimeUnit.MILLISECONDS));
                        final TcpChannel clientChannel = ioFuture.get();
                        final TcpChannel serverChannel = futureConnection.get();
                        clientChannel.close();
                        serverChannel.close();
                        assertTrue("Close timed out", closeLatch.await(500L, TimeUnit.MILLISECONDS));
                        assertFalse("Client read too much", clientReadTooMuch.get());
                        assertTrue("Client read OK", clientReadOnceOK.get());
                        assertTrue("Client read done", clientReadDoneOK.get());
                        assertTrue("Client write OK", clientWriteOK.get());
                        assertFalse("Server read too much", serverReadTooMuch.get());
                        assertTrue("Server read OK", serverReadOnceOK.get());
                        assertTrue("Server read done", serverReadDoneOK.get());
                        assertTrue("Server write OK", serverWriteOK.get());
                    } finally {
                        nioTcpConnector.stop();
                    }
                } finally {
                    futureConnection.cancel();
                    nioTcpAcceptor.stop();
                }
            } finally {
                provider.stop();
            }
        }
    }
}
