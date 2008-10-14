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
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import org.jboss.xnio.IoHandler;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.TcpChannelSource;
import org.jboss.xnio.TcpConnector;
import org.jboss.xnio.channels.ChannelOption;
import org.jboss.xnio.channels.CommonOptions;
import org.jboss.xnio.channels.TcpChannel;
import org.jboss.xnio.channels.UnsupportedOptionException;
import org.jboss.xnio.channels.Configurable;
import org.jboss.xnio.FutureConnection;
import org.jboss.xnio.FinishedFutureConnection;
import org.jboss.xnio.AbstractFutureConnection;
import org.jboss.xnio.FailedFutureConnection;
import org.jboss.xnio.log.Logger;

/**
 *
 */
public final class NioTcpConnector implements Configurable, Lifecycle, TcpConnector {

    private static final Logger log = Logger.getLogger(NioTcpConnector.class);

    private NioProvider nioProvider;
    private Executor executor;
    private boolean keepAlive = false;
    private boolean oobInline = false;
    private int receiveBufferSize = -1;
    private boolean reuseAddress = false;
    private int sendBufferSize = -1;
    private boolean tcpNoDelay = false;
    private int connectTimeout = -1;

    // accessors - configuration

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

    public int getSendBufferSize() {
        return sendBufferSize;
    }

    public void setSendBufferSize(final int sendBufferSize) {
        this.sendBufferSize = sendBufferSize;
    }

    public boolean isTcpNoDelay() {
        return tcpNoDelay;
    }

    public void setTcpNoDelay(final boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(final int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    // accessors - dependencies

    public NioProvider getNioProvider() {
        return nioProvider;
    }

    public void setNioProvider(final NioProvider nioProvider) {
        this.nioProvider = nioProvider;
    }

    public Executor getExecutor() {
        return executor;
    }

    public void setExecutor(final Executor executor) {
        this.executor = executor;
    }

    // lifecycle

    public void start() {
        if (nioProvider == null) {
            throw new NullPointerException("nioProvider is null");
        }
        if (executor == null) {
            executor = nioProvider.getExecutor();
        }
    }

    public void stop() {
        executor = null;
    }

    private void configureStream(final Socket socket) throws SocketException {
        socket.setKeepAlive(keepAlive);
        socket.setOOBInline(oobInline);
        if (receiveBufferSize > 0) {
            socket.setReceiveBufferSize(receiveBufferSize);
        }
        socket.setReuseAddress(reuseAddress);
        if (sendBufferSize > 0) {
            socket.setSendBufferSize(sendBufferSize);
        }
        socket.setTcpNoDelay(tcpNoDelay);
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
            log.trace("Connecting from %s to %s", src, dest);
            final SocketChannel socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(false);
            final Socket socket = socketChannel.socket();
            if (src != null) socket.bind(src);
            configureStream(socket);
            if (socketChannel.connect(dest)) {
                final NioSocketChannelImpl channel = new NioSocketChannelImpl(nioProvider, socketChannel, handler);
                executor.execute(new Runnable() {
                    public void run() {
                        log.trace("Connection from %s to %s is up (immediate)", src, dest);
                        if (! HandlerUtils.<TcpChannel>handleOpened(handler, channel)) {
                            IoUtils.safeClose(socketChannel);
                        }
                    }
                });
                nioProvider.addChannel(channel);
                return new FinishedFutureConnection<SocketAddress, TcpChannel>(channel);
            } else {
                final ConnectionHandler connectionHandler = new ConnectionHandler(executor, socketChannel, nioProvider, handler);
                connectionHandler.handle.getSelectionKey().interestOps(SelectionKey.OP_CONNECT).selector().wakeup();
                return connectionHandler.future;
            }
        } catch (IOException e) {
            return new FailedFutureConnection<SocketAddress, TcpChannel>(e, src);
        }
    }

    private static final Set<ChannelOption<?>> OPTIONS;

    static {
        final Set<ChannelOption<?>> options = new HashSet<ChannelOption<?>>();
        options.add(CommonOptions.KEEP_ALIVE);
        options.add(CommonOptions.TCP_OOB_INLINE);
        options.add(CommonOptions.RECEIVE_BUFFER);
        options.add(CommonOptions.REUSE_ADDRESSES);
        options.add(CommonOptions.SEND_BUFFER);
        options.add(CommonOptions.TCP_NODELAY);
        OPTIONS = Collections.unmodifiableSet(options);
    }

    @SuppressWarnings({"unchecked"})
    public <T> T getOption(final ChannelOption<T> option) throws UnsupportedOptionException, IOException {
        if (option == null) {
            throw new NullPointerException("option is null");
        }
        if (! OPTIONS.contains(option)) {
            throw new UnsupportedOptionException("Option not supported: " + option);
        }
        if (CommonOptions.KEEP_ALIVE.equals(option)) {
            return (T) Boolean.valueOf(keepAlive);
        } else if (CommonOptions.TCP_OOB_INLINE.equals(option)) {
            return (T) Boolean.valueOf(oobInline);
        } else if (CommonOptions.RECEIVE_BUFFER.equals(option)) {
            final int v = receiveBufferSize;
            return v == -1 ? null : (T) Integer.valueOf(v);
        } else if (CommonOptions.REUSE_ADDRESSES.equals(option)) {
            return (T) Boolean.valueOf(reuseAddress);
        } else if (CommonOptions.SEND_BUFFER.equals(option)) {
            final int v = sendBufferSize;
            return v == -1 ? null : (T) Integer.valueOf(v);
        } else if (CommonOptions.TCP_NODELAY.equals(option)) {
            return (T) Boolean.valueOf(tcpNoDelay);
        } else {
            throw new IllegalStateException("Failed to get supported option: " + option);
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
        if (CommonOptions.KEEP_ALIVE.equals(option)) {
            setKeepAlive(((Boolean)value).booleanValue());
            return this;
        } else if (CommonOptions.TCP_OOB_INLINE.equals(option)) {
            setOobInline(((Boolean)value).booleanValue());
            return this;
        } else if (CommonOptions.RECEIVE_BUFFER.equals(option)) {
            setReceiveBufferSize(((Integer)value).intValue());
            return this;
        } else if (CommonOptions.REUSE_ADDRESSES.equals(option)) {
            setReuseAddress(((Boolean)value).booleanValue());
            return this;
        } else if (CommonOptions.SEND_BUFFER.equals(option)) {
            setSendBufferSize(((Integer)value).intValue());
            return this;
        } else if (CommonOptions.TCP_NODELAY.equals(option)) {
            setTcpNoDelay(((Boolean)value).booleanValue());
            return this;
        } else {
            throw new IllegalStateException("Failed to set supported option: " + option);
        }
    }

    /**
     *
     */
    private final class ConnectionHandler implements Runnable {
        private final FutureImpl future;
        private final SocketChannel socketChannel;
        private final NioHandle handle;
        private final IoHandler<? super TcpChannel> handler;

        public ConnectionHandler(final Executor executor, final SocketChannel socketChannel, final NioProvider nioProvider, final IoHandler<? super TcpChannel> handler) throws IOException {
            this.socketChannel = socketChannel;
            this.handler = handler;
            // *should* be safe...
            //noinspection ThisEscapedInObjectConstruction
            handle = nioProvider.addConnectHandler(socketChannel, this);
            future = new FutureImpl(executor, socketChannel.socket().getLocalSocketAddress());
        }

        public void run() {
            try {
                if (socketChannel.finishConnect()) {
                    log.trace("Connection is up (deferred)");
                    final NioSocketChannelImpl channel = new NioSocketChannelImpl(nioProvider, socketChannel, handler);
                    future.setResult(channel);
                    handler.handleOpened(channel);
                    handle.cancelKey();
                } else {
                    log.trace("Connection is not yet up (deferred)");
                    handle.getSelectionKey().interestOps(SelectionKey.OP_CONNECT).selector().wakeup();
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
                IoUtils.safeClose(socketChannel);
                return this;
            }
        }
    }
}