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

package org.xnio.nio;

import java.io.IOException;
import java.io.Closeable;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.Pipe;
import java.nio.channels.SelectionKey;
import java.nio.channels.FileChannel;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.TimeUnit;
import org.xnio.IoUtils;
import org.xnio.Option;
import org.xnio.ChannelListener;
import org.xnio.channels.StreamChannel;
import org.xnio.channels.UnsupportedOptionException;

/**
 *
 */
final class NioPipeChannel implements StreamChannel {

    private final Pipe.SourceChannel sourceChannel;
    private final Pipe.SinkChannel sinkChannel;
    private final NioHandle sourceHandle;
    private final NioHandle sinkHandle;
    private final NioXnio nioXnio;
    private final AtomicLong bytes;
    private final AtomicLong messages;
    private final Closeable mbeanHandle;

    private volatile ChannelListener<? super StreamChannel> readListener = null;
    private volatile ChannelListener<? super StreamChannel> writeListener = null;
    private volatile ChannelListener<? super StreamChannel> closeListener = null;

    private volatile int closeBits = 0;

    private static final AtomicReferenceFieldUpdater<NioPipeChannel, ChannelListener> readListenerUpdater = AtomicReferenceFieldUpdater.newUpdater(NioPipeChannel.class, ChannelListener.class, "readListener");
    private static final AtomicReferenceFieldUpdater<NioPipeChannel, ChannelListener> writeListenerUpdater = AtomicReferenceFieldUpdater.newUpdater(NioPipeChannel.class, ChannelListener.class, "writeListener");
    private static final AtomicReferenceFieldUpdater<NioPipeChannel, ChannelListener> closeListenerUpdater = AtomicReferenceFieldUpdater.newUpdater(NioPipeChannel.class, ChannelListener.class, "closeListener");

    private static final AtomicIntegerFieldUpdater<NioPipeChannel> closeBitsUpdater = AtomicIntegerFieldUpdater.newUpdater(NioPipeChannel.class, "closeBits");

    private final ChannelListener.Setter<StreamChannel> readSetter = IoUtils.getSetter(this, readListenerUpdater);
    private final ChannelListener.Setter<StreamChannel> writeSetter = IoUtils.getSetter(this, writeListenerUpdater);
    private final ChannelListener.Setter<StreamChannel> closeSetter = IoUtils.getSetter(this, closeListenerUpdater);

    private NioPipeChannel(final Pipe.SourceChannel sourceChannel, final Pipe.SinkChannel sinkChannel, final NioXnio nioXnio, final AtomicLong bytes, final AtomicLong messages, final Closeable mbeanHandle) throws IOException {
        this.sourceChannel = sourceChannel;
        this.sinkChannel = sinkChannel;
        this.nioXnio = nioXnio;
        this.bytes = bytes;
        this.messages = messages;
        this.mbeanHandle = mbeanHandle;
        // todo leaking [this]
        sourceHandle = nioXnio.addReadHandler(sourceChannel, new ReadHandler());
        sinkHandle = nioXnio.addWriteHandler(sinkChannel, new WriteHandler());
    }

    static NioPipeChannel create(final Pipe.SourceChannel sourceChannel, final Pipe.SinkChannel sinkChannel, final NioXnio nioXnio, final AtomicLong bytes, final AtomicLong messages, final Closeable mbeanHandle) throws IOException {
        final NioPipeChannel channel = new NioPipeChannel(sourceChannel, sinkChannel, nioXnio, bytes, messages, mbeanHandle);
        return channel;
    }

    public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        return target.transferFrom(sourceChannel, position, count);
    }

    public ChannelListener.Setter<StreamChannel> getReadSetter() {
        return readSetter;
    }

    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        return src.transferTo(position, count, sinkChannel);
    }

    public ChannelListener.Setter<StreamChannel> getWriteSetter() {
        return writeSetter;
    }

    public ChannelListener.Setter<StreamChannel> getCloseSetter() {
        return closeSetter;
    }

    public boolean flush() throws IOException {
        return true;
    }

    public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        final long ret = sinkChannel.write(srcs, offset, length);
        if (ret > 0) {
            bytes.addAndGet(ret);
            messages.incrementAndGet();
        }
        return ret;
    }

    public long write(final ByteBuffer[] srcs) throws IOException {
        final long ret = sinkChannel.write(srcs);
        if (ret > 0) {
            bytes.addAndGet(ret);
            messages.incrementAndGet();
        }
        return ret;
    }

    public int write(final ByteBuffer src) throws IOException {
        final int ret = sinkChannel.write(src);
        if (ret > 0) {
            bytes.addAndGet(ret);
            messages.incrementAndGet();
        }
        return ret;
    }

    public boolean isOpen() {
        return sourceChannel.isOpen() && sinkChannel.isOpen();
    }

    private static int setBits(NioPipeChannel instance, int bits) {
        int old;
        int updated;
        do {
            old = instance.closeBits;
            updated = old | bits;
            if (updated == old) {
                break;
            }
        } while (! closeBitsUpdater.compareAndSet(instance, old, updated));
        return old;
    }

    public void close() throws IOException {
        // since we've got two channels, only rethrow a failure on the WRITE side, since that's the side that stands to lose data
        IoUtils.safeClose(sourceChannel);
        try {
            sinkChannel.close();
        } finally {
            if (setBits(this, 0x04) < 0x04) {
                nioXnio.removeManaged(this);
                IoUtils.<StreamChannel>invokeChannelListener(this, closeListener);
                IoUtils.safeClose(mbeanHandle);
            }
        }
    }

    public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        final long ret = sourceChannel.read(dsts, offset, length);
        return ret;
    }

    public long read(final ByteBuffer[] dsts) throws IOException {
        final long ret = sourceChannel.read(dsts);
        return ret;
    }

    public int read(final ByteBuffer dst) throws IOException {
        final int ret = sourceChannel.read(dst);
        return ret;
    }

    public void suspendReads() {
        try {
            sourceHandle.suspend();
        } catch (CancelledKeyException ex) {
            // ignore
        }
    }

    public void suspendWrites() {
        try {
            sinkHandle.suspend();
        } catch (CancelledKeyException ex) {
            // ignore
        }
    }

    public void resumeReads() {
        try {
            sourceHandle.resume(SelectionKey.OP_READ);
        } catch (CancelledKeyException ex) {
            // ignore
        }
    }

    public void resumeWrites() {
        try {
            sinkHandle.resume(SelectionKey.OP_WRITE);
        } catch (CancelledKeyException ex) {
            // ignore
        }
    }

    public void shutdownReads() throws IOException {
        boolean ok = false;
        try {
            sourceChannel.close();
            ok = true;
        } finally {
            if (setBits(this, 0x02) == 0x03) {
                if (ok) close(); else IoUtils.safeClose(this);
            }
        }
    }

    public boolean shutdownWrites() throws IOException {
        boolean ok = false;
        try {
            sinkChannel.close();
            ok = true;
        } finally {
            if (setBits(this, 0x01) == 0x03) {
                if (ok) close(); else IoUtils.safeClose(this);
            }
        }
        return true;
    }

    public void awaitReadable() throws IOException {
        SelectorUtils.await(nioXnio, sourceChannel, SelectionKey.OP_READ);
    }

    public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
        SelectorUtils.await(nioXnio, sourceChannel, SelectionKey.OP_READ, time, timeUnit);
    }

    public void awaitWritable() throws IOException {
        SelectorUtils.await(nioXnio, sinkChannel, SelectionKey.OP_WRITE);
    }

    public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
        SelectorUtils.await(nioXnio, sinkChannel, SelectionKey.OP_WRITE, time, timeUnit);
    }

    public boolean supportsOption(final Option<?> option) {
        return false;
    }

    public <T> T getOption(final Option<T> option) throws UnsupportedOptionException, IOException {
        return null;
    }

    public <T> NioPipeChannel setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        return this;
    }

    private class ReadHandler implements Runnable {
        public void run() {
            IoUtils.<StreamChannel>invokeChannelListener(NioPipeChannel.this, readListener);
        }
    }

    private class WriteHandler implements Runnable {
        public void run() {
            IoUtils.<StreamChannel>invokeChannelListener(NioPipeChannel.this, writeListener);
        }
    }

    public String toString() {
        return String.format("pipe channel (NIO) <%s>", Integer.toString(hashCode(), 16));
    }
}
