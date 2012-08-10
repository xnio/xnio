/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
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
import org.xnio.ChannelPipe;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 * Abstract test class for {@link ChannelPipe} usage test.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 *
 */
public abstract class AbstractNioChannelPipeTest<S extends StreamSourceChannel, T extends StreamSinkChannel> {

    private static final Logger log = Logger.getLogger("TEST");
    protected final List<Throwable> problems = new CopyOnWriteArrayList<Throwable>();
    protected AtomicBoolean leftChannelOK;
    protected AtomicBoolean rightChannelOK;

    /**
     * Create the pipe channel.
     * 
     * @param worker the worker
     * @return       the create pipe channel
     */
    protected abstract ChannelPipe<S, T> createPipeChannel(XnioWorker worker) throws IOException;

    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected void doConnectionTest(final Runnable body, final ChannelListener<? extends S> leftChannelHandler, final ChannelListener<? super T> rightChannelHandler) throws Exception {
        final Xnio xnio = Xnio.getInstance("nio", NioFullDuplexChannelPipeTestCase.class.getClassLoader());
        final XnioWorker worker = xnio.createWorker(OptionMap.create(Options.WORKER_WRITE_THREADS, 2, Options.WORKER_READ_THREADS, 2));
        try {
            final ChannelPipe<? extends S, ? extends T> channelPipe = createPipeChannel(worker);
            try {
                final Thread invokeLeftChannelHandler = new Thread(new ChannelListenerInvoker(leftChannelHandler, channelPipe.getLeftSide()));
                final Thread invokeRightChannelHandler = new Thread(new ChannelListenerInvoker(rightChannelHandler, channelPipe.getRightSide()));
                invokeLeftChannelHandler.start();
                invokeRightChannelHandler.start();
                invokeLeftChannelHandler.join();
                invokeRightChannelHandler.join();
                body.run();
                channelPipe.getLeftSide().close();
                channelPipe.getRightSide().close();
            } catch (Exception e) {
                log.errorf(e, "Error running body");
                throw e;
            } catch (Error e) {
                log.errorf(e, "Error running body");
                throw e;
            }finally {
                IoUtils.safeClose(channelPipe.getLeftSide());
                IoUtils.safeClose(channelPipe.getRightSide());
            }
        } finally {
            worker.shutdown();
            worker.awaitTermination(1L, TimeUnit.MINUTES);
        }
    }

    @Before
    public void setupTest() {
        problems.clear();
        leftChannelOK = new AtomicBoolean(false);
        rightChannelOK = new AtomicBoolean(false);
    }

    @After
    public void checkProblems() {
        assertTrue(leftChannelOK.get());
        assertTrue(rightChannelOK.get());
        for (Throwable problem : problems) {
            log.error("Test exception", problem);
        }
        assertTrue(problems.isEmpty());
    }

    @Test
    public void pipeCreation() throws Exception {
        log.info("Test: pipeCreation");
        doConnectionTest(new Runnable() {public void run(){} }, null, ChannelListeners.closingChannelListener());
        leftChannelOK.set(true);
        rightChannelOK.set(true);
    }

    @Test
    public void leftChannelClose() throws Exception {
        log.info("Test: leftChannelClose");
        final CountDownLatch latch = new CountDownLatch(4);
        doConnectionTest(new LatchAwaiter(latch), new ChannelListener<S>() {
            public void handleEvent(final S channel) {
                log.info("In pipe creation, leftChannel setup");
                try {
                    channel.getCloseSetter().set(new ChannelListener<StreamSourceChannel>() {
                        public void handleEvent(final StreamSourceChannel channel) {
                            log.info("In left channel close");
                            latch.countDown();
                        }
                    });
                    channel.close();
                    leftChannelOK.set(true);
                    latch.countDown();
                } catch (Throwable t) {
                    log.error("In left channel", t);
                    latch.countDown();
                    throw new RuntimeException(t);
                }
            }
        }, new ChannelListener<T>() {
            public void handleEvent(final T channel) {
                log.info("In pipe creation, rightChannel setup");
                channel.getCloseSetter().set(new ChannelListener<StreamSinkChannel>() {
                    public void handleEvent(final StreamSinkChannel channel) {
                        log.info("In right channel close");
                        latch.countDown();
                    }
                });
                channel.getWriteSetter().set(new ChannelListener<StreamSinkChannel>() {
                    public void handleEvent(final StreamSinkChannel channel) {
                        log.info("In right channel readable");
                        try {
                            channel.write(ByteBuffer.allocate(100));
                        } catch (IOException e) {
                            rightChannelOK.set(true);
                            IoUtils.safeClose(channel);
                        }
                        latch.countDown();
                    }
                });
                channel.resumeWrites();
            }
        });
    }

    @Test
    public void rightChannelClose() throws Exception {
        log.info("Test: rightChannelClose");
        final CountDownLatch latch = new CountDownLatch(2);
        doConnectionTest(new LatchAwaiter(latch), new ChannelListener<S>() {
            public void handleEvent(final S channel) {
                try {
                    channel.getCloseSetter().set(new ChannelListener<StreamSourceChannel>() {
                        public void handleEvent(final StreamSourceChannel channel) {
                            latch.countDown();
                        }
                    });
                    channel.getReadSetter().set(new ChannelListener<StreamSourceChannel>() {
                        public void handleEvent(final StreamSourceChannel channel) {
                            try {
                                final int c = channel.read(ByteBuffer.allocate(100));
                                if (c == -1) {
                                    leftChannelOK.set(true);
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
                    channel.resumeReads();
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
                    channel.getCloseSetter().set(new ChannelListener<StreamSinkChannel>() {
                        public void handleEvent(final StreamSinkChannel channel) {
                            rightChannelOK.set(true);
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
    }

    @Test
    public void oneWayTransfer() throws Exception {
        log.info("Test: oneWayTransfer");
        final CountDownLatch latch = new CountDownLatch(2);
        final AtomicInteger leftChannelReceived = new AtomicInteger(0);
        final AtomicInteger rightChannelSent = new AtomicInteger(0);
        doConnectionTest(new LatchAwaiter(latch), new ChannelListener<S>() {
            public void handleEvent(final S channel) {
                channel.getCloseSetter().set(new ChannelListener<StreamSourceChannel>() {
                    public void handleEvent(final StreamSourceChannel channel) {
                        latch.countDown();
                    }
                });
                channel.getReadSetter().set(new ChannelListener<StreamSourceChannel>() {
                    public void handleEvent(final StreamSourceChannel channel) {
                        try {
                            int c;
                            while ((c = channel.read(ByteBuffer.allocate(100))) > 0) {
                                leftChannelReceived.addAndGet(c);
                            }
                            if (c == -1) {
                                IoUtils.safeClose(channel);
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
                channel.resumeReads();
            }
        }, new ChannelListener<T>() {
            public void handleEvent(final T channel) {
                channel.getCloseSetter().set(new ChannelListener<StreamSinkChannel>() {
                    public void handleEvent(final StreamSinkChannel channel) {
                        latch.countDown();
                    }
                });
                final ByteBuffer buffer = ByteBuffer.allocate(100);
                try {
                    buffer.put("This Is A Test Gumma\r\n".getBytes("UTF-8")).flip();
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }
                channel.getWriteSetter().set(new ChannelListener<StreamSinkChannel>() {
                    public void handleEvent(final StreamSinkChannel channel) {
                        try {
                            int c;
                            while ((c = channel.write(buffer)) > 0) {
                                if (rightChannelSent.addAndGet(c) > 1000) {
                                    final ChannelListener<StreamSinkChannel> listener = new ChannelListener<StreamSinkChannel>() {
                                        public void handleEvent(final StreamSinkChannel channel) {
                                            try {
                                                IoUtils.safeClose(channel);
                                            } catch (Throwable t) {
                                                log.errorf(t, "Failed to close channel (propagating as RT exception)");
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
                        } catch (Throwable t) {
                            log.errorf(t, "Failed to close channel (propagating as RT exception)");
                            throw new RuntimeException(t);
                        }
                    }
                });
                channel.resumeWrites();
            }
        });
        assertEquals(rightChannelSent.get(), leftChannelReceived.get());
        leftChannelOK.set(true);
        rightChannelOK.set(true);
    }

    @Test
    public void leftSourceChannelNastyClose() throws Exception {
        log.info("Test: leftSourceChannelNastyClose");
        final CountDownLatch latch = new CountDownLatch(2);
        doConnectionTest(new LatchAwaiter(latch), new ChannelListener<S>() {
            public void handleEvent(final S channel) {
                try {
                    channel.getCloseSetter().set(new ChannelListener<StreamSourceChannel>() {
                        public void handleEvent(final StreamSourceChannel channel) {
                            latch.countDown();
                        }
                    });
                    channel.setOption(Options.CLOSE_ABORT, Boolean.TRUE);
                    channel.close();
                    leftChannelOK.set(true);
                } catch (Throwable t) {
                    log.errorf(t, "Failed to close channel (propagating as RT exception)");
                    latch.countDown();
                    throw new RuntimeException(t);
                }
            }
        }, new ChannelListener<T>() {
            public void handleEvent(final T channel) {
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
                            } catch (IOException e) { // broken pipe
                                IoUtils.safeClose(channel);
                                rightChannelOK.set(true);
                                return;
                            }
                        }
                    });
                    channel.resumeWrites();
                } catch (Throwable t) {
                    log.errorf(t, "Failed to close channel (propagating as RT exception)");
                    latch.countDown();
                    throw new RuntimeException(t);
                }
            }
        });
    }

    @Test
    public void rightSinkChannelNastyClose() throws Exception {
        log.info("Test: rightSinkChannelNastyClose");
        final CountDownLatch latch = new CountDownLatch(2);
        final CountDownLatch rightChannelLatch = new CountDownLatch(1);
        doConnectionTest(new LatchAwaiter(latch), new ChannelListener<S>() {
            public void handleEvent(final S channel) {
                try {
                    log.info("Left channel opened");
                    channel.getCloseSetter().set(new ChannelListener<StreamSourceChannel>() {
                        public void handleEvent(final StreamSourceChannel channel) {
                            latch.countDown();
                        }
                    });
                    channel.getReadSetter().set(new ChannelListener<StreamSourceChannel>() {
                        public void handleEvent(final StreamSourceChannel channel) {
                            int res;
                            try {
                                res = channel.read(ByteBuffer.allocate(100));
                            } catch (IOException e) {
                                IoUtils.safeClose(channel);
                                return;
                            }
                            if (res == -1) {
                                leftChannelOK.set(true);
                                IoUtils.safeClose(channel);
                            } else {
                                channel.wakeupReads();
                            }
                        }
                    });
                    channel.wakeupReads();
                    rightChannelLatch.countDown();
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
                    log.info("Right channel opened");
                    channel.getCloseSetter().set(new ChannelListener<StreamSinkChannel>() {
                        public void handleEvent(final StreamSinkChannel channel) {
                            latch.countDown();
                        }
                    });
                    channel.getWriteSetter().set(new ChannelListener<StreamSinkChannel>() {
                        public void handleEvent(final StreamSinkChannel channel) {
                            try {
                                if (channel.write(ByteBuffer.allocate(1)) > 0) {
                                    IoUtils.safeClose(channel);
                                    rightChannelOK.set(true);
                                }
                            } catch (IOException e) {
                                IoUtils.safeClose(channel);
                            }
                        }
                    });
                    channel.resumeWrites();
                } catch (Throwable t) {
                    log.error("Error occurred on server", t);
                    latch.countDown();
                    throw new RuntimeException(t);
                }
            }
        });
    }

    private static class ChannelListenerInvoker<T extends Channel> implements Runnable {
        private final ChannelListener<T> channelListener;
        private final T channel;

        public ChannelListenerInvoker(ChannelListener<T> l, T c) {
            channelListener = l;
            channel = c;
        }

        public void run() {
            ChannelListeners.invokeChannelListener(channel, channelListener);
        }
    }

    protected static class LatchAwaiter implements Runnable {
        private final CountDownLatch latch;

        public LatchAwaiter(CountDownLatch l) {
            latch = l;
        }
        
        public void run() {
            try {
                assertTrue(latch.await(500L, TimeUnit.MILLISECONDS));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
