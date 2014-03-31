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
import org.xnio.ssl.SslConnection;
import org.xnio.conduits.ConduitStreamSinkChannel;
import org.xnio.conduits.ConduitStreamSourceChannel;


/**
 * Test for {@code XnioSsl} connections with the start TLS option enabled.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 *
 */
public class NioStartTLSTcpConnectionTestCase extends NioSslTcpConnectionTestCase {

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
        }, new ChannelListener<SslConnection>() {
            public void handleEvent(final SslConnection channel) {
                log.info("In client open");
                try {
                    channel.getCloseSetter().set(new ChannelListener<SslConnection>() {
                        public void handleEvent(final SslConnection channel) {
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
        }, new ChannelListener<SslConnection>() {
            public void handleEvent(final SslConnection channel) {
                log.info("In server opened");
                channel.getCloseSetter().set(new ChannelListener<ConnectedChannel>() {
                    public void handleEvent(final ConnectedChannel channel) {
                        log.info("In server close");
                        latch.countDown();
                    }
                });
                setReadListener(channel, new ChannelListener<ConduitStreamSourceChannel>() {
                    public void handleEvent(final ConduitStreamSourceChannel sourceChannel) {
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
        log.info("Test: oneWayTransfer");
        final CountDownLatch latch = new CountDownLatch(2);
        final AtomicInteger clientSent = new AtomicInteger(0);
        final AtomicInteger serverReceived = new AtomicInteger(0);
        final AtomicBoolean clientHandshakeStarted = new AtomicBoolean(false);
        doConnectionTest(new Runnable() {
            public void run() {
                try {
                    assertTrue(latch.await(500000L, TimeUnit.MILLISECONDS));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }, new ChannelListener<SslConnection>() {
            public void handleEvent(final SslConnection connection) {
                connection.getCloseSetter().set(new ChannelListener<SslConnection>() {
                    public void handleEvent(final SslConnection channel) {
                        latch.countDown();
                    }
                });
                connection.getSinkChannel().setWriteListener(new ChannelListener<ConduitStreamSinkChannel>() {
                    private boolean continueWriting() throws IOException {
                        if (!clientHandshakeStarted.get() && clientSent.get() > 100) {
                            if (serverReceived.get() == clientSent.get()) {
                                connection.startHandshake();
                                //log.info("client starting handshake");
                                clientHandshakeStarted.set(true);
                                return true;
                            }
                            return false;
                        }
                        return true;
                    }

                    public void handleEvent(final ConduitStreamSinkChannel channel) {
                        try {
                            final ByteBuffer buffer = ByteBuffer.allocate(100);
                            buffer.put("This Is A Test\r\n".getBytes("UTF-8")).flip();
                            int c;
                            try {
                                while (continueWriting() && (c = channel.write(buffer)) > 0) {
                                    if (clientSent.addAndGet(c) > 1000) {
                                        final ChannelListener<ConduitStreamSinkChannel> listener = new ChannelListener<ConduitStreamSinkChannel>() {
                                            public void handleEvent(final ConduitStreamSinkChannel channel) {
                                                try {
                                                    if (channel.flush()) {
                                                        final ChannelListener<ConduitStreamSinkChannel> listener = new ChannelListener<ConduitStreamSinkChannel>() {
                                                            public void handleEvent(final ConduitStreamSinkChannel channel) {
                                                                // really lame, but due to the way SSL shuts down...
                                                                if (serverReceived.get() == clientSent.get()) {
                                                                    try {
                                                                        channel.shutdownWrites();
                                                                        connection.close();
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
                                        channel.setWriteListener(listener);
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
                connection.getSinkChannel().resumeWrites();
            }
        }, new ChannelListener<SslConnection>() {
            public void handleEvent(final SslConnection connection) {
                connection.getCloseSetter().set(new ChannelListener<SslConnection>() {
                    public void handleEvent(final SslConnection channel) {
                        latch.countDown();
                    }
                });
                connection.getSourceChannel().setReadListener(new ChannelListener<ConduitStreamSourceChannel>() {
                    public void handleEvent(final ConduitStreamSourceChannel channel) {
                        try {
                            int c;
                            while ((c = channel.read(ByteBuffer.allocate(100))) > 0) {
                                if (serverReceived.addAndGet(c) > 100) {
                                    connection.startHandshake();
                                }
                            }
                            if (c == -1) {
                                channel.shutdownReads();
                                connection.close();
                            }
                        } catch (Throwable t) {
                            t.printStackTrace();
                            throw new RuntimeException(t);
                        }
                    }
                });
                connection.getSourceChannel().resumeReads();
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
        }, new ChannelListener<SslConnection>() {
            public void handleEvent(final SslConnection connection) {
                connection.getCloseSetter().set(new ChannelListener<SslConnection>() {
                    public void handleEvent(final SslConnection connection) {
                        latch.countDown();
                    }
                });
                connection.getSourceChannel().setReadListener(new ChannelListener<ConduitStreamSourceChannel>() {
                    public void handleEvent(final ConduitStreamSourceChannel channel) {
                        try {
                            int c;
                            while ((c = channel.read(ByteBuffer.allocate(100))) > 0) {
                                if (clientReceived.addAndGet(c) > 100) {
                                    connection.startHandshake();
                                }
                            }
                            if (c == -1) {
                                channel.shutdownReads();
                                connection.close();
                            }
                        } catch (Throwable t) {
                            t.printStackTrace();
                            throw new RuntimeException(t);
                        }
                    }
                });

                connection.getSourceChannel().resumeReads();
            }
        }, new ChannelListener<SslConnection>() {
            public void handleEvent(final SslConnection connection) {
                connection.getCloseSetter().set(new ChannelListener<SslConnection>() {
                    public void handleEvent(final SslConnection connection) {
                        latch.countDown();
                    }
                });
                connection.getSinkChannel().setWriteListener(new ChannelListener<ConduitStreamSinkChannel>() {
                    private boolean continueWriting() throws IOException {
                        if (!serverHandshakeStarted.get() && serverSent.get() > 100) {
                            if (clientReceived.get() == serverSent.get()) {
                                connection.startHandshake();
                                //log.info("client starting handshake");
                                serverHandshakeStarted.set(true);
                                return true;
                            }
                            return false;
                        }
                        return true;
                    }

                    public void handleEvent(final ConduitStreamSinkChannel channel) {
                        try {
                            final ByteBuffer buffer = ByteBuffer.allocate(100);
                            buffer.put("This Is A Test\r\n".getBytes("UTF-8")).flip();
                            int c;
                            try {
                                while (continueWriting() && (c = channel.write(buffer)) > 0) {
                                    if (serverSent.addAndGet(c) > 100) {
                                        connection.startHandshake();
                                        if (serverSent.get() > 1000) {
                                            final ChannelListener<ConduitStreamSinkChannel> listener = new ChannelListener<ConduitStreamSinkChannel>() {
                                                public void handleEvent(final ConduitStreamSinkChannel channel) {
                                                    try {
                                                        if (channel.flush()) {
                                                            final ChannelListener<ConduitStreamSinkChannel> listener = new ChannelListener<ConduitStreamSinkChannel>() {
                                                                public void handleEvent(final ConduitStreamSinkChannel channel) {
                                                                    // really lame, but due to the way SSL shuts down...
                                                                    if (clientReceived.get() == serverSent.get()) {
                                                                        try {
                                                                            channel.shutdownWrites();
                                                                            connection.close();
                                                                        } catch (Throwable t) {
                                                                            t.printStackTrace();
                                                                            throw new RuntimeException(t);
                                                                        }
                                                                    }
                                                                }
                                                            };
                                                            channel.setWriteListener(listener);
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
                connection.getSinkChannel().resumeWrites();
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
        }, new ChannelListener<SslConnection>() {
            public void handleEvent(final SslConnection connection) {
                connection.getCloseSetter().set(new ChannelListener<SslConnection>() {
                    public void handleEvent(final SslConnection channel) {
                        latch.countDown();
                    }
                });
                connection.getSourceChannel().setReadListener(new ChannelListener<ConduitStreamSourceChannel>() {

                    private boolean continueReading() throws IOException {
                        return clientHandshakeStarted.get() || clientReceived.get() < 101;
                    }

                    public void handleEvent(final ConduitStreamSourceChannel channel) {
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
                            throw new RuntimeException(t);
                        }
                    }
                });
                connection.getSinkChannel().setWriteListener(new ChannelListener<ConduitStreamSinkChannel>() {

                    private boolean continueWriting(ConduitStreamSinkChannel channel) throws IOException {
                        if (!clientHandshakeStarted.get() && clientSent.get() > 100) {
                            if (serverReceived.get() == clientSent.get() && serverSent.get() > 100 && clientReceived.get() == serverSent.get() ) {
                                connection.startHandshake();
                                //log.info("client starting handshake");
                                clientHandshakeStarted.set(true);
                                return true;
                            }
                            return false;
                        }
                        return true;
                    }

                    public void handleEvent(final ConduitStreamSinkChannel channel) {
                                                try {
                            final ByteBuffer buffer = ByteBuffer.allocate(100);
                            buffer.put("This Is A Test\r\n".getBytes("UTF-8")).flip();
                            int c = 0;
                            try {
                                while (continueWriting(channel) && (clientSent.get() > 1000 || (c = channel.write(buffer)) > 0)) {
                                    //log.info("clientSent: " + (clientSent.get() + c));
                                    if (clientSent.addAndGet(c) > 1000) {
                                        final ChannelListener<ConduitStreamSinkChannel> listener = new ChannelListener<ConduitStreamSinkChannel>() {
                                            public void handleEvent(final ConduitStreamSinkChannel channel) {
                                                try {
                                                    if (channel.flush()) {
                                                        try {
                                                            //log.info("client closing channel");
                                                            connection.close();
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
                                } catch (Exception cce) {/* do nothing */}
                                throw e;
                            }
                        } catch (Throwable t) {
                            t.printStackTrace();
                            throw new RuntimeException(t);
                        }
                    }
                });

                connection.getSourceChannel().resumeReads();
                connection.getSinkChannel().resumeWrites();
            }
        }, new ChannelListener<SslConnection>() {
            public void handleEvent(final SslConnection connection) {
                connection.getCloseSetter().set(new ChannelListener<SslConnection>() {
                    public void handleEvent(final SslConnection connection) {
                        latch.countDown();
                    }
                });
                connection.getSourceChannel().setReadListener(new ChannelListener<ConduitStreamSourceChannel>() {
                    private boolean continueReading() throws IOException {
                        return serverHandshakeStarted.get() || serverReceived.get() < 101;
                    }

                    public void handleEvent(final ConduitStreamSourceChannel channel) {
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
                connection.getSinkChannel().setWriteListener(new ChannelListener<ConduitStreamSinkChannel>() {

                    private boolean continueWriting() throws IOException {
                        if (!serverHandshakeStarted.get() && serverSent.get() > 100) {
                            if (clientReceived.get() == serverSent.get() && clientSent.get() > 100 && serverReceived.get() == clientSent.get() ) {
                                //log.info("server starting handshake");
                                connection.startHandshake();
                                serverHandshakeStarted.set(true);
                                return true;
                            }
                            return false;
                        }
                        return true;
                    }

                    public void handleEvent(final ConduitStreamSinkChannel channel) {
                        try {
                            final ByteBuffer buffer = ByteBuffer.allocate(100);
                            buffer.put("This Is A Test\r\n".getBytes("UTF-8")).flip();
                            int c;
                            try {
                                while (continueWriting() && (c = channel.write(buffer)) > 0) {
                                    //log.info("server sent: "+ (serverSent.get() + c));
                                    if (serverSent.addAndGet(c) > 1000) {
                                        final ChannelListener<ConduitStreamSinkChannel> listener = new ChannelListener<ConduitStreamSinkChannel>() {
                                            public void handleEvent(final ConduitStreamSinkChannel channel) {
                                                try {
                                                    if (channel.flush()) {
                                                        try {
                                                            //log.info("server closing channel");
                                                            connection.close();
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
                                        channel.setWriteListener(listener);
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
                connection.getSourceChannel().resumeReads();
                connection.getSinkChannel().resumeWrites();
            }
        });
        assertEquals(serverSent.get(), clientReceived.get());
        assertEquals(clientSent.get(), serverReceived.get());
    }
}
