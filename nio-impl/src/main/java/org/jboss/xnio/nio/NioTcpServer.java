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
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Collection;
import java.util.Set;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Iterator;
import java.util.HashSet;
import java.util.Collections;
import org.jboss.xnio.IoHandler;
import org.jboss.xnio.IoHandlerFactory;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.channels.TcpChannel;
import org.jboss.xnio.channels.BoundServer;
import org.jboss.xnio.channels.BoundChannel;
import org.jboss.xnio.channels.ChannelOption;
import org.jboss.xnio.channels.UnsupportedOptionException;
import org.jboss.xnio.channels.CommonOptions;
import org.jboss.xnio.log.Logger;

/**
 *
 */
public final class NioTcpServer implements BoundServer<SocketAddress, BoundChannel<SocketAddress>> {
    private static final Logger log = Logger.getLogger("org.jboss.xnio.nio.tcp.server");

    private final Executor executor;
    private final IoHandlerFactory<? super TcpChannel> handlerFactory;
    private final NioXnio xnio;

    private final Object lock = new Object();

    private final Set<NioTcpServerChannel> boundChannels = new LinkedHashSet<NioTcpServerChannel>();

    private boolean closed;
    private Boolean reuseAddress;
    private Integer receiveBufferSize;
    private Integer backlog;
    private Boolean keepAlive;
    private Boolean oobInline;
    private Boolean tcpNoDelay;

    private static final Set<ChannelOption<?>> options;

    static {
        final Set<ChannelOption<?>> optionSet = new HashSet<ChannelOption<?>>();
        optionSet.add(CommonOptions.BACKLOG);
        optionSet.add(CommonOptions.REUSE_ADDRESSES);
        optionSet.add(CommonOptions.RECEIVE_BUFFER);
        optionSet.add(CommonOptions.KEEP_ALIVE);
        optionSet.add(CommonOptions.TCP_OOB_INLINE);
        optionSet.add(CommonOptions.TCP_NODELAY);
        options = Collections.unmodifiableSet(optionSet);
    }

    static NioTcpServer create(final NioTcpServerConfig config) throws IOException {
        final NioTcpServer tcpServer = new NioTcpServer(config);
        boolean ok = false;
        try {
            final SocketAddress[] addresses = config.getInitialAddresses();
            if (addresses != null) {
                for (SocketAddress address : addresses) {
                    tcpServer.bind(address);
                }
            }
            ok = true;
            log.trace("Successfully started TCP server");
            return tcpServer;
        } finally {
            if (! ok) {
                IoUtils.safeClose(tcpServer);
            }
        }
    }

    private NioTcpServer(final NioTcpServerConfig config) throws IOException {
        synchronized (lock) {
            xnio = config.getXnio();
            executor = config.getExecutor();
            handlerFactory = config.getHandlerFactory();
            reuseAddress = config.getReuseAddresses();
            receiveBufferSize = config.getReceiveBuffer();
            backlog = config.getBacklog();
            keepAlive = config.getKeepAlive();
            oobInline = config.getOobInline();
            tcpNoDelay = config.getNoDelay();
        }
    }

    public Collection<BoundChannel<SocketAddress>> getChannels() {
        synchronized (lock) {
            return new ArrayList<BoundChannel<SocketAddress>>(boundChannels);
        }
    }

    public BoundChannel<SocketAddress> bind(final SocketAddress address) throws IOException {
        synchronized (lock) {
            if (closed) {
                throw new ClosedChannelException();
            }
            final ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(false);
            final ServerSocket serverSocket = serverSocketChannel.socket();
            final Boolean reuseAddress = this.reuseAddress;
            if (reuseAddress != null) {
                serverSocket.setReuseAddress(reuseAddress.booleanValue());
            }
            final Integer receiveBufferSize = this.receiveBufferSize;
            if (receiveBufferSize != null) {
                serverSocket.setReceiveBufferSize(receiveBufferSize.intValue());
            }
            final Integer backlog = this.backlog;
            if (backlog != null) {
                serverSocket.bind(address, backlog.intValue());
            } else {
                serverSocket.bind(address);
            }
            return new NioTcpServerChannel(serverSocketChannel);
        }
    }

    public void close() throws IOException {
        synchronized (lock) {
            if (! closed) {
                closed = true;
                final Iterator<NioTcpServerChannel> it = boundChannels.iterator();
                while (it.hasNext()) {
                    IoUtils.safeClose(it.next());
                    it.remove();
                }
            }
        }
    }

    public <T> T getOption(final ChannelOption<T> option) throws UnsupportedOptionException, IOException {
        synchronized (lock) {
            if (option == CommonOptions.REUSE_ADDRESSES) {
                return option.getType().cast(reuseAddress);
            } else if (option == CommonOptions.RECEIVE_BUFFER) {
                return option.getType().cast(receiveBufferSize);
            } else if (option == CommonOptions.BACKLOG) {
                return option.getType().cast(backlog);
            } else if (option == CommonOptions.KEEP_ALIVE) {
                return option.getType().cast(keepAlive);
            } else if (option == CommonOptions.TCP_OOB_INLINE) {
                return option.getType().cast(oobInline);
            } else if (option == CommonOptions.TCP_NODELAY) {
                return option.getType().cast(tcpNoDelay);
            } else {
                throw badOption(option);
            }
        }
    }

    public Set<ChannelOption<?>> getOptions() {
        return options;
    }

    public <T> NioTcpServer setOption(final ChannelOption<T> option, final T value) throws IllegalArgumentException, IOException {
        synchronized (lock) {
            if (option == CommonOptions.REUSE_ADDRESSES) {
                reuseAddress = CommonOptions.REUSE_ADDRESSES.getType().cast(value);
            } else if (option == CommonOptions.RECEIVE_BUFFER) {
                receiveBufferSize = CommonOptions.RECEIVE_BUFFER.getType().cast(value);
            } else if (option == CommonOptions.BACKLOG) {
                backlog = CommonOptions.BACKLOG.getType().cast(value);
            } else if (option == CommonOptions.KEEP_ALIVE) {
                keepAlive = CommonOptions.KEEP_ALIVE.getType().cast(value);
            } else if (option == CommonOptions.TCP_OOB_INLINE) {
                oobInline = CommonOptions.TCP_OOB_INLINE.getType().cast(value);
            } else if (option == CommonOptions.TCP_NODELAY) {
                tcpNoDelay = CommonOptions.TCP_NODELAY.getType().cast(value);
            } else {
                throw badOption(option);
            }
            return this;
        }
    }

    // NioCore interface

    private final class Handler implements Runnable {
        private final ServerSocketChannel socketChannel;
        private final Executor executor;

        public Handler(final ServerSocketChannel channel, final Executor executor) {
            socketChannel = channel;
            this.executor = executor;
        }

        public void run() {
            try {
                final SocketChannel socketChannel = this.socketChannel.accept();
                if (socketChannel != null) {
                    boolean ok = false;
                    try {
                        socketChannel.configureBlocking(false);
                        final Socket socket = socketChannel.socket();
                        final Boolean keepAlive = NioTcpServer.this.keepAlive;
                        if (keepAlive != null) socket.setKeepAlive(keepAlive.booleanValue());
                        final Boolean oobInline = NioTcpServer.this.oobInline;
                        if (oobInline != null) socket.setOOBInline(oobInline.booleanValue());
                        final Boolean tcpNoDelay = NioTcpServer.this.tcpNoDelay;
                        if (tcpNoDelay != null) socket.setTcpNoDelay(tcpNoDelay.booleanValue());
                        // IDEA thinks this is an unsafe cast, but it really isn't.  But to shut it up...
                        //noinspection unchecked
                        final IoHandler<? super TcpChannel> streamIoHandler = handlerFactory.createHandler();
                        final NioTcpChannel channel = new NioTcpChannel(xnio, socketChannel, streamIoHandler, executor);
                        ok = HandlerUtils.<TcpChannel>handleOpened(streamIoHandler, channel);
                        if (ok) {
                            xnio.addManaged(channel);
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
        return String.format("TCP server (NIO) <%s>", Integer.toHexString(hashCode()));
    }

    private static UnsupportedOptionException badOption(final ChannelOption<?> option) {
        return new UnsupportedOptionException("Option " + option + " is unsupported");
    }

    /**
     *
     */
    public final class NioTcpServerChannel implements BoundChannel<SocketAddress> {

        private final NioHandle handle;
        private final ServerSocket serverSocket;
        private final SocketAddress address;

        private final AtomicBoolean open = new AtomicBoolean(true);

        public NioTcpServerChannel(final ServerSocketChannel channel) throws IOException {
            serverSocket = channel.socket();
            address = serverSocket.getLocalSocketAddress();
            handle = xnio.addConnectHandler(channel, new Handler(channel, executor), false);
        }

        public SocketAddress getLocalAddress() {
            return address;
        }

        public boolean isOpen() {
            return open.get();
        }

        public void close() throws IOException {
            if (open.getAndSet(false)) synchronized (lock) {
                try {
                    handle.cancelKey();
                } catch (Throwable t) {
                    final IOException ioe = new IOException("Error closing the handle for " + toString() + ": " + t);
                    ioe.initCause(t);
                    throw ioe;
                } finally {
                    xnio.removeManaged(this);
                    boundChannels.remove(this);
                }
            }
        }

        public String toString() {
            return String.format("TCP server channel (NIO) <%s> (local: %s)", Integer.toHexString(hashCode()), getLocalAddress());
        }
    }
}