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
import java.net.SocketException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.Executor;
import org.jboss.xnio.IoHandler;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.TcpChannelSource;
import org.jboss.xnio.channels.TcpChannel;
import org.jboss.xnio.FutureConnection;
import org.jboss.xnio.FinishedFutureConnection;
import org.jboss.xnio.AbstractFutureConnection;
import org.jboss.xnio.FailedFutureConnection;
import org.jboss.xnio.CloseableTcpConnector;
import org.jboss.xnio.log.Logger;

/**
 *
 */
public final class NioTcpConnector implements CloseableTcpConnector {

    private static final Logger log = Logger.getLogger("org.jboss.xnio.nio.tcp.connector");

    private final NioXnio nioXnio;
    private final Executor executor;

    private final Object lock = new Object();

    private boolean closed;

    private final Boolean keepAlive;
    private final Boolean oobInline;
    private final Integer receiveBufferSize;
    private final Boolean reuseAddress;
    private final Integer sendBufferSize;
    private final Boolean tcpNoDelay;

    private NioTcpConnector(NioTcpConnectorConfig config) {
        nioXnio = config.getXnio();
        executor = config.getExecutor();
        if (nioXnio == null) {
            throw new NullPointerException("nioXnio is null");
        }
        if (executor == null) {
            throw new NullPointerException("executor is null");
        }
        keepAlive = config.getKeepAlive();
        oobInline = config.getOobInline();
        receiveBufferSize = config.getReceiveBuffer();
        reuseAddress = config.getReuseAddresses();
        sendBufferSize = config.getSendBuffer();
        tcpNoDelay = config.getNoDelay();
    }

    private void configureStream(final Socket socket) throws SocketException {
        if (keepAlive != null) socket.setKeepAlive(keepAlive.booleanValue());
        if (oobInline != null) socket.setOOBInline(oobInline.booleanValue());
        if (receiveBufferSize != null) socket.setReceiveBufferSize(receiveBufferSize.intValue());
        if (reuseAddress != null) socket.setReuseAddress(reuseAddress.booleanValue());
        if (sendBufferSize != null) socket.setSendBufferSize(sendBufferSize.intValue());
        if (tcpNoDelay != null) socket.setTcpNoDelay(tcpNoDelay.booleanValue());
    }

    public FutureConnection<SocketAddress, TcpChannel> connectTo(final SocketAddress dest, final IoHandler<? super TcpChannel> handler) {
        if (dest == null) {
            throw new NullPointerException("dest is null");
        }
        if (handler == null) {
            throw new NullPointerException("handler is null");
        }
        return doConnectTo(null, dest, handler);
    }

    public FutureConnection<SocketAddress, TcpChannel> connectTo(final SocketAddress src, final SocketAddress dest, final IoHandler<? super TcpChannel> handler) {
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

    public TcpChannelSource createChannelSource(final SocketAddress dest) {
        if (dest == null) {
            throw new NullPointerException("dest is null");
        }
        return new TcpChannelSource() {
            public FutureConnection<SocketAddress, TcpChannel> open(final IoHandler<? super TcpChannel> handler) {
                if (handler == null) {
                    throw new NullPointerException("handler is null");
                }
                return doConnectTo(null, dest, handler);
            }
        };
    }

    public TcpChannelSource createChannelSource(final SocketAddress src, final SocketAddress dest) {
        if (src == null) {
            throw new NullPointerException("src is null");
        }
        if (dest == null) {
            throw new NullPointerException("dest is null");
        }
        return new TcpChannelSource() {
            public FutureConnection<SocketAddress, TcpChannel> open(final IoHandler<? super TcpChannel> handler) {
                if (handler == null) {
                    throw new NullPointerException("handler is null");
                }
                return doConnectTo(src, dest, handler);
            }
        };
    }

    private FutureConnection<SocketAddress, TcpChannel> doConnectTo(final SocketAddress src, final SocketAddress dest, final IoHandler<? super TcpChannel> handler) {
        try {
            synchronized (lock) {
                if (closed) {
                    throw new ClosedChannelException();
                }
                log.trace("Connecting from %s to %s", src == null ? "-any-" : src, dest);
                final SocketChannel socketChannel = SocketChannel.open();
                socketChannel.configureBlocking(false);
                final Socket socket = socketChannel.socket();
                if (src != null) socket.bind(src);
                configureStream(socket);
                if (socketChannel.connect(dest)) {
                    final NioTcpChannel channel = new NioTcpChannel(nioXnio, socketChannel, handler, executor);
                    nioXnio.addManaged(channel);
                    executor.execute(new Runnable() {
                        public void run() {
                            log.trace("Connection from %s to %s is up (immediate)", src == null ? "-any-" : src, dest);
                            if (! HandlerUtils.<TcpChannel>handleOpened(handler, channel)) {
                                IoUtils.safeClose(socketChannel);
                                nioXnio.removeManaged(channel);
                            }
                        }
                    });
                    return new FinishedFutureConnection<SocketAddress, TcpChannel>(channel);
                } else {
                    final ConnectionHandler connectionHandler = new ConnectionHandler(executor, socketChannel, nioXnio, handler);
                    connectionHandler.handle.resume(SelectionKey.OP_CONNECT);
                    return connectionHandler.future;
                }
            }
        } catch (IOException e) {
            return new FailedFutureConnection<SocketAddress, TcpChannel>(e, src);
        }
    }

    public void close() throws IOException {
        synchronized (lock) {
            log.trace("Closing %s", this);
            closed = true;
        }
    }

    public String toString() {
        return String.format("TCP connector (NIO) <%s>", Integer.toHexString(hashCode()));
    }

    static NioTcpConnector create(final NioTcpConnectorConfig config) {
        return new NioTcpConnector(config);
    }

    /**
     *
     */
    private final class ConnectionHandler implements Runnable {
        private final FutureImpl future;
        private final SocketChannel socketChannel;
        private final NioHandle handle;
        private final IoHandler<? super TcpChannel> handler;

        public ConnectionHandler(final Executor executor, final SocketChannel socketChannel, final NioXnio nioXnio, final IoHandler<? super TcpChannel> handler) throws IOException {
            this.socketChannel = socketChannel;
            this.handler = handler;
            // *should* be safe...
            //noinspection ThisEscapedInObjectConstruction
            handle = nioXnio.addConnectHandler(socketChannel, this, true);
            future = new FutureImpl(executor, socketChannel.socket().getLocalSocketAddress());
        }

        public void run() {
            try {
                if (socketChannel.finishConnect()) {
                    log.trace("Connection is up (deferred)");
                    final NioTcpChannel channel = new NioTcpChannel(nioXnio, socketChannel, handler, executor);
                    future.setResult(channel);
                    handler.handleOpened(channel);
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
                if (finishCancel()) {
                    IoUtils.safeClose(socketChannel);
                }
                return this;
            }
        }
    }
}