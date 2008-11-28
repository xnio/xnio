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
import org.jboss.xnio.channels.ChannelOption;
import org.jboss.xnio.channels.CommonOptions;
import org.jboss.xnio.channels.UdpChannel;
import org.jboss.xnio.IoHandlerFactory;
import org.jboss.xnio.ConfigurableFactory;

/**
 *
 */
public final class BioUdpServerFactory extends AbstractConfigurable implements ConfigurableFactory<BoundServer<SocketAddress, UdpChannel>> {

    private static final Set<ChannelOption<?>> options;
    private final NioXnio xnio;
    private final Executor executor;
    private final IoHandlerFactory<? super UdpChannel> factory;
    private final SocketAddress[] initialAddresses;
    private final Object lock = new Object();
    private boolean created;

    static {
        final Set<ChannelOption<?>> optionSet = new HashSet<ChannelOption<?>>();
        optionSet.add(CommonOptions.REUSE_ADDRESSES);
        optionSet.add(CommonOptions.RECEIVE_BUFFER);
        optionSet.add(CommonOptions.SEND_BUFFER);
        optionSet.add(CommonOptions.IP_TRAFFIC_CLASS);
        optionSet.add(CommonOptions.BROADCAST);
        options = Collections.unmodifiableSet(optionSet);
    }

    BioUdpServerFactory(final NioXnio xnio, final Executor executor, final IoHandlerFactory<? super UdpChannel> factory, final SocketAddress[] initialAddresses) {
        super(options);
        this.xnio = xnio;
        this.executor = executor;
        this.factory = factory;
        this.initialAddresses = initialAddresses;
    }

    public BoundServer<SocketAddress, UdpChannel> create() throws IOException {
        synchronized (lock) {
            if (created) {
                throw new IllegalStateException("Already created");
            }
            final BioUdpServerConfig config = new BioUdpServerConfig();
            config.setExecutor(executor);
            config.setHandlerFactory(factory);
            config.setInitialAddresses(initialAddresses);
            config.setReuseAddresses(getOption(CommonOptions.REUSE_ADDRESSES));
            config.setReceiveBuffer(getOption(CommonOptions.RECEIVE_BUFFER));
            config.setSendBuffer(getOption(CommonOptions.SEND_BUFFER));
            config.setTrafficClass(getOption(CommonOptions.IP_TRAFFIC_CLASS));
            config.setBroadcast(getOption(CommonOptions.BROADCAST));
            final BioUdpServer udpServer = BioUdpServer.create(config);
            xnio.addManaged(udpServer);
            created = true;
            return udpServer;
        }
    }
}