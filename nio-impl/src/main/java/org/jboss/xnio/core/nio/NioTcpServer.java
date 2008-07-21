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
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import org.jboss.xnio.IoHandler;
import org.jboss.xnio.IoHandlerFactory;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.channels.ChannelOption;
import org.jboss.xnio.channels.CommonOptions;
import org.jboss.xnio.channels.Configurable;
import org.jboss.xnio.channels.TcpChannel;
import org.jboss.xnio.channels.UnsupportedOptionException;
import org.jboss.xnio.log.Logger;
import org.jboss.xnio.spi.Lifecycle;
import org.jboss.xnio.spi.SpiUtils;
import org.jboss.xnio.spi.TcpServerService;

/**
 *
 */
public final class NioTcpServer implements Lifecycle, TcpServerService {
    private static final Logger log = Logger.getLogger(NioTcpServer.class);

    private NioHandle[] handles;
    private ServerSocket[] serverSockets;
    private ServerSocketChannel[] serverSocketChannels;
    private Executor executor;

    private IoHandlerFactory<? super TcpChannel> handlerFactory;

    private boolean reuseAddress = true;
    private int receiveBufferSize = -1;
    private int backlog = -1;
    private boolean keepAlive = false;
    private boolean oobInline = false;
    private boolean tcpNoDelay = false;

    private NioProvider nioProvider;
    private SocketAddress[] bindAddresses = new SocketAddress[0];

    // accessors

    public NioProvider getNioProvider() {
        return nioProvider;
    }

    public void setNioProvider(final NioProvider nioProvider) {
        this.nioProvider = nioProvider;
    }

    public boolean isReuseAddress() {
        return reuseAddress;
    }

    public void setReuseAddress(final boolean reuseAddress) {
        this.reuseAddress = reuseAddress;
    }

    public int getReceiveBufferSize() {
        return receiveBufferSize;
    }

    public void setReceiveBufferSize(final int receiveBufferSize) {
        this.receiveBufferSize = receiveBufferSize;
    }

    public int getBacklog() {
        return backlog;
    }

    public void setBacklog(final int backlog) {
        this.backlog = backlog;
    }

    public IoHandlerFactory<? super TcpChannel> getHandlerFactory() {
        return handlerFactory;
    }

    public void setHandlerFactory(final IoHandlerFactory<? super TcpChannel> handlerFactory) {
        this.handlerFactory = handlerFactory;
    }

    public SocketAddress[] getBindAddresses() {
        return bindAddresses;
    }

    public void setBindAddresses(final SocketAddress[] bindAddresses) {
        this.bindAddresses = bindAddresses;
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

    public Executor getExecutor() {
        return executor;
    }

    public void setExecutor(final Executor executor) {
        this.executor = executor;
    }

    // lifecycle

    public void start() throws IOException {
        if (nioProvider == null) {
            throw new NullPointerException("nioProvider is null");
        }
        if (handlerFactory == null) {
            throw new NullPointerException("handlerFactory is null");
        }
        final int bindCount = bindAddresses.length;
        serverSocketChannels = new ServerSocketChannel[bindCount];
        serverSockets = new ServerSocket[bindCount];
        handles = new NioHandle[bindCount];
        for (int i = 0; i < bindCount; i++) {
            try {
                ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
                serverSocketChannel.configureBlocking(false);
                ServerSocket serverSocket = serverSocketChannel.socket();
                serverSocket.setReuseAddress(reuseAddress);
                if (receiveBufferSize > 0) {
                    serverSocket.setReceiveBufferSize(receiveBufferSize);
                }
                NioHandle handle = nioProvider.addConnectHandler(serverSocketChannel, new Handler(i));
                final SocketAddress bindAddress = bindAddresses[i];
                if (backlog > 0) {
                    log.trace("Binding TCP server to %s with a backlog of %d", bindAddress, Integer.valueOf(backlog));
                    serverSocket.bind(bindAddress, backlog);
                } else {
                    log.trace("Binding TCP server to %s", bindAddress);
                    serverSocket.bind(bindAddress);
                }
                serverSocketChannels[i] = serverSocketChannel;
                serverSockets[i] = serverSocket;
                handles[i] = handle;
            } catch (IOException ex) {
                // undo the opened sockets
                for (; i >= 0; i --) {
                    IoUtils.safeClose(serverSocketChannels[i]);
                }
                throw ex;
            }
        }
        for (int i = 0; i < bindCount; i++) {
            handles[i].getSelectionKey().interestOps(SelectionKey.OP_ACCEPT).selector().wakeup();
        }
        log.trace("Successfully started TCP server");
    }

    public void stop() {
        int bindCount = bindAddresses.length;
        for (int i = 0; i < bindCount; i ++) {
            if (handles != null && handles.length > i && handles[i] != null) {
                if (handles[i] != null) try {
                    handles[i].cancelKey();
                } catch (Throwable t) {
                    log.trace(t, "Cancel key failed");
                }
            }
            if (serverSocketChannels != null && serverSocketChannels.length > i && serverSocketChannels[i] != null) {
                if (serverSocketChannels[i] != null) try {
                    serverSocketChannels[i].close();
                } catch (Throwable t) {
                    log.trace(t, "Cancel key failed");
                }
            }
        }
    }

    private static final Set<ChannelOption<?>> OPTIONS;

    static {
        final Set<ChannelOption<?>> options = new HashSet<ChannelOption<?>>();
        options.add(CommonOptions.REUSE_ADDRESSES);
        options.add(CommonOptions.RECEIVE_BUFFER);
        options.add(CommonOptions.BACKLOG);
        options.add(CommonOptions.KEEP_ALIVE);
        options.add(CommonOptions.TCP_OOB_INLINE);
        options.add(CommonOptions.TCP_NODELAY);
        OPTIONS = Collections.unmodifiableSet(options);
    }

    @SuppressWarnings({"unchecked"})
    public <T> T getOption(final ChannelOption<T> option) throws UnsupportedOptionException, IOException {
        if (option == null) {
            throw new NullPointerException("name is null");
        }
        if (! OPTIONS.contains(option)) {
            throw new UnsupportedOptionException("Option not supported: " + option);
        }
        if (CommonOptions.BACKLOG.equals(option)) {
            final int v = backlog;
            return v == -1 ? null : (T) Integer.valueOf(v);
        } else if (CommonOptions.KEEP_ALIVE.equals(option)) {
            return (T) Boolean.valueOf(keepAlive);
        } else if (CommonOptions.TCP_OOB_INLINE.equals(option)) {
            return (T) Boolean.valueOf(oobInline);
        } else if (CommonOptions.RECEIVE_BUFFER.equals(option)) {
            final int v = receiveBufferSize;
            return v == -1 ? null : (T) Integer.valueOf(v);
        } else if (CommonOptions.REUSE_ADDRESSES.equals(option)) {
            return (T) Boolean.valueOf(reuseAddress);
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
        if (CommonOptions.BACKLOG.equals(option)) {
            setBacklog(((Integer)value).intValue());
            return this;
        } else if (CommonOptions.KEEP_ALIVE.equals(option)) {
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
        } else if (CommonOptions.TCP_NODELAY.equals(option)) {
            setTcpNoDelay(((Boolean)value).booleanValue());
            return this;
        } else {
            throw new IllegalStateException("Failed to set supported option: " + option);
        }
    }

    // NioCore interface

    private final class Handler implements Runnable {
        private final int idx;

        public Handler(final int idx) {
            this.idx = idx;
        }

        public void run() {
            try {
                final SocketChannel socketChannel = serverSocketChannels[idx].accept();
                if (socketChannel != null) {
                    boolean ok = false;
                    try {
                        socketChannel.configureBlocking(false);
                        final Socket socket = socketChannel.socket();
                        socket.setKeepAlive(keepAlive);
                        socket.setOOBInline(oobInline);
                        socket.setTcpNoDelay(tcpNoDelay);
                        // IDEA thinks this is an unsafe cast, but it really isn't.  But to shut it up...
                        //noinspection unchecked
                        final IoHandler<? super TcpChannel> streamIoHandler = handlerFactory.createHandler();
                        final NioSocketChannelImpl channel = new NioSocketChannelImpl(nioProvider, socketChannel, streamIoHandler);
                        ok = SpiUtils.<TcpChannel>handleOpened(streamIoHandler, channel);
                        if (ok) {
                            nioProvider.addChannel(channel);
                            log.trace("TCP server accepted connection");
                        }
                    } finally {
                        if (! ok) {
                            log.trace("TCP server failed to accept connection");
                            // do NOT call close handler, since open handler was either not called or it failed
                            IoUtils.safeClose(socketChannel);
                        }
                    }
                }
            } catch (ClosedChannelException e) {
                log.trace("Channel closed: %s", e.getMessage());
                return;
            } catch (IOException e) {
                log.trace(e, "I/O error on TCP server");
            }
        }
    }

    public String toString() {
        return String.format("TCP server (NIO) <%s>", Integer.toString(hashCode(), 16));
    }
}