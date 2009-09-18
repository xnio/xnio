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
import java.net.SocketException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executor;
import org.jboss.xnio.AbstractFutureConnection;
import org.jboss.xnio.FailedFutureConnection;
import org.jboss.xnio.FinishedFutureConnection;
import org.jboss.xnio.FutureConnection;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.TcpChannelSource;
import org.jboss.xnio.TcpConnector;
import org.jboss.xnio.OptionMap;
import org.jboss.xnio.ChannelListener;
import org.jboss.xnio.channels.TcpChannel;
import org.jboss.xnio.channels.CommonOptions;
import org.jboss.xnio.log.Logger;

/**
 *
 */
final class NioTcpConnector implements TcpConnector {

    private static final Logger log = Logger.getLogger("org.jboss.xnio.nio.tcp.connector");

    private final NioXnio nioXnio;
    private final Executor executor;

    private final Boolean keepAlive;
    private final Boolean oobInline;
    private final Integer receiveBufferSize;
    private final Boolean reuseAddress;
    private final Integer sendBufferSize;
    private final Boolean tcpNoDelay;
    private final boolean manageConnections;

    private NioTcpConnector(NioXnio nioXnio, Executor executor, OptionMap optionMap) {
        if (nioXnio == null) {
            throw new NullPointerException("nioXnio is null");
        }
        if (executor == null) {
            throw new NullPointerException("executor is null");
        }
        this.nioXnio = nioXnio;
        this.executor = executor;
        reuseAddress = optionMap.get(CommonOptions.REUSE_ADDRESSES);
        receiveBufferSize = optionMap.get(CommonOptions.RECEIVE_BUFFER);
        sendBufferSize = optionMap.get(CommonOptions.SEND_BUFFER);
        keepAlive = optionMap.get(CommonOptions.KEEP_ALIVE);
        oobInline = optionMap.get(CommonOptions.TCP_OOB_INLINE);
        tcpNoDelay = optionMap.get(CommonOptions.TCP_NODELAY);
        manageConnections = ! optionMap.contains(CommonOptions.MANAGE_CONNECTIONS) || optionMap.get(CommonOptions.MANAGE_CONNECTIONS).booleanValue();
    }

    private void configureStream(final Socket socket) throws SocketException {
        if (keepAlive != null) socket.setKeepAlive(keepAlive.booleanValue());
        if (oobInline != null) socket.setOOBInline(oobInline.booleanValue());
        if (receiveBufferSize != null) socket.setReceiveBufferSize(receiveBufferSize.intValue());
        if (reuseAddress != null) socket.setReuseAddress(reuseAddress.booleanValue());
        if (sendBufferSize != null) socket.setSendBufferSize(sendBufferSize.intValue());
        if (tcpNoDelay != null) socket.setTcpNoDelay(tcpNoDelay.booleanValue());
    }

    public FutureConnection<InetSocketAddress, TcpChannel> connectTo(final InetSocketAddress dest, final ChannelListener<? super TcpChannel> handler) {
        if (dest == null) {
            throw new NullPointerException("dest is null");
        }
        if (handler == null) {
            throw new NullPointerException("handler is null");
        }
        return doConnectTo(null, dest, handler);
    }

    public FutureConnection<InetSocketAddress, TcpChannel> connectTo(final InetSocketAddress src, final InetSocketAddress dest, final ChannelListener<? super TcpChannel> handler) {
        if (src == null) {
            throw new NullPointerException("src is null");
        }
        if (dest == null) {
            throw new NullPointerException("dest is null");
        }
        if (handler == null) {
            throw new NullPointerException("handler is null");
        }
        return doConnectTo(src, dest, handler);
    }

    public TcpChannelSource createChannelSource(final InetSocketAddress dest) {
        if (dest == null) {
            throw new NullPointerException("dest is null");
        }
        return new TcpChannelSource() {
            public FutureConnection<InetSocketAddress, TcpChannel> open(final ChannelListener<? super TcpChannel> handler) {
                if (handler == null) {
                    throw new NullPointerException("handler is null");
                }
                return doConnectTo(null, dest, handler);
            }
        };
    }

    public TcpChannelSource createChannelSource(final InetSocketAddress src, final InetSocketAddress dest) {
        if (src == null) {
            throw new NullPointerException("src is null");
        }
        if (dest == null) {
            throw new NullPointerException("dest is null");
        }
        return new TcpChannelSource() {
            public FutureConnection<InetSocketAddress, TcpChannel> open(final ChannelListener<? super TcpChannel> handler) {
                if (handler == null) {
                    throw new NullPointerException("handler is null");
                }
                return doConnectTo(src, dest, handler);
            }
        };
    }

    private FutureConnection<InetSocketAddress, TcpChannel> doConnectTo(final InetSocketAddress src, final InetSocketAddress dest, final ChannelListener<? super TcpChannel> handler) {
        try {
            log.trace("Connecting from %s to %s", src == null ? "-any-" : src, dest);
            final SocketChannel socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(false);
            final Socket socket = socketChannel.socket();
            if (src != null) socket.bind(src);
            configureStream(socket);
            if (socketChannel.connect(dest)) {
                final NioTcpChannel channel = new NioTcpChannel(nioXnio, socketChannel, executor, manageConnections);
                nioXnio.addManaged(channel);
                executor.execute(new Runnable() {
                    public void run() {
                        log.trace("Connection from %s to %s is up (immediate)", src == null ? "-any-" : src, dest);
                        if (! IoUtils.<TcpChannel>invokeChannelListener(channel, handler)) {
                            IoUtils.safeClose(socketChannel);
                            nioXnio.removeManaged(channel);
                        }
                    }
                });
                return new FinishedFutureConnection<InetSocketAddress, TcpChannel>(channel);
            } else {
                final ConnectionHandler connectionHandler = new ConnectionHandler(executor, socketChannel, nioXnio, handler);
                connectionHandler.handle.resume(SelectionKey.OP_CONNECT);
                return connectionHandler.future;
            }
        } catch (IOException e) {
            return new FailedFutureConnection<InetSocketAddress, TcpChannel>(e, src);
        }
    }

    public String toString() {
        return String.format("TCP connector (NIO) <%s>", Integer.toHexString(hashCode()));
    }

    static TcpConnector create(final NioXnio nioXnio, final Executor executor, final OptionMap optionMap) {
        return new NioTcpConnector(nioXnio, executor, optionMap);
    }

    /**
     *
     */
    private final class ConnectionHandler implements Runnable {
        private final FutureImpl future;
        private final SocketChannel socketChannel;
        private final NioHandle handle;
        private final ChannelListener<? super TcpChannel> handler;

        public ConnectionHandler(final Executor executor, final SocketChannel socketChannel, final NioXnio nioXnio, final ChannelListener<? super TcpChannel> handler) throws IOException {
            this.socketChannel = socketChannel;
            this.handler = handler;
            handle = nioXnio.addConnectHandler(socketChannel, this, true);
            future = new FutureImpl(executor, (InetSocketAddress) socketChannel.socket().getLocalSocketAddress());
        }

        public void run() {
            try {
                if (socketChannel.finishConnect()) {
                    log.trace("Connection is up (deferred)");
                    final NioTcpChannel channel = new NioTcpChannel(nioXnio, socketChannel, executor, manageConnections);
                    future.setResult(channel);
                    if (! IoUtils.<TcpChannel>invokeChannelListener(channel, handler)) {
                        IoUtils.safeClose(socketChannel);
                        nioXnio.removeManaged(channel);
                    }
                    handle.cancelKey();
                } else {
                    log.trace("Connection is not yet up (deferred)");
                    handle.resume(SelectionKey.OP_CONNECT);
                    return;
                }
            } catch (IOException e) {
                future.setException(e);
                handle.cancelKey();
            } catch (Exception e) {
                final String message = e.getMessage();
                final IOException ioexception = new IOException("Connection failed unexpectedly: " + message);
                ioexception.setStackTrace(e.getStackTrace());
                future.setException(ioexception);
                handle.cancelKey();
            }
        }

        private final class FutureImpl extends AbstractFutureConnection<InetSocketAddress, TcpChannel> {
            private final Executor executor;
            private final InetSocketAddress localAddress;

            public FutureImpl(final Executor executor, final InetSocketAddress address) {
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

            protected Executor getNotifierExecutor() {
                return executor;
            }

            public InetSocketAddress getLocalAddress() {
                return localAddress;
            }

            public FutureConnection<InetSocketAddress, TcpChannel> cancel() {
                if (finishCancel()) {
                    IoUtils.safeClose(socketChannel);
                }
                return this;
            }
        }
    }
}