/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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

package org.jboss.xnio.nio;

import java.net.SocketAddress;
import java.util.concurrent.Executor;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import java.io.IOException;
import org.jboss.xnio.channels.BoundServer;
import org.jboss.xnio.channels.TcpChannel;
import org.jboss.xnio.channels.ChannelOption;
import org.jboss.xnio.channels.CommonOptions;
import org.jboss.xnio.channels.BoundChannel;
import org.jboss.xnio.IoHandlerFactory;
import org.jboss.xnio.ConfigurableFactory;

/**
 *
 */
public final class NioTcpServerFactory extends AbstractConfigurable implements ConfigurableFactory<BoundServer<SocketAddress, BoundChannel<SocketAddress>>> {

    private static final Set<ChannelOption<?>> options;
    private final NioXnio xnio;
    private final Executor executor;
    private final IoHandlerFactory<? super TcpChannel> factory;
    private final SocketAddress[] initialAddresses;
    private final Object lock = new Object();
    private boolean created;

    static {
        final Set<ChannelOption<?>> optionSet = new HashSet<ChannelOption<?>>();
        optionSet.add(CommonOptions.BACKLOG);
        optionSet.add(CommonOptions.REUSE_ADDRESSES);
        optionSet.add(CommonOptions.RECEIVE_BUFFER);
        optionSet.add(CommonOptions.KEEP_ALIVE);
        optionSet.add(CommonOptions.TCP_OOB_INLINE);
        optionSet.add(CommonOptions.TCP_NODELAY);
        options = Collections.unmodifiableSet(optionSet);
    }

    NioTcpServerFactory(final NioXnio xnio, final Executor executor, final IoHandlerFactory<? super TcpChannel> factory, final SocketAddress[] initialAddresses) {
        super(options);
        this.xnio = xnio;
        this.executor = executor;
        this.factory = factory;
        this.initialAddresses = initialAddresses;
    }

    public BoundServer<SocketAddress, BoundChannel<SocketAddress>> create() throws IOException {
        synchronized (lock) {
            if (created) {
                throw new IllegalStateException("Already created");
            }
            final NioTcpServerConfig config = new NioTcpServerConfig();
            config.setXnio(xnio);
            config.setExecutor(executor);
            config.setHandlerFactory(factory);
            config.setInitialAddresses(initialAddresses);
            config.setBacklog(getOption(CommonOptions.BACKLOG));
            config.setReuseAddresses(getOption(CommonOptions.REUSE_ADDRESSES));
            config.setReceiveBuffer(getOption(CommonOptions.RECEIVE_BUFFER));
            config.setKeepAlive(getOption(CommonOptions.KEEP_ALIVE));
            config.setOobInline(getOption(CommonOptions.TCP_OOB_INLINE));
            config.setNoDelay(getOption(CommonOptions.TCP_NODELAY));
            final NioTcpServer tcpServer = NioTcpServer.create(config);
            created = true;
            return tcpServer;
        }
    }
}
