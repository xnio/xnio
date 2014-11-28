/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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

import static java.security.AccessController.doPrivileged;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.PrivilegedAction;
import java.util.List;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.BoundChannel;
import org.xnio.channels.Configurable;
import org.xnio.channels.ConnectedMessageChannel;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.channels.MulticastMessageChannel;
import org.xnio.channels.StreamChannel;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.channels.UnsupportedOptionException;

/**
 * A worker for I/O channel notification.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 *
 * @since 3.0
 */
@SuppressWarnings("unused")
public abstract class XnioWorker extends AbstractExecutorService implements Configurable, ExecutorService {

    private final Xnio xnio;
    private final TaskPool taskPool;
    private final String name;
    private final Runnable terminationTask;

    private final AtomicInteger taskSeq = new AtomicInteger(1);
    private final AtomicInteger coreSize = new AtomicInteger();

    private static final AtomicInteger seq = new AtomicInteger(1);

    /**
     * Construct a new instance.  Intended to be called only from implementations.  To construct an XNIO worker,
     * use the {@link Xnio#createWorker(OptionMap)} method.
     *
     * @param xnio the XNIO provider which produced this worker instance
     * @param threadGroup the thread group for worker threads
     * @param optionMap the option map to use to configure this worker
     * @param terminationTask an optional runnable task to run when the worker shutdown completes
     */
    protected XnioWorker(final Xnio xnio, final ThreadGroup threadGroup, final OptionMap optionMap, final Runnable terminationTask) {
        this.xnio = xnio;
        this.terminationTask = terminationTask;
        String workerName = optionMap.get(Options.WORKER_NAME);
        if (workerName == null) {
            workerName = "XNIO-" + seq.getAndIncrement();
        }
        name = workerName;
        final BlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<Runnable>();
        this.coreSize.set(optionMap.get(Options.WORKER_TASK_CORE_THREADS, 4));
        final boolean markThreadAsDaemon = optionMap.get(Options.THREAD_DAEMON, false);
        final int threadCount = optionMap.get(Options.WORKER_TASK_MAX_THREADS, 16);
        taskPool = new TaskPool(
            threadCount, // ignore core threads setting, always fill to max
            threadCount,
            optionMap.get(Options.WORKER_TASK_KEEPALIVE, 60000), TimeUnit.MILLISECONDS,
            taskQueue,
            new ThreadFactory() {
                public Thread newThread(final Runnable r) {
                    final Thread taskThread = new Thread(threadGroup, r, name + " task-" + taskSeq.getAndIncrement(), optionMap.get(Options.STACK_SIZE, 0L));
                    // Mark the thread as daemon if the Options.THREAD_DAEMON has been set
                    if (markThreadAsDaemon) {
                        taskThread.setDaemon(true);
                    }
                    return taskThread;
                }
            }, new ThreadPoolExecutor.AbortPolicy());
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
     * @param acceptListener the initial accept listener
     * @param optionMap the initial configuration for the server
     * @return the acceptor
     * @throws IOException if the server could not be created
     */
    public AcceptingChannel<? extends ConnectedStreamChannel> createStreamServer(SocketAddress bindAddress, ChannelListener<? super AcceptingChannel<ConnectedStreamChannel>> acceptListener, OptionMap optionMap) throws IOException {
        if (bindAddress == null) {
            throw new IllegalArgumentException("bindAddress is null");
        }
        if (bindAddress instanceof InetSocketAddress) {
            return createTcpServer((InetSocketAddress) bindAddress, acceptListener, optionMap);
        } else if (bindAddress instanceof LocalSocketAddress) {
            return createLocalStreamServer((LocalSocketAddress) bindAddress, acceptListener, optionMap);
        } else {
            throw new UnsupportedOperationException("Unsupported socket address " + bindAddress.getClass());
        }
    }

    /**
     * Implementation helper method to create a TCP stream server.
     *
     * @param bindAddress the address to bind to
     * @param acceptListener the initial accept listener
     * @param optionMap the initial configuration for the server
     * @return the acceptor
     * @throws IOException if the server could not be created
     */
    protected AcceptingChannel<? extends ConnectedStreamChannel> createTcpServer(InetSocketAddress bindAddress, ChannelListener<? super AcceptingChannel<ConnectedStreamChannel>> acceptListener, OptionMap optionMap) throws IOException {
        throw new UnsupportedOperationException("TCP server");
    }

    /**
     * Implementation helper method to create a UNIX domain stream server.
     *
     * @param bindAddress the address to bind to
     * @param acceptListener the initial accept listener
     * @param optionMap the initial configuration for the server
     * @return the acceptor
     * @throws IOException if the server could not be created
     */
    protected AcceptingChannel<? extends ConnectedStreamChannel> createLocalStreamServer(LocalSocketAddress bindAddress, ChannelListener<? super AcceptingChannel<ConnectedStreamChannel>> acceptListener, OptionMap optionMap) throws IOException {
        throw new UnsupportedOperationException("UNIX stream server");
    }

    // Connectors

    /**
     * Connect to a remote stream server.  The protocol family is determined by the type of the socket address given.
     *
     * @param destination the destination address
     * @param openListener the listener which will be notified when the channel is open, or {@code null} for none
     * @param optionMap the option map
     * @return the future result of this operation
     */
    public IoFuture<ConnectedStreamChannel> connectStream(SocketAddress destination, ChannelListener<? super ConnectedStreamChannel> openListener, OptionMap optionMap) {
        if (destination == null) {
            throw new IllegalArgumentException("destination is null");
        }
        if (destination instanceof InetSocketAddress) {
            return connectTcpStream(Xnio.ANY_INET_ADDRESS, (InetSocketAddress) destination, openListener, null, optionMap);
        } else if (destination instanceof LocalSocketAddress) {
            return connectLocalStream(Xnio.ANY_LOCAL_ADDRESS, (LocalSocketAddress) destination, openListener, null, optionMap);
        } else {
            throw new UnsupportedOperationException("Connect to server with socket address " + destination.getClass());
        }
    }

    /**
     * Connect to a remote stream server.  The protocol family is determined by the type of the socket address given.
     *
     * @param destination the destination address
     * @param openListener the listener which will be notified when the channel is open, or {@code null} for none
     * @param bindListener the listener which will be notified when the channel is bound, or {@code null} for none
     * @param optionMap the option map
     * @return the future result of this operation
     */
    public IoFuture<ConnectedStreamChannel> connectStream(SocketAddress destination, ChannelListener<? super ConnectedStreamChannel> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        if (destination == null) {
            throw new IllegalArgumentException("destination is null");
        }
        if (destination instanceof InetSocketAddress) {
            return connectTcpStream(Xnio.ANY_INET_ADDRESS, (InetSocketAddress) destination, openListener, bindListener, optionMap);
        } else if (destination instanceof LocalSocketAddress) {
            return connectLocalStream(Xnio.ANY_LOCAL_ADDRESS, (LocalSocketAddress) destination, openListener, bindListener, optionMap);
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
    public IoFuture<ConnectedStreamChannel> connectStream(SocketAddress bindAddress, SocketAddress destination, ChannelListener<? super ConnectedStreamChannel> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
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
            return connectTcpStream((InetSocketAddress) bindAddress, (InetSocketAddress) destination, openListener, bindListener, optionMap);
        } else if (destination instanceof LocalSocketAddress) {
            return connectLocalStream((LocalSocketAddress) bindAddress, (LocalSocketAddress) destination, openListener, bindListener, optionMap);
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
     * @param optionMap the option map    @return the future result of this operation
     * @return the future result of this operation
     */
    protected IoFuture<ConnectedStreamChannel> connectTcpStream(InetSocketAddress bindAddress, InetSocketAddress destinationAddress, ChannelListener<? super ConnectedStreamChannel> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
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
    protected IoFuture<ConnectedStreamChannel> connectLocalStream(LocalSocketAddress bindAddress, LocalSocketAddress destinationAddress, ChannelListener<? super ConnectedStreamChannel> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        throw new UnsupportedOperationException("Connect to local stream server");
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
    public IoFuture<ConnectedStreamChannel> acceptStream(SocketAddress destination, ChannelListener<? super ConnectedStreamChannel> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        if (destination == null) {
            throw new IllegalArgumentException("destination is null");
        }
        if (destination instanceof InetSocketAddress) {
            return acceptTcpStream((InetSocketAddress) destination, openListener, bindListener, optionMap);
        } else if (destination instanceof LocalSocketAddress) {
            return acceptLocalStream((LocalSocketAddress) destination, openListener, bindListener, optionMap);
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
    protected IoFuture<ConnectedStreamChannel> acceptLocalStream(LocalSocketAddress destination, ChannelListener<? super ConnectedStreamChannel> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
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
    @SuppressWarnings({ "unused" })
    protected IoFuture<ConnectedStreamChannel> acceptTcpStream(InetSocketAddress destination, ChannelListener<? super ConnectedStreamChannel> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        throw new UnsupportedOptionException("Accept a TCP connection");
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
    public IoFuture<ConnectedMessageChannel> connectDatagram(SocketAddress destination, ChannelListener<? super ConnectedMessageChannel> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        if (destination == null) {
            throw new IllegalArgumentException("destination is null");
        }
        if (destination instanceof InetSocketAddress) {
            return connectUdpDatagram(Xnio.ANY_INET_ADDRESS, (InetSocketAddress) destination, openListener, bindListener, optionMap);
        } else if (destination instanceof LocalSocketAddress) {
            return connectLocalDatagram(Xnio.ANY_LOCAL_ADDRESS, (LocalSocketAddress) destination, openListener, bindListener, optionMap);
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
    public IoFuture<ConnectedMessageChannel> connectDatagram(SocketAddress bindAddress, SocketAddress destination, ChannelListener<? super ConnectedMessageChannel> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
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
            return connectUdpDatagram((InetSocketAddress) bindAddress, (InetSocketAddress) destination, openListener, bindListener, optionMap);
        } else if (destination instanceof LocalSocketAddress) {
            return connectLocalDatagram((LocalSocketAddress) bindAddress, (LocalSocketAddress) destination, openListener, bindListener, optionMap);
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
    protected IoFuture<ConnectedMessageChannel> connectUdpDatagram(InetSocketAddress bindAddress, InetSocketAddress destination, ChannelListener<? super ConnectedMessageChannel> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
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
    protected IoFuture<ConnectedMessageChannel> connectLocalDatagram(LocalSocketAddress bindAddress, LocalSocketAddress destination, ChannelListener<? super ConnectedMessageChannel> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        throw new UnsupportedOperationException("Connect to local datagram server");
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
    public IoFuture<ConnectedMessageChannel> acceptDatagram(SocketAddress destination, ChannelListener<? super ConnectedMessageChannel> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        if (destination == null) {
            throw new IllegalArgumentException("destination is null");
        }
        if (destination instanceof LocalSocketAddress) {
            return acceptLocalDatagram((LocalSocketAddress) destination, openListener, bindListener, optionMap);
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
    protected IoFuture<ConnectedMessageChannel> acceptLocalDatagram(LocalSocketAddress destination, ChannelListener<? super ConnectedMessageChannel> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        throw new UnsupportedOptionException("Accept a local message connection");
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
     * @return the UDP server channel
     * @throws IOException if the server could not be created
     *
     * @since 3.0
     */
    public MulticastMessageChannel createUdpServer(InetSocketAddress bindAddress, ChannelListener<? super MulticastMessageChannel> bindListener, OptionMap optionMap) throws IOException {
        throw new UnsupportedOperationException("UDP Server");
    }

    /**
     * Create a UDP server.  The UDP server can be configured to be multicast-capable; this should only be
     * done if multicast is needed, since some providers have a performance penalty associated with multicast.
     * The provider's default executor will be used to execute listener methods.
     *
     * @param bindAddress the bind address
     * @param optionMap the initial configuration for the server
     * @return the UDP server channel
     * @throws IOException if the server could not be created
     *
     * @since 3.0
     */
    public MulticastMessageChannel createUdpServer(InetSocketAddress bindAddress, OptionMap optionMap) throws IOException {
        return createUdpServer(bindAddress, ChannelListeners.nullChannelListener(), optionMap);
    }

    //==================================================
    //
    // Stream pipe methods
    //
    //==================================================

    /**
     * Open a bidirectional stream pipe.
     *
     * @param leftOpenListener the left-hand open listener
     * @param rightOpenListener the right-hand open listener
     * @param optionMap the pipe channel configuration
     * @throws IOException if the pipe could not be created
     */
    public void createPipe(ChannelListener<? super StreamChannel> leftOpenListener, ChannelListener<? super StreamChannel> rightOpenListener, final OptionMap optionMap) throws IOException {
        throw new UnsupportedOperationException("Bi-directional Pipe");
    }

    /**
     * Open a unidirectional stream pipe.
     *
     * @param sourceListener the source open listener
     * @param sinkListener the sink open listener
     * @param optionMap the pipe channel configuration
     * @throws IOException if the pipe could not be created
     */
    public void createOneWayPipe(ChannelListener<? super StreamSourceChannel> sourceListener, ChannelListener<? super StreamSinkChannel> sinkListener, final OptionMap optionMap) throws IOException {
        throw new UnsupportedOperationException("Unidirectional Pipe");
    }

    //==================================================
    //
    // State methods
    //
    //==================================================

    public abstract void shutdown();

    public abstract List<Runnable> shutdownNow();

    public abstract boolean isShutdown();

    public abstract boolean isTerminated();

    public abstract boolean awaitTermination(final long timeout, final TimeUnit unit) throws InterruptedException;

    //==================================================
    //
    // Thread pool methods
    //
    //==================================================

    /**
     * Get the user task to run once termination is complete.
     *
     * @return the termination task
     */
    protected Runnable getTerminationTask() {
        return terminationTask;
    }

    /**
     * Callback to indicate that the task thread pool has terminated.
     */
    protected void taskPoolTerminated() {}

    /**
     * Initiate shutdown of the task thread pool.  When all the tasks and threads have completed,
     * the {@link #taskPoolTerminated()} method is called.
     */
    protected void shutDownTaskPool() {
        doPrivileged(new PrivilegedAction<Object>() {
            public Object run() {
                taskPool.shutdown();
                return null;
            }
        });
    }

    /**
     * Shut down the task thread pool immediately and return its pending tasks.
     *
     * @return the pending task list
     */
    protected List<Runnable> shutDownTaskPoolNow() {
        return doPrivileged(new PrivilegedAction<List<Runnable>>() {
            public List<Runnable> run() {
                return taskPool.shutdownNow();
            }
        });
    }

    /**
     * Execute a command in the task pool.
     *
     * @param command the command to run
     */
    public void execute(final Runnable command) {
        taskPool.execute(command);
    }

    //==================================================
    //
    // Configuration methods
    //
    //==================================================

    private static Set<Option<?>> OPTIONS = Option.setBuilder()
            .add(Options.WORKER_TASK_CORE_THREADS)
            .add(Options.WORKER_TASK_MAX_THREADS)
            .add(Options.WORKER_TASK_KEEPALIVE)
            .create();

    public boolean supportsOption(final Option<?> option) {
        return OPTIONS.contains(option);
    }

    public <T> T getOption(final Option<T> option) throws IOException {
        if (option.equals(Options.WORKER_TASK_CORE_THREADS)) {
            return option.cast(Integer.valueOf(coreSize.get()));
        } else if (option.equals(Options.WORKER_TASK_MAX_THREADS)) {
            return option.cast(Integer.valueOf(taskPool.getMaximumPoolSize()));
        } else if (option.equals(Options.WORKER_TASK_KEEPALIVE)) {
            return option.cast(Long.valueOf(taskPool.getKeepAliveTime(TimeUnit.MILLISECONDS)));
        } else {
            return null;
        }
    }

    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        if (option.equals(Options.WORKER_TASK_CORE_THREADS)) {
            return option.cast(Integer.valueOf(coreSize.getAndSet(Options.WORKER_TASK_CORE_THREADS.cast(value).intValue())));
        } else if (option.equals(Options.WORKER_TASK_MAX_THREADS)) {
            final int old = taskPool.getMaximumPoolSize();
            taskPool.setCorePoolSize(Options.WORKER_TASK_CORE_THREADS.cast(value).intValue());
            taskPool.setMaximumPoolSize(Options.WORKER_TASK_CORE_THREADS.cast(value).intValue());
            return option.cast(Integer.valueOf(old));
        } else if (option.equals(Options.WORKER_TASK_KEEPALIVE)) {
            final long old = taskPool.getKeepAliveTime(TimeUnit.MILLISECONDS);
            taskPool.setKeepAliveTime(Options.WORKER_TASK_KEEPALIVE.cast(value).intValue(), TimeUnit.MILLISECONDS);
            return option.cast(Long.valueOf(old));
        } else {
            return null;
        }
    }

    //==================================================
    //
    // Accessor methods
    //
    //==================================================

    /**
     * Get the XNIO provider which produced this worker.
     *
     * @return the XNIO provider
     */
    public Xnio getXnio() {
        return xnio;
    }

    /**
     * Get the name of this worker.
     *
     * @return the name of the worker
     */
    public String getName() {
        return name;
    }

    final class TaskPool extends ThreadPoolExecutor {

        TaskPool(final int corePoolSize, final int maximumPoolSize, final long keepAliveTime, final TimeUnit unit, final BlockingQueue<Runnable> workQueue, final ThreadFactory threadFactory, final RejectedExecutionHandler handler) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
        }

        protected void terminated() {
            taskPoolTerminated();
        }
    }

}
