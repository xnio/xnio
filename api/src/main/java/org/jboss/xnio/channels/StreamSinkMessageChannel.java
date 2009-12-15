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
import java.nio.channels.ClosedChannelException;
import java.nio.channels.Channel;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.jboss.xnio.Option;
import org.jboss.xnio.ChannelListener;
import org.jboss.xnio.OptionMap;
import org.jboss.xnio.Options;

final class StreamSinkMessageChannel implements WritableMessageChannel {
    private final StreamSinkChannel streamChannel;

    private final int maxOutboundMessageSize;
    private final OurSetter writeSetter;
    private final OurSetter closeSetter;

    // write fields
    private final Object writeLock = new Object();
    private final ByteBuffer writeLengthBuf = ByteBuffer.allocate(4);
    private WriteState writeState = WriteState.WAITING;
    private ByteBuffer writeBuffer;

    private enum WriteState {
        DOWN,
        WAITING,
        LENGTH,
        BODY,
    }

    StreamSinkMessageChannel(final StreamSinkChannel streamChannel, final OptionMap optionMap) {
        maxOutboundMessageSize = optionMap.get(Options.MAX_OUTBOUND_MESSAGE_SIZE, 2048);
        this.streamChannel = streamChannel;
        writeSetter = new OurSetter(streamChannel.getWriteSetter());
        closeSetter = new OurSetter(streamChannel.getCloseSetter());
    }

    private final class OurSetter implements ChannelListener.Setter<StreamSinkMessageChannel> {
        private final ChannelListener.Setter<? extends StreamSinkChannel> setter;

        private OurSetter(final ChannelListener.Setter<? extends StreamSinkChannel> setter) {
            this.setter = setter;
        }

        public void set(final ChannelListener<? super StreamSinkMessageChannel> channelListener) {
            setter.set(new ChannelListener<Channel>() {
                public void handleEvent(final Channel channel) {
                    channelListener.handleEvent(StreamSinkMessageChannel.this);
                }
            });
        }
    }

    public ChannelListener.Setter<StreamSinkMessageChannel> getCloseSetter() {
        return closeSetter;
    }

    public ChannelListener.Setter<StreamSinkMessageChannel> getWriteSetter() {
        return writeSetter;
    }

    public boolean isOpen() {
        return streamChannel.isOpen();
    }

    public void close() throws IOException {
        synchronized (writeLock) {
            writeState = WriteState.DOWN;
            writeBuffer = null;
        }
        streamChannel.close();
    }

    public boolean supportsOption(final Option<?> option) {
        return streamChannel.supportsOption(option);
    }

    public <T> T getOption(final Option<T> option) throws IOException {
        return streamChannel.getOption(option);
    }

    public <T> StreamSinkMessageChannel setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        streamChannel.setOption(option, value);
        return this;
    }

    public boolean send(final ByteBuffer buffer) throws IOException {
        return send(new ByteBuffer[] { buffer });
    }

    public boolean send(final ByteBuffer[] buffers) throws IOException {
        return send(buffers, 0, buffers.length);
    }

    public boolean send(final ByteBuffer[] buffers, final int offs, final int len) throws IOException {
        int size = 0;
        for (int i = 0; i < len; i ++) {
            size += buffers[offs + i].remaining();
            if (size < 0) break;
        }
        if (size < 0 || size > maxOutboundMessageSize) {
            throw new IOException("Message exceeds outbound message size");
        }
        final ByteBuffer writeLengthBuf = this.writeLengthBuf;
        final StreamSinkChannel streamChannel = this.streamChannel;
        synchronized (writeLock) {
            boolean ok = false;
            try {
                for (;;) switch (writeState) {
                    case LENGTH: {
                        while (writeLengthBuf.hasRemaining()) {
                            if (streamChannel.write(writeLengthBuf) == 0) {
                                ok = true;
                                return false;
                            }
                        }
                        writeLengthBuf.clear();
                        writeState = WriteState.LENGTH;
                        // fall thru
                    }
                    case BODY: {
                        final ByteBuffer writeBuffer = this.writeBuffer;
                        while (writeBuffer.hasRemaining()) {
                            if (streamChannel.write(writeBuffer) == 0) {
                                ok = true;
                                return false;
                            }
                        }
                        this.writeBuffer = null;
                        writeState = WriteState.WAITING;
                        // fall thru
                    }
                    case WAITING: {
                        writeLengthBuf.putInt(size).flip();
                        while (writeLengthBuf.hasRemaining()) {
                            if (streamChannel.write(writeLengthBuf) == 0) {
                                final ByteBuffer writeBuffer = ByteBuffer.allocate(size);
                                for (int i = 0; i < len; i++) {
                                    writeBuffer.put(buffers[i + offs]);
                                }
                                writeBuffer.flip();
                                this.writeBuffer = writeBuffer;
                                writeState = WriteState.LENGTH;
                                ok = true;
                                return false;
                            }
                        }
                        writeLengthBuf.clear();
                        while (size > 0) {
                            long res = streamChannel.write(buffers, offs, len);
                            if (res == 0L) {
                                final ByteBuffer writeBuffer = ByteBuffer.allocate(size);
                                for (int i = 0; i < len; i++) {
                                    writeBuffer.put(buffers[i + offs]);
                                }
                                writeBuffer.flip();
                                this.writeBuffer = writeBuffer;
                                writeState = WriteState.BODY;
                                ok = true;
                                return true;
                            }
                        }
                        ok = true;
                        return true;
                    }
                    case DOWN: {
                        throw new ClosedChannelException();
                    }
                }
            } finally {
                if (! ok) {
                    writeState = WriteState.DOWN;
                    writeBuffer = null;
                }
            }
        }
    }

    public boolean flush() throws IOException {
        synchronized (writeLock) {
            boolean ok = false;
            try {
                for (;;) switch (writeState) {
                    case LENGTH: {
                        while (writeLengthBuf.hasRemaining()) {
                            if (streamChannel.write(writeLengthBuf) == 0) {
                                ok = true;
                                return false;
                            }
                        }
                        writeLengthBuf.clear();
                        writeState = WriteState.BODY;
                        // fall thru
                    }
                    case BODY: {
                        final ByteBuffer writeBuffer = this.writeBuffer;
                        while (writeBuffer.hasRemaining()) {
                            if (streamChannel.write(writeBuffer) == 0) {
                                ok = true;
                                return false;
                            }
                        }
                        this.writeBuffer = null;
                        writeState = WriteState.WAITING;
                        ok = true;
                        return true;
                    }
                    case DOWN: {
                        throw new ClosedChannelException();
                    }
                    default: {
                        ok = true;
                        return true;
                    }
                }
            } finally {
                if (! ok) {
                    writeState = WriteState.DOWN;
                    writeBuffer = null;
                }
            }
        }
    }

    public void suspendWrites() {
        streamChannel.suspendWrites();
    }

    public void resumeWrites() {
        streamChannel.resumeWrites();
    }

    public boolean shutdownWrites() throws IOException {
        synchronized (writeLock) {
            if (flush()) {
                writeState = WriteState.DOWN;
                writeBuffer = null;
                return streamChannel.shutdownWrites();
            }
        }
        return false;
    }

    public void awaitWritable() throws IOException {
        streamChannel.awaitWritable();
    }

    public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
        streamChannel.awaitWritable(time, timeUnit);
    }
}