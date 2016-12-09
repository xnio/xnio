/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2014 Red Hat, Inc. and/or its affiliates.
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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.xnio.IoUtils.safeClose;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Ignore;
import org.junit.Test;
import org.xnio.Buffers;
import org.xnio.ChannelListener;
import org.xnio.channels.ConnectedChannel;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 * Superclass for all ssl tcp test cases.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 *
 */
public abstract class AbstractNioSslTcpTest<T extends ConnectedChannel, R extends StreamSourceChannel, W extends StreamSinkChannel> extends AbstractNioTcpTest<T, R, W> {

    protected abstract void shutdownReads(T channel) throws IOException;
    protected abstract void shutdownWrites(T channel) throws IOException;

    @Test
    @Ignore
    @Override
    public void connect() throws Exception {
        clientClose(); // with SSL, START_TLS false, we don't support the normal close, because handshake is started immediately
        // so the only way to test connection is to have either the server or the client shutdown writes and wait for a read to return -1
    }

    @Test
    @Ignore
    @Override
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
                    shutdownWrites(channel);
                    setReadListener(channel, new ChannelListener<R>() {

                        @Override
                        public void handleEvent(R sourceChannel) {
                            int c;
                            try {
                                c = sourceChannel.read(ByteBuffer.allocate(100));
                                if (c == -1) {
                                    channel.close();
                                    clientOK.set(true);
                                    latch.countDown();
                                }
                            } catch (Throwable t) {
                                log.error("In client", t);
                                latch.countDown();
                                throw new RuntimeException(t);
                            }
                        }

                    });
                    resumeReads(channel);
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
                            int c = sourceChannel.read(ByteBuffer.allocate(100));
                            if (c == -1) {
                                serverOK.set(true);
                                channel.close();
                                latch.countDown();
                            }
                        } catch (IOException t) {
                            t.printStackTrace();
                            throw new RuntimeException(t);
                        }
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
                                    log.info("client closing connection");
                                    clientOK.set(true);
                                    channel.close();
                                    return;
                                }
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
                            log.info("In server close");
                            latch.countDown();
                        }
                    });
                    shutdownWrites(channel);
                    setReadListener(channel, new ChannelListener<R>() {
                        @Override
                        public void handleEvent(R sourceChannel) {
                            int c;
                            try {
                                c = sourceChannel.read(ByteBuffer.allocate(100));
                                if (c == -1) {
                                    log.info("server closing connection");
                                    channel.close();
                                    serverOK.set(true);
                                }
                            } catch (Throwable t) {
                                log.error("In server", t);
                                latch.countDown();
                                throw new RuntimeException(t);
                            }
                        }

                    });
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
    @Ignore
    public void oneWayTransfer1() throws Exception {
        return;
    }

    @Test
    @Ignore
    public void oneWayTransfer2() throws Exception {
        return;
    }

    @Override
    public void twoWayTransfer() throws Exception {
        log.info("Test: twoWayTransfer");
        final CountDownLatch latch = new CountDownLatch(2);
        ByteBuffer dataToSend = ByteBuffer.allocate(20000);
        Buffers.fill(dataToSend, 1, 20000);
        dataToSend.rewind();
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
                final XferTracker trk = new XferTracker(20000, () -> safeClose(channel));
                channel.getCloseSetter().set(new ChannelListener<ConnectedChannel>() {
                    public void handleEvent(final ConnectedChannel connection) {
                        latch.countDown();
                    }
                });
                setReadListener(channel, new ChannelListener<R>() {
                    public void handleEvent(final R sourceChannel) {
                        try {
                            log.info("client handle readable");
                            int c;
                            while ((c = sourceChannel.read(ByteBuffer.allocate(100))) > 0) {
                                if (trk.addReceivedBytes(c)) {
                                    sourceChannel.suspendReads();
                                }
                                log.info("client received: " + c);
                            }
                        } catch (Throwable t) {
                            t.printStackTrace();
                            throw new RuntimeException(t);
                        }
                    }
                });
                final ByteBuffer sendBuf = dataToSend.duplicate();
                setWriteListener(channel, new ChannelListener<W>() {
                    public void handleEvent(final W sinkChannel) {
                        log.info("client handle writable");
                        try {
                            int c;
                            try {
                                while ((c = sinkChannel.write(sendBuf)) > 0) {
                                    log.info("client sent: " + c);
                                    if (trk.addSentBytes(c)) {
                                        setWriteListener(channel, new ChannelListener<W>() {
                                            public void handleEvent(final W sinkChannel) {
                                                try {
                                                    if (sinkChannel.flush()) {
                                                        sinkChannel.suspendWrites();
                                                        trk.markFlushed();
                                                    }
                                                } catch (Throwable t) {
                                                    t.printStackTrace();
                                                    throw new RuntimeException(t);
                                                }
                                            }
                                        });
                                    }
                                }
                            } catch (ClosedChannelException e) {
                                sinkChannel.shutdownWrites();
                                throw e;
                            }
                        } catch (Throwable t) {
                            t.printStackTrace();
                            throw new RuntimeException(t);
                        }
                    }
                });
                resumeReads(channel);
                resumeWrites(channel);
            }
        }, new ChannelListener<T>() {
            public void handleEvent(final T channel) {
                final XferTracker trk = new XferTracker(20000, () -> safeClose(channel));
                channel.getCloseSetter().set(new ChannelListener<ConnectedChannel>() {
                    public void handleEvent(final ConnectedChannel channel) {
                        latch.countDown();
                    }
                });
                setReadListener(channel, new ChannelListener<R>() {
                    public void handleEvent(final R sourceChannel) {
                        log.info("server handle readable");
                        try {
                            int c;
                            while ((c = sourceChannel.read(ByteBuffer.allocate(100))) > 0) {
                                if (trk.addReceivedBytes(c)) {
                                    sourceChannel.suspendReads();
                                }
                                log.info("server received: " + c);
                            }
                            if (c == -1) {
                                log.info("server shutting down reads");
                                sourceChannel.shutdownReads();
                            }
                        } catch (Throwable t) {
                            t.printStackTrace();
                            throw new RuntimeException(t);
                        }
                    }
                });
                final ByteBuffer sendBuf = dataToSend.duplicate();
                setWriteListener(channel, new ChannelListener<W>() {
                    public void handleEvent(final W sinkChannel) {
                        log.info("server handle writable");
                        try {
                            final ByteBuffer buffer = ByteBuffer.allocate(100);
                            buffer.put("This Is A Test\r\n".getBytes("UTF-8")).flip();
                            int c;
                            try {
                                while ((c = sinkChannel.write(sendBuf)) > 0) {
                                    log.info("server sent: " + c);
                                    if (trk.addSentBytes(c)) {
                                        setWriteListener(channel, new ChannelListener<W>() {
                                            public void handleEvent(final W sinkChannel) {
                                                try {
                                                    if (sinkChannel.flush()) {
                                                        sinkChannel.suspendWrites();
                                                        trk.markFlushed();
                                                    }
                                                } catch (Throwable t) {
                                                    t.printStackTrace();
                                                    throw new RuntimeException(t);
                                                }
                                            }
                                        });
                                        return;
                                    }
                                    buffer.rewind();
                                }
                            } catch (ClosedChannelException e) {
                                sinkChannel.shutdownWrites();
                                throw e;
                            }
                        } catch (Throwable t) {
                            t.printStackTrace();
                            throw new RuntimeException(t);
                        }
                    }
                });
                resumeReads(channel);
                resumeWrites(channel);
            }
        });
    }

    static final class XferTracker {
        private final AtomicInteger status = new AtomicInteger(0);
        private final int xferSize;
        private final Runnable complete;

        XferTracker(final int xferSize, final Runnable complete) {
            this.xferSize = xferSize;
            this.complete = complete;
        }

        boolean addSentBytes(int count) {
            int oldVal;
            do {
                oldVal = status.get();
                if ((oldVal & 0x7fff) + count > xferSize) {
                    fail("Exceeded max allowed xfer size");
                    throw new IllegalStateException();
                }
            } while (! status.compareAndSet(oldVal, oldVal + count));
            return xferSize == (oldVal & 0x7fff) + count;
        }

        void markFlushed() {
            int oldVal, newVal;
            do {
                oldVal = status.get();
                newVal = oldVal | 1 << 30;
            } while (! status.compareAndSet(oldVal, newVal));
            if (newVal == (xferSize << 15) + xferSize + (1 << 30)) {
                complete.run();
            }
        }

        boolean addReceivedBytes(int count) {
            int oldVal, newVal;
            do {
                oldVal = status.get();
                if (((oldVal >>> 15) & 0x7fff) + count > xferSize) {
                    fail("Exceeded max allowed xfer size");
                    throw new IllegalStateException();
                }
                newVal = oldVal + (count << 15);
            } while (! status.compareAndSet(oldVal, newVal));
            if (newVal == (xferSize << 15) + xferSize + (1 << 30)) {
                complete.run();
            }
            return xferSize == ((oldVal >>> 15) & 0x7fff) + count;
        }
    }
}
