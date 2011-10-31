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
import org.xnio.ChannelListener;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.BoundChannel;
import org.xnio.channels.ConnectedSslStreamChannel;

import javax.net.ssl.SSLContext;

/**
 * An SSL provider for XNIO.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@SuppressWarnings("unused")
public abstract class XnioSsl {

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
     * @return the SSL connection
     */
    public abstract IoFuture<ConnectedSslStreamChannel> connectSsl(XnioWorker worker, InetSocketAddress destination, ChannelListener<? super ConnectedSslStreamChannel> openListener, OptionMap optionMap);

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
    public abstract IoFuture<ConnectedSslStreamChannel> connectSsl(XnioWorker worker, InetSocketAddress destination, ChannelListener<? super ConnectedSslStreamChannel> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap);

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
    public abstract IoFuture<ConnectedSslStreamChannel> connectSsl(XnioWorker worker, InetSocketAddress bindAddress, InetSocketAddress destination, ChannelListener<? super ConnectedSslStreamChannel> openListener, OptionMap optionMap);

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
    public abstract IoFuture<ConnectedSslStreamChannel> connectSsl(XnioWorker worker, InetSocketAddress bindAddress, InetSocketAddress destination, ChannelListener<? super ConnectedSslStreamChannel> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap);

    /**
     * Create a bound TCP SSL server.  The given executor will be used to execute SSL tasks.
     *
     * @param worker the worker to use
     * @param bindAddress the address to bind to
     * @param acceptListener the initial accept listener
     * @param optionMap the initial configuration for the server
     * @return the unbound TCP SSL server
     * @throws IOException if the server could not be created
     */
    public abstract AcceptingChannel<ConnectedSslStreamChannel> createSslTcpServer(XnioWorker worker, InetSocketAddress bindAddress, ChannelListener<? super AcceptingChannel<ConnectedSslStreamChannel>> acceptListener, OptionMap optionMap) throws IOException;
}
