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

package org.xnio.nio.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.logging.Logger;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.xnio.BufferAllocator;
import org.xnio.Buffers;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.ConnectionChannelThread;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Pool;
import org.xnio.ReadChannelThread;
import org.xnio.WriteChannelThread;
import org.xnio.Xnio;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.Channels;
import org.xnio.channels.ConnectedSslStreamChannel;
import org.xnio.channels.ConnectedStreamChannel;

@SuppressWarnings( { "JavaDoc" })
public final class NioSslTcpTestCase {

    private static final String KEY_STORE_PROPERTY = "javax.net.ssl.keyStore";
    private static final String KEY_STORE_PASSWORD_PROPERTY = "javax.net.ssl.keyStorePassword";
    private static final String TRUST_STORE_PROPERTY = "javax.net.ssl.trustStore";
    private static final String TRUST_STORE_PASSWORD_PROPERTY = "javax.net.ssl.trustStorePassword";
    private static final String DEFAULT_KEY_STORE = "keystore.jks";
    private static final String DEFAULT_KEY_STORE_PASSWORD = "jboss-remoting-test";

    private static final Logger log = Logger.getLogger("TEST");

    private final TestThreadFactory threadFactory = new TestThreadFactory();

    private static final int SERVER_PORT = 12345;

    @BeforeClass
    public static void setKeyStoreAndTrustStore() {
        final URL storePath = NioSslTcpTestCase.class.getClassLoader().getResource(DEFAULT_KEY_STORE);
        if (System.getProperty(KEY_STORE_PROPERTY) == null) {
            System.setProperty(KEY_STORE_PROPERTY, storePath.getFile());
        }
        if (System.getProperty(KEY_STORE_PASSWORD_PROPERTY) == null) {
            System.setProperty(KEY_STORE_PASSWORD_PROPERTY, DEFAULT_KEY_STORE_PASSWORD);
        }
        if (System.getProperty(TRUST_STORE_PROPERTY) == null) {
            System.setProperty(TRUST_STORE_PROPERTY, storePath.getFile());
        }
        if (System.getProperty(TRUST_STORE_PASSWORD_PROPERTY) == null) {
            System.setProperty(TRUST_STORE_PASSWORD_PROPERTY, DEFAULT_KEY_STORE_PASSWORD);
        }
    }

    private void doConnectionTest(final Runnable body, final ChannelListener<? super ConnectedStreamChannel> clientHandler, final ChannelListener<? super ConnectedStreamChannel> serverHandler) throws Exception {
        Xnio xnio = Xnio.getInstance("nio", NioSslTcpTestCase.class.getClassLoader());
        final ConnectionChannelThread connectionChannelThread = xnio.createReadChannelThread(threadFactory);
        final ConnectionChannelThread serverChannelThread = xnio.createReadChannelThread(threadFactory);
        final ReadChannelThread readChannelThread = xnio.createReadChannelThread(threadFactory);
        final ReadChannelThread clientReadChannelThread = xnio.createReadChannelThread(threadFactory);
        final WriteChannelThread writeChannelThread = xnio.createWriteChannelThread(threadFactory);
        final WriteChannelThread clientWriteChannelThread = xnio.createWriteChannelThread(threadFactory);
        final Pool<ByteBuffer> bufferPool = Buffers.allocatedBufferPool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 17000);
        try {
            // IDEA thinks this is unchecked because of http://youtrack.jetbrains.net/issue/IDEA-59290
            @SuppressWarnings("unchecked")
            final AcceptingChannel<? extends ConnectedStreamChannel> server = xnio.createSslTcpServer(
                    new InetSocketAddress(Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 }), SERVER_PORT),
                    serverChannelThread,
                    ChannelListeners.<ConnectedSslStreamChannel>openListenerAdapter(readChannelThread, writeChannelThread, new CatchingChannelListener<ConnectedSslStreamChannel>(
                            serverHandler,
                            threadFactory
                    )), OptionMap.builder().set(Options.REUSE_ADDRESSES, Boolean.TRUE).getMap(), bufferPool);
            server.resumeAccepts();
            try {
                final IoFuture<? extends ConnectedStreamChannel> ioFuture = xnio.connectSsl(new InetSocketAddress
                        (Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 }), SERVER_PORT),
                        connectionChannelThread, clientReadChannelThread, clientWriteChannelThread,
                        new CatchingChannelListener<ConnectedStreamChannel>(clientHandler, threadFactory), null,
                        OptionMap.EMPTY, bufferPool);
                final ConnectedStreamChannel channel = ioFuture.get();
                try {
                    body.run();
                    channel.close();
                    server.close();
                } catch (Exception e) {
                    log.errorf(e, "Error running body");
                    throw e;
                } catch (Error e) {
                    log.errorf(e, "Error running body");
                    throw e;
                } finally {
                    IoUtils.safeClose(channel);
                }
            } finally {
                IoUtils.safeClose(server);
            }
        } finally {
            connectionChannelThread.shutdown();
            serverChannelThread.shutdown();
            clientReadChannelThread.shutdown();
            clientWriteChannelThread.shutdown();
            readChannelThread.shutdown();
            writeChannelThread.shutdown();
        }
        connectionChannelThread.awaitTermination();
        serverChannelThread.awaitTermination();
        readChannelThread.awaitTermination();
        writeChannelThread.awaitTermination();
        clientReadChannelThread.awaitTermination();
        clientWriteChannelThread.awaitTermination();
    }

    @Test
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
        }, new ChannelListener<ConnectedStreamChannel>() {
            public void handleEvent(final ConnectedStreamChannel channel) {
                log.info("In client open");
                try {
                    channel.getCloseSetter().set(new ChannelListener<ConnectedStreamChannel>() {
                        public void handleEvent(final ConnectedStreamChannel channel) {
                            log.info("In client close");
                            latch.countDown();
                        }
                    });
                    Channels.shutdownWritesBlocking(channel);
                    int c = Channels.readBlocking(channel, ByteBuffer.allocate(100));
                    channel.close();
                    if (c == -1) {
                        clientOK.set(true);
                    }
                } catch (Throwable t) {
                    log.error("In client", t);
                    latch.countDown();
                    throw new RuntimeException(t);
                }
            }
        }, new ChannelListener<ConnectedStreamChannel>() {
            public void handleEvent(final ConnectedStreamChannel channel) {
                log.info("In server opened");
                channel.getCloseSetter().set(new ChannelListener<ConnectedStreamChannel>() {
                    public void handleEvent(final ConnectedStreamChannel channel) {
                        log.info("In server close");
                        latch.countDown();
                    }
                });
                channel.getReadSetter().set(new ChannelListener<ConnectedStreamChannel>() {
                    public void handleEvent(final ConnectedStreamChannel channel) {
                        log.info("In server readable");
                        try {
                            Channels.shutdownWritesBlocking(channel);
                            int c = Channels.readBlocking(channel, ByteBuffer.allocate(100));
                            channel.close();
                            if (c == -1) {
                                serverOK.set(true);
                            }
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

    @Test
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
        }, new ChannelListener<ConnectedStreamChannel>() {
            public void handleEvent(final ConnectedStreamChannel channel) {
                try {
                    channel.getCloseSetter().set(new ChannelListener<ConnectedStreamChannel>() {
                        public void handleEvent(final ConnectedStreamChannel channel) {
                            latch.countDown();
                        }
                    });
                    channel.getReadSetter().set(new ChannelListener<ConnectedStreamChannel>() {
                        public void handleEvent(final ConnectedStreamChannel channel) {
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
        }, new ChannelListener<ConnectedStreamChannel>() {
            public void handleEvent(final ConnectedStreamChannel channel) {
                try {
                    channel.getCloseSetter().set(new ChannelListener<ConnectedStreamChannel>() {
                        public void handleEvent(final ConnectedStreamChannel channel) {
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

    @Ignore @Test
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
        }, new ChannelListener<ConnectedStreamChannel>() {
            public void handleEvent(final ConnectedStreamChannel channel) {
                channel.getCloseSetter().set(new ChannelListener<ConnectedStreamChannel>() {
                    public void handleEvent(final ConnectedStreamChannel channel) {
                        latch.countDown();
                    }
                });
                channel.getReadSetter().set(new ChannelListener<ConnectedStreamChannel>() {
                    public void handleEvent(final ConnectedStreamChannel channel) {
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
                channel.getWriteSetter().set(new ChannelListener<ConnectedStreamChannel>() {
                    public void handleEvent(final ConnectedStreamChannel channel) {
                        try {
                            final ByteBuffer buffer = ByteBuffer.allocate(100);
                            buffer.put("This Is A Test\r\n".getBytes("UTF-8")).flip();
                            int c;
                            try {
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
                            } catch (ClosedChannelException e) {
                                channel.shutdownWrites();
                                throw e;
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
        }, new ChannelListener<ConnectedStreamChannel>() {
            public void handleEvent(final ConnectedStreamChannel channel) {
                channel.getCloseSetter().set(new ChannelListener<ConnectedStreamChannel>() {
                    public void handleEvent(final ConnectedStreamChannel channel) {
                        latch.countDown();
                    }
                });
                channel.getReadSetter().set(new ChannelListener<ConnectedStreamChannel>() {
                    public void handleEvent(final ConnectedStreamChannel channel) {
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
                channel.getWriteSetter().set(new ChannelListener<ConnectedStreamChannel>() {
                    public void handleEvent(final ConnectedStreamChannel channel) {
                        try {
                            final ByteBuffer buffer = ByteBuffer.allocate(100);
                            buffer.put("This Is A Test Gumma\r\n".getBytes("UTF-8")).flip();
                            int c;
                            try {
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
                            }
                            catch (ClosedChannelException e) {
                                channel.shutdownWrites();
                                throw e;
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

    @Test
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
        }, new ChannelListener<ConnectedStreamChannel>() {
            public void handleEvent(final ConnectedStreamChannel channel) {
                try {
                    channel.getCloseSetter().set(new ChannelListener<ConnectedStreamChannel>() {
                        public void handleEvent(final ConnectedStreamChannel channel) {
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
        }, new ChannelListener<ConnectedStreamChannel>() {
            public void handleEvent(final ConnectedStreamChannel channel) {
                try {
                    channel.getCloseSetter().set(new ChannelListener<ConnectedStreamChannel>() {
                        public void handleEvent(final ConnectedStreamChannel channel) {
                            latch.countDown();
                        }
                    });
                    channel.getReadSetter().set(new ChannelListener<ConnectedStreamChannel>() {
                        public void handleEvent(final ConnectedStreamChannel channel) {
                            try {
                                channel.read(ByteBuffer.allocate(100));
                            } catch (IOException e) {
                                serverOK.set(true);
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

    @Test
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
        }, new ChannelListener<ConnectedStreamChannel>() {
            public void handleEvent(final ConnectedStreamChannel channel) {
                try {
                    log.info("Client opened");
                    channel.getCloseSetter().set(new ChannelListener<ConnectedStreamChannel>() {
                        public void handleEvent(final ConnectedStreamChannel channel) {
                            latch.countDown();
                        }
                    });
                    channel.getReadSetter().set(new ChannelListener<ConnectedStreamChannel>() {
                        public void handleEvent(final ConnectedStreamChannel channel) {
                            try {
                                channel.read(ByteBuffer.allocate(100));
                                channel.close();
                            } catch (IOException e) {
                                clientOK.set(true);
                            } finally {
                                IoUtils.safeClose(channel);
                            }
                        }
                    });
                    serverLatch.countDown();
                    channel.resumeReads();
                } catch (Throwable t) {
                    log.error("Error occurred on client", t);
                    try {
                        channel.close();
                    } catch (Throwable t2) {
                        log.error("Error occurred on client (close)", t2);
                        latch.countDown();
                        throw new RuntimeException(t);
                    }
                    throw new RuntimeException(t);
                }
            }
        }, new ChannelListener<ConnectedStreamChannel>() {
            public void handleEvent(final ConnectedStreamChannel channel) {
                try {
                    log.info("Server opened");
                    channel.getCloseSetter().set(new ChannelListener<ConnectedStreamChannel>() {
                        public void handleEvent(final ConnectedStreamChannel channel) {
                            latch.countDown();
                        }
                    });
                    serverLatch.await(500L, TimeUnit.MILLISECONDS);
                    log.info("Closing connection...");
                    channel.setOption(Options.CLOSE_ABORT, Boolean.TRUE);
                    channel.close();
                    serverOK.set(true);
                } catch (Throwable t) {
                    log.error("Error occurred on server", t);
                    latch.countDown();
                    throw new RuntimeException(t);
                }
            }
        });
        assertTrue(serverOK.get());
        assertTrue(clientOK.get());
        threadFactory.await();
    }
}
