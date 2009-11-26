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
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.PrivilegedAction;
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
import java.net.InetSocketAddress;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import org.jboss.xnio.ChannelSource;
import org.jboss.xnio.FailedIoFuture;
import org.jboss.xnio.FinishedIoFuture;
import org.jboss.xnio.IoFuture;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.TcpAcceptor;
import org.jboss.xnio.TcpConnector;
import org.jboss.xnio.TcpServer;
import org.jboss.xnio.UdpServer;
import org.jboss.xnio.Version;
import org.jboss.xnio.Xnio;
import org.jboss.xnio.OptionMap;
import org.jboss.xnio.ChannelListener;
import org.jboss.xnio.Options;
import org.jboss.xnio.XnioConfiguration;
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

    private interface SelectorCreator {
        Selector open() throws IOException;
    }

    private final SelectorCreator selectorCreator;

    static {
        log.info("XNIO NIO Implementation Version " + Version.VERSION);
    }

    private final Object lock = new Object();

    /**
     * @protectedby lock
     */
    private volatile boolean closed;

    private final List<NioSelectorRunnable> readers = new ArrayList<NioSelectorRunnable>();
    private final List<NioSelectorRunnable> writers = new ArrayList<NioSelectorRunnable>();
    private final List<NioSelectorRunnable> connectors = new ArrayList<NioSelectorRunnable>();

    /**
     * @protectedby lock
     */
    private final Set<Closeable> managedSet = new HashSet<Closeable>();

    private static final class CreateAction implements PrivilegedExceptionAction<NioXnio> {
        private final XnioConfiguration configuration;

        public CreateAction(final XnioConfiguration configuration) {
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
    public static Xnio create(XnioConfiguration configuration) throws IOException {
        return doCreate(configuration);
    }

    private static NioXnio doCreate(final XnioConfiguration configuration) throws IOException {
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
        return doCreate(new XnioConfiguration());
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
        final XnioConfiguration configuration = new XnioConfiguration();
        configuration.setOptionMap(OptionMap.builder().set(Options.READ_THREADS, readSelectorThreads).set(Options.WRITE_THREADS, writeSelectorThreads).set(Options.CONNECT_THREADS, connectSelectorThreads).getMap());
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
        final XnioConfiguration configuration = new XnioConfiguration();
        configuration.setExecutor(handlerExecutor);
        configuration.setOptionMap(OptionMap.builder().set(Options.READ_THREADS, readSelectorThreads).set(Options.WRITE_THREADS, writeSelectorThreads).set(Options.CONNECT_THREADS, connectSelectorThreads).getMap());
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
        final XnioConfiguration configuration = new XnioConfiguration();
        configuration.setExecutor(handlerExecutor);
        configuration.setThreadFactory(selectorThreadFactory);
        configuration.setOptionMap(OptionMap.builder().set(Options.READ_THREADS, readSelectorThreads).set(Options.WRITE_THREADS, writeSelectorThreads).set(Options.CONNECT_THREADS, connectSelectorThreads).getMap());
        return doCreate(configuration);
    }

    private NioXnio(XnioConfiguration configuration) throws IOException {
        super(configuration);
        final String providerClassName = SelectorProvider.provider().getClass().getCanonicalName();
        if ("sun.nio.ch.PollSelectorProvider".equals(providerClassName)) {
            log.warn("The currently defined selector provider class (%s) is not supported for use with XNIO", providerClassName);
        }
        log.trace("Starting up with selector provider %s", providerClassName);
        selectorCreator = AccessController.doPrivileged(
            new PrivilegedAction<SelectorCreator>() {
                public SelectorCreator run() {
                    try {
                        // A Polling selector is most efficient on most platforms for one-off selectors.  Try to hack a way to get them on demand.
                        final Class<? extends Selector> selectorImplClass = Class.forName("sun.nio.ch.PollSelectorImpl").asSubclass(Selector.class);
                        final Constructor<? extends Selector> constructor = selectorImplClass.getDeclaredConstructor(SelectorProvider.class);
                        // Usually package private.  So untrusting.
                        constructor.setAccessible(true);
                        log.trace("Using polling selector type for temporary selectors.");
                        return new SelectorCreator() {
                            public Selector open() throws IOException {
                                try {
                                    return constructor.newInstance(SelectorProvider.provider());
                                } catch (InstantiationException e) {
                                    return Selector.open();
                                } catch (IllegalAccessException e) {
                                    return Selector.open();
                                } catch (InvocationTargetException e) {
                                    try {
                                        throw e.getTargetException();
                                    } catch (java.io.IOException e2) {
                                        throw e2;
                                    } catch (RuntimeException e2) {
                                        throw e2;
                                    } catch (Throwable t) {
                                        throw new IllegalStateException("Unexpected invocation exception", t);
                                    }
                                }
                            }
                        };
                    } catch (Exception e) {
                        // ignore.
                    }
                    // Can't get our selector type?  That's OK, just use the default.
                    log.trace("Using default selector type for temporary selectors.");
                    return new SelectorCreator() {
                        public Selector open() throws IOException {
                            return Selector.open();
                        }
                    };
                }
            }
        );
        ThreadFactory selectorThreadFactory = configuration.getThreadFactory();
        final OptionMap optionMap = getOptionMap(configuration);
        final int readSelectorThreads = optionMap.get(Options.READ_THREADS, 1);
        final int writeSelectorThreads = optionMap.get(Options.WRITE_THREADS, 1);
        final int connectSelectorThreads = optionMap.get(Options.CONNECT_THREADS, 1);
        final int selectorCacheSize = optionMap.get(Options.SELECTOR_CACHE_SIZE, 30);
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
            log.debug("Creating NioXnio instance using %d read threads, %d write threads, %d connect threads", Integer.valueOf(readSelectorThreads), Integer.valueOf(writeSelectorThreads), Integer.valueOf(connectSelectorThreads));
        }
    }

    private static OptionMap getOptionMap(final XnioConfiguration configuration) {
        final OptionMap optionMap = configuration.getOptionMap();
        return optionMap == null ? OptionMap.EMPTY : optionMap;
    }

    /** {@inheritDoc} */
    public TcpServer createTcpServer(final Executor executor, final ChannelListener<? super TcpChannel> openHandler, final OptionMap optionMap) {
        if (executor == null) {
            throw new NullPointerException("executor is null");
        }
        if (openHandler == null) {
            throw new NullPointerException("openHandler is null");
        }
        if (optionMap == null) {
            throw new NullPointerException("optionMap is null");
        }
        synchronized (lock) {
            if (closed) {
                throw notOpen();
            }
            return NioTcpServer.create(this, executor, openHandler, optionMap);
        }
    }

    /** {@inheritDoc} */
    public TcpConnector createTcpConnector(final Executor executor, final InetSocketAddress src, final OptionMap optionMap) {
        if (executor == null) {
            throw new NullPointerException("executor is null");
        }
        if (optionMap == null) {
            throw new NullPointerException("optionMap is null");
        }
        synchronized (lock) {
            if (closed) {
                throw notOpen();
            }
            return NioTcpConnector.create(this, executor, optionMap, src);
        }
    }

    /** {@inheritDoc} */
    public UdpServer createUdpServer(final Executor executor, final ChannelListener<? super UdpChannel> openHandler, final OptionMap optionMap) {
        if (executor == null) {
            throw new NullPointerException("executor is null");
        }
        if (openHandler == null) {
            throw new NullPointerException("openHandler is null");
        }
        if (optionMap == null) {
            throw new NullPointerException("optionMap is null");
        }
        synchronized (lock) {
            if (closed) {
                throw notOpen();
            }
            if (optionMap.contains(Options.MULTICAST) && optionMap.get(Options.MULTICAST).booleanValue()) {
                return new BioUdpServer(this, executor, openHandler, optionMap);
            } else {
                return new NioUdpServer(this, executor, openHandler, optionMap);
            }
        }
    }

    public ChannelSource<? extends StreamChannel> createPipeServer(final Executor executor, final ChannelListener<? super StreamChannel> leftOpenListener) {
        if (executor == null) {
            throw new NullPointerException("executor is null");
        }
        if (leftOpenListener == null) {
            throw new NullPointerException("openHandler is null");
        }
        synchronized (lock) {
            if (closed) {
                throw notOpen();
            }
            return new ChannelSource<StreamChannel>() {
                public IoFuture<? extends StreamChannel> open(final ChannelListener<? super StreamChannel> rightOpenListener) {
                    synchronized (lock) {
                        if (closed) {
                            throw notOpen();
                        }
                        final NioPipeConnection nioPipeConnection;
                        try {
                            //noinspection unchecked
                            nioPipeConnection = new NioPipeConnection(NioXnio.this);
                            if (! IoUtils.<StreamChannel>invokeChannelListener(nioPipeConnection.getLeftSide(), leftOpenListener) ||
                                    ! IoUtils.<StreamChannel>invokeChannelListener(nioPipeConnection.getRightSide(), rightOpenListener)) {
                                IoUtils.safeClose(nioPipeConnection);
                            }
                        } catch (IOException e) {
                            return new FailedIoFuture<StreamChannel>(e);
                        }
                        return new FinishedIoFuture<StreamChannel>(nioPipeConnection.getLeftSide());
                    }
                }
            };
        }
    }

    public ChannelSource<? extends StreamSourceChannel> createPipeSourceServer(final Executor executor, final ChannelListener<? super StreamSinkChannel> sinkOpenListener) {
        if (executor == null) {
            throw new NullPointerException("executor is null");
        }
        if (sinkOpenListener == null) {
            throw new NullPointerException("sinkOpenListener is null");
        }
        synchronized (lock) {
            if (closed) {
                throw notOpen();
            }
            return new ChannelSource<StreamSourceChannel>() {
                public IoFuture<? extends StreamSourceChannel> open(final ChannelListener<? super StreamSourceChannel> sourceOpenListener) {
                    synchronized (lock) {
                        if (closed) {
                            throw notOpen();
                        }
                        final NioOneWayPipeConnection nioPipeConnection;
                        try {
                            //noinspection unchecked
                            nioPipeConnection = new NioOneWayPipeConnection(NioXnio.this);
                            if (! IoUtils.<StreamSinkChannel>invokeChannelListener(nioPipeConnection.getSinkSide(), sinkOpenListener) ||
                                    ! IoUtils.<StreamSourceChannel>invokeChannelListener(nioPipeConnection.getSourceSide(), sourceOpenListener)) {
                                IoUtils.safeClose(nioPipeConnection);
                            }
                        } catch (IOException e) {
                            return new FailedIoFuture<StreamSourceChannel>(e);
                        }
                        return new FinishedIoFuture<StreamSourceChannel>(nioPipeConnection.getSourceSide());
                    }
                }
            };
        }
    }

    public ChannelSource<? extends StreamSinkChannel> createPipeSinkServer(final Executor executor, final ChannelListener<? super StreamSourceChannel> sourceOpenListener) {
        if (executor == null) {
            throw new NullPointerException("executor is null");
        }
        if (sourceOpenListener == null) {
            throw new NullPointerException("sourceOpenListener is null");
        }
        synchronized (lock) {
            if (closed) {
                throw notOpen();
            }
            return new ChannelSource<StreamSinkChannel>() {
                public IoFuture<? extends StreamSinkChannel> open(final ChannelListener<? super StreamSinkChannel> sinkOpenListener) {
                    synchronized (lock) {
                        if (closed) {
                            throw notOpen();
                        }
                        final NioOneWayPipeConnection nioPipeConnection;
                        try {
                            //noinspection unchecked
                            nioPipeConnection = new NioOneWayPipeConnection(NioXnio.this);
                            if (! IoUtils.<StreamSourceChannel>invokeChannelListener(nioPipeConnection.getSourceSide(), sourceOpenListener) ||
                                    ! IoUtils.<StreamSinkChannel>invokeChannelListener(nioPipeConnection.getSinkSide(), sinkOpenListener)) {
                                IoUtils.safeClose(nioPipeConnection);
                            }
                        } catch (IOException e) {
                            return new FailedIoFuture<StreamSinkChannel>(e);
                        }
                        return new FinishedIoFuture<StreamSinkChannel>(nioPipeConnection.getSinkSide());
                    }
                }
            };
        }
    }

    public IoFuture<? extends Closeable> createPipeConnection(final Executor executor, final ChannelListener<? super StreamChannel> leftHandler, final ChannelListener<? super StreamChannel> rightHandler) {
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
                final NioPipeConnection connection = new NioPipeConnection(this);
                if (! IoUtils.<StreamChannel>invokeChannelListener(connection.getLeftSide(), leftHandler) ||
                        ! IoUtils.<StreamChannel>invokeChannelListener(connection.getRightSide(), rightHandler)) {
                    IoUtils.safeClose(connection);
                }
                return new FinishedIoFuture<Closeable>(connection);
            } catch (IOException e) {
                return new FailedIoFuture<Closeable>(e);
            }
        }
    }

    public IoFuture<? extends Closeable> createOneWayPipeConnection(final Executor executor, final ChannelListener<? super StreamSourceChannel> sourceHandler, final ChannelListener<? super StreamSinkChannel> sinkHandler) {
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
                final NioOneWayPipeConnection connection = new NioOneWayPipeConnection(this);
                if (! IoUtils.<StreamSourceChannel>invokeChannelListener(connection.getSourceSide(), sourceHandler) ||
                        ! IoUtils.<StreamSinkChannel>invokeChannelListener(connection.getSinkSide(), sinkHandler)) {
                    IoUtils.safeClose(connection);
                }
                return new FinishedIoFuture<Closeable>(connection);
            } catch (IOException e) {
                return new FailedIoFuture<Closeable>(e);
            }
        }
    }

    public TcpAcceptor createTcpAcceptor(final Executor executor, final OptionMap optionMap) {
        if (executor == null) {
            throw new NullPointerException("executor is null");
        }
        if (optionMap == null) {
            throw new NullPointerException("optionMap is null");
        }
        synchronized (lock) {
            if (closed) {
                throw notOpen();
            }
            return NioTcpAcceptor.create(this, executor, optionMap);
        }
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
        return doAdd(channel, connectors, handler, oneshot, getExecutor());
    }

    NioHandle addConnectHandler(final SelectableChannel channel, final Runnable handler, final boolean oneshot, final Executor executor) throws IOException {
        return doAdd(channel, connectors, handler, oneshot, executor);
    }

    NioHandle addReadHandler(final SelectableChannel channel, final Runnable handler) throws IOException {
        return doAdd(channel, readers, handler, true, getExecutor());
    }

    NioHandle addReadHandler(final SelectableChannel channel, final Runnable handler, final Executor executor) throws IOException {
        return doAdd(channel, readers, handler, true, executor);
    }

    NioHandle addWriteHandler(final SelectableChannel channel, final Runnable handler) throws IOException {
        return doAdd(channel, writers, handler, true, getExecutor());
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
                return selectorCreator.open();
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
