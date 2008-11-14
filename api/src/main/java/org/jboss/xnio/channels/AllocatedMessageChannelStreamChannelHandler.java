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

package org.jboss.xnio.channels;

import org.jboss.xnio.IoHandler;
import org.jboss.xnio.log.Logger;
import java.nio.ByteBuffer;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;

final class AllocatedMessageChannelStreamChannelHandler implements IoHandler<StreamChannel> {

    private static final Logger log = Logger.getLogger("org.jboss.xnio.channels.allocated-message");

    private volatile AllocatedMessageChannelImpl messageChannel;
    private final int maxInboundMessageSize;
    private final int maxOutboundMessageSize;

    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);

    private final IoHandler<? super AllocatedMessageChannel> handler;

    private final AtomicBoolean isnew = new AtomicBoolean(true);
    private final AtomicBoolean closed = new AtomicBoolean();

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
        EOF,
    }

    // write fields
    private final Object writeLock = new Object();
    private final ByteBuffer writeLengthBuf = ByteBuffer.allocate(4);
    private WriteState writeState = WriteState.WAITING;
    private ByteBuffer writeBuffer;
    private IOException writeException;
    private boolean writeShutdown;

    private enum WriteState {
        FAILED,
        WAITING,
        LENGTH,
        BODY,
    }

    AllocatedMessageChannelStreamChannelHandler(final IoHandler<? super AllocatedMessageChannel> handler, final int maxInboundMessageSize, final int maxOutboundMessageSize) {
        this.handler = handler;
        this.maxInboundMessageSize = maxInboundMessageSize;
        this.maxOutboundMessageSize = maxOutboundMessageSize;
    }

    public void handleOpened(final StreamChannel channel) {
        if (isnew.getAndSet(false)) {
            messageChannel = new AllocatedMessageChannelImpl(channel);
        }
        if (channel.getOptions().contains(CommonOptions.TCP_NODELAY)) {
            try {
                channel.setOption(CommonOptions.TCP_NODELAY, Boolean.TRUE);
            } catch (IOException e) {
                log.trace("Setting TCP_NODELAY on channel %s failed: %s", channel, e);
            }
        }
        handler.handleOpened(messageChannel);
    }

    public void handleReadable(final StreamChannel channel) {
        handler.handleReadable(messageChannel);
    }

    public void handleWritable(final StreamChannel channel) {
        synchronized (writeLock) {
            if (writeException != null) {
                handler.handleWritable(messageChannel);
            } else switch (writeState) {
                case LENGTH: {
                    while (writeLengthBuf.hasRemaining()) {
                        try {
                            final int cnt = channel.write(writeLengthBuf);
                            if (cnt == 0) {
                                channel.resumeWrites();
                                return;
                            }
                        } catch (IOException e) {
                            writeException = e;
                            handler.handleWritable(messageChannel);
                            return;
                        }
                    }
                    writeState = WriteState.BODY;
                    // fall thru
                }
                case BODY: {
                    while (writeBuffer.hasRemaining()) {
                        try {
                            final int cnt = channel.write(writeBuffer);
                            if (cnt == 0) {
                                channel.resumeWrites();
                                return;
                            }
                        } catch (IOException e) {
                            writeException = e;
                            writeState = WriteState.FAILED;
                            handler.handleWritable(messageChannel);
                            return;
                        }
                    }
                    writeBuffer = null;
                    if (writeShutdown) {
                        writeShutdown = false;
                        try {
                            channel.shutdownWrites();
                        } catch (IOException e) {
                            log.trace("Write shutdown failed: %s", e);
                        }
                        return;
                    }
                    writeState = WriteState.WAITING;
                    // fall thru
                }
                case WAITING: {
                    handler.handleWritable(messageChannel);
                    return;
                }
            }
        }
    }

    public void handleClosed(final StreamChannel channel) {
        handler.handleClosed(messageChannel);
    }

    AllocatedMessageChannel getChannel(final StreamChannel channel) {
        if (isnew.getAndSet(false)) {
            messageChannel = new AllocatedMessageChannelImpl(channel);
        }
        return messageChannel;
    }

    private final class AllocatedMessageChannelImpl implements AllocatedMessageChannel {

        private final StreamChannel streamChannel;

        private AllocatedMessageChannelImpl(final StreamChannel streamChannel) {
            this.streamChannel = streamChannel;
        }

        public boolean isOpen() {
            return ! closed.get();
        }

        public void close() throws IOException {
            if (closed.getAndSet(true)) {
                streamChannel.close();
            }
        }

        public <T> T getOption(final ChannelOption<T> option) throws UnsupportedOptionException, IOException {
            return streamChannel.getOption(option);
        }

        public Set<ChannelOption<?>> getOptions() {
            return streamChannel.getOptions();
        }

        public <T> Configurable setOption(final ChannelOption<T> option, final T value) throws IllegalArgumentException, IOException {
            streamChannel.setOption(option, value);
            return this;
        }

        public boolean send(final ByteBuffer buffer) throws IOException {
            return send(new ByteBuffer[] { buffer }, 0, 1);
        }

        public boolean send(final ByteBuffer[] buffers) throws IOException {
            return send(buffers, 0, buffers.length);
        }

        public boolean send(final ByteBuffer[] buffers, final int offs, final int len) throws IOException {
            long total = 0;
            for (int i = 0; i < len; i ++) {
                total += (long) buffers[offs + i].remaining();
            }
            if (total > maxOutboundMessageSize) {
                throw new IOException("Packet too large");
            }
            synchronized (writeLock) {
                if (writeException != null) {
                    final IOException e = new IOException("Write operation failed");
                    e.initCause(writeException);
                    writeException = null;
                    throw e;
                }
                final StreamChannel streamChannel = this.streamChannel;
                switch (writeState) {
                    case WAITING: {
                        final ByteBuffer writeLengthBuf = AllocatedMessageChannelStreamChannelHandler.this.writeLengthBuf;
                        writeLengthBuf.clear();
                        writeLengthBuf.putInt((int)total);
                        writeLengthBuf.flip();
                        // stage one - write the length
                        while (writeLengthBuf.hasRemaining()) {
                            final int cnt = streamChannel.write(writeLengthBuf);
                            if (cnt == 0) {
                                if (writeLengthBuf.remaining() == 4) {
                                    // no harm done; channel simply isn't writable
                                    return false;
                                } else {
                                    // partial length written
                                    // copy the whole payload, boo
                                    writeBuffer = ByteBuffer.allocate((int)total);
                                    for (int i = 0; i < len; i ++) {
                                        writeBuffer.put(buffers[offs + i]);
                                    }
                                    writeState = WriteState.LENGTH;
                                    streamChannel.resumeWrites();
                                    return true;
                                }
                            }
                        }
                        if (total == 0L) {
                            // no payload to send
                            return true;
                        }
                        // stage two - write the body
                        long sentTotal = 0;
                        for (;;) {
                            final long cnt = streamChannel.write(buffers, offs, len);
                            if (cnt == 0) {
                                // not all of the payload was sent; copy the remainder
                                int rem = (int) (total - sentTotal);
                                writeBuffer = ByteBuffer.allocate(rem);
                                writeState = WriteState.BODY;
                                streamChannel.resumeWrites();
                                return true;
                            } else {
                                sentTotal += (long) cnt;
                                if (sentTotal == total) {
                                    // wrote the whole thing!
                                    return true;
                                }
                            }
                        }
                    }
                    default: {
                        // there's an outstanding partial write; we would block
                        return false;
                    }
                }
            }
        }

        public ByteBuffer receive() throws IOException {
            synchronized (readLock) {
                for (;;) switch (readState) {
                    case EOF: {
                        return null;
                    }
                    case DRAIN: {
                        while (drainCnt > 0L) {
                            readBuffer.clear();
                            if ((long)readBuffer.limit() > drainCnt) {
                                readBuffer.limit((int)drainCnt);
                            }
                            final int cnt = streamChannel.read(readBuffer);
                            if (cnt == -1) {
                                readState = ReadState.EOF;
                                readBuffer = null;
                                return null;
                            } else if (cnt == 0) {
                                return EMPTY_BUFFER;
                            }
                        }
                        readBuffer = null;
                        readState = ReadState.LENGTH;
                        // fall thru
                    }
                    case LENGTH: {
                        while (readLengthBuf.hasRemaining()) {
                            final int c = streamChannel.read(readLengthBuf);
                            if (c == -1) {
                                readState = ReadState.EOF;
                                return null;
                            } else if (c == 0) {
                                return EMPTY_BUFFER;
                            }
                        }
                        readLengthBuf.flip();
                        final int len = readLengthBuf.getInt();
                        if (len > maxInboundMessageSize || len < 0) {
                            log.trace("Received oversized message (%d), draining", Integer.valueOf(len));
                            readState = ReadState.DRAIN;
                            drainCnt = (long)len & 0xFFFFFFFFL;
                            break;
                        }
                        readBuffer = ByteBuffer.allocate(len);
                        readState = ReadState.BODY;
                        // fall thru
                    }
                    case BODY: {
                        while (readBuffer.hasRemaining()) {
                            final int c = streamChannel.read(readBuffer);
                            if (c == -1) {
                                readState = ReadState.EOF;
                                readBuffer = null;
                                return null;
                            } else if (c == 0) {
                                return EMPTY_BUFFER;
                            }
                        }
                        readBuffer.flip();
                        try {
                            return readBuffer;
                        } finally {
                            readBuffer = null;
                            readState = ReadState.LENGTH;
                            readLengthBuf.clear();
                        }
                    }
                    default: {
                        throw new IllegalStateException();
                    }
                }
            }
        }

        public void suspendReads() {
            streamChannel.suspendReads();
        }

        public void suspendWrites() {
            streamChannel.suspendWrites();
        }

        public void resumeReads() {
            streamChannel.resumeReads();
        }

        public void resumeWrites() {
            streamChannel.resumeWrites();
        }

        public void shutdownReads() throws IOException {
            synchronized (readLock) {
                readBuffer = null;
                readState = ReadState.LENGTH;
                streamChannel.shutdownReads();
            }
        }

        public void shutdownWrites() throws IOException {
            synchronized (writeLock) {
                if (writeState == WriteState.WAITING) {
                    streamChannel.shutdownWrites();
                } else {

                }
                writeBuffer = null;
                writeState = WriteState.WAITING;
                streamChannel.shutdownWrites();
            }
        }

        public void awaitReadable() throws IOException {
            streamChannel.awaitReadable();
        }

        public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
            streamChannel.awaitReadable(time, timeUnit);
        }

        public void awaitWritable() throws IOException {
            streamChannel.awaitWritable();
        }

        public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
            streamChannel.awaitWritable(time, timeUnit);
        }

        public String toString() {
            return "allocated message channel <" + Integer.toHexString(hashCode()) + "> over " + streamChannel;
        }
    }
}
