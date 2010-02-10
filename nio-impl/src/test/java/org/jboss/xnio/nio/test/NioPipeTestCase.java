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
import org.jboss.xnio.channels.StreamChannel;
import org.jboss.xnio.channels.StreamSourceChannel;
import org.jboss.xnio.channels.StreamSinkChannel;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.Xnio;
import org.jboss.xnio.IoFuture;
import org.jboss.xnio.ChannelListener;
import org.jboss.xnio.XnioConfiguration;
import org.jboss.xnio.log.Logger;
import org.jboss.xnio.test.support.LoggingHelper;
import org.jboss.xnio.test.support.TestThreadFactory;
import static org.jboss.xnio.Buffers.flip;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.ByteBuffer;
import java.io.IOException;
import java.io.Closeable;

/**
 *
 */
public final class NioPipeTestCase extends TestCase {
    static {
        LoggingHelper.init();
    }

    private static final Logger log = Logger.getLogger("TEST");

    private final TestThreadFactory threadFactory = new TestThreadFactory();

    private void doOneWayPipeTest(final Runnable body, final ChannelListener<? super StreamSourceChannel> sourceHandler, final ChannelListener<? super StreamSinkChannel> sinkHandler) throws Exception {
        final XnioConfiguration config = new XnioConfiguration();
        config.setThreadFactory(threadFactory);
        Xnio xnio = Xnio.create("nio", config);
        try {
            final IoFuture<? extends Closeable> future = xnio.createOneWayPipeConnection(sourceHandler, sinkHandler);
            final Closeable closeable = future.get();
            try {
                body.run();
            } finally {
                IoUtils.safeClose(closeable);
            }
        } finally {
            IoUtils.safeClose(xnio);
        }
    }

    private void doTwoWayPipeTest(final Runnable body, final ChannelListener<? super StreamChannel> leftHandler, final ChannelListener<? super StreamChannel> rightHandler) throws Exception {
        final XnioConfiguration config = new XnioConfiguration();
        config.setThreadFactory(threadFactory);
        Xnio xnio = Xnio.create("nio", config);
        try {
            final IoFuture<? extends Closeable> future = xnio.createPipeConnection(leftHandler, rightHandler);
            final Closeable closeable = future.get();
            try {
                body.run();
            } finally {
                IoUtils.safeClose(closeable);
            }
        } finally {
            IoUtils.safeClose(xnio);
        }
    }

    public void testOneWayPipeConnect() throws Exception {
        log.info("Test: testOneWayPipeConnect");
        threadFactory.clear();
        doOneWayPipeTest(new Runnable() {
            public void run() {
            }
        }, new ChannelListener<StreamSourceChannel>() {
            public void handleEvent(final StreamSourceChannel channel) {
            }
        }, new ChannelListener<StreamSinkChannel>() {
            public void handleEvent(final StreamSinkChannel channel) {
            }
        });
        threadFactory.await();
    }

    public void testTwoWayPipeConnect() throws Exception {
        log.info("Test: testTwoWayPipeConnect");
        threadFactory.clear();
        doTwoWayPipeTest(new Runnable() {
            public void run() {
            }
        }, new ChannelListener<StreamChannel>() {
            public void handleEvent(final StreamChannel channel) {
            }
        }, new ChannelListener<StreamChannel>() {
            public void handleEvent(final StreamChannel channel) {
            }
        });
        threadFactory.await();
    }

    public void testOneWayPipeSourceClose() throws Exception {
        log.info("Test: testOneWayPipeSourceClose");
        threadFactory.clear();
        final CountDownLatch latch = new CountDownLatch(2);
        final AtomicBoolean sourceOK = new AtomicBoolean(false);
        final AtomicBoolean sinkOK = new AtomicBoolean(false);
        doOneWayPipeTest(new Runnable() {
            public void run() {
                try {
                    assertTrue(latch.await(1500L, TimeUnit.MILLISECONDS));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }, new ChannelListener<StreamSourceChannel>() {
            public void handleEvent(final StreamSourceChannel channel) {
                try {
                    channel.getCloseSetter().set(new ChannelListener<StreamSourceChannel>() {
                        public void handleEvent(final StreamSourceChannel channel) {
                            latch.countDown();
                        }
                    });
                    channel.close();
                    sourceOK.set(true);
                } catch (Throwable t) {
                    t.printStackTrace();
                    latch.countDown();
                    throw new RuntimeException(t);
                }
            }
        }, new ChannelListener<StreamSinkChannel>() {
            public void handleEvent(final StreamSinkChannel channel) {
                try {
                    channel.getCloseSetter().set(new ChannelListener<StreamSinkChannel>() {
                        public void handleEvent(final StreamSinkChannel channel) {
                            latch.countDown();
                        }
                    });
                    channel.getWriteSetter().set(new ChannelListener<StreamSinkChannel>() {
                        public void handleEvent(final StreamSinkChannel channel) {
                            try {
                                channel.write(ByteBuffer.allocate(100));
                                channel.resumeWrites();
                            } catch (IOException e) {
                                if (e.getMessage() != null && e.getMessage().contains("roken pipe")) {
                                    sinkOK.set(true);
                                } else {
                                    e.printStackTrace();
                                }
                                IoUtils.safeClose(channel);
                            }
                        }
                    });
                    channel.resumeWrites();
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
        });
        assertTrue(sourceOK.get());
        assertTrue(sinkOK.get());
        threadFactory.await();
    }

    public void testOneWayPipeSinkClose() throws Exception {
        log.info("Test: testOneWayPipeSinkClose");
        threadFactory.clear();
        final CountDownLatch latch = new CountDownLatch(2);
        final AtomicBoolean sourceOK = new AtomicBoolean(false);
        final AtomicBoolean sinkOK = new AtomicBoolean(false);
        doOneWayPipeTest(new Runnable() {
            public void run() {
                try {
                    assertTrue(latch.await(1500L, TimeUnit.MILLISECONDS));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }, new ChannelListener<StreamSourceChannel>() {
            public void handleEvent(final StreamSourceChannel channel) {
                try {
                    log.info("In source open handler");
                    channel.getCloseSetter().set(new ChannelListener<StreamSourceChannel>() {
                        public void handleEvent(final StreamSourceChannel channel) {
                            log.info("In source close handler");
                            latch.countDown();
                        }
                    });
                    channel.getReadSetter().set(new ChannelListener<StreamSourceChannel>() {
                        public void handleEvent(final StreamSourceChannel channel) {
                            try {
                                log.info("In source read handler");
                                final int c = channel.read(ByteBuffer.allocate(100));
                                if (c == -1) {
                                    sourceOK.set(true);
                                    channel.close();
                                } else if (c == 0) {
                                    channel.resumeReads();
                                } else {
                                    log.warn("Unexpected data");
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                                IoUtils.safeClose(channel);
                            }
                        }
                    });
                    channel.resumeReads();
                } catch (Throwable t) {
                    log.error(t, "Channel closed due to error");
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
        }, new ChannelListener<StreamSinkChannel>() {
            public void handleEvent(final StreamSinkChannel channel) {
                try {
                    log.info("In sink open handler");
                    channel.getCloseSetter().set(new ChannelListener<StreamSinkChannel>() {
                        public void handleEvent(final StreamSinkChannel channel) {
                            log.info("In sink close handler");
                            latch.countDown();
                        }
                    });
                    channel.getWriteSetter().set(new ChannelListener<StreamSinkChannel>() {
                        public void handleEvent(final StreamSinkChannel channel) {
                            log.info("In sink write handler");
                            IoUtils.safeClose(channel);
                        }
                    });
                    channel.resumeWrites();
                    sinkOK.set(true);
                } catch (Throwable t) {
                    log.error(t, "Channel closed due to error");
                    latch.countDown();
                    throw new RuntimeException(t);
                }
            }
        });
        assertTrue(sourceOK.get());
        assertTrue(sinkOK.get());
        threadFactory.await();
    }

    public void testOneWayPipeSinkCloseFromOpenHandler() throws Exception {
        log.info("Test: testOneWayPipeSinkCloseFromOpenHandler");
        threadFactory.clear();
        final CountDownLatch latch = new CountDownLatch(2);
        final AtomicBoolean sourceOK = new AtomicBoolean(false);
        final AtomicBoolean sinkOK = new AtomicBoolean(false);
        doOneWayPipeTest(new Runnable() {
            public void run() {
                try {
                    assertTrue(latch.await(1500L, TimeUnit.MILLISECONDS));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }, new ChannelListener<StreamSourceChannel>() {
            public void handleEvent(final StreamSourceChannel channel) {
                try {
                    log.info("In source open handler");
                    channel.getCloseSetter().set(new ChannelListener<StreamSourceChannel>() {
                        public void handleEvent(final StreamSourceChannel channel) {
                            log.info("In source close handler");
                            latch.countDown();
                        }
                    });
                    channel.getReadSetter().set(new ChannelListener<StreamSourceChannel>() {
                        public void handleEvent(final StreamSourceChannel channel) {
                            try {
                                log.info("In source read handler");
                                final int c = channel.read(ByteBuffer.allocate(100));
                                if (c == -1) {
                                    sourceOK.set(true);
                                    channel.close();
                                } else if (c == 0) {
                                    channel.resumeReads();
                                } else {
                                    log.warn("Unexpected data");
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                                IoUtils.safeClose(channel);
                            }
                        }
                    });
                    channel.resumeReads();
                } catch (Throwable t) {
                    log.error(t, "Channel closed due to error");
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
        }, new ChannelListener<StreamSinkChannel>() {
            public void handleEvent(final StreamSinkChannel channel) {
                try {
                    log.info("In sink open handler");
                    channel.getCloseSetter().set(new ChannelListener<StreamSinkChannel>() {
                        public void handleEvent(final StreamSinkChannel channel) {
                            log.info("In sink close handler");
                            latch.countDown();
                        }
                    });
                    channel.close();
                    sinkOK.set(true);
                } catch (Throwable t) {
                    log.error(t, "Channel closed due to error");
                    latch.countDown();
                    throw new RuntimeException(t);
                }
            }
        });
        assertTrue(sourceOK.get());
        assertTrue(sinkOK.get());
        threadFactory.await();
    }

    public void testTwoWayPipeLeftClose() throws Exception {
        log.info("Test: testTwoWayPipeLeftClose");
        threadFactory.clear();
        final CountDownLatch latch = new CountDownLatch(2);
        final AtomicBoolean leftOK = new AtomicBoolean(false);
        final AtomicBoolean rightOK = new AtomicBoolean(false);
        doTwoWayPipeTest(new Runnable() {
            public void run() {
                try {
                    assertTrue(latch.await(1500L, TimeUnit.MILLISECONDS));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }, new ChannelListener<StreamChannel>() {
            public void handleEvent(final StreamChannel channel) {
                try {
                    channel.getCloseSetter().set(new ChannelListener<StreamChannel>() {
                        public void handleEvent(final StreamChannel channel) {
                            latch.countDown();
                        }
                    });
                    channel.close();
                    leftOK.set(true);
                } catch (Throwable t) {
                    t.printStackTrace();
                    latch.countDown();
                    throw new RuntimeException(t);
                }
            }
        }, new ChannelListener<StreamChannel>() {
            public void handleEvent(final StreamChannel channel) {
                try {
                    channel.getCloseSetter().set(new ChannelListener<StreamChannel>() {
                        public void handleEvent(final StreamChannel channel) {
                            latch.countDown();
                        }
                    });
                    channel.getReadSetter().set(new ChannelListener<StreamChannel>() {
                        public void handleEvent(final StreamChannel channel) {
                            try {
                                final int c = channel.read(ByteBuffer.allocate(100));
                                if (c == -1) {
                                    rightOK.set(true);
                                }
                                channel.close();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    });
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
        });
        assertTrue(leftOK.get());
        assertTrue(rightOK.get());
        threadFactory.await();
    }

    public void testTwoWayTransfer() throws Exception {
        log.info("Test: testTwoWayTransfer");
        threadFactory.clear();
        final CountDownLatch latch = new CountDownLatch(2);
        final AtomicInteger leftSent = new AtomicInteger(0);
        final AtomicInteger leftReceived = new AtomicInteger(0);
        final AtomicInteger rightSent = new AtomicInteger(0);
        final AtomicInteger rightReceived = new AtomicInteger(0);
        final AtomicBoolean delayleftStop = new AtomicBoolean();
        final AtomicBoolean delayrightStop = new AtomicBoolean();
        doTwoWayPipeTest(new Runnable() {
            public void run() {
                try {
                    assertTrue(latch.await(1500L, TimeUnit.MILLISECONDS));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }, new ChannelListener<StreamChannel>() {
            public void handleEvent(final StreamChannel channel) {
                channel.getCloseSetter().set(new ChannelListener<StreamChannel>() {
                    public void handleEvent(final StreamChannel channel) {
                        latch.countDown();
                    }
                });
                channel.getReadSetter().set(new ChannelListener<StreamChannel>() {
                    public void handleEvent(final StreamChannel channel) {
                        try {
                            final int c = channel.read(ByteBuffer.allocate(100));
                            if (c == -1) {
                                if (delayleftStop.getAndSet(true)) {
                                    channel.close();
                                }
                            } else {
                                leftReceived.addAndGet(c);
                                channel.resumeReads();
                            }
                        } catch (Throwable t) {
                            t.printStackTrace();
                            throw new RuntimeException(t);
                        }
                    }
                });
                channel.getWriteSetter().set(new ChannelListener<StreamChannel>() {
                    public void handleEvent(final StreamChannel channel) {
                        try {
                            final ByteBuffer buffer = ByteBuffer.allocate(100);
                            buffer.put("This Is A Test\r\n".getBytes("UTF-8"));
                            final int c = channel.write(flip(buffer));
                            if (leftSent.addAndGet(c) > 1000) {
                                channel.shutdownWrites();
                                if (delayleftStop.getAndSet(true)) {
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
                });
                channel.resumeReads();
                channel.resumeWrites();
            }
        }, new ChannelListener<StreamChannel>() {
            public void handleEvent(final StreamChannel channel) {
                channel.getReadSetter().set(new ChannelListener<StreamChannel>() {
                    public void handleEvent(final StreamChannel channel) {
                        try {
                            final int c = channel.read(ByteBuffer.allocate(100));
                            if (c == -1) {
                                if (delayrightStop.getAndSet(true)) {
                                    channel.close();
                                }
                            } else {
                                rightReceived.addAndGet(c);
                                channel.resumeReads();
                            }
                        } catch (Throwable t) {
                            t.printStackTrace();
                            throw new RuntimeException(t);
                        }
                    }
                });
                channel.getWriteSetter().set(new ChannelListener<StreamChannel>() {
                    public void handleEvent(final StreamChannel channel) {
                        try {
                            final ByteBuffer buffer = ByteBuffer.allocate(100);
                            buffer.put("This Is A Test Gumma\r\n".getBytes("UTF-8"));
                            final int c = channel.write(flip(buffer));
                            if (rightSent.addAndGet(c) > 1000) {
                                channel.shutdownWrites();
                                if (delayrightStop.getAndSet(true)) {
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
                });
                channel.getCloseSetter().set(new ChannelListener<StreamChannel>() {
                    public void handleEvent(final StreamChannel channel) {
                        latch.countDown();
                    }
                });
                channel.resumeReads();
                channel.resumeWrites();
            }
        });
        assertEquals(rightSent.get(), leftReceived.get());
        assertEquals(leftSent.get(), rightReceived.get());
        threadFactory.await();
    }

    public void testStopClosesBothSides() throws Exception {
        log.info("Test: testStopClosesBothSides");
        threadFactory.clear();
        final AtomicBoolean leftOK = new AtomicBoolean(false);
        final AtomicBoolean rightOK = new AtomicBoolean(false);
        doTwoWayPipeTest(new Runnable() {
            public void run() {
            }
        }, new ChannelListener<StreamChannel>() {
            public void handleEvent(final StreamChannel channel) {
                channel.getCloseSetter().set(new ChannelListener<StreamChannel>() {
                    public void handleEvent(final StreamChannel channel) {
                        leftOK.set(true);
                    }
                });
            }
        }, new ChannelListener<StreamChannel>() {
            public void handleEvent(final StreamChannel channel) {
                channel.getCloseSetter().set(new ChannelListener<StreamChannel>() {
                    public void handleEvent(final StreamChannel channel) {
                        rightOK.set(true);
                    }
                });
            }
        });
        assertTrue(leftOK.get());
        assertTrue(rightOK.get());
        threadFactory.await();
    }

    public void testStopClosesBothSidesOneWay() throws Exception {
        log.info("Test: testStopClosesBothSidesOneWay");
        threadFactory.clear();
        final AtomicBoolean sourceOK = new AtomicBoolean(false);
        final AtomicBoolean sinkOK = new AtomicBoolean(false);
        doOneWayPipeTest(new Runnable() {
            public void run() {
            }
        }, new ChannelListener<StreamSourceChannel>() {
            public void handleEvent(final StreamSourceChannel channel) {
                channel.getCloseSetter().set(new ChannelListener<StreamSourceChannel>() {
                    public void handleEvent(final StreamSourceChannel channel) {
                        sourceOK.set(true);
                    }
                });
            }
        }, new ChannelListener<StreamSinkChannel>() {
            public void handleEvent(final StreamSinkChannel channel) {
                channel.getCloseSetter().set(new ChannelListener<StreamSinkChannel>() {
                    public void handleEvent(final StreamSinkChannel channel) {
                        sinkOK.set(true);
                    }
                });
            }
        });
        assertTrue(sourceOK.get());
        assertTrue(sinkOK.get());
        threadFactory.await();
    }
}
