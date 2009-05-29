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

package org.jboss.xnio.samples;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import org.jboss.xnio.ConfigurableFactory;
import org.jboss.xnio.IoFuture;
import static org.jboss.xnio.IoUtils.safeClose;
import org.jboss.xnio.TcpConnector;
import org.jboss.xnio.Xnio;
import org.jboss.xnio.channels.ConnectedStreamChannel;
import org.jboss.xnio.channels.TcpChannel;

/**
 * A simple echo client that will connect to the given IP address and echo everything back to it.
 */
public final class EchoClient {
    private EchoClient() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length != 2) {
            System.err.println("Usage: java " + EchoClient.class.getName() + " <host> <port>");
            return;
        }
        final Xnio xnio = Xnio.create();
        try {
            final ConfigurableFactory<? extends TcpConnector> connectorFactory = xnio.createTcpConnector();
            final TcpConnector connector = connectorFactory.create();
            final IoFuture<TcpChannel> ioFuture = connector.connectTo(new InetSocketAddress(InetAddress.getByName(args[0]), Integer.parseInt(args[1])), new EchoHandler());
            final ConnectedStreamChannel<SocketAddress> channel = ioFuture.get();
            try {
                while (channel.isOpen()) {
                    Thread.sleep(1000L);
                }
                channel.close();
                xnio.close();
            } finally {
                safeClose(channel);
            }
        } finally {
            safeClose(xnio);
        }
    }
}
