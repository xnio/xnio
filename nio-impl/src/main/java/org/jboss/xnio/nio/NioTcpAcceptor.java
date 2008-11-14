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

import org.jboss.xnio.channels.Configurable;
import org.jboss.xnio.channels.ChannelOption;
import org.jboss.xnio.channels.UnsupportedOptionException;
import org.jboss.xnio.channels.TcpChannel;
import org.jboss.xnio.TcpAcceptor;
import org.jboss.xnio.TcpChannelDestination;
import org.jboss.xnio.FutureConnection;
import org.jboss.xnio.IoHandler;
import org.jboss.xnio.FailedFutureConnection;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.FinishedFutureConnection;
import org.jboss.xnio.AbstractFutureConnection;
import org.jboss.xnio.log.Logger;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.Executor;
import java.net.SocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;

/**
 *
 */
public final class NioTcpAcceptor implements Configurable, Lifecycle, TcpAcceptor {
    private static final Logger log = Logger.getLogger("org.jboss.xnio.nio.tcp.acceptor");

    private NioProvider nioProvider;
    private Executor executor;
    private int receiveBufferSize = -1;
    private boolean reuseAddress = false;
    private boolean keepAlive = false;
    private boolean oobInline = false;
    private boolean tcpNoDelay = false;

    public int getReceiveBufferSize() {
        return receiveBufferSize;
    }

    public void setReceiveBufferSize(final int receiveBufferSize) {
        this.receiveBufferSize = receiveBufferSize;
    }

    public boolean isReuseAddress() {
        return reuseAddress;
    }

    public void setReuseAddress(final boolean reuseAddress) {
        this.reuseAddress = reuseAddress;
    }

    public boolean isKeepAlive() {
        return keepAlive;
    }

    public void setKeepAlive(final boolean keepAlive) {
        this.keepAlive = keepAlive;
    }

    public boolean isOobInline() {
        return oobInline;
    }

    public void setOobInline(final boolean oobInline) {
        this.oobInline = oobInline;
    }

    public boolean isTcpNoDelay() {
        return tcpNoDelay;
    }

    public void setTcpNoDelay(final boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
    }

    public void setNioProvider(final NioProvider nioProvider) {
        this.nioProvider = nioProvider;
    }

    void setExecutor(final Executor executor) {
        this.executor = executor;
    }

    public <T> T getOption(final ChannelOption<T> option) throws UnsupportedOptionException, IOException {
        return null;
    }

    public Set<ChannelOption<?>> getOptions() {
        return null;
    }

    public <T> NioTcpAcceptor setOption(final ChannelOption<T> option, final T value) throws IllegalArgumentException, IOException {
        return null;
    }

    public void start() throws IOException {
        if (nioProvider == null) {
            throw new NullPointerException("nioProvider is null");
        }
        if (executor == null) {
            executor = nioProvider.getExecutor();
        }
    }

    public void stop() throws IOException {
        executor = null;
    }

    public FutureConnection<SocketAddress, TcpChannel> acceptTo(final SocketAddress dest, final IoHandler<? super TcpChannel> handler) {
        try {
            final ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(false);
            final ServerSocket serverSocket = serverSocketChannel.socket();
            if (receiveBufferSize != -1) {
                serverSocket.setReceiveBufferSize(receiveBufferSize);
            }
            serverSocket.setReuseAddress(reuseAddress);
            serverSocket.bind(dest, 1);
            final SocketChannel socketChannel = serverSocketChannel.accept();
            // unlikely, but...
            if (socketChannel != null) {
                return new FinishedFutureConnection<SocketAddress, TcpChannel>(new NioSocketChannelImpl(nioProvider, socketChannel, handler));
            }
            final Handler nioHandler = new Handler(serverSocketChannel, handler);
            final NioHandle handle = nioProvider.addConnectHandler(serverSocketChannel, nioHandler, true);
            nioHandler.handle = handle;
            handle.resume(SelectionKey.OP_ACCEPT);
            return nioHandler.future;
        } catch (IOException e) {
            return new FailedFutureConnection<SocketAddress, TcpChannel>(e, dest);
        }
    }

    public TcpChannelDestination createChannelDestination(final SocketAddress dest) {
        return new TcpChannelDestination() {
            public FutureConnection<SocketAddress, TcpChannel> accept(final IoHandler<? super TcpChannel> handler) {
                return acceptTo(dest, handler);
            }
        };
    }

    private final class Handler implements Runnable {
        private final FutureImpl future;
        private final ServerSocketChannel serverSocketChannel;
        private final IoHandler<? super TcpChannel> handler;
        private volatile NioHandle handle;

        public Handler(final ServerSocketChannel serverSocketChannel, final IoHandler<? super TcpChannel> handler) {
            this.serverSocketChannel = serverSocketChannel;
            this.handler = handler;
            future = new FutureImpl(executor, serverSocketChannel.socket().getLocalSocketAddress());
        }

        public void run() {
            try {
                boolean ok = false;
                final SocketChannel socketChannel = serverSocketChannel.accept();
                if (socketChannel == null) {
                    handle.resume(SelectionKey.OP_ACCEPT);
                    return;
                }
                try {
                    IoUtils.safeClose(serverSocketChannel);
                    socketChannel.configureBlocking(false);
                    final Socket socket = socketChannel.socket();
                    socket.setKeepAlive(keepAlive);
                    socket.setOOBInline(oobInline);
                    socket.setTcpNoDelay(tcpNoDelay);
                    final NioSocketChannelImpl channel = new NioSocketChannelImpl(nioProvider, socketChannel, handler);
                    ok = HandlerUtils.<TcpChannel>handleOpened(handler, channel);
                    if (ok) {
                        nioProvider.addChannel(channel);
                        log.trace("TCP server accepted connection");
                    }
                    future.setResult(channel);
                } finally {
                    if (! ok) {
                        log.trace("TCP server failed to accept connection");
                        // do NOT call close handler, since open handler was either not called or it failed
                        IoUtils.safeClose(serverSocketChannel);
                        IoUtils.safeClose(socketChannel);
                    }
                }
            } catch (ClosedChannelException e) {
                IoUtils.safeClose(serverSocketChannel);
                log.trace("Channel closed: %s", e.getMessage());
                future.setException(e);
            } catch (IOException e) {
                IoUtils.safeClose(serverSocketChannel);
                log.trace(e, "I/O error on TCP server");
                future.setException(e);
            }
        }

        private final class FutureImpl extends AbstractFutureConnection<SocketAddress, TcpChannel> {
            private final Executor executor;
            private final SocketAddress localAddress;

            public FutureImpl(final Executor executor, final SocketAddress address) {
                this.executor = executor;
                localAddress = address;
            }

            protected boolean setException(final IOException exception) {
                return super.setException(exception);
            }

            protected boolean setResult(final TcpChannel result) {
                return super.setResult(result);
            }

            protected boolean finishCancel() {
                return super.finishCancel();
            }

            protected void runNotifier(final Notifier<TcpChannel> streamChannelNotifier) {
                executor.execute(new Runnable() {
                    public void run() {
                        try {
                            streamChannelNotifier.notify(FutureImpl.this);
                        } catch (Throwable t) {
                            log.error(t, "Completion handler \"%s\" failed", streamChannelNotifier);
                        }
                    }
                });
            }

            public SocketAddress getLocalAddress() {
                return localAddress;
            }

            public FutureConnection<SocketAddress, TcpChannel> cancel() {
                IoUtils.safeClose(serverSocketChannel);
                finishCancel();
                return this;
            }
        }
    }

    public String toString() {
        return String.format("TCP acceptor (NIO) <%s>", Integer.toString(hashCode(), 16));
    }
}
