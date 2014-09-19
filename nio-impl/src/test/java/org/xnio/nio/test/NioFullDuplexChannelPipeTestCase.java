/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.logging.Logger;
import org.junit.Test;
import org.xnio.ChannelListener;
import org.xnio.ChannelPipe;
import org.xnio.IoUtils;
import org.xnio.Options;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamChannel;

/**
 * Test for full duplex channel pipe usage.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
public final class NioFullDuplexChannelPipeTestCase extends AbstractNioChannelPipeTest<StreamChannel, StreamChannel> {

    private static final Logger log = Logger.getLogger("TEST");

    @Override
    protected ChannelPipe<StreamChannel, StreamChannel> createPipeChannel(XnioWorker worker) throws IOException {
        return worker.createFullDuplexPipe();
    }

    @Test
    public void leftChannelClose() throws Exception {
        log.info("Test: leftChannelClose");
        final CountDownLatch latch = new CountDownLatch(4);
        doConnectionTest(new LatchAwaiter(latch), new ChannelListener<StreamChannel>() {
            public void handleEvent(final StreamChannel channel) {
                log.info("In pipe creation, leftChannel setup");
                try {
                    channel.getCloseSetter().set(new ChannelListener<StreamChannel>() {
                        public void handleEvent(final StreamChannel channel) {
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
        }, new ChannelListener<StreamChannel>() {
            public void handleEvent(final StreamChannel channel) {
                log.info("In pipe creation, rightChannel setup");
                channel.getCloseSetter().set(new ChannelListener<StreamChannel>() {
                    public void handleEvent(final StreamChannel channel) {
                        log.info("In right channel close");
                        latch.countDown();
                    }
                });
                channel.getReadSetter().set(new ChannelListener<StreamChannel>() {
                    public void handleEvent(final StreamChannel channel) {
                        log.info("In right channel readable");
                        try {
                            final int c = channel.read(ByteBuffer.allocate(100));
                            if (c == -1) {
                                rightChannelOK.set(true);
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
    }

    @Test
    public void rightChannelClose() throws Exception {
        log.info("Test: rightChannelClose");
        final CountDownLatch latch = new CountDownLatch(2);
        doConnectionTest(new LatchAwaiter(latch), new ChannelListener<StreamChannel>() {
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
        }, new ChannelListener<StreamChannel>() {
            public void handleEvent(final StreamChannel channel) {
                try {
                    channel.getCloseSetter().set(new ChannelListener<StreamChannel>() {
                        public void handleEvent(final StreamChannel channel) {
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
    public void twoWayTransfer() throws Exception {
        log.info("Test: twoWayTransfer");
        final CountDownLatch latch = new CountDownLatch(2);
        final AtomicInteger leftChannelSent = new AtomicInteger(0);
        final AtomicInteger leftChannelReceived = new AtomicInteger(0);
        final AtomicInteger rightChannelSent = new AtomicInteger(0);
        final AtomicInteger rightChannelReceived = new AtomicInteger(0);
        doConnectionTest(new LatchAwaiter(latch), new ChannelListener<StreamChannel>() {
            public void handleEvent(final StreamChannel channel) {
                channel.getCloseSetter().set(new ChannelListener<StreamChannel>() {
                    public void handleEvent(final StreamChannel channel) {
                        latch.countDown();
                    }
                });
                channel.getReadSetter().set(new ChannelListener<StreamChannel>() {
                    public void handleEvent(final StreamChannel channel) {
                        try {
                            int c;
                            while ((c = channel.read(ByteBuffer.allocate(100))) > 0) {
                                leftChannelReceived.addAndGet(c);
                            }
                            if (c == -1) {
                                channel.shutdownReads();
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
                channel.getWriteSetter().set(new ChannelListener<StreamChannel>() {
                    public void handleEvent(final StreamChannel channel) {
                        try {
                            int c;
                            while ((c = channel.write(buffer)) > 0) {
                                if (leftChannelSent.addAndGet(c) > 1000) {
                                    final ChannelListener<StreamChannel> listener = new ChannelListener<StreamChannel>() {
                                        public void handleEvent(final StreamChannel channel) {
                                            try {
                                                channel.shutdownWrites();
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
                channel.resumeReads();
                channel.resumeWrites();
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
                            int c;
                            while ((c = channel.read(ByteBuffer.allocate(100))) > 0) {
                                rightChannelReceived.addAndGet(c);
                            }
                            if (c == -1) {
                                channel.shutdownReads();
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
                channel.getWriteSetter().set(new ChannelListener<StreamChannel>() {
                    public void handleEvent(final StreamChannel channel) {
                        try {
                            int c;
                            while ((c = channel.write(buffer)) > 0) {
                                if (rightChannelSent.addAndGet(c) > 1000) {
                                    final ChannelListener<StreamChannel> listener = new ChannelListener<StreamChannel>() {
                                        public void handleEvent(final StreamChannel channel) {
                                            try {
                                                channel.shutdownWrites();
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
                channel.resumeReads();
                channel.resumeWrites();
            }
        });
        assertEquals(rightChannelSent.get(), leftChannelReceived.get());
        assertEquals(leftChannelSent.get(), rightChannelReceived.get());
        leftChannelOK.set(true);
        rightChannelOK.set(true);
    }
}
