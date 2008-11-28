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

package org.jboss.xnio.management;

import java.net.Socket;
import java.net.SocketAddress;

import org.jboss.xnio.nio.NioTcpChannel;

/**
 *
 */
public class ConnectedInetChannel extends Channel implements ConnectedInetChannelMBean {

    final Socket socket;

    /**
     * @param nioTcpChannel
     * @param socket
     * @param string
     */
    public ConnectedInetChannel(final NioTcpChannel nioTcpChannel,
            final Socket socket) {
        super(nioTcpChannel, socket);
        this.socket = socket;
    }

    public String getLocalAddress() {
        SocketAddress sa = socket.getLocalSocketAddress();
        if (sa==null) {
            return null;
        } else {
            return sa.toString();
        }
    }

    public int getLocalPort() {
        return socket.getLocalPort();
    }

    public String getRemoteAddress() {
        SocketAddress sa = socket.getRemoteSocketAddress();
        if (sa==null) {
            return null;
        } else {
            return sa.toString();
        }
    }

    public int getRemotePort() {
        return socket.getPort();
    }

}
