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

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SocketChannel;
import java.nio.channels.SelectionKey;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;
import org.jboss.xnio.IoHandler;
import org.jboss.xnio.log.Logger;
import org.jboss.xnio.channels.CommonOptions;
import org.jboss.xnio.channels.TcpChannel;
import org.jboss.xnio.channels.UnsupportedOptionException;
import org.jboss.xnio.channels.ChannelOption;
import org.jboss.xnio.channels.Configurable;

/**
 *
 */
public final class NioSocketChannelImpl implements TcpChannel {

    private static final Logger log = Logger.getLogger(NioSocketChannelImpl.class);

    private final SocketChannel socketChannel;
    private final Socket socket;
    private final IoHandler<? super TcpChannel> handler;
    private final NioHandle readHandle;
    private final NioHandle writeHandle;
    private final NioProvider nioProvider;
    private final AtomicBoolean closeCalled = new AtomicBoolean(false);
    private final AtomicBoolean shutdownReads = new AtomicBoolean(false);
    private final AtomicBoolean shutdownWrites = new AtomicBoolean(false);

    public NioSocketChannelImpl(final NioProvider nioProvider, final SocketChannel socketChannel, final IoHandler<? super TcpChannel> handler) throws IOException {
        this.handler = handler;
        this.socketChannel = socketChannel;
        this.nioProvider = nioProvider;
        socket = socketChannel.socket();
        readHandle = nioProvider.addReadHandler(socketChannel, new ReadHandler());
        writeHandle = nioProvider.addWriteHandler(socketChannel, new WriteHandler());
    }

    public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        return socketChannel.write(srcs, offset, length);
    }

    public long write(final ByteBuffer[] srcs) throws IOException {
        return socketChannel.write(srcs);
    }

    public int write(final ByteBuffer src) throws IOException {
        return socketChannel.write(src);
    }

    public boolean isOpen() {
        return socketChannel.isOpen();
    }

    public void close() throws IOException {
        try {
            socketChannel.close();
        } finally {
            nioProvider.removeChannel(this);
            readHandle.cancelKey();
            writeHandle.cancelKey();
            if (! closeCalled.getAndSet(true)) {
                log.trace("Closing channel %s", this);
                HandlerUtils.<TcpChannel>handleClosed(handler, this);
            }
        }
    }

    public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        return socketChannel.read(dsts, offset, length);
    }

    public long read(final ByteBuffer[] dsts) throws IOException {
        return socketChannel.read(dsts);
    }

    public int read(final ByteBuffer dst) throws IOException {
        return socketChannel.read(dst);
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
        socket.shutdownInput();
    }

    public void shutdownWrites() throws IOException {
        socket.shutdownOutput();
    }

    public void awaitReadable() throws IOException {
        SelectorUtils.await(SelectionKey.OP_READ, socketChannel);
    }

    public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
        SelectorUtils.await(SelectionKey.OP_READ, socketChannel, time, timeUnit);
    }

    public void awaitWritable() throws IOException {
        SelectorUtils.await(SelectionKey.OP_WRITE, socketChannel);
    }

    public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
        SelectorUtils.await(SelectionKey.OP_WRITE, socketChannel, time, timeUnit);
    }

    public SocketAddress getPeerAddress() {
        return socket.getRemoteSocketAddress();
    }

    public SocketAddress getLocalAddress() {
        return socket.getLocalSocketAddress();
    }

    private static final Set<ChannelOption<?>> OPTIONS = Collections.<ChannelOption<?>>singleton(CommonOptions.CLOSE_ABORT);

    @SuppressWarnings({"unchecked"})
    public <T> T getOption(final ChannelOption<T> option) throws UnsupportedOptionException, IOException {
        if (option == null) {
            throw new NullPointerException("name is null");
        }
        if (! OPTIONS.contains(option)) {
            throw new UnsupportedOptionException("Option not supported: " + option);
        }
        if (CommonOptions.CLOSE_ABORT.equals(option)) {
            return (T) Boolean.valueOf(socket.getSoLinger() != -1);
        } else {
            throw new UnsupportedOptionException("Option " + option + " not supported");
        }
    }

    public Set<ChannelOption<?>> getOptions() {
        return OPTIONS;
    }

    public <T> Configurable setOption(final ChannelOption<T> option, final T value) throws IllegalArgumentException, IOException {
        if (option == null) {
            throw new NullPointerException("name is null");
        }
        if (! OPTIONS.contains(option)) {
            throw new UnsupportedOptionException("Option not supported: " + option);
        }
        if (CommonOptions.CLOSE_ABORT.equals(option)) {
            if (value == null) {
                throw new NullPointerException("value is null");
            }
            socket.setSoLinger(((Boolean) value).booleanValue(), 0);
        }
        return this;
    }

    private final class ReadHandler implements Runnable {
        public void run() {
            HandlerUtils.<TcpChannel>handleReadable(handler, NioSocketChannelImpl.this);
        }
    }

    private final class WriteHandler implements Runnable {
        public void run() {
            HandlerUtils.<TcpChannel>handleWritable(handler, NioSocketChannelImpl.this);
        }
    }

    public String toString() {
        return String.format("TCP socket channel (NIO) <%s>", Integer.toString(hashCode(), 16));
    }
}