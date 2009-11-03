/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, JBoss Inc., and individual contributors as indicated
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

package org.jboss.xnio;

import java.net.ServerSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.InetSocketAddress;
import java.io.IOException;
import java.io.Closeable;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import javax.management.StandardMBean;
import javax.management.NotCompliantMBeanException;

import org.jboss.xnio.management.TcpServerMBean;

final class ManagedServerSocket extends ServerSocket {
    private final OptionMap optionMap;

    private static final AtomicLongFieldUpdater<ManagedServerSocket> acceptedConnectionsUpdater = AtomicLongFieldUpdater.newUpdater(ManagedServerSocket.class, "acceptedConnections");

    private volatile long acceptedConnections = 0L;
    private volatile Closeable registration;

    private static final TcpServerMBean.Listener[] NONE = new TcpServerMBean.Listener[0];

    ManagedServerSocket(final OptionMap optionMap) throws IOException {
        this.optionMap = optionMap;
    }

    ManagedServerSocket(final int port, final OptionMap optionMap) throws IOException {
        super(port, optionMap.get(Options.BACKLOG, 0));
        this.optionMap = optionMap;
    }

    ManagedServerSocket(final int port, final int backlog, final OptionMap optionMap) throws IOException {
        super(port, backlog);
        this.optionMap = optionMap;
    }

    ManagedServerSocket(final int port, final int backlog, final InetAddress bindAddr, final OptionMap optionMap) throws IOException {
        super(port, backlog, bindAddr);
        this.optionMap = optionMap;
    }

    public Socket accept() throws IOException {
        final OptionMap optionMap = this.optionMap;
        final ManagedSocket socket = new ManagedSocket();
        implAccept(socket);
        socket.configure(optionMap);
        acceptedConnectionsUpdater.getAndIncrement(this);
        return socket;
    }

    public void bind(final SocketAddress endpoint) throws IOException {
        super.bind(endpoint, optionMap.get(Options.BACKLOG, 0));
    }

    void setRegistration(final Closeable registration) {
        this.registration = registration;
    }

    MBean getMBean() throws NotCompliantMBeanException {
        return new MBean();
    }

    public void close() throws IOException {
        try {
            super.close();
        } finally {
            IoUtils.safeClose(registration);
        }
    }

    private final class MBean extends StandardMBean implements TcpServerMBean {
        public MBean() throws NotCompliantMBeanException {
            super(TcpServerMBean.class);
        }

        public String toString() {
            return "TCPServerMBean";
        }

        public Listener[] getBoundListeners() {
            final SocketAddress local = getLocalSocketAddress();
            if (local == null) {
                return NONE;
            }
            return new Listener[] {
                new Listener() {
                    public InetSocketAddress getBindAddress() {
                        return (InetSocketAddress) local;
                    }

                    public long getAcceptedConnections() {
                        return acceptedConnections;
                    }
                }
            };
        }

        public long getAcceptedConnections() {
            return acceptedConnections;
        }

        public void bind(final InetSocketAddress address) throws IOException {
            throw new IOException("Cannot bind this socket type");
        }

        public void bind(final String hostName, final int port) throws IOException {
            throw new IOException("Cannot bind this socket type");
        }

        public void unbind(final InetSocketAddress address) throws IOException {
            throw new IOException("Cannot unbind this socket type");
        }

        public void unbind(final String hostName, final int port) throws IOException {
            throw new IOException("Cannot unbind this socket type");
        }

        public void close() {
            IoUtils.safeClose(ManagedServerSocket.this);
        }

    }

}
