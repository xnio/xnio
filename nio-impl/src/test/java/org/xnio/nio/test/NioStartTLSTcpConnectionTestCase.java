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
import org.xnio.conduits.ConduitStreamSinkChannel;
import org.xnio.conduits.ConduitStreamSourceChannel;
import org.xnio.ssl.SslConnection;


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

    @Test
    public void oneWayTransfer3() throws Exception {
        log.info("Test: oneWayTransfer");
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
        }, new ChannelListener<SslConnection>() {
            public void handleEvent(final SslConnection connection) {
                connection.getCloseSetter().set(new ChannelListener<SslConnection>() {
                    public void handleEvent(final SslConnection channel) {
                        latch.countDown();
                    }
                });
                connection.getSinkChannel().setWriteListener(new ChannelListener<ConduitStreamSinkChannel>() {
                    private boolean continueWriting() throws IOException {
                        if (clientSent.get() > 100) {
                            if (!clientHandshakeStarted.get()) {
                                if (serverReceived.get() == clientSent.get()) {
                                    connection.startHandshake();
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

                    public void handleEvent(final ConduitStreamSinkChannel channel) {
                        try {
                            final ByteBuffer buffer = ByteBuffer.allocate(100);
                            buffer.put("This Is A Test\r\n".getBytes("UTF-8")).flip();
                            int c;
                            try {
                                while (continueWriting() && (c = channel.write(buffer)) > 0) {
                                    log.info("client wrote " + (c + clientSent.get()));
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
                                                                        log.info("client shutting down writes");
                                                                        channel.shutdownWrites();
                                                                        if (connection.isWriteShutdown()) {
                                                                            log.info("client write handler closing connection");
                                                                            connection.close();
                                                                        }
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
                                log.info("server received " +  (c + serverReceived.get()));
                                if (serverReceived.addAndGet(c) > 100 && !serverHandshakeStarted.get() ) {
                                    connection.startHandshake();
                                    serverHandshakeStarted.set(true);
                                }
                            }
                            if (c == -1) {
                                log.info("server shutting down reads");
                                channel.shutdownReads();
                                if(connection.isReadShutdown()) {
                                    log.info("server read handler closing connection");
                                    connection.close();
                                }
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
                                log.info("client received " +  (c + clientReceived.get()));
                                if (clientReceived.addAndGet(c) > 100 && !clientHandshakeStarted.get()) {
                                    connection.startHandshake();
                                    clientHandshakeStarted.set(true);
                                }
                            }
                            if (c == -1) {
                                channel.shutdownReads();
                                if (connection.isReadShutdown())
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
                        if (serverSent.get() > 100) {
                            if (!serverHandshakeStarted.get()) {
                                if (clientReceived.get() == serverSent.get()) {
                                    connection.startHandshake();
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

                    public void handleEvent(final ConduitStreamSinkChannel channel) {
                        try {
                            final ByteBuffer buffer = ByteBuffer.allocate(100);
                            buffer.put("This Is A Test\r\n".getBytes("UTF-8")).flip();
                            int c;
                            try {
                                while (continueWriting() && (c = channel.write(buffer)) > 0) {
                                    log.info("server wrote " + (c + serverSent.get()));
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
                                                                            if (connection.isWriteShutdown())
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
        }, new ChannelListener<SslConnection>() {
            public void handleEvent(final SslConnection connection) {
                connection.getCloseSetter().set(new ChannelListener<SslConnection>() {
                    public void handleEvent(final SslConnection connection) {
                        latch.countDown();
                    }
                });
                final ConduitStreamSourceChannel sourceChannel = connection.getSourceChannel();
                sourceChannel.setReadListener(new ChannelListener<ConduitStreamSourceChannel>() {

                    private boolean continueReading() throws IOException {
                        return clientHandshakeStarted.get() || clientReceived.get() < 101;
                    }

                    public void handleEvent(final ConduitStreamSourceChannel sourceChannel) {
                        log.info("client handle read events");
                        try {
                            int c = 0;
                            while (continueReading() && (c = sourceChannel.read(ByteBuffer.allocate(100))) > 0) {
                                log.info("client received: "+ (clientReceived.get() + c));
                                clientReceived.addAndGet(c);
                            }
                            if (c == -1) {
                                log.info("client shutdown reads");
                                connection.close();
                            }
                        } catch (Throwable t) {
                            t.printStackTrace();
                        }
                    }
                });
                final ConduitStreamSinkChannel sinkChannel = connection.getSinkChannel();
                sinkChannel.setWriteListener(new ChannelListener<ConduitStreamSinkChannel>() {
                    private boolean continueWriting(ConduitStreamSinkChannel sinkChannel) throws IOException {
                        if (clientSent.get() > 100) {
                            if (!clientHandshakeStarted.get()) {
                                if (serverReceived.get() == clientSent.get() && serverSent.get() > 100 && clientReceived.get() == serverSent.get() ) {
                                    connection.startHandshake();
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

                    public void handleEvent(final ConduitStreamSinkChannel sinkChannel) {
                                                try {
                            final ByteBuffer buffer = ByteBuffer.allocate(100);
                            buffer.put("This Is A Test\r\n".getBytes("UTF-8")).flip();
                            int c = 0;
                            try {
                                while (continueWriting(sinkChannel) && (clientSent.get() > 1000 || (c = sinkChannel.write(buffer)) > 0)) {
                                    log.info("clientSent: " + (clientSent.get() + c));
                                    if (clientSent.addAndGet(c) > 1000) {
                                        sinkChannel.setWriteListener(new ChannelListener<ConduitStreamSinkChannel>() {
                                            public void handleEvent(final ConduitStreamSinkChannel sinkChannel) {
                                                try {
                                                    if (sinkChannel.flush()) {
                                                        try {
                                                            log.info("client closing channel");
                                                            sinkChannel.shutdownWrites();
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
                                        });
                                        return;
                                    }
                                    buffer.rewind();
                                }
                            } catch (ClosedChannelException e) {
                                try {
                                    sinkChannel.shutdownWrites();
                                } catch (Exception cce) {/* do nothing */}
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
                connection.getCloseSetter().set(new ChannelListener<SslConnection>() {
                    public void handleEvent(final SslConnection connection) {
                        latch.countDown();
                    }
                });
                final ConduitStreamSourceChannel sourceChannel = connection.getSourceChannel();
                sourceChannel.setReadListener(new ChannelListener<ConduitStreamSourceChannel>() {
                    private boolean continueReading() throws IOException {
                        return serverHandshakeStarted.get() || serverReceived.get() < 101;
                    }

                    public void handleEvent(final ConduitStreamSourceChannel sourceChannel) {
                        try {
                            int c = 0;
                            while (continueReading() && (c = sourceChannel.read(ByteBuffer.allocate(100))) > 0) {
                                log.info("server received: "+ (serverReceived.get() + c));
                                serverReceived.addAndGet(c);
                            }
                            if (c == -1) {
                                log.info("server shutdown reads");
                                connection.close();
                            }
                        } catch (Throwable t) {
                            t.printStackTrace();
                            throw new RuntimeException(t);
                        }
                    }
                });
                final ConduitStreamSinkChannel sinkChannel = connection.getSinkChannel();
                sinkChannel.setWriteListener(new ChannelListener<ConduitStreamSinkChannel>() {

                    private boolean continueWriting(ConduitStreamSinkChannel sinkChannel) throws IOException {
                        if (serverSent.get() > 100) {
                            if (!serverHandshakeStarted.get()) {
                                if (clientReceived.get() == serverSent.get() && clientSent.get() > 100 && serverReceived.get() == clientSent.get() ) {
                                    connection.startHandshake();
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

                    public void handleEvent(final ConduitStreamSinkChannel sinkChannel) {
                        try {
                            final ByteBuffer buffer = ByteBuffer.allocate(100);
                            buffer.put("This Is A Test\r\n".getBytes("UTF-8")).flip();
                            int c;
                            try {
                                while (continueWriting(sinkChannel) && (c = sinkChannel.write(buffer)) > 0) {
                                    log.info("server sent: "+ (serverSent.get() + c));
                                    if (serverSent.addAndGet(c) > 1000) {
                                        sinkChannel.setWriteListener(new ChannelListener<ConduitStreamSinkChannel>() {
                                            public void handleEvent(final ConduitStreamSinkChannel sinkChannel) {
                                                try {
                                                    if (sinkChannel.flush()) {
                                                        try {
                                                            log.info("server closing channel");
                                                            sinkChannel.shutdownWrites();
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
                                        });
                                        return;
                                    }
                                }
                                buffer.rewind();
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
        }, new ChannelListener<SslConnection>() {
            public void handleEvent(final SslConnection connection) {
                try {
                    connection.getCloseSetter().set(new ChannelListener<ConnectedChannel>() {
                        public void handleEvent(final ConnectedChannel channel) {
                            log.info("at client close listener");
                            latch.countDown();
                        }
                    });
                    connection.setOption(Options.CLOSE_ABORT, Boolean.TRUE);
                    log.info("client closing channel");
                    connection.close();
                    clientOK.set(true);
                } catch (Throwable t) {
                    log.errorf(t, "Failed to close channel (propagating as RT exception)");
                    latch.countDown();
                    throw new RuntimeException(t);
                }
            }
        }, new ChannelListener<SslConnection>() {
            public void handleEvent(final SslConnection connection) {
                try {
                    connection.getCloseSetter().set(new ChannelListener<ConnectedChannel>() {
                        public void handleEvent(final ConnectedChannel channel) {
                            log.info("at server close listener");
                            latch.countDown();
                        }
                    });
                    connection.getSourceChannel().setReadListener(new ChannelListener<ConduitStreamSourceChannel>() {
                        public void handleEvent(final ConduitStreamSourceChannel sourceChannel) {
                                try {
                                    int res = sourceChannel.read(ByteBuffer.allocate(100));
                                    log.info("server read " + res);
                                    // with start TLS, client nasty close behaves differently, no IOException is thrown
                                    if (res == -1) {
                                        serverOK.set(true);
                                        IoUtils.safeClose(connection);
                                        return;
                                    }
                                    if (res > 0) IoUtils.safeClose(connection);
                                } catch (Throwable t) {
                                    t.printStackTrace();
                                    throw new RuntimeException(t);
                                }
                        }
                    });
                    log.info("resuming reads for server");
                    resumeReads(connection);
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
        }, new ChannelListener<SslConnection>() {
            public void handleEvent(final SslConnection connection) {
                try {
                    log.info("Client opened");
                    connection.getCloseSetter().set(new ChannelListener<ConnectedChannel>() {
                        public void handleEvent(final ConnectedChannel channel) {
                            latch.countDown();
                        }
                    });
                    final ConduitStreamSourceChannel sourceChannel = connection.getSourceChannel();
                    sourceChannel.setReadListener(new ChannelListener<ConduitStreamSourceChannel>() {
                        public void handleEvent(final ConduitStreamSourceChannel sourceChannel) {
                            try {
                                int res = sourceChannel.read(ByteBuffer.allocate(100));
                                // with start TLS, server nasty close behaves differently, no IOException is thrown
                                if (res == -1) {
                                    clientOK.set(true);
                                    IoUtils.safeClose(connection);
                                    return;
                                }
                                if (res > 0) IoUtils.safeClose(connection);
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
                                if (sinkChannel.write(ByteBuffer.wrap(new byte[] { 1 })) > 0) {
                                    sinkChannel.suspendWrites();
                                }
                            } catch (IOException e) {
                                IoUtils.safeClose(connection);
                            }
                        }
                    });
                    sourceChannel.resumeReads();
                    sinkChannel.resumeWrites();
                    serverLatch.countDown();
                } catch (Throwable t) {
                    log.error("Error occurred on client", t);
                    try {
                        connection.close();
                    } catch (Throwable t2) {
                        log.error("Error occurred on client (close)", t2);
                        latch.countDown();
                        throw new RuntimeException(t);
                    }
                    throw new RuntimeException(t);
                }
            }
        }, new ChannelListener<SslConnection>() {
            public void handleEvent(final SslConnection connection) {
                try {
                    log.info("Server opened");
                    connection.getCloseSetter().set(new ChannelListener<ConnectedChannel>() {
                        public void handleEvent(final ConnectedChannel channel) {
                            latch.countDown();
                        }
                    });
                    final ConduitStreamSourceChannel sourceChannel = connection.getSourceChannel();
                    sourceChannel.setReadListener(new ChannelListener<ConduitStreamSourceChannel>() {
                        public void handleEvent(final ConduitStreamSourceChannel sourceChannel) {
                            try {
                                if (sourceChannel.read(ByteBuffer.allocate(1)) > 0) {
                                    log.info("Closing connection...");
                                    connection.setOption(Options.CLOSE_ABORT, Boolean.TRUE);
                                    connection.close();
                                    serverOK.set(true);
                                }
                            } catch (IOException e) {
                                IoUtils.safeClose(connection);
                            }
                        }
                    });
                    sourceChannel.resumeReads();
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
