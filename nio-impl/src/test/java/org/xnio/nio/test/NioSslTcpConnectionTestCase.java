/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2013 Red Hat, Inc. and/or its affiliates.
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
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.BeforeClass;
import org.xnio.ChannelListener;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.StreamConnection;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.BoundChannel;
import org.xnio.channels.Channels;
import org.xnio.channels.SslConnection;
import org.xnio.conduits.ConduitStreamSinkChannel;
import org.xnio.conduits.ConduitStreamSourceChannel;
import org.xnio.ssl.XnioSsl;

/**
 * Test for {@code XnioSsl} connections.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 *
 */
public class NioSslTcpConnectionTestCase extends AbstractNioTcpTest<SslConnection, ConduitStreamSourceChannel, ConduitStreamSinkChannel>{
    private XnioSsl xnioSsl;
    private static final String KEY_STORE_PROPERTY = "javax.net.ssl.keyStore";
    private static final String KEY_STORE_PASSWORD_PROPERTY = "javax.net.ssl.keyStorePassword";
    private static final String TRUST_STORE_PROPERTY = "javax.net.ssl.trustStore";
    private static final String TRUST_STORE_PASSWORD_PROPERTY = "javax.net.ssl.trustStorePassword";
    private static final String DEFAULT_KEY_STORE = "keystore.jks";
    private static final String DEFAULT_KEY_STORE_PASSWORD = "jboss-remoting-test";

    @BeforeClass
    public static void setKeyStoreAndTrustStore() {
        final URL storePath = NioSslTcpChannelTestCase.class.getClassLoader().getResource(DEFAULT_KEY_STORE);
        if (System.getProperty(KEY_STORE_PROPERTY) == null) {
            System.setProperty(KEY_STORE_PROPERTY, storePath.getFile());
        }
        if (System.getProperty(KEY_STORE_PASSWORD_PROPERTY) == null) {
            System.setProperty(KEY_STORE_PASSWORD_PROPERTY, DEFAULT_KEY_STORE_PASSWORD);
        }
        if (System.getProperty(TRUST_STORE_PROPERTY) == null) {
            System.setProperty(TRUST_STORE_PROPERTY, storePath.getFile());
        }
        if (System.getProperty(TRUST_STORE_PASSWORD_PROPERTY) == null) {
            System.setProperty(TRUST_STORE_PASSWORD_PROPERTY, DEFAULT_KEY_STORE_PASSWORD);
        }
    }

    @Override
    protected AcceptingChannel<? extends SslConnection> createServer(XnioWorker worker, InetSocketAddress address,
            ChannelListener<AcceptingChannel<SslConnection>> openListener, OptionMap optionMap) throws IOException {
        return xnioSsl.createSslConnectionServer(worker, address,  openListener,  optionMap);
    }

    @Override
    protected IoFuture<? extends SslConnection> connect(XnioWorker worker, InetSocketAddress address,
            ChannelListener<SslConnection> openListener, ChannelListener<? super BoundChannel> bindListener,
            OptionMap optionMap) {
        return xnioSsl.openSslConnection(worker, address,  openListener, bindListener, optionMap);
    }

    @Override
    protected void setReadListener(SslConnection connection, ChannelListener<ConduitStreamSourceChannel> readListener) {
        connection.getSourceChannel().setReadListener(readListener);
    }

    @Override
    protected void setWriteListener(SslConnection connection, ChannelListener<ConduitStreamSinkChannel> writeListener) {
        connection.getSinkChannel().setWriteListener(writeListener);
    }

    @Override
    protected void resumeReads(SslConnection connection) {
        connection.getSourceChannel().resumeReads();
    }

    @Override
    protected void resumeWrites(SslConnection connection) {
        connection.getSinkChannel().resumeWrites();
    }

    @Override
    protected void doConnectionTest(final Runnable body, final ChannelListener<? super SslConnection> clientHandler, final ChannelListener<? super SslConnection> serverHandler) throws Exception {
        xnioSsl = Xnio.getInstance("nio", NioSslTcpChannelTestCase.class.getClassLoader()).getSslProvider(OptionMap.EMPTY);
        super.doConnectionTest(body,  clientHandler, serverHandler);
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
            public void handleEvent(final SslConnection connection) {
                log.info("In client open");
                try {
                    connection.getCloseSetter().set(new ChannelListener<StreamConnection>() {
                        public void handleEvent(final StreamConnection connection) {
                            log.info("In client close");
                            latch.countDown();
                        }
                    });
                    Channels.shutdownWritesBlocking(connection.getSinkChannel());
                    int c = Channels.readBlocking(connection.getSourceChannel(), ByteBuffer.allocate(100));
                    connection.close();
                    if (c == -1) {
                        clientOK.set(true);
                    }
                    latch.countDown();
                } catch (Throwable t) {
                    log.error("In client", t);
                    latch.countDown();
                    throw new RuntimeException(t);
                }
            }
        }, new ChannelListener<SslConnection>() {
            public void handleEvent(final SslConnection connection) {
                log.info("In server opened");
                connection.getCloseSetter().set(new ChannelListener<StreamConnection>() {
                    public void handleEvent(final StreamConnection connection) {
                        log.info("In server close");
                        latch.countDown();
                    }
                });
                final ConduitStreamSourceChannel sourceChannel = connection.getSourceChannel();
                sourceChannel.setReadListener(new ChannelListener<ConduitStreamSourceChannel>() {
                    public void handleEvent(final ConduitStreamSourceChannel sourceChannel) {
                        log.info("In server readable");
                        try {
                            Channels.shutdownWritesBlocking(connection.getSinkChannel());
                            int c = Channels.readBlocking(sourceChannel, ByteBuffer.allocate(100));
                            connection.close();
                            if (c == -1) {
                                serverOK.set(true);
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        latch.countDown();
                    }
                });
                sourceChannel.resumeReads();
            }
        });
        assertTrue(serverOK.get());
        assertTrue(clientOK.get());
    }

    @Override
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
        }, new ChannelListener<SslConnection>() {
            public void handleEvent(final SslConnection connection) {
                connection.getCloseSetter().set(new ChannelListener<StreamConnection>() {
                    public void handleEvent(final StreamConnection connection) {
                        latch.countDown();
                    }
                });
                final ConduitStreamSourceChannel sourceChannel = connection.getSourceChannel();
                sourceChannel.getReadSetter().set(new ChannelListener<ConduitStreamSourceChannel>() {
                    public void handleEvent(final ConduitStreamSourceChannel sourceChannel) {
                        try {
                            int c;
                            while ((c = sourceChannel.read(ByteBuffer.allocate(100))) > 0) {
                                clientReceived.addAndGet(c);
                            }
                            if (c == -1) {
                                sourceChannel.shutdownReads();
                            }
                        } catch (Throwable t) {
                            t.printStackTrace();
                            throw new RuntimeException(t);
                        }
                    }
                });
                final ConduitStreamSinkChannel sinkChannel = connection.getSinkChannel();
                sinkChannel.setWriteListener(new ChannelListener<ConduitStreamSinkChannel>() {
                    public void handleEvent(final ConduitStreamSinkChannel sinkChannel) {
                        try {
                            final ByteBuffer buffer = ByteBuffer.allocate(100);
                            buffer.put("This Is A Test\r\n".getBytes("UTF-8")).flip();
                            int c;
                            try {
                                while ((c = sinkChannel.write(buffer)) > 0) {
                                    if (clientSent.addAndGet(c) > 1000) {
                                        final ChannelListener<ConduitStreamSinkChannel> listener = new ChannelListener<ConduitStreamSinkChannel>() {
                                            public void handleEvent(final ConduitStreamSinkChannel sinkChannel) {
                                                try {
                                                    if (sinkChannel.flush()) {
                                                        final ChannelListener<ConduitStreamSinkChannel> listener = new ChannelListener<ConduitStreamSinkChannel>() {
                                                            public void handleEvent(final ConduitStreamSinkChannel sinkChannel) {
                                                                // really lame, but due to the way SSL shuts down...
                                                                if (!(clientReceived.get() < serverSent.get() || serverReceived.get() < clientSent.get() || serverSent.get() == 0 || clientSent.get() == 0)) {
                                                                                try {
                                                                    connection.close();
                                                                } catch (Throwable t) {
                                                                    t.printStackTrace();
                                                                    throw new RuntimeException(t);
                                                                }
                                                                }
                                                            }
                                                        };
                                                        sinkChannel.setWriteListener(listener);
                                                        listener.handleEvent(sinkChannel);
                                                        return;
                                                    }
                                                } catch (Throwable t) {
                                                    t.printStackTrace();
                                                    throw new RuntimeException(t);
                                                }
                                            }
                                        };
                                        sinkChannel.setWriteListener(listener);
                                        listener.handleEvent(sinkChannel);
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

                sourceChannel.resumeReads();
                sinkChannel.resumeWrites();
            }
        }, new ChannelListener<SslConnection>() {
            public void handleEvent(final SslConnection connection) {
                connection.getCloseSetter().set(new ChannelListener<StreamConnection>() {
                    public void handleEvent(final StreamConnection connection) {
                        latch.countDown();
                    }
                });
                final ConduitStreamSourceChannel sourceChannel = connection.getSourceChannel();
                sourceChannel.setReadListener(new ChannelListener<ConduitStreamSourceChannel>() {
                    public void handleEvent(final ConduitStreamSourceChannel sourceChannel) {
                        try {
                            int c;
                            while ((c = sourceChannel.read(ByteBuffer.allocate(100))) > 0) {
                                serverReceived.addAndGet(c);
                            }
                            if (c == -1) {
                                sourceChannel.shutdownReads();
                            }
                        } catch (Throwable t) {
                            t.printStackTrace();
                            throw new RuntimeException(t);
                        }
                    }
                });
                final ConduitStreamSinkChannel sinkChannel = connection.getSinkChannel();
                sinkChannel.setWriteListener(new ChannelListener<ConduitStreamSinkChannel>() {
                    public void handleEvent(final ConduitStreamSinkChannel sinkChannel) {
                        try {
                            final ByteBuffer buffer = ByteBuffer.allocate(100);
                            buffer.put("This Is A Test\r\n".getBytes("UTF-8")).flip();
                            int c;
                            try {
                                while ((c = sinkChannel.write(buffer)) > 0) {
                                    if (serverSent.addAndGet(c) > 1000) {
                                        final ChannelListener<ConduitStreamSinkChannel> listener = new ChannelListener<ConduitStreamSinkChannel>() {
                                            public void handleEvent(final ConduitStreamSinkChannel sinkChannel) {
                                                try {
                                                    if (sinkChannel.flush()) {
                                                        final ChannelListener<ConduitStreamSinkChannel> listener = new ChannelListener<ConduitStreamSinkChannel>() {
                                                            public void handleEvent(final ConduitStreamSinkChannel sinkChannel) {
                                                                // really lame, but due to the way SSL shuts down...
                                                                if (!(clientReceived.get() < serverSent.get() || serverReceived.get() < clientSent.get() || serverSent.get() == 0 || clientSent.get() == 0)) {
                                                                    try {
                                                                    connection.close();
                                                                } catch (Throwable t) {
                                                                    t.printStackTrace();
                                                                    throw new RuntimeException(t);
                                                                }
                                                                }
                                                            }
                                                        };
                                                        sinkChannel.setWriteListener(listener);
                                                        listener.handleEvent(sinkChannel);

                                                        return;
                                                    }
                                                } catch (Throwable t) {
                                                    t.printStackTrace();
                                                    throw new RuntimeException(t);
                                                }
                                            }
                                        };
                                        sinkChannel.setWriteListener(listener);
                                        listener.handleEvent(sinkChannel);
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
                sourceChannel.resumeReads();
                sinkChannel.resumeWrites();
            }
        });
        assertEquals(serverSent.get(), clientReceived.get());
        assertEquals(clientSent.get(), serverReceived.get());
    }

    @Override // FIXME
    public void serverNastyClose() {}
}
