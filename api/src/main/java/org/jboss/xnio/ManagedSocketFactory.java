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

import java.net.Socket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.io.IOException;

import javax.net.SocketFactory;
import javax.management.NotCompliantMBeanException;

final class ManagedSocketFactory extends SocketFactory {
    private final OptionMap optionMap;
    private final Xnio xnio;

    ManagedSocketFactory(final Xnio xnio, final OptionMap optionMap) {
        this.optionMap = optionMap;
        this.xnio = xnio;
    }

    public Socket createSocket() throws IOException {
        final OptionMap optionMap = this.optionMap;
        final ManagedSocket socket = new ManagedSocket();
        socket.configure(optionMap);
        if (optionMap.get(Options.MANAGE_CONNECTIONS, true)) {
            try {
                socket.setRegistration(xnio.registerMBean(socket.getMBean()));
            } catch (NotCompliantMBeanException e) {
                throw new IOException("Cannot register channel MBean");
            }
        }
        return socket;
    }

    public Socket createSocket(final String remoteHost, final int remotePort) throws IOException {
        return createSocket(InetAddress.getByName(remoteHost), remotePort);
    }

    public Socket createSocket(final String remoteHost, final int remotePort, final InetAddress bindAddress, final int bindPort) throws IOException {
        return createSocket(InetAddress.getByName(remoteHost), remotePort, bindAddress, bindPort);
    }

    public Socket createSocket(final InetAddress remoteAddress, final int remotePort) throws IOException {
        final Socket socket = createSocket();
        socket.connect(new InetSocketAddress(remoteAddress, remotePort));
        return socket;
    }

    public Socket createSocket(final InetAddress remoteAddress, final int remotePort, final InetAddress bindAddress, final int bindPort) throws IOException {
        final Socket socket = createSocket();
        socket.bind(new InetSocketAddress(bindAddress, bindPort));
        socket.connect(new InetSocketAddress(remoteAddress, remotePort));
        return socket;
    }
}
