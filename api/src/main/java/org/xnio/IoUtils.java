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
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.Selector;
import java.nio.channels.Channel;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.zip.ZipFile;
import org.jboss.logging.Logger;
import org.xnio.channels.BoundChannel;

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
    private static final Closeable NULL_CLOSEABLE = new Closeable() {
        private final String string = String.format("null closeable <%s>", Integer.toHexString(hashCode()));
        public void close() throws IOException {
            // no operation
        }

        public String toString() {
            return string;
        }
    };
    private static final Cancellable NULL_CANCELLABLE = new Cancellable() {
        public Cancellable cancel() {
            return this;
        }
    };
    private static final IoUtils.ResultNotifier RESULT_NOTIFIER = new IoUtils.ResultNotifier();

    private IoUtils() {}

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
     * Get the null closeable.  This is a simple {@code Closeable} instance that does nothing when its {@code close()}
     * method is invoked.
     *
     * @return the null closeable
     */
    public static Closeable nullCloseable() {
        return NULL_CLOSEABLE;
    }

    private static final Logger closeLog = Logger.getLogger("org.xnio.safe-close");

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
            closeLog.tracef(t, "Closing resource failed");
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
            closeLog.tracef(t, "Closing resource failed");
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
            closeLog.tracef(t, "Closing resource failed");
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
            closeLog.tracef(t, "Closing resource failed");
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
            closeLog.tracef(t, "Closing resource failed");
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
            closeLog.tracef(t, "Closing resource failed");
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
            closeLog.tracef(t, "Closing resource failed");
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
     * Get the result notifier.  This notifier will forward the result of the {@code IoFuture} to the attached
     * {@code Result}.
     *
     * @param <T> the result type
     * @return the notifier
     */
    @SuppressWarnings({ "unchecked" })
    public static <T> IoFuture.Notifier<T, Result<T>> resultNotifier() {
        return RESULT_NOTIFIER;
    }

    /**
     * Get the notifier that invokes the channel listener given as an attachment.
     *
     * @param <T> the channel type
     * @return the notifier
     */
    @SuppressWarnings({ "unchecked" })
    public static <T extends Channel> IoFuture.Notifier<T, ChannelListener<? super T>> channelListenerNotifier() {
        return CHANNEL_LISTENER_NOTIFIER;
    }

    private static final IoFuture.Notifier CHANNEL_LISTENER_NOTIFIER = new IoFuture.HandlingNotifier<Channel, ChannelListener<? super Channel>>() {
        @SuppressWarnings({ "unchecked" })
        public void handleDone(final Channel channel, final ChannelListener channelListener) {
            channelListener.handleEvent(channel);
        }
    };

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

    /**
     * Wait for all the futures to complete.
     *
     * @param futures the futures to wait for
     */
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

    /**
     * Wait for all the futures to complete.
     *
     * @param futures the futures to wait for
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
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
    public static <I, O> IoFuture<? extends O> cast(final IoFuture<I> parent, final Class<O> type) {
        return new CastingIoFuture<O, I>(parent, type);
    }

    // nested classes

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
     * Get a notifier which forwards the result to another {@code IoFuture}'s manager.
     *
     * @param <T> the channel type
     * @return the notifier
     */
    @SuppressWarnings({ "unchecked" })
    public static <T> IoFuture.Notifier<T, FutureResult<T>> getManagerNotifier() {
        return MANAGER_NOTIFIER;
    }

    private static final ManagerNotifier MANAGER_NOTIFIER = new ManagerNotifier();

    private static class ManagerNotifier<T extends Channel> extends IoFuture.HandlingNotifier<T, FutureResult<T>> {
        public void handleCancelled(final FutureResult<T> manager) {
            manager.setCancelled();
        }

        public void handleFailed(final IOException exception, final FutureResult<T> manager) {
            manager.setException(exception);
        }

        public void handleDone(final T result, final FutureResult<T> manager) {
            manager.setResult(result);
        }
    }

    /**
     * A channel source which tries to acquire a channel from a delegate channel source the given number of times before
     * giving up.
     *
     * @param delegate the delegate channel source
     * @param maxTries the number of times to retry
     * @param <T> the channel type
     * @return the retrying channel source
     */
    public static <T extends Channel> ChannelSource<T> getRetryingChannelSource(final ChannelSource<T> delegate, final int maxTries) {
        if (maxTries < 1) {
            throw new IllegalArgumentException("maxTries must be at least 1");
        }
        return new RetryingChannelSource<T>(maxTries, delegate);
    }

    private static class RetryingNotifier<T extends Channel> extends IoFuture.HandlingNotifier<T, Result<T>> {

        private volatile int remaining;
        private final int maxTries;
        private final Result<T> result;
        private final ChannelSource<T> delegate;
        private final ChannelListener<? super T> openListener;

        RetryingNotifier(final int maxTries, final Result<T> result, final ChannelSource<T> delegate, final ChannelListener<? super T> openListener) {
            this.maxTries = maxTries;
            this.result = result;
            this.delegate = delegate;
            this.openListener = openListener;
            remaining = maxTries;
        }

        public void handleFailed(final IOException exception, final Result<T> attachment) {
            if (remaining-- == 0) {
                result.setException(new IOException("Failed to create channel after " + maxTries + " tries", exception));
                return;
            }
            tryOne(attachment);
        }

        void tryOne(final Result<T> attachment) {
            final IoFuture<? extends T> ioFuture = delegate.open(openListener);
            ioFuture.addNotifier(this, attachment);
        }
    }

    private static class RetryingChannelSource<T extends Channel> implements ChannelSource<T> {

        private final int maxTries;
        private final ChannelSource<T> delegate;

        RetryingChannelSource(final int maxTries, final ChannelSource<T> delegate) {
            this.maxTries = maxTries;
            this.delegate = delegate;
        }

        public IoFuture<T> open(final ChannelListener<? super T> openListener) {
            final FutureResult<T> result = new FutureResult<T>();
            final IoUtils.RetryingNotifier<T> notifier = new IoUtils.RetryingNotifier<T>(maxTries, result, delegate, openListener);
            notifier.tryOne(result);
            return result.getIoFuture();
        }
    }

    /**
     * A cancellable which closes the given resource on cancel.
     *
     * @param c the resource
     * @return the cancellable
     */
    public static Cancellable closingCancellable(final Closeable c) {
        return new ClosingCancellable(c);
    }

    private static class ClosingCancellable implements Cancellable {

        private final Closeable c;

        ClosingCancellable(final Closeable c) {
            this.c = c;
        }

        public Cancellable cancel() {
            safeClose(c);
            return this;
        }
    }

    /**
     * Get the null cancellable.
     *
     * @return the null cancellable
     */
    public static Cancellable nullCancellable() {
        return NULL_CANCELLABLE;
    }

    private static class ResultNotifier<T> extends IoFuture.HandlingNotifier<T, Result<T>> {

        public void handleCancelled(final Result<T> result) {
            result.setCancelled();
        }

        public void handleFailed(final IOException exception, final Result<T> result) {
            result.setException(exception);
        }

        public void handleDone(final T value, final Result<T> result) {
            result.setResult(value);
        }
    }

    /**
     * Create a channel source from a connector.
     *
     * @param connector the connector to use
     * @param destination the destination to connect to
     * @param <C> the channel type
     * @return the channel source
     */
    public static <C extends Channel> ChannelSource<C> getChannelSource(final Connector<C> connector, final SocketAddress destination) {
        return new ChannelSource<C>() {
            public IoFuture<C> open(final ChannelListener<? super C> openListener) {
                return connector.connectTo(destination, openListener, null);
            }
        };
    }

    /**
     * Create a channel destination from a acceptor.
     *
     * @param acceptor the acceptor to use
     * @param destination the destination to accept on
     * @param <C> the channel type
     * @return the channel destination
     */
    public static <C extends Channel> ChannelDestination<C> getChannelDestination(final Acceptor<C> acceptor, final SocketAddress destination) {
        return new ChannelDestination<C>() {
            public IoFuture<C> accept(final ChannelListener<? super C> openListener, final ChannelListener<? super BoundChannel> bindListener) {
                return acceptor.acceptTo(destination, openListener, bindListener);
            }
        };
    }
}
