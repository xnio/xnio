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

package org.jboss.xnio;

import java.io.Closeable;
import java.io.IOException;
import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jboss.xnio.channels.ConnectedStreamChannel;
import org.jboss.xnio.channels.UnsupportedOptionException;
import org.jboss.xnio.channels.Configurable;
import org.jboss.xnio.channels.MulticastDatagramChannel;
import org.jboss.xnio.core.nio.NioProvider;
import org.jboss.xnio.spi.Provider;
import org.jboss.xnio.spi.TcpServerService;
import org.jboss.xnio.spi.TcpConnectorService;
import org.jboss.xnio.spi.Lifecycle;
import org.jboss.xnio.spi.UdpServerService;

/**
 * An XNIO provider for a standalone application.
 */
public final class Xnio implements Closeable {
    private final Provider provider;

    private final AtomicBoolean closed = new AtomicBoolean(false);

    private Xnio(Provider provider) {
        this.provider = provider;
    }

    /**
     * Create an NIO-based XNIO provider.  A direct executor is used for the handlers; the provider will create its own
     * selector threads, of which there will be one reader thread, one writer thread, and one connect/accept thread.
     *
     * @return a new provider
     * @throws IOException if an I/o error occurs while starting the service
     */
    public static Xnio createNio() throws IOException {
        return createNio(1, 1, 1);
    }

    /**
     * Create an NIO-based XNIO provider.  A direct executor is used for the handlers; the provider will
     * create its own selector threads.
     *
     * @param readSelectorThreads the number of threads to assign for readable events
     * @param writeSelectorThreads the number of threads to assign for writable events
     * @param connectSelectorThreads the number of threads to assign for connect/accept events
     * @return a new provider
     * @throws IOException if an I/O error occurs while starting the service
     * @throws IllegalArgumentException if a given argument is not valid
     */
    public static Xnio createNio(final int readSelectorThreads, final int writeSelectorThreads, final int connectSelectorThreads) throws IOException, IllegalArgumentException {
        final NioProvider nioProvider = new NioProvider();
        nioProvider.setExecutor(IoUtils.directExecutor());
        nioProvider.setReadSelectorThreads(readSelectorThreads);
        nioProvider.setWriteSelectorThreads(writeSelectorThreads);
        nioProvider.setConnectionSelectorThreads(connectSelectorThreads);
        nioProvider.start();
        return new Xnio(nioProvider);
    }

    /**
     * Create an NIO-based XNIO provider.  The given handler executor is used for the handlers; the provider will
     * create its own selector threads.
     *
     * @param handlerExecutor the executor to use to handle events
     * @param readSelectorThreads the number of threads to assign for readable events
     * @param writeSelectorThreads the number of threads to assign for writable events
     * @param connectSelectorThreads the number of threads to assign for connect/accept events
     * @return a new provider
     * @throws IOException if an I/O error occurs while starting the service
     * @throws IllegalArgumentException if a given argument is not valid
     */
    public static Xnio createNio(Executor handlerExecutor, final int readSelectorThreads, final int writeSelectorThreads, final int connectSelectorThreads) throws IOException, IllegalArgumentException {
        final NioProvider nioProvider = new NioProvider();
        nioProvider.setExecutor(handlerExecutor);
        nioProvider.setReadSelectorThreads(readSelectorThreads);
        nioProvider.setWriteSelectorThreads(writeSelectorThreads);
        nioProvider.setConnectionSelectorThreads(connectSelectorThreads);
        nioProvider.start();
        return new Xnio(nioProvider);
    }

    /**
     * Create an NIO-based XNIO provider.  The given handler executor is used for the handlers; the given thread
     * factory is used to create selector threads.
     *
     * @param handlerExecutor the executor to use to handle events
     * @param selectorThreadFactory the selector thread factory to use
     * @param readSelectorThreads the number of threads to assign for readable events
     * @param writeSelectorThreads the number of threads to assign for writable events
     * @param connectSelectorThreads the number of threads to assign for connect/accept events
     * @return a new provider
     * @throws IOException
     * @throws IllegalArgumentException
     */
    public static Xnio createNio(Executor handlerExecutor, ThreadFactory selectorThreadFactory, final int readSelectorThreads, final int writeSelectorThreads, final int connectSelectorThreads) throws IOException, IllegalArgumentException {
        final NioProvider nioProvider = new NioProvider();
        nioProvider.setExecutor(handlerExecutor);
        nioProvider.setSelectorThreadFactory(selectorThreadFactory);
        nioProvider.setReadSelectorThreads(readSelectorThreads);
        nioProvider.setWriteSelectorThreads(writeSelectorThreads);
        nioProvider.setConnectionSelectorThreads(connectSelectorThreads);
        nioProvider.start();
        return new Xnio(nioProvider);
    }

    /**
     * Create a TCP server.  The server will bind to the given addresses.
     *
     * @param executor the executor to use to execute the handlers
     * @param handlerFactory the factory which will produce handlers for inbound connections
     * @param bindAddresses the addresses to bind to
     * @return a factory that can be used to configure the new TCP server
     */
    public ConfigurableFactory<Closeable> createTcpServer(final Executor executor, final IoHandlerFactory<? super ConnectedStreamChannel<SocketAddress>> handlerFactory, SocketAddress... bindAddresses) {
        if (executor == null) {
            throw new NullPointerException("executor is null");
        }
        if (handlerFactory == null) {
            throw new NullPointerException("handlerFactory is null");
        }
        if (bindAddresses == null) {
            throw new NullPointerException("bindAddresses is null");
        }
        if (bindAddresses.length == 0) {
            throw new IllegalArgumentException("no bind addresses specified");
        }
        if (closed.get()) {
            throw new IllegalStateException("XNIO provider not open");
        }
        final TcpServerService tcpServerService = provider.createTcpServer();
        if (executor != null) tcpServerService.setExecutor(executor);
        tcpServerService.setBindAddresses(bindAddresses);
        tcpServerService.setHandlerFactory(handlerFactory);
        final AtomicBoolean started = new AtomicBoolean(false);
        final AtomicBoolean stopped = new AtomicBoolean(false);
        return new SimpleConfigurableFactory<Closeable, TcpServerService>(tcpServerService, started, new LifecycleCloseable(tcpServerService, stopped));
    }

    /**
     * Create a TCP server.  The server will bind to the given addresses.  The provider's executor will be used
     * to execute handler methods.
     *
     * @param handlerFactory the factory which will produce handlers for inbound connections
     * @param bindAddresses the addresses to bind to
     * @return a factory that can be used to configure the new TCP server
     */
    public ConfigurableFactory<Closeable> createTcpServer(final IoHandlerFactory<? super ConnectedStreamChannel<SocketAddress>> handlerFactory, SocketAddress... bindAddresses) {
        if (handlerFactory == null) {
            throw new NullPointerException("handlerFactory is null");
        }
        if (bindAddresses == null) {
            throw new NullPointerException("bindAddresses is null");
        }
        if (bindAddresses.length == 0) {
            throw new IllegalArgumentException("no bind addresses specified");
        }
        if (closed.get()) {
            throw new IllegalStateException("XNIO provider not open");
        }
        final TcpServerService tcpServerService = provider.createTcpServer();
        tcpServerService.setBindAddresses(bindAddresses);
        tcpServerService.setHandlerFactory(handlerFactory);
        final AtomicBoolean started = new AtomicBoolean(false);
        final AtomicBoolean stopped = new AtomicBoolean(false);
        return new SimpleConfigurableFactory<Closeable, TcpServerService>(tcpServerService, started, new LifecycleCloseable(tcpServerService, stopped));
    }

    /**
     * Create a configurable TCP connector.  The connector can be configured before it is actually created.
     *
     * @param executor the executor to use to execute the handlers
     * @return a factory that can be used to configure the new TCP connector
     */
    public ConfigurableFactory<TcpConnector> createTcpConnector(final Executor executor) {
        if (executor == null) {
            throw new NullPointerException("executor is null");
        }
        if (closed.get()) {
            throw new IllegalStateException("XNIO provider not open");
        }
        final TcpConnectorService connectorService = provider.createTcpConnector();
        connectorService.setExecutor(executor);
        final AtomicBoolean started = new AtomicBoolean(false);
        final AtomicBoolean stopped = new AtomicBoolean(false);
        return new SimpleConfigurableFactory<TcpConnector, TcpConnectorService>(connectorService, started, new LifecycleConnector(connectorService, stopped));
    }

    /**
     * Create a configurable TCP connector.  The connector can be configured before it is actually created.  The
     * provider's executor will be used to execute handler methods.
     *
     * @return a factory that can be used to configure the new TCP connector
     */
    public ConfigurableFactory<TcpConnector> createTcpConnector() {
        if (closed.get()) {
            throw new IllegalStateException("XNIO provider not open");
        }
        final TcpConnectorService connectorService = provider.createTcpConnector();
        final AtomicBoolean started = new AtomicBoolean(false);
        final AtomicBoolean stopped = new AtomicBoolean(false);
        return new SimpleConfigurableFactory<TcpConnector, TcpConnectorService>(connectorService, started, new LifecycleConnector(connectorService, stopped));
    }

    /**
     * Create a UDP server.  The server will bind to the given addresses.  The UDP server can be configured to be
     * multicast-capable; this should only be done if multicast is needed, since some providers have a performance
     * penalty associated with multicast.
     *
     * @param multicast {@code true} if the UDP server should be multicast-capable
     * @param executor the executor to use to execute the handlers
     * @param handlerFactory the factory which will produce handlers for each channel
     * @param bindAddresses the addresses to bind
     * @return a factory that can be used to configure the new UDP server
     */
    public ConfigurableFactory<Closeable> createUdpServer(final Executor executor, final boolean multicast, final IoHandlerFactory<? super MulticastDatagramChannel> handlerFactory, SocketAddress... bindAddresses) {
        if (executor == null) {
            throw new NullPointerException("executor is null");
        }
        if (handlerFactory == null) {
            throw new NullPointerException("handlerFactory is null");
        }
        if (bindAddresses == null) {
            throw new NullPointerException("bindAddresses is null");
        }
        if (bindAddresses.length == 0) {
            throw new IllegalArgumentException("no bind addresses specified");
        }
        if (closed.get()) {
            throw new IllegalStateException("XNIO provider not open");
        }
        final UdpServerService serverService = multicast ? provider.createMulticastUdpServer() : provider.createUdpServer();
        serverService.setBindAddresses(bindAddresses);
        //noinspection unchecked
        serverService.setHandlerFactory(handlerFactory);
        final AtomicBoolean started = new AtomicBoolean(false);
        final AtomicBoolean stopped = new AtomicBoolean(false);
        return new SimpleConfigurableFactory<Closeable, UdpServerService>(serverService, started, new LifecycleCloseable(serverService, stopped));
    }

    /**
     * Create a UDP server.  The server will bind to the given addresses.  The provider's executor will be used to
     * execute handler methods.
     *
     * @param multicast {@code true} if the UDP server should be multicast-capable
     * @param handlerFactory the factory which will produce handlers for each channel
     * @param bindAddresses the addresses to bind
     * @return a factory that can be used to configure the new UDP server
     */
    public ConfigurableFactory<Closeable> createUdpServer(final boolean multicast, final IoHandlerFactory<? super MulticastDatagramChannel> handlerFactory, SocketAddress... bindAddresses) {
        if (handlerFactory == null) {
            throw new NullPointerException("handlerFactory is null");
        }
        if (bindAddresses == null) {
            throw new NullPointerException("bindAddresses is null");
        }
        if (bindAddresses.length == 0) {
            throw new IllegalArgumentException("no bind addresses specified");
        }
        if (closed.get()) {
            throw new IllegalStateException("XNIO provider not open");
        }
        final UdpServerService serverService = multicast ? provider.createMulticastUdpServer() : provider.createUdpServer();
        serverService.setBindAddresses(bindAddresses);
        //noinspection unchecked
        serverService.setHandlerFactory(handlerFactory);
        final AtomicBoolean started = new AtomicBoolean(false);
        final AtomicBoolean stopped = new AtomicBoolean(false);
        return new SimpleConfigurableFactory<Closeable, UdpServerService>(serverService, started, new LifecycleCloseable(serverService, stopped));
    }

    /**
     * Close this XNIO provider.  Calling this method more than one time has no additional effect.
     */
    public void close() throws IOException{
        if (! closed.getAndSet(true)) {
            provider.stop();
        }
    }


    private static class LifecycleCloseable implements Closeable {
        private final Lifecycle lifecycle;
        private final AtomicBoolean closed;

        private LifecycleCloseable(final Lifecycle lifecycle, final AtomicBoolean closed) {
            this.closed = closed;
            this.lifecycle = lifecycle;
        }

        public void close() throws IOException {
            if (! closed.getAndSet(true)) {
                lifecycle.stop();
            }
        }
    }

    private static class LifecycleConnector extends LifecycleCloseable implements TcpConnector {
        private final AtomicBoolean closed;
        private final Connector<SocketAddress, ConnectedStreamChannel<SocketAddress>> realConnector;

        private <T extends Lifecycle & TcpConnector> LifecycleConnector(final T lifecycle, final AtomicBoolean closed) {
            super(lifecycle, closed);
            this.closed = closed;
            realConnector = lifecycle;
        }

        public IoFuture<ConnectedStreamChannel<SocketAddress>> connectTo(final SocketAddress dest, final IoHandler<? super ConnectedStreamChannel<SocketAddress>> ioHandler) {
            if (closed.get()) {
                throw new IllegalStateException("Connector closed");
            }
            return realConnector.connectTo(dest, ioHandler);
        }

        public IoFuture<ConnectedStreamChannel<SocketAddress>> connectTo(final SocketAddress src, final SocketAddress dest, final IoHandler<? super ConnectedStreamChannel<SocketAddress>> ioHandler) {
            if (closed.get()) {
                throw new IllegalStateException("Connector closed");
            }
            return realConnector.connectTo(src, dest, ioHandler);
        }

        public TcpClient createClient(final SocketAddress dest) {
            return new TcpClient() {
                public IoFuture<ConnectedStreamChannel<SocketAddress>> connect(final IoHandler<? super ConnectedStreamChannel<SocketAddress>> ioHandler) {
                    return realConnector.connectTo(dest, ioHandler);
                }
            };
        }

        public TcpClient createClient(final SocketAddress src, final SocketAddress dest) {
            return new TcpClient() {
                public IoFuture<ConnectedStreamChannel<SocketAddress>> connect(final IoHandler<? super ConnectedStreamChannel<SocketAddress>> ioHandler) {
                    return realConnector.connectTo(src, dest, ioHandler);
                }
            };
        }
    }

    private static class SimpleConfigurableFactory<T, Z extends Configurable & Lifecycle> implements ConfigurableFactory<T> {
        private final AtomicBoolean started;
        private final T resource;
        private final Z configurableLifecycle;

        private SimpleConfigurableFactory(final Z configurableLifecycle, final AtomicBoolean started, final T resource) {
            this.started = started;
            this.resource = resource;
            this.configurableLifecycle = configurableLifecycle;
        }

        public T create() throws IOException {
            if (started.get()) {
                throw new IllegalStateException("Already created");
            }
            configurableLifecycle.start();
            return resource;
        }

        public Object getOption(final String name) throws UnsupportedOptionException, IOException {
            return configurableLifecycle.getOption(name);
        }

        public Map<String, Class<?>> getOptions() {
            return configurableLifecycle.getOptions();
        }

        public ConfigurableFactory<T> setOption(final String name, final Object value) throws IllegalArgumentException, IOException {
            if (started.get()) {
                throw new IllegalStateException("Already created");
            }
            configurableLifecycle.setOption(name, value);
            return this;
        }
    }
}
