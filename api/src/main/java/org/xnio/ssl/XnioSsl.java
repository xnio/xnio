/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2013 Red Hat, Inc. and/or its affiliates, and individual
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

import javax.net.ssl.SSLContext;

import org.xnio.ChannelListener;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.Xnio;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.BoundChannel;
import org.xnio.channels.ConnectedSslStreamChannel;

/**
 * An SSL provider for XNIO.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 */
@SuppressWarnings("unused")
public abstract class XnioSsl {

    private static final InetSocketAddress ANY_INET_ADDRESS = new InetSocketAddress(0);

    /**
     * The corresponding XNIO instance.
     */
    protected final Xnio xnio;

    /**
     * The SSL context for this instance.
     */
    protected final SSLContext sslContext;

    /**
     * Construct a new instance.
     *
     * @param xnio the XNIO instance
     * @param sslContext
     * @param optionMap the option map to configure this provider
     */
    @SuppressWarnings("unused")
    protected XnioSsl(final Xnio xnio, final SSLContext sslContext, final OptionMap optionMap) {
        this.xnio = xnio;
        this.sslContext = sslContext;
    }

    /**
     * Create an SSL connection to a remote host.
     *
     * @param worker the worker to use
     * @param destination the destination connection address
     * @param openListener the initial open-connection listener
     * @param optionMap the option map
     *
     * @return the SSL connection
     */
    @Deprecated
    public IoFuture<ConnectedSslStreamChannel> connectSsl(XnioWorker worker, InetSocketAddress destination, ChannelListener<? super ConnectedSslStreamChannel> openListener, OptionMap optionMap) {
        return connectSsl(worker, ANY_INET_ADDRESS, destination, openListener, null, optionMap);
    }

    /**
     * Create an SSL connection to a remote host.
     *
     * @param worker the worker to use
     * @param destination the destination connection address
     * @param openListener the initial open-connection listener
     * @param bindListener the bind listener
     * @param optionMap the option map
     * @return the SSL connection
     */
    @Deprecated
    public IoFuture<ConnectedSslStreamChannel> connectSsl(final XnioWorker worker, final InetSocketAddress destination, final ChannelListener<? super ConnectedSslStreamChannel> openListener, final ChannelListener<? super BoundChannel> bindListener, final OptionMap optionMap) {
        return connectSsl(worker, ANY_INET_ADDRESS, destination, openListener, bindListener, optionMap);
    }

    /**
     * Create an SSL connection to a remote host.
     *
     * @param worker the worker to use
     * @param bindAddress the local bind address
     * @param destination the destination connection address
     * @param openListener the initial open-connection listener
     * @param optionMap the option map
     * @return the SSL connection
     */
    @Deprecated
    public IoFuture<ConnectedSslStreamChannel> connectSsl(final XnioWorker worker, final InetSocketAddress bindAddress, final InetSocketAddress destination, final ChannelListener<? super ConnectedSslStreamChannel> openListener, final OptionMap optionMap) {
        return connectSsl(worker, bindAddress, destination, openListener, null, optionMap);
    }

    /**
     * Create an SSL connection to a remote host.
     *
     * @param worker the worker to use
     * @param bindAddress the local bind address
     * @param destination the destination connection address
     * @param openListener the initial open-connection listener
     * @param bindListener the bind listener
     * @param optionMap the option map
     * @return the SSL connection
     */
    @Deprecated
    public abstract IoFuture<ConnectedSslStreamChannel> connectSsl(XnioWorker worker, InetSocketAddress bindAddress, InetSocketAddress destination, ChannelListener<? super ConnectedSslStreamChannel> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap);

    /**
     * Create an SSL connection to a remote host.
     *
     * @param worker the worker to use
     * @param destination the destination connection address
     * @param openListener the initial open-connection listener
     * @param optionMap the option map
     *
     * @return the SSL connection
     */
    public IoFuture<SslConnection> openSslConnection(XnioWorker worker, InetSocketAddress destination, ChannelListener<? super SslConnection> openListener, OptionMap optionMap) {
        return openSslConnection(worker, ANY_INET_ADDRESS, destination, openListener, null, optionMap);
    }

    /**
     * Create an SSL connection to a remote host.
     *
     * @param ioThread the IO thread to use
     * @param destination the destination connection address
     * @param openListener the initial open-connection listener
     * @param optionMap the option map
     *
     * @return the SSL connection
     */
    public IoFuture<SslConnection> openSslConnection(XnioIoThread ioThread, InetSocketAddress destination, ChannelListener<? super SslConnection> openListener, OptionMap optionMap) {
        return openSslConnection(ioThread, ANY_INET_ADDRESS, destination, openListener, null, optionMap);
    }

    /**
     * Create an SSL connection to a remote host.
     *
     * @param worker the worker to use
     * @param destination the destination connection address
     * @param openListener the initial open-connection listener
     * @param bindListener the bind listener
     * @param optionMap the option map
     * @return the SSL connection
     */
    public IoFuture<SslConnection> openSslConnection(final XnioWorker worker, final InetSocketAddress destination, final ChannelListener<? super SslConnection> openListener, final ChannelListener<? super BoundChannel> bindListener, final OptionMap optionMap) {
        return openSslConnection(worker, ANY_INET_ADDRESS, destination, openListener, bindListener, optionMap);
    }

    /**
     * Create an SSL connection to a remote host.
     *
     * @param ioThread the IO Thread to use
     * @param destination the destination connection address
     * @param openListener the initial open-connection listener
     * @param bindListener the bind listener
     * @param optionMap the option map
     * @return the SSL connection
     */
    public IoFuture<SslConnection> openSslConnection(final XnioIoThread ioThread, final InetSocketAddress destination, final ChannelListener<? super SslConnection> openListener, final ChannelListener<? super BoundChannel> bindListener, final OptionMap optionMap) {
        return openSslConnection(ioThread, ANY_INET_ADDRESS, destination, openListener, bindListener, optionMap);
    }

    /**
     * Create an SSL connection to a remote host.
     *
     * @param worker the worker to use
     * @param bindAddress the local bind address
     * @param destination the destination connection address
     * @param openListener the initial open-connection listener
     * @param optionMap the option map
     * @return the SSL connection
     */
    public IoFuture<SslConnection> openSslConnection(final XnioWorker worker, final InetSocketAddress bindAddress, final InetSocketAddress destination, final ChannelListener<? super SslConnection> openListener, final OptionMap optionMap) {
        return openSslConnection(worker, bindAddress, destination, openListener, null, optionMap);
    }

    /**
     * Create an SSL connection to a remote host.
     *
     * @param ioThread the IO Thread to use
     * @param bindAddress the local bind address
     * @param destination the destination connection address
     * @param openListener the initial open-connection listener
     * @param optionMap the option map
     * @return the SSL connection
     */
    public IoFuture<SslConnection> openSslConnection(final XnioIoThread ioThread, final InetSocketAddress bindAddress, final InetSocketAddress destination, final ChannelListener<? super SslConnection> openListener, final OptionMap optionMap) {
        return openSslConnection(ioThread, bindAddress, destination, openListener, null, optionMap);
    }

    /**
     * Create an SSL connection to a remote host.
     *
     * @param worker the worker to use
     * @param bindAddress the local bind address
     * @param destination the destination connection address
     * @param openListener the initial open-connection listener
     * @param bindListener the bind listener
     * @param optionMap the option map
     * @return the SSL connection
     */
    public abstract IoFuture<SslConnection> openSslConnection(XnioWorker worker, InetSocketAddress bindAddress, InetSocketAddress destination, ChannelListener<? super SslConnection> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap);


    /**
     * Create an SSL connection to a remote host.
     *
     * @param ioThread the IO Thread to use
     * @param bindAddress the local bind address
     * @param destination the destination connection address
     * @param openListener the initial open-connection listener
     * @param bindListener the bind listener
     * @param optionMap the option map
     * @return the SSL connection
     */
    public abstract IoFuture<SslConnection> openSslConnection(XnioIoThread ioThread, InetSocketAddress bindAddress, InetSocketAddress destination, ChannelListener<? super SslConnection> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap);


    /**
     * Create a bound TCP SSL server.
     *
     * @param worker the worker to use
     * @param bindAddress the address to bind to
     * @param acceptListener the initial accept listener
     * @param optionMap the initial configuration for the server
     * @return the unbound TCP SSL server
     * @throws IOException if the server could not be created
     */
    @Deprecated
    public abstract AcceptingChannel<ConnectedSslStreamChannel> createSslTcpServer(XnioWorker worker, InetSocketAddress bindAddress, ChannelListener<? super AcceptingChannel<ConnectedSslStreamChannel>> acceptListener, OptionMap optionMap) throws IOException;

    /**
     * Create a bound TCP SSL server.
     *
     * @param worker the worker to use
     * @param bindAddress the address to bind to
     * @param acceptListener the initial accept listener
     * @param optionMap the initial configuration for the server
     * @return the unbound TCP SSL server
     * @throws IOException if the server could not be created
     */
    public abstract AcceptingChannel<SslConnection> createSslConnectionServer(XnioWorker worker, InetSocketAddress bindAddress, ChannelListener<? super AcceptingChannel<SslConnection>> acceptListener, OptionMap optionMap) throws IOException;
}
