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
import org.jboss.xnio.spi.Lifecycle;
import org.jboss.xnio.spi.Provider;
import org.jboss.xnio.spi.PipeService;
import org.jboss.xnio.spi.OneWayPipeService;
import org.jboss.xnio.channels.StreamChannel;
import org.jboss.xnio.channels.StreamSourceChannel;
import org.jboss.xnio.channels.StreamSinkChannel;
import org.jboss.xnio.IoHandler;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.nio.NioProvider;
import org.jboss.xnio.test.support.LoggingHelper;
import static org.jboss.xnio.Buffers.flip;
import static org.jboss.xnio.IoUtils.nullHandler;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.ByteBuffer;
import java.io.IOException;

/**
 *
 */
public final class NioPipeTestCase extends TestCase {
    static {
        LoggingHelper.init();
    }

    private static final void stop(Lifecycle lifecycle) {
        try {
            lifecycle.stop();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private void doOneWayPipeTest(final Runnable body, final IoHandler<? super StreamSourceChannel> sourceHandler, final IoHandler<? super StreamSinkChannel> sinkHandler) throws Exception {
        synchronized (this) {
            final Provider provider = new NioProvider();
            provider.start();
            try {
                final OneWayPipeService pipeService = provider.createOneWayPipe();
                pipeService.getSourceEnd().setHandler(sourceHandler);
                pipeService.getSinkEnd().setHandler(sinkHandler);
                pipeService.start();
                try {
                    body.run();
                } finally {
                    stop(pipeService);
                }
            } finally {
                stop(provider);
            }
        }
    }

    private void doTwoWayPipeTest(final Runnable body, final IoHandler<? super StreamChannel> leftHandler, final IoHandler<? super StreamChannel> rightHandler) throws Exception {
        synchronized (this) {
            final Provider provider = new NioProvider();
            provider.start();
            try {
                final PipeService pipeService = provider.createPipe();
                pipeService.getLeftEnd().setHandler(leftHandler);
                pipeService.getRightEnd().setHandler(rightHandler);
                pipeService.start();
                try {
                    body.run();
                } finally {
                    stop(pipeService);
                }
            } finally {
                stop(provider);
            }
        }
    }

    public void testOneWayPipeConnect() throws Exception {
        doOneWayPipeTest(new Runnable() {
            public void run() {
            }
        }, nullHandler(), nullHandler());
    }

    public void testTwoWayPipeConnect() throws Exception {
        doTwoWayPipeTest(new Runnable() {
            public void run() {
            }
        }, nullHandler(), nullHandler());
    }

    public void testOneWayPipeSourceClose() throws Exception {
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
        }, new IoHandler<StreamSourceChannel>() {
            public void handleOpened(final StreamSourceChannel channel) {
                try {
                    channel.close();
                    sourceOK.set(true);
                } catch (Throwable t) {
                    t.printStackTrace();
                    latch.countDown();
                    throw new RuntimeException(t);
                }
            }

            public void handleReadable(final StreamSourceChannel channel) {
            }

            public void handleWritable(final StreamSourceChannel channel) {
            }

            public void handleClosed(final StreamSourceChannel channel) {
                latch.countDown();
            }
        }, new IoHandler<StreamSinkChannel>() {
            public void handleOpened(final StreamSinkChannel channel) {
                try {
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

            public void handleReadable(final StreamSinkChannel channel) {
            }

            public void handleWritable(final StreamSinkChannel channel) {
                try {
                    channel.write(ByteBuffer.allocate(100));
                    channel.resumeWrites();
                } catch (IOException e) {
                    if (e.getMessage().contains("roken pipe")) {
                        sinkOK.set(true);
                    } else {
                        e.printStackTrace();
                    }
                    IoUtils.safeClose(channel);
                }
            }

            public void handleClosed(final StreamSinkChannel channel) {
                latch.countDown();
            }
        });
        assertTrue(sourceOK.get());
        assertTrue(sinkOK.get());
    }

    public void testOneWayPipeSinkClose() throws Exception {
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
        }, new IoHandler<StreamSourceChannel>() {
            public void handleOpened(final StreamSourceChannel channel) {
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

            public void handleReadable(final StreamSourceChannel channel) {
                try {
                    final int c = channel.read(ByteBuffer.allocate(100));
                    if (c == -1) {
                        sourceOK.set(true);
                        channel.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    IoUtils.safeClose(channel);
                }
            }

            public void handleWritable(final StreamSourceChannel channel) {
            }

            public void handleClosed(final StreamSourceChannel channel) {
                latch.countDown();
            }
        }, new IoHandler<StreamSinkChannel>() {
            public void handleOpened(final StreamSinkChannel channel) {
                try {
                    channel.close();
                    sinkOK.set(true);
                } catch (Throwable t) {
                    t.printStackTrace();
                    latch.countDown();
                    throw new RuntimeException(t);
                }
            }

            public void handleReadable(final StreamSinkChannel channel) {
            }

            public void handleWritable(final StreamSinkChannel channel) {
            }

            public void handleClosed(final StreamSinkChannel channel) {
                latch.countDown();
            }
        });
        assertTrue(sourceOK.get());
        assertTrue(sinkOK.get());
    }

    public void testTwoWayPipeLeftClose() throws Exception {
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
        }, new IoHandler<StreamChannel>() {
            public void handleOpened(final StreamChannel channel) {
                try {
                    channel.close();
                    leftOK.set(true);
                } catch (Throwable t) {
                    t.printStackTrace();
                    latch.countDown();
                    throw new RuntimeException(t);
                }
            }

            public void handleReadable(final StreamChannel channel) {
            }

            public void handleWritable(final StreamChannel channel) {
            }

            public void handleClosed(final StreamChannel channel) {
                latch.countDown();
            }
        }, new IoHandler<StreamChannel>() {
            public void handleOpened(final StreamChannel channel) {
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

            public void handleReadable(final StreamChannel channel) {
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

            public void handleWritable(final StreamChannel channel) {
            }

            public void handleClosed(final StreamChannel channel) {
                latch.countDown();
            }
        });
        assertTrue(leftOK.get());
        assertTrue(rightOK.get());
    }

    public void testTwoWayTransfer() throws Exception {
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
        }, new IoHandler<StreamChannel>() {
            public void handleOpened(final StreamChannel channel) {
                channel.resumeReads();
                channel.resumeWrites();
            }

            public void handleReadable(final StreamChannel channel) {
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

            public void handleWritable(final StreamChannel channel) {
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

            public void handleClosed(final StreamChannel channel) {
                latch.countDown();
            }
        }, new IoHandler<StreamChannel>() {
            public void handleOpened(final StreamChannel channel) {
                channel.resumeReads();
                channel.resumeWrites();
            }

            public void handleReadable(final StreamChannel channel) {
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

            public void handleWritable(final StreamChannel channel) {
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

            public void handleClosed(final StreamChannel channel) {
                latch.countDown();
            }
        });
        assertEquals(rightSent.get(), leftReceived.get());
        assertEquals(leftSent.get(), rightReceived.get());
    }

    public void testStopClosesBothSides() throws Exception {
        final AtomicBoolean leftOK = new AtomicBoolean(false);
        final AtomicBoolean rightOK = new AtomicBoolean(false);
        doTwoWayPipeTest(new Runnable() {
            public void run() {
            }
        }, new IoHandler<StreamChannel>() {
            public void handleOpened(final StreamChannel channel) {
            }

            public void handleReadable(final StreamChannel channel) {
            }

            public void handleWritable(final StreamChannel channel) {
            }

            public void handleClosed(final StreamChannel channel) {
                leftOK.set(true);
            }
        }, new IoHandler<StreamChannel>() {
            public void handleOpened(final StreamChannel channel) {
            }

            public void handleReadable(final StreamChannel channel) {
            }

            public void handleWritable(final StreamChannel channel) {
            }

            public void handleClosed(final StreamChannel channel) {
                rightOK.set(true);
            }
        });
        assertTrue(leftOK.get());
        assertTrue(rightOK.get());
    }

    public void testStopClosesBothSidesOneWay() throws Exception {
        final AtomicBoolean sourceOK = new AtomicBoolean(false);
        final AtomicBoolean sinkOK = new AtomicBoolean(false);
        doOneWayPipeTest(new Runnable() {
            public void run() {
            }
        }, new IoHandler<StreamSourceChannel>() {
            public void handleOpened(final StreamSourceChannel channel) {
            }

            public void handleReadable(final StreamSourceChannel channel) {
            }

            public void handleWritable(final StreamSourceChannel channel) {
            }

            public void handleClosed(final StreamSourceChannel channel) {
                sourceOK.set(true);
            }
        }, new IoHandler<StreamSinkChannel>() {
            public void handleOpened(final StreamSinkChannel channel) {
            }

            public void handleReadable(final StreamSinkChannel channel) {
            }

            public void handleWritable(final StreamSinkChannel channel) {
            }

            public void handleClosed(final StreamSinkChannel channel) {
                sinkOK.set(true);
            }
        });
        assertTrue(sourceOK.get());
        assertTrue(sinkOK.get());
    }
}
