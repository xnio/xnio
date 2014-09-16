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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;
import org.xnio.ChannelListener;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.StreamConnection;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.BoundChannel;
import org.xnio.channels.Channels;
import org.xnio.conduits.ConduitStreamSinkChannel;
import org.xnio.conduits.ConduitStreamSourceChannel;

/**
 * Test for TCP stream connections.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 */
public class NioTcpConnectionTestCase extends AbstractNioTcpTest<StreamConnection, ConduitStreamSourceChannel, ConduitStreamSinkChannel> {

    @Test
    public void acceptor() throws Exception {
        log.info("Test: acceptor");
        final CountDownLatch ioLatch = new CountDownLatch(4);
        final CountDownLatch closeLatch = new CountDownLatch(2);
        final AtomicBoolean clientOpened = new AtomicBoolean();
        final AtomicBoolean clientReadOnceOK = new AtomicBoolean();
        final AtomicBoolean clientReadDoneOK = new AtomicBoolean();
        final AtomicBoolean clientReadTooMuch = new AtomicBoolean();
        final AtomicBoolean clientWriteOK = new AtomicBoolean();
        final AtomicBoolean serverOpened = new AtomicBoolean();
        final AtomicBoolean serverReadOnceOK = new AtomicBoolean();
        final AtomicBoolean serverReadDoneOK = new AtomicBoolean();
        final AtomicBoolean serverReadTooMuch = new AtomicBoolean();
        final AtomicBoolean serverWriteOK = new AtomicBoolean();
        final byte[] bytes = "Ummagumma!".getBytes("UTF-8");
        final Xnio xnio = Xnio.getInstance("nio");
        final XnioWorker worker = xnio.createWorker(OptionMap.EMPTY);
        try {
            final FutureResult<InetSocketAddress> futureAddressResult = new FutureResult<InetSocketAddress>();
            final IoFuture<InetSocketAddress> futureAddress = futureAddressResult.getIoFuture();
            worker.acceptStreamConnection(new InetSocketAddress(Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 }), 0), new ChannelListener<StreamConnection>() {
                private final ByteBuffer inboundBuf = ByteBuffer.allocate(512);
                private int readCnt = 0;
                private final ByteBuffer outboundBuf = ByteBuffer.wrap(bytes);

                public void handleEvent(final StreamConnection connection) {
                    connection.getCloseSetter().set(new ChannelListener<StreamConnection>() {
                        public void handleEvent(final StreamConnection channel) {
                            closeLatch.countDown();
                        }
                    });
                    final ConduitStreamSourceChannel sourceChannel = connection.getSourceChannel();
                    sourceChannel.setReadListener(new ChannelListener<ConduitStreamSourceChannel>() {
                        public void handleEvent(final ConduitStreamSourceChannel sourceChannel) {
                            try {
                                final int res = sourceChannel.read(inboundBuf);
                                if (res == -1) {
                                    serverReadDoneOK.set(true);
                                    ioLatch.countDown();
                                    sourceChannel.shutdownReads();
                                } else if (res > 0) {
                                    final int ttl = readCnt += res;
                                    if (ttl == bytes.length) {
                                        serverReadOnceOK.set(true);
                                    } else if (ttl > bytes.length) {
                                        serverReadTooMuch.set(true);
                                        IoUtils.safeClose(connection);
                                        return;
                                    }
                                }
                            } catch (IOException e) {
                                log.errorf(e, "Server read failed");
                                IoUtils.safeClose(connection);
                            }
                        }
                    });
                    final ConduitStreamSinkChannel sinkChannel = connection.getSinkChannel();
                    sinkChannel.setWriteListener(new ChannelListener<ConduitStreamSinkChannel>() {
                        public void handleEvent(final ConduitStreamSinkChannel sinkChannel) {
                            try {
                                sinkChannel.write(outboundBuf);
                                if (!outboundBuf.hasRemaining()) {
                                    serverWriteOK.set(true);
                                    Channels.shutdownWritesBlocking(sinkChannel);
                                    ioLatch.countDown();
                                }
                            } catch (IOException e) {
                                log.errorf(e, "Server write failed");
                                IoUtils.safeClose(connection);
                            }
                        }
                    });
                    sourceChannel.resumeReads();
                    sinkChannel.resumeWrites();
                    serverOpened.set(true);
                }
            }, new ChannelListener<BoundChannel>() {
                public void handleEvent(final BoundChannel channel) {
                    futureAddressResult.setResult(channel.getLocalAddress(InetSocketAddress.class));
                }
            }, OptionMap.create(Options.REUSE_ADDRESSES, Boolean.TRUE));
            final InetSocketAddress localAddress = futureAddress.get();
            worker.openStreamConnection(localAddress, new ChannelListener<StreamConnection>() {
                private final ByteBuffer inboundBuf = ByteBuffer.allocate(512);
                private int readCnt = 0;
                private final ByteBuffer outboundBuf = ByteBuffer.wrap(bytes);

                public void handleEvent(final StreamConnection connection) {
                    connection.getCloseSetter().set(new ChannelListener<StreamConnection>() {
                        public void handleEvent(final StreamConnection connection) {
                            closeLatch.countDown();
                        }
                    });
                    final ConduitStreamSourceChannel sourceChannel = connection.getSourceChannel();
                    sourceChannel.setReadListener(new ChannelListener<ConduitStreamSourceChannel>() {
                        public void handleEvent(final ConduitStreamSourceChannel sourceChannel) {
                            try {
                                final int res = sourceChannel.read(inboundBuf);
                                if (res == -1) {
                                    sourceChannel.shutdownReads();
                                    clientReadDoneOK.set(true);
                                    ioLatch.countDown();
                                } else if (res > 0) {
                                    final int ttl = readCnt += res;
                                    if (ttl == bytes.length) {
                                        clientReadOnceOK.set(true);
                                    } else if (ttl > bytes.length) {
                                        clientReadTooMuch.set(true);
                                        IoUtils.safeClose(connection);
                                        return;
                                    }
                                }
                            } catch (IOException e) {
                                log.errorf(e, "Client read failed");
                                IoUtils.safeClose(connection);
                            }
                        }
                    });
                    final ConduitStreamSinkChannel sinkChannel = connection.getSinkChannel();
                    sinkChannel.setWriteListener(new ChannelListener<ConduitStreamSinkChannel>() {
                        public void handleEvent(final ConduitStreamSinkChannel sinkChannel) {
                            try {
                                sinkChannel.write(outboundBuf);
                                if (!outboundBuf.hasRemaining()) {
                                    clientWriteOK.set(true);
                                    Channels.shutdownWritesBlocking(sinkChannel);
                                    ioLatch.countDown();
                                }
                            } catch (IOException e) {
                                log.errorf(e, "Client write failed");
                                IoUtils.safeClose(connection);
                            }
                        }
                    });
                    sourceChannel.resumeReads();
                    sinkChannel.resumeWrites();
                    clientOpened.set(true);
                }
            }, null, OptionMap.EMPTY);
            assertTrue("Read timed out", ioLatch.await(500L, TimeUnit.MILLISECONDS));
            assertTrue("Close timed out", closeLatch.await(500L, TimeUnit.MILLISECONDS));
            assertFalse("Client read too much", clientReadTooMuch.get());
            assertTrue("Client read OK", clientReadOnceOK.get());
            assertTrue("Client read done", clientReadDoneOK.get());
            assertTrue("Client write OK", clientWriteOK.get());
            assertFalse("Server read too much", serverReadTooMuch.get());
            assertTrue("Server read OK", serverReadOnceOK.get());
            assertTrue("Server read done", serverReadDoneOK.get());
            assertTrue("Server write OK", serverWriteOK.get());
        } finally {
            worker.shutdown();
        }
    }

    @Override
    protected AcceptingChannel<? extends StreamConnection> createServer(XnioWorker worker, InetSocketAddress address,
            ChannelListener<AcceptingChannel<StreamConnection>> openListener, OptionMap optionMap) throws IOException {
        return worker.createStreamConnectionServer(address, openListener, optionMap);
    }

    @Override
    protected IoFuture<? extends StreamConnection> connect(XnioWorker worker, InetSocketAddress address,
            ChannelListener<StreamConnection> openListener, ChannelListener<? super BoundChannel> bindListener,
            OptionMap optionMap) {
        return worker.openStreamConnection(address,  openListener, bindListener, optionMap);
    }

    @Override
    protected void setReadListener(StreamConnection channel, ChannelListener<ConduitStreamSourceChannel> readListener) {
        channel.getSourceChannel().setReadListener(readListener);
    }

    @Override
    protected void setWriteListener(StreamConnection channel, ChannelListener<ConduitStreamSinkChannel> writeListener) {
        channel.getSinkChannel().setWriteListener(writeListener);
    }

    @Override
    protected void resumeReads(StreamConnection channel) {
        channel.getSourceChannel().resumeReads();
    }

    @Override
    protected void resumeWrites(StreamConnection channel) {
        channel.getSinkChannel().resumeWrites();
    }
}
