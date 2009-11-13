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
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.TcpChannelSource;
import org.jboss.xnio.TcpConnector;
import org.jboss.xnio.OptionMap;
import org.jboss.xnio.ChannelListener;
import org.jboss.xnio.Options;
import org.jboss.xnio.IoFuture;
import org.jboss.xnio.FailedIoFuture;
import org.jboss.xnio.FinishedIoFuture;
import org.jboss.xnio.FutureResult;
import org.jboss.xnio.Cancellable;
import org.jboss.xnio.channels.TcpChannel;
import org.jboss.xnio.channels.BoundChannel;
import org.jboss.xnio.log.Logger;

/**
 *
 */
final class NioTcpConnector implements TcpConnector {

    private static final Logger log = Logger.getLogger("org.jboss.xnio.nio.tcp.connector");

    private final NioXnio nioXnio;
    private final Executor executor;
    private final InetSocketAddress src;
    private final OptionMap optionMap;

    private NioTcpConnector(NioXnio nioXnio, Executor executor, OptionMap optionMap, final InetSocketAddress src) {
        if (nioXnio == null) {
            throw new NullPointerException("nioXnio is null");
        }
        if (executor == null) {
            throw new NullPointerException("executor is null");
        }
        this.nioXnio = nioXnio;
        this.executor = executor;
        this.src = src;
        this.optionMap = optionMap;
    }

    private void configureStream(final Socket socket) throws SocketException {
        final OptionMap optionMap = this.optionMap;
        if (optionMap.contains(Options.KEEP_ALIVE)) socket.setKeepAlive(optionMap.get(Options.KEEP_ALIVE).booleanValue());
        if (optionMap.contains(Options.TCP_OOB_INLINE)) socket.setOOBInline(optionMap.get(Options.TCP_OOB_INLINE).booleanValue());
        if (optionMap.contains(Options.RECEIVE_BUFFER)) socket.setReceiveBufferSize(optionMap.get(Options.RECEIVE_BUFFER).intValue());
        if (optionMap.contains(Options.REUSE_ADDRESSES)) socket.setReuseAddress(optionMap.get(Options.REUSE_ADDRESSES).booleanValue());
        if (optionMap.contains(Options.SEND_BUFFER)) socket.setSendBufferSize(optionMap.get(Options.SEND_BUFFER).intValue());
        if (optionMap.contains(Options.TCP_NODELAY)) socket.setTcpNoDelay(optionMap.get(Options.TCP_NODELAY).booleanValue());
    }

    public IoFuture<TcpChannel> connectTo(final InetSocketAddress dest, final ChannelListener<? super TcpChannel> openListener, final ChannelListener<? super BoundChannel<InetSocketAddress>> bindListener) {
        if (dest == null) {
            throw new NullPointerException("dest is null");
        }
        return doConnectTo(dest, openListener, bindListener);
    }

    public TcpChannelSource createChannelSource(final InetSocketAddress dest) {
        if (dest == null) {
            throw new NullPointerException("dest is null");
        }
        return new TcpChannelSource() {
            public IoFuture<TcpChannel> open(final ChannelListener<? super TcpChannel> handler) {
                return doConnectTo(dest, handler, null);
            }
        };
    }

    private static InetSocketAddress getNonNull(InetSocketAddress addr) {
        if (addr == null) {
            return new InetSocketAddress(0);
        } else {
            return addr;
        }
    }

    private IoFuture<TcpChannel> doConnectTo(final InetSocketAddress dest, final ChannelListener<? super TcpChannel> openListener, final ChannelListener<? super BoundChannel<InetSocketAddress>> bindListener) {
        try {
            final InetSocketAddress src = getNonNull(this.src);
            log.trace("Connecting from %s to %s", src, dest);
            final SocketChannel socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(false);
            final Socket socket = socketChannel.socket();
            final Executor executor = this.executor;
            final NioXnio nioXnio = this.nioXnio;
            final NioTcpChannel channel = new NioTcpChannel(nioXnio, socketChannel, executor, optionMap.get(Options.MANAGE_CONNECTIONS, true), src, dest);
            nioXnio.addManaged(channel);
            configureStream(socket);
            socket.bind(src);
            if (bindListener != null) {
                IoUtils.invokeChannelListener(executor, channel.getBoundChannel(), bindListener);
            }
            if (socketChannel.connect(dest)) {
                log.trace("Connection from %s to %s is up (immediate)", src, dest);
                executor.execute(IoUtils.<TcpChannel>getChannelListenerTask(channel, openListener));
                return new FinishedIoFuture<TcpChannel>(channel);
            } else {
                final ConnectionHandler connectionHandler = new ConnectionHandler(executor, channel, socketChannel, nioXnio, openListener);
                connectionHandler.handle.resume(SelectionKey.OP_CONNECT);
                return connectionHandler.futureResult.getIoFuture();
            }
        } catch (IOException e) {
            return new FailedIoFuture<TcpChannel>(e);
        }
    }

    public String toString() {
        return String.format("TCP connector (NIO) <%s>", Integer.toHexString(hashCode()));
    }

    static TcpConnector create(final NioXnio nioXnio, final Executor executor, final OptionMap optionMap, final InetSocketAddress src) {
        return new NioTcpConnector(nioXnio, executor, optionMap, src);
    }

    /**
     *
     */
    private final class ConnectionHandler implements Runnable {
        private final FutureResult<TcpChannel> futureResult;
        private final NioTcpChannel channel;
        private final SocketChannel socketChannel;
        private final NioHandle handle;
        private final ChannelListener<? super TcpChannel> openListener;

        public ConnectionHandler(final Executor executor, final NioTcpChannel channel, final SocketChannel socketChannel, final NioXnio nioXnio, final ChannelListener<? super TcpChannel> openListener) throws IOException {
            this.channel = channel;
            this.socketChannel = socketChannel;
            this.openListener = openListener;
            handle = nioXnio.addConnectHandler(socketChannel, this, true);
            futureResult = new FutureResult<TcpChannel>(executor);
            futureResult.addCancelHandler(new Cancellable() {
                public Cancellable cancel() {
                    if (futureResult.setCancelled()) {
                        IoUtils.safeClose(socketChannel);
                    }
                    return this;
                }
            });
        }

        public void run() {
            final SocketChannel socketChannel = this.socketChannel;
            final FutureResult<TcpChannel> futureResult = this.futureResult;
            final NioHandle handle = this.handle;
            try {
                if (socketChannel.finishConnect()) {
                    log.trace("Connection is up (deferred)");
                    final NioTcpChannel channel = this.channel;
                    if (futureResult.setResult(channel)) {
                        IoUtils.<TcpChannel>invokeChannelListener(channel, openListener);
                    }
                    handle.cancelKey();
                } else {
                    log.trace("Connection is not yet up (deferred)");
                    handle.resume(SelectionKey.OP_CONNECT);
                    return;
                }
            } catch (IOException e) {
                futureResult.setException(e);
                handle.cancelKey();
            } catch (Exception e) {
                final String message = e.getMessage();
                final IOException ioexception = new IOException("Connection failed unexpectedly: " + message);
                ioexception.setStackTrace(e.getStackTrace());
                futureResult.setException(ioexception);
                handle.cancelKey();
            }
        }
    }
}