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

import org.jboss.xnio.IoUtils;
import org.jboss.xnio.ChannelListener;
import static org.jboss.xnio.Buffers.flip;
import org.jboss.xnio.log.Logger;
import org.jboss.xnio.channels.StreamChannel;
import java.nio.ByteBuffer;
import java.io.IOException;
import java.util.List;
import java.util.LinkedList;
import java.util.Collections;

/**
 * A simple stream handler that echos data back to the other end.  A simple linked list is used to queue buffers.
 */
public final class EchoHandler implements ChannelListener<StreamChannel> {

    private static final Logger log = Logger.getLogger(EchoHandler.class);

    private final List<ByteBuffer> buflist = Collections.synchronizedList(new LinkedList<ByteBuffer>());

    public void handleEvent(final StreamChannel channel) {
        log.info("Opened echo handler!");
        channel.getReadSetter().set(new ChannelListener<StreamChannel>() {
            public void handleEvent(final StreamChannel channel) {
                final ByteBuffer buffer = ByteBuffer.allocate(400);
                try {
                    final int c = channel.read(buffer);
                    if (c == -1) {
                        log.info("Remote side closed the channel.");
                        IoUtils.safeClose(channel);
                        return;
                    } else if (c == 0) {
                        return;
                    }
                    flip(buffer);
                    buflist.add(buffer);
                    channel.resumeWrites();
                } catch (IOException e) {
                    log.error("I/O exception on read: %s", e);
                    IoUtils.safeClose(channel);
                    return;
                } finally {
                    channel.resumeReads();
                }
            }
        });
        channel.getWriteSetter().set(new ChannelListener<StreamChannel>() {
            public void handleEvent(final StreamChannel channel) {
                while (! buflist.isEmpty()) {
                    final ByteBuffer buffer = buflist.get(0);
                    try {
                        final int c = channel.write(buffer);
                        if (c == 0) {
                            channel.resumeWrites();
                            return;
                        }
                    } catch (IOException e) {
                        log.error("I/O exception on write: %s", e);
                        IoUtils.safeClose(channel);
                        return;
                    }
                    if (! buffer.hasRemaining()) {
                        buflist.remove(0);
                    }
                }
            }
        });
        channel.getCloseSetter().set(new ChannelListener<StreamChannel>() {
            public void handleEvent(final StreamChannel channel) {
                log.info("Closed echo handler!");
            }
        });
        channel.resumeReads();
    }
}
