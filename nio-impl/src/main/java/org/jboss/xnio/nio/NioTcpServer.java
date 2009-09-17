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

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.jboss.xnio.FailedIoFuture;
import org.jboss.xnio.FinishedIoFuture;
import org.jboss.xnio.IoFuture;
import org.jboss.xnio.IoHandler;
import org.jboss.xnio.IoHandlerFactory;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.TcpServer;
import org.jboss.xnio.OptionMap;
import org.jboss.xnio.Option;
import org.jboss.xnio.channels.BoundChannel;
import org.jboss.xnio.channels.CommonOptions;
import org.jboss.xnio.channels.TcpChannel;
import org.jboss.xnio.channels.UnsupportedOptionException;
import org.jboss.xnio.log.Logger;
import org.jboss.xnio.management.TcpServerMBean;

import javax.management.NotCompliantMBeanException;
import javax.management.StandardMBean;

/**
 *
 */
public final class NioTcpServer implements TcpServer {
    private static final Logger log = Logger.getLogger("org.jboss.xnio.nio.tcp.server");
    private static final Logger chanLog = Logger.getLogger("org.jboss.xnio.nio.tcp.server.channel");

    private final Executor executor;
    private final IoHandlerFactory<? super TcpChannel> handlerFactory;
    private final NioXnio xnio;

    private final Object lock = new Object();

    private final Set<NioTcpServerChannel> boundChannels = new LinkedHashSet<NioTcpServerChannel>();

    private final AtomicLong globalAcceptedConnections = new AtomicLong();

    private boolean closed;
    private Boolean reuseAddress;
    private Integer receiveBufferSize;
    private Integer backlog;
    private Boolean keepAlive;
    private Boolean oobInline;
    private Boolean tcpNoDelay;
    private boolean manageConnections;

    private static final Set<Option<?>> options;
    private final Closeable mbeanHandle;

    static {
        final Set<Option<?>> optionSet = new HashSet<Option<?>>();
        optionSet.add(CommonOptions.BACKLOG);
        optionSet.add(CommonOptions.REUSE_ADDRESSES);
        optionSet.add(CommonOptions.RECEIVE_BUFFER);
        optionSet.add(CommonOptions.KEEP_ALIVE);
        optionSet.add(CommonOptions.TCP_OOB_INLINE);
        optionSet.add(CommonOptions.TCP_NODELAY);
        options = Collections.unmodifiableSet(optionSet);
    }

    static NioTcpServer create(final NioXnio nioXnio, final Executor executor, final IoHandlerFactory<? super TcpChannel> handlerFactory, final OptionMap optionMap) {
        return new NioTcpServer(nioXnio, executor, handlerFactory, optionMap);
    }

    private NioTcpServer(final NioXnio nioXnio, final Executor executor, final IoHandlerFactory<? super TcpChannel> handlerFactory, final OptionMap optionMap) {
        synchronized (lock) {
            xnio = nioXnio;
            this.executor = executor;
            this.handlerFactory = handlerFactory;
            reuseAddress = optionMap.get(CommonOptions.REUSE_ADDRESSES);
            receiveBufferSize = optionMap.get(CommonOptions.RECEIVE_BUFFER);
            backlog = optionMap.get(CommonOptions.BACKLOG);
            keepAlive = optionMap.get(CommonOptions.KEEP_ALIVE);
            oobInline = optionMap.get(CommonOptions.TCP_OOB_INLINE);
            tcpNoDelay = optionMap.get(CommonOptions.TCP_NODELAY);
            manageConnections = ! optionMap.contains(CommonOptions.MANAGE_CONNECTIONS) || optionMap.get(CommonOptions.MANAGE_CONNECTIONS).booleanValue();
            Closeable closeable = IoUtils.nullCloseable();
            try {
                closeable = nioXnio.registerMBean(new MBean());
            } catch (NotCompliantMBeanException e) {
                log.trace(e, "Failed to register MBean");
            }
            mbeanHandle = closeable;
        }
    }

    public Collection<BoundChannel<SocketAddress>> getChannels() {
        synchronized (lock) {
            return new ArrayList<BoundChannel<SocketAddress>>(boundChannels);
        }
    }

    public IoFuture<BoundChannel<SocketAddress>> bind(final SocketAddress address) {
        synchronized (lock) {
            try {
                if (closed) {
                    throw new ClosedChannelException();
                }
                final ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
                serverSocketChannel.configureBlocking(false);
                final ServerSocket serverSocket = serverSocketChannel.socket();
                final Boolean reuseAddress = this.reuseAddress;
                if (reuseAddress != null) serverSocket.setReuseAddress(reuseAddress.booleanValue());
                final Integer receiveBufferSize = this.receiveBufferSize;
                if (receiveBufferSize != null) serverSocket.setReceiveBufferSize(receiveBufferSize.intValue());
                final Integer backlog = this.backlog;
                if (backlog != null) {
                    serverSocket.bind(address, backlog.intValue());
                } else {
                    serverSocket.bind(address);
                }
                final NioTcpServerChannel channel = new NioTcpServerChannel(serverSocketChannel);
                boundChannels.add(channel);
                return new FinishedIoFuture<BoundChannel<SocketAddress>>(channel);
            } catch (IOException e) {
                return new FailedIoFuture<BoundChannel<SocketAddress>>(e);
            }
        }
    }

    public void close() throws IOException {
        synchronized (lock) {
            if (! closed) {
                log.trace("Closing %s", this);
                closed = true;
                final ArrayList<NioTcpServerChannel> list = new ArrayList<NioTcpServerChannel>(boundChannels);
                for (final NioTcpServerChannel boundChannel : list) {
                    IoUtils.safeClose(boundChannel);
                }
                IoUtils.safeClose(mbeanHandle);
            }
        }
    }

    public <T> T getOption(final Option<T> option) throws UnsupportedOptionException, IOException {
        synchronized (lock) {
            if (option == CommonOptions.REUSE_ADDRESSES) {
                return option.cast(reuseAddress);
            } else if (option == CommonOptions.RECEIVE_BUFFER) {
                return option.cast(receiveBufferSize);
            } else if (option == CommonOptions.BACKLOG) {
                return option.cast(backlog);
            } else if (option == CommonOptions.KEEP_ALIVE) {
                return option.cast(keepAlive);
            } else if (option == CommonOptions.TCP_OOB_INLINE) {
                return option.cast(oobInline);
            } else if (option == CommonOptions.TCP_NODELAY) {
                return option.cast(tcpNoDelay);
            } else {
                return null;
            }
        }
    }

    public Set<Option<?>> getOptions() {
        return options;
    }

    public <T> NioTcpServer setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        synchronized (lock) {
            if (option == CommonOptions.REUSE_ADDRESSES) {
                reuseAddress = CommonOptions.REUSE_ADDRESSES.cast(value);
            } else if (option == CommonOptions.RECEIVE_BUFFER) {
                receiveBufferSize = CommonOptions.RECEIVE_BUFFER.cast(value);
            } else if (option == CommonOptions.BACKLOG) {
                backlog = CommonOptions.BACKLOG.cast(value);
            } else if (option == CommonOptions.KEEP_ALIVE) {
                keepAlive = CommonOptions.KEEP_ALIVE.cast(value);
            } else if (option == CommonOptions.TCP_OOB_INLINE) {
                oobInline = CommonOptions.TCP_OOB_INLINE.cast(value);
            } else if (option == CommonOptions.TCP_NODELAY) {
                tcpNoDelay = CommonOptions.TCP_NODELAY.cast(value);
            }
            return this;
        }
    }

    // NioCore interface

    private final class Handler implements Runnable {
        private final ServerSocketChannel socketChannel;
        private final Executor executor;
        private final AtomicLong globalAcceptedConnections;
        private final AtomicLong acceptedConnections;

        public Handler(final ServerSocketChannel channel, final Executor executor, final AtomicLong acceptedConnections, final AtomicLong connections) {
            socketChannel = channel;
            this.executor = executor;
            globalAcceptedConnections = acceptedConnections;
            this.acceptedConnections = connections;
        }

        public void run() {
            final AtomicLong acceptedConnections = this.acceptedConnections;
            final AtomicLong globalAcceptedConnections = this.globalAcceptedConnections;
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
                        final NioTcpChannel channel = new NioTcpChannel(xnio, socketChannel, streamIoHandler, executor, manageConnections);
                        ok = HandlerUtils.<TcpChannel>handleOpened(streamIoHandler, channel);
                        if (ok) {
                            acceptedConnections.incrementAndGet();
                            globalAcceptedConnections.incrementAndGet();
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

    public final class NioTcpServerChannel implements BoundChannel<SocketAddress> {

        private final NioHandle handle;
        private final ServerSocket serverSocket;
        private final SocketAddress address;
        private final ServerSocketChannel channel;
        private final AtomicLong acceptedConnections = new AtomicLong();

        private final AtomicBoolean open = new AtomicBoolean(true);

        public NioTcpServerChannel(final ServerSocketChannel channel) throws IOException {
            this.channel = channel;
            serverSocket = channel.socket();
            address = serverSocket.getLocalSocketAddress();
            handle = xnio.addConnectHandler(channel, new Handler(channel, executor, globalAcceptedConnections, acceptedConnections), false);
            handle.resume(SelectionKey.OP_ACCEPT);
        }

        public SocketAddress getLocalAddress() {
            return address;
        }

        public boolean isOpen() {
            return open.get();
        }

        public void close() throws IOException {
            if (open.getAndSet(false)) synchronized (lock) {
                chanLog.trace("Closing %s", this);
                try {
                    channel.close();
                } finally {
                    xnio.removeManaged(this);
                }
            }
        }

        public String toString() {
            return String.format("TCP server channel (NIO) <%s> (local: %s)", Integer.toHexString(hashCode()), getLocalAddress());
        }
    }

    public final class MBean extends StandardMBean implements TcpServerMBean {

        protected MBean() throws NotCompliantMBeanException {
            super(TcpServerMBean.class);
        }

        public String toString() {
            return "TCPServerMBean";
        }

        public Listener[] getBoundListeners() {
            synchronized (lock) {
                final Listener[] listeners = new Listener[boundChannels.size()];
                int i = 0;
                for (NioTcpServerChannel channel : boundChannels) {
                    final SocketAddress bindAddress = channel.address;
                    final long acceptedConnections = channel.acceptedConnections.get();
                    listeners[i ++] = new Listener() {
                        public SocketAddress getBindAddress() {
                            return bindAddress;
                        }

                        public long getAcceptedConnections() {
                            return acceptedConnections;
                        }
                    };
                }
                return listeners;
            }
        }

        public long getAcceptedConnections() {
            return globalAcceptedConnections.get();
        }

        public void bind(final SocketAddress address) throws IOException {
            if (address == null) {
                throw new NullPointerException("address is null");
            }
            NioTcpServer.this.bind(address).get();
        }

        public void bind(final String hostName, final int port) throws IOException {
            bind(new InetSocketAddress(hostName, port));
        }

        public void unbind(final SocketAddress address) throws IOException {
            if (address == null) {
                throw new NullPointerException("address is null");
            }
            synchronized (lock) {
                for (NioTcpServerChannel channel : boundChannels) {
                    if (channel.address.equals(address)) {
                        channel.close();
                        return;
                    }
                }
            }
            throw new IOException("No channel bound to address " + address);
        }

        public void unbind(final String hostName, final int port) throws IOException {
            unbind(new InetSocketAddress(hostName, port));
        }

        public void close() {
            IoUtils.safeClose(NioTcpServer.this);
        }
    }
}
