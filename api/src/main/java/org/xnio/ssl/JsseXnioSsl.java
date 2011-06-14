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
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import org.xnio.BufferAllocator;
import org.xnio.ByteBufferSlicePool;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.ConnectionChannelThread;
import org.xnio.Connector;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Pool;
import org.xnio.ReadChannelThread;
import org.xnio.WriteChannelThread;
import org.xnio.Xnio;
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
        super(xnio, optionMap);
        sslContext = JsseSslUtils.createSSLContext(optionMap);
        sslContext.getClientSessionContext().setSessionCacheSize(optionMap.get(Options.SSL_CLIENT_SESSION_CACHE_SIZE, 0));
        sslContext.getClientSessionContext().setSessionTimeout(optionMap.get(Options.SSL_CLIENT_SESSION_TIMEOUT, 0));
        sslContext.getServerSessionContext().setSessionCacheSize(optionMap.get(Options.SSL_SERVER_SESSION_CACHE_SIZE, 0));
        sslContext.getServerSessionContext().setSessionTimeout(optionMap.get(Options.SSL_SERVER_SESSION_TIMEOUT, 0));
        // todo - find out better default values
        final int appBufSize = optionMap.get(Options.SSL_APPLICATION_BUFFER_SIZE, 17000);
        final int pktBufSize = optionMap.get(Options.SSL_PACKET_BUFFER_SIZE, 17000);
        final int appBufRegionSize = optionMap.get(Options.SSL_APPLICATION_BUFFER_REGION_SIZE, appBufSize * 16);
        final int pktBufRegionSize = optionMap.get(Options.SSL_PACKET_BUFFER_REGION_SIZE, pktBufSize * 16);
        socketBufferPool = new ByteBufferSlicePool(optionMap.get(Options.USE_DIRECT_BUFFERS, false) ? BufferAllocator.DIRECT_BYTE_BUFFER_ALLOCATOR : BufferAllocator.BYTE_BUFFER_ALLOCATOR, pktBufSize, pktBufRegionSize);
        applicationBufferPool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, appBufSize, appBufRegionSize);
    }

    /**
     * Get the JSSE SSL context for this provider instance.
     *
     * @return the SSL context
     */
    public SSLContext getSslContext() {
        return sslContext;
    }

    /**
     * Create an SSL connection to a remote host.
     *
     * @param destination the destination connection address
     * @param thread the connection channel thread to use for this connection
     * @param readThread the initial read channel thread to use for this connection, or {@code null} for none
     * @param writeThread the initial write channel thread to use for this connection, or {@code null} for none
     * @param openListener the initial open-connection listener
     * @param bindListener the bind listener
     * @param optionMap the option map
     * @return the SSL connection
     */
    public IoFuture<ConnectedSslStreamChannel> connectSsl(final InetSocketAddress destination, final ConnectionChannelThread thread, ReadChannelThread readThread, WriteChannelThread writeThread, final ChannelListener<? super ConnectedSslStreamChannel> openListener, final ChannelListener<? super BoundChannel> bindListener, final OptionMap optionMap) {
        return connectSsl(new InetSocketAddress(0), destination, thread, readThread, writeThread, openListener, bindListener, optionMap);
    }

    /**
     * Create an SSL connection to a remote host.
     *
     * @param destination the destination connection address
     * @param thread the connection channel thread to use for this connection
     * @param readThread the initial read channel thread to use for this connection, or {@code null} for none
     * @param writeThread the initial write channel thread to use for this connection, or {@code null} for none
     * @param openListener the initial open-connection listener
     * @param optionMap the option map
     * @return the SSL connection
     */
    public IoFuture<ConnectedSslStreamChannel> connectSsl(final InetSocketAddress destination, final ConnectionChannelThread thread, ReadChannelThread readThread, WriteChannelThread writeThread, final ChannelListener<? super ConnectedSslStreamChannel> openListener, final OptionMap optionMap) {
        return connectSsl(new InetSocketAddress(0), destination, thread, readThread, writeThread, openListener, null, optionMap);
    }

    /**
     * Create a bound TCP SSL server.  The given executor will be used to execute SSL tasks.
     *
     * @param bindAddress the address to bind to
     * @param thread the connection channel thread to use for this connection
     * @param acceptListener the initial accept listener
     * @param optionMap the initial configuration for the server
     * @return the unbound TCP SSL server
     * @throws IOException if the server could not be created
     *
     * @since 3.0
     */
    public AcceptingChannel<ConnectedSslStreamChannel> createSslTcpServer(InetSocketAddress bindAddress, ConnectionChannelThread thread, ChannelListener<? super AcceptingChannel<ConnectedSslStreamChannel>> acceptListener, OptionMap optionMap) throws IOException {
        final JsseAcceptingSslStreamChannel server = new JsseAcceptingSslStreamChannel(sslContext, xnio.createStreamServer(bindAddress, thread, null, optionMap), optionMap, socketBufferPool, applicationBufferPool, optionMap.get(Options.SSL_STARTTLS, false));
        if (acceptListener != null) server.getAcceptSetter().set(acceptListener);
        return server;
    }

    ConnectedSslStreamChannel createSslConnectedStreamChannel(final SSLContext sslContext, final ConnectedStreamChannel tcpChannel, final OptionMap optionMap, final boolean server) {
        return new JsseConnectedSslStreamChannel(tcpChannel, JsseSslUtils.createSSLEngine(sslContext, optionMap, tcpChannel.getPeerAddress(InetSocketAddress.class), server), true, socketBufferPool, applicationBufferPool, optionMap.get(Options.SSL_STARTTLS, false));
    }

    /**
     * Create an SSL connection to a remote host.
     *
     * @param bindAddress the local bind address
     * @param destination the destination connection address
     * @param thread the connection channel thread to use for this connection
     * @param readThread the initial read channel thread to use for this connection, or {@code null} for none
     * @param writeThread the initial write channel thread to use for this connection, or {@code null} for none
     * @param openListener the initial open-connection listener
     * @param bindListener the bind listener
     * @param optionMap the option map
     * @return the SSL connection
     */
    public IoFuture<ConnectedSslStreamChannel> connectSsl(final InetSocketAddress bindAddress, final InetSocketAddress destination, final ConnectionChannelThread thread, ReadChannelThread readThread, WriteChannelThread writeThread, final ChannelListener<? super ConnectedSslStreamChannel> openListener, final ChannelListener<? super BoundChannel> bindListener, final OptionMap optionMap) {
        final FutureResult<ConnectedSslStreamChannel> futureResult = new FutureResult<ConnectedSslStreamChannel>(IoUtils.directExecutor());
        xnio.connectStream(bindAddress, destination, thread, readThread, writeThread, new ChannelListener<ConnectedStreamChannel>() {
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

    /**
     * Create an SSL TCP connector.  The given executor will be used to execute SSL tasks.
     *
     * @param src the source address for connections
     * @param thread the connection channel thread to use for this connection
     * @param readThread the initial read channel thread to use for this connection, or {@code null} for none
     * @param writeThread the initial write channel thread to use for this connection, or {@code null} for none
     * @param optionMap the initial configuration for the connector
     * @return the SSL TCP connector
     */
    public Connector<ConnectedSslStreamChannel> createSslTcpConnector(final InetSocketAddress src, final ConnectionChannelThread thread, final ReadChannelThread readThread, final WriteChannelThread writeThread, final OptionMap optionMap) {
        return new Connector<ConnectedSslStreamChannel>() {
            public IoFuture<ConnectedSslStreamChannel> connectTo(final SocketAddress destination, final ChannelListener<? super ConnectedSslStreamChannel> openListener, final ChannelListener<? super BoundChannel> bindListener) {
                return connectSsl(src, (InetSocketAddress) destination, thread, readThread, writeThread, openListener, bindListener, optionMap);
            }
        };
    }

    /**
     * Create an SSL TCP connector.  The provider's default executor will be used to execute listener methods.
     *
     * @param thread the connection channel thread to use for this connection
     * @param readThread the initial read channel thread to use for this connection, or {@code null} for none
     * @param writeThread the initial write channel thread to use for this connection, or {@code null} for none
     * @param optionMap the initial configuration for the connector
     * @return the SSL TCP connector
     */
    public Connector<ConnectedSslStreamChannel> createSslTcpConnector(final ConnectionChannelThread thread, ReadChannelThread readThread, WriteChannelThread writeThread, final OptionMap optionMap) {
        return createSslTcpConnector(ANY_INET_ADDRESS, thread, readThread, writeThread, optionMap);
    }
}
