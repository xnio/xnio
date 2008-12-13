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

package org.jboss.xnio.deployer;

import org.jboss.xnio.ConfigurableFactory;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.log.Logger;
import org.jboss.xnio.channels.CommonOptions;
import org.jboss.xnio.channels.ChannelOption;
import java.io.Closeable;
import java.io.IOException;

/**
 *
 */
public abstract class AbstractXnioController {

    private static final Logger log = Logger.getLogger("org.jboss.xnio.deployer");

    private final ConfigurableFactory<? extends Closeable> configurableFactory;
    private Closeable handle;

    protected AbstractXnioController(final ConfigurableFactory<? extends Closeable> factory) {
        configurableFactory = factory;
    }

    public void stop() {
        IoUtils.safeClose(handle);
        handle = null;
    }

    public void start() throws IOException {
        handle = configurableFactory.create();
    }

    private <T> void doSetOption(final ChannelOption<T> option, final T value) throws IOException {
        final ConfigurableFactory<? extends Closeable> factory = configurableFactory;
        if (value != null) {
            if (factory.getOptions().contains(option)) {
                factory.setOption(option, value);
            } else {
                log.debug("XNIO component does not support option \"%s\"", option);
            }
        }
    }

    public void setBacklog(Integer backlog) throws IOException {
        doSetOption(CommonOptions.BACKLOG, backlog);
    }

    public void setBroadcast(Boolean broadcast) throws IOException {
        doSetOption(CommonOptions.BROADCAST, broadcast);
    }

    public void setCloseAbort(Boolean closeAbort) throws IOException {
        doSetOption(CommonOptions.CLOSE_ABORT, closeAbort);
    }

    public void setIpTrafficClass(Integer ipTrafficClass) throws IOException {
        doSetOption(CommonOptions.IP_TRAFFIC_CLASS, ipTrafficClass);
    }

    public void setKeepAlive(Boolean keepAlive) throws IOException {
        doSetOption(CommonOptions.KEEP_ALIVE, keepAlive);
    }

    public void setManageConnections(Boolean manageConnections) throws IOException {
        doSetOption(CommonOptions.MANAGE_CONNECTIONS, manageConnections);
    }

    public void setMulticastTtl(Integer multicastTtl) throws IOException {
        doSetOption(CommonOptions.MULTICAST_TTL, multicastTtl);
    }

    public void setOobInline(Boolean oobInline) throws IOException {
        doSetOption(CommonOptions.TCP_OOB_INLINE, oobInline);
    }

    public void setReceiveBufferSize(Integer receiveBufferSize) throws IOException {
        doSetOption(CommonOptions.RECEIVE_BUFFER, receiveBufferSize);
    }

    public void setReuseAddresses(Boolean reuseAddresses) throws IOException {
        doSetOption(CommonOptions.REUSE_ADDRESSES, reuseAddresses);
    }

    public void setSendBufferSize(Integer sendBufferSize) throws IOException {
        doSetOption(CommonOptions.SEND_BUFFER, sendBufferSize);
    }

    public void setTcpNoDelay(Boolean tcpNoDelay) throws IOException {
        doSetOption(CommonOptions.TCP_NODELAY, tcpNoDelay);
    }
}
