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

package org.xnio.nio;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import org.xnio.FailedIoFuture;
import org.xnio.FinishedIoFuture;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.TcpServer;
import org.xnio.OptionMap;
import org.xnio.Option;
import org.xnio.ChannelListener;
import org.xnio.Options;
import org.xnio.channels.BoundChannel;
import org.xnio.channels.TcpChannel;
import org.xnio.channels.UnsupportedOptionException;
import org.xnio.log.Logger;
import org.xnio.management.TcpServerMBean;

import javax.management.NotCompliantMBeanException;
import javax.management.StandardMBean;

/**
 *
 */
final class NioTcpServer implements TcpServer {
    private static final Logger log = Logger.getLogger("org.xnio.nio.tcp.server");
    private static final Logger chanLog = Logger.getLogger("org.xnio.nio.tcp.server.channel");

    private static final AtomicReferenceFieldUpdater<NioTcpServer,ChannelListener> openListenerUpdater = AtomicReferenceFieldUpdater.newUpdater(NioTcpServer.class, ChannelListener.class, "openListener");
    private static final AtomicReferenceFieldUpdater<NioTcpServer,ChannelListener> bindListenerUpdater = AtomicReferenceFieldUpdater.newUpdater(NioTcpServer.class, ChannelListener.class, "bindListener");
    private static final AtomicReferenceFieldUpdater<NioTcpServer,ChannelListener> closeListenerUpdater = AtomicReferenceFieldUpdater.newUpdater(NioTcpServer.class, ChannelListener.class, "closeListener");

    private static final AtomicReferenceFieldUpdater<Binding, ChannelListener> boundCloseListenerUpdater = AtomicReferenceFieldUpdater.newUpdater(Binding.class, ChannelListener.class, "closeListener");

    private volatile ChannelListener<? super BoundChannel<InetSocketAddress>> bindListener = null;
    private volatile ChannelListener<? super TcpChannel> openListener = null;
    private volatile ChannelListener<? super TcpServer> closeListener = null;

    private final ChannelListener.Setter<BoundChannel<InetSocketAddress>> bindSetter = IoUtils.getSetter(this, bindListenerUpdater);
    private final ChannelListener.Setter<TcpChannel> openSetter = IoUtils.getSetter(this, openListenerUpdater);
    private final ChannelListener.Setter<TcpServer> closeSetter = IoUtils.getSetter(this, closeListenerUpdater);

    private final Executor executor;
    private final NioXnio xnio;

    private final Object lock = new Object();

    private final Set<Binding> boundChannels = new LinkedHashSet<Binding>();

    private final AtomicLong globalAcceptedConnections = new AtomicLong();

    private boolean closed;
    private Boolean reuseAddress;
    private Integer receiveBufferSize;
    private Integer backlog;
    private Boolean keepAlive;
    private Boolean oobInline;
    private Boolean tcpNoDelay;
    private boolean manageConnections;

    private static final Set<Option<?>> options = Option.setBuilder()
            .add(Options.BACKLOG)
            .add(Options.REUSE_ADDRESSES)
            .add(Options.RECEIVE_BUFFER)
            .add(Options.KEEP_ALIVE)
            .add(Options.TCP_OOB_INLINE)
            .add(Options.TCP_NODELAY)
            .create();

    private final Closeable mbeanHandle;

    static NioTcpServer create(final NioXnio nioXnio, final Executor executor, final ChannelListener<? super TcpChannel> channelListener, final OptionMap optionMap) {
        return new NioTcpServer(nioXnio, executor, channelListener, optionMap);
    }

    private NioTcpServer(final NioXnio nioXnio, final Executor executor, final ChannelListener<? super TcpChannel> openListener, final OptionMap optionMap) {
        synchronized (lock) {
            xnio = nioXnio;
            this.executor = executor;
            // this has to be if/else because the wildcards confuse Java's type resolution "system" :|
            if (openListener == null) {
                this.openListener = IoUtils.closingChannelListener();
            } else {
                this.openListener = openListener;
            }
            reuseAddress = optionMap.get(Options.REUSE_ADDRESSES);
            receiveBufferSize = optionMap.get(Options.RECEIVE_BUFFER);
            backlog = optionMap.get(Options.BACKLOG);
            keepAlive = optionMap.get(Options.KEEP_ALIVE);
            oobInline = optionMap.get(Options.TCP_OOB_INLINE);
            tcpNoDelay = optionMap.get(Options.TCP_NODELAY);
            manageConnections = ! optionMap.contains(Options.MANAGE_CONNECTIONS) || optionMap.get(Options.MANAGE_CONNECTIONS).booleanValue();
            Closeable closeable = IoUtils.nullCloseable();
            try {
                closeable = nioXnio.registerMBean(new MBean());
            } catch (NotCompliantMBeanException e) {
                log.trace(e, "Failed to register MBean");
            }
            mbeanHandle = closeable;
        }
    }

    public Collection<BoundChannel<InetSocketAddress>> getChannels() {
        synchronized (lock) {
            return new ArrayList<BoundChannel<InetSocketAddress>>(boundChannels);
        }
    }

    public IoFuture<BoundChannel<InetSocketAddress>> bind(final InetSocketAddress address) {
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
                final Binding channel = new Binding(serverSocketChannel);
                boundChannels.add(channel);
                final ChannelListener<? super BoundChannel<InetSocketAddress>> bindListener = this.bindListener;
                if (bindListener != null) {
                    executor.execute(IoUtils.<BoundChannel<InetSocketAddress>>getChannelListenerTask(channel, bindListener));
                }
                return new FinishedIoFuture<BoundChannel<InetSocketAddress>>(channel);
            } catch (IOException e) {
                return new FailedIoFuture<BoundChannel<InetSocketAddress>>(e);
            }
        }
    }

    public void close() throws IOException {
        synchronized (lock) {
            if (! closed) {
                log.trace("Closing %s", this);
                closed = true;
                IoUtils.<TcpServer>invokeChannelListener(executor, this, closeListener);
                final ArrayList<Binding> list = new ArrayList<Binding>(boundChannels);
                for (final Binding boundChannel : list) {
                    IoUtils.safeClose(boundChannel);
                }
                IoUtils.safeClose(mbeanHandle);
            }
        }
    }

    public boolean supportsOption(final Option<?> option) {
        return options.contains(option);
    }

    public <T> T getOption(final Option<T> option) throws UnsupportedOptionException, IOException {
        synchronized (lock) {
            if (option == Options.REUSE_ADDRESSES) {
                return option.cast(reuseAddress);
            } else if (option == Options.RECEIVE_BUFFER) {
                return option.cast(receiveBufferSize);
            } else if (option == Options.BACKLOG) {
                return option.cast(backlog);
            } else if (option == Options.KEEP_ALIVE) {
                return option.cast(keepAlive);
            } else if (option == Options.TCP_OOB_INLINE) {
                return option.cast(oobInline);
            } else if (option == Options.TCP_NODELAY) {
                return option.cast(tcpNoDelay);
            } else {
                return null;
            }
        }
    }

    public <T> NioTcpServer setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        synchronized (lock) {
            if (option == Options.REUSE_ADDRESSES) {
                reuseAddress = Options.REUSE_ADDRESSES.cast(value);
            } else if (option == Options.RECEIVE_BUFFER) {
                receiveBufferSize = Options.RECEIVE_BUFFER.cast(value);
            } else if (option == Options.BACKLOG) {
                backlog = Options.BACKLOG.cast(value);
            } else if (option == Options.KEEP_ALIVE) {
                keepAlive = Options.KEEP_ALIVE.cast(value);
            } else if (option == Options.TCP_OOB_INLINE) {
                oobInline = Options.TCP_OOB_INLINE.cast(value);
            } else if (option == Options.TCP_NODELAY) {
                tcpNoDelay = Options.TCP_NODELAY.cast(value);
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
        private final Binding binding;

        Handler(final ServerSocketChannel channel, final Executor executor, final AtomicLong acceptedConnections, final AtomicLong connections, final Binding binding) {
            socketChannel = channel;
            this.executor = executor;
            globalAcceptedConnections = acceptedConnections;
            this.acceptedConnections = connections;
            this.binding = binding;
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
                        final NioTcpChannel channel = new NioTcpChannel(xnio, socketChannel, executor, manageConnections, (InetSocketAddress) socket.getLocalSocketAddress(), (InetSocketAddress) socket.getRemoteSocketAddress());
                        xnio.addManaged(channel);
                        log.trace("TCP server accepted connection");
                        ok = IoUtils.<TcpChannel>invokeChannelListener(channel, openListener);
                        if (ok) {
                            acceptedConnections.incrementAndGet();
                            globalAcceptedConnections.incrementAndGet();
                        }
                    } finally {
                        if (! ok) {
                            // do NOT call close handler, since open handler was either not called or it failed
                            IoUtils.safeClose(socketChannel);
                        }
                    }
                }
            } catch (ClosedChannelException e) {
                log.trace("Channel closed: %s", e);
                IoUtils.safeClose(binding);
                return;
            } catch (IOException e) {
                log.trace(e, "I/O error on TCP server");
                IoUtils.safeClose(binding);
            }
        }
    }

    public String toString() {
        return String.format("TCP server (NIO) <%s>", Integer.toHexString(hashCode()));
    }

    public ChannelListener.Setter<? extends BoundChannel<InetSocketAddress>> getBindSetter() {
        return bindSetter;
    }

    public ChannelListener.Setter<TcpChannel> getOpenSetter() {
        return openSetter;
    }

    public ChannelListener.Setter<TcpServer> getCloseSetter() {
        return closeSetter;
    }

    public boolean isOpen() {
        synchronized (lock) {
            return ! closed;
        }
    }

    public final class Binding implements BoundChannel<InetSocketAddress> {

        private final NioHandle handle;
        private final ServerSocket serverSocket;
        private final InetSocketAddress address;
        private final ServerSocketChannel channel;
        private final AtomicLong acceptedConnections = new AtomicLong();
        volatile ChannelListener<? super Binding> closeListener = null;

        private final ChannelListener.Setter<Binding> closeSetter = IoUtils.getSetter(this, boundCloseListenerUpdater);

        private final AtomicBoolean open = new AtomicBoolean(true);

        public Binding(final ServerSocketChannel channel) throws IOException {
            this.channel = channel;
            serverSocket = channel.socket();
            address = (InetSocketAddress) serverSocket.getLocalSocketAddress();
            handle = xnio.addConnectHandler(channel, new Handler(channel, IoUtils.directExecutor(), globalAcceptedConnections, acceptedConnections, this), false);
            handle.resume(SelectionKey.OP_ACCEPT);
        }

        public InetSocketAddress getLocalAddress() {
            return address;
        }

        public boolean isOpen() {
            return open.get();
        }

        public void close() throws IOException {
            if (open.getAndSet(false)) synchronized (lock) {
                chanLog.trace("Closing %s", this);
                try {
                    handle.suspend();
                    channel.close();
                } finally {
                    IoUtils.invokeChannelListener(executor, this, closeListener);
                    xnio.removeManaged(this);
                }
            }
        }

        public ChannelListener.Setter<Binding> getCloseSetter() {
            return closeSetter;
        }

        public <T> T getOption(final Option<T> option) throws IOException {
            return null;
        }

        public boolean supportsOption(final Option<?> option) {
            return false;
        }

        public <T> Binding setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
            return this;
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
                for (Binding channel : boundChannels) {
                    final InetSocketAddress bindAddress = channel.address;
                    final long acceptedConnections = channel.acceptedConnections.get();
                    listeners[i ++] = new Listener() {
                        public InetSocketAddress getBindAddress() {
                            if (bindAddress == null) {
                                return new InetSocketAddress(0);
                            }
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

        public void bind(final InetSocketAddress address) throws IOException {
            if (address == null) {
                throw new NullPointerException("address is null");
            }
            NioTcpServer.this.bind(address).get();
        }

        public void bind(final String hostName, final int port) throws IOException {
            bind(new InetSocketAddress(hostName, port));
        }

        public void unbind(final InetSocketAddress address) throws IOException {
            if (address == null) {
                throw new NullPointerException("address is null");
            }
            synchronized (lock) {
                for (Binding channel : boundChannels) {
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
