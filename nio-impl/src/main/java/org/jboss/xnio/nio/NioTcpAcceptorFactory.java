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

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import org.jboss.xnio.CloseableTcpAcceptor;
import org.jboss.xnio.ConfigurableFactory;
import org.jboss.xnio.channels.ChannelOption;
import org.jboss.xnio.channels.CommonOptions;

/**
 *
 */
public final class NioTcpAcceptorFactory extends AbstractConfigurable implements ConfigurableFactory<CloseableTcpAcceptor> {

    private static final Set<ChannelOption<?>> options;
    private final NioXnio xnio;
    private final Executor executor;
    private final Object lock = new Object();
    private boolean created;

    static {
        final Set<ChannelOption<?>> optionSet = new HashSet<ChannelOption<?>>();
        optionSet.add(CommonOptions.REUSE_ADDRESSES);
        optionSet.add(CommonOptions.RECEIVE_BUFFER);
        optionSet.add(CommonOptions.KEEP_ALIVE);
        optionSet.add(CommonOptions.TCP_OOB_INLINE);
        optionSet.add(CommonOptions.TCP_NODELAY);
        options = Collections.unmodifiableSet(optionSet);
    }


    NioTcpAcceptorFactory(final NioXnio xnio, final Executor executor) {
        super(options);
        this.xnio = xnio;
        this.executor = executor;
    }

    public CloseableTcpAcceptor create() throws IOException, IllegalStateException {
        synchronized (lock) {
            if (created) {
                throw new IllegalStateException("Already created");
            }
            final NioTcpAcceptorConfig config = new NioTcpAcceptorConfig();
            config.setXnio(xnio);
            config.setExecutor(executor);
            config.setReuseAddresses(getOption(CommonOptions.REUSE_ADDRESSES));
            config.setReceiveBuffer(getOption(CommonOptions.RECEIVE_BUFFER));
            config.setKeepAlive(getOption(CommonOptions.KEEP_ALIVE));
            config.setOobInline(getOption(CommonOptions.TCP_OOB_INLINE));
            config.setNoDelay(getOption(CommonOptions.TCP_NODELAY));
            final NioTcpAcceptor nioTcpAcceptor = NioTcpAcceptor.create(config);
            xnio.addManaged(nioTcpAcceptor);
            created = true;
            return nioTcpAcceptor;
        }
    }
}
