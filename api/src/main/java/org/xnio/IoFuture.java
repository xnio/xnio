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

import java.util.EventListener;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CancellationException;
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
    interface Notifier<T, A> extends EventListener {
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
     * boilerplate for standard {@link org.xnio.IoFuture.Notifier} implementations.
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
         * @param data the result
         * @param attachment the attachment
         */
        public void handleDone(final T data, final A attachment) {
        }
    }
}
