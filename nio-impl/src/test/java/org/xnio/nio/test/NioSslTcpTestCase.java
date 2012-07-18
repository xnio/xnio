/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc.


package org.xnio.nio.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.logging.Logger;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.Channels;
import org.xnio.channels.ConnectedSslStreamChannel;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.ssl.XnioSsl;

@SuppressWarnings( { "JavaDoc" })
public final class NioSslTcpTestCase {

    private static final String KEY_STORE_PROPERTY = "javax.net.ssl.keyStore";
    private static final String KEY_STORE_PASSWORD_PROPERTY = "javax.net.ssl.keyStorePassword";
    private static final String TRUST_STORE_PROPERTY = "javax.net.ssl.trustStore";
    private static final String TRUST_STORE_PASSWORD_PROPERTY = "javax.net.ssl.trustStorePassword";
    private static final String DEFAULT_KEY_STORE = "keystore.jks";
    private static final String DEFAULT_KEY_STORE_PASSWORD = "jboss-remoting-test";

    private static final Logger log = Logger.getLogger("TEST");

    private final List<Throwable> problems = new CopyOnWriteArrayList<Throwable>();

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

    private void checkProblems() {
        for (Throwable problem : problems) {
            log.error("Test exception", problem);
        }
        assertTrue(problems.isEmpty());
    }

    private void doConnectionTest(final Runnable body, final ChannelListener<? super ConnectedStreamChannel> clientHandler, final ChannelListener<? super ConnectedStreamChannel> serverHandler) throws Exception {
        final Xnio xnio = Xnio.getInstance("nio", NioSslTcpTestCase.class.getClassLoader());
        final XnioWorker worker = xnio.createWorker(OptionMap.EMPTY);
        try {
            final XnioSsl xnioSsl = xnio.getSslProvider(OptionMap.EMPTY);
            // IDEA thinks this is unchecked because of http://youtrack.jetbrains.net/issue/IDEA-59290
            @SuppressWarnings("unchecked")
            final AcceptingChannel<? extends ConnectedStreamChannel> server = xnioSsl.createSslTcpServer(worker, new InetSocketAddress(Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 }), SERVER_PORT), ChannelListeners.<ConnectedSslStreamChannel>openListenerAdapter(new CatchingChannelListener<ConnectedSslStreamChannel>(serverHandler, problems)), OptionMap.create(Options.REUSE_ADDRESSES, Boolean.TRUE));
            server.resumeAccepts();
            try {
                final IoFuture<? extends ConnectedStreamChannel> ioFuture = xnioSsl.connectSsl(worker, new InetSocketAddress
                        (Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 }), SERVER_PORT),
                        new CatchingChannelListener<ConnectedStreamChannel>(clientHandler, problems), null,
                        OptionMap.EMPTY);
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
            worker.shutdown();
            worker.awaitTermination(1L, TimeUnit.MINUTES);
        }
    }

    @Test
    public void testClientTcpClose() throws Exception {
        problems.clear();
        log.info("Test: testClientTcpClose");
        final CountDownLatch latch = new CountDownLatch(4);
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
                    latch.countDown();
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
        checkProblems();
    }

    @Test
    public void testServerTcpClose() throws Exception {
        problems.clear();
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
                                    channel.close();
                                }
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
        checkProblems();
    }

    @Test
    public void testTwoWayTransfer() throws Exception {
        problems.clear();
        log.info("Test: testTwoWayTransfer");
        final CountDownLatch latch = new CountDownLatch(2);
        final AtomicInteger clientSent = new AtomicInteger(0);
        final AtomicInteger clientReceived = new AtomicInteger(0);
        final AtomicInteger serverSent = new AtomicInteger(0);
        final AtomicInteger serverReceived = new AtomicInteger(0);
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
                                channel.shutdownReads();
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
                                        final ChannelListener<ConnectedStreamChannel> listener = new ChannelListener<ConnectedStreamChannel>() {
                                            public void handleEvent(final ConnectedStreamChannel channel) {
                                                try {
                                                    if (channel.flush()) {
                                                        final ChannelListener<ConnectedStreamChannel> listener = new ChannelListener<ConnectedStreamChannel>() {
                                                            public void handleEvent(final ConnectedStreamChannel channel) {
                                                                // really lame, but due to the way SSL shuts down...
                                                                if (clientReceived.get() < serverSent.get() || serverReceived.get() < clientSent.get() || serverSent.get() == 0 || clientSent.get() == 0) {
                                                                    channel.getWriteThread().executeAfter(new Runnable() {
                                                                        public void run() {
                                                                            channel.wakeupWrites();
                                                                        }
                                                                    }, 1L, TimeUnit.MILLISECONDS);
                                                                    channel.suspendWrites();
                                                                } else try {
                                                                    channel.shutdownWrites();
                                                                    channel.flush();
                                                                } catch (Throwable t) {
                                                                    t.printStackTrace();
                                                                    throw new RuntimeException(t);
                                                                }
                                                            }
                                                        };
                                                        channel.getWriteSetter().set(listener);
                                                        listener.handleEvent(channel);
                                                        return;
                                                    }
                                                } catch (Throwable t) {
                                                    t.printStackTrace();
                                                    throw new RuntimeException(t);
                                                }
                                            }
                                        };
                                        channel.getWriteSetter().set(listener);
                                        listener.handleEvent(channel);
                                        return;
                                    }
                                    buffer.rewind();
                                }
                            } catch (ClosedChannelException e) {
                                channel.shutdownWrites();
                                throw e;
                            }
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
                                channel.shutdownReads();
                            }
                        } catch (Throwable t) {
                            t.printStackTrace();
                            throw new RuntimeException(t);
                        }
                    }
                });
                channel.getWriteSetter().set(new ChannelListener<ConnectedStreamChannel>() {
                    boolean done = false;
                    public void handleEvent(final ConnectedStreamChannel channel) {
                        try {
                            final ByteBuffer buffer = ByteBuffer.allocate(100);
                            buffer.put("This Is A Test\r\n".getBytes("UTF-8")).flip();
                            int c;
                            try {
                                while ((c = channel.write(buffer)) > 0) {
                                    if (serverSent.addAndGet(c) > 1000) {
                                        final ChannelListener<ConnectedStreamChannel> listener = new ChannelListener<ConnectedStreamChannel>() {
                                            public void handleEvent(final ConnectedStreamChannel channel) {
                                                try {
                                                    if (channel.flush()) {
                                                        final ChannelListener<ConnectedStreamChannel> listener = new ChannelListener<ConnectedStreamChannel>() {
                                                            public void handleEvent(final ConnectedStreamChannel channel) {
                                                                // really lame, but due to the way SSL shuts down...
                                                                if (clientReceived.get() < serverSent.get() || serverReceived.get() < clientSent.get() || serverSent.get() == 0 || clientSent.get() == 0) {
                                                                    channel.getWriteThread().executeAfter(new Runnable() {
                                                                        public void run() {
                                                                            channel.wakeupWrites();
                                                                        }
                                                                    }, 1L, TimeUnit.MILLISECONDS);
                                                                    channel.suspendWrites();
                                                                } else try {
                                                                    channel.shutdownWrites();
                                                                    channel.flush();
                                                                } catch (Throwable t) {
                                                                    t.printStackTrace();
                                                                    throw new RuntimeException(t);
                                                                }
                                                            }
                                                        };
                                                        channel.getWriteSetter().set(listener);
                                                        listener.handleEvent(channel);
                                                        return;
                                                    }
                                                } catch (Throwable t) {
                                                    t.printStackTrace();
                                                    throw new RuntimeException(t);
                                                }
                                            }
                                        };
                                        channel.getWriteSetter().set(listener);
                                        listener.handleEvent(channel);
                                        return;
                                    }
                                    buffer.rewind();
                                }
                            } catch (ClosedChannelException e) {
                                channel.shutdownWrites();
                                throw e;
                            }
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
        checkProblems();
    }

    @Test
    public void testClientTcpNastyClose() throws Exception {
        problems.clear();
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
                            int res;
                            try {
                                res = channel.read(ByteBuffer.allocate(100));
                            } catch (IOException e) {
                                serverOK.set(true);
                                IoUtils.safeClose(channel);
                                return;
                            }
                            if (res > 0) IoUtils.safeClose(channel);
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
        checkProblems();
    }

    // FIXME @Test
    public void testServerTcpNastyClose() throws Exception {
        problems.clear();
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
                            int res;
                            try {
                                res = channel.read(ByteBuffer.allocate(100));
                            } catch (IOException e) {
                                clientOK.set(true);
                                IoUtils.safeClose(channel);
                                return;
                            }
                            if (res > 0) IoUtils.safeClose(channel);
                        }
                    });
                    channel.getWriteSetter().set(new ChannelListener<ConnectedStreamChannel>() {
                        public void handleEvent(final ConnectedStreamChannel channel) {
                            try {
                                if (channel.write(ByteBuffer.wrap(new byte[] { 1 })) > 0) {
                                    channel.flush();
                                    channel.suspendWrites();
                                }
                            } catch (IOException e) {
                                IoUtils.safeClose(channel);
                            }
                        }
                    });
                    channel.resumeReads();
                    channel.resumeWrites();
                    serverLatch.countDown();
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
                    channel.getReadSetter().set(new ChannelListener<ConnectedStreamChannel>() {
                        public void handleEvent(final ConnectedStreamChannel channel) {
                            try {
                                if (channel.read(ByteBuffer.allocate(1)) > 0) {
                                    log.info("Closing connection...");
                                    channel.setOption(Options.CLOSE_ABORT, Boolean.TRUE);
                                    channel.close();
                                    serverOK.set(true);
                                }
                            } catch (IOException e) {
                                IoUtils.safeClose(channel);
                            }
                        }
                    });
                    channel.resumeReads();
                } catch (Throwable t) {
                    log.error("Error occurred on server", t);
                    latch.countDown();
                    throw new RuntimeException(t);
                }
            }
        });
        assertTrue(serverOK.get());
        assertTrue(clientOK.get());
        checkProblems();
    }
}
