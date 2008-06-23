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

package org.jboss.xnio.core.nio;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executor;
import java.util.Map;
import java.util.Collections;
import java.util.HashMap;
import org.jboss.xnio.AbstractIoFuture;
import org.jboss.xnio.FailedIoFuture;
import org.jboss.xnio.FinishedIoFuture;
import org.jboss.xnio.IoFuture;
import org.jboss.xnio.IoHandler;
import org.jboss.xnio.channels.UnsupportedOptionException;
import org.jboss.xnio.channels.Configurable;
import org.jboss.xnio.channels.TcpChannel;
import org.jboss.xnio.channels.ChannelOption;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.TcpConnector;
import org.jboss.xnio.TcpClient;
import org.jboss.xnio.log.Logger;
import org.jboss.xnio.spi.TcpConnectorService;
import org.jboss.xnio.spi.Lifecycle;
import org.jboss.xnio.spi.SpiUtils;

/**
 *
 */
public final class NioTcpConnector implements Lifecycle, TcpConnector, TcpConnectorService {

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

    public IoFuture<TcpChannel> connectTo(final SocketAddress dest, final IoHandler<? super TcpChannel> handler) {
        if (dest == null) {
            throw new NullPointerException("dest is null");
        }
        if (handler == null) {
            throw new NullPointerException("handler is null");
        }
        return doConnectTo(null, dest, handler);
    }

    public IoFuture<TcpChannel> connectTo(final SocketAddress src, final SocketAddress dest, final IoHandler<? super TcpChannel> handler) {
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

    public TcpClient createClient(final SocketAddress dest) {
        if (dest == null) {
            throw new NullPointerException("dest is null");
        }
        return new TcpClient() {
            public IoFuture<TcpChannel> connect(final IoHandler<? super TcpChannel> handler) {
                if (handler == null) {
                    throw new NullPointerException("handler is null");
                }
                return doConnectTo(null, dest, handler);
            }
        };
    }

    public TcpClient createClient(final SocketAddress src, final SocketAddress dest) {
        if (src == null) {
            throw new NullPointerException("src is null");
        }
        if (dest == null) {
            throw new NullPointerException("dest is null");
        }
        return new TcpClient() {
            public IoFuture<TcpChannel> connect(final IoHandler<? super TcpChannel> handler) {
                if (handler == null) {
                    throw new NullPointerException("handler is null");
                }
                return doConnectTo(src, dest, handler);
            }
        };
    }

    private IoFuture<TcpChannel> doConnectTo(final SocketAddress src, final SocketAddress dest, final IoHandler<? super TcpChannel> handler) {
        try {
            final SocketChannel socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(false);
            final Socket socket = socketChannel.socket();
            if (src != null) socket.bind(src);
            configureStream(socket);
            if (socketChannel.connect(dest)) {
                final NioSocketChannelImpl channel = new NioSocketChannelImpl(nioProvider, socketChannel, handler);
                executor.execute(new Runnable() {
                    public void run() {
                        if (! SpiUtils.<TcpChannel>handleOpened(handler, channel)) {
                            IoUtils.safeClose(socketChannel);
                        }
                    }
                });
                nioProvider.addChannel(channel);
                return new FinishedIoFuture<TcpChannel>(channel);
            } else {
                final ConnectionHandler connectionHandler = new ConnectionHandler(executor, socketChannel, nioProvider, handler);
                connectionHandler.handle.getSelectionKey().interestOps(SelectionKey.OP_CONNECT).selector().wakeup();
                return connectionHandler.future;
            }
        } catch (IOException e) {
            return new FailedIoFuture<TcpChannel>(e);
        }
    }

    private static final Map<String, Class<?>> OPTIONS;

    static {
        final Map<String, Class<?>> options = new HashMap<String, Class<?>>();
        options.put(ChannelOption.KEEP_ALIVE, Boolean.class);
        options.put(ChannelOption.TCP_OOB_INLINE, Boolean.class);
        options.put(ChannelOption.RECEIVE_BUFFER, Integer.class);
        options.put(ChannelOption.REUSE_ADDRESSES, Boolean.class);
        options.put(ChannelOption.SEND_BUFFER, Integer.class);
        options.put(ChannelOption.TCP_NODELAY, Boolean.class);
        OPTIONS = Collections.unmodifiableMap(options);
    }

    public Object getOption(final String name) throws UnsupportedOptionException, IOException {
        if (name == null) {
            throw new NullPointerException("name is null");
        }
        if (! OPTIONS.containsKey(name)) {
            throw new UnsupportedOptionException("Option not supported: " + name);
        }
        if (ChannelOption.KEEP_ALIVE.equals(name)) {
            return Boolean.valueOf(keepAlive);
        } else if (ChannelOption.TCP_OOB_INLINE.equals(name)) {
            return Boolean.valueOf(oobInline);
        } else if (ChannelOption.RECEIVE_BUFFER.equals(name)) {
            final int v = receiveBufferSize;
            return v == -1 ? null : Integer.valueOf(v);
        } else if (ChannelOption.REUSE_ADDRESSES.equals(name)) {
            return Boolean.valueOf(reuseAddress);
        } else if (ChannelOption.SEND_BUFFER.equals(name)) {
            final int v = sendBufferSize;
            return v == -1 ? null : Integer.valueOf(v);
        } else if (ChannelOption.TCP_NODELAY.equals(name)) {
            return Boolean.valueOf(tcpNoDelay);
        } else {
            throw new IllegalStateException("Failed to get supported option: " + name);
        }
    }

    public Map<String, Class<?>> getOptions() {
        return OPTIONS;
    }

    public TcpConnectorService setOption(final String name, final Object value) throws IllegalArgumentException, IOException {
        if (name == null) {
            throw new NullPointerException("name is null");
        }
        if (! OPTIONS.containsKey(name)) {
            throw new UnsupportedOptionException("Option not supported: " + name);
        }
        if (ChannelOption.KEEP_ALIVE.equals(name)) {
            setKeepAlive(((Boolean)value).booleanValue());
            return this;
        } else if (ChannelOption.TCP_OOB_INLINE.equals(name)) {
            setOobInline(((Boolean)value).booleanValue());
            return this;
        } else if (ChannelOption.RECEIVE_BUFFER.equals(name)) {
            setReceiveBufferSize(((Integer)value).intValue());
            return this;
        } else if (ChannelOption.REUSE_ADDRESSES.equals(name)) {
            setReuseAddress(((Boolean)value).booleanValue());
            return this;
        } else if (ChannelOption.SEND_BUFFER.equals(name)) {
            setSendBufferSize(((Integer)value).intValue());
            return this;
        } else if (ChannelOption.TCP_NODELAY.equals(name)) {
            setTcpNoDelay(((Boolean)value).booleanValue());
            return this;
        } else {
            throw new IllegalStateException("Failed to set supported option: " + name);
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
            handle = nioProvider.addConnectHandler(socketChannel, this);
            future = new FutureImpl(executor);
        }

        public void run() {
            try {
                if (socketChannel.finishConnect()) {
                    final NioSocketChannelImpl channel = new NioSocketChannelImpl(nioProvider, socketChannel, handler);
                    future.setResult(channel);
                    handler.handleOpened(channel);
                    handle.cancelKey();
                } else {
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

        private final class FutureImpl extends AbstractIoFuture<TcpChannel> {
            private final Executor executor;

            public FutureImpl(final Executor executor) {
                this.executor = executor;
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

            public IoFuture<TcpChannel> cancel() {
                IoUtils.safeClose(socketChannel);
                return this;
            }
        }
    }
}