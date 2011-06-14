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

package org.xnio;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.FileChannel;
import java.security.AccessController;
import java.security.GeneralSecurityException;
import java.security.PrivilegedAction;
import java.util.EnumMap;
import java.util.ServiceLoader;

import org.jboss.logging.Logger;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.BoundChannel;
import org.xnio.channels.ConnectedMessageChannel;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.channels.MulticastMessageChannel;
import org.xnio.channels.SimpleAcceptingChannel;
import org.xnio.channels.StreamChannel;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.channels.UnsupportedOptionException;
import org.xnio.ssl.JsseXnioSsl;
import org.xnio.ssl.XnioSsl;

/**
 * The XNIO provider class.
 *
 * @apiviz.landmark
 */
public abstract class Xnio {

    private static final InetSocketAddress ANY_INET_ADDRESS = new InetSocketAddress(0);
    private static final LocalSocketAddress ANY_LOCAL_ADDRESS = new LocalSocketAddress("");

    private static final EnumMap<FileAccess, OptionMap> FILE_ACCESS_OPTION_MAPS;

    private static final RuntimePermission ALLOW_BLOCKING_SETTING = new RuntimePermission("changeThreadBlockingSetting");

    static {
        Logger.getLogger("org.xnio").info("XNIO Version " + Version.VERSION);
        final EnumMap<FileAccess, OptionMap> map = new EnumMap<FileAccess, OptionMap>(FileAccess.class);
        map.put(FileAccess.READ_ONLY, OptionMap.create(Options.FILE_ACCESS, FileAccess.READ_ONLY));
        map.put(FileAccess.READ_WRITE, OptionMap.create(Options.FILE_ACCESS, FileAccess.READ_WRITE));
        FILE_ACCESS_OPTION_MAPS = map;
    }

    /**
     * The name of this provider instance.
     */
    private final String name;

    /**
     * Construct an XNIO provider instance.
     *
     * @param name the provider name
     */
    protected Xnio(String name) {
        if (name == null) {
            throw new IllegalArgumentException("name is null");
        }
        this.name = name;
    }

    private static final ThreadLocal<Boolean> BLOCKING = new ThreadLocal<Boolean>() {
        protected Boolean initialValue() {
            return Boolean.TRUE;
        }
    };

    /**
     * Allow (or disallow) blocking I/O on the current thread.  Requires the {@code changeThreadBlockingSetting}
     * {@link RuntimePermission}.
     *
     * @param newSetting {@code true} to allow blocking I/O, {@code false} to disallow it
     * @return the previous setting
     * @throws SecurityException if a security manager is present and disallows changing the {@code changeThreadBlockingSetting} {@code RuntimePermission}
     */
    public static boolean allowBlocking(boolean newSetting) throws SecurityException {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(ALLOW_BLOCKING_SETTING);
        }
        final ThreadLocal<Boolean> threadLocal = BLOCKING;
        try {
            return threadLocal.get().booleanValue();
        } finally {
            threadLocal.set(Boolean.valueOf(newSetting));
        }
    }

    /**
     * Determine whether blocking I/O is allowed from the current thread.
     *
     * @return {@code true} if blocking I/O is allowed, {@code false} otherwise
     */
    public static boolean isBlockingAllowed() {
        return BLOCKING.get().booleanValue();
    }

    /**
     * Get an XNIO provider instance.  If multiple providers are
     * available, use the first one encountered.
     *
     * @param classLoader the class loader to search in
     * @return the XNIO provider instance
     *
     * @since 3.0
     */
    public static Xnio getInstance(ClassLoader classLoader) {
        return doGetInstance(null, ServiceLoader.load(XnioProvider.class, classLoader));
    }

    /**
     * Get an XNIO provider instance from XNIO's class loader.  If multiple providers are
     * available, use the first one encountered.
     *
     * @return the XNIO provider instance
     *
     * @since 3.0
     */
    public static Xnio getInstance() {
        return doGetInstance(null, ServiceLoader.load(XnioProvider.class, Xnio.class.getClassLoader()));
    }

    /**
     * Get a specific XNIO provider instance.
     *
     * @param provider the provider name, or {@code null} for the first available
     * @param classLoader the class loader to search in
     * @return the XNIO provider instance
     *
     * @since 3.0
     */
    public static Xnio getInstance(String provider, ClassLoader classLoader) {
        return doGetInstance(provider, ServiceLoader.load(XnioProvider.class, classLoader));
    }

    /**
     * Get a specific XNIO provider instance from XNIO's class loader.
     *
     * @param provider the provider name, or {@code null} for the first available
     * @return the XNIO provider instance
     *
     * @since 3.0
     */
    public static Xnio getInstance(String provider) {
        return doGetInstance(provider, ServiceLoader.load(XnioProvider.class, Xnio.class.getClassLoader()));
    }

    private static Xnio doGetInstance(final String provider, final ServiceLoader<XnioProvider> serviceLoader) {
        for (XnioProvider xnioProvider : serviceLoader) {
            if (provider == null || provider.equals(xnioProvider.getName())) {
                return xnioProvider.getInstance();
            }
        }
        throw new IllegalArgumentException("No matching XNIO provider found");
    }

    //==================================================
    //
    // SSL methods
    //
    //==================================================

    /**
     * Get an SSL provider for this XNIO provider.
     *
     * @param optionMap the option map to use for configuring SSL
     * @return the SSL provider
     * @throws GeneralSecurityException if an exception occurred configuring the SSL provider
     */
    public XnioSsl getSslProvider(final OptionMap optionMap) throws GeneralSecurityException {
        return new JsseXnioSsl(this, optionMap);
    }

    //==================================================
    //
    // Stream methods
    //
    //==================================================

    // Servers

    /**
     * Create a stream server, for TCP or UNIX domain servers.  The type of server is determined by the bind address.
     *
     * @param bindAddress the address to bind to
     * @param thread the connection channel thread to use for this server
     * @param acceptListener the initial accept listener
     * @param optionMap the initial configuration for the server
     * @return the acceptor
     * @throws IOException if the server could not be created
     *
     * @since 2.0
     */
    public AcceptingChannel<? extends ConnectedStreamChannel> createStreamServer(SocketAddress bindAddress, ConnectionChannelThread thread, ChannelListener<? super AcceptingChannel<ConnectedStreamChannel>> acceptListener, OptionMap optionMap) throws IOException {
        if (bindAddress == null) {
            throw new IllegalArgumentException("bindAddress is null");
        }
        if (bindAddress instanceof InetSocketAddress) {
            return createTcpServer((InetSocketAddress) bindAddress, thread, acceptListener, optionMap);
        } else if (bindAddress instanceof LocalSocketAddress) {
            return createLocalStreamServer((LocalSocketAddress) bindAddress, thread, acceptListener, optionMap);
        } else {
            throw new UnsupportedOperationException("Unsupported socket address " + bindAddress.getClass());
        }
    }

    /**
     * Implementation helper method to create a TCP stream server.
     *
     * @param bindAddress the address to bind to
     * @param thread the connection channel thread to use for this server
     * @param acceptListener the initial accept listener
     * @param optionMap the initial configuration for the server
     * @return the acceptor
     * @throws IOException if the server could not be created
     *
     * @since 3.0
     */
    @SuppressWarnings({ "unused" })
    protected AcceptingChannel<? extends ConnectedStreamChannel> createTcpServer(InetSocketAddress bindAddress, ConnectionChannelThread thread, ChannelListener<? super AcceptingChannel<ConnectedStreamChannel>> acceptListener, OptionMap optionMap) throws IOException {
        throw new UnsupportedOperationException("TCP server");
    }

    /**
     * Implementation helper method to create a UNIX domain stream server.
     *
     * @param bindAddress the address to bind to
     * @param thread the connection channel thread to use for this server
     * @param acceptListener the initial accept listener
     * @param optionMap the initial configuration for the server
     * @return the acceptor
     * @throws IOException if the server could not be created
     *
     * @since 3.0
     */
    @SuppressWarnings({ "unused" })
    protected AcceptingChannel<? extends ConnectedStreamChannel> createLocalStreamServer(LocalSocketAddress bindAddress, ConnectionChannelThread thread, ChannelListener<? super AcceptingChannel<ConnectedStreamChannel>> acceptListener, OptionMap optionMap) throws IOException {
        throw new UnsupportedOperationException("UNIX stream server");
    }

    // Connectors

    /**
     * Connect to a remote stream server.  The protocol family is determined by the type of the socket address given.
     *
     *
     * @param destination the destination address
     * @param thread the connection channel thread to use for this connection
     * @param readThread the initial read channel thread to use for this connection, or {@code null} for none
     * @param writeThread the initial write channel thread to use for this connection, or {@code null} for none
     * @param openListener the listener which will be notified when the channel is open, or {@code null} for none
     * @param bindListener the listener which will be notified when the channel is bound, or {@code null} for none
     * @param optionMap the option map    @return the future result of this operation
     * @return the future result of this operation
     *
     * @since 3.0
     */
    public IoFuture<ConnectedStreamChannel> connectStream(SocketAddress destination, ConnectionChannelThread thread, ReadChannelThread readThread, WriteChannelThread writeThread, ChannelListener<? super ConnectedStreamChannel> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        if (thread == null) {
            throw new IllegalArgumentException("thread is null");
        }
        if (destination == null) {
            throw new IllegalArgumentException("destination is null");
        }
        if (destination instanceof InetSocketAddress) {
            return connectTcp(ANY_INET_ADDRESS, (InetSocketAddress) destination, thread, readThread, writeThread, openListener, bindListener, optionMap);
        } else if (destination instanceof LocalSocketAddress) {
            return connectStreamLocal(ANY_LOCAL_ADDRESS, (LocalSocketAddress) destination, thread, readThread, writeThread, openListener, bindListener, optionMap);
        } else {
            throw new UnsupportedOperationException("Connect to server with socket address " + destination.getClass());
        }
    }

    /**
     * Connect to a remote stream server.  The protocol family is determined by the type of the socket addresses given
     * (which must match).
     *
     * @param bindAddress the local address to bind to
     * @param destination the destination address
     * @param thread the connection channel thread to use for this connection
     * @param readThread the initial read channel thread to use for this connection, or {@code null} for none
     * @param writeThread the initial write channel thread to use for this connection, or {@code null} for none
     * @param openListener the listener which will be notified when the channel is open, or {@code null} for none
     * @param bindListener the listener which will be notified when the channel is bound, or {@code null} for none
     * @param optionMap the option map
     * @return the future result of this operation
     *
     * @since 3.0
     */
    public IoFuture<ConnectedStreamChannel> connectStream(SocketAddress bindAddress, SocketAddress destination, ConnectionChannelThread thread, ReadChannelThread readThread, WriteChannelThread writeThread, ChannelListener<? super ConnectedStreamChannel> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        if (thread == null) {
            throw new IllegalArgumentException("thread is null");
        }
        if (bindAddress == null) {
            throw new IllegalArgumentException("bindAddress is null");
        }
        if (destination == null) {
            throw new IllegalArgumentException("destination is null");
        }
        if (bindAddress.getClass() != destination.getClass()) {
            throw new IllegalArgumentException("Bind address " + bindAddress.getClass() + " is not the same type as destination address " + destination.getClass());
        }
        if (destination instanceof InetSocketAddress) {
            return connectTcp((InetSocketAddress) bindAddress, (InetSocketAddress) destination, thread, readThread, writeThread, openListener, bindListener, optionMap);
        } else if (destination instanceof LocalSocketAddress) {
            return connectStreamLocal((LocalSocketAddress) bindAddress, (LocalSocketAddress) destination, thread, readThread, writeThread, openListener, bindListener, optionMap);
        } else {
            throw new UnsupportedOperationException("Connect to stream server with socket address " + destination.getClass());
        }
    }

    /**
     * Implementation helper method to connect to a TCP server.
     *
     *
     * @param bindAddress the bind address
     * @param destinationAddress the destination address
     * @param thread the connection channel thread to use for this connection
     * @param readThread the initial read channel thread to use for this connection, or {@code null} for none
     * @param writeThread the initial write channel thread to use for this connection, or {@code null} for none
     * @param openListener the listener which will be notified when the channel is open, or {@code null} for none
     * @param bindListener the listener which will be notified when the channel is bound, or {@code null} for none
     * @param optionMap the option map    @return the future result of this operation
     * @return the future result of this operation
     *
     * @since 3.0
     */
    @SuppressWarnings({ "unused" })
    protected IoFuture<ConnectedStreamChannel> connectTcp(InetSocketAddress bindAddress, InetSocketAddress destinationAddress, ConnectionChannelThread thread, ReadChannelThread readThread, WriteChannelThread writeThread, ChannelListener<? super ConnectedStreamChannel> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        throw new UnsupportedOperationException("Connect to TCP server");
    }

    /**
     * Implementation helper method to connect to a local (UNIX domain) server.
     *
     * @param bindAddress the bind address
     * @param destinationAddress the destination address
     * @param thread the connection channel thread to use for this connection
     * @param readThread the initial read channel thread to use for this connection, or {@code null} for none
     * @param writeThread the initial write channel thread to use for this connection, or {@code null} for none
     * @param openListener the listener which will be notified when the channel is open, or {@code null} for none
     * @param bindListener the listener which will be notified when the channel is bound, or {@code null} for none
     * @param optionMap the option map
     * @return the future result of this operation
     *
     * @since 3.0
     */
    @SuppressWarnings({ "unused" })
    protected IoFuture<ConnectedStreamChannel> connectStreamLocal(LocalSocketAddress bindAddress, LocalSocketAddress destinationAddress, ConnectionChannelThread thread, ReadChannelThread readThread, WriteChannelThread writeThread, ChannelListener<? super ConnectedStreamChannel> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        throw new UnsupportedOperationException("Connect to local stream server");
    }

    /**
     * Create a connector which can be used by applications which need to establish connections without having access
     * to the XNIO provider instance.
     *
     * @param bindAddress the bind address
     * @param thread the connection channel thread to use for this connection
     * @param readThread the initial read channel thread to use for this connection, or {@code null} for none
     * @param writeThread the initial write channel thread to use for this connection, or {@code null} for none
     * @param optionMap the option map
     * @return the new connector
     *
     * @since 3.0
     */
    public Connector<ConnectedStreamChannel> createStreamConnector(final SocketAddress bindAddress, final ConnectionChannelThread thread, final ReadChannelThread readThread, final WriteChannelThread writeThread, final OptionMap optionMap) {
        return new Connector<ConnectedStreamChannel>() {
            public IoFuture<ConnectedStreamChannel> connectTo(final SocketAddress destination, final ChannelListener<? super ConnectedStreamChannel> openListener, final ChannelListener<? super BoundChannel> bindListener) {
                return connectStream(bindAddress, destination, thread, readThread, writeThread, openListener, bindListener, optionMap);
            }
        };
    }

    // Acceptors

    /**
     * Accept a stream connection at a destination address.  If a wildcard address is specified, then a destination address
     * is chosen in a manner specific to the OS and/or channel type.
     *
     * @param destination the destination (bind) address
     * @param thread the connection channel thread to use for this connection
     * @param readThread the initial read channel thread to use for this connection, or {@code null} for none
     * @param writeThread the initial write channel thread to use for this connection, or {@code null} for none
     * @param openListener the listener which will be notified when the channel is open, or {@code null} for none
     * @param bindListener the listener which will be notified when the acceptor is bound, or {@code null} for none
     * @param optionMap the option map
     * @return the future connection
     *
     * @since 3.0
     */
    public IoFuture<ConnectedStreamChannel> acceptStream(SocketAddress destination, ConnectionChannelThread thread, ReadChannelThread readThread, WriteChannelThread writeThread, ChannelListener<? super ConnectedStreamChannel> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        if (thread == null) {
            throw new IllegalArgumentException("thread is null");
        }
        if (destination == null) {
            throw new IllegalArgumentException("destination is null");
        }
        if (destination instanceof InetSocketAddress) {
            return acceptTcp((InetSocketAddress) destination, thread, readThread, writeThread, openListener, bindListener, optionMap);
        } else if (destination instanceof LocalSocketAddress) {
            return acceptStreamLocal((LocalSocketAddress) destination, thread, readThread, writeThread, openListener, bindListener, optionMap);
        } else {
            throw new UnsupportedOperationException("Accept a connection to socket address " + destination.getClass());
        }
    }

    /**
     * Implementation helper method to accept a local (UNIX domain) stream connection.
     *
     * @param destination the destination (bind) address
     * @param thread the connection channel thread to use for this connection
     * @param readThread the initial read channel thread to use for this connection, or {@code null} for none
     * @param writeThread the initial write channel thread to use for this connection, or {@code null} for none
     * @param openListener the listener which will be notified when the channel is open, or {@code null} for none
     * @param bindListener the listener which will be notified when the acceptor is bound, or {@code null} for none
     * @param optionMap the option map
     *
     * @return the future connection
     */
    @SuppressWarnings({ "unused" })
    protected IoFuture<ConnectedStreamChannel> acceptStreamLocal(LocalSocketAddress destination, ConnectionChannelThread thread, ReadChannelThread readThread, WriteChannelThread writeThread, ChannelListener<? super ConnectedStreamChannel> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        throw new UnsupportedOptionException("Accept a local stream connection");
    }

    /**
     * Implementation helper method to accept a TCP connection.
     *
     * @param destination the destination (bind) address
     * @param thread the connection channel thread to use for this connection
     * @param readThread the initial read channel thread to use for this connection, or {@code null} for none
     * @param writeThread the initial write channel thread to use for this connection, or {@code null} for none
     * @param openListener the listener which will be notified when the channel is open, or {@code null} for none
     * @param bindListener the listener which will be notified when the acceptor is bound, or {@code null} for none
     * @param optionMap the option map
     *
     * @return the future connection
     */
    @SuppressWarnings({ "unused" })
    protected IoFuture<ConnectedStreamChannel> acceptTcp(InetSocketAddress destination, ConnectionChannelThread thread, ReadChannelThread readThread, WriteChannelThread writeThread, ChannelListener<? super ConnectedStreamChannel> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        throw new UnsupportedOptionException("Accept a TCP connection");
    }

    /**
     * Create an acceptor which can be used by applications which need to accept connections without having access
     * to the XNIO provider instance.
     *
     * @param thread the connection channel thread to use for this connection
     * @param readThread the initial read channel thread to use for this connection, or {@code null} for none
     * @param writeThread the initial write channel thread to use for this connection, or {@code null} for none
     * @param optionMap the option map
     * @return the new connector
     */
    public Acceptor<ConnectedStreamChannel> createStreamAcceptor(final ConnectionChannelThread thread, final ReadChannelThread readThread, final WriteChannelThread writeThread, final OptionMap optionMap) {
        return new Acceptor<ConnectedStreamChannel>() {
            public IoFuture<ConnectedStreamChannel> acceptTo(final SocketAddress destination, final ChannelListener<? super ConnectedStreamChannel> openListener, final ChannelListener<? super BoundChannel> bindListener) {
                return acceptStream(destination, thread, readThread, writeThread, openListener, bindListener, optionMap);
            }
        };
    }

    //==================================================
    //
    // Message (datagram) channel methods
    //
    //==================================================

    /**
     * Connect to a remote stream server.  The protocol family is determined by the type of the socket address given.
     *
     * @param destination the destination address
     * @param thread the connection channel thread to use for this connection
     * @param openListener the listener which will be notified when the channel is open, or {@code null} for none
     * @param bindListener the listener which will be notified when the channel is bound, or {@code null} for none
     * @param optionMap the option map
     * @return the future result of this operation
     */
    public IoFuture<ConnectedMessageChannel> connectDatagram(SocketAddress destination, ConnectionChannelThread thread, ChannelListener<? super ConnectedMessageChannel> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        if (thread == null) {
            throw new IllegalArgumentException("thread is null");
        }
        if (destination == null) {
            throw new IllegalArgumentException("destination is null");
        }
        if (destination instanceof InetSocketAddress) {
            return connectUdp(ANY_INET_ADDRESS, (InetSocketAddress) destination, thread, openListener, bindListener, optionMap);
        } else if (destination instanceof LocalSocketAddress) {
            return connectDatagramLocal(ANY_LOCAL_ADDRESS, (LocalSocketAddress) destination, thread, openListener, bindListener, optionMap);
        } else {
            throw new UnsupportedOperationException("Connect to datagram server with socket address " + destination.getClass());
        }
    }

    /**
     * Connect to a remote datagram server.  The protocol family is determined by the type of the socket addresses given
     * (which must match).
     *
     * @param bindAddress the local address to bind to
     * @param destination the destination address
     * @param thread the connection channel thread to use for this connection
     * @param openListener the listener which will be notified when the channel is open, or {@code null} for none
     * @param bindListener the listener which will be notified when the channel is bound, or {@code null} for none
     * @param optionMap the option map
     * @return the future result of this operation
     */
    public IoFuture<ConnectedMessageChannel> connectDatagram(SocketAddress bindAddress, SocketAddress destination, ConnectionChannelThread thread, ChannelListener<? super ConnectedMessageChannel> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        if (thread == null) {
            throw new IllegalArgumentException("thread is null");
        }
        if (bindAddress == null) {
            throw new IllegalArgumentException("bindAddress is null");
        }
        if (destination == null) {
            throw new IllegalArgumentException("destination is null");
        }
        if (bindAddress.getClass() != destination.getClass()) {
            throw new IllegalArgumentException("Bind address " + bindAddress.getClass() + " is not the same type as destination address " + destination.getClass());
        }
        if (destination instanceof InetSocketAddress) {
            return connectUdp((InetSocketAddress) bindAddress, (InetSocketAddress) destination, thread, openListener, bindListener, optionMap);
        } else if (destination instanceof LocalSocketAddress) {
            return connectDatagramLocal((LocalSocketAddress) bindAddress, (LocalSocketAddress) destination, thread, openListener, bindListener, optionMap);
        } else {
            throw new UnsupportedOperationException("Connect to server with socket address " + destination.getClass());
        }
    }

    /**
     * Implementation helper method to connect to a UDP server.
     *
     * @param bindAddress the bind address
     * @param destination the destination address
     * @param thread the connection channel thread to use for this connection
     * @param openListener the listener which will be notified when the channel is open, or {@code null} for none
     * @param bindListener the listener which will be notified when the channel is bound, or {@code null} for none
     * @param optionMap the option map
     * @return the future result of this operation
     */
    @SuppressWarnings({ "unused" })
    protected IoFuture<ConnectedMessageChannel> connectUdp(InetSocketAddress bindAddress, InetSocketAddress destination, ConnectionChannelThread thread, ChannelListener<? super ConnectedMessageChannel> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        throw new UnsupportedOperationException("Connect to UDP server");
    }

    /**
     * Implementation helper method to connect to a local (UNIX domain) datagram server.
     *
     * @param bindAddress the bind address
     * @param destination the destination address
     * @param thread the connection channel thread to use for this connection
     * @param openListener the listener which will be notified when the channel is open, or {@code null} for none
     * @param bindListener the listener which will be notified when the channel is bound, or {@code null} for none
     * @param optionMap the option map
     * @return the future result of this operation
     */
    @SuppressWarnings({ "unused" })
    protected IoFuture<ConnectedMessageChannel> connectDatagramLocal(LocalSocketAddress bindAddress, LocalSocketAddress destination, ConnectionChannelThread thread, ChannelListener<? super ConnectedMessageChannel> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        throw new UnsupportedOperationException("Connect to local datagram server");
    }

    /**
     * Create a connector which can be used by applications which need to establish connections without having access
     * to the XNIO provider instance.
     *
     * @param bindAddress the bind address
     * @param thread the connection channel thread to use for this connection
     * @param optionMap the option map
     * @return the new connector
     */
    public Connector<ConnectedMessageChannel> createDatagramConnector(final SocketAddress bindAddress, final ConnectionChannelThread thread, final OptionMap optionMap) {
        return new Connector<ConnectedMessageChannel>() {
            public IoFuture<ConnectedMessageChannel> connectTo(final SocketAddress destination, final ChannelListener<? super ConnectedMessageChannel> openListener, final ChannelListener<? super BoundChannel> bindListener) {
                return connectDatagram(bindAddress, destination, thread, openListener, bindListener, optionMap);
            }
        };
    }

    // Acceptors

    /**
     * Accept a message connection at a destination address.  If a wildcard address is specified, then a destination address
     * is chosen in a manner specific to the OS and/or channel type.
     *
     * @param destination the destination (bind) address
     * @param thread the connection channel thread to use for this connection
     * @param openListener the listener which will be notified when the channel is open, or {@code null} for none
     * @param bindListener the listener which will be notified when the acceptor is bound, or {@code null} for none
     * @param optionMap the option map
     * @return the future connection
     */
    public IoFuture<ConnectedMessageChannel> acceptDatagram(SocketAddress destination, ConnectionChannelThread thread, ChannelListener<? super ConnectedMessageChannel> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        if (thread == null) {
            throw new IllegalArgumentException("thread is null");
        }
        if (destination == null) {
            throw new IllegalArgumentException("destination is null");
        }
        if (destination instanceof LocalSocketAddress) {
            return acceptDatagramLocal((LocalSocketAddress) destination, thread, openListener, bindListener, optionMap);
        } else {
            throw new UnsupportedOperationException("Accept a connection to socket address " + destination.getClass());
        }
    }

    /**
     * Implementation helper method to accept a local (UNIX domain) datagram connection.
     *
     * @param destination the destination (bind) address
     * @param thread the connection channel thread to use for this connection
     * @param openListener the listener which will be notified when the channel is open, or {@code null} for none
     * @param bindListener the listener which will be notified when the acceptor is bound, or {@code null} for none
     * @param optionMap the option map
     *
     * @return the future connection
     */
    @SuppressWarnings({ "unused" })
    protected IoFuture<ConnectedMessageChannel> acceptDatagramLocal(LocalSocketAddress destination, ConnectionChannelThread thread, ChannelListener<? super ConnectedMessageChannel> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        throw new UnsupportedOptionException("Accept a local message connection");
    }

    /**
     * Create an acceptor which can be used by applications which need to accept connections without having access
     * to the XNIO provider instance.
     *
     * @param thread the connection channel thread to use for this connection
     * @param optionMap the option map
     * @return the new connector
     */
    public Acceptor<ConnectedMessageChannel> createMessageAcceptor(final ConnectionChannelThread thread, final OptionMap optionMap) {
        return new Acceptor<ConnectedMessageChannel>() {
            public IoFuture<ConnectedMessageChannel> acceptTo(final SocketAddress destination, final ChannelListener<? super ConnectedMessageChannel> openListener, final ChannelListener<? super BoundChannel> bindListener) {
                return acceptDatagram(destination, thread, openListener, bindListener, optionMap);
            }
        };
    }

    //==================================================
    //
    // UDP methods
    //
    //==================================================

    /**
     * Create a UDP server.  The UDP server can be configured to be multicast-capable; this should only be
     * done if multicast is needed, since some providers have a performance penalty associated with multicast.
     * The provider's default executor will be used to execute listener methods.
     *
     * @param bindAddress the bind address
     * @param readThread the initial read thread, or {@code null} for none
     * @param writeThread the initial write thread, or {@code null} for none
     * @param bindListener the initial open-connection listener
     * @param optionMap the initial configuration for the server
     * @return the UDP server channel
     * @throws IOException if the server could not be created
     *
     * @since 3.0
     */
    @SuppressWarnings({ "unused" })
    public MulticastMessageChannel createUdpServer(InetSocketAddress bindAddress, ReadChannelThread readThread, WriteChannelThread writeThread, ChannelListener<? super MulticastMessageChannel> bindListener, OptionMap optionMap) throws IOException {
        throw new UnsupportedOperationException("UDP Server");
    }

    /**
     * Create a UDP server.  The UDP server can be configured to be multicast-capable; this should only be
     * done if multicast is needed, since some providers have a performance penalty associated with multicast.
     * The provider's default executor will be used to execute listener methods.
     *
     * @param bindAddress the bind address
     * @param readThread the initial read thread, or {@code null} for none
     * @param writeThread the initial write thread, or {@code null} for none
     * @param optionMap the initial configuration for the server
     * @return the UDP server channel
     * @throws IOException if the server could not be created
     *
     * @since 3.0
     */
    @SuppressWarnings({ "unused" })
    public MulticastMessageChannel createUdpServer(InetSocketAddress bindAddress, ReadChannelThread readThread, WriteChannelThread writeThread, OptionMap optionMap) throws IOException {
        return createUdpServer(bindAddress, readThread, writeThread, ChannelListeners.nullChannelListener(), optionMap);
    }

    /**
     * Create a UDP server.  The UDP server can be configured to be multicast-capable; this should only be
     * done if multicast is needed, since some providers have a performance penalty associated with multicast.
     * The provider's default executor will be used to execute listener methods.
     *
     * @param bindAddress the bind address
     * @param readThread the initial read thread, or {@code null} for none
     * @param bindListener the initial open-connection listener
     * @param optionMap the initial configuration for the server
     * @throws IOException if the server could not be created
     *
     * @return the UDP server channel
     *
     * @since 3.0
     */
    @SuppressWarnings({ "unused" })
    public MulticastMessageChannel createUdpServer(InetSocketAddress bindAddress, ReadChannelThread readThread, ChannelListener<? super MulticastMessageChannel> bindListener, OptionMap optionMap) throws IOException {
        return createUdpServer(bindAddress, readThread, null, bindListener, optionMap);
    }

    /**
     * Create a UDP server.  The UDP server can be configured to be multicast-capable; this should only be
     * done if multicast is needed, since some providers have a performance penalty associated with multicast.
     * The provider's default executor will be used to execute listener methods.
     *
     * @param bindAddress the bind address
     * @param readThread the initial read thread, or {@code null} for none
     * @param optionMap the initial configuration for the server
     * @return the UDP server channel
     * @throws IOException if the server could not be created
     *
     * @since 3.0
     */
    @SuppressWarnings({ "unused" })
    public MulticastMessageChannel createUdpServer(InetSocketAddress bindAddress, ReadChannelThread readThread, OptionMap optionMap) throws IOException {
        return createUdpServer(bindAddress, readThread, null, ChannelListeners.nullChannelListener(), optionMap);
    }

    //==================================================
    //
    // Stream pipe methods
    //
    //==================================================

    /**
     * Create a pipe "server".  The provided open listener acts upon the server "end" of the
     * pipe. The returned channel source is used to establish connections to the server.
     *
     * @param readThread the initial read channel thread to use for this server's connections, or {@code null} for none
     * @param writeThread the initial write channel thread to use for this server's connections, or {@code null} for none
     * @param acceptListener the channel accept listener
     *
     * @return the client channel source
     *
     * @since 2.0
     */
    @SuppressWarnings({ "unused" })
    public ChannelSource<? extends StreamChannel> createPipeServer(ReadChannelThread readThread, WriteChannelThread writeThread, ChannelListener<? super SimpleAcceptingChannel<StreamChannel>> acceptListener) {
        throw new UnsupportedOperationException("Pipe Server");
    }

    /**
     * Create a one-way pipe "server".  The provided open listener acts upon the server "end" of the
     * the pipe. The returned channel source is used to establish connections to the server.  The data flows from the
     * server to the client.
     *
     * @param readThread the initial read channel thread to use for this server's connections, or {@code null} for none
     * @param acceptListener the channel accept listener
     *
     * @return the client channel source
     *
     * @since 2.0
     */
    @SuppressWarnings({ "unused" })
    public ChannelSource<? extends StreamSourceChannel> createPipeSourceServer(ReadChannelThread readThread, ChannelListener<? super SimpleAcceptingChannel<StreamSinkChannel>> acceptListener) {
        throw new UnsupportedOperationException("One-way Pipe Server");
    }

    /**
     * Create a one-way pipe "server".  The provided open listener acts upon the server "end" of the
     * the pipe. The returned channel source is used to establish connections to the server.  The data flows from the
     * client to the server.
     *
     * @param writeThread the initial write channel thread to use for this server's connections, or {@code null} for none
     * @param acceptListener the channel accept listener
     *
     * @return the client channel source
     *
     * @since 2.0
     */
    @SuppressWarnings({ "unused" })
    public ChannelSource<? extends StreamSinkChannel> createPipeSinkServer(WriteChannelThread writeThread, ChannelListener<? super SimpleAcceptingChannel<StreamSourceChannel>> acceptListener) {
        throw new UnsupportedOperationException("One-way Pipe Server");
    }

    //==================================================
    //
    // File system methods
    //
    //==================================================

    /**
     * Open a file on the filesystem.
     *
     * @param file the file to open
     * @param options the file-open options
     * @return the file channel
     * @throws IOException if an I/O error occurs
     */
    public FileChannel openFile(File file, OptionMap options) throws IOException {
        switch (options.get(Options.FILE_ACCESS, FileAccess.READ_WRITE)) {
            case READ_ONLY: return new XnioFileChannel(new RandomAccessFile(file, "r").getChannel());
            case READ_WRITE: return new XnioFileChannel(new RandomAccessFile(file, "rw").getChannel());
            default: throw new IllegalStateException();
        }
    }

    /**
     * Open a file on the filesystem.
     *
     * @param fileName the file name of the file to open
     * @param options the file-open options
     * @return the file channel
     * @throws IOException if an I/O error occurs
     */
    public FileChannel openFile(String fileName, OptionMap options) throws IOException {
        return openFile(new File(fileName), options);
    }

    /**
     * Open a file on the filesystem.
     *
     * @param file the file to open
     * @param access the file access level to use
     * @return the file channel
     * @throws IOException if an I/O error occurs
     */
    public FileChannel openFile(File file, FileAccess access) throws IOException {
        if (access == null) {
            throw new IllegalArgumentException("access is null");
        }
        return openFile(file, FILE_ACCESS_OPTION_MAPS.get(access));
    }

    /**
     * Open a file on the filesystem.
     *
     * @param fileName the file name of the file to open
     * @param access the file access level to use
     * @return the file channel
     * @throws IOException if an I/O error occurs
     */
    public FileChannel openFile(String fileName, FileAccess access) throws IOException {
        if (access == null) {
            throw new IllegalArgumentException("access is null");
        }
        return openFile(new File(fileName), FILE_ACCESS_OPTION_MAPS.get(access));
    }

    //==================================================
    //
    // General methods
    //
    //==================================================

    /**
     * Create a read channel thread.
     *
     * @param threadGroup the thread group to assign the new thread to, or {@code null} to choose one automatically
     * @param optionMap thread creation options
     * @return the read channel thread
     * @throws IOException if the thread could not be created
     */
    public abstract ReadChannelThread createReadChannelThread(ThreadGroup threadGroup, OptionMap optionMap) throws IOException;

    /**
     * Create a read channel thread.
     *
     * @param optionMap thread creation options
     * @return the read channel thread
     * @throws IOException if the thread could not be created
     */
    public final ReadChannelThread createReadChannelThread(OptionMap optionMap) throws IOException {
        return createReadChannelThread(null, optionMap);
    }

    /**
     * Create a read channel thread.
     *
     * @return the read channel thread
     * @throws IOException if the thread could not be created
     */
    public final ReadChannelThread createReadChannelThread() throws IOException {
        return createReadChannelThread(null, OptionMap.EMPTY);
    }

    /**
     * Create a write channel thread.
     *
     * @param threadGroup the thread group to assign the new thread to, or {@code null} to choose one automatically
     * @param optionMap thread creation options
     * @return the write channel thread
     * @throws IOException if the thread could not be created
     */
    public abstract WriteChannelThread createWriteChannelThread(ThreadGroup threadGroup, OptionMap optionMap) throws IOException;

    /**
     * Create a write channel thread.
     *
     * @param optionMap thread creation options
     * @return the write channel thread
     * @throws IOException if the thread could not be created
     */
    public final WriteChannelThread createWriteChannelThread(OptionMap optionMap) throws IOException {
        return createWriteChannelThread(null, optionMap);
    }

    /**
     * Create a write channel thread.
     *
     * @return the write channel thread
     * @throws IOException if the thread could not be created
     */
    public final WriteChannelThread createWriteChannelThread() throws IOException {
        return createWriteChannelThread(null, OptionMap.EMPTY);
    }

    /**
     * Get the name of this XNIO provider.
     *
     * @return the name
     */
    public final String getName() {
        return name;
    }

    /**
     * Get a string representation of this XNIO provider.
     *
     * @return the string representation
     */
    public final String toString() {
        return String.format("XNIO provider \"%s\" <%s@%s>", getName(), getClass().getName(), Integer.toHexString(hashCode()));
    }

    /**
     * Get an XNIO property.  The property name must start with {@code "xnio."}.
     *
     * @param name the property name
     * @return the property value, or {@code null} if it wasn't found
     * @since 1.2
     */
    protected String getProperty(final String name) {
        if (! name.startsWith("xnio.")) {
            throw new SecurityException("Not allowed to read non-XNIO properties");
        }
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            return AccessController.doPrivileged(new GetPropertyAction(name, null));
        } else {
            return System.getProperty(name);
        }
    }

    /**
     * Get an XNIO property.  The property name must start with {@code "xnio."}.
     *
     * @param name the property name
     * @param defaultValue the default value
     * @return the property value, or {@code defaultValue} if it wasn't found
     * @since 1.2
     */
    protected String getProperty(final String name, final String defaultValue) {
        if (! name.startsWith("xnio.")) {
            throw new SecurityException("Not allowed to read non-XNIO properties");
        }
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            return AccessController.doPrivileged(new GetPropertyAction(name, defaultValue));
        } else {
            return System.getProperty(name, defaultValue);
        }
    }

    private static final class GetPropertyAction implements PrivilegedAction<String> {
        private final String propertyName;
        private final String defaultValue;

        private GetPropertyAction(final String propertyName, final String defaultValue) {
            this.propertyName = propertyName;
            this.defaultValue = defaultValue;
        }

        public String run() {
            return System.getProperty(propertyName, defaultValue);
        }
    }
}
