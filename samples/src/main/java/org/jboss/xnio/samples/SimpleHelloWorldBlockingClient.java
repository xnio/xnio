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

package org.jboss.xnio.samples;

import org.jboss.xnio.Xnio;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.OptionMap;
import org.jboss.xnio.TcpConnector;
import org.jboss.xnio.IoFuture;
import org.jboss.xnio.channels.TcpChannel;
import org.jboss.xnio.channels.Channels;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;

public final class SimpleHelloWorldBlockingClient {

    private SimpleHelloWorldBlockingClient() {
    }

    public static void main(String[] args) throws Exception {
        final Charset charset = Charset.forName("utf-8");
        final Xnio xnio = Xnio.create();
        try {
            final TcpConnector connector = xnio.createTcpConnector(OptionMap.EMPTY);
            final IoFuture<TcpChannel> futureConnection = connector.connectTo(new InetSocketAddress("localhost", 12345), null, null);
            final TcpChannel channel = futureConnection.get();
            try {
                // Send the greeting
                Channels.writeBlocking(channel, ByteBuffer.wrap("Hello world!\n".getBytes(charset)));
                // Make sure all data is written
                Channels.flushBlocking(channel);
                // And send EOF
                channel.shutdownWrites();
                System.out.println("Sent greeting string!  The response is...");
                ByteBuffer recvBuf = ByteBuffer.allocate(128);
                // Now receive and print the whole response
                while (Channels.readBlocking(channel, recvBuf) != -1) {
                    recvBuf.flip();
                    final CharBuffer chars = charset.decode(recvBuf);
                    System.out.print(chars);
                    recvBuf.clear();
                }
            } finally {
                IoUtils.safeClose(channel);
            }
        } finally {
            IoUtils.safeClose(xnio);
        }
    }
}
