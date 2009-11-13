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
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.TcpAcceptor;
import org.jboss.xnio.TcpChannelDestination;
import org.jboss.xnio.OptionMap;
import org.jboss.xnio.ChannelListener;
import org.jboss.xnio.Options;
import org.jboss.xnio.IoFuture;
import org.jboss.xnio.FailedIoFuture;
import org.jboss.xnio.Option;
import org.jboss.xnio.FutureResult;
import org.jboss.xnio.channels.TcpChannel;
import org.jboss.xnio.channels.BoundChannel;
import org.jboss.xnio.channels.Configurable;
import org.jboss.xnio.log.Logger;

/**
 *
 */
final class NioTcpAcceptor implements TcpAcceptor {
    private static final Logger log = Logger.getLogger("org.jboss.xnio.nio.tcp.acceptor");

    private final NioXnio nioXnio;
    private final Executor executor;
    private final OptionMap optionMap;

    private NioTcpAcceptor(NioXnio nioXnio, Executor executor, OptionMap optionMap) {
        if (nioXnio == null) {
            throw new NullPointerException("nioXnio is null");
        }
        if (executor == null) {
            throw new NullPointerException("executor is null");
        }
        this.nioXnio = nioXnio;
        this.executor = executor;
        this.optionMap = optionMap;
    }

    static NioTcpAcceptor create(NioXnio nioXnio, Executor executor, OptionMap optionMap) {
        return new NioTcpAcceptor(nioXnio, executor, optionMap);
    }

    public IoFuture<TcpChannel> acceptTo(final InetSocketAddress dest, final ChannelListener<? super TcpChannel> handler, final ChannelListener<? super BoundChannel<InetSocketAddress>> bindListener) {
        try {
            final ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(false);
            final ServerSocket serverSocket = serverSocketChannel.socket();
            final OptionMap optionMap = this.optionMap;
            if (optionMap.contains(Options.RECEIVE_BUFFER)) serverSocket.setReceiveBufferSize(optionMap.get(Options.RECEIVE_BUFFER, 0));
            serverSocket.setReuseAddress(optionMap.get(Options.REUSE_ADDRESSES, true));
            serverSocket.bind(dest, 1);
            final AtomicReference<ChannelListener<? super BoundChannel<InetSocketAddress>>> listenerReference;
            final BoundChannel<InetSocketAddress> boundChannel;
            if (bindListener != null) {
                listenerReference = new AtomicReference<ChannelListener<? super BoundChannel<InetSocketAddress>>>(null);
                boundChannel = new BoundChannel<InetSocketAddress>() {
                    public InetSocketAddress getLocalAddress() {
                        return (InetSocketAddress) serverSocket.getLocalSocketAddress();
                    }

                    public ChannelListener.Setter<? extends BoundChannel<InetSocketAddress>> getCloseSetter() {
                        return IoUtils.<BoundChannel<InetSocketAddress>>getSetter(listenerReference);
                    }

                    public boolean isOpen() {
                        return serverSocketChannel.isOpen();
                    }

                    public void close() throws IOException {
                        serverSocketChannel.close();
                    }

                    public boolean supportsOption(final Option<?> option) {
                        return false;
                    }

                    public <T> T getOption(final Option<T> option) throws IOException {
                        return null;
                    }

                    public <T> Configurable setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
                        return this;
                    }
                };
                IoUtils.<BoundChannel<InetSocketAddress>>invokeChannelListener(executor, boundChannel, bindListener);
            } else {
                listenerReference = null;
                boundChannel = null;
            }
            final Handler nioHandler = new Handler(serverSocketChannel, handler, listenerReference, boundChannel);
            final NioHandle handle = nioXnio.addConnectHandler(serverSocketChannel, nioHandler, true);
            nioHandler.handle = handle;
            handle.resume(SelectionKey.OP_ACCEPT);
            return nioHandler.futureResult.getIoFuture();
        } catch (IOException e) {
            return new FailedIoFuture<TcpChannel>(e);
        }
    }

    public TcpChannelDestination createChannelDestination(final InetSocketAddress dest) {
        return new TcpChannelDestination() {
            public IoFuture<TcpChannel> accept(final ChannelListener<? super TcpChannel> openListener, final ChannelListener<? super BoundChannel<InetSocketAddress>> bindListener) {
                return acceptTo(dest, openListener, bindListener);
            }
        };
    }

    private final class Handler implements Runnable {
        private final FutureResult<TcpChannel> futureResult;
        private final ServerSocketChannel serverSocketChannel;
        private final ChannelListener<? super TcpChannel> openListener;
        private final AtomicReference<ChannelListener<? super BoundChannel<InetSocketAddress>>> listenerReference;
        private final BoundChannel<InetSocketAddress> boundChannel;
        private volatile NioHandle handle;

        public Handler(final ServerSocketChannel serverSocketChannel, final ChannelListener<? super TcpChannel> openListener, final AtomicReference<ChannelListener<? super BoundChannel<InetSocketAddress>>> listenerReference, final BoundChannel<InetSocketAddress> boundChannel) {
            this.serverSocketChannel = serverSocketChannel;
            this.openListener = openListener;
            this.listenerReference = listenerReference;
            this.boundChannel = boundChannel;
            futureResult = new FutureResult<TcpChannel>(executor);
        }

        public void run() {
            final ServerSocketChannel serverSocketChannel = this.serverSocketChannel;
            try {
                boolean ok = false;
                final SocketChannel socketChannel = serverSocketChannel.accept();
                if (socketChannel == null) {
                    handle.resume(SelectionKey.OP_ACCEPT);
                    return;
                }
                try {
                    IoUtils.safeClose(serverSocketChannel);
                    if (listenerReference != null) {
                        final ChannelListener<? super BoundChannel<InetSocketAddress>> closeListener = listenerReference.get();
                        if (closeListener != null) {
                            IoUtils.invokeChannelListener(boundChannel, closeListener);
                        }
                    }
                    socketChannel.configureBlocking(false);
                    final Socket socket = socketChannel.socket();
                    final OptionMap optionMap = NioTcpAcceptor.this.optionMap;
                    if (optionMap.contains(Options.KEEP_ALIVE)) socket.setKeepAlive(optionMap.get(Options.KEEP_ALIVE).booleanValue());
                    if (optionMap.contains(Options.TCP_OOB_INLINE)) socket.setOOBInline(optionMap.get(Options.TCP_OOB_INLINE).booleanValue());
                    if (optionMap.contains(Options.TCP_NODELAY)) socket.setTcpNoDelay(optionMap.get(Options.TCP_NODELAY).booleanValue());
                    final NioXnio nioXnio = NioTcpAcceptor.this.nioXnio;
                    final NioTcpChannel channel = new NioTcpChannel(nioXnio, socketChannel, executor, optionMap.get(Options.MANAGE_CONNECTIONS, true), (InetSocketAddress) socket.getLocalSocketAddress(), (InetSocketAddress) socket.getRemoteSocketAddress());
                    ok = true;
                    nioXnio.addManaged(channel);
                    IoUtils.<TcpChannel>invokeChannelListener(channel, openListener);
                    futureResult.setResult(channel);
                } finally {
                    if (! ok) {
                        log.trace("TCP server failed to accept connection");
                        // do NOT call close handler, since open handler was either not called or it failed
                        IoUtils.safeClose(serverSocketChannel);
                        IoUtils.safeClose(socketChannel);
                    }
                }
            } catch (IOException e) {
                IoUtils.safeClose(serverSocketChannel);
                futureResult.setException(e);
            }
        }
    }

    public String toString() {
        return String.format("TCP acceptor (NIO) <%s>", Integer.toString(hashCode(), 16));
    }
}
