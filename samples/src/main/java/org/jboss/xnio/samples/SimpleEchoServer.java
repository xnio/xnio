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
import org.jboss.xnio.ChannelListener;
import org.jboss.xnio.OptionMap;
import org.jboss.xnio.TcpServer;
import org.jboss.xnio.channels.TcpChannel;
import org.jboss.xnio.channels.Channels;
import java.nio.ByteBuffer;
import java.io.IOException;
import java.net.InetSocketAddress;

public final class SimpleEchoServer {

    public static void main(String[] args) throws Exception {
        final Xnio xnio = Xnio.create();

        // First define the listener that actually is run on each connection.
        final ChannelListener<TcpChannel> readListener = new ChannelListener<TcpChannel>() {
            public void handleEvent(final TcpChannel channel) {
                final ByteBuffer buffer = ByteBuffer.allocate(512);
                int res;
                try {
                    while ((res = channel.read(buffer)) > 0) {
                        buffer.flip();
                        Channels.writeBlocking(channel, buffer);
                    }
                    // make sure everything is flushed out
                    Channels.flushBlocking(channel);
                    if (res == -1) {
                        channel.close();
                    } else {
                        channel.resumeReads();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    IoUtils.safeClose(channel);
                }
            }
        };

        // Create an open listener.
        final ChannelListener<TcpChannel> openListener = new ChannelListener<TcpChannel>() {
            public void handleEvent(final TcpChannel channel) {
                // TCP channel has been accepted at this stage.
                channel.getReadSetter().set(readListener);
                // read listener is set; start it up
                channel.resumeReads();
            }
        };

        // Create the server.
        final TcpServer server = xnio.createTcpServer(openListener, OptionMap.EMPTY);

        // Bind it.
        server.bind(new InetSocketAddress(12345));
    }
}
