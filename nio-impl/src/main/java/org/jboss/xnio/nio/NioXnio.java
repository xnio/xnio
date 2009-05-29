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
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.jboss.xnio.ChannelSource;
import org.jboss.xnio.ConfigurableFactory;
import org.jboss.xnio.FailedIoFuture;
import org.jboss.xnio.FinishedIoFuture;
import org.jboss.xnio.IoFuture;
import org.jboss.xnio.IoHandler;
import org.jboss.xnio.IoHandlerFactory;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.TcpAcceptor;
import org.jboss.xnio.TcpConnector;
import org.jboss.xnio.Version;
import org.jboss.xnio.Xnio;
import org.jboss.xnio.channels.BoundChannel;
import org.jboss.xnio.channels.BoundServer;
import org.jboss.xnio.channels.StreamChannel;
import org.jboss.xnio.channels.StreamSinkChannel;
import org.jboss.xnio.channels.StreamSourceChannel;
import org.jboss.xnio.channels.TcpChannel;
import org.jboss.xnio.channels.UdpChannel;
import org.jboss.xnio.log.Logger;
import org.jboss.xnio.management.OneWayPipeConnectionMBean;
import org.jboss.xnio.management.PipeConnectionMBean;
import org.jboss.xnio.management.PipeServerMBean;
import org.jboss.xnio.management.PipeSinkServerMBean;
import org.jboss.xnio.management.PipeSourceServerMBean;
import org.jboss.xnio.management.TcpConnectionMBean;
import org.jboss.xnio.management.TcpServerMBean;
import org.jboss.xnio.management.UdpServerMBean;

/**
 * An NIO-based XNIO provider for a standalone application.
 */
public final class NioXnio extends Xnio {

    private static final Logger log = Logger.getLogger("org.jboss.xnio.nio");

    static {
        log.info("XNIO NIO Implementation Version " + Version.VERSION);
    }

    private final Object lock = new Object();

    /**
     * @protectedby lock
     */
    private volatile boolean closed;

    private final Executor executor;

    private final List<NioSelectorRunnable> readers = new ArrayList<NioSelectorRunnable>();
    private final List<NioSelectorRunnable> writers = new ArrayList<NioSelectorRunnable>();
    private final List<NioSelectorRunnable> connectors = new ArrayList<NioSelectorRunnable>();

    /**
     * @protectedby lock
     */
    private final Set<Closeable> managedSet = new HashSet<Closeable>();

    private static final class CreateAction implements PrivilegedExceptionAction<NioXnio> {
        private final NioXnioConfiguration configuration;

        public CreateAction(final NioXnioConfiguration configuration) {
            this.configuration = configuration;
        }

        public NioXnio run() throws IOException {
            return new NioXnio(configuration);
        }
    }

    /**
     * Create an NIO-based XNIO provider.  The provided configuration is used to set up the provider.
     *
     * @param configuration the configuration
     * @return a new XNIO instance
     * @throws IOException if an I/O error occurs while starting the service
     * @since 1.2
     */
    public static Xnio create(NioXnioConfiguration configuration) throws IOException {
        return doCreate(configuration);
    }

    private static NioXnio doCreate(final NioXnioConfiguration configuration) throws IOException {
        try {
            return AccessController.doPrivileged(new CreateAction(configuration));
        } catch (PrivilegedActionException e) {
            try {
                throw e.getCause();
            } catch (Error error) {
                throw error;
            } catch (RuntimeException re) {
                throw re;
            } catch (IOException ioe) {
                throw ioe;
            } catch (Throwable cause) {
                throw new RuntimeException("Unexpected exception", cause);
            }
        }
    }

    /**
     * Create an NIO-based XNIO provider.  A direct executor is used for the handlers; the provider will create its own
     * selector threads, of which there will be one reader thread, one writer thread, and one connect/accept thread.
     *
     * @return a new XNIO instance
     * @throws IOException if an I/O error occurs while starting the service
     */
    public static Xnio create() throws IOException {
        final NioXnioConfiguration configuration = new NioXnioConfiguration();
        configuration.setReadSelectorThreads(1);
        configuration.setWriteSelectorThreads(1);
        configuration.setConnectSelectorThreads(1);
        return doCreate(configuration);
    }

    /**
     * Create an NIO-based XNIO provider.  A direct executor is used for the handlers; the provider will
     * create its own selector threads.
     *
     * @param readSelectorThreads the number of threads to assign for readable events
     * @param writeSelectorThreads the number of threads to assign for writable events
     * @param connectSelectorThreads the number of threads to assign for connect/accept events
     * @return a new XNIO instance
     * @throws IOException if an I/O error occurs while starting the service
     * @throws IllegalArgumentException if a given argument is not valid
     */
    public static Xnio create(final int readSelectorThreads, final int writeSelectorThreads, final int connectSelectorThreads) throws IOException, IllegalArgumentException {
        final NioXnioConfiguration configuration = new NioXnioConfiguration();
        configuration.setReadSelectorThreads(readSelectorThreads);
        configuration.setWriteSelectorThreads(writeSelectorThreads);
        configuration.setConnectSelectorThreads(connectSelectorThreads);
        return doCreate(configuration);
    }

    /**
     * Create an NIO-based XNIO provider.  The given handler executor is used for the handlers; the provider will
     * create its own selector threads.
     *
     * @param handlerExecutor the executor to use to handle events
     * @param readSelectorThreads the number of threads to assign for readable events
     * @param writeSelectorThreads the number of threads to assign for writable events
     * @param connectSelectorThreads the number of threads to assign for connect/accept events
     * @return a new XNIO instance
     * @throws IOException if an I/O error occurs while starting the service
     * @throws IllegalArgumentException if a given argument is not valid
     */
    public static Xnio create(Executor handlerExecutor, final int readSelectorThreads, final int writeSelectorThreads, final int connectSelectorThreads) throws IOException, IllegalArgumentException {
        if (handlerExecutor == null) {
            throw new NullPointerException("handlerExecutor is null");
        }
        final NioXnioConfiguration configuration = new NioXnioConfiguration();
        configuration.setExecutor(handlerExecutor);
        configuration.setReadSelectorThreads(readSelectorThreads);
        configuration.setWriteSelectorThreads(writeSelectorThreads);
        configuration.setConnectSelectorThreads(connectSelectorThreads);
        return doCreate(configuration);
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
     * @return a new XNIO instance
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
        final NioXnioConfiguration configuration = new NioXnioConfiguration();
        configuration.setExecutor(handlerExecutor);
        configuration.setSelectorThreadFactory(selectorThreadFactory);
        configuration.setReadSelectorThreads(readSelectorThreads);
        configuration.setWriteSelectorThreads(writeSelectorThreads);
        configuration.setConnectSelectorThreads(connectSelectorThreads);
        return doCreate(configuration);
    }

    private NioXnio(NioXnioConfiguration configuration) throws IOException {
        super(configuration);
        final String providerClassName = SelectorProvider.provider().getClass().getCanonicalName();
        if ("sun.nio.ch.PollSelectorProvider".equals(providerClassName)) {
            log.warn("The currently defined selector provider class (%s) is not supported for use with XNIO", providerClassName);
        }
        log.trace("Starting up with selector provider %s", providerClassName);
        ThreadFactory selectorThreadFactory = configuration.getSelectorThreadFactory();
        final Executor executor = configuration.getExecutor();
        final int readSelectorThreads = configuration.getReadSelectorThreads();
        final int writeSelectorThreads = configuration.getWriteSelectorThreads();
        final int connectSelectorThreads = configuration.getConnectSelectorThreads();
        final int selectorCacheSize = configuration.getSelectorCacheSize();
        if (selectorThreadFactory == null) {
            selectorThreadFactory = Executors.defaultThreadFactory();
        }
        if (readSelectorThreads < 1) {
            throw new IllegalArgumentException("readSelectorThreads must be >= 1");
        }
        if (writeSelectorThreads < 1) {
            throw new IllegalArgumentException("writeSelectorThreads must be >= 1");
        }
        if (connectSelectorThreads < 1) {
            throw new IllegalArgumentException("connectSelectorThreads must be >= 1");
        }
        if (selectorCacheSize < 0) {
            throw new IllegalArgumentException("selectorCacheSize must be >= 0");
        }
        cache = new Selector[selectorCacheSize];
        synchronized (lock) {
            this.executor = executor == null ? IoUtils.directExecutor() : executor;
            for (int i = 0; i < readSelectorThreads; i ++) {
                readers.add(new NioSelectorRunnable());
            }
            for (int i = 0; i < writeSelectorThreads; i ++) {
                writers.add(new NioSelectorRunnable());
            }
            for (int i = 0; i < connectSelectorThreads; i ++) {
                connectors.add(new NioSelectorRunnable());
            }
            for (NioSelectorRunnable runnable : readers) {
                selectorThreadFactory.newThread(runnable).start();
            }
            for (NioSelectorRunnable runnable : writers) {
                selectorThreadFactory.newThread(runnable).start();
            }
            for (NioSelectorRunnable runnable : connectors) {
                selectorThreadFactory.newThread(runnable).start();
            }
            log.debug("Creating NioXnio instance using executor %s, %d read threads, %d write threads, %d connect threads", this.executor, Integer.valueOf(readSelectorThreads), Integer.valueOf(writeSelectorThreads), Integer.valueOf(connectSelectorThreads));
        }
    }

    /** {@inheritDoc} */
    public ConfigurableFactory<? extends BoundServer<SocketAddress, BoundChannel<SocketAddress>>> createTcpServer(final Executor executor, final IoHandlerFactory<? super TcpChannel> handlerFactory, SocketAddress... bindAddresses) {
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
        synchronized (lock) {
            if (closed) {
                throw notOpen();
            }
            return new NioTcpServerFactory(this, executor, handlerFactory, bindAddresses);
        }
    }

    /** {@inheritDoc} */
    public ConfigurableFactory<? extends BoundServer<SocketAddress, BoundChannel<SocketAddress>>> createTcpServer(final IoHandlerFactory<? super TcpChannel> handlerFactory, SocketAddress... bindAddresses) {
        return createTcpServer(executor, handlerFactory, bindAddresses);
    }

    /** {@inheritDoc} */
    public ConfigurableFactory<? extends TcpConnector> createTcpConnector(final Executor executor) {
        if (executor == null) {
            throw new NullPointerException("executor is null");
        }
        synchronized (lock) {
            if (closed) {
                throw notOpen();
            }
            return new NioTcpConnectorFactory(this, executor);
        }
    }

    /** {@inheritDoc} */
    public ConfigurableFactory<? extends TcpConnector> createTcpConnector() {
        return createTcpConnector(executor);
    }

    /** {@inheritDoc} */
    public ConfigurableFactory<? extends BoundServer<SocketAddress, UdpChannel>> createUdpServer(final Executor executor, final boolean multicast, final IoHandlerFactory<? super UdpChannel> handlerFactory, SocketAddress... bindAddresses) {
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
        synchronized (lock) {
            if (closed) {
                throw notOpen();
            }
            if (multicast) {
                return new BioUdpServerFactory(this, executor, handlerFactory, bindAddresses);
            } else {
                return new NioUdpServerFactory(this, executor, handlerFactory, bindAddresses);
            }
        }
    }

    /** {@inheritDoc} */
    public ConfigurableFactory<? extends BoundServer<SocketAddress, UdpChannel>> createUdpServer(final boolean multicast, final IoHandlerFactory<? super UdpChannel> handlerFactory, SocketAddress... bindAddresses) {
        return createUdpServer(executor, multicast, handlerFactory, bindAddresses);
    }

    /** {@inheritDoc} */
    public ChannelSource<? extends StreamChannel> createPipeServer(final Executor executor, final IoHandlerFactory<? super StreamChannel> handlerFactory) {
        if (executor == null) {
            throw new NullPointerException("executor is null");
        }
        if (handlerFactory == null) {
            throw new NullPointerException("handlerFactory is null");
        }
        synchronized (lock) {
            if (closed) {
                throw notOpen();
            }
            return new ChannelSource<StreamChannel>() {
                public IoFuture<? extends StreamChannel> open(final IoHandler<? super StreamChannel> handler) {
                    synchronized (lock) {
                        if (closed) {
                            throw notOpen();
                        }
                        final NioPipeConnection nioPipeConnection;
                        try {
                            //noinspection unchecked
                            nioPipeConnection = new NioPipeConnection(NioXnio.this, handler, handlerFactory.createHandler(), executor);
                        } catch (IOException e) {
                            return new FailedIoFuture<StreamChannel>(e);
                        }
                        return new FinishedIoFuture<StreamChannel>(nioPipeConnection.getLeftSide());
                    }
                }
            };
        }
    }

    /** {@inheritDoc} */
    public ChannelSource<? extends StreamChannel> createPipeServer(final IoHandlerFactory<? super StreamChannel> handlerFactory) {
        return createPipeServer(executor, handlerFactory);
    }

    /** {@inheritDoc} */
    public ChannelSource<? extends StreamSourceChannel> createPipeSourceServer(final Executor executor, final IoHandlerFactory<? super StreamSinkChannel> handlerFactory) {
        if (executor == null) {
            throw new NullPointerException("executor is null");
        }
        if (handlerFactory == null) {
            throw new NullPointerException("handlerFactory is null");
        }
        synchronized (lock) {
            if (closed) {
                throw notOpen();
            }
            return new ChannelSource<StreamSourceChannel>() {
                public IoFuture<? extends StreamSourceChannel> open(final IoHandler<? super StreamSourceChannel> handler) {
                    synchronized (lock) {
                        if (closed) {
                            throw notOpen();
                        }
                        final NioOneWayPipeConnection nioPipeConnection;
                        try {
                            //noinspection unchecked
                            nioPipeConnection = new NioOneWayPipeConnection(NioXnio.this, handler, handlerFactory.createHandler(), executor);
                        } catch (IOException e) {
                            return new FailedIoFuture<StreamSourceChannel>(e);
                        }
                        return new FinishedIoFuture<StreamSourceChannel>(nioPipeConnection.getSourceSide());
                    }
                }
            };
        }
    }

    /** {@inheritDoc} */
    public ChannelSource<? extends StreamSourceChannel> createPipeSourceServer(final IoHandlerFactory<? super StreamSinkChannel> handlerFactory) {
        return createPipeSourceServer(executor, handlerFactory);
    }

    /** {@inheritDoc} */
    public ChannelSource<? extends StreamSinkChannel> createPipeSinkServer(final Executor executor, final IoHandlerFactory<? super StreamSourceChannel> handlerFactory) {
        if (executor == null) {
            throw new NullPointerException("executor is null");
        }
        if (handlerFactory == null) {
            throw new NullPointerException("handlerFactory is null");
        }
        synchronized (lock) {
            if (closed) {
                throw notOpen();
            }
            return new ChannelSource<StreamSinkChannel>() {
                public IoFuture<? extends StreamSinkChannel> open(final IoHandler<? super StreamSinkChannel> handler) {
                    synchronized (lock) {
                        if (closed) {
                            throw notOpen();
                        }
                        final NioOneWayPipeConnection nioPipeConnection;
                        try {
                            //noinspection unchecked
                            nioPipeConnection = new NioOneWayPipeConnection(NioXnio.this, handlerFactory.createHandler(), handler, executor);
                        } catch (IOException e) {
                            return new FailedIoFuture<StreamSinkChannel>(e);
                        }
                        return new FinishedIoFuture<StreamSinkChannel>(nioPipeConnection.getSinkSide());
                    }
                }
            };
        }
    }

    /** {@inheritDoc} */
    public ChannelSource<? extends StreamSinkChannel> createPipeSinkServer(final IoHandlerFactory<? super StreamSourceChannel> handlerFactory) {
        return createPipeSinkServer(executor, handlerFactory);
    }

    /** {@inheritDoc} */
    public IoFuture<? extends Closeable> createPipeConnection(final Executor executor, final IoHandler<? super StreamChannel> leftHandler, final IoHandler<? super StreamChannel> rightHandler) {
        if (executor == null) {
            throw new NullPointerException("executor is null");
        }
        if (leftHandler == null) {
            throw new NullPointerException("leftHandler is null");
        }
        if (rightHandler == null) {
            throw new NullPointerException("rightHandler is null");
        }
        synchronized (lock) {
            if (closed) {
                throw notOpen();
            }
            try {
                return new FinishedIoFuture<Closeable>(new NioPipeConnection(this, leftHandler, rightHandler, executor));
            } catch (IOException e) {
                return new FailedIoFuture<Closeable>(e);
            }
        }
    }

    /** {@inheritDoc} */
    public IoFuture<? extends Closeable> createPipeConnection(final IoHandler<? super StreamChannel> leftHandler, final IoHandler<? super StreamChannel> rightHandler) {
        return createPipeConnection(executor, leftHandler, rightHandler);
    }

    /** {@inheritDoc} */
    public IoFuture<? extends Closeable> createOneWayPipeConnection(final Executor executor, final IoHandler<? super StreamSourceChannel> sourceHandler, final IoHandler<? super StreamSinkChannel> sinkHandler) {
        if (executor == null) {
            throw new NullPointerException("executor is null");
        }
        if (sourceHandler == null) {
            throw new NullPointerException("sourceHandler is null");
        }
        if (sinkHandler == null) {
            throw new NullPointerException("sinkHandler is null");
        }
        synchronized (lock) {
            if (closed) {
                throw notOpen();
            }
            try {
                return new FinishedIoFuture<Closeable>(new NioOneWayPipeConnection(this, sourceHandler, sinkHandler, executor));
            } catch (IOException e) {
                return new FailedIoFuture<Closeable>(e);
            }
        }
    }

    /** {@inheritDoc} */
    public IoFuture<? extends Closeable> createOneWayPipeConnection(final IoHandler<? super StreamSourceChannel> sourceHandler, final IoHandler<? super StreamSinkChannel> sinkHandler) {
        return createOneWayPipeConnection(executor, sourceHandler, sinkHandler);
    }

    /** {@inheritDoc} */
    public ConfigurableFactory<? extends TcpAcceptor> createTcpAcceptor(final Executor executor) {
        if (executor == null) {
            throw new NullPointerException("executor is null");
        }
        synchronized (lock) {
            if (closed) {
                throw notOpen();
            }
            return new NioTcpAcceptorFactory(this, executor);
        }
    }

    /** {@inheritDoc} */
    public ConfigurableFactory<? extends TcpAcceptor> createTcpAcceptor() {
        return createTcpAcceptor(executor);
    }

    /**
     * {@inheritDoc} This implementation relies on NIO mechanisms to awaken interrupted threads.
     */
    public void awaken(final Thread targetThread) {
        // no-op
    }

    /** {@inheritDoc} */
    public void close() throws IOException{
        synchronized (lock) {
            if (! closed) {
                closed = true;
                Iterator<Closeable> it = managedSet.iterator();
                while (it.hasNext()) {
                    Closeable closeable = it.next();
                    it.remove();
                    IoUtils.safeClose(closeable);
                }
                for (NioSelectorRunnable runnable : readers) {
                    runnable.shutdown();
                }
                for (NioSelectorRunnable runnable : writers) {
                    runnable.shutdown();
                }
                for (NioSelectorRunnable runnable : connectors) {
                    runnable.shutdown();
                }
                readers.clear();
                writers.clear();
                connectors.clear();
            }
        }
    }

    public String toString() {
        return "NIO " + super.toString();
    }

    protected Closeable registerMBean(final TcpServerMBean mBean) {
        return super.registerMBean(mBean);
    }

    protected Closeable registerMBean(final TcpConnectionMBean mBean) {
        return super.registerMBean(mBean);
    }

    protected Closeable registerMBean(final UdpServerMBean mBean) {
        return super.registerMBean(mBean);
    }

    protected Closeable registerMBean(final OneWayPipeConnectionMBean mBean) {
        return super.registerMBean(mBean);
    }

    protected Closeable registerMBean(final PipeConnectionMBean mBean) {
        return super.registerMBean(mBean);
    }

    protected Closeable registerMBean(final PipeServerMBean mBean) {
        return super.registerMBean(mBean);
    }

    protected Closeable registerMBean(final PipeSourceServerMBean mBean) {
        return super.registerMBean(mBean);
    }

    protected Closeable registerMBean(final PipeSinkServerMBean mBean) {
        return super.registerMBean(mBean);
    }

    private final AtomicInteger loadSequence = new AtomicInteger();

    private NioHandle doAdd(final SelectableChannel channel, final List<NioSelectorRunnable> runnableSet, final Runnable handler, final boolean oneshot, final Executor executor) throws IOException {
        if (closed) {
            throw new ClosedChannelException();
        }
        final SynchronousHolder<NioHandle, IOException> holder = new SynchronousHolder<NioHandle, IOException>();
        NioSelectorRunnable nioSelectorRunnable = runnableSet.get(loadSequence.getAndIncrement() % runnableSet.size());
        final NioSelectorRunnable actualSelectorRunnable = nioSelectorRunnable;
        nioSelectorRunnable.runTask(new SelectorTask() {
            public void run(final Selector selector) {
                try {
                    final SelectionKey selectionKey = channel.register(selector, 0);
                    final NioHandle handle = new NioHandle(selectionKey, actualSelectorRunnable, handler, executor, oneshot);
                    selectionKey.attach(handle);
                    holder.set(handle);
                } catch (ClosedChannelException e) {
                    holder.setProblem(e);
                }
            }
        });
        return holder.get();
    }

    NioHandle addConnectHandler(final SelectableChannel channel, final Runnable handler, final boolean oneshot) throws IOException {
        return doAdd(channel, connectors, handler, oneshot, executor);
    }

    NioHandle addConnectHandler(final SelectableChannel channel, final Runnable handler, final boolean oneshot, final Executor executor) throws IOException {
        return doAdd(channel, connectors, handler, oneshot, executor);
    }

    NioHandle addReadHandler(final SelectableChannel channel, final Runnable handler) throws IOException {
        return doAdd(channel, readers, handler, true, executor);
    }

    NioHandle addReadHandler(final SelectableChannel channel, final Runnable handler, final Executor executor) throws IOException {
        return doAdd(channel, readers, handler, true, executor);
    }

    NioHandle addWriteHandler(final SelectableChannel channel, final Runnable handler) throws IOException {
        return doAdd(channel, writers, handler, true, executor);
    }

    NioHandle addWriteHandler(final SelectableChannel channel, final Runnable handler, final Executor executor) throws IOException {
        return doAdd(channel, writers, handler, true, executor);
    }

    void addManaged(Closeable closeable) {
        synchronized (lock) {
            managedSet.add(closeable);
        }
    }

    void removeManaged(Closeable closeable) {
        synchronized (lock) {
            managedSet.remove(closeable);
        }
    }

    private int selectorCacheCount;
    private final Selector[] cache;
    private final Lock selectorCacheLock = new ReentrantLock();

    Selector getSelector() throws IOException {
        selectorCacheLock.lock();
        try {
            final int cnt = selectorCacheCount;
            if (cnt > 0) {
                final Selector[] cache = this.cache;
                try {
                    return cache[cnt - 1];
                } finally {
                    cache[selectorCacheCount = cnt - 1] = null;
                }
            } else {
                return Selector.open();
            }
        } finally {
            selectorCacheLock.unlock();
        }
    }

    void returnSelector(final Selector selector) {
        selectorCacheLock.lock();
        try {
            final int cnt = selectorCacheCount;
            if (cnt == cache.length) {
                IoUtils.safeClose(selector);
            } else {
                // keep frequently used selectors "hot"
                selectorCacheCount = cnt + 1;
                cache[cnt] = selector;
            }
        } finally {
            selectorCacheLock.unlock();
        }
    }

    // private API

    private static IllegalStateException notOpen() {
        return new IllegalStateException("XNIO provider not open");
    }
}
