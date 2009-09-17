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

import org.jboss.xnio.Xnio;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.OptionMap;
import org.jboss.xnio.UdpServer;
import java.io.IOException;
import java.net.InetSocketAddress;

/**
 *
 */
public final class UdpEchoServer {
    private UdpEchoServer() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        final Xnio xnio = Xnio.create();
        final UdpServer server = xnio.createUdpServer(new UdpEchoServerHandlerFactory(), OptionMap.EMPTY);
        try {
            server.bind(new InetSocketAddress(10007)).await();
            while (true) {
                Thread.sleep(1000L);
            }
        } finally {
            IoUtils.safeClose(server);
        }
    }
}
