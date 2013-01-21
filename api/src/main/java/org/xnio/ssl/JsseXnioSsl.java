/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2011 Red Hat, Inc. and/or its affiliates, and individual
 * contributors as indicated by the @author tags.
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

package org.xnio.ssl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

import javax.net.ssl.SSLContext;

import org.xnio.BufferAllocator;
import org.xnio.ByteBufferSlicePool;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Pool;
import org.xnio.StreamConnection;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.BoundChannel;
import org.xnio.channels.ConnectedSslStreamChannel;
import org.xnio.channels.ConnectedStreamChannel;

/**
 * An XNIO SSL provider based on JSSE.  Works with any XNIO provider.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class JsseXnioSsl extends XnioSsl {
    private final Pool<ByteBuffer> socketBufferPool;
    private final Pool<ByteBuffer> applicationBufferPool;
    private final SSLContext sslContext;

    /**
     * Construct a new instance.
     *
     * @param xnio the XNIO instance to associate with
     * @param optionMap the options for this provider
     * @throws NoSuchProviderException if the given SSL provider is not found
     * @throws NoSuchAlgorithmException if the given SSL algorithm is not supported
     * @throws KeyManagementException if the SSL context could not be initialized
     */
    public JsseXnioSsl(final Xnio xnio, final OptionMap optionMap) throws NoSuchProviderException, NoSuchAlgorithmException, KeyManagementException {
        this(xnio, optionMap, JsseSslUtils.createSSLContext(optionMap));
    }

    /**
     * Construct a new instance.
     *
     * @param xnio the XNIO instance to associate with
     * @param optionMap the options for this provider
     * @param sslContext the SSL context to use for this instance
     */
    public JsseXnioSsl(final Xnio xnio, final OptionMap optionMap, final SSLContext sslContext) {
        super(xnio, sslContext, optionMap);
        // todo - find out better default values
        final int appBufSize = optionMap.get(Options.SSL_APPLICATION_BUFFER_SIZE, 17000);
        final int pktBufSize = optionMap.get(Options.SSL_PACKET_BUFFER_SIZE, 17000);
        final int appBufRegionSize = optionMap.get(Options.SSL_APPLICATION_BUFFER_REGION_SIZE, appBufSize * 16);
        final int pktBufRegionSize = optionMap.get(Options.SSL_PACKET_BUFFER_REGION_SIZE, pktBufSize * 16);
        socketBufferPool = new ByteBufferSlicePool(optionMap.get(Options.USE_DIRECT_BUFFERS, false) ? BufferAllocator.DIRECT_BYTE_BUFFER_ALLOCATOR : BufferAllocator.BYTE_BUFFER_ALLOCATOR, pktBufSize, pktBufRegionSize);
        applicationBufferPool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, appBufSize, appBufRegionSize);
        this.sslContext = sslContext;
    }

    /**
     * Get the JSSE SSL context for this provider instance.
     *
     * @return the SSL context
     */
    @SuppressWarnings("unused")
    public SSLContext getSslContext() {
        return sslContext;
    }

    public IoFuture<ConnectedSslStreamChannel> connectSsl(final XnioWorker worker, final InetSocketAddress bindAddress, final InetSocketAddress destination, final ChannelListener<? super ConnectedSslStreamChannel> openListener, final ChannelListener<? super BoundChannel> bindListener, final OptionMap optionMap) {
        final FutureResult<ConnectedSslStreamChannel> futureResult = new FutureResult<ConnectedSslStreamChannel>(IoUtils.directExecutor());
        final IoFuture<ConnectedStreamChannel> connectedChannelFuture = worker.connectStream(bindAddress, destination, new ChannelListener<ConnectedStreamChannel>() {
                    public void handleEvent(final ConnectedStreamChannel tcpChannel) {
                        final ConnectedSslStreamChannel channel = createSslConnectedStreamChannel(sslContext, tcpChannel, optionMap);
                        if (!futureResult.setResult(channel)) {
                            IoUtils.safeClose(channel);
                        } else {
                            ChannelListeners.invokeChannelListener(channel, openListener);
                        }
                    }
                }, bindListener, optionMap).addNotifier(new IoFuture.HandlingNotifier<ConnectedStreamChannel, FutureResult<ConnectedSslStreamChannel>>() {
            public void handleCancelled(final FutureResult<ConnectedSslStreamChannel> result) {
                result.setCancelled();
            }

            public void handleFailed(final IOException exception, final FutureResult<ConnectedSslStreamChannel> result) {
                result.setException(exception);
            }
        }, futureResult);
        futureResult.getIoFuture().addNotifier(new IoFuture.HandlingNotifier<ConnectedStreamChannel, IoFuture<ConnectedStreamChannel>>() {
            public void handleCancelled(final IoFuture<ConnectedStreamChannel> result) {
                result.cancel();
            }
        }, connectedChannelFuture);
        futureResult.addCancelHandler(connectedChannelFuture);
        return futureResult.getIoFuture();
    }

    private StreamConnection createWrappedConnection(final StreamConnection original, final InetSocketAddress destination, final OptionMap optionMap) {
        return SslConnectionWrapper.wrap(original, JsseSslUtils.createSSLEngine(sslContext, optionMap, destination), socketBufferPool, applicationBufferPool);
    }

    public IoFuture<StreamConnection> openSslConnection(final XnioWorker worker, final InetSocketAddress bindAddress, final InetSocketAddress destination, final ChannelListener<? super StreamConnection> openListener, final ChannelListener<? super BoundChannel> bindListener, final OptionMap optionMap) {
        final FutureResult<StreamConnection> futureResult = new FutureResult<StreamConnection>(worker);
        final IoFuture<StreamConnection> connection = worker.openStreamConnection(bindAddress, destination, openListener, bindListener, optionMap);
        connection.addNotifier(new IoFuture.HandlingNotifier<StreamConnection, FutureResult<StreamConnection>>() {
            public void handleCancelled(final FutureResult<StreamConnection> attachment) {
                attachment.setCancelled();
            }

            public void handleFailed(final IOException exception, final FutureResult<StreamConnection> attachment) {
                attachment.setException(exception);
            }
        }, futureResult);
        futureResult.addCancelHandler(connection);
        worker.openStreamConnection(bindAddress, destination, new ChannelListener<StreamConnection>() {
            public void handleEvent(final StreamConnection channel) {
                final StreamConnection wrappedConnection = createWrappedConnection(channel, destination, optionMap);
                if (! futureResult.setResult(wrappedConnection)) {
                    IoUtils.safeClose(channel);
                } else {
                    ChannelListeners.invokeChannelListener(wrappedConnection, openListener);
                }
            }
        }, bindListener, optionMap);
        return futureResult.getIoFuture();
    }

    public AcceptingChannel<ConnectedSslStreamChannel> createSslTcpServer(final XnioWorker worker, final InetSocketAddress bindAddress, final ChannelListener<? super AcceptingChannel<ConnectedSslStreamChannel>> acceptListener, final OptionMap optionMap) throws IOException {
        final JsseAcceptingSslStreamChannel server = new JsseAcceptingSslStreamChannel(sslContext, worker.createStreamServer(bindAddress, null, optionMap), optionMap, socketBufferPool, applicationBufferPool, optionMap.get(Options.SSL_STARTTLS, false));
        if (acceptListener != null) server.getAcceptSetter().set(acceptListener);
        return server;
    }

    public AcceptingChannel<StreamConnection> createSslConnectionServer(final XnioWorker worker, final InetSocketAddress bindAddress, final ChannelListener<? super AcceptingChannel<StreamConnection>> acceptListener, final OptionMap optionMap) throws IOException {
       final JsseAcceptingSslStreamConnection server = new JsseAcceptingSslStreamConnection(sslContext, worker.createStreamConnectionServer(bindAddress,  null,  optionMap), optionMap, socketBufferPool, applicationBufferPool);
        if (acceptListener != null) server.getAcceptSetter().set(acceptListener);
        return server;
    }

    ConnectedSslStreamChannel createSslConnectedStreamChannel(final SSLContext sslContext, final ConnectedStreamChannel tcpChannel, final OptionMap optionMap) {
        return new JsseConnectedSslStreamChannel(tcpChannel, JsseSslUtils.createSSLEngine(sslContext, optionMap, tcpChannel.getPeerAddress(InetSocketAddress.class)), socketBufferPool, applicationBufferPool, optionMap.get(Options.SSL_STARTTLS, false));
    }
}
