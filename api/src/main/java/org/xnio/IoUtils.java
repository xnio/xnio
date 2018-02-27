/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.xnio;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.Selector;
import java.nio.channels.Channel;
import java.nio.channels.WritableByteChannel;
import java.util.Random;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.zip.ZipFile;
import org.xnio.channels.SuspendableReadChannel;

import java.util.logging.Handler;

import static org.xnio._private.Messages.closeMsg;
import static org.xnio._private.Messages.msg;

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
    @SuppressWarnings("rawtypes")
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

    /**
     * Close a resource, logging an error if an error occurs.
     *
     * @param resource the resource to close
     */
    public static void safeClose(final AutoCloseable resource) {
        try {
            if (resource != null) {
                closeMsg.closingResource(resource);
                resource.close();
            }
        } catch (ClosedChannelException ignored) {
        } catch (Throwable t) {
            closeMsg.resourceCloseFailed(t, resource);
        }
    }

    /**
     * Close a resource, logging an error if an error occurs.
     *
     * @param resource the resource to close
     */
    public static void safeClose(final Closeable resource) {
        try {
            if (resource != null) {
                closeMsg.closingResource(resource);
                resource.close();
            }
        } catch (ClosedChannelException ignored) {
            msg.tracef("safeClose, ignoring ClosedChannelException exception");
        } catch (Throwable t) {
            closeMsg.resourceCloseFailed(t, resource);
        }
    }

    /**
     * Close a series of resources, logging errors if they occur.
     *
     * @param resources the resources to close
     */
    public static void safeClose(final Closeable... resources) {
        for (Closeable resource : resources) {
            safeClose(resource);
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
                closeMsg.closingResource(resource);
                resource.close();
            }
        } catch (ClosedChannelException ignored) {
        } catch (Throwable t) {
            closeMsg.resourceCloseFailed(t, resource);
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
                closeMsg.closingResource(resource);
                resource.close();
            }
        } catch (Throwable t) {
            closeMsg.resourceCloseFailed(t, resource);
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
                closeMsg.closingResource(resource);
                resource.close();
            }
        } catch (ClosedChannelException ignored) {
        } catch (Throwable t) {
            closeMsg.resourceCloseFailed(t, resource);
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
                closeMsg.closingResource(resource);
                resource.close();
            }
        } catch (ClosedChannelException ignored) {
        } catch (Throwable t) {
            closeMsg.resourceCloseFailed(t, resource);
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
                closeMsg.closingResource(resource);
                resource.close();
            }
        } catch (Throwable t) {
            closeMsg.resourceCloseFailed(t, resource);
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
                closeMsg.closingResource(resource);
                resource.close();
            }
        } catch (Throwable t) {
            closeMsg.resourceCloseFailed(t, resource);
        }
    }

    /**
     * Close a future resource, logging an error if an error occurs.  Attempts to cancel the operation if it is
     * still in progress.
     *
     * @param futureResource the resource to close
     */
    public static void safeClose(final IoFuture<? extends Closeable> futureResource) {
        if (futureResource != null) {
            futureResource.cancel().addNotifier(closingNotifier(), null);
        }
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

    @SuppressWarnings("rawtypes")
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
                        throw msg.opTimedOut();
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

    /**
     * Safely shutdown reads on the given channel.
     *
     * @param channel the channel
     */
    public static void safeShutdownReads(final SuspendableReadChannel channel) {
        if (channel != null) {
            try {
                channel.shutdownReads();
            } catch (IOException e) {
                closeMsg.resourceReadShutdownFailed(null, null);
            }
        }
    }

    /**
     * Platform-independent channel-to-channel transfer method.  Uses regular {@code read} and {@code write} operations
     * to move bytes from the {@code source} channel to the {@code sink} channel.  After this call, the {@code throughBuffer}
     * should be checked for remaining bytes; if there are any, they should be written to the {@code sink} channel before
     * proceeding.  This method may be used with NIO channels, XNIO channels, or a combination of the two.
     * <p>
     * If either or both of the given channels are blocking channels, then this method may block.
     *
     * @param source the source channel to read bytes from
     * @param count the number of bytes to transfer (must be >= {@code 0L})
     * @param throughBuffer the buffer to transfer through (must not be {@code null})
     * @param sink the sink channel to write bytes to
     * @return the number of bytes actually transferred (possibly 0)
     * @throws IOException if an I/O error occurs during the transfer of bytes
     */
    public static long transfer(final ReadableByteChannel source, final long count, final ByteBuffer throughBuffer, final WritableByteChannel sink) throws IOException {
        long res;
        long total = 0L;
        throughBuffer.limit(0);
        while (total < count) {
            throughBuffer.compact();
            try {
                if (count - total < (long) throughBuffer.remaining()) {
                    throughBuffer.limit((int) (count - total));
                }
                res = source.read(throughBuffer);
                if (res <= 0) {
                    return total == 0L ? res : total;
                }
            } finally {
                throughBuffer.flip();
            }
            res = sink.write(throughBuffer);
            if (res == 0) {
                return total;
            }
            total += res;
        }
        return total;
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
            parent.addNotifier(new Notifier<I, A>() {
                public void notify(final IoFuture<? extends I> future, final A attachment) {
                    notifier.notify(CastingIoFuture.this, attachment);
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

    @SuppressWarnings("rawtypes")
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
    public static <T extends Channel> ChannelSource<T> getRetryingChannelSource(final ChannelSource<T> delegate, final int maxTries) throws IllegalArgumentException {
        if (maxTries < 1) {
            throw msg.minRange("maxTries", 1);
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

        public void handleCancelled(final Result<T> attachment) {
            result.setCancelled();
        }

        public void handleDone(final T data, final Result<T> attachment) {
            result.setResult(data);
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
     * Get a thread-local RNG.  Do not share this instance with other threads.
     *
     * @return the thread-local RNG
     */
    public static Random getThreadLocalRandom() {
        return ThreadLocalRandom.current();
    }
}
