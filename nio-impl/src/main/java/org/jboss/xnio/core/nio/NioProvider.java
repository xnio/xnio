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
import org.jboss.xnio.spi.Provider;
import org.jboss.xnio.spi.TcpServer;
import org.jboss.xnio.spi.TcpConnector;
import org.jboss.xnio.spi.UdpServer;
import org.jboss.xnio.spi.Pipe;
import org.jboss.xnio.spi.OneWayPipe;
import org.jboss.xnio.spi.Lifecycle;

/**
 *
 */
public final class NioProvider implements Provider, Lifecycle {

    private Executor selectorExecutor;
    private Executor executor;
    private ExecutorService selectorExecutorService;
    private ExecutorService executorService;

    private final Set<NioSelectorRunnable> readers = new HashSet<NioSelectorRunnable>();
    private final Set<NioSelectorRunnable> writers = new HashSet<NioSelectorRunnable>();
    private final Set<NioSelectorRunnable> connectors = new HashSet<NioSelectorRunnable>();

    private int readSelectorThreads = 2;
    private int writeSelectorThreads = 1;
    private int connectionSelectorThreads = 1;

    // dependencies

    public Executor getSelectorExecutor() {
        return selectorExecutor;
    }

    public void setSelectorExecutor(final Executor selectorExecutor) {
        this.selectorExecutor = selectorExecutor;
    }

    public Executor getExecutor() {
        return executor;
    }

    public void setExecutor(final Executor executor) {
        this.executor = executor;
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

    public void create() {
        if (selectorExecutor == null) {
            selectorExecutor = selectorExecutorService = Executors.newFixedThreadPool(readSelectorThreads + writeSelectorThreads + connectionSelectorThreads);
        }
        if (executor == null) {
            executor = executorService = Executors.newCachedThreadPool();
        }
    }

    public void start() throws IOException {
        for (int i = 0; i < readSelectorThreads; i ++) {
            readers.add(new NioSelectorRunnable(executor));
        }
        for (int i = 0; i < writeSelectorThreads; i ++) {
            writers.add(new NioSelectorRunnable(executor));
        }
        for (int i = 0; i < connectionSelectorThreads; i ++) {
            connectors.add(new NioSelectorRunnable(executor));
        }
        for (NioSelectorRunnable runnable : readers) {
            selectorExecutor.execute(runnable);
        }
        for (NioSelectorRunnable runnable : writers) {
            selectorExecutor.execute(runnable);
        }
        for (NioSelectorRunnable runnable : connectors) {
            selectorExecutor.execute(runnable);
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
    }

    public void destroy() {
        selectorExecutor = null;
        executor = null;
        if (selectorExecutorService != null) {
            try {
                selectorExecutorService.shutdown();
            } catch (Throwable t) {
                // todo log @ trace
            } finally {
                selectorExecutorService = null;
            }
        }
        if (executorService != null) {
            try {
                executorService.shutdown();
            } catch (Throwable t) {
                // todo log @ trace
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