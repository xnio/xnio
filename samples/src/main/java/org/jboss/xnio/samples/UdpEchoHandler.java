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

import static org.jboss.xnio.Buffers.flip;
import org.jboss.xnio.log.Logger;
import org.jboss.xnio.channels.UdpChannel;
import org.jboss.xnio.channels.MultipointReadResult;
import org.jboss.xnio.ChannelListener;
import java.nio.ByteBuffer;
import java.io.IOException;
import java.net.InetSocketAddress;

/**
 *
 */
public final class UdpEchoHandler implements ChannelListener<UdpChannel> {
    private static final Logger log = Logger.getLogger(UdpEchoHandler.class);

    public void handleEvent(final UdpChannel channel) {
        log.info("UDP echo channel opened!");
        channel.getReadSetter().set(new ChannelListener<UdpChannel>() {
            public void handleEvent(final UdpChannel channel) {
                final ByteBuffer buffer = ByteBuffer.allocate(65536);
                try {
                    final MultipointReadResult<InetSocketAddress> result = channel.receive(buffer);
                    channel.resumeReads();
                    if (result != null) {
                        flip(buffer);
                        channel.send(result.getSourceAddress(), buffer);
                    }
                } catch (IOException e) {
                    log.error("Error echoing datagram: %s", e);
                }
            }
        });
        channel.getCloseSetter().set(new ChannelListener<UdpChannel>() {
            public void handleEvent(final UdpChannel channel) {
                log.info("UDP echo channel closed!");
            }
        });
        channel.resumeReads();
    }
}
