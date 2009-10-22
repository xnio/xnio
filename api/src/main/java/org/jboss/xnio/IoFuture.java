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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.io.IOException;

/**
 * The future result of an asynchronous request.  Use instances of this interface to retrieve the final status of
 * an asynchronous operation.
 * <p/>
 * It is recommended, due to the vagaries of the way generics work, that when you use {@code IoFuture} instances, you
 * use a wildcard to express the return type.  This enables you to take advantage of covariance to retrofit
 * more specific types later on without breaking anything.
 * <p/>
 * For example, if you have a method which returns a future {@code InputStream}, you might be tempted to declare it like
 * this:
 * <tt><pre>
 * IoFuture&lt;InputStream&gt; getFutureInputStream();
 * </pre></tt>
 * Now if you later decide that what you really need is a {@code DataInputStream} (which extends {@code InputStream}),
 * you're in trouble because you have written {@code IoFuture&lt;InputStream&gt;} everywhere, which cannot be assigned to or from
 * an {@code IoFuture&lt;DataInputStream&gt;}.
 * <p/>
 * On the other hand, if you declare it like this:
 * <tt><pre>
 * IoFuture&lt;? extends InputStream&gt; getFutureInputStream();
 * </pre></tt>
 * Now you can change it at any time to {@code IoFuture&lt;? extends DataInputStream&gt;} without breaking the contract, since
 * it will be assignable to variables of type {@code IoFuture&lt;? extends InputStream&gt;}.
 *
 * @param <T> the type of result that this operation produces
 *
 * @apiviz.landmark
 */
public interface IoFuture<T> extends Cancellable {
    /**
     * The current status of an asynchronous operation.
     *
     * @apiviz.exclude
     */
    enum Status {
        /**
         * The operation is still in progress.
         */
        WAITING,
        /**
         * The operation has completed successfully.
         */
        DONE,
        /**
         * The operation was cancelled.
         */
        CANCELLED,
        /**
         * The operation did not succeed.
         */
        FAILED,
    }

    /** {@inheritDoc} */
    IoFuture<T> cancel();

    /**
     * Get the current status.
     *
     * @return the current status
     */
    Status getStatus();

    /**
     * Wait for the operation to complete.  This method will block until the status changes from {@link Status#WAITING}.
     *
     * @return the new status
     */
    Status await();

    /**
     * Wait for the operation to complete, with a timeout.  This method will block until the status changes from {@link Status#WAITING},
     * or the given time elapses.  If the time elapses before the operation is complete, {@link Status#WAITING} is
     * returned.
     *
     * @param time the amount of time to wait
     * @param timeUnit the time unit
     * @return the new status, or {@link Status#WAITING} if the timeout expired
     */
    Status await(long time, TimeUnit timeUnit);

    /**
     * Wait for the operation to complete.  This method will block until the status changes from {@link Status#WAITING},
     * or the current thread is interrupted.
     *
     * @return the new status
     * @throws InterruptedException if the operation is interrupted
     */
    Status awaitInterruptibly() throws InterruptedException;

    /**
     * Wait for the operation to complete, with a timeout.  This method will block until the status changes from {@link Status#WAITING},
     * the given time elapses, or the current thread is interrupted.  If the time elapses before the operation is complete, {@link Status#WAITING} is
     * returned.
     *
     * @param time the amount of time to wait
     * @param timeUnit the time unit
     * @return the new status, or {@link Status#WAITING} if the timeout expired
     * @throws InterruptedException if the operation is interrupted
     */
    Status awaitInterruptibly(long time, TimeUnit timeUnit) throws InterruptedException;

    /**
     * Get the result of the operation.  If the operation is not complete, blocks until the operation completes.  If
     * the operation fails, or has already failed at the time this method is called, the failure reason is thrown.
     *
     * @return the result of the operation
     * @throws IOException if the operation failed
     * @throws CancellationException if the operation was cancelled
     */
    T get() throws IOException, CancellationException;

    /**
     * Get the result of the operation.  If the operation is not complete, blocks until the operation completes.  If
     * the operation fails, or has already failed at the time this method is called, the failure reason is thrown.  If
     * the current thread is interrupted while waiting, an exception is thrown.
     *
     * @return the result of the operation
     * @throws IOException if the operation failed
     * @throws InterruptedException if the operation is interrupted
     * @throws CancellationException if the operation was cancelled
     */
    T getInterruptibly() throws IOException, InterruptedException, CancellationException;

    /**
     * Get the failure reason.
     *
     * @return the failure reason
     * @throws IllegalStateException if the operation did not fail
     */
    IOException getException() throws IllegalStateException;

    /**
     * Add a notifier to be called when this operation is complete.  If the operation is already complete, the notifier
     * is called immediately, possibly in the caller's thread.  The given attachment is provided to the notifier.
     *
     * @param notifier the notifier to be called
     * @param attachment the attachment to pass in to the notifier
     * @param <A> the attachment type
     * @return this instance
     */
    <A> IoFuture<T> addNotifier(Notifier<? super T, A> notifier, A attachment);

    /**
     * A notifier that handles changes in the status of an {@code IoFuture}.
     *
     * @param <T> the type of result that the associated future operation produces
     * @param <A> the attachment type
     * @apiviz.exclude
     */
    interface Notifier<T, A> {
        /**
         * Receive notification of the completion of an outstanding operation.
         *
         * @param ioFuture the future corresponding to this operation
         * @param attachment the attachment
         */
        void notify(IoFuture<? extends T> ioFuture, final A attachment);
    }

    /**
     * A base notifier class that calls the designated handler method on notification.  Use this class to reduce
     * boilerplate for standard {@link org.jboss.xnio.IoFuture.Notifier} implementations.
     *
     * @param <T> the type of result that the associated future operation produces
     * @param <A> the attachment type
     *
     * @since 1.1
     *
     * @apiviz.exclude
     */
    abstract class HandlingNotifier<T, A> implements Notifier<T, A> {
        /**
         * {@inheritDoc}
         */
        public void notify(final IoFuture<? extends T> future, A attachment) {
            switch (future.getStatus()) {
                case CANCELLED:
                    handleCancelled(attachment);
                    break;
                case DONE:
                    try {
                        handleDone(future.get(), attachment);
                    } catch (IOException e) {
                        // not possible
                        throw new IllegalStateException();
                    }
                    break;
                case FAILED:
                    handleFailed(future.getException(), attachment);
                    break;
                default:
                    // not possible
                    throw new IllegalStateException();
            }
        }

        /**
         * Handle cancellation.
         *
         * @param attachment the attachment
         */
        public void handleCancelled(final A attachment) {
        }

        /**
         * Handle failure.
         *
         * @param exception the failure reason
         * @param attachment the attachment
         */
        public void handleFailed(final IOException exception, final A attachment) {
        }

        /**
         * Handle completion.
         *
         * @param result the result
         * @param attachment the attachment
         */
        public void handleDone(final T result, final A attachment) {
        }
    }

    /**
     * A simple interface to a managed {@link IoFuture}.  Provides methods to set the result of a future operation.
     *
     * @param <T> the {@code IoFuture} result type
     */
    class Manager<T> {
        private final Executor executor;
        private final AbstractIoFuture<T> ioFuture;

        /**
         * Construct a new instance.
         *
         * @param executor the executor to use to execute handler notifiers.
         */
        public Manager(final Executor executor) {
            this.executor = executor;
            ioFuture = new AbstractIoFuture<T>() {
                protected Executor getNotifierExecutor() {
                    return executor;
                }
            };
        }

        /**
         * Construct a new instance.  The direct executor will be used to execute handler notifiers.
         */
        public Manager() {
            this(IoUtils.directExecutor());
        }

        /**
         * Get the {@code IoFuture} for this manager.
         *
         * @return the {@code IoFuture}
         */
        public IoFuture<T> getIoFuture() {
            return ioFuture;
        }

        /**
         * Add a cancellation handler.  The argument will be cancelled whenever this instance's {@code cancel()} method
         * is invoked.  The argument may be cancelled more than once, in the event that this {@code IoFuture} instance
         * is cancelled more than once; the handler should be prepared to handle this situation.
         *
         * @param cancellable the cancel handler
         */
        public void addCancelHandler(final Cancellable cancellable) {
            ioFuture.addCancelHandler(cancellable);
        }

        /**
         * Set the result for this operation.  Any threads blocking on this instance will be unblocked.
         *
         * @param result the result to set
         * @return {@code false} if the operation was already completed, {@code true} otherwise
         */
        public boolean setResult(final T result) {
            return ioFuture.setResult(result);
        }

        /**
         * Set the exception for this operation.  Any threads blocking on this instance will be unblocked.
         *
         * @param exception the exception to set
         * @return {@code false} if the operation was already completed, {@code true} otherwise
         */
        public boolean setException(final IOException exception) {
            return ioFuture.setException(exception);
        }

        /**
         * Acknowledge the cancellation of this operation.
         *
         * @return {@code false} if the operation was already completed, {@code true} otherwise
         */
        public boolean finishCancel() {
            return ioFuture.finishCancel();
        }

        /**
         * Run a notifier.  Implementors will run the notifier, preferably in another thread.  The default implementation
         * runs the notifier using the {@code Executor} retrieved via {@link #getNotifierExecutor()}.
         *
         * @param runnable
         */
        public void runNotifier(final Runnable runnable) {
            ioFuture.runNotifier(runnable);
        }

        /**
         * Get the executor used to run asynchronous notifiers.
         *
         * @return the executor to use
         */
        public Executor getNotifierExecutor() {
            return executor;
        }
    }
}
