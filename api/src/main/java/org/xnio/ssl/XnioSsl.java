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
import org.xnio.ConnectionChannelThread;
import org.xnio.Connector;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.ReadChannelThread;
import org.xnio.WriteChannelThread;
import org.xnio.Xnio;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.BoundChannel;
import org.xnio.channels.ConnectedSslStreamChannel;

/**
 * An SSL provider for XNIO.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class XnioSsl {

    /**
     * The corresponding XNIO instance.
     */
    protected final Xnio xnio;

    /**
     * Construct a new instance.
     *
     * @param xnio the XNIO instance
     * @param optionMap the option map to configure this provider
     */
    @SuppressWarnings("unused")
    protected XnioSsl(final Xnio xnio, final OptionMap optionMap) {
        this.xnio = xnio;
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
    public abstract IoFuture<ConnectedSslStreamChannel> connectSsl(InetSocketAddress destination, ConnectionChannelThread thread, ReadChannelThread readThread, WriteChannelThread writeThread, ChannelListener<? super ConnectedSslStreamChannel> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap);

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
    public abstract IoFuture<ConnectedSslStreamChannel> connectSsl(InetSocketAddress destination, ConnectionChannelThread thread, ReadChannelThread readThread, WriteChannelThread writeThread, ChannelListener<? super ConnectedSslStreamChannel> openListener, OptionMap optionMap);

    /**
     * Create a bound TCP SSL server.  The given executor will be used to execute SSL tasks.
     *
     * @param bindAddress the address to bind to
     * @param thread the connection channel thread to use for this connection
     * @param acceptListener the initial accept listener
     * @param optionMap the initial configuration for the server
     * @return the unbound TCP SSL server
     * @throws IOException if the server could not be created
     */
    public abstract AcceptingChannel<ConnectedSslStreamChannel> createSslTcpServer(InetSocketAddress bindAddress, ConnectionChannelThread thread, ChannelListener<? super AcceptingChannel<ConnectedSslStreamChannel>> acceptListener, OptionMap optionMap) throws IOException;

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
    public abstract IoFuture<ConnectedSslStreamChannel> connectSsl(InetSocketAddress bindAddress, InetSocketAddress destination, ConnectionChannelThread thread, ReadChannelThread readThread, WriteChannelThread writeThread, ChannelListener<? super ConnectedSslStreamChannel> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap);

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
    public abstract Connector<ConnectedSslStreamChannel> createSslTcpConnector(InetSocketAddress src, ConnectionChannelThread thread, ReadChannelThread readThread, WriteChannelThread writeThread, OptionMap optionMap);

    /**
     * Create an SSL TCP connector.  The provider's default executor will be used to execute listener methods.
     *
     * @param thread the connection channel thread to use for this connection
     * @param readThread the initial read channel thread to use for this connection, or {@code null} for none
     * @param writeThread the initial write channel thread to use for this connection, or {@code null} for none
     * @param optionMap the initial configuration for the connector
     * @return the SSL TCP connector
     */
    public abstract Connector<ConnectedSslStreamChannel> createSslTcpConnector(ConnectionChannelThread thread, ReadChannelThread readThread, WriteChannelThread writeThread, OptionMap optionMap);
}
