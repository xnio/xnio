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

package org.jboss.xnio.core.nio;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.security.AccessController;
import java.security.PrivilegedAction;
import org.jboss.xnio.spi.Provider;
import org.jboss.xnio.spi.TcpServer;
import org.jboss.xnio.spi.TcpConnector;
import org.jboss.xnio.spi.UdpServer;
import org.jboss.xnio.spi.Pipe;
import org.jboss.xnio.spi.OneWayPipe;
import org.jboss.xnio.spi.Lifecycle;
import org.jboss.xnio.log.Logger;

/**
 *
 */
public final class NioProvider implements Provider, Lifecycle {
    private static final Logger log = Logger.getLogger(NioProvider.class);

    private Executor executor;
    private ExecutorService executorService;
    private ThreadFactory selectorThreadFactory;

    private final Set<NioSelectorRunnable> readers = new HashSet<NioSelectorRunnable>();
    private final Set<NioSelectorRunnable> writers = new HashSet<NioSelectorRunnable>();
    private final Set<NioSelectorRunnable> connectors = new HashSet<NioSelectorRunnable>();

    private int readSelectorThreads = 2;
    private int writeSelectorThreads = 1;
    private int connectionSelectorThreads = 1;

    // dependencies

    public Executor getExecutor() {
        return executor;
    }

    public void setExecutor(final Executor executor) {
        this.executor = executor;
    }

    public ThreadFactory getSelectorThreadFactory() {
        return selectorThreadFactory;
    }

    public void setSelectorThreadFactory(final ThreadFactory selectorThreadFactory) {
        this.selectorThreadFactory = selectorThreadFactory;
    }

    // configuration

    public int getReadSelectorThreads() {
        return readSelectorThreads;
    }

    public void setReadSelectorThreads(final int readSelectorThreads) {
        this.readSelectorThreads = readSelectorThreads;
    }

    public int getWriteSelectorThreads() {
        return writeSelectorThreads;
    }

    public void setWriteSelectorThreads(final int writeSelectorThreads) {
        this.writeSelectorThreads = writeSelectorThreads;
    }

    public int getConnectionSelectorThreads() {
        return connectionSelectorThreads;
    }

    public void setConnectionSelectorThreads(final int connectionSelectorThreads) {
        this.connectionSelectorThreads = connectionSelectorThreads;
    }

    // lifecycle

    public void start() throws IOException {
        if (selectorThreadFactory == null) {
            selectorThreadFactory = Executors.defaultThreadFactory();
        }
        if (executor == null) {
            executor = executorService = Executors.newCachedThreadPool();
        }
        for (int i = 0; i < readSelectorThreads; i ++) {
            readers.add(new NioSelectorRunnable());
        }
        for (int i = 0; i < writeSelectorThreads; i ++) {
            writers.add(new NioSelectorRunnable());
        }
        for (int i = 0; i < connectionSelectorThreads; i ++) {
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
    }

    public void stop() throws IOException {
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
        if (executorService != null) {
            try {
                AccessController.doPrivileged(new PrivilegedAction<Void>() {
                    public Void run() {
                        executorService.shutdown();
                        return null;
                    }
                });
            } catch (Throwable t) {
                log.trace(t, "Failed to shut down executor service");
            } finally {
                executorService = null;
            }
        }
    }

    // Provider SPI impl

    public TcpServer createTcpServer() {
        final NioTcpServer tcpServer = new NioTcpServer();
        tcpServer.setNioProvider(this);
        return tcpServer;
    }

    public TcpConnector createTcpConnector() {
        final NioTcpConnector tcpConnector = new NioTcpConnector();
        tcpConnector.setNioProvider(this);
        return tcpConnector;
    }

    public UdpServer createUdpServer() {
        NioUdpServer udpServer = new NioUdpServer();
        udpServer.setNioProvider(this);
        return udpServer;
    }

    public UdpServer createMulticastUdpServer() {
        BioMulticastServer bioMulticastServer = new BioMulticastServer();
        bioMulticastServer.setExecutor(executor);
        return bioMulticastServer;
    }

    public Pipe createPipe() {
        NioPipeConnection pipeConnection = new NioPipeConnection();
        pipeConnection.setNioProvider(this);
        return pipeConnection;
    }

    public OneWayPipe createOneWayPipe() {
        NioOneWayPipeConnection pipeConnection = new NioOneWayPipeConnection();
        pipeConnection.setNioProvider(this);
        return pipeConnection;
    }

    // API

    private NioHandle doAdd(final SelectableChannel channel, final Set<NioSelectorRunnable> runnableSet, final Runnable handler) throws IOException {
        final SynchronousHolder<NioHandle, IOException> holder = new SynchronousHolder<NioHandle, IOException>();
        NioSelectorRunnable nioSelectorRunnable = null;
        int bestLoad = Integer.MAX_VALUE;
        for (NioSelectorRunnable item : runnableSet) {
            final int load = item.getKeyLoad();
            if (load < bestLoad) {
                nioSelectorRunnable = item;
                bestLoad = load;
            }
        }
        if (nioSelectorRunnable == null) {
            throw new IOException("No threads defined to handle this event type");
        }
        final NioSelectorRunnable actualSelectorRunnable = nioSelectorRunnable;
        nioSelectorRunnable.queueTask(new SelectorTask() {
            public void run(final Selector selector) {
                try {
                    final SelectionKey selectionKey = channel.register(selector, 0);
                    final NioHandle handle = new NioHandle(selectionKey, actualSelectorRunnable, handler, executor);
                    selectionKey.attach(handle);
                    holder.set(handle);
                } catch (ClosedChannelException e) {
                    holder.setProblem(e);
                }
            }
        });
        nioSelectorRunnable.wakeup();
        return holder.get();
    }

    public NioHandle addConnectHandler(final SelectableChannel channel, final Runnable handler) throws IOException {
        return doAdd(channel, connectors, handler);
    }

    public NioHandle addReadHandler(final SelectableChannel channel, final Runnable handler) throws IOException {
        return doAdd(channel, readers, handler);
    }

    public NioHandle addWriteHandler(final SelectableChannel channel, final Runnable handler) throws IOException {
        return doAdd(channel, writers, handler);
    }
}