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

package org.jboss.xnio.nio.standalone;

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
import org.jboss.xnio.nio.NioProvider;
import org.jboss.xnio.spi.TcpServerService;
import org.jboss.xnio.spi.TcpConnectorService;
import org.jboss.xnio.spi.Lifecycle;
import org.jboss.xnio.spi.UdpServerService;
import org.jboss.xnio.spi.PipeService;
import org.jboss.xnio.spi.PipeEnd;
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
import org.jboss.xnio.IoUtils;

/**
 * An NIO-based XNIO provider for a standalone application.
 */
public class XnioNioImpl extends Xnio {
    private final NioProvider provider;

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Object lifecycleLock;

    /**
     * Create an NIO-based XNIO provider.  A direct executor is used for the handlers; the provider will create its own
     * selector threads, of which there will be one reader thread, one writer thread, and one connect/accept thread.
     *
     * @throws IOException if an I/O error occurs while starting the service
     */
    public XnioNioImpl() throws IOException {
        this(1, 1, 1);
    }

    /**
     * Create an NIO-based XNIO provider.  A direct executor is used for the handlers; the provider will
     * create its own selector threads.
     *
     * @param readSelectorThreads the number of threads to assign for readable events
     * @param writeSelectorThreads the number of threads to assign for writable events
     * @param connectSelectorThreads the number of threads to assign for connect/accept events
     * @throws IOException if an I/O error occurs while starting the service
     * @throws IllegalArgumentException if a given argument is not valid
     */
    public XnioNioImpl(final int readSelectorThreads, final int writeSelectorThreads, final int connectSelectorThreads) throws IOException, IllegalArgumentException {
        lifecycleLock = new Object();
        final NioProvider provider;
        synchronized (lifecycleLock) {
            provider = new NioProvider();
            provider.setExecutor(IoUtils.directExecutor());
            provider.setReadSelectorThreads(readSelectorThreads);
            provider.setWriteSelectorThreads(writeSelectorThreads);
            provider.setConnectionSelectorThreads(connectSelectorThreads);
            provider.start();
        }
        this.provider = provider;
    }

    /**
     * Create an NIO-based XNIO provider.  The given handler executor is used for the handlers; the provider will
     * create its own selector threads.
     *
     * @param handlerExecutor the executor to use to handle events
     * @param readSelectorThreads the number of threads to assign for readable events
     * @param writeSelectorThreads the number of threads to assign for writable events
     * @param connectSelectorThreads the number of threads to assign for connect/accept events
     * @throws IOException if an I/O error occurs while starting the service
     * @throws IllegalArgumentException if a given argument is not valid
     */
    public XnioNioImpl(Executor handlerExecutor, final int readSelectorThreads, final int writeSelectorThreads, final int connectSelectorThreads) throws IOException, IllegalArgumentException {
        lifecycleLock = new Object();
        final NioProvider provider;
        synchronized (lifecycleLock) {
            provider = new NioProvider();
            provider.setExecutor(handlerExecutor);
            provider.setReadSelectorThreads(readSelectorThreads);
            provider.setWriteSelectorThreads(writeSelectorThreads);
            provider.setConnectionSelectorThreads(connectSelectorThreads);
            provider.start();
        }
        this.provider = provider;
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
     * @throws IOException if an I/O error occurs while starting the service
     * @throws IllegalArgumentException if a given argument is not valid
     */
    public XnioNioImpl(Executor handlerExecutor, ThreadFactory selectorThreadFactory, final int readSelectorThreads, final int writeSelectorThreads, final int connectSelectorThreads) throws IOException, IllegalArgumentException {
        lifecycleLock = new Object();
        final NioProvider provider;
        synchronized (lifecycleLock) {
            provider = new NioProvider();
            provider.setExecutor(handlerExecutor);
            provider.setSelectorThreadFactory(selectorThreadFactory);
            provider.setReadSelectorThreads(readSelectorThreads);
            provider.setWriteSelectorThreads(writeSelectorThreads);
            provider.setConnectionSelectorThreads(connectSelectorThreads);
            provider.start();
        }
        this.provider = provider;
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
        final TcpServerService tcpServerService;
        synchronized (lifecycleLock) {
            tcpServerService = provider.createTcpServer();
            if (executor != null) tcpServerService.setExecutor(executor);
            tcpServerService.setBindAddresses(bindAddresses);
            tcpServerService.setHandlerFactory(handlerFactory);
        }
        final AtomicBoolean started = new AtomicBoolean(false);
        final AtomicBoolean stopped = new AtomicBoolean(false);
        return new SimpleConfigurableFactory<Closeable, TcpServerService>(tcpServerService, started, new LifecycleCloseable(tcpServerService, stopped));
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
        final TcpServerService tcpServerService;
        synchronized (lifecycleLock) {
            tcpServerService = provider.createTcpServer();
            tcpServerService.setBindAddresses(bindAddresses);
            tcpServerService.setHandlerFactory(handlerFactory);
        }
        final AtomicBoolean started = new AtomicBoolean(false);
        final AtomicBoolean stopped = new AtomicBoolean(false);
        return new SimpleConfigurableFactory<Closeable, TcpServerService>(tcpServerService, started, new LifecycleCloseable(tcpServerService, stopped));
    }

    /** {@inheritDoc} */
    public ConfigurableFactory<CloseableTcpConnector> createTcpConnector(final Executor executor) {
        if (executor == null) {
            throw new NullPointerException("executor is null");
        }
        if (closed.get()) {
            throw new IllegalStateException("XNIO provider not open");
        }
        final TcpConnectorService connectorService;
        synchronized (lifecycleLock) {
            connectorService = provider.createTcpConnector();
            connectorService.setExecutor(executor);
        }
        final AtomicBoolean started = new AtomicBoolean(false);
        final AtomicBoolean stopped = new AtomicBoolean(false);
        return new SimpleConfigurableFactory<CloseableTcpConnector, TcpConnectorService>(connectorService, started, new LifecycleConnector(connectorService, stopped));
    }

    /** {@inheritDoc} */
    public ConfigurableFactory<CloseableTcpConnector> createTcpConnector() {
        if (closed.get()) {
            throw new IllegalStateException("XNIO provider not open");
        }
        final TcpConnectorService connectorService;
        synchronized (lifecycleLock) {
            connectorService = provider.createTcpConnector();
        }
        final AtomicBoolean started = new AtomicBoolean(false);
        final AtomicBoolean stopped = new AtomicBoolean(false);
        return new SimpleConfigurableFactory<CloseableTcpConnector, TcpConnectorService>(connectorService, started, new LifecycleConnector(connectorService, stopped));
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
        final UdpServerService serverService;
        synchronized (lifecycleLock) {
            serverService = multicast ? provider.createMulticastUdpServer() : provider.createUdpServer();
            serverService.setBindAddresses(bindAddresses);
            //noinspection unchecked
            serverService.setHandlerFactory(handlerFactory);
        }
        final AtomicBoolean started = new AtomicBoolean(false);
        final AtomicBoolean stopped = new AtomicBoolean(false);
        return new SimpleConfigurableFactory<Closeable, UdpServerService>(serverService, started, new LifecycleCloseable(serverService, stopped));
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
        final UdpServerService serverService;
        synchronized (lifecycleLock) {
            serverService = multicast ? provider.createMulticastUdpServer() : provider.createUdpServer();
            serverService.setBindAddresses(bindAddresses);
            //noinspection unchecked
            serverService.setHandlerFactory(handlerFactory);
        }
        final AtomicBoolean started = new AtomicBoolean(false);
        final AtomicBoolean stopped = new AtomicBoolean(false);
        return new SimpleConfigurableFactory<Closeable, UdpServerService>(serverService, started, new LifecycleCloseable(serverService, stopped));
    }

    /** {@inheritDoc} */
    public ChannelSource<StreamChannel> createPipeServer(final IoHandlerFactory<? super StreamChannel> handlerFactory) {
        if (handlerFactory == null) {
            throw new NullPointerException("handlerFactory is null");
        }
        synchronized (lifecycleLock) {
            return new ChannelSource<StreamChannel>() {
                public IoFuture<StreamChannel> open(final IoHandler<? super StreamChannel> handler) {
                    final PipeService pipeService = provider.createPipe();
                    final PipeEnd<StreamChannel> leftEnd = pipeService.getLeftEnd();
                    final PipeEnd<StreamChannel> rightEnd = pipeService.getRightEnd();
                    leftEnd.setHandler(handlerFactory.createHandler());
                    rightEnd.setHandler(handler);
                    try {
                        pipeService.start();
                    } catch (IOException e) {
                        return new FailedIoFuture<StreamChannel>(e);
                    }
                    return new FinishedIoFuture<StreamChannel>(null /* TODO */);
                }
            };
        }
    }

    /** {@inheritDoc} */
    public ChannelSource<StreamSourceChannel> createPipeSourceServer(final IoHandlerFactory<? super StreamSinkChannel> handlerFactory) {
        return null;
    }

    /** {@inheritDoc} */
    public ChannelSource<StreamSinkChannel> createPipeSinkServer(final IoHandlerFactory<? super StreamSourceChannel> handlerFactory) {
        return null;
    }

    /** {@inheritDoc} */
    public IoFuture<Closeable> createPipeConnection(final IoHandler<? super StreamChannel> leftHandler, final IoHandler<? super StreamChannel> rightHandler) {
        return null;
    }

    /** {@inheritDoc} */
    public IoFuture<Closeable> createOneWayPipeConnection(final IoHandler<? super StreamSourceChannel> sourceHandler, final IoHandler<? super StreamSinkChannel> sinkHandler) {
        return null;
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
