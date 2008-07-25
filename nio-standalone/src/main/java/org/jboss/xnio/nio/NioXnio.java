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
import java.net.SocketAddress;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jboss.xnio.channels.UnsupportedOptionException;
import org.jboss.xnio.channels.Configurable;
import org.jboss.xnio.channels.UdpChannel;
import org.jboss.xnio.channels.TcpChannel;
import org.jboss.xnio.channels.ChannelOption;
import org.jboss.xnio.channels.StreamChannel;
import org.jboss.xnio.channels.StreamSourceChannel;
import org.jboss.xnio.channels.StreamSinkChannel;
import org.jboss.xnio.Xnio;
import org.jboss.xnio.ConfigurableFactory;
import org.jboss.xnio.IoHandlerFactory;
import org.jboss.xnio.CloseableTcpConnector;
import org.jboss.xnio.ChannelSource;
import org.jboss.xnio.IoFuture;
import org.jboss.xnio.IoHandler;
import org.jboss.xnio.FailedIoFuture;
import org.jboss.xnio.FinishedIoFuture;
import org.jboss.xnio.TcpConnector;
import org.jboss.xnio.TcpClient;

/**
 * An NIO-based XNIO provider for a standalone application.
 */
public final class NioXnio extends Xnio {
    private final NioProvider provider;

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Object lifecycleLock;

    /**
     * Create an NIO-based XNIO provider.  A direct executor is used for the handlers; the provider will create its own
     * selector threads, of which there will be one reader thread, one writer thread, and one connect/accept thread.
     *
     * @returns a new XNIO instance
     * @throws IOException if an I/O error occurs while starting the service
     */
    public static Xnio create() throws IOException {
        return new NioXnio(null, null, 1, 1, 1);
    }

    /**
     * Create an NIO-based XNIO provider.  A direct executor is used for the handlers; the provider will
     * create its own selector threads.
     *
     * @param readSelectorThreads the number of threads to assign for readable events
     * @param writeSelectorThreads the number of threads to assign for writable events
     * @param connectSelectorThreads the number of threads to assign for connect/accept events
     * @returns a new XNIO instance
     * @throws IOException if an I/O error occurs while starting the service
     * @throws IllegalArgumentException if a given argument is not valid
     */
    public static Xnio create(final int readSelectorThreads, final int writeSelectorThreads, final int connectSelectorThreads) throws IOException, IllegalArgumentException {
        return new NioXnio(null, null, readSelectorThreads, writeSelectorThreads, connectSelectorThreads);
    }

    /**
     * Create an NIO-based XNIO provider.  The given handler executor is used for the handlers; the provider will
     * create its own selector threads.
     *
     * @param handlerExecutor the executor to use to handle events
     * @param readSelectorThreads the number of threads to assign for readable events
     * @param writeSelectorThreads the number of threads to assign for writable events
     * @param connectSelectorThreads the number of threads to assign for connect/accept events
     * @returns a new XNIO instance
     * @throws IOException if an I/O error occurs while starting the service
     * @throws IllegalArgumentException if a given argument is not valid
     */
    public static Xnio create(Executor handlerExecutor, final int readSelectorThreads, final int writeSelectorThreads, final int connectSelectorThreads) throws IOException, IllegalArgumentException {
        if (handlerExecutor == null) {
            throw new NullPointerException("handlerExecutor is null");
        }
        return new NioXnio(handlerExecutor, null, readSelectorThreads, writeSelectorThreads, connectSelectorThreads);
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
     * @returns a new XNIO instance
     * @throws IOException if an I/O error occurs while starting the service
     * @throws IllegalArgumentException if a given argument is not valid
     */
    public static Xnio create(Executor handlerExecutor, ThreadFactory selectorThreadFactory, final int readSelectorThreads, final int writeSelectorThreads, final int connectSelectorThreads) throws IOException, IllegalArgumentException {
        if (handlerExecutor == null) {
            throw new NullPointerException("handlerExecutor is null");
        }
        if (selectorThreadFactory == null) {
            throw new NullPointerException("selectorThreadFactory is null");
        }
        return new NioXnio(handlerExecutor, selectorThreadFactory, readSelectorThreads, writeSelectorThreads, connectSelectorThreads);
    }

    private NioXnio(Executor handlerExecutor, ThreadFactory selectorThreadFactory, final int readSelectorThreads, final int writeSelectorThreads, final int connectSelectorThreads) throws IOException, IllegalArgumentException {
        lifecycleLock = new Object();
        final NioProvider provider;
        synchronized (lifecycleLock) {
            provider = new NioProvider();
            if (handlerExecutor != null) provider.setExecutor(handlerExecutor);
            if (selectorThreadFactory != null) provider.setSelectorThreadFactory(selectorThreadFactory);
            provider.setReadSelectorThreads(readSelectorThreads);
            provider.setWriteSelectorThreads(writeSelectorThreads);
            provider.setConnectionSelectorThreads(connectSelectorThreads);
            provider.start();
            this.provider = provider;
        }
    }

    /** {@inheritDoc} */
    public ConfigurableFactory<Closeable> createTcpServer(final Executor executor, final IoHandlerFactory<? super TcpChannel> handlerFactory, SocketAddress... bindAddresses) {
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
        final NioTcpServer nioTcpServer;
        synchronized (lifecycleLock) {
            nioTcpServer = new NioTcpServer();
            nioTcpServer.setNioProvider(provider);
            nioTcpServer.setExecutor(executor);
            nioTcpServer.setBindAddresses(bindAddresses);
            nioTcpServer.setHandlerFactory(handlerFactory);
        }
        final AtomicBoolean started = new AtomicBoolean(false);
        final AtomicBoolean stopped = new AtomicBoolean(false);
        return new SimpleConfigurableFactory<Closeable, NioTcpServer>(nioTcpServer, started, new LifecycleCloseable(nioTcpServer, stopped));
    }

    /** {@inheritDoc} */
    public ConfigurableFactory<Closeable> createTcpServer(final IoHandlerFactory<? super TcpChannel> handlerFactory, SocketAddress... bindAddresses) {
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
        final NioTcpServer nioTcpServer;
        synchronized (lifecycleLock) {
            nioTcpServer = new NioTcpServer();
            nioTcpServer.setNioProvider(provider);
            nioTcpServer.setBindAddresses(bindAddresses);
            nioTcpServer.setHandlerFactory(handlerFactory);
        }
        final AtomicBoolean started = new AtomicBoolean(false);
        final AtomicBoolean stopped = new AtomicBoolean(false);
        return new SimpleConfigurableFactory<Closeable, NioTcpServer>(nioTcpServer, started, new LifecycleCloseable(nioTcpServer, stopped));
    }

    /** {@inheritDoc} */
    public ConfigurableFactory<CloseableTcpConnector> createTcpConnector(final Executor executor) {
        if (executor == null) {
            throw new NullPointerException("executor is null");
        }
        if (closed.get()) {
            throw new IllegalStateException("XNIO provider not open");
        }
        final NioTcpConnector nioTcpConnector;
        synchronized (lifecycleLock) {
            nioTcpConnector = new NioTcpConnector();
            nioTcpConnector.setNioProvider(provider);
            nioTcpConnector.setExecutor(executor);
        }
        final AtomicBoolean started = new AtomicBoolean(false);
        final AtomicBoolean stopped = new AtomicBoolean(false);
        return new SimpleConfigurableFactory<CloseableTcpConnector, NioTcpConnector>(nioTcpConnector, started, new LifecycleConnector(nioTcpConnector, stopped));
    }

    /** {@inheritDoc} */
    public ConfigurableFactory<CloseableTcpConnector> createTcpConnector() {
        if (closed.get()) {
            throw new IllegalStateException("XNIO provider not open");
        }
        final NioTcpConnector nioTcpConnector;
        synchronized (lifecycleLock) {
            nioTcpConnector = new NioTcpConnector();
            nioTcpConnector.setNioProvider(provider);
        }
        final AtomicBoolean started = new AtomicBoolean(false);
        final AtomicBoolean stopped = new AtomicBoolean(false);
        return new SimpleConfigurableFactory<CloseableTcpConnector, NioTcpConnector>(nioTcpConnector, started, new LifecycleConnector(nioTcpConnector, stopped));
    }

    /** {@inheritDoc} */
    public ConfigurableFactory<Closeable> createUdpServer(final Executor executor, final boolean multicast, final IoHandlerFactory<? super UdpChannel> handlerFactory, SocketAddress... bindAddresses) {
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
        if (multicast) {
            final BioMulticastServer bioMulticastServer;
            synchronized (lifecycleLock) {
                bioMulticastServer = new BioMulticastServer();
                bioMulticastServer.setBindAddresses(bindAddresses);
                bioMulticastServer.setExecutor(executor);
                bioMulticastServer.setHandlerFactory(handlerFactory);
            }
            final AtomicBoolean started = new AtomicBoolean(false);
            final AtomicBoolean stopped = new AtomicBoolean(false);
            return new SimpleConfigurableFactory<Closeable, BioMulticastServer>(bioMulticastServer, started, new LifecycleCloseable(bioMulticastServer, stopped));
        } else {
            final NioUdpServer nioUdpServer;
            synchronized (lifecycleLock) {
                nioUdpServer = new NioUdpServer();
                nioUdpServer.setNioProvider(provider);
                nioUdpServer.setBindAddresses(bindAddresses);
                nioUdpServer.setExecutor(executor);
                nioUdpServer.setHandlerFactory(handlerFactory);
            }
            final AtomicBoolean started = new AtomicBoolean(false);
            final AtomicBoolean stopped = new AtomicBoolean(false);
            return new SimpleConfigurableFactory<Closeable, NioUdpServer>(nioUdpServer, started, new LifecycleCloseable(nioUdpServer, stopped));
        }
    }

    /** {@inheritDoc} */
    public ConfigurableFactory<Closeable> createUdpServer(final boolean multicast, final IoHandlerFactory<? super UdpChannel> handlerFactory, SocketAddress... bindAddresses) {
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
        if (multicast) {
            final BioMulticastServer bioMulticastServer;
            synchronized (lifecycleLock) {
                bioMulticastServer = new BioMulticastServer();
                bioMulticastServer.setBindAddresses(bindAddresses);
                bioMulticastServer.setHandlerFactory(handlerFactory);
            }
            final AtomicBoolean started = new AtomicBoolean(false);
            final AtomicBoolean stopped = new AtomicBoolean(false);
            return new SimpleConfigurableFactory<Closeable, BioMulticastServer>(bioMulticastServer, started, new LifecycleCloseable(bioMulticastServer, stopped));
        } else {
            final NioUdpServer nioUdpServer;
            synchronized (lifecycleLock) {
                nioUdpServer = new NioUdpServer();
                nioUdpServer.setNioProvider(provider);
                nioUdpServer.setBindAddresses(bindAddresses);
                nioUdpServer.setHandlerFactory(handlerFactory);
            }
            final AtomicBoolean started = new AtomicBoolean(false);
            final AtomicBoolean stopped = new AtomicBoolean(false);
            return new SimpleConfigurableFactory<Closeable, NioUdpServer>(nioUdpServer, started, new LifecycleCloseable(nioUdpServer, stopped));
        }
    }

    /** {@inheritDoc} */
    public ChannelSource<StreamChannel> createPipeServer(final IoHandlerFactory<? super StreamChannel> handlerFactory) {
        if (handlerFactory == null) {
            throw new NullPointerException("handlerFactory is null");
        }
        if (closed.get()) {
            throw new IllegalStateException("XNIO provider not open");
        }
        return new ChannelSource<StreamChannel>() {
            public IoFuture<StreamChannel> open(final IoHandler<? super StreamChannel> handler) {
                synchronized (lifecycleLock) {
                    final NioPipeConnection nioPipeConnection = new NioPipeConnection();
                    nioPipeConnection.setNioProvider(provider);
                    // ? bug ?
                    //noinspection unchecked
                    nioPipeConnection.setRightHandler(handlerFactory.createHandler());
                    nioPipeConnection.setLeftHandler(handler);
                    try {
                        nioPipeConnection.start();
                    } catch (IOException e) {
                        return new FailedIoFuture<StreamChannel>(e);
                    }
                    return new FinishedIoFuture<StreamChannel>(nioPipeConnection.getLeftSide());
                }
            }
        };
    }

    /** {@inheritDoc} */
    public ChannelSource<StreamSourceChannel> createPipeSourceServer(final IoHandlerFactory<? super StreamSinkChannel> handlerFactory) {
        if (handlerFactory == null) {
            throw new NullPointerException("handlerFactory is null");
        }
        if (closed.get()) {
            throw new IllegalStateException("XNIO provider not open");
        }
        return new ChannelSource<StreamSourceChannel>() {
            public IoFuture<StreamSourceChannel> open(final IoHandler<? super StreamSourceChannel> handler) {
                synchronized (lifecycleLock) {
                    final NioOneWayPipeConnection nioPipeConnection = new NioOneWayPipeConnection();
                    nioPipeConnection.setNioProvider(provider);
                    // ? bug ?
                    //noinspection unchecked
                    nioPipeConnection.setSinkHandler(handlerFactory.createHandler());
                    nioPipeConnection.setSourceHandler(handler);
                    try {
                        nioPipeConnection.start();
                    } catch (IOException e) {
                        return new FailedIoFuture<StreamSourceChannel>(e);
                    }
                    return new FinishedIoFuture<StreamSourceChannel>(nioPipeConnection.getSourceSide());
                }
            }
        };
    }

    /** {@inheritDoc} */
    public ChannelSource<StreamSinkChannel> createPipeSinkServer(final IoHandlerFactory<? super StreamSourceChannel> handlerFactory) {
        if (handlerFactory == null) {
            throw new NullPointerException("handlerFactory is null");
        }
        if (closed.get()) {
            throw new IllegalStateException("XNIO provider not open");
        }
        return new ChannelSource<StreamSinkChannel>() {
            public IoFuture<StreamSinkChannel> open(final IoHandler<? super StreamSinkChannel> handler) {
                synchronized (lifecycleLock) {
                    final NioOneWayPipeConnection nioPipeConnection = new NioOneWayPipeConnection();
                    nioPipeConnection.setNioProvider(provider);
                    // ? bug ?
                    //noinspection unchecked
                    nioPipeConnection.setSourceHandler(handlerFactory.createHandler());
                    nioPipeConnection.setSinkHandler(handler);
                    try {
                        nioPipeConnection.start();
                    } catch (IOException e) {
                        return new FailedIoFuture<StreamSinkChannel>(e);
                    }
                    return new FinishedIoFuture<StreamSinkChannel>(nioPipeConnection.getSinkSide());
                }
            }
        };
    }

    /** {@inheritDoc} */
    public IoFuture<Closeable> createPipeConnection(final IoHandler<? super StreamChannel> leftHandler, final IoHandler<? super StreamChannel> rightHandler) {
        if (leftHandler == null) {
            throw new NullPointerException("leftHandler is null");
        }
        if (rightHandler == null) {
            throw new NullPointerException("rightHandler is null");
        }
        if (closed.get()) {
            throw new IllegalStateException("XNIO provider not open");
        }
        synchronized (lifecycleLock) {
            final NioPipeConnection nioPipeConnection = new NioPipeConnection();
            nioPipeConnection.setNioProvider(provider);
            nioPipeConnection.setLeftHandler(leftHandler);
            nioPipeConnection.setRightHandler(rightHandler);
            try {
                nioPipeConnection.start();
            } catch (IOException e) {
                return new FailedIoFuture<Closeable>(e);
            }
            return new FinishedIoFuture<Closeable>(new LifecycleCloseable(nioPipeConnection, new AtomicBoolean(false)));
        }
    }

    /** {@inheritDoc} */
    public IoFuture<Closeable> createOneWayPipeConnection(final IoHandler<? super StreamSourceChannel> sourceHandler, final IoHandler<? super StreamSinkChannel> sinkHandler) {
        if (sourceHandler == null) {
            throw new NullPointerException("sourceHandler is null");
        }
        if (sinkHandler == null) {
            throw new NullPointerException("sinkHandler is null");
        }
        if (closed.get()) {
            throw new IllegalStateException("XNIO provider not open");
        }
        synchronized (lifecycleLock) {
            final NioOneWayPipeConnection nioOneWayPipeConnection = new NioOneWayPipeConnection();
            nioOneWayPipeConnection.setNioProvider(provider);
            nioOneWayPipeConnection.setSourceHandler(sourceHandler);
            nioOneWayPipeConnection.setSinkHandler(sinkHandler);
            try {
                nioOneWayPipeConnection.start();
            } catch (IOException e) {
                return new FailedIoFuture<Closeable>(e);
            }
            return new FinishedIoFuture<Closeable>(new LifecycleCloseable(nioOneWayPipeConnection, new AtomicBoolean(false)));
        }
    }

    /** {@inheritDoc} */
    public void close() throws IOException{
        if (! closed.getAndSet(true)) {
            synchronized (lifecycleLock) {
                provider.stop();
            }
        }
    }

    private class LifecycleCloseable implements Closeable {
        private final Lifecycle lifecycle;
        private final AtomicBoolean closed;

        private LifecycleCloseable(final Lifecycle lifecycle, final AtomicBoolean closed) {
            this.closed = closed;
            this.lifecycle = lifecycle;
        }

        public void close() throws IOException {
            if (! closed.getAndSet(true)) {
                synchronized (lifecycleLock) {
                    lifecycle.stop();
                }
            }
        }
    }

    private class LifecycleConnector extends LifecycleCloseable implements CloseableTcpConnector {
        private final AtomicBoolean closed;
        private final TcpConnector realConnector;

        private <T extends Lifecycle & TcpConnector> LifecycleConnector(final T lifecycle, final AtomicBoolean closed) {
            super(lifecycle, closed);
            this.closed = closed;
            realConnector = lifecycle;
        }

        public IoFuture<TcpChannel> connectTo(final SocketAddress dest, final IoHandler<? super TcpChannel> ioHandler) {
            if (closed.get()) {
                throw new IllegalStateException("Connector closed");
            }
            return realConnector.connectTo(dest, ioHandler);
        }

        public IoFuture<TcpChannel> connectTo(final SocketAddress src, final SocketAddress dest, final IoHandler<? super TcpChannel> ioHandler) {
            if (closed.get()) {
                throw new IllegalStateException("Connector closed");
            }
            return realConnector.connectTo(src, dest, ioHandler);
        }

        public TcpClient createChannelSource(final SocketAddress dest) {
            return new TcpClient() {
                public IoFuture<TcpChannel> open(final IoHandler<? super TcpChannel> handler) {
                    return realConnector.connectTo(dest, handler);
                }
            };
        }

        public TcpClient createChannelSource(final SocketAddress src, final SocketAddress dest) {
            return new TcpClient() {
                public IoFuture<TcpChannel> open(final IoHandler<? super TcpChannel> handler) {
                    return realConnector.connectTo(src, dest, handler);
                }
            };
        }
    }

    private class SimpleConfigurableFactory<Q, Z extends Configurable & Lifecycle> implements ConfigurableFactory<Q> {
        private final AtomicBoolean started;
        private final Q resource;
        private final Z configurableLifecycle;

        private SimpleConfigurableFactory(final Z configurableLifecycle, final AtomicBoolean started, final Q resource) {
            this.started = started;
            this.resource = resource;
            this.configurableLifecycle = configurableLifecycle;
        }

        public Q create() throws IOException {
            if (started.get()) {
                throw new IllegalStateException("Already created");
            }
            synchronized (lifecycleLock) {
                configurableLifecycle.start();
            }
            return resource;
        }

        public <T> T getOption(final ChannelOption<T> option) throws UnsupportedOptionException, IOException {
            return configurableLifecycle.getOption(option);
        }

        public Set<ChannelOption<?>> getOptions() {
            return configurableLifecycle.getOptions();
        }

        public <T> Configurable setOption(final ChannelOption<T> option, final T value) throws IllegalArgumentException, IOException {
            if (started.get()) {
                throw new IllegalStateException("Already created");
            }
            configurableLifecycle.setOption(option, value);
            return this;
        }
    }
}
