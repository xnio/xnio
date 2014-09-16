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
import static org.junit.Assert.assertTrue;

import java.io.EOFException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.logging.Logger;
import org.junit.After;
import org.junit.Before;
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
import org.xnio.channels.BoundChannel;
import org.xnio.channels.ConnectedChannel;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 * Abstract test for TCP connected channels.
 * 
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 */
public abstract class AbstractNioTcpTest<T extends ConnectedChannel, R extends StreamSourceChannel, W extends StreamSinkChannel> {

    protected static final Logger log = Logger.getLogger("TEST");

    private final List<Throwable> problems = new CopyOnWriteArrayList<Throwable>();

    protected static final int SERVER_PORT = 12345;

    private OptionMap serverOptionMap = OptionMap.create(Options.REUSE_ADDRESSES, Boolean.TRUE); // any random map

    private OptionMap clientOptionMap = OptionMap.EMPTY;

    private int threads = 1;

    protected abstract AcceptingChannel<? extends T> createServer(XnioWorker worker, InetSocketAddress address, ChannelListener<AcceptingChannel<T>> openListener, OptionMap optionMap) throws IOException;
    protected abstract IoFuture<? extends T> connect(XnioWorker worker, InetSocketAddress address, ChannelListener<T> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap);
    protected abstract void setReadListener(T channel, ChannelListener<R> readListener);
    protected abstract void setWriteListener(T channel, ChannelListener<W> writeListener);
    protected abstract void resumeReads(T channel);
    protected abstract void resumeWrites(T channel);

    protected void doConnectionTest(final Runnable body, final ChannelListener<? super T> clientHandler, final ChannelListener<? super T> serverHandler) throws Exception {
        final Xnio xnio = Xnio.getInstance("nio", AbstractNioTcpTest.class.getClassLoader());
        final XnioWorker worker;
        if (threads == 1) {
            worker = xnio.createWorker(OptionMap.create(Options.READ_TIMEOUT, 10000, Options.WRITE_TIMEOUT, 10000));
        } else {
            worker = xnio.createWorker(OptionMap.create(Options.WORKER_IO_THREADS, threads));
        }
        try {
            final AcceptingChannel<? extends T> server = createServer(worker,
                    new InetSocketAddress(Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 }), SERVER_PORT),
                    ChannelListeners.<T>openListenerAdapter(new CatchingChannelListener<T>(
                            serverHandler,
                            problems
                    )), serverOptionMap);
            server.resumeAccepts();
            try {
                final IoFuture<? extends T> ioFuture = connect(worker, new InetSocketAddress(Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 }), SERVER_PORT), new CatchingChannelListener<T>(clientHandler, problems), null, clientOptionMap);
                final T channel = ioFuture.get();
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

    /**
     * Set the number of threads that will be used by this test.
     * 
     * @param threads the number of threads.
     */
    protected void setNumberOfThreads(int threads) {
        this.threads = threads;
    }

    /**
     * Set the option map used to create the server.
     * 
     * @param serverOptionMap the option map that must be used to create server
     */
    protected void setServerOptionMap(OptionMap serverOptionMap) {
        this.serverOptionMap = serverOptionMap;
    }

    /**
     * Set the option map used to connect to the server
     * 
     * @param clientOptionMap the option map that must be used to connect to the server
     */
    protected void setClientOptionMap(OptionMap clientOptionMap) {
        this.clientOptionMap = clientOptionMap;
    }

    @Before
    public void clearProblems() {
        problems.clear();
    }

    @After
    public void checkProblems() {
        for (Throwable problem : problems) {
            log.error("Test exception", problem);
        }
        assertTrue(problems.isEmpty());
    }

    @Test
    public void connect() throws Exception {
        log.info("Test: tcpConnect");
        doConnectionTest(new Runnable() {
            public void run() {
            }
        }, null, ChannelListeners.closingChannelListener());
    }

    @Test
    public void clientClose() throws Exception {
        log.info("Test: clientClose");
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
        }, new ChannelListener<T>() {
            public void handleEvent(final T channel) {
                log.info("In client open");
                try {
                    channel.getCloseSetter().set(new ChannelListener<ConnectedChannel>() {
                        public void handleEvent(final ConnectedChannel channel) {
                            log.info("In client close");
                            latch.countDown();
                        }
                    });
                    channel.close();
                    clientOK.set(true);
                    latch.countDown();
                } catch (Throwable t) {
                    log.error("In client", t);
                    latch.countDown();
                    throw new RuntimeException(t);
                }
            }
        }, new ChannelListener<T>() {
            public void handleEvent(final T channel) {
                log.info("In server opened");
                channel.getCloseSetter().set(new ChannelListener<ConnectedChannel>() {
                    public void handleEvent(final ConnectedChannel channel) {
                        log.info("In server close");
                        latch.countDown();
                    }
                });
                setReadListener(channel, new ChannelListener<R>() {
                    public void handleEvent(final R sourceChannel) {
                        log.info("In server readable");
                        try {
                            final int c = sourceChannel.read(ByteBuffer.allocate(100));
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
                resumeReads(channel);
            }
        });
        assertTrue(serverOK.get());
        assertTrue(clientOK.get());
    }

    @Test
    public void serverClose() throws Exception {
        log.info("Test: serverClose");
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
        }, new ChannelListener<T>() {
            public void handleEvent(final T channel) {
                try {
                    channel.getCloseSetter().set(new ChannelListener<ConnectedChannel>() {
                        public void handleEvent(final ConnectedChannel channel) {
                            latch.countDown();
                        }
                    });
                    setReadListener(channel, new ChannelListener<R>() {
                        public void handleEvent(final R sourceChannel) {
                            try {
                                final int c = sourceChannel.read(ByteBuffer.allocate(100));
                                if (c == -1) {
                                    clientOK.set(true);
                                    channel.close();
                                    return;
                                }
                                // retry
                                return;
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    });
                    resumeReads(channel);
                } catch (Throwable t) {
                    try {
                        channel.close();
                    } catch (Throwable t2) {
                        log.errorf(t2, "Failed to close channel (propagating as RT exception)");
                        latch.countDown();
                        throw new RuntimeException(t);
                    }
                    throw new RuntimeException(t);
                }
            }
        }, new ChannelListener<T>() {
            public void handleEvent(final T channel) {
                try {
                    channel.getCloseSetter().set(new ChannelListener<ConnectedChannel>() {
                        public void handleEvent(final ConnectedChannel channel) {
                            serverOK.set(true);
                            latch.countDown();
                        }
                    });
                    channel.close();
                } catch (Throwable t) {
                    log.errorf(t, "Failed to close channel (propagating as RT exception)");
                    latch.countDown();
                    throw new RuntimeException(t);
                }
            }
        });
        assertTrue(serverOK.get());
        assertTrue(clientOK.get());
    }

    @Test
    public void oneWayTransfer1() throws Exception {
        log.info("Test: oneWayTransfer1");
        final CountDownLatch latch = new CountDownLatch(2);
        final AtomicInteger clientSent = new AtomicInteger(0);
        final AtomicInteger serverReceived = new AtomicInteger(0);
        doConnectionTest(new Runnable() {
            public void run() {
                try {
                    assertTrue(latch.await(500L, TimeUnit.MILLISECONDS));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }, new ChannelListener<T>() {
            public void handleEvent(final T channel) {
                channel.getCloseSetter().set(new ChannelListener<ConnectedChannel>() {
                    public void handleEvent(final ConnectedChannel channel) {
                        latch.countDown();
                    }
                });
                final ByteBuffer buffer = ByteBuffer.allocate(100);
                try {
                    buffer.put("This Is A Test\r\n".getBytes("UTF-8")).flip();
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }
                setWriteListener(channel, new ChannelListener<W>() {
                    public void handleEvent(final W sinkChannel) {
                        try {
                            int c;
                            while ((c = sinkChannel.write(buffer)) > 0) {
                                if (clientSent.addAndGet(c) > 1000) {
                                    final ChannelListener<StreamSinkChannel> listener = new ChannelListener<StreamSinkChannel>() {
                                        public void handleEvent(final StreamSinkChannel sinkChannel) {
                                            try {
                                                sinkChannel.shutdownWrites();
                                                channel.close();
                                            } catch (Throwable t) {
                                                log.errorf(t, "Failed to close channel (propagating as RT exception)");
                                                throw new RuntimeException(t);
                                            }
                                        }
                                    };
                                    sinkChannel.getWriteSetter().set(listener);
                                    listener.handleEvent(sinkChannel);
                                    return;
                                }
                                buffer.rewind();
                            }
                        } catch (Throwable t) {
                            log.errorf(t, "Failed to close channel (propagating as RT exception)");
                            throw new RuntimeException(t);
                        }
                    }
                });
                resumeWrites(channel);
            }
        }, new ChannelListener<T>() {
            public void handleEvent(final T channel) {
                channel.getCloseSetter().set(new ChannelListener<ConnectedChannel>() {
                    public void handleEvent(final ConnectedChannel channel) {
                        latch.countDown();
                    }
                });
                setReadListener(channel, new ChannelListener<R>() {
                    public void handleEvent(final R sourceChannel) {
                        try {
                            int c;
                            while ((c = sourceChannel.read(ByteBuffer.allocate(100))) > 0) {
                                serverReceived.addAndGet(c);
                            }
                            if (c == -1) {
                                sourceChannel.shutdownReads();
                                channel.close();
                            }
                        } catch (Throwable t) {
                            log.errorf(t, "Failed to close channel (propagating as RT exception)");
                            throw new RuntimeException(t);
                        }
                    }
                });
                resumeReads(channel);
            }
        });
        assertEquals(clientSent.get(), serverReceived.get());
    }

    @Test
    public void oneWayTransfer2() throws Exception {
        log.info("Test: oneWayTransfer2");
        final CountDownLatch latch = new CountDownLatch(2);
        final AtomicInteger clientReceived = new AtomicInteger(0);
        final AtomicInteger serverSent = new AtomicInteger(0);
        doConnectionTest(new Runnable() {
            public void run() {
                try {
                    assertTrue(latch.await(500L, TimeUnit.MILLISECONDS));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }, new ChannelListener<T>() {
            public void handleEvent(final T channel) {
                channel.getCloseSetter().set(new ChannelListener<ConnectedChannel>() {
                    public void handleEvent(final ConnectedChannel channel) {
                        latch.countDown();
                    }
                });
                setReadListener(channel, new ChannelListener<R>() {
                    public void handleEvent(final R sourceChannel) {
                        try {
                            int c;
                            while ((c = sourceChannel.read(ByteBuffer.allocate(100))) > 0) {
                                clientReceived.addAndGet(c);
                            }
                            if (c == -1) {
                                sourceChannel.shutdownReads();
                                channel.close();
                            }
                        } catch (Throwable t) {
                            log.errorf(t, "Failed to close channel (propagating as RT exception)");
                            throw new RuntimeException(t);
                        }
                    }
                });
                resumeReads(channel);
            }
        }, new ChannelListener<T>() {
            public void handleEvent(final T channel) {
                channel.getCloseSetter().set(new ChannelListener<ConnectedChannel>() {
                    public void handleEvent(final ConnectedChannel channel) {
                        latch.countDown();
                    }
                });
                final ByteBuffer buffer = ByteBuffer.allocate(100);
                try {
                    buffer.put("This Is A Test Gumma\r\n".getBytes("UTF-8")).flip();
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }
                setWriteListener(channel, new ChannelListener<W>() {
                    public void handleEvent(final W sinkChannel) {
                        try {
                            int c;
                            while ((c = sinkChannel.write(buffer)) > 0) {
                                if (serverSent.addAndGet(c) > 1000) {
                                    final ChannelListener<StreamSinkChannel> listener = new ChannelListener<StreamSinkChannel>() {
                                        public void handleEvent(final StreamSinkChannel sinkChannel) {
                                            try {
                                                sinkChannel.shutdownWrites();
                                                channel.close();
                                            } catch (Throwable t) {
                                                log.errorf(t, "Failed to close channel (propagating as RT exception)");
                                                throw new RuntimeException(t);
                                            }
                                        }
                                    };
                                    sinkChannel.getWriteSetter().set(listener);
                                    listener.handleEvent(sinkChannel);
                                    return;
                                }
                                buffer.rewind();
                            }
                        } catch (Throwable t) {
                            log.errorf(t, "Failed to close channel (propagating as RT exception)");
                            throw new RuntimeException(t);
                        }
                    }
                });
                resumeWrites(channel);;
            }
        });
        assertEquals(serverSent.get(), clientReceived.get());
    }

    @Test
    public void twoWayTransfer() throws Exception {
        log.info("Test: twoWayTransfer");
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
        }, new ChannelListener<T>() {
            public void handleEvent(final T channel) {
                channel.getCloseSetter().set(new ChannelListener<ConnectedChannel>() {
                    public void handleEvent(final ConnectedChannel channel) {
                        latch.countDown();
                    }
                });
                setReadListener(channel, new ChannelListener<R>() {
                    public void handleEvent(final R sourceChannel) {
                        try {
                            int c;
                            while ((c = sourceChannel.read(ByteBuffer.allocate(100))) > 0) {
                                clientReceived.addAndGet(c);
                            }
                            if (c == -1) {
                                sourceChannel.shutdownReads();
                            }
                        } catch (Throwable t) {
                            log.errorf(t, "Failed to close channel (propagating as RT exception)");
                            throw new RuntimeException(t);
                        }
                    }
                });
                final ByteBuffer buffer = ByteBuffer.allocate(100);
                try {
                    buffer.put("This Is A Test\r\n".getBytes("UTF-8")).flip();
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }
                setWriteListener(channel, new ChannelListener<W>() {
                    public void handleEvent(final W sinkChannel) {
                        try {
                            int c;
                            while ((c = sinkChannel.write(buffer)) > 0) {
                                if (clientSent.addAndGet(c) > 1000) {
                                    final ChannelListener<StreamSinkChannel> listener = new ChannelListener<StreamSinkChannel>() {
                                        public void handleEvent(final StreamSinkChannel sinkChannel) {
                                            try {
                                                sinkChannel.shutdownWrites();
                                            } catch (Throwable t) {
                                                log.errorf(t, "Failed to close channel (propagating as RT exception)");
                                                throw new RuntimeException(t);
                                            }
                                        }
                                    };
                                    sinkChannel.getWriteSetter().set(listener);
                                    listener.handleEvent(sinkChannel);
                                    return;
                                }
                                buffer.rewind();
                            }
                        } catch (Throwable t) {
                            log.errorf(t, "Failed to close channel (propagating as RT exception)");
                            throw new RuntimeException(t);
                        }
                    }
                });
                resumeReads(channel);
                resumeWrites(channel);
            }
        }, new ChannelListener<T>() {
            public void handleEvent(final T channel) {
                channel.getCloseSetter().set(new ChannelListener<ConnectedChannel>() {
                    public void handleEvent(final ConnectedChannel channel) {
                        latch.countDown();
                    }
                });
                setReadListener(channel, new ChannelListener<R>() {
                    public void handleEvent(final R sourceChannel) {
                        try {
                            int c;
                            while ((c = sourceChannel.read(ByteBuffer.allocate(100))) > 0) {
                                serverReceived.addAndGet(c);
                            }
                            if (c == -1) {
                                sourceChannel.shutdownReads();
                            }
                        } catch (Throwable t) {
                            log.errorf(t, "Failed to close channel (propagating as RT exception)");
                            throw new RuntimeException(t);
                        }
                    }
                });
                final ByteBuffer buffer = ByteBuffer.allocate(100);
                try {
                    buffer.put("This Is A Test Gumma\r\n".getBytes("UTF-8")).flip();
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }
                setWriteListener(channel, new ChannelListener<W>() {
                    public void handleEvent(final W sinkChannel) {
                        try {
                            int c;
                            while ((c = sinkChannel.write(buffer)) > 0) {
                                if (serverSent.addAndGet(c) > 1000) {
                                    final ChannelListener<StreamSinkChannel> listener = new ChannelListener<StreamSinkChannel>() {
                                        public void handleEvent(final StreamSinkChannel channel) {
                                            try {
                                                channel.shutdownWrites();
                                            } catch (Throwable t) {
                                                log.errorf(t, "Failed to close channel (propagating as RT exception)");
                                                throw new RuntimeException(t);
                                            }
                                        }
                                    };
                                    sinkChannel.getWriteSetter().set(listener);
                                    listener.handleEvent(sinkChannel);
                                    return;
                                }
                                buffer.rewind();
                            }
                        } catch (Throwable t) {
                            log.errorf(t, "Failed to close channel (propagating as RT exception)");
                            throw new RuntimeException(t);
                        }
                    }
                });
                resumeReads(channel);
                resumeWrites(channel);;
            }
        });
        assertEquals(serverSent.get(), clientReceived.get());
        assertEquals(clientSent.get(), serverReceived.get());
    }

    @Test
    public void clientNastyClose() throws Exception {
        log.info("Test: clientNastyClose");
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
        }, new ChannelListener<T>() {
            public void handleEvent(final T channel) {
                try {
                    channel.getCloseSetter().set(new ChannelListener<ConnectedChannel>() {
                        public void handleEvent(final ConnectedChannel channel) {
                            log.info("at client close listener");
                            latch.countDown();
                        }
                    });
                    channel.setOption(Options.CLOSE_ABORT, Boolean.TRUE);
                    log.info("client closing channel");
                    channel.close();
                    clientOK.set(true);
                } catch (EOFException e) {
                    // eof exception is fine
                    clientOK.set(true);
                    latch.countDown();
                } catch (Throwable t) {
                    log.errorf(t, "Failed to close channel (propagating as RT exception)");
                    latch.countDown();
                    throw new RuntimeException(t);
                }
            }
        }, new ChannelListener<T>() {
            public void handleEvent(final T channel) {
                try {
                    channel.getCloseSetter().set(new ChannelListener<ConnectedChannel>() {
                        public void handleEvent(final ConnectedChannel channel) {
                            log.info("at server close listener");
                            latch.countDown();
                        }
                    });
                    setReadListener(channel, new ChannelListener<R>() {
                        public void handleEvent(final R sourceChannel) {
                            int res;
                            try {
                                res = sourceChannel.read(ByteBuffer.allocate(100));
                                log.info("server read " + res);
                            } catch (IOException e) {
                                serverOK.set(true);
                                IoUtils.safeClose(channel);
                                return;
                            }
                            if (res > 0) IoUtils.safeClose(channel);
                        }
                    });
                    log.info("resuming reads for server");
                    resumeReads(channel);
                } catch (Throwable t) {
                    log.errorf(t, "Failed to close channel (propagating as RT exception)");
                    latch.countDown();
                    throw new RuntimeException(t);
                }
            }
        });
        assertTrue(serverOK.get());
        assertTrue(clientOK.get());
    }

    @Test
    public void serverNastyClose() throws Exception {
        log.info("Test: serverNastyClose");
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
        }, new ChannelListener<T>() {
            public void handleEvent(final T channel) {
                try {
                    log.info("Client opened");
                    channel.getCloseSetter().set(new ChannelListener<ConnectedChannel>() {
                        public void handleEvent(final ConnectedChannel channel) {
                            latch.countDown();
                        }
                    });
                    setReadListener(channel, new ChannelListener<R>() {
                        public void handleEvent(final R sourceChannel) {
                            int res;
                            try {
                                res = sourceChannel.read(ByteBuffer.allocate(100));
                            } catch (IOException e) {
                                clientOK.set(true);
                                IoUtils.safeClose(channel);
                                return;
                            }
                            if (res == -1) {
                                clientOK.set(true);
                                IoUtils.safeClose(channel);
                            }
                        }
                    });
                    setWriteListener(channel, new ChannelListener<W>() {
                        public void handleEvent(final W sinkChannel) {
                            try {
                                if (sinkChannel.write(ByteBuffer.wrap(new byte[] { 1 })) > 0) {
                                    sinkChannel.suspendWrites();
                                }
                            } catch (IOException e) {
                                IoUtils.safeClose(channel);
                            }
                        }
                    });
                    resumeReads(channel);
                    resumeWrites(channel);
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
        }, new ChannelListener<T>() {
            public void handleEvent(final T channel) {
                try {
                    log.info("Server opened");
                    channel.getCloseSetter().set(new ChannelListener<ConnectedChannel>() {
                        public void handleEvent(final ConnectedChannel channel) {
                            latch.countDown();
                        }
                    });
                    setReadListener(channel, new ChannelListener<R>() {
                        public void handleEvent(final R sourceChannel) {
                            try {
                                if (sourceChannel.read(ByteBuffer.allocate(1)) > 0) {
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
                    resumeReads(channel);
                } catch (Throwable t) {
                    log.error("Error occurred on server", t);
                    latch.countDown();
                    throw new RuntimeException(t);
                }
            }
        });
        assertTrue(serverOK.get());
        assertTrue(clientOK.get());
    }
}
