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
import java.net.InetSocketAddress;
import java.io.IOException;

import javax.net.ServerSocketFactory;
import javax.management.NotCompliantMBeanException;

final class ManagedServerSocketFactory extends ServerSocketFactory {
    private final OptionMap optionMap;
    private final Xnio xnio;

    ManagedServerSocketFactory(final Xnio xnio, final OptionMap optionMap) {
        this.optionMap = optionMap;
        this.xnio = xnio;
    }

    public ServerSocket createServerSocket() throws IOException {
        final ManagedServerSocket serverSocket = new ManagedServerSocket(optionMap);
        try {
            serverSocket.setRegistration(xnio.registerMBean(serverSocket.getMBean()));
        } catch (NotCompliantMBeanException e) {
            throw new IOException("Cannot register server MBean");
        }
        return serverSocket;
    }

    public ServerSocket createServerSocket(final int port) throws IOException {
        final ServerSocket serverSocket = createServerSocket();
        serverSocket.bind(new InetSocketAddress(port), optionMap.get(Options.BACKLOG, 0));
        return serverSocket;
    }

    public ServerSocket createServerSocket(final int port, final int backlog) throws IOException {
        final ServerSocket serverSocket = createServerSocket();
        serverSocket.bind(new InetSocketAddress(port), backlog == 0 ? optionMap.get(Options.BACKLOG, 0) : backlog);
        return serverSocket;
    }

    public ServerSocket createServerSocket(final int port, final int backlog, final InetAddress ifAddress) throws IOException {
        final ServerSocket serverSocket = createServerSocket();
        serverSocket.bind(new InetSocketAddress(ifAddress, port), backlog == 0 ? optionMap.get(Options.BACKLOG, 0) : backlog);
        return serverSocket;
    }
}
