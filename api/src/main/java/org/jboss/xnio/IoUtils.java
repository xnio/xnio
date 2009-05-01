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
import java.io.InterruptedIOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.channels.Channel;
import java.nio.channels.Selector;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipFile;
import org.jboss.xnio.channels.StreamChannel;
import org.jboss.xnio.log.Logger;

import java.util.logging.Handler;

/**
 * General I/O utility methods.
 *
 * @apiviz.exclude
 */
public final class IoUtils {

    private static final Executor NULL_EXECUTOR = new Executor() {
        private final String string = String.format("null executor <%s>", Integer.toHexString(hashCode()));

        public void execute(final Runnable command) {
            // no operation
        }

        public String toString() {
            return string;
        }
    };
    private static final Executor DIRECT_EXECUTOR = new Executor() {
        private final String string = String.format("direct executor <%s>", Integer.toHexString(hashCode()));

        public void execute(final Runnable command) {
            command.run();
        }

        public String toString() {
            return string;
        }
    };
    private static final IoHandler<Channel> NULL_HANDLER = new IoHandler<Channel>() {
        private final String string = String.format("null handler <%s>", Integer.toHexString(hashCode()));

        public void handleOpened(final Channel channel) {
        }

        public void handleReadable(final Channel channel) {
        }

        public void handleWritable(final Channel channel) {
        }

        public void handleClosed(final Channel channel) {
        }

        public String toString() {
            return string;
        }
    };
    private static final IoHandlerFactory<Channel> NULL_HANDLER_FACTORY = new IoHandlerFactory<Channel>() {
        private final String string = String.format("null handler factory <%s>", Integer.toHexString(hashCode()));

        public IoHandler<Channel> createHandler() {
            return NULL_HANDLER;
        }

        public String toString() {
            return string;
        }
    };
    private static final Closeable NULL_CLOSEABLE = new Closeable() {
        private final String string = String.format("null closeable <%s>", Integer.toHexString(hashCode()));
        public void close() throws IOException {
            // no operation
        }

        public String toString() {
            return string;
        }
    };

    private IoUtils() {}

    /**
     * Create a persistent connection using a channel source.  The provided handler will handle the connection.  The {@code reconnectExecutor} will
     * be used to execute a reconnect task in the event that the connection fails or is lost or terminated.  If you wish
     * to impose a time delay on reconnect, use the {@link org.jboss.xnio.IoUtils#delayedExecutor(java.util.concurrent.ScheduledExecutorService, long, java.util.concurrent.TimeUnit) delayedExecutor()} method
     * to create a delayed executor.  If you do not want to auto-reconnect use the {@link org.jboss.xnio.IoUtils#nullExecutor()} method to
     * create a null executor.  If you want auto-reconnect to take place immediately, use the {@link IoUtils#directExecutor()} method
     * to create a direct executor.
     *
     * @param channelSource the client to connect on
     * @param handler the handler for the connection
     * @param reconnectExecutor the executor that should execute the reconnect task
     * @param <T> the channel type
     * @return a handle which can be used to terminate the connection
     */
    public static <T extends StreamChannel> Closeable createConnection(final ChannelSource<T> channelSource, final IoHandler<? super T> handler, final Executor reconnectExecutor) {
        final Connection<T> connection = new Connection<T>(channelSource, handler, reconnectExecutor);
        connection.connect();
        return connection;
    }

    /**
     * Create a delayed executor.  This is an executor that executes tasks after a given time delay with the help of the
     * provided {@code ScheduledExecutorService}.  To get an executor for this method, use one of the methods on the
     * {@link java.util.concurrent.Executors} class.
     *
     * @param scheduledExecutorService the executor service to use to schedule the task
     * @param delay the time delay before reconnect
     * @param unit the unit of time to use
     * @return an executor that delays execution
     */
    public static Executor delayedExecutor(final ScheduledExecutorService scheduledExecutorService, final long delay, final TimeUnit unit) {
        return new Executor() {
            public void execute(final Runnable command) {
                scheduledExecutorService.schedule(command, delay, unit);
            }
        };
    }

    /**
     * Get the direct executor.  This is an executor that executes the provided task in the same thread.
     *
     * @return a direct executor
     */
    public static Executor directExecutor() {
        return DIRECT_EXECUTOR;
    }

    /**
     * Get the null executor.  This is an executor that never actually executes the provided task.
     *
     * @return a null executor
     */
    public static Executor nullExecutor() {
        return NULL_EXECUTOR;
    }

    /**
     * Get a closeable executor wrapper for an {@code ExecutorService}.  The given timeout is used to determine how long
     * the {@code close()} method will wait for a clean shutdown before the executor is shut down forcefully.
     *
     * @param executorService the executor service
     * @param timeout the timeout
     * @param unit the unit for the timeout
     * @return a new closeable executor
     */
    public static CloseableExecutor closeableExecutor(final ExecutorService executorService, final long timeout, final TimeUnit unit) {
        return new CloseableExecutor() {
            public void close() throws IOException {
                executorService.shutdown();
                try {
                    if (executorService.awaitTermination(timeout, unit)) {
                        return;
                    }
                    executorService.shutdownNow();
                    throw new IOException("Executor did not shut down cleanly (killed)");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    executorService.shutdownNow();
                    throw new InterruptedIOException("Interrupted while awaiting executor shutdown");
                }
            }

            public void execute(final Runnable command) {
                executorService.execute(command);
            }
        };
    }

    /**
     * Get the null closeable.  This is a simple {@code Closeable} instance that does nothing when its {@code close()}
     * method is invoked.
     *
     * @return the null closeable
     */
    public static Closeable nullCloseable() {
        return NULL_CLOSEABLE;
    }

    /**
     * Get the null handler.  This is a handler whose handler methods all return without taking any action.
     *
     * @return the null handler
     */
    public static IoHandler<Channel> nullHandler() {
        return NULL_HANDLER;
    }

    /**
     * Get the null handler factory.  This is a handler factory that returns the null handler.
     *
     * @return the null handler factory
     */
    public static IoHandlerFactory<Channel> nullHandlerFactory() {
        return NULL_HANDLER_FACTORY;
    }

    /**
     * Get a singleton handler factory that returns the given handler one time only.
     *
     * @param handler the handler to return
     * @return a singleton handler factory
     */
    public static <T extends Channel> IoHandlerFactory<T> singletonHandlerFactory(final IoHandler<T> handler) {
        final AtomicReference<IoHandler<T>> reference = new AtomicReference<IoHandler<T>>(handler);
        return new IoHandlerFactory<T>() {
            public IoHandler<? super T> createHandler() {
                final IoHandler<T> handler = reference.getAndSet(null);
                if (handler == null) {
                    throw new IllegalStateException("Handler already taken from singleton handler factory");
                }
                return handler;
            }
        };
    }

    private static final Logger closeLog = Logger.getLogger("org.jboss.xnio.safe-close");

    /**
     * Close a resource, logging an error if an error occurs.
     *
     * @param resource the resource to close
     */
    public static void safeClose(final Closeable resource) {
        try {
            if (resource != null) {
                resource.close();
            }
        } catch (Throwable t) {
            closeLog.trace(t, "Closing resource failed");
        }
    }

    /**
     * Close a resource, logging an error if an error occurs.
     *
     * @param resource the resource to close
     */
    public static void safeClose(final Socket resource) {
        try {
            if (resource != null) {
                resource.close();
            }
        } catch (Throwable t) {
            closeLog.trace(t, "Closing resource failed");
        }
    }

    /**
     * Close a resource, logging an error if an error occurs.
     *
     * @param resource the resource to close
     */
    public static void safeClose(final DatagramSocket resource) {
        try {
            if (resource != null) {
                resource.close();
            }
        } catch (Throwable t) {
            closeLog.trace(t, "Closing resource failed");
        }
    }

    /**
     * Close a resource, logging an error if an error occurs.
     *
     * @param resource the resource to close
     */
    public static void safeClose(final Selector resource) {
        try {
            if (resource != null) {
                resource.close();
            }
        } catch (Throwable t) {
            closeLog.trace(t, "Closing resource failed");
        }
    }

    /**
     * Close a resource, logging an error if an error occurs.
     *
     * @param resource the resource to close
     */
    public static void safeClose(final ServerSocket resource) {
        try {
            if (resource != null) {
                resource.close();
            }
        } catch (Throwable t) {
            closeLog.trace(t, "Closing resource failed");
        }
    }

    /**
     * Close a resource, logging an error if an error occurs.
     *
     * @param resource the resource to close
     */
    public static void safeClose(final ZipFile resource) {
        try {
            if (resource != null) {
                resource.close();
            }
        } catch (Throwable t) {
            closeLog.trace(t, "Closing resource failed");
        }
    }

    /**
     * Close a resource, logging an error if an error occurs.
     *
     * @param resource the resource to close
     */
    public static void safeClose(final Handler resource) {
        try {
            if (resource != null) {
                resource.close();
            }
        } catch (Throwable t) {
            closeLog.trace(t, "Closing resource failed");
        }
    }

    /**
     * Close a future resource, logging an error if an error occurs.  Attempts to cancel the operation if it is
     * still in progress.
     *
     * @param futureResource the resource to close
     */
    public static void safeClose(final IoFuture<? extends Closeable> futureResource) {
        futureResource.cancel().addNotifier(closingNotifier(), null);
    }

    private static final IoFuture.Notifier<Object, Closeable> ATTACHMENT_CLOSING_NOTIFIER = new IoFuture.Notifier<Object, Closeable>() {
        public void notify(final IoFuture<?> future, final Closeable attachment) {
            IoUtils.safeClose(attachment);
        }
    };

    private static final IoFuture.Notifier<Closeable, Void> CLOSING_NOTIFIER = new IoFuture.HandlingNotifier<Closeable, Void>() {
        public void handleDone(final Closeable result, final Void attachment) {
            IoUtils.safeClose(result);
        }
    };

    /**
     * Get a notifier that closes the attachment.
     *
     * @return a notifier which will close its attachment
     */
    public static IoFuture.Notifier<Object, Closeable> attachmentClosingNotifier() {
        return ATTACHMENT_CLOSING_NOTIFIER;
    }

    /**
     * Get a notifier that closes the result.
     *
     * @return a notifier which will close the result of the operation (if successful)
     */
    public static IoFuture.Notifier<Closeable, Void> closingNotifier() {
        return CLOSING_NOTIFIER;
    }

    /**
     * Get a notifier that runs the supplied action.
     *
     * @param runnable the notifier type
     * @param <T> the future type (not used)
     * @return a notifier which will run the given command
     */
    public static <T> IoFuture.Notifier<T, Void> runnableNotifier(final Runnable runnable) {
        return new IoFuture.Notifier<T, Void>() {
            public void notify(final IoFuture<? extends T> future, final Void attachment) {
                runnable.run();
            }
        };
    }

    /**
     * Get a {@code java.util.concurrent}-style {@code Future} instance wrapper for an {@code IoFuture} instance.
     *
     * @param ioFuture the {@code IoFuture} to wrap
     * @return a {@code Future}
     */
    public static <T> Future<T> getFuture(final IoFuture<T> ioFuture) {
        return new Future<T>() {
            public boolean cancel(final boolean mayInterruptIfRunning) {
                ioFuture.cancel();
                return ioFuture.await() == IoFuture.Status.CANCELLED;
            }

            public boolean isCancelled() {
                return ioFuture.getStatus() == IoFuture.Status.CANCELLED;
            }

            public boolean isDone() {
                return ioFuture.getStatus() == IoFuture.Status.DONE;
            }

            public T get() throws InterruptedException, ExecutionException {
                try {
                    return ioFuture.getInterruptibly();
                } catch (IOException e) {
                    throw new ExecutionException(e);
                }
            }

            public T get(final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                try {
                    if (ioFuture.awaitInterruptibly(timeout, unit) == IoFuture.Status.WAITING) {
                        throw new TimeoutException("Operation timed out");
                    }
                    return ioFuture.getInterruptibly();
                } catch (IOException e) {
                    throw new ExecutionException(e);
                }
            }

            public String toString() {
                return String.format("java.util.concurrent.Future wrapper <%s> for %s", Integer.toHexString(hashCode()), ioFuture);
            }
        };
    }

    private static final IoFuture.Notifier<Object, CountDownLatch> COUNT_DOWN_NOTIFIER = new IoFuture.Notifier<Object, CountDownLatch>() {
        public void notify(final IoFuture<?> future, final CountDownLatch latch) {
            latch.countDown();
        }
    };

    public static void awaitAll(IoFuture<?>... futures) {
        final int len = futures.length;
        final CountDownLatch cdl = new CountDownLatch(len);
        for (IoFuture<?> future : futures) {
            future.addNotifier(COUNT_DOWN_NOTIFIER, cdl);
        }
        boolean intr = false;
        try {
            while (cdl.getCount() > 0L) {
                try {
                    cdl.await();
                } catch (InterruptedException e) {
                    intr = true;
                }
            }
        } finally {
            if (intr) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static void awaitAllInterruptibly(IoFuture<?>... futures) throws InterruptedException {
        final int len = futures.length;
        final CountDownLatch cdl = new CountDownLatch(len);
        for (IoFuture<?> future : futures) {
            future.addNotifier(COUNT_DOWN_NOTIFIER, cdl);
        }
        cdl.await();
    }

    /**
     * Create an {@code IoFuture} which wraps another {@code IoFuture}, but returns a different type.
     *
     * @param parent the original {@code IoFuture}
     * @param type the class of the new {@code IoFuture}
     * @param <I> the type of the original result
     * @param <O> the type of the wrapped result
     * @return a wrapper {@code IoFuture}
     */
    public static <I, O> IoFuture<O> cast(final IoFuture<I> parent, final Class<O> type) {
        return new CastingIoFuture<O, I>(parent, type);
    }

    // nested classes

    private static final Logger connLog = Logger.getLogger("org.jboss.xnio.connection");

    private static final class Connection<T extends StreamChannel> implements Closeable {

        private final ChannelSource<T> channelSource;
        private final IoHandler<? super T> handler;
        private final Executor reconnectExecutor;

        private volatile boolean stopFlag = false;
        private volatile IoFuture<T> currentFuture;

        private final NotifierImpl<?> notifier = new NotifierImpl<Void>();
        private final HandlerImpl handlerWrapper = new HandlerImpl();
        private final ReconnectTask reconnectTask = new ReconnectTask();

        private Connection(final ChannelSource<T> channelSource, final IoHandler<? super T> handler, final Executor reconnectExecutor) {
            this.channelSource = channelSource;
            this.handler = handler;
            this.reconnectExecutor = reconnectExecutor;
        }

        private void connect() {
            closeLog.trace("Establishing connection");
            final IoFuture<T> ioFuture = channelSource.open(handlerWrapper);
            ioFuture.addNotifier(notifier, null);
            currentFuture = ioFuture;
        }

        public void close() throws IOException {
            stopFlag = true;
            final IoFuture<T> future = currentFuture;
            if (future != null) {
                future.cancel();
            }
        }

        public String toString() {
            return String.format("persistent connection <%s> via %s", Integer.toHexString(hashCode()), channelSource);
        }

        private final class NotifierImpl<A> extends IoFuture.HandlingNotifier<T, A> {

            public void handleCancelled(final A attachment) {
                connLog.trace("Connection cancelled");
            }

            public void handleFailed(final IOException exception, final A attachment) {
                connLog.trace(exception, "Connection failed");
            }

            public void handleDone(final T result, final A attachment) {
                connLog.trace("Connection established");
            }

            public void notify(final IoFuture<? extends T> future, final A attachment) {
                super.notify(future, attachment);
                if (! stopFlag) {
                    reconnectExecutor.execute(reconnectTask);
                }
                return;
            }
        }

        private final class HandlerImpl implements IoHandler<T> {
            public void handleOpened(final T channel) {
                handler.handleOpened(channel);
            }

            public void handleReadable(final T channel) {
                handler.handleReadable(channel);
            }

            public void handleWritable(final T channel) {
                handler.handleWritable(channel);
            }

            public void handleClosed(final T channel) {
                try {
                    connLog.trace("Connection closed");
                    if (! stopFlag) {
                        reconnectExecutor.execute(reconnectTask);
                    }
                } finally {
                    handler.handleClosed(channel);
                }
            }

            public String toString() {
                return String.format("persistent connection handler <%s> wrapping %s", Integer.toHexString(hashCode()), handler);
            }
        }

        private final class ReconnectTask implements Runnable {
            public void run() {
                if (! stopFlag) {
                    connect();
                }
            }

            public String toString() {
                return String.format("reconnect task <%s> for %s", Integer.toHexString(hashCode()), Connection.this);
            }
        }
    }

    private static class CastingIoFuture<O, I> implements IoFuture<O> {

        private final IoFuture<I> parent;
        private final Class<O> type;

        private CastingIoFuture(final IoFuture<I> parent, final Class<O> type) {
            this.parent = parent;
            this.type = type;
        }

        public IoFuture<O> cancel() {
            parent.cancel();
            return this;
        }

        public Status getStatus() {
            return parent.getStatus();
        }

        public Status await() {
            return parent.await();
        }

        public Status await(final long time, final TimeUnit timeUnit) {
            return parent.await(time, timeUnit);
        }

        public Status awaitInterruptibly() throws InterruptedException {
            return parent.awaitInterruptibly();
        }

        public Status awaitInterruptibly(final long time, final TimeUnit timeUnit) throws InterruptedException {
            return parent.awaitInterruptibly(time, timeUnit);
        }

        public O get() throws IOException, CancellationException {
            return type.cast(parent.get());
        }

        public O getInterruptibly() throws IOException, InterruptedException, CancellationException {
            return type.cast(parent.getInterruptibly());
        }

        public IOException getException() throws IllegalStateException {
            return parent.getException();
        }

        public <A> IoFuture<O> addNotifier(final Notifier<? super O, A> notifier, final A attachment) {
            parent.<A>addNotifier(new Notifier<I, A>() {
                public void notify(final IoFuture<? extends I> future, final A attachment) {
                    notifier.notify((IoFuture<O>)CastingIoFuture.this, attachment);
                }
            }, attachment);
            return this;
        }
    }

    /**
     * Get a URI connector which connects to the Internet address specified in the given URI.
     *
     * @param original the Internet address connector
     * @param defaultPort the default port to use if none is given in the URI
     * @return the URI connector
     *
     * @since 1.3
     */
    public static <T extends Channel> BoundConnector<URI, T> inetUriConnector(final BoundConnector<InetSocketAddress, T> original, final int defaultPort) {
        return new BoundConnector<URI, T>() {
            public FutureConnection<URI, T> connectTo(final URI dest, final IoHandler<? super T> ioHandler) {
                final FutureConnection<InetSocketAddress, T> futureConnection = original.connectTo(getSockAddr(dest), ioHandler);
                return new UriFutureConnection<T>(futureConnection);
            }

            public ChannelSource<T> createChannelSource(final URI dest) {
                return original.createChannelSource(getSockAddr(dest));
            }

            private InetSocketAddress getSockAddr(final URI dest) {
                final String destHost = dest.getHost();
                final int destPort = dest.getPort();
                final InetSocketAddress destSockAddr = new InetSocketAddress(destHost, destPort == -1 ? defaultPort : destPort);
                return destSockAddr;
            }
        };
    }

    private static class UriFutureConnection<T extends Channel> implements FutureConnection<URI, T> {

        private final FutureConnection<InetSocketAddress, T> futureConnection;

        private UriFutureConnection(final FutureConnection<InetSocketAddress, T> futureConnection) {
            this.futureConnection = futureConnection;
        }

        public URI getLocalAddress() {
            throw new UnsupportedOperationException();
        }

        public FutureConnection<URI, T> cancel() {
            futureConnection.cancel();
            return this;
        }

        public Status getStatus() {
            return futureConnection.getStatus();
        }

        public Status await() {
            return futureConnection.await();
        }

        public Status await(final long time, final TimeUnit timeUnit) {
            return futureConnection.await(time, timeUnit);
        }

        public Status awaitInterruptibly() throws InterruptedException {
            return futureConnection.awaitInterruptibly();
        }

        public Status awaitInterruptibly(final long time, final TimeUnit timeUnit) throws InterruptedException {
            return futureConnection.awaitInterruptibly(time, timeUnit);
        }

        public T get() throws IOException, CancellationException {
            return futureConnection.get();
        }

        public T getInterruptibly() throws IOException, InterruptedException, CancellationException {
            return futureConnection.getInterruptibly();
        }

        public IOException getException() throws IllegalStateException {
            return futureConnection.getException();
        }

        public <A> IoFuture<T> addNotifier(final Notifier<? super T, A> aNotifier, final A attachment) {
            return futureConnection.addNotifier(aNotifier, attachment);
        }
    }

    /**
     * Get a bound connector for a certain source address.
     *
     * @param connector the connector
     * @param src the source address
     * @param <A> the address type
     * @param <T> the channel type
     * @return the bound connector
     *
     * @since 1.3
     */
    public static <A, T extends Channel> BoundConnector<A, T> bindConnector(final Connector<A, T> connector, final A src) {
        return new BoundConnector<A, T>() {
            public FutureConnection<A, T> connectTo(final A dest, final IoHandler<? super T> ioHandler) {
                return connector.connectTo(src, dest, ioHandler);
            }

            public ChannelSource<T> createChannelSource(final A dest) {
                return connector.createChannelSource(src, dest);
            }
        };
    }
}
