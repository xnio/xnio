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
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import junit.framework.TestCase;
import static org.jboss.xnio.Buffers.flip;
import org.jboss.xnio.IoFuture;
import org.jboss.xnio.IoUtils;
import static org.jboss.xnio.IoUtils.safeClose;
import org.jboss.xnio.TcpAcceptor;
import org.jboss.xnio.TcpConnector;
import org.jboss.xnio.Xnio;
import org.jboss.xnio.TcpServer;
import org.jboss.xnio.OptionMap;
import org.jboss.xnio.ChannelListener;
import org.jboss.xnio.Options;
import org.jboss.xnio.XnioConfiguration;
import org.jboss.xnio.FutureResult;
import org.jboss.xnio.channels.TcpChannel;
import org.jboss.xnio.channels.BoundChannel;
import org.jboss.xnio.log.Logger;
import org.jboss.xnio.nio.NioXnio;
import org.jboss.xnio.test.support.LoggingHelper;
import org.jboss.xnio.test.support.TestThreadFactory;

/**
 *
 */
public final class NioTcpTestCase extends TestCase {
    static {
        LoggingHelper.init();
    }

    private static final Logger log = Logger.getLogger("TEST");

    private final TestThreadFactory threadFactory = new TestThreadFactory();

    private static final int SERVER_PORT = 12345;

    private void doConnectionTest(final Runnable body, final ChannelListener<? super TcpChannel> clientHandler, final ChannelListener<? super TcpChannel> serverHandler) throws Exception {
        final XnioConfiguration conf = new XnioConfiguration();
        conf.setThreadFactory(threadFactory);
        conf.setExecutor(new ThreadPoolExecutor(4, 10, 1000L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(50)));
        Xnio xnio = Xnio.create("nio", conf);
        try {
            final TcpServer server  = xnio.createTcpServer(new CatchingChannelListener<TcpChannel>(serverHandler, threadFactory),
                    OptionMap.builder().set(Options.REUSE_ADDRESSES, Boolean.TRUE).getMap());
            try {
                server.bind(new InetSocketAddress(Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 }), SERVER_PORT)).await();
                final TcpConnector connector = xnio.createTcpConnector(OptionMap.EMPTY);
                final IoFuture<TcpChannel> ioFuture = connector.connectTo(new InetSocketAddress(Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 }), SERVER_PORT), new CatchingChannelListener<TcpChannel>(clientHandler, threadFactory), null);
                final TcpChannel channel = ioFuture.get();
                try {
                    body.run();
                    channel.close();
                    server.close();
                    xnio.close();
                } finally {
                    safeClose(channel);
                }
            } finally {
                IoUtils.safeClose(server);
            }
        } finally {
            IoUtils.safeClose(xnio);
        }
    }

    public void testTcpConnect() throws Exception {
        threadFactory.clear();
        log.info("Test: testTcpConnect");
        doConnectionTest(new Runnable() {
            public void run() {
            }
        }, null, null);
        threadFactory.await();
    }

    public void testClientTcpClose() throws Exception {
        threadFactory.clear();
        log.info("Test: testClientTcpClose");
        final CountDownLatch latch = new CountDownLatch(2);
        final AtomicBoolean clientOK = new AtomicBoolean(false);
        final AtomicBoolean serverOK = new AtomicBoolean(false);
        doConnectionTest(new Runnable() {
            public void run() {
                try {
                    assertTrue(latch.await(500L, TimeUnit.MILLISECONDS));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }, new ChannelListener<TcpChannel>() {
            public void handleEvent(final TcpChannel channel) {
                log.info("In client open");
                try {
                    channel.close();
                    channel.getCloseSetter().set(new ChannelListener<TcpChannel>() {
                        public void handleEvent(final TcpChannel channel) {
                            log.info("In client close");
                            latch.countDown();
                        }
                    });
                    clientOK.set(true);
                } catch (Throwable t) {
                    t.printStackTrace();
                    latch.countDown();
                    throw new RuntimeException(t);
                }
            }
        }, new ChannelListener<TcpChannel>() {
            public void handleEvent(final TcpChannel channel) {
                log.info("In server opened");
                channel.getCloseSetter().set(new ChannelListener<TcpChannel>() {
                    public void handleEvent(final TcpChannel channel) {
                        log.info("In client close");
                        latch.countDown();
                    }
                });
                channel.getReadSetter().set(new ChannelListener<TcpChannel>() {
                    public void handleEvent(final TcpChannel channel) {
                        log.info("In server readable");
                        try {
                            final int c = channel.read(ByteBuffer.allocate(100));
                            if (c == -1) {
                                serverOK.set(true);
                            }
                            channel.close();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        latch.countDown();
                    }
                });
                channel.resumeReads();
            }
        });
        assertTrue(serverOK.get());
        assertTrue(clientOK.get());
        threadFactory.await();
    }

    public void testServerTcpClose() throws Exception {
        threadFactory.clear();
        log.info("Test: testServerTcpClose");
        final CountDownLatch latch = new CountDownLatch(2);
        final AtomicBoolean clientOK = new AtomicBoolean(false);
        final AtomicBoolean serverOK = new AtomicBoolean(false);
        doConnectionTest(new Runnable() {
            public void run() {
                try {
                    assertTrue(latch.await(500L, TimeUnit.MILLISECONDS));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }, new ChannelListener<TcpChannel>() {
            public void handleEvent(final TcpChannel channel) {
                try {
                    channel.getCloseSetter().set(new ChannelListener<TcpChannel>() {
                        public void handleEvent(final TcpChannel channel) {
                            latch.countDown();
                        }
                    });
                    channel.getReadSetter().set(new ChannelListener<TcpChannel>() {
                        public void handleEvent(final TcpChannel channel) {
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
                    });
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
        }, new ChannelListener<TcpChannel>() {
            public void handleEvent(final TcpChannel channel) {
                try {
                    channel.getCloseSetter().set(new ChannelListener<TcpChannel>() {
                        public void handleEvent(final TcpChannel channel) {
                            serverOK.set(true);
                            latch.countDown();
                        }
                    });
                    channel.close();
                } catch (Throwable t) {
                    t.printStackTrace();
                    latch.countDown();
                    throw new RuntimeException(t);
                }
            }
        });
        assertTrue(serverOK.get());
        assertTrue(clientOK.get());
        threadFactory.await();
    }

    public void testTwoWayTransfer() throws Exception {
        threadFactory.clear();
        log.info("Test: testTwoWayTransfer");
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
                    assertTrue(latch.await(500L, TimeUnit.MILLISECONDS));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }, new ChannelListener<TcpChannel>() {
            public void handleEvent(final TcpChannel channel) {
                channel.getCloseSetter().set(new ChannelListener<TcpChannel>() {
                    public void handleEvent(final TcpChannel channel) {
                        latch.countDown();
                    }
                });
                channel.getReadSetter().set(new ChannelListener<TcpChannel>() {
                    public void handleEvent(final TcpChannel channel) {
                        try {
                            int c;
                            while ((c = channel.read(ByteBuffer.allocate(100))) > 0) {
                                clientReceived.addAndGet(c);
                            }
                            if (c == -1) {
                                if (delayClientStop.getAndSet(true)) {
                                    channel.close();
                                }
                            } else {
                                channel.resumeReads();
                            }
                        } catch (Throwable t) {
                            t.printStackTrace();
                            throw new RuntimeException(t);
                        }
                    }
                });
                channel.getWriteSetter().set(new ChannelListener<TcpChannel>() {
                    public void handleEvent(final TcpChannel channel) {
                        try {
                            final ByteBuffer buffer = ByteBuffer.allocate(100);
                            buffer.put("This Is A Test\r\n".getBytes("UTF-8")).flip();
                            int c;
                            while ((c = channel.write(buffer)) > 0) {
                                if (clientSent.addAndGet(c) > 1000) {
                                    channel.shutdownWrites();
                                    if (delayClientStop.getAndSet(true)) {
                                        channel.close();
                                    }
                                    return;
                                }
                                buffer.rewind();
                            }
                            channel.resumeWrites();
                        } catch (Throwable t) {
                            t.printStackTrace();
                            throw new RuntimeException(t);
                        }
                    }
                });

                channel.resumeReads();
                channel.resumeWrites();
            }
        }, new ChannelListener<TcpChannel>() {
            public void handleEvent(final TcpChannel channel) {
                channel.getCloseSetter().set(new ChannelListener<TcpChannel>() {
                    public void handleEvent(final TcpChannel channel) {
                        latch.countDown();
                    }
                });
                channel.getReadSetter().set(new ChannelListener<TcpChannel>() {
                    public void handleEvent(final TcpChannel channel) {
                        try {
                            int c;
                            while ((c = channel.read(ByteBuffer.allocate(100))) > 0) {
                                serverReceived.addAndGet(c);
                            }
                            if (c == -1) {
                                if (delayServerStop.getAndSet(true)) {
                                    channel.close();
                                }
                            } else {
                                channel.resumeReads();
                            }
                        } catch (Throwable t) {
                            t.printStackTrace();
                            throw new RuntimeException(t);
                        }
                    }
                });
                channel.getWriteSetter().set(new ChannelListener<TcpChannel>() {
                    public void handleEvent(final TcpChannel channel) {
                        try {
                            final ByteBuffer buffer = ByteBuffer.allocate(100);
                            buffer.put("This Is A Test Gumma\r\n".getBytes("UTF-8")).flip();
                            int c;
                            while ((c = channel.write(buffer)) > 0) {
                                if (serverSent.addAndGet(c) > 1000) {
                                    channel.shutdownWrites();
                                    if (delayServerStop.getAndSet(true)) {
                                        channel.close();
                                    }
                                    return;
                                }
                                buffer.rewind();
                            }
                            channel.resumeWrites();
                        } catch (Throwable t) {
                            t.printStackTrace();
                            throw new RuntimeException(t);
                        }
                    }
                });
                channel.resumeReads();
                channel.resumeWrites();
            }
        });
        assertEquals(serverSent.get(), clientReceived.get());
        assertEquals(clientSent.get(), serverReceived.get());
        threadFactory.await();
    }

    public void testClientTcpNastyClose() throws Exception {
        threadFactory.clear();
        log.info("Test: testClientTcpNastyClose");
        final CountDownLatch latch = new CountDownLatch(2);
        final AtomicBoolean clientOK = new AtomicBoolean(false);
        final AtomicBoolean serverOK = new AtomicBoolean(false);
        doConnectionTest(new Runnable() {
            public void run() {
                try {
                    assertTrue(latch.await(500L, TimeUnit.MILLISECONDS));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }, new ChannelListener<TcpChannel>() {
            public void handleEvent(final TcpChannel channel) {
                try {
                    channel.getCloseSetter().set(new ChannelListener<TcpChannel>() {
                        public void handleEvent(final TcpChannel channel) {
                            latch.countDown();
                        }
                    });
                    channel.setOption(Options.CLOSE_ABORT, Boolean.TRUE);
                    channel.close();
                    clientOK.set(true);
                } catch (Throwable t) {
                    t.printStackTrace();
                    latch.countDown();
                    throw new RuntimeException(t);
                }
            }
        }, new ChannelListener<TcpChannel>() {
            public void handleEvent(final TcpChannel channel) {
                try {
                    channel.getCloseSetter().set(new ChannelListener<TcpChannel>() {
                        public void handleEvent(final TcpChannel channel) {
                            latch.countDown();
                        }
                    });
                    channel.getReadSetter().set(new ChannelListener<TcpChannel>() {
                        public void handleEvent(final TcpChannel channel) {
                            try {
                                channel.read(ByteBuffer.allocate(100));
                            } catch (IOException e) {
                                if (e.getMessage().toLowerCase().contains("reset")) {
                                    serverOK.set(true);
                                } else {
                                    throw new RuntimeException(e);
                                }
                            } finally {
                                IoUtils.safeClose(channel);
                            }
                        }
                    });
                    channel.resumeReads();
                } catch (Throwable t) {
                    t.printStackTrace();
                    latch.countDown();
                    throw new RuntimeException(t);
                }
            }
        });
        assertTrue(serverOK.get());
        assertTrue(clientOK.get());
        threadFactory.await();
    }

    public void testServerTcpNastyClose() throws Exception {
        threadFactory.clear();
        log.info("Test: testServerTcpNastyClose");
        final CountDownLatch latch = new CountDownLatch(2);
        final AtomicBoolean clientOK = new AtomicBoolean(false);
        final AtomicBoolean serverOK = new AtomicBoolean(false);
        final CountDownLatch serverLatch = new CountDownLatch(1);
        doConnectionTest(new Runnable() {
            public void run() {
                try {
                    assertTrue(latch.await(500L, TimeUnit.MILLISECONDS));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }, new ChannelListener<TcpChannel>() {
            public void handleEvent(final TcpChannel channel) {
                try {
                    log.info("Client opened");
                    channel.getCloseSetter().set(new ChannelListener<TcpChannel>() {
                        public void handleEvent(final TcpChannel channel) {
                            latch.countDown();
                        }
                    });
                    channel.getReadSetter().set(new ChannelListener<TcpChannel>() {
                        public void handleEvent(final TcpChannel channel) {
                            try {
                                channel.read(ByteBuffer.allocate(100));
                                channel.close();
                            } catch (IOException e) {
                                if (e.getMessage().toLowerCase().contains("reset")) {
                                    clientOK.set(true);
                                } else {
                                    throw new RuntimeException(e);
                                }
                            } finally {
                                IoUtils.safeClose(channel);
                            }
                        }
                    });
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
        }, new ChannelListener<TcpChannel>() {
            public void handleEvent(final TcpChannel channel) {
                try {
                    log.info("Server opened");
                    channel.getCloseSetter().set(new ChannelListener<TcpChannel>() {
                        public void handleEvent(final TcpChannel channel) {
                            latch.countDown();
                        }
                    });
                    serverLatch.await(500L, TimeUnit.MILLISECONDS);
                    channel.setOption(Options.CLOSE_ABORT, Boolean.TRUE);
                    channel.close();
                    serverOK.set(true);
                } catch (Throwable t) {
                    t.printStackTrace();
                    latch.countDown();
                    throw new RuntimeException(t);
                }
            }
        });
        assertTrue(serverOK.get());
        assertTrue(clientOK.get());
        threadFactory.await();
    }

    public void testAcceptor() throws Exception {
        threadFactory.clear();
        log.info("Test: testAcceptor");
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
        final Xnio xnio = NioXnio.create();
        try {
            final TcpAcceptor acceptor = xnio.createTcpAcceptor(OptionMap.builder().set(Options.REUSE_ADDRESSES, Boolean.TRUE).getMap());
            final FutureResult<InetSocketAddress> futureAddressResult = new FutureResult<InetSocketAddress>();
            final IoFuture<InetSocketAddress> futureAddress = futureAddressResult.getIoFuture();
            final IoFuture<TcpChannel> futureConnection = acceptor.acceptTo(new InetSocketAddress(Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 }), 0), new ChannelListener<TcpChannel>() {
                private final ByteBuffer inboundBuf = ByteBuffer.allocate(512);
                private int readCnt = 0;
                private final ByteBuffer outboundBuf = ByteBuffer.wrap(bytes);

                public void handleEvent(final TcpChannel channel) {
                    channel.getCloseSetter().set(new ChannelListener<TcpChannel>() {
                        public void handleEvent(final TcpChannel channel) {
                            closeLatch.countDown();
                        }
                    });
                    channel.getReadSetter().set(new ChannelListener<TcpChannel>() {
                        public void handleEvent(final TcpChannel channel) {
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
                    });
                    channel.getWriteSetter().set(new ChannelListener<TcpChannel>() {
                        public void handleEvent(final TcpChannel channel) {
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
                    });
                    channel.resumeReads();
                    channel.resumeWrites();
                    serverOpened.set(true);
                }
            }, new ChannelListener<BoundChannel<InetSocketAddress>>() {
                public void handleEvent(final BoundChannel<InetSocketAddress> channel) {
                    futureAddressResult.setResult(channel.getLocalAddress());
                }
            });
            final InetSocketAddress localAddress = futureAddress.get();
            final TcpConnector connector = xnio.createTcpConnector(OptionMap.EMPTY);
            final IoFuture<TcpChannel> ioFuture = connector.connectTo(localAddress, new ChannelListener<TcpChannel>() {
                private final ByteBuffer inboundBuf = ByteBuffer.allocate(512);
                private int readCnt = 0;
                private final ByteBuffer outboundBuf = ByteBuffer.wrap(bytes);

                public void handleEvent(final TcpChannel channel) {
                    channel.getCloseSetter().set(new ChannelListener<TcpChannel>() {
                        public void handleEvent(final TcpChannel channel) {
                            closeLatch.countDown();
                        }
                    });
                    channel.getReadSetter().set(new ChannelListener<TcpChannel>() {
                        public void handleEvent(final TcpChannel channel) {
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
                    });
                    channel.getWriteSetter().set(new ChannelListener<TcpChannel>() {
                        public void handleEvent(final TcpChannel channel) {
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
                    });
                    channel.resumeReads();
                    channel.resumeWrites();
                    clientOpened.set(true);
                }
            }, null);
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
            IoUtils.safeClose(xnio);
        }
        threadFactory.await();
    }
}
