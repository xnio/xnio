package org.jboss.xnio.core.nio.test;

import junit.framework.TestCase;
import org.jboss.xnio.spi.Lifecycle;
import org.jboss.xnio.spi.Provider;
import org.jboss.xnio.spi.Pipe;
import org.jboss.xnio.spi.OneWayPipe;
import org.jboss.xnio.channels.StreamChannel;
import org.jboss.xnio.channels.StreamSourceChannel;
import org.jboss.xnio.channels.StreamSinkChannel;
import org.jboss.xnio.IoHandler;
import org.jboss.xnio.IoUtils;
import static org.jboss.xnio.Buffers.flip;
import static org.jboss.xnio.IoUtils.nullHandler;
import org.jboss.xnio.core.nio.NioProvider;
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
    private static final void stop(Lifecycle lifecycle) {
        try {
            lifecycle.stop();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private static final void destroy(Lifecycle lifecycle) {
        try {
            lifecycle.destroy();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void doOneWayPipeTest(final Runnable body, final IoHandler<? super StreamSourceChannel> sourceHandler, final IoHandler<? super StreamSinkChannel> sinkHandler) throws Exception {
        synchronized (this) {
            final Provider provider = new NioProvider();
            provider.create();
            try {
                provider.start();
                try {
                    final OneWayPipe pipe = provider.createOneWayPipe();
                    pipe.getSourceEnd().setHandler(sourceHandler);
                    pipe.getSinkEnd().setHandler(sinkHandler);
                    pipe.create();
                    try {
                        pipe.start();
                        try {
                            body.run();
                        } finally {
                            stop(pipe);
                        }
                    } finally {
                        destroy(pipe);
                    }
                } finally {
                    stop(provider);
                }
            } finally {
                destroy(provider);
            }
        }
    }

    private void doTwoWayPipeTest(final Runnable body, final IoHandler<? super StreamChannel> leftHandler, final IoHandler<? super StreamChannel> rightHandler) throws Exception {
        synchronized (this) {
            final Provider provider = new NioProvider();
            provider.create();
            try {
                provider.start();
                try {
                    final Pipe pipe = provider.createPipe();
                    pipe.getLeftEnd().setHandler(leftHandler);
                    pipe.getRightEnd().setHandler(rightHandler);
                    pipe.create();
                    try {
                        pipe.start();
                        try {
                            body.run();
                        } finally {
                            stop(pipe);
                        }
                    } finally {
                        destroy(pipe);
                    }
                } finally {
                    stop(provider);
                }
            } finally {
                destroy(provider);
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

            public void handleClose(final StreamSourceChannel channel) {
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

            public void handleClose(final StreamSinkChannel channel) {
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

            public void handleClose(final StreamSourceChannel channel) {
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

            public void handleClose(final StreamSinkChannel channel) {
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

            public void handleClose(final StreamChannel channel) {
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

            public void handleClose(final StreamChannel channel) {
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

            public void handleClose(final StreamChannel channel) {
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

            public void handleClose(final StreamChannel channel) {
                latch.countDown();
            }
        });
        assertEquals(rightSent.get(), leftReceived.get());
        assertEquals(leftSent.get(), rightReceived.get());
    }

}
