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
import static org.jboss.xnio.Buffers.slice;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.Set;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 *
 */
final class StreamChannelAllocatedMessageChannelHandler implements IoHandler<AllocatedMessageChannel> {

    private final IoHandler<? super StreamChannel> handler;

    private final AtomicBoolean isnew = new AtomicBoolean(true);
    private final AtomicBoolean closed = new AtomicBoolean();

    private volatile StreamChannelImpl streamChannel;

    private final Lock readLock = new ReentrantLock();
    private ByteBuffer readBuffer;

    StreamChannel getChannel(final AllocatedMessageChannel channel) {
        if (isnew.getAndSet(false)) {
            streamChannel = new StreamChannelImpl(channel);
        }
        return streamChannel;
    }

    public StreamChannelAllocatedMessageChannelHandler(final IoHandler<? super StreamChannel> handler) {
        this.handler = handler;
    }

    public void handleOpened(final AllocatedMessageChannel channel) {
        if (isnew.getAndSet(false)) {
            streamChannel = new StreamChannelImpl(channel);
        }
        handler.handleOpened(streamChannel);
    }

    public void handleClosed(final AllocatedMessageChannel channel) {
        handler.handleClosed(streamChannel);
    }

    public void handleReadable(final AllocatedMessageChannel channel) {
        handler.handleReadable(streamChannel);
    }

    public void handleWritable(final AllocatedMessageChannel channel) {
        handler.handleWritable(streamChannel);
    }

    private class StreamChannelImpl implements StreamChannel {

        private final AllocatedMessageChannel messageChannel;

        private StreamChannelImpl(final AllocatedMessageChannel messageChannel) {
            this.messageChannel = messageChannel;
        }

        public boolean isOpen() {
            return false;
        }

        public void close() throws IOException {
            if (! closed.getAndSet(true)) {
                messageChannel.close();
            }
        }

        public void suspendReads() {
            messageChannel.suspendReads();
        }

        public void resumeReads() {
            messageChannel.resumeReads();
        }

        public void shutdownReads() throws IOException {
            messageChannel.shutdownReads();
        }

        public void awaitReadable() throws IOException {
            messageChannel.awaitReadable();
        }

        public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
            messageChannel.awaitReadable(time, timeUnit);
        }

        public <T> T getOption(final ChannelOption<T> option) throws UnsupportedOptionException, IOException {
            return messageChannel.getOption(option);
        }

        public Set<ChannelOption<?>> getOptions() {
            return messageChannel.getOptions();
        }

        public <T> Configurable setOption(final ChannelOption<T> option, final T value) throws IllegalArgumentException, IOException {
            return messageChannel.setOption(option, value);
        }

        public void suspendWrites() {
            messageChannel.suspendWrites();
        }

        public void resumeWrites() {
            messageChannel.resumeWrites();
        }

        public void shutdownWrites() throws IOException {
            messageChannel.shutdownWrites();
        }

        public void awaitWritable() throws IOException {
            messageChannel.awaitWritable();
        }

        public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
            messageChannel.awaitWritable(time, timeUnit);
        }

        public int write(final ByteBuffer src) throws IOException {
            int c = src.remaining();
            return messageChannel.send(src) ? c : 0;
        }

        public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
            long c = 0L;
            for (int i = 0; i < length; i ++) {
                c += (long) srcs[i].remaining();
            }
            return messageChannel.send(srcs, offset, length) ? c : 0;
        }

        public long write(final ByteBuffer[] srcs) throws IOException {
            return write(srcs, 0, srcs.length);
        }

        public int read(final ByteBuffer dst) throws IOException {
            int cnt = 0;
            readLock.lock();
            try {
                ByteBuffer readBuffer = StreamChannelAllocatedMessageChannelHandler.this.readBuffer;
                try {
                    for (;;) {
                        if (readBuffer == null && (readBuffer = messageChannel.receive()) == null) {
                            return cnt;
                        }
                        final int dstRemaining = dst.remaining();
                        final int readBufferRemaining = readBuffer.remaining();
                        if (readBufferRemaining < dstRemaining) {
                            dst.put(readBuffer);
                            cnt += readBufferRemaining;
                            readBuffer = null;
                        } else {
                            dst.put(slice(readBuffer, dstRemaining));
                            cnt += dstRemaining;
                            // dst is full
                            return cnt;
                        }
                    }
                } finally {
                    StreamChannelAllocatedMessageChannelHandler.this.readBuffer = readBuffer;
                }
            } finally {
                readLock.unlock();
            }
        }

        public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
            long cnt = 0L;
            readLock.lock();
            try {
                ByteBuffer readBuffer = StreamChannelAllocatedMessageChannelHandler.this.readBuffer;
                try {
                    for (int i = 0; i < length; i++) {
                        final ByteBuffer dst = dsts[i + offset];
                        for (;;) {
                            if (readBuffer == null && (readBuffer = messageChannel.receive()) == null) {
                                return cnt;
                            }
                            final int dstRemaining = dst.remaining();
                            final int readBufferRemaining = readBuffer.remaining();
                            if (readBufferRemaining < dstRemaining) {
                                dst.put(readBuffer);
                                cnt += (long) readBufferRemaining;
                                readBuffer = null;
                            } else {
                                dst.put(slice(readBuffer, dstRemaining));
                                cnt += (long) dstRemaining;
                                // dst is full
                                break;
                            }
                        }
                    }
                    return cnt;
                } finally {
                    StreamChannelAllocatedMessageChannelHandler.this.readBuffer = readBuffer;
                }
            } finally {
                readLock.unlock();
            }
        }

        public long read(final ByteBuffer[] dsts) throws IOException {
            return read(dsts, 0, dsts.length);
        }
    }
}
