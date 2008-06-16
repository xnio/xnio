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

package org.jboss.xnio.core.nio.test;

import junit.framework.TestCase;
import org.jboss.xnio.spi.Provider;
import org.jboss.xnio.spi.TcpServer;
import org.jboss.xnio.spi.Lifecycle;
import org.jboss.xnio.spi.TcpConnector;
import org.jboss.xnio.core.nio.NioProvider;
import org.jboss.xnio.channels.ConnectedStreamChannel;
import org.jboss.xnio.channels.ChannelOption;
import org.jboss.xnio.IoFuture;
import org.jboss.xnio.IoHandler;
import org.jboss.xnio.IoHandlerFactory;
import org.jboss.xnio.IoUtils;
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

    private static final int SERVER_PORT = 12345;

    private static final void stop(Lifecycle lifecycle) {
        try {
            lifecycle.stop();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private void doConnectionTest(final Runnable body, final IoHandler<? super ConnectedStreamChannel<SocketAddress>> clientHandler, final IoHandler<? super ConnectedStreamChannel<SocketAddress>> serverHandler) throws Exception {
        synchronized (this) {
            final Provider provider = new NioProvider();
            provider.start();
            try {
                final TcpServer tcpServer = provider.createTcpServer();
                tcpServer.setReuseAddress(true);
                tcpServer.setBindAddresses(new SocketAddress[] { new InetSocketAddress(Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 }), SERVER_PORT)});
                tcpServer.setHandlerFactory(new IoHandlerFactory<ConnectedStreamChannel<SocketAddress>>() {
                    public IoHandler<? super ConnectedStreamChannel<SocketAddress>> createHandler() {
                        return serverHandler;
                    }
                });
                tcpServer.start();
                try {
                    final TcpConnector connector = provider.createTcpConnector();
                    connector.setConnectTimeout(10);
                    connector.start();
                    try {
                        final IoFuture<ConnectedStreamChannel<SocketAddress>> ioFuture = connector.connectTo(new InetSocketAddress(Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 }), SERVER_PORT), clientHandler);
                        final ConnectedStreamChannel<SocketAddress> connectedStreamChannel = ioFuture.get();
                        try {
                            body.run();
                            connectedStreamChannel.close();
                        } finally {
                            safeClose(connectedStreamChannel);
                        }
                    } finally {
                        stop(connector);
                    }
                } finally {
                    stop(tcpServer);
                }
            } finally {
                stop(provider);
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
                    channel.setOption(ChannelOption.CLOSE_ABORT, Boolean.TRUE);
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
                    channel.read(ByteBuffer.allocate(100));
                    channel.close();
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
                    serverLatch.await(3000L, TimeUnit.MILLISECONDS);
                    channel.setOption(ChannelOption.CLOSE_ABORT, Boolean.TRUE);
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
}
