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

package org.jboss.xnio.nio;

import java.io.Closeable;
import java.io.IOException;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.channels.FileChannel;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import org.jboss.xnio.IoUtils;
import org.jboss.xnio.Option;
import org.jboss.xnio.ChannelListener;
import org.jboss.xnio.Options;
import org.jboss.xnio.channels.TcpChannel;
import org.jboss.xnio.channels.UnsupportedOptionException;
import org.jboss.xnio.channels.BoundChannel;
import org.jboss.xnio.channels.Configurable;
import org.jboss.xnio.log.Logger;
import org.jboss.xnio.management.TcpConnectionMBean;

import javax.management.StandardMBean;
import javax.management.NotCompliantMBeanException;

/**
 *
 */
final class NioTcpChannel implements TcpChannel, Closeable {

    private static final Logger log = Logger.getLogger("org.jboss.xnio.nio.tcp.channel");

    private final SocketChannel socketChannel;
    private final Socket socket;

    private volatile ChannelListener<? super TcpChannel> readListener = null;
    private volatile ChannelListener<? super TcpChannel> writeListener = null;
    private volatile ChannelListener<? super TcpChannel> closeListener = null;

    private static final AtomicReferenceFieldUpdater<NioTcpChannel, ChannelListener> readListenerUpdater = AtomicReferenceFieldUpdater.newUpdater(NioTcpChannel.class, ChannelListener.class, "readListener");
    private static final AtomicReferenceFieldUpdater<NioTcpChannel, ChannelListener> writeListenerUpdater = AtomicReferenceFieldUpdater.newUpdater(NioTcpChannel.class, ChannelListener.class, "writeListener");
    private static final AtomicReferenceFieldUpdater<NioTcpChannel, ChannelListener> closeListenerUpdater = AtomicReferenceFieldUpdater.newUpdater(NioTcpChannel.class, ChannelListener.class, "closeListener");

    private final ChannelListener.Setter<TcpChannel> readSetter = IoUtils.getSetter(this, readListenerUpdater);
    private final ChannelListener.Setter<TcpChannel> writeSetter = IoUtils.getSetter(this, writeListenerUpdater);
    private final ChannelListener.Setter<TcpChannel> closeSetter = IoUtils.getSetter(this, closeListenerUpdater);

    private final NioHandle readHandle;
    private final NioHandle writeHandle;
    private final NioXnio nioXnio;
    private volatile int closeBits = 0;
    private volatile long bytesRead = 0L;
    private volatile long bytesWritten = 0L;
    private volatile long msgsRead = 0L;
    private volatile long msgsWritten = 0L;

    private static final AtomicIntegerFieldUpdater<NioTcpChannel> closeBitsUpdater = AtomicIntegerFieldUpdater.newUpdater(NioTcpChannel.class, "closeBits");

    private static final AtomicLongFieldUpdater<NioTcpChannel> bytesReadUpdater = AtomicLongFieldUpdater.newUpdater(NioTcpChannel.class, "bytesRead");
    private static final AtomicLongFieldUpdater<NioTcpChannel> bytesWrittenUpdater = AtomicLongFieldUpdater.newUpdater(NioTcpChannel.class, "bytesWritten");
    private static final AtomicLongFieldUpdater<NioTcpChannel> msgsReadUpdater = AtomicLongFieldUpdater.newUpdater(NioTcpChannel.class, "msgsRead");
    private static final AtomicLongFieldUpdater<NioTcpChannel> msgsWrittenUpdater = AtomicLongFieldUpdater.newUpdater(NioTcpChannel.class, "msgsWritten");

    private final Closeable mbeanHandle;

    private static final Set<Option<?>> OPTIONS = Option.setBuilder()
            .add(Options.CLOSE_ABORT)
            .add(Options.KEEP_ALIVE)
            .add(Options.TCP_OOB_INLINE)
            .add(Options.RECEIVE_BUFFER)
            .add(Options.SEND_BUFFER)
            .add(Options.TCP_NODELAY)
            .add(Options.IP_TRAFFIC_CLASS)
            .create();

    public NioTcpChannel(final NioXnio nioXnio, final SocketChannel socketChannel, final Executor executor, final boolean manage, final InetSocketAddress bindAddress, final InetSocketAddress peerAddress) throws IOException {
        this.socketChannel = socketChannel;
        this.nioXnio = nioXnio;
        socket = socketChannel.socket();
        if (executor != null) {
            readHandle = nioXnio.addReadHandler(socketChannel, new ReadHandler(), executor);
            writeHandle = nioXnio.addWriteHandler(socketChannel, new WriteHandler(), executor);
        } else {
            readHandle = nioXnio.addReadHandler(socketChannel, new ReadHandler());
            writeHandle = nioXnio.addWriteHandler(socketChannel, new WriteHandler());
        }
        try {
            mbeanHandle = manage ? nioXnio.registerMBean(new MBean(bindAddress, peerAddress)) : IoUtils.nullCloseable();
        } catch (NotCompliantMBeanException e) {
            throw new IOException("Failed to register channel mbean: " + e);
        }
    }

    BoundChannel<InetSocketAddress> getBoundChannel() {
        return new BoundChannel<InetSocketAddress>() {
            public InetSocketAddress getLocalAddress() {
                return NioTcpChannel.this.getLocalAddress();
            }

            public ChannelListener.Setter<? extends BoundChannel<InetSocketAddress>> getCloseSetter() {
                return NioTcpChannel.this.getCloseSetter();
            }

            public boolean isOpen() {
                return NioTcpChannel.this.isOpen();
            }

            public void close() throws IOException {
                NioTcpChannel.this.close();
            }

            public boolean supportsOption(final Option<?> option) {
                return NioTcpChannel.this.supportsOption(option);
            }

            public <T> T getOption(final Option<T> option) throws IOException {
                return NioTcpChannel.this.getOption(option);
            }

            public <T> Configurable setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
                NioTcpChannel.this.setOption(option, value);
                return this;
            }
        };
    }

    public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        return target.transferFrom(socketChannel, position, count);
    }

    public ChannelListener.Setter<TcpChannel> getReadSetter() {
        return readSetter;
    }

    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        return src.transferTo(position, count, socketChannel);
    }

    public ChannelListener.Setter<TcpChannel> getWriteSetter() {
        return writeSetter;
    }

    public ChannelListener.Setter<TcpChannel> getCloseSetter() {
        return closeSetter;
    }

    public boolean flush() throws IOException {
        return true;
    }

    public long write(final ByteBuffer[] srcs, final int offset,
            final int length) throws IOException {
        long written = socketChannel.write(srcs, offset, length);
        if (written > 0) {
            bytesWrittenUpdater.addAndGet(this, written);
            msgsWrittenUpdater.incrementAndGet(this);
        }
        return written;
    }

    public long write(final ByteBuffer[] srcs) throws IOException {
        long written = socketChannel.write(srcs);
        if (written > 0) {
            bytesWrittenUpdater.addAndGet(this, written);
            msgsWrittenUpdater.incrementAndGet(this);
        }
        return written;
    }

    public int write(final ByteBuffer src) throws IOException {
        int written = socketChannel.write(src);
        if (written > 0) {
            bytesWrittenUpdater.addAndGet(this, written);
            msgsWrittenUpdater.incrementAndGet(this);
        }
        return written;
    }

    public boolean isOpen() {
        return socketChannel.isOpen();
    }

    private static int setBits(NioTcpChannel instance, int bits) {
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
        if (setBits(this, 0x04) < 0x04) {
            log.trace("Closing %s", this);
            nioXnio.removeManaged(this);
            socketChannel.close();
            IoUtils.<TcpChannel>invokeChannelListener(this, closeListener);
            IoUtils.safeClose(mbeanHandle);
        }
    }

    public long read(final ByteBuffer[] dsts, final int offset, final int length)
            throws IOException {
        long read = socketChannel.read(dsts, offset, length);
        if (read > 0) {
            bytesReadUpdater.addAndGet(this, read);
            msgsReadUpdater.incrementAndGet(this);
        }
        return read;
    }

    public long read(final ByteBuffer[] dsts) throws IOException {
        long read = socketChannel.read(dsts);
        if (read > 0) {
            bytesReadUpdater.addAndGet(this, read);
            msgsReadUpdater.incrementAndGet(this);
        }
        return read;
    }

    public int read(final ByteBuffer dst) throws IOException {
        int read = socketChannel.read(dst);
        if (read > 0) {
            bytesReadUpdater.addAndGet(this, read);
            msgsReadUpdater.incrementAndGet(this);
        }
        return read;
    }

    public void suspendReads() {
        try {
            readHandle.suspend();
        } catch (CancelledKeyException ex) {
            // ignore
        }
    }

    public void suspendWrites() {
        try {
            writeHandle.suspend();
        } catch (CancelledKeyException ex) {
            // ignore
        }
    }

    public void resumeReads() {
        try {
            readHandle.resume(SelectionKey.OP_READ);
        } catch (CancelledKeyException ex) {
            // ignore
        }
    }

    public void resumeWrites() {
        try {
            writeHandle.resume(SelectionKey.OP_WRITE);
        } catch (CancelledKeyException ex) {
            // ignore
        }
    }

    public void shutdownReads() throws IOException {
        boolean ok = false;
        try {
            socket.shutdownInput();
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
            socket.shutdownOutput();
            ok = true;
        } finally {
            if (setBits(this, 0x01) == 0x03) {
                if (ok) close(); else IoUtils.safeClose(this);
            }
        }
        return true;
    }

    public void awaitReadable() throws IOException {
        SelectorUtils.await(nioXnio, socketChannel, SelectionKey.OP_READ);
    }

    public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
        SelectorUtils.await(nioXnio, socketChannel, SelectionKey.OP_READ, time, timeUnit);
    }

    public void awaitWritable() throws IOException {
        SelectorUtils.await(nioXnio, socketChannel, SelectionKey.OP_WRITE);
    }

    public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
        SelectorUtils.await(nioXnio, socketChannel, SelectionKey.OP_WRITE, time, timeUnit);
    }

    public InetSocketAddress getPeerAddress() {
        return (InetSocketAddress) socket.getRemoteSocketAddress();
    }

    public InetSocketAddress getLocalAddress() {
        return (InetSocketAddress) socket.getLocalSocketAddress();
    }

    public boolean supportsOption(final Option<?> option) {
        return OPTIONS.contains(option);
    }

    public <T> T getOption(final Option<T> option) throws UnsupportedOptionException, IOException {
        if (option == Options.CLOSE_ABORT) {
            return option.cast(Boolean.valueOf(socket.getSoLinger() != -1));
        } else if (option == Options.KEEP_ALIVE) {
            return option.cast(Boolean.valueOf(socket.getKeepAlive()));
        } else if (option == Options.TCP_OOB_INLINE) {
            return option.cast(Boolean.valueOf(socket.getOOBInline()));
        } else if (option == Options.RECEIVE_BUFFER) {
            return option.cast(Integer.valueOf(socket.getReceiveBufferSize()));
        } else if (option == Options.SEND_BUFFER) {
            return option.cast(Integer.valueOf(socket.getSendBufferSize()));
        } else if (option == Options.TCP_NODELAY) {
            return option.cast(Boolean.valueOf(socket.getTcpNoDelay()));
        } else if (option == Options.IP_TRAFFIC_CLASS) {
            return option.cast(Integer.valueOf(socket.getTrafficClass()));
        } else {
            return null;
        }
    }

    public <T> NioTcpChannel setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        if (option == Options.CLOSE_ABORT) {
            socket.setSoLinger(((Boolean) value).booleanValue(), 0);
        } else if (option == Options.KEEP_ALIVE) {
            socket.setKeepAlive(((Boolean) value).booleanValue());
        } else if (option == Options.TCP_OOB_INLINE) {
            socket.setOOBInline(((Boolean) value).booleanValue());
        } else if (option == Options.RECEIVE_BUFFER) {
            socket.setReceiveBufferSize(((Integer) value).intValue());
        } else if (option == Options.SEND_BUFFER) {
            socket.setSendBufferSize(((Integer) value).intValue());
        } else if (option == Options.TCP_NODELAY) {
            socket.setTcpNoDelay(((Boolean) value).booleanValue());
        } else if (option == Options.IP_TRAFFIC_CLASS) {
            socket.setTrafficClass(((Integer) value).intValue());
        }
        return this;
    }

    private final class ReadHandler implements Runnable {
        public void run() {
            IoUtils.<TcpChannel>invokeChannelListener(NioTcpChannel.this, readListener);
        }
    }

    private final class WriteHandler implements Runnable {
        public void run() {
            IoUtils.<TcpChannel>invokeChannelListener(NioTcpChannel.this, writeListener);
        }
    }

    @Override
    public String toString() {
        return String.format("TCP socket channel (NIO) <%s> (local: %s, remote: %s)", Integer.toString(hashCode(), 16), getLocalAddress(), getPeerAddress());
    }

    public final class MBean extends StandardMBean implements TcpConnectionMBean {
        private final InetSocketAddress bindAddress;
        private final InetSocketAddress peerAddress;

        public MBean(final InetSocketAddress bindAddress, final InetSocketAddress peerAddress) throws NotCompliantMBeanException {
            super(TcpConnectionMBean.class);
            this.bindAddress = bindAddress;
            this.peerAddress = peerAddress;
        }

        public long getBytesRead() {
            return bytesRead;
        }

        public long getBytesWritten() {
            return bytesWritten;
        }

        public long getMessagesRead() {
            return msgsRead;
        }

        public long getMessagesWritten() {
            return msgsWritten;
        }

        public String toString() {
            return "ChannelMBean";
        }

        public InetSocketAddress getPeerAddress() {
            return peerAddress;
        }

        public InetSocketAddress getBindAddress() {
            return bindAddress;
        }

        public void close() {
            IoUtils.safeClose(NioTcpChannel.this);
        }
    }
}