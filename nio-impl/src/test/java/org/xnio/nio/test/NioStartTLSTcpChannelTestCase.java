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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.xnio.ChannelListener;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.channels.ConnectedChannel;
import org.xnio.channels.ConnectedSslStreamChannel;
import org.xnio.channels.ConnectedStreamChannel;


/**
 * Test for {@code XnioSsl} channels with the start TLS option enabled.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 *
 */
public class NioStartTLSTcpChannelTestCase extends NioSslTcpChannelTestCase {

    @Before
    public void setStartTLSOption() {
        final OptionMap optionMap = OptionMap.create(Options.SSL_STARTTLS, true);
        super.setServerOptionMap(optionMap);
        super.setClientOptionMap(optionMap);
    }

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
        }, new ChannelListener<ConnectedSslStreamChannel>() {
            public void handleEvent(final ConnectedSslStreamChannel channel) {
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
        }, new ChannelListener<ConnectedSslStreamChannel>() {
            public void handleEvent(final ConnectedSslStreamChannel channel) {
                log.info("In server opened");
                channel.getCloseSetter().set(new ChannelListener<ConnectedChannel>() {
                    public void handleEvent(final ConnectedChannel channel) {
                        log.info("In server close");
                        latch.countDown();
                    }
                });
                setReadListener(channel, new ChannelListener<ConnectedSslStreamChannel>() {
                    public void handleEvent(final ConnectedSslStreamChannel sourceChannel) {
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

    @Override
    public void oneWayTransfer1() throws Exception {
        log.info("Test: oneWayTransfer1");
        final CountDownLatch latch = new CountDownLatch(2);
        final AtomicInteger clientSent = new AtomicInteger(0);
        final AtomicInteger serverReceived = new AtomicInteger(0);
        final AtomicBoolean clientHandshakeStarted = new AtomicBoolean(false);
        final AtomicBoolean serverHandshakeStarted = new AtomicBoolean(false);
        doConnectionTest(new Runnable() {
            public void run() {
                try {
                    assertTrue(latch.await(500000L, TimeUnit.MILLISECONDS));
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
                channel.getWriteSetter().set(new ChannelListener<ConnectedStreamChannel>() {
                    private boolean continueWriting(ConnectedStreamChannel channel) throws IOException {
                        if (!clientHandshakeStarted.get() && clientSent.get() > 100) {
                            if (serverReceived.get() == clientSent.get()) {
                                ((ConnectedSslStreamChannel) channel).startHandshake();
                                clientHandshakeStarted.set(true);
                                return true;
                            }
                            return false;
                        }
                        return true;
                    }

                    public void handleEvent(final ConnectedStreamChannel channel) {
                        try {
                            final ByteBuffer buffer = ByteBuffer.allocate(100);
                            buffer.put("This Is A Test\r\n".getBytes("UTF-8")).flip();
                            int c;
                            try {
                                while (continueWriting(channel) && (c = channel.write(buffer)) > 0) {
                                    if (clientSent.addAndGet(c) > 1000) {
                                        final ChannelListener<ConnectedStreamChannel> listener = new ChannelListener<ConnectedStreamChannel>() {
                                            public void handleEvent(final ConnectedStreamChannel channel) {
                                                try {
                                                    if (channel.flush()) {
                                                        try {
                                                            channel.shutdownWrites();
                                                            channel.close();
                                                        } catch (Throwable t) {
                                                            t.printStackTrace();
                                                            throw new RuntimeException(t);
                                                        }
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
                                }
                                buffer.rewind();
                            } catch (ClosedChannelException e) {
                                try {
                                    channel.shutdownWrites();
                                } catch (Exception exception) {}
                                throw e;
                            }
                        } catch (Throwable t) {
                            t.printStackTrace();
                            throw new RuntimeException(t);
                        }
                    }
                });
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
                                if (serverReceived.addAndGet(c) > 100 && !serverHandshakeStarted.get()) {
                                    ((ConnectedSslStreamChannel) channel).startHandshake();
                                    serverHandshakeStarted.set(true);
                                }
                            }
                            if (c == -1) {
                                channel.shutdownReads();
                                channel.close();
                            }
                        } catch (Throwable t) {
                            t.printStackTrace();
                            throw new RuntimeException(t);
                        }
                    }
                });
                channel.resumeReads();
            }
        });
        assertEquals(clientSent.get(), serverReceived.get());
    }

    @Override
    public void oneWayTransfer2() throws Exception {
        log.info("Test: oneWayTransfer2");
        final CountDownLatch latch = new CountDownLatch(2);
        final AtomicInteger clientReceived = new AtomicInteger(0);
        final AtomicInteger serverSent = new AtomicInteger(0);
        final AtomicBoolean serverHandshakeStarted = new AtomicBoolean(false);
        doConnectionTest(new Runnable() {
            public void run() {
                try {
                    assertTrue(latch.await(500000L, TimeUnit.MILLISECONDS));
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
                                if (clientReceived.addAndGet(c) > 100) {
                                    ((ConnectedSslStreamChannel) channel).startHandshake();
                                }
                            }
                            if (c == -1) {
                                channel.shutdownReads();
                                channel.close();
                            }
                        } catch (Throwable t) {
                            t.printStackTrace();
                            throw new RuntimeException(t);
                        }
                    }
                });

                channel.resumeReads();
            }
        }, new ChannelListener<ConnectedStreamChannel>() {
            public void handleEvent(final ConnectedStreamChannel channel) {
                channel.getCloseSetter().set(new ChannelListener<ConnectedStreamChannel>() {
                    public void handleEvent(final ConnectedStreamChannel channel) {
                        latch.countDown();
                    }
                });
                channel.getWriteSetter().set(new ChannelListener<ConnectedStreamChannel>() {
                    private boolean continueWriting(ConnectedStreamChannel channel) throws IOException {
                        if (!serverHandshakeStarted.get() && serverSent.get() > 100) {
                            if (clientReceived.get() == serverSent.get()) {
                                ((ConnectedSslStreamChannel) channel).startHandshake();
                                serverHandshakeStarted.set(true);
                                return true;
                            }
                            return false;
                        }
                        return true;
                    }

                    public void handleEvent(final ConnectedStreamChannel channel) {
                        try {
                            final ByteBuffer buffer = ByteBuffer.allocate(100);
                            buffer.put("This Is A Test\r\n".getBytes("UTF-8")).flip();
                            int c;
                            try {
                                while (continueWriting(channel) && (c = channel.write(buffer)) > 0) {
                                    if (serverSent.addAndGet(c) > 1000) {
                                        final ChannelListener<ConnectedStreamChannel> listener = new ChannelListener<ConnectedStreamChannel>() {
                                            public void handleEvent(final ConnectedStreamChannel channel) {
                                                try {
                                                    if (channel.flush()) {
                                                        final ChannelListener<ConnectedStreamChannel> listener = new ChannelListener<ConnectedStreamChannel>() {
                                                            public void handleEvent(final ConnectedStreamChannel channel) {
                                                                // really lame, but due to the way SSL shuts down...
                                                                if (clientReceived.get() == serverSent.get()) {
                                                                    try {
                                                                        channel.shutdownWrites();
                                                                        channel.close();
                                                                    } catch (Throwable t) {
                                                                        t.printStackTrace();
                                                                        throw new RuntimeException(t);
                                                                    }
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
                                }
                                buffer.rewind();
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
                channel.resumeWrites();
            }
        });
        assertEquals(serverSent.get(), clientReceived.get());
    }

    @Override
    public void twoWayTransfer() throws Exception {
        log.info("Test: twoWayTransfer");
        final CountDownLatch latch = new CountDownLatch(2);
        final AtomicInteger clientSent = new AtomicInteger(0);
        final AtomicInteger clientReceived = new AtomicInteger(0);
        final AtomicInteger serverSent = new AtomicInteger(0);
        final AtomicInteger serverReceived = new AtomicInteger(0);
        final AtomicBoolean clientHandshakeStarted = new AtomicBoolean(false);
        final AtomicBoolean serverHandshakeStarted = new AtomicBoolean(false);
        doConnectionTest(new Runnable() {
            public void run() {
                try {
                    assertTrue(latch.await(500000L, TimeUnit.MILLISECONDS));
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

                    private boolean continueReading() throws IOException {
                        return clientHandshakeStarted.get() || clientReceived.get() < 101;
                    }

                    public void handleEvent(final ConnectedStreamChannel channel) {
                        //log.info("client handle read events");
                        try {
                            int c = 0;
                            while (continueReading() && (c = channel.read(ByteBuffer.allocate(100))) > 0) {
                                //log.info("client received: "+ (clientReceived.get() + c));
                                clientReceived.addAndGet(c);
                            }
                            if (c == -1) {
                                //log.info("client shutdown reads");
                                channel.shutdownReads();
                            }
                        } catch (Throwable t) {
                            t.printStackTrace();
                        }
                    }
                });
                channel.getWriteSetter().set(new ChannelListener<ConnectedStreamChannel>() {

                    private boolean continueWriting(ConnectedStreamChannel channel) throws IOException {
                        if (!clientHandshakeStarted.get() && clientSent.get() > 100) {
                            if (serverReceived.get() == clientSent.get() && serverSent.get() > 100 && clientReceived.get() == serverSent.get() ) {
                                ((ConnectedSslStreamChannel) channel).startHandshake();
                                //log.info("client starting handshake");
                                clientHandshakeStarted.set(true);
                                return true;
                            }
                            return false;
                        }
                        return true;
                    }

                    public void handleEvent(final ConnectedStreamChannel channel) {
                                                try {
                            final ByteBuffer buffer = ByteBuffer.allocate(100);
                            buffer.put("This Is A Test\r\n".getBytes("UTF-8")).flip();
                            int c = 0;
                            try {
                                while (continueWriting(channel) && (clientSent.get() > 1000 || (c = channel.write(buffer)) > 0)) {
                                    //log.info("clientSent: " + (clientSent.get() + c));
                                    if (clientSent.addAndGet(c) > 1000) {
                                        final ChannelListener<ConnectedStreamChannel> listener = new ChannelListener<ConnectedStreamChannel>() {
                                            public void handleEvent(final ConnectedStreamChannel channel) {
                                                try {
                                                    if (channel.flush()) {
                                                        try {
                                                            //log.info("client closing channel");
                                                            channel.close();
                                                        } catch (Throwable t) {
                                                            t.printStackTrace();
                                                            throw new RuntimeException(t);
                                                        }
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
                                try {
                                    channel.shutdownWrites();
                                } catch (Exception cce) {/* do nothing */}
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
                    private boolean continueReading() throws IOException {
                        return serverHandshakeStarted.get() || serverReceived.get() < 101;
                    }

                    public void handleEvent(final ConnectedStreamChannel channel) {
                        try {
                            int c = 0;
                            while (continueReading() && (c = channel.read(ByteBuffer.allocate(100))) > 0) {
                                //log.info("server received: "+ (serverReceived.get() + c));
                                serverReceived.addAndGet(c);
                            }
                            if (c == -1) {
                                //log.info("server shutdown reads");
                                channel.shutdownReads();
                            }
                        } catch (Throwable t) {
                            t.printStackTrace();
                            throw new RuntimeException(t);
                        }
                    }
                });
                channel.getWriteSetter().set(new ChannelListener<ConnectedStreamChannel>() {

                    private boolean continueWriting(ConnectedStreamChannel channel) throws IOException {
                        if (!serverHandshakeStarted.get() && serverSent.get() > 100) {
                            if (clientReceived.get() == serverSent.get() && clientSent.get() > 100 && serverReceived.get() == clientSent.get() ) {
                                //log.info("server starting handshake");
                                ((ConnectedSslStreamChannel) channel).startHandshake();
                                serverHandshakeStarted.set(true);
                                return true;
                            }
                            return false;
                        }
                        return true;
                    }

                    public void handleEvent(final ConnectedStreamChannel channel) {
                        try {
                            final ByteBuffer buffer = ByteBuffer.allocate(100);
                            buffer.put("This Is A Test\r\n".getBytes("UTF-8")).flip();
                            int c;
                            try {
                                while (continueWriting(channel) && (c = channel.write(buffer)) > 0) {
                                    //log.info("server sent: "+ (serverSent.get() + c));
                                    if (serverSent.addAndGet(c) > 1000) {
                                        final ChannelListener<ConnectedStreamChannel> listener = new ChannelListener<ConnectedStreamChannel>() {
                                            public void handleEvent(final ConnectedStreamChannel channel) {
                                                try {
                                                    if (channel.flush()) {
                                                        try {
                                                            //log.info("server closing channel");
                                                            channel.close();
                                                        } catch (Throwable t) {
                                                            t.printStackTrace();
                                                            throw new RuntimeException(t);
                                                        }
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
                                }
                                buffer.rewind();
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
    }
}
