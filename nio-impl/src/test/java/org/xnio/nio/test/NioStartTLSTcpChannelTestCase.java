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
import org.junit.Test;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
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

    @Test
    public void oneWayTransfer3() throws Exception {
        log.info("Test: oneWayTransfer3");
        final CountDownLatch latch = new CountDownLatch(2);
        final AtomicInteger clientSent = new AtomicInteger(0);
        final AtomicInteger serverReceived = new AtomicInteger(0);
        final AtomicBoolean clientHandshakeStarted = new AtomicBoolean(false);
        final AtomicBoolean serverHandshakeStarted = new AtomicBoolean(false);
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
                channel.getWriteSetter().set(new ChannelListener<ConnectedStreamChannel>() {
                    private boolean continueWriting(ConnectedStreamChannel channel) throws IOException {
                        if (clientSent.get() > 100) {
                            if (!clientHandshakeStarted.get()) {
                                if (serverReceived.get() == clientSent.get()) {
                                    ((ConnectedSslStreamChannel) channel).startHandshake();
                                    log.info("client starting handshake");
                                    clientHandshakeStarted.set(true);
                                    return true;
                                }
                                return false;
                            }
                            if (serverHandshakeStarted.get()) {
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
                                    log.info("client wrote " + (c + clientSent.get()));
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
                                log.info("server received " +  (c + serverReceived.get()));
                                if (serverReceived.addAndGet(c) > 100 && !serverHandshakeStarted.get()) {
                                    log.info("server starting handshake");
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

    @Test
    public void oneWayTransfer4() throws Exception {
        log.info("Test: oneWayTransfer4");
        final CountDownLatch latch = new CountDownLatch(2);
        final AtomicInteger clientReceived = new AtomicInteger(0);
        final AtomicInteger serverSent = new AtomicInteger(0);
        final AtomicBoolean clientHandshakeStarted = new AtomicBoolean(false);
        final AtomicBoolean serverHandshakeStarted = new AtomicBoolean(false);
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
                                if (clientReceived.addAndGet(c) > 100 && !clientHandshakeStarted.get()) {
                                    ((ConnectedSslStreamChannel) channel).startHandshake();
                                    clientHandshakeStarted.set(true);
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
                        if (serverSent.get() > 100) {
                            if (!serverHandshakeStarted.get()) {
                                if (clientReceived.get() == serverSent.get()) {
                                    ((ConnectedSslStreamChannel) channel).startHandshake();
                                    log.info("server starting handshake");
                                    serverHandshakeStarted.set(true);
                                    return true;
                                }
                                return false;
                            }
                            if (clientHandshakeStarted.get()) {
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

    @Test
    public void twoWayTransferWithHandshake() throws Exception {
        log.info("Test: twoWayTransferWithHandshake");
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

                    private boolean continueReading() throws IOException {
                        return clientHandshakeStarted.get() || clientReceived.get() < 101;
                    }

                    public void handleEvent(final ConnectedStreamChannel channel) {
                        log.info("client handle read events");
                        try {
                            int c = 0;
                            while (continueReading() && (c = channel.read(ByteBuffer.allocate(100))) > 0) {
                                log.info("client received: "+ (clientReceived.get() + c));
                                clientReceived.addAndGet(c);
                            }
                            if (c == -1) {
                                log.info("client shutdown reads");
                                channel.close();
                            }
                        } catch (Throwable t) {
                            t.printStackTrace();
                        }
                    }
                });
                channel.getWriteSetter().set(new ChannelListener<ConnectedStreamChannel>() {
                    private boolean continueWriting(ConnectedStreamChannel channel) throws IOException {
                        if (clientSent.get() > 100) {
                            if (!clientHandshakeStarted.get()) {
                                if (serverReceived.get() == clientSent.get() && serverSent.get() > 100 && clientReceived.get() == serverSent.get() ) {
                                    ((ConnectedSslStreamChannel) channel).startHandshake();
                                    log.info("client starting handshake");
                                    clientHandshakeStarted.set(true);
                                    return true;
                                }
                                return false;
                            }
                            if (clientHandshakeStarted.get()) {
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
                                    log.info("clientSent: " + (clientSent.get() + c));
                                    if (clientSent.addAndGet(c) > 1000) {
                                        final ChannelListener<ConnectedStreamChannel> listener = new ChannelListener<ConnectedStreamChannel>() {
                                            public void handleEvent(final ConnectedStreamChannel channel) {
                                                try {
                                                    if (channel.flush()) {
                                                        try {
                                                            log.info("client closing channel");
                                                            channel.shutdownWrites();
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
                                log.info("server received: "+ (serverReceived.get() + c));
                                serverReceived.addAndGet(c);
                            }
                            if (c == -1) {
                                log.info("server shutdown reads");
                                channel.close();
                            }
                        } catch (Throwable t) {
                            t.printStackTrace();
                            throw new RuntimeException(t);
                        }
                    }
                });
                channel.getWriteSetter().set(new ChannelListener<ConnectedStreamChannel>() {

                    private boolean continueWriting(ConnectedStreamChannel channel) throws IOException {
                        if (serverSent.get() > 100) {
                            if (!serverHandshakeStarted.get()) {
                                if (clientReceived.get() == serverSent.get() && clientSent.get() > 100 && serverReceived.get() == clientSent.get() ) {
                                    ((ConnectedSslStreamChannel) channel).startHandshake();
                                    log.info("server starting handshake");
                                    serverHandshakeStarted.set(true);
                                    return true;
                                }
                                return false;
                            }
                            if (clientHandshakeStarted.get()) {
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
                                    log.info("server sent: "+ (serverSent.get() + c));
                                    if (serverSent.addAndGet(c) > 1000) {
                                        final ChannelListener<ConnectedStreamChannel> listener = new ChannelListener<ConnectedStreamChannel>() {
                                            public void handleEvent(final ConnectedStreamChannel channel) {
                                                try {
                                                    if (channel.flush()) {
                                                        try {
                                                            log.info("server closing channel");
                                                            channel.shutdownWrites();
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

    @Override
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
        }, new ChannelListener<ConnectedStreamChannel>() {
            public void handleEvent(final ConnectedStreamChannel channel) {
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
                } catch (Throwable t) {
                    log.errorf(t, "Failed to close channel (propagating as RT exception)");
                    latch.countDown();
                    throw new RuntimeException(t);
                }
            }
        }, new ChannelListener<ConnectedStreamChannel>() {
            public void handleEvent(final ConnectedStreamChannel channel) {
                try {
                    channel.getCloseSetter().set(new ChannelListener<ConnectedChannel>() {
                        public void handleEvent(final ConnectedChannel channel) {
                            log.info("at server close listener");
                            latch.countDown();
                        }
                    });
                    channel.getReadSetter().set(new ChannelListener<ConnectedStreamChannel>() {
                        public void handleEvent(final ConnectedStreamChannel channel) {
                                try {
                                    int res = channel.read(ByteBuffer.allocate(100));
                                    log.info("server read " + res);
                                    // with start TLS, client nasty close behaves differently, no IOException is thrown
                                    if (res == -1) {
                                        serverOK.set(true);
                                        IoUtils.safeClose(channel);
                                        return;
                                    }
                                    if (res > 0) IoUtils.safeClose(channel);
                                } catch (Throwable t) {
                                    t.printStackTrace();
                                    throw new RuntimeException(t);
                                }
                        }
                    });
                    log.info("resuming reads for server");
                    channel.resumeReads();
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

    @Override
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
        }, new ChannelListener<ConnectedStreamChannel>() {
            public void handleEvent(final ConnectedStreamChannel channel) {
                try {
                    log.info("Client opened");
                    channel.getCloseSetter().set(new ChannelListener<ConnectedChannel>() {
                        public void handleEvent(final ConnectedChannel channel) {
                            latch.countDown();
                        }
                    });
                    channel.getReadSetter().set(new ChannelListener<ConnectedStreamChannel>() {
                        public void handleEvent(final ConnectedStreamChannel channel) {
                            try {
                                int res = channel.read(ByteBuffer.allocate(100));
                                // with start TLS, server nasty close behaves differently, no IOException is thrown
                                if (res == -1) {
                                    clientOK.set(true);
                                    IoUtils.safeClose(channel);
                                    return;
                                }
                                if (res > 0) IoUtils.safeClose(channel);
                            } catch (Throwable t) {
                                t.printStackTrace();
                                throw new RuntimeException(t);
                            }
                        }
                    });
                    channel.getWriteSetter().set(new ChannelListener<ConnectedStreamChannel>() {
                        public void handleEvent(final ConnectedStreamChannel channel) {
                            try {
                                if (channel.write(ByteBuffer.wrap(new byte[] { 1 })) > 0) {
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
                    channel.getCloseSetter().set(new ChannelListener<ConnectedChannel>() {
                        public void handleEvent(final ConnectedChannel channel) {
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
    }
}
