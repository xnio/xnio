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

package org.jboss.xnio.channels;

import java.nio.ByteBuffer;
import java.io.IOException;
import org.jboss.xnio.ChannelListener;
import org.jboss.xnio.OptionMap;
import org.jboss.xnio.Buffers;
import org.jboss.xnio.Options;

final class MessageStreamChannelListener implements ChannelListener<StreamSourceChannel> {

    private final int maxInboundMessageSize;

    private final ByteBuffer readLengthBuf = ByteBuffer.allocate(4);
    private ReadState readState = ReadState.LENGTH;
    private ByteBuffer readBuffer;
    private volatile MessageHandler messageHandler;
    private final MessageHandler.Setter setter = new MessageHandler.Setter() {
        public void set(final MessageHandler newMessageHandler) {
            messageHandler = newMessageHandler;
        }
    };

    private enum ReadState {
        LENGTH,
        BODY,
        EOF,
    }

    MessageStreamChannelListener(final OptionMap optionMap) {
        maxInboundMessageSize = optionMap.get(Options.MAX_INBOUND_MESSAGE_SIZE, 2048);
    }

    MessageHandler.Setter getSetter() {
        return setter;
    }

    public void handleEvent(final StreamSourceChannel channel) {
        final ByteBuffer readLengthBuf = this.readLengthBuf;
        for (;;) switch (readState) {
            case EOF: {
                return;
            }
            case LENGTH: {
                while (readLengthBuf.hasRemaining()) {
                    final int res;
                    try {
                        res = channel.read(readLengthBuf);
                    } catch (IOException e) {
                        handleException(e);
                        return;
                    }
                    if (res == 0) {
                        channel.resumeReads();
                        return;
                    }
                    if (res == -1) {
                        readState = ReadState.EOF;
                        if (readLengthBuf.position() == 0) {
                            handleEof();
                            return;
                        } else {
                            readLengthBuf.clear();
                            handleException(new IOException("Truncated message"));
                            return;
                        }
                    }
                }
                int len = Buffers.flip(readLengthBuf).getInt();
                readLengthBuf.clear();
                if (len == 0) {
                    // keep state at LENGTH
                    channel.resumeReads();
                    return;
                }
                final int maxInboundMessageSize = this.maxInboundMessageSize;
                if (len > maxInboundMessageSize) {
                    readState = ReadState.EOF;
                    handleException(new IOException("Received oversized message"));
                    return;
                }
                readState = ReadState.BODY;
                readBuffer = ByteBuffer.allocate(len);
                // fall thru to BODY
            }
            case BODY: {
                final ByteBuffer readBuffer = this.readBuffer;
                while (readBuffer.hasRemaining()) {
                    final int res;
                    try {
                        res = channel.read(readBuffer);
                    } catch (IOException e) {
                        readState = ReadState.EOF;
                        handleException(e);
                        return;
                    }
                    if (res == 0) {
                        channel.resumeReads();
                        return;
                    }
                    if (res == -1) {
                        readState = ReadState.EOF;
                        handleException(new IOException("Truncated message"));
                    }
                }
                readBuffer.flip();
                this.readBuffer = null;
                readState = ReadState.LENGTH;
                handleMessage(readBuffer);
                break;
            }
            default:
                throw new IllegalStateException();
        }
    }

    private void handleMessage(final ByteBuffer readBuffer) {
        final MessageHandler messageHandler = this.messageHandler;
        if (messageHandler != null) try {
            messageHandler.handleMessage(readBuffer);
        } catch (Throwable t) {
            // todo log
        }
    }

    private void handleEof() {
        final MessageHandler messageHandler = this.messageHandler;
        if (messageHandler != null) try {
            messageHandler.handleEof();
        } catch (Throwable t) {
            // todo log
        }
    }

    private void handleException(final IOException e) {
        final MessageHandler messageHandler = this.messageHandler;
        if (messageHandler != null) try {
            messageHandler.handleException(e);
        } catch (Throwable t) {
            // todo log
        }
    }
}
