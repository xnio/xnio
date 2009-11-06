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
import org.jboss.xnio.Buffers;
import org.jboss.xnio.Options;

final class WrappingAllocatedMessageChannel implements AllocatedMessageChannel {
    private final StreamChannel streamChannel;

    private final int maxInboundMessageSize;
    private final int maxOutboundMessageSize;
    private final OurSetter readSetter;
    private final OurSetter writeSetter;
    private final OurSetter closeSetter;


    // read fields
    private final Object readLock = new Object();
    private final ByteBuffer readLengthBuf = ByteBuffer.allocate(4);
    private long drainCnt;
    private ReadState readState = ReadState.LENGTH;
    private ByteBuffer readBuffer;

    private enum ReadState {
        DRAIN,
        LENGTH,
        BODY,
        BODY_DRAIN,
        RUNT_EOF,
        EOF,
    }

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

    WrappingAllocatedMessageChannel(final StreamChannel streamChannel, final OptionMap optionMap) {
        maxInboundMessageSize = optionMap.get(Options.MAX_INBOUND_MESSAGE_SIZE, 2048);
        maxOutboundMessageSize = optionMap.get(Options.MAX_OUTBOUND_MESSAGE_SIZE, 2048);
        this.streamChannel = streamChannel;
        readSetter = new OurSetter(streamChannel.getReadSetter());
        writeSetter = new OurSetter(streamChannel.getWriteSetter());
        closeSetter = new OurSetter(streamChannel.getCloseSetter());
    }

    public ByteBuffer receive() throws IOException {
        final Object readLock = this.readLock;
        final StreamChannel streamChannel = this.streamChannel;
        final ByteBuffer readLengthBuf = this.readLengthBuf;
        synchronized (readLock) {
            for (;;) switch (readState) {
                case EOF: {
                    return EOF;
                }
                case LENGTH: {
                    while (readLengthBuf.hasRemaining()) {
                        final int res = streamChannel.read(readLengthBuf);
                        if (res == 0) { return WOULD_BLOCK; }
                        if (res == -1) {
                            if (readLengthBuf.position() == 0) {
                                readState = ReadState.EOF;
                                return EOF;
                            } else {
                                readState = ReadState.RUNT_EOF;
                                return RUNT;
                            }
                        }
                    }
                    int len = Buffers.flip(readLengthBuf).getInt();
                    readLengthBuf.clear();
                    if (len == 0) {
                        // keep state at LENGTH
                        return EMPTY;
                    }
                    final int maxInboundMessageSize = this.maxInboundMessageSize;
                    if (len > maxInboundMessageSize) {
                        readBuffer = ByteBuffer.allocate(maxInboundMessageSize);
                        drainCnt = len - maxInboundMessageSize;
                        readState = ReadState.BODY_DRAIN;
                        return GIANT;
                    }
                    readState = ReadState.BODY;
                    readBuffer = ByteBuffer.allocate(len);
                    // fall thru to BODY
                }
                case BODY_DRAIN:
                case BODY: {
                    final ByteBuffer readBuffer = this.readBuffer;
                    while (readBuffer.hasRemaining()) {
                        final int res = streamChannel.read(readBuffer);
                        if (res == 0) { return WOULD_BLOCK; }
                        if (res == -1) { readState = ReadState.RUNT_EOF; return RUNT; }
                    }
                    readBuffer.flip();
                    if (readState == ReadState.BODY_DRAIN) {
                        readState = ReadState.DRAIN;
                        this.readBuffer = ByteBuffer.allocate((int) Math.min(drainCnt, 8192L));
                        ByteBuffer drainBuffer = this.readBuffer;
                        while (drainCnt > 0L) {
                            while (drainBuffer.hasRemaining()) {
                                int res = streamChannel.read(drainBuffer);
                                if (res == 0) {
                                    this.readBuffer = null;
                                    return readBuffer;
                                }
                                if (res == -1) {
                                    this.readBuffer = readBuffer;
                                    readState = ReadState.RUNT_EOF;
                                    return RUNT;
                                }
                                drainCnt -= (long) res;
                            }
                            drainBuffer.position((int) Math.max(0L, drainBuffer.capacity() - drainCnt));
                        }
                    }
                    this.readBuffer = null;
                    readState = ReadState.LENGTH;
                    return readBuffer;
                }
                case DRAIN: {
                    ByteBuffer drainBuffer = readBuffer;
                    while (drainCnt > 0L) {
                        while (drainBuffer.hasRemaining()) {
                            int res = streamChannel.read(drainBuffer);
                            if (res == 0) {
                                return WOULD_BLOCK;
                            }
                            if (res == -1) {
                                readBuffer = null;
                                readState = ReadState.RUNT_EOF;
                                return RUNT;
                            }
                            drainCnt -= (long) res;
                        }
                        drainBuffer.position((int) Math.max(0L, drainBuffer.capacity() - drainCnt));
                    }
                    readBuffer = null;
                    readState = ReadState.LENGTH;
                    break;
                }
                case RUNT_EOF: {
                    readState = ReadState.EOF;
                    try {
                        final ByteBuffer readBuffer = this.readBuffer;
                        return readBuffer == null ? EOF : readBuffer;
                    } finally {
                        readBuffer = null;
                    }
                }
            }
        }
    }

    public void suspendReads() {
        streamChannel.suspendReads();
    }

    public void resumeReads() {
        streamChannel.resumeReads();
    }

    public void shutdownReads() throws IOException {
        streamChannel.shutdownReads();
    }

    public void awaitReadable() throws IOException {
        streamChannel.awaitReadable();
    }

    public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
        streamChannel.awaitReadable(time, timeUnit);
    }

    private final class OurSetter implements ChannelListener.Setter<WrappingAllocatedMessageChannel> {
        private final ChannelListener.Setter<? extends StreamChannel> setter;

        private OurSetter(final ChannelListener.Setter<? extends StreamChannel> setter) {
            this.setter = setter;
        }

        public void set(final ChannelListener<? super WrappingAllocatedMessageChannel> channelListener) {
            setter.set(new ChannelListener<Channel>() {
                public void handleEvent(final Channel channel) {
                    channelListener.handleEvent(WrappingAllocatedMessageChannel.this);
                }
            });
        }
    }

    public ChannelListener.Setter<WrappingAllocatedMessageChannel> getReadSetter() {
        return readSetter;
    }

    public ChannelListener.Setter<WrappingAllocatedMessageChannel> getCloseSetter() {
        return closeSetter;
    }

    public ChannelListener.Setter<WrappingAllocatedMessageChannel> getWriteSetter() {
        return writeSetter;
    }

    public boolean isOpen() {
        return streamChannel.isOpen();
    }

    public void close() throws IOException {
        synchronized (readLock) {
            readState = ReadState.EOF;
            readBuffer = null;
        }
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

    public <T> AllocatedMessageChannel setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
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
        final StreamChannel streamChannel = this.streamChannel;
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
