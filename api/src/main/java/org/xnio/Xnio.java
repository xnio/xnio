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

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.FileChannel;
import java.security.AccessController;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import org.jboss.logging.Logger;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.BoundChannel;
import org.xnio.channels.ConnectedSslStreamChannel;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.channels.ConnectedMessageChannel;
import org.xnio.channels.MulticastMessageChannel;
import org.xnio.channels.StreamChannel;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.channels.UnsupportedOptionException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

/**
 * The XNIO provider class.
 *
 * @apiviz.landmark
 */
public abstract class Xnio {

    private static final InetSocketAddress ANY_INET_ADDRESS = new InetSocketAddress(0);
    private static final LocalSocketAddress ANY_LOCAL_ADDRESS = new LocalSocketAddress("");

    static {
        Logger.getLogger("org.xnio").info("XNIO Version " + Version.VERSION);
    }

    /**
     * Construct an XNIO provider instance.
     */
    protected Xnio() {
    }

    //==================================================
    //
    // SSL methods
    //
    //==================================================

    private static ConnectedSslStreamChannel createSslConnectedStreamChannel(final SSLContext sslContext, final ConnectedStreamChannel tcpChannel, final Executor executor, final OptionMap optionMap, final boolean server) {
        final InetSocketAddress peerAddress = tcpChannel.getPeerAddress(InetSocketAddress.class);
        final SSLEngine engine = sslContext.createSSLEngine(peerAddress.getHostName(), peerAddress.getPort());
        final boolean clientMode = optionMap.get(Options.SSL_USE_CLIENT_MODE, ! server);
        engine.setUseClientMode(clientMode);
        if (! clientMode) {
            final SslClientAuthMode clientAuthMode = optionMap.get(Options.SSL_CLIENT_AUTH_MODE);
            if (clientAuthMode != null) switch (clientAuthMode) {
                case NOT_REQUESTED:
                    engine.setNeedClientAuth(false);
                    engine.setWantClientAuth(false);
                    break;
                case REQUESTED:
                    engine.setWantClientAuth(true);
                    break;
                case REQUIRED:
                    engine.setNeedClientAuth(true);
                    break;
            }
        }
        engine.setEnableSessionCreation(optionMap.get(Options.SSL_ENABLE_SESSION_CREATION, true));
        final Sequence<String> cipherSuites = optionMap.get(Options.SSL_ENABLED_CIPHER_SUITES);
        if (cipherSuites != null) {
            final Set<String> supported = new HashSet<String>(Arrays.asList(engine.getSupportedCipherSuites()));
            final List<String> finalList = new ArrayList<String>();
            for (String name : cipherSuites) {
                if (supported.contains(name)) {
                    finalList.add(name);
                }
            }
            engine.setEnabledCipherSuites(finalList.toArray(new String[finalList.size()]));
        }
        final Sequence<String> protocols = optionMap.get(Options.SSL_ENABLED_PROTOCOLS);
        if (protocols != null) {
            final Set<String> supported = new HashSet<String>(Arrays.asList(engine.getSupportedProtocols()));
            final List<String> finalList = new ArrayList<String>();
            for (String name : protocols) {
                if (supported.contains(name)) {
                    finalList.add(name);
                }
            }
            engine.setEnabledProtocols(finalList.toArray(new String[finalList.size()]));
        }
        return new WrappingSslConnectedStreamChannel(tcpChannel, engine, executor);
    }

    /**
     * Create a bound TCP SSL server.  The given executor will be used to execute SSL tasks.
     *
     * @param executor the executor to use to execute SSL tasks
     * @param openListener the initial open-connection listener
     * @param bindAddress the address to bind to
     * @param optionMap the initial configuration for the server
     * @return the unbound TCP SSL server
     * @throws NoSuchProviderException if an SSL provider was selected which is not supported
     * @throws NoSuchAlgorithmException if an SSL algorithm was selected which is not supported
     *
     * @since 3.0
     */
    public AcceptingChannel<? extends ConnectedSslStreamChannel> createSslTcpServer(Executor executor, ChannelListener<? super ConnectedSslStreamChannel> openListener, InetSocketAddress bindAddress, OptionMap optionMap) throws NoSuchProviderException, NoSuchAlgorithmException {
        final SSLContext sslContext = getSSLContext(optionMap);
        final SslEngineSslTcpServer server = new SslEngineSslTcpServer(sslContext, createStreamServer(null, optionMap), executor, optionMap);
        if (openListener != null) server.getOpenSetter().set(openListener);
        return server;
    }

    /**
     * Create a bound TCP SSL server.  A direct executor will be used to execute SSL tasks.
     *
     * @param openListener the initial open-connection listener
     * @param bindAddress the address to bind to
     * @param optionMap the initial configuration for the server
     * @return the unbound TCP SSL server
     * @throws NoSuchProviderException if an SSL provider was selected which is not supported
     * @throws NoSuchAlgorithmException if an SSL algorithm was selected which is not supported
     *
     * @since 3.0
     */
    public AcceptingChannel<? extends ConnectedSslStreamChannel> createSslTcpServer(ChannelListener<? super ConnectedSslStreamChannel> openListener, InetSocketAddress bindAddress, OptionMap optionMap) throws NoSuchProviderException, NoSuchAlgorithmException {
        final SSLContext sslContext = getSSLContext(optionMap);
        final SslEngineSslTcpServer server = new SslEngineSslTcpServer(sslContext, createStreamServer(null, optionMap), IoUtils.directExecutor(), optionMap);
        if (openListener != null) server.getOpenSetter().set(openListener);
        return server;
    }

    /**
     * Create an SSL TCP connector.  The given executor will be used to execute SSL tasks.
     *
     * @param executor the executor to use to execute SSL tasks
     * @param src the source address for connections
     * @param optionMap the initial configuration for the connector
     * @return the SSL TCP connector
     * @throws NoSuchProviderException if an SSL provider was selected which is not supported
     * @throws NoSuchAlgorithmException if an SSL algorithm was selected which is not supported
     *
     * @since 2.1
     */
    public Connector<? extends ConnectedSslStreamChannel> createSslTcpConnector(final Executor executor, SocketAddress src, final OptionMap optionMap) throws NoSuchProviderException, NoSuchAlgorithmException {
        final SSLContext sslContext = getSSLContext(optionMap);
        final Connector<? extends ConnectedStreamChannel> connector = createTcpConnector(src, optionMap);
        return new Connector<ConnectedSslStreamChannel>() {
            @SuppressWarnings({ "deprecation" })
            public IoFuture<ConnectedSslStreamChannel> connectTo(final SocketAddress destination, final ChannelListener<? super ConnectedSslStreamChannel> openListener, final ChannelListener<? super BoundChannel> bindListener) {
                final FutureResult<ConnectedSslStreamChannel> futureResult = new FutureResult<ConnectedSslStreamChannel>(IoUtils.directExecutor());
                connector.connectTo(destination, new ChannelListener<ConnectedStreamChannel>() {
                    public void handleEvent(final ConnectedStreamChannel tcpChannel) {
                        final ConnectedSslStreamChannel channel = createSslConnectedStreamChannel(sslContext, tcpChannel, executor, optionMap, false);
                        futureResult.setResult(channel);
                        IoUtils.invokeChannelListener(channel, openListener);
                    }
                }, bindListener).addNotifier(
                    new IoFuture.HandlingNotifier<ConnectedStreamChannel, FutureResult<ConnectedSslStreamChannel>>() {
                        public void handleCancelled(final FutureResult<ConnectedSslStreamChannel> result) {
                            result.setCancelled();
                        }

                        public void handleFailed(final IOException exception, final FutureResult<ConnectedSslStreamChannel> result) {
                            result.setException(exception);
                        }
                    }, futureResult);
                return futureResult.getIoFuture();
            }

            public ChannelSource<ConnectedSslStreamChannel> createChannelSource(final SocketAddress destination) {
                return new ChannelSource<ConnectedSslStreamChannel>() {
                    public IoFuture<? extends ConnectedSslStreamChannel> open(final ChannelListener<? super ConnectedSslStreamChannel> openListener) {
                        return connectTo(destination, openListener, null);
                    }
                };
            }
        };
    }

    /**
     * Create an SSL TCP connector.  A direct executor will be used to execute SSL tasks.
     *
     * @param src the source address for connections
     * @param optionMap the initial configuration for the connector
     * @return the SSL TCP connector
     * @throws NoSuchProviderException if an SSL provider was selected which is not supported
     * @throws NoSuchAlgorithmException if an SSL algorithm was selected which is not supported
     *
     * @since 2.1
     */
    public Connector<? extends ConnectedSslStreamChannel> createSslTcpConnector(SocketAddress src, final OptionMap optionMap) throws NoSuchProviderException, NoSuchAlgorithmException {
        return createSslTcpConnector(IoUtils.directExecutor(), src, optionMap);
    }

    /**
     * Create an SSL TCP connector.  The provider's default executor will be used to execute listener methods.
     *
     * @param optionMap the initial configuration for the connector
     * @return the SSL TCP connector
     * @throws NoSuchProviderException if an SSL provider was selected which is not supported
     * @throws NoSuchAlgorithmException if an SSL algorithm was selected which is not supported
     *
     * @since 2.1
     */
    public Connector<? extends ConnectedSslStreamChannel> createSslTcpConnector(final OptionMap optionMap) throws NoSuchProviderException, NoSuchAlgorithmException {
        return createSslTcpConnector(IoUtils.directExecutor(), null, optionMap);
    }

    private SSLContext getSSLContext(final OptionMap optionMap) throws NoSuchAlgorithmException, NoSuchProviderException {
        final String provider = optionMap.get(Options.SSL_PROVIDER);
        final String protocol = optionMap.get(Options.SSL_PROTOCOL);
        final SSLContext sslContext;
        if (protocol == null) {
            sslContext = SSLContext.getDefault();
        } else if (provider == null) {
            sslContext = SSLContext.getInstance(protocol);
        } else {
            sslContext = SSLContext.getInstance(protocol, provider);
        }
        return sslContext;
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
     *
     * @param bindAddress the address to bind to
     * @param openListener the initial open-connection listener
     * @param optionMap the initial configuration for the server
     * @return the acceptor
     *
     * @since 2.0
     */
    public AcceptingChannel<? extends ConnectedStreamChannel> createStreamServer(SocketAddress bindAddress, ChannelListener<? super ConnectedStreamChannel> openListener, OptionMap optionMap) {
        if (bindAddress == null) {
            throw new IllegalArgumentException("bindAddress is null");
        }
        if (bindAddress instanceof InetSocketAddress) {
            return createTcpServer((InetSocketAddress) bindAddress, openListener, optionMap);
        } else if (bindAddress instanceof LocalSocketAddress) {
            return createLocalStreamServer((LocalSocketAddress) bindAddress, openListener, optionMap);
        } else {
            throw new UnsupportedOperationException("Unsupported socket address " + bindAddress.getClass());
        }
    }

    /**
     * Implementation helper method to create a TCP stream server.
     *
     *
     * @param bindAddress the address to bind to
     * @param openListener the initial open-connection listener
     * @param optionMap the initial configuration for the server
     * @return the acceptor
     *
     * @since 3.0
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    protected AcceptingChannel<? extends ConnectedStreamChannel> createTcpServer(InetSocketAddress bindAddress, ChannelListener<? super ConnectedStreamChannel> openListener, OptionMap optionMap) {
        throw new UnsupportedOperationException("TCP server");
    }

    /**
     * Implementation helper method to create a UNIX domain stream server.
     *
     *
     * @param bindAddress the address to bind to
     * @param openListener the initial open-connection listener
     * @param optionMap the initial configuration for the server
     * @return the acceptor
     *
     * @since 3.0
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    protected AcceptingChannel<? extends ConnectedStreamChannel> createLocalStreamServer(LocalSocketAddress bindAddress, ChannelListener<? super ConnectedStreamChannel> openListener, OptionMap optionMap) {
        throw new UnsupportedOperationException("UNIX stream server");
    }

    // Connectors

    /**
     * Connect to a remote stream server.  The protocol family is determined by the type of the socket address given.
     *
     * @param destination the destination address
     * @param openListener the listener which will be notified when the channel is open, or {@code null} for none
     * @param bindListener the listener which will be notified when the channel is bound, or {@code null} for none
     * @param optionMap the option map
     * @return the future result of this operation
     */
    public IoFuture<? extends ConnectedStreamChannel> connectStream(SocketAddress destination, ChannelListener<? super ConnectedStreamChannel> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        if (destination == null) {
            throw new IllegalArgumentException("destination is null");
        }
        if (destination instanceof InetSocketAddress) {
            return connectTcp(ANY_INET_ADDRESS, (InetSocketAddress) destination, openListener, bindListener, optionMap);
        } else if (destination instanceof LocalSocketAddress) {
            return connectStreamLocal(ANY_LOCAL_ADDRESS, (LocalSocketAddress) destination, openListener, bindListener, optionMap);
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
     * @param openListener the listener which will be notified when the channel is open, or {@code null} for none
     * @param bindListener the listener which will be notified when the channel is bound, or {@code null} for none
     * @param optionMap the option map
     * @return the future result of this operation
     */
    public IoFuture<? extends ConnectedStreamChannel> connectStream(SocketAddress bindAddress, SocketAddress destination, ChannelListener<? super ConnectedStreamChannel> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
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
            return connectTcp((InetSocketAddress) bindAddress, (InetSocketAddress) destination, openListener, bindListener, optionMap);
        } else if (destination instanceof LocalSocketAddress) {
            return connectStreamLocal((LocalSocketAddress) bindAddress, (LocalSocketAddress) destination, openListener, bindListener, optionMap);
        } else {
            throw new UnsupportedOperationException("Connect to stream server with socket address " + destination.getClass());
        }
    }

    /**
     * Implementation helper method to connect to a TCP server.
     *
     * @param bindAddress the bind address
     * @param destinationAddress the destination address
     * @param openListener the listener which will be notified when the channel is open, or {@code null} for none
     * @param bindListener the listener which will be notified when the channel is bound, or {@code null} for none
     * @param optionMap the option map
     * @return the future result of this operation
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    protected IoFuture<? extends ConnectedStreamChannel> connectTcp(InetSocketAddress bindAddress, InetSocketAddress destinationAddress, ChannelListener<? super ConnectedStreamChannel> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        throw new UnsupportedOperationException("Connect to TCP server");
    }

    /**
     * Implementation helper method to connect to a local (UNIX domain) server.
     *
     * @param bindAddress the bind address
     * @param destinationAddress the destination address
     * @param openListener the listener which will be notified when the channel is open, or {@code null} for none
     * @param bindListener the listener which will be notified when the channel is bound, or {@code null} for none
     * @param optionMap the option map
     * @return the future result of this operation
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    protected IoFuture<? extends ConnectedStreamChannel> connectStreamLocal(LocalSocketAddress bindAddress, LocalSocketAddress destinationAddress, ChannelListener<? super ConnectedStreamChannel> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        throw new UnsupportedOperationException("Connect to local stream server");
    }

    /**
     * Create a connector which can be used by applications which need to establish connections without having access
     * to the XNIO provider instance.
     *
     * @param bindAddress the bind address
     * @param optionMap the option map
     * @return the new connector
     */
    public Connector<? extends ConnectedStreamChannel> createStreamConnector(final SocketAddress bindAddress, final OptionMap optionMap) {
        return new Connector<ConnectedStreamChannel>() {
            public IoFuture<? extends ConnectedStreamChannel> connectTo(final SocketAddress destination, final ChannelListener<? super ConnectedStreamChannel> openListener, final ChannelListener<? super BoundChannel> bindListener) {
                return connectStream(bindAddress, destination, openListener, bindListener, optionMap);
            }

            public ChannelSource<ConnectedStreamChannel> createChannelSource(final SocketAddress destination) {
                return new ChannelSource<ConnectedStreamChannel>() {
                    public IoFuture<? extends ConnectedStreamChannel> open(final ChannelListener<? super ConnectedStreamChannel> openListener) {
                        return connectStream(bindAddress, destination, openListener, null, optionMap);
                    }
                };
            }
        };
    }

    // Acceptors

    /**
     * Accept a stream connection at a destination address.  If a wildcard address is specified, then a destination address
     * is chosen in a manner specific to the OS and/or channel type.
     *
     * @param destination the destination (bind) address
     * @param openListener the listener which will be notified when the channel is open, or {@code null} for none
     * @param bindListener the listener which will be notified when the acceptor is bound, or {@code null} for none
     * @param optionMap the option map
     * @return the future connection
     */
    public IoFuture<? extends ConnectedStreamChannel> acceptStream(SocketAddress destination, ChannelListener<? super ConnectedStreamChannel> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        if (destination == null) {
            throw new IllegalArgumentException("destination is null");
        }
        if (destination instanceof InetSocketAddress) {
            return acceptTcp((InetSocketAddress) destination, openListener, bindListener, optionMap);
        } else if (destination instanceof LocalSocketAddress) {
            return acceptStreamLocal((LocalSocketAddress) destination, openListener, bindListener, optionMap);
        } else {
            throw new UnsupportedOperationException("Accept a connection to socket address " + destination.getClass());
        }
    }

    /**
     * Implementation helper method to accept a local (UNIX domain) stream connection.
     *
     * @param destination the destination (bind) address
     * @param openListener the listener which will be notified when the channel is open, or {@code null} for none
     * @param bindListener the listener which will be notified when the acceptor is bound, or {@code null} for none
     * @param optionMap the option map
     *
     * @return the future connection
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    protected IoFuture<? extends ConnectedStreamChannel> acceptStreamLocal(final LocalSocketAddress destination, final ChannelListener<? super ConnectedStreamChannel> openListener, final ChannelListener<? super BoundChannel> bindListener, final OptionMap optionMap) {
        throw new UnsupportedOptionException("Accept a local stream connection");
    }

    /**
     * Implementation helper method to accept a TCP connection.
     *
     * @param destination the destination (bind) address
     * @param openListener the listener which will be notified when the channel is open, or {@code null} for none
     * @param bindListener the listener which will be notified when the acceptor is bound, or {@code null} for none
     * @param optionMap the option map
     *
     * @return the future connection
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    protected IoFuture<? extends ConnectedStreamChannel> acceptTcp(final InetSocketAddress destination, final ChannelListener<? super ConnectedStreamChannel> openListener, final ChannelListener<? super BoundChannel> bindListener, final OptionMap optionMap) {
        throw new UnsupportedOptionException("Accept a TCP connection");
    }

    /**
     * Create an acceptor which can be used by applications which need to accept connections without having access
     * to the XNIO provider instance.
     *
     * @param optionMap the option map
     * @return the new connector
     */
    public Acceptor<? extends ConnectedStreamChannel> createStreamAcceptor(final OptionMap optionMap) {
        return new Acceptor<ConnectedStreamChannel>() {
            public IoFuture<? extends ConnectedStreamChannel> acceptTo(final SocketAddress destination, final ChannelListener<? super ConnectedStreamChannel> openListener, final ChannelListener<? super BoundChannel> bindListener) {
                return acceptStream(destination, openListener, bindListener, optionMap);
            }

            public ChannelDestination<ConnectedStreamChannel> createChannelDestination(final SocketAddress destination) {
                return new ChannelDestination<ConnectedStreamChannel>() {
                    public IoFuture<? extends ConnectedStreamChannel> accept(final ChannelListener<? super ConnectedStreamChannel> openListener, final ChannelListener<? super BoundChannel> bindListener) {
                        return acceptStream(destination, openListener, bindListener, optionMap);
                    }
                };
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
     * @param openListener the listener which will be notified when the channel is open, or {@code null} for none
     * @param bindListener the listener which will be notified when the channel is bound, or {@code null} for none
     * @param optionMap the option map
     * @return the future result of this operation
     */
    public IoFuture<? extends ConnectedMessageChannel> connectDatagram(SocketAddress destination, ChannelListener<? super ConnectedMessageChannel> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        if (destination == null) {
            throw new IllegalArgumentException("destination is null");
        }
        if (destination instanceof InetSocketAddress) {
            return connectUdp(ANY_INET_ADDRESS, (InetSocketAddress) destination, openListener, bindListener, optionMap);
        } else if (destination instanceof LocalSocketAddress) {
            return connectDatagramLocal(ANY_LOCAL_ADDRESS, (LocalSocketAddress) destination, openListener, bindListener, optionMap);
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
     * @param openListener the listener which will be notified when the channel is open, or {@code null} for none
     * @param bindListener the listener which will be notified when the channel is bound, or {@code null} for none
     * @param optionMap the option map
     * @return the future result of this operation
     */
    public IoFuture<? extends ConnectedMessageChannel> connectDatagram(SocketAddress bindAddress, SocketAddress destination, ChannelListener<? super ConnectedMessageChannel> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
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
            return connectUdp((InetSocketAddress) bindAddress, (InetSocketAddress) destination, openListener, bindListener, optionMap);
        } else if (destination instanceof LocalSocketAddress) {
            return connectDatagramLocal((LocalSocketAddress) bindAddress, (LocalSocketAddress) destination, openListener, bindListener, optionMap);
        } else {
            throw new UnsupportedOperationException("Connect to server with socket address " + destination.getClass());
        }
    }

    /**
     * Implementation helper method to connect to a UDP server.
     *
     * @param bindAddress the bind address
     * @param destination the destination address
     * @param openListener the listener which will be notified when the channel is open, or {@code null} for none
     * @param bindListener the listener which will be notified when the channel is bound, or {@code null} for none
     * @param optionMap the option map
     * @return the future result of this operation
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    protected IoFuture<? extends ConnectedMessageChannel> connectUdp(InetSocketAddress bindAddress, InetSocketAddress destination, ChannelListener<? super ConnectedMessageChannel> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        throw new UnsupportedOperationException("Connect to UDP server");
    }

    /**
     * Implementation helper method to connect to a local (UNIX domain) datagram server.
     *
     * @param bindAddress the bind address
     * @param destination the destination address
     * @param openListener the listener which will be notified when the channel is open, or {@code null} for none
     * @param bindListener the listener which will be notified when the channel is bound, or {@code null} for none
     * @param optionMap the option map
     * @return the future result of this operation
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    protected IoFuture<? extends ConnectedMessageChannel> connectDatagramLocal(LocalSocketAddress bindAddress, LocalSocketAddress destination, ChannelListener<? super ConnectedMessageChannel> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        throw new UnsupportedOperationException("Connect to local datagram server");
    }

    /**
     * Create a connector which can be used by applications which need to establish connections without having access
     * to the XNIO provider instance.
     *
     * @param bindAddress the bind address
     * @param optionMap the option map
     * @return the new connector
     */
    public Connector<? extends ConnectedMessageChannel> createDatagramConnector(final SocketAddress bindAddress, final OptionMap optionMap) {
        return new Connector<ConnectedMessageChannel>() {
            public IoFuture<? extends ConnectedMessageChannel> connectTo(final SocketAddress destination, final ChannelListener<? super ConnectedMessageChannel> openListener, final ChannelListener<? super BoundChannel> bindListener) {
                return connectDatagram(bindAddress, destination, openListener, bindListener, optionMap);
            }

            public ChannelSource<ConnectedMessageChannel> createChannelSource(final SocketAddress destination) {
                return new ChannelSource<ConnectedMessageChannel>() {
                    public IoFuture<? extends ConnectedMessageChannel> open(final ChannelListener<? super ConnectedMessageChannel> openListener) {
                        return connectDatagram(bindAddress, destination, openListener, null, optionMap);
                    }
                };
            }
        };
    }

    // Acceptors

    /**
     * Accept a message connection at a destination address.  If a wildcard address is specified, then a destination address
     * is chosen in a manner specific to the OS and/or channel type.
     *
     * @param destination the destination (bind) address
     * @param openListener the listener which will be notified when the channel is open, or {@code null} for none
     * @param bindListener the listener which will be notified when the acceptor is bound, or {@code null} for none
     * @param optionMap the option map
     * @return the future connection
     */
    public IoFuture<? extends ConnectedMessageChannel> acceptDatagram(SocketAddress destination, ChannelListener<? super ConnectedMessageChannel> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        if (destination == null) {
            throw new IllegalArgumentException("destination is null");
        }
        if (destination instanceof LocalSocketAddress) {
            return acceptDatagramLocal((LocalSocketAddress) destination, openListener, bindListener, optionMap);
        } else {
            throw new UnsupportedOperationException("Accept a connection to socket address " + destination.getClass());
        }
    }

    /**
     * Implementation helper method to accept a local (UNIX domain) datagram connection.
     *
     * @param destination the destination (bind) address
     * @param openListener the listener which will be notified when the channel is open, or {@code null} for none
     * @param bindListener the listener which will be notified when the acceptor is bound, or {@code null} for none
     * @param optionMap the option map
     *
     * @return the future connection
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    protected IoFuture<? extends ConnectedMessageChannel> acceptDatagramLocal(final LocalSocketAddress destination, final ChannelListener<? super ConnectedMessageChannel> openListener, final ChannelListener<? super BoundChannel> bindListener, final OptionMap optionMap) {
        throw new UnsupportedOptionException("Accept a local message connection");
    }

    /**
     * Create an acceptor which can be used by applications which need to accept connections without having access
     * to the XNIO provider instance.
     *
     * @param optionMap the option map
     * @return the new connector
     */
    public Acceptor<? extends ConnectedMessageChannel> createMessageAcceptor(final OptionMap optionMap) {
        return new Acceptor<ConnectedMessageChannel>() {
            public IoFuture<? extends ConnectedMessageChannel> acceptTo(final SocketAddress destination, final ChannelListener<? super ConnectedMessageChannel> openListener, final ChannelListener<? super BoundChannel> bindListener) {
                return acceptDatagram(destination, openListener, bindListener, optionMap);
            }

            public ChannelDestination<ConnectedMessageChannel> createChannelDestination(final SocketAddress destination) {
                return new ChannelDestination<ConnectedMessageChannel>() {
                    public IoFuture<? extends ConnectedMessageChannel> accept(final ChannelListener<? super ConnectedMessageChannel> openListener, final ChannelListener<? super BoundChannel> bindListener) {
                        return acceptDatagram(destination, openListener, bindListener, optionMap);
                    }
                };
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
     * @param bindListener the initial open-connection listener
     * @param optionMap the initial configuration for the server
     *
     * @return a factory that can be used to configure the new UDP server
     *
     * @since 2.0
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    public MulticastMessageChannel createUdpServer(SocketAddress bindAddress, ChannelListener<? super MulticastMessageChannel> bindListener, OptionMap optionMap) {
        throw new UnsupportedOperationException("UDP Server");
    }

    /**
     * Create a UDP server.  The UDP server can be configured to be multicast-capable; this should only be
     * done if multicast is needed, since some providers have a performance penalty associated with multicast.
     * The provider's default executor will be used to execute listener methods.
     *
     * @param bindListener the initial open-connection listener
     * @param optionMap the initial configuration for the server
     *
     * @return a factory that can be used to configure the new UDP server
     *
     * @since 2.0
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    public MulticastMessageChannel createUdpServer(ChannelListener<? super MulticastMessageChannel> bindListener, OptionMap optionMap) {
        return createUdpServer(ANY_INET_ADDRESS, bindListener, optionMap);
    }

    /**
     * Create a UDP server.  The UDP server can be configured to be multicast-capable; this should only be
     * done if multicast is needed, since some providers have a performance penalty associated with multicast.
     * The provider's default executor will be used to execute listener methods.
     *
     * @param bindAddress the bind address
     * @param optionMap the initial configuration for the server
     *
     * @return a factory that can be used to configure the new UDP server
     *
     * @since 2.0
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    public MulticastMessageChannel createUdpServer(SocketAddress bindAddress, OptionMap optionMap) {
        return createUdpServer(bindAddress, IoUtils.nullChannelListener(), optionMap);
    }

    /**
     * Create a UDP server.  The UDP server can be configured to be multicast-capable; this should only be
     * done if multicast is needed, since some providers have a performance penalty associated with multicast.
     * The provider's default executor will be used to execute listener methods.
     *
     * @param optionMap the initial configuration for the server
     *
     * @return a factory that can be used to configure the new UDP server
     *
     * @since 2.0
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    public MulticastMessageChannel createUdpServer(OptionMap optionMap) {
        return createUdpServer(ANY_INET_ADDRESS, IoUtils.nullChannelListener(), optionMap);
    }

    //==================================================
    //
    // Stream pipe methods
    //
    //==================================================

    /**
     * Create a pipe "server".  The provided open listener acts upon the server "end" of the
     * pipe. The returned channel source is used to establish connections to the server.  The provider's default executor will be used to
     * execute listener methods.
     *
     * @param openListener the initial open-connection listener
     *
     * @return the client channel source
     *
     * @since 2.0
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    public ChannelSource<? extends StreamChannel> createPipeServer(ChannelListener<? super StreamChannel> openListener) {
        throw new UnsupportedOperationException("Pipe Server");
    }

    /**
     * Create a one-way pipe "server".  The provided open listener acts upon the server "end" of the
     * the pipe. The returned channel source is used to establish connections to the server.  The data flows from the
     * server to the client.  The provider's default executor will be used to
     * execute listener methods.
     *
     * @param openListener the initial open-connection listener
     *
     * @return the client channel source
     *
     * @since 2.0
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    public ChannelSource<? extends StreamSourceChannel> createPipeSourceServer(ChannelListener<? super StreamSinkChannel> openListener) {
        throw new UnsupportedOperationException("One-way Pipe Server");
    }

    /**
     * Create a one-way pipe "server".  The provided open listener acts upon the server "end" of the
     * the pipe. The returned channel source is used to establish connections to the server.  The data flows from the
     * client to the server.  The provider's default executor will be used to
     * execute listener methods.
     *
     * @param openListener the initial open-connection listener
     *
     * @return the client channel source
     *
     * @since 2.0
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    public ChannelSource<? extends StreamSinkChannel> createPipeSinkServer(ChannelListener<? super StreamSourceChannel> openListener) {
        throw new UnsupportedOperationException("One-way Pipe Server");
    }

    /**
     * Create a single pipe connection.  The provider's default executor will be used to
     * execute listener methods.
     *
     * @param leftListener the listener for the "left" side of the pipe
     * @param rightListener the listener for the "right" side of the pipe
     *
     * @return the future connection
     *
     * @since 2.0
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    public IoFuture<? extends Closeable> createPipeConnection(ChannelListener<? super StreamChannel> leftListener, ChannelListener<? super StreamChannel> rightListener) {
        throw new UnsupportedOperationException("Pipe Connection");
    }

    /**
     * Create a single one-way pipe connection.  The provider's default executor will be used to
     * execute listener methods.
     *
     * @param sourceListener the listener for the "source" side of the pipe
     * @param sinkListener the listener for the "sink" side of the pipe
     *
     * @return the future connection
     *
     * @since 2.0
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    public IoFuture<? extends Closeable> createOneWayPipeConnection(ChannelListener<? super StreamSourceChannel> sourceListener, ChannelListener<? super StreamSinkChannel> sinkListener) {
        throw new UnsupportedOperationException("One-way Pipe Connection");
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
            case READ_ONLY: return new RandomAccessFile(file, "r").getChannel();
            case READ_WRITE: return new RandomAccessFile(file, "rw").getChannel();
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
        return openFile(file, OptionMap.builder().set(Options.FILE_ACCESS, access).getMap());
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
        return openFile(new File(fileName), OptionMap.builder().set(Options.FILE_ACCESS, access).getMap());
    }

    //==================================================
    //
    // General methods
    //
    //==================================================

    /**
     * Get the name of this XNIO instance.
     *
     * @return the name
     */
    public abstract String getName();

    /**
     * Get a string representation of this XNIO instance.
     *
     * @return the string representation
     */
    public String toString() {
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
