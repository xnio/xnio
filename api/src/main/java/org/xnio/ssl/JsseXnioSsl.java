/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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

package org.xnio.ssl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
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
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.BoundChannel;
import org.xnio.channels.ConnectedSslStreamChannel;
import org.xnio.channels.ConnectedStreamChannel;

import javax.net.ssl.SSLContext;

/**
 * An XNIO SSL provider based on JSSE.  Works with any XNIO provider.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class JsseXnioSsl extends XnioSsl {
    private static final InetSocketAddress ANY_INET_ADDRESS = new InetSocketAddress(0);
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

    public IoFuture<ConnectedSslStreamChannel> connectSsl(final XnioWorker worker, final InetSocketAddress destination, final ChannelListener<? super ConnectedSslStreamChannel> openListener, final OptionMap optionMap) {
        return connectSsl(worker, ANY_INET_ADDRESS, destination, openListener, null, optionMap);
    }

    public IoFuture<ConnectedSslStreamChannel> connectSsl(final XnioWorker worker, final InetSocketAddress destination, final ChannelListener<? super ConnectedSslStreamChannel> openListener, final ChannelListener<? super BoundChannel> bindListener, final OptionMap optionMap) {
        return connectSsl(worker, ANY_INET_ADDRESS, destination, openListener, bindListener, optionMap);
    }

    public IoFuture<ConnectedSslStreamChannel> connectSsl(final XnioWorker worker, final InetSocketAddress bindAddress, final InetSocketAddress destination, final ChannelListener<? super ConnectedSslStreamChannel> openListener, final OptionMap optionMap) {
        return connectSsl(worker, bindAddress, destination, openListener, null, optionMap);
    }

    public IoFuture<ConnectedSslStreamChannel> connectSsl(final XnioWorker worker, final InetSocketAddress bindAddress, final InetSocketAddress destination, final ChannelListener<? super ConnectedSslStreamChannel> openListener, final ChannelListener<? super BoundChannel> bindListener, final OptionMap optionMap) {
        final FutureResult<ConnectedSslStreamChannel> futureResult = new FutureResult<ConnectedSslStreamChannel>(IoUtils.directExecutor());
        worker.connectStream(bindAddress, destination, new ChannelListener<ConnectedStreamChannel>() {
                    public void handleEvent(final ConnectedStreamChannel tcpChannel) {
                        final ConnectedSslStreamChannel channel = createSslConnectedStreamChannel(sslContext, tcpChannel, optionMap, false);
                        futureResult.setResult(channel);
                        ChannelListeners.invokeChannelListener(channel, openListener);
                    }
                }, bindListener, optionMap).addNotifier(new IoFuture.HandlingNotifier<ConnectedStreamChannel, FutureResult<ConnectedSslStreamChannel>>() {
            public void handleCancelled(final FutureResult<ConnectedSslStreamChannel> result) {
                result.setCancelled();
            }

            public void handleFailed(final IOException exception, final FutureResult<ConnectedSslStreamChannel> result) {
                result.setException(exception);
            }
        }, futureResult);
        return futureResult.getIoFuture();
    }

    public AcceptingChannel<ConnectedSslStreamChannel> createSslTcpServer(final XnioWorker worker, final InetSocketAddress bindAddress, final ChannelListener<? super AcceptingChannel<ConnectedSslStreamChannel>> acceptListener, final OptionMap optionMap) throws IOException {
        final JsseAcceptingSslStreamChannel server = new JsseAcceptingSslStreamChannel(sslContext, worker.createStreamServer(bindAddress, null, optionMap), optionMap, socketBufferPool, applicationBufferPool, optionMap.get(Options.SSL_STARTTLS, false));
        if (acceptListener != null) server.getAcceptSetter().set(acceptListener);
        return server;
    }

    ConnectedSslStreamChannel createSslConnectedStreamChannel(final SSLContext sslContext, final ConnectedStreamChannel tcpChannel, final OptionMap optionMap, final boolean server) {
        return new JsseConnectedSslStreamChannel(tcpChannel, JsseSslUtils.createSSLEngine(sslContext, optionMap, tcpChannel.getPeerAddress(InetSocketAddress.class), server), socketBufferPool, applicationBufferPool, optionMap.get(Options.SSL_STARTTLS, false));
    }
}
