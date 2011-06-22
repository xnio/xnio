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
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import org.jboss.logging.Logger;
import org.xnio.ConnectionChannelThread;
import org.xnio.Option;
import org.xnio.ChannelListener;
import org.xnio.Options;
import org.xnio.ReadChannelThread;
import org.xnio.WriteChannelThread;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.UnsupportedOptionException;

final class NioTcpServer implements AcceptingChannel<NioTcpChannel> {
    private static final Logger log = Logger.getLogger("org.xnio.nio.tcp.server");

    private final NioSetter<NioTcpServer> acceptSetter = new NioSetter<NioTcpServer>();
    private final NioSetter<NioTcpServer> closeSetter = new NioSetter<NioTcpServer>();

    @SuppressWarnings( { "unused" })
    private volatile NioHandle<NioTcpServer> acceptHandle;

    @SuppressWarnings( { "unchecked" })
    private static final AtomicReferenceFieldUpdater<NioTcpServer, NioHandle<NioTcpServer>> acceptHandleUpdater = unsafeUpdater(NioHandle.class, "acceptHandle");

    @SuppressWarnings( { "unchecked" })
    private static <T> AtomicReferenceFieldUpdater<NioTcpServer, T> unsafeUpdater(Class<?> clazz, String name) {
        return (AtomicReferenceFieldUpdater) AtomicReferenceFieldUpdater.newUpdater(NioTcpServer.class, clazz, name);
    }
    private final NioXnio xnio;

    private final ServerSocketChannel channel;
    private final ServerSocket socket;

    private static final Set<Option<?>> options = Option.setBuilder()
            .add(Options.REUSE_ADDRESSES)
            .add(Options.RECEIVE_BUFFER)
            .add(Options.SEND_BUFFER)
            .add(Options.KEEP_ALIVE)
            .add(Options.TCP_OOB_INLINE)
            .add(Options.TCP_NODELAY)
            .create();

    @SuppressWarnings( { "unused" })
    private volatile int keepAlive;
    @SuppressWarnings( { "unused" })
    private volatile int oobInline;
    @SuppressWarnings( { "unused" })
    private volatile int tcpNoDelay;
    @SuppressWarnings( { "unused" })
    private volatile int sendBuffer = -1;

    private static final AtomicIntegerFieldUpdater<NioTcpServer> keepAliveUpdater = AtomicIntegerFieldUpdater.newUpdater(NioTcpServer.class, "keepAlive");
    private static final AtomicIntegerFieldUpdater<NioTcpServer> oobInlineUpdater = AtomicIntegerFieldUpdater.newUpdater(NioTcpServer.class, "oobInline");
    private static final AtomicIntegerFieldUpdater<NioTcpServer> tcpNoDelayUpdater = AtomicIntegerFieldUpdater.newUpdater(NioTcpServer.class, "tcpNoDelay");
    private static final AtomicIntegerFieldUpdater<NioTcpServer> sendBufferUpdater = AtomicIntegerFieldUpdater.newUpdater(NioTcpServer.class, "sendBuffer");

    NioTcpServer(final NioXnio xnio, final ServerSocketChannel channel) {
        this.xnio = xnio;
        this.channel = channel;
        socket = channel.socket();
    }

    public void close() throws IOException {
        channel.close();
        final NioHandle<NioTcpServer> handle = acceptHandle;
        if (handle != null) {
            handle.cancelKey();
        }
    }

    public boolean supportsOption(final Option<?> option) {
        return options.contains(option);
    }

    public <T> T getOption(final Option<T> option) throws UnsupportedOptionException, IOException {
        if (option == Options.REUSE_ADDRESSES) {
            return option.cast(Boolean.valueOf(socket.getReuseAddress()));
        } else if (option == Options.RECEIVE_BUFFER) {
            return option.cast(Integer.valueOf(socket.getReceiveBufferSize()));
        } else if (option == Options.SEND_BUFFER) {
            final int value = sendBuffer;
            return value == -1 ? null : option.cast(Integer.valueOf(value));
        } else if (option == Options.KEEP_ALIVE) {
            return option.cast(Boolean.valueOf(keepAlive != 0));
        } else if (option == Options.TCP_OOB_INLINE) {
            return option.cast(Boolean.valueOf(oobInline != 0));
        } else if (option == Options.TCP_NODELAY) {
            return option.cast(Boolean.valueOf(tcpNoDelay != 0));
        } else {
            return null;
        }
    }

    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        final Object old;
        if (option == Options.REUSE_ADDRESSES) {
            old = Boolean.valueOf(socket.getReuseAddress());
            socket.setReuseAddress(Options.REUSE_ADDRESSES.cast(value).booleanValue());
        } else if (option == Options.RECEIVE_BUFFER) {
            old = Integer.valueOf(socket.getReceiveBufferSize());
            socket.setReceiveBufferSize(Options.RECEIVE_BUFFER.cast(value).intValue());
        } else if (option == Options.SEND_BUFFER) {
            final int newValue = value == null ? -1 : Options.SEND_BUFFER.cast(value).intValue();
            if (value != null && newValue < 1) {
                throw new IllegalArgumentException("Bad send buffer size specified");
            }
            final int oldValue = sendBufferUpdater.getAndSet(this, newValue);
            old = oldValue == -1 ? null : Integer.valueOf(oldValue);
        } else if (option == Options.KEEP_ALIVE) {
            old = Boolean.valueOf(keepAliveUpdater.getAndSet(this, Options.KEEP_ALIVE.cast(value).booleanValue() ? 1 : 0) != 0);
        } else if (option == Options.TCP_OOB_INLINE) {
            old = Boolean.valueOf(oobInlineUpdater.getAndSet(this, Options.TCP_OOB_INLINE.cast(value).booleanValue() ? 1 : 0) != 0);
        } else if (option == Options.TCP_NODELAY) {
            old = Boolean.valueOf(tcpNoDelayUpdater.getAndSet(this, Options.TCP_NODELAY.cast(value).booleanValue() ? 1 : 0) != 0);
        } else {
            return null;
        }
        return option.cast(old);
    }

    public NioTcpChannel accept(final ReadChannelThread readThread, final WriteChannelThread writeThread) throws IOException {
        final SocketChannel accepted = channel.accept();
        if (accepted == null) {
            return null;
        }
        accepted.configureBlocking(false);
        final Socket socket = accepted.socket();
        socket.setKeepAlive(keepAlive != 0);
        socket.setOOBInline(oobInline != 0);
        socket.setTcpNoDelay(tcpNoDelay != 0);
        final int sendBuffer = this.sendBuffer;
        if (sendBuffer > 0) socket.setSendBufferSize(sendBuffer);
        final NioTcpChannel newChannel = new NioTcpChannel(xnio, accepted);
        newChannel.setReadThread(readThread);
        newChannel.setWriteThread(writeThread);
        log.trace("TCP server accepted connection");
        return newChannel;
    }

    public String toString() {
        return String.format("TCP server (NIO) <%s>", Integer.toHexString(hashCode()));
    }

    public ChannelListener.Setter<NioTcpServer> getAcceptSetter() {
        return acceptSetter;
    }

    public ChannelListener.Setter<NioTcpServer> getCloseSetter() {
        return closeSetter;
    }

    public boolean isOpen() {
        return channel.isOpen();
    }

    public SocketAddress getLocalAddress() {
        return socket.getLocalSocketAddress();
    }

    public <A extends SocketAddress> A getLocalAddress(final Class<A> type) {
        final SocketAddress address = getLocalAddress();
        return type.isInstance(address) ? type.cast(address) : null;
    }

    public void suspendAccepts() {
        final NioHandle<NioTcpServer> writeHandle = acceptHandle;
        if (writeHandle != null) writeHandle.resume(0);
    }

    public void resumeAccepts() {
        final NioHandle<NioTcpServer> writeHandle = acceptHandle;
        if (writeHandle != null) writeHandle.resume(SelectionKey.OP_ACCEPT);
    }

    public void awaitAcceptable() throws IOException {
        SelectorUtils.await(xnio, channel, SelectionKey.OP_ACCEPT);
    }

    public void awaitAcceptable(final long time, final TimeUnit timeUnit) throws IOException {
        SelectorUtils.await(xnio, channel, SelectionKey.OP_ACCEPT, time, timeUnit);
    }

    public void setAcceptThread(final ConnectionChannelThread thread) throws IllegalArgumentException {
        try {
            final NioHandle<NioTcpServer> newHandle = thread == null ? null : ((AbstractNioChannelThread) thread).addChannel(channel, this, SelectionKey.OP_ACCEPT, acceptSetter);
            final NioHandle<NioTcpServer> oldValue = acceptHandleUpdater.getAndSet(this, newHandle);
            if (oldValue != null && (newHandle == null || oldValue.getSelectionKey() != newHandle.getSelectionKey())) {
                oldValue.cancelKey();
            }
        } catch (ClosedChannelException e) {
            // do nothing
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("Thread belongs to the wrong provider");
        }
    }

    public ConnectionChannelThread getAcceptThread() {
        final NioHandle<NioTcpServer> handle = acceptHandleUpdater.get(this);
        return handle == null ? null : (ConnectionChannelThread) handle.getChannelThread();
    }
}
