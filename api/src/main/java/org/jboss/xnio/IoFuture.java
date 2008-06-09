package org.jboss.xnio;

import java.util.concurrent.TimeUnit;
import java.io.IOException;

/**
 * The future result of an asynchronous request.  Use instances of this interface to retrieve the final status of
 * an asynchronous operation.
 *
 * @param <T> the type of result that this operation produces
 */
public interface IoFuture<T> {
    /**
     * The current status of an asynchronous operation.
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
        /**
         * The request timed out before the operation was complete; otherwise equivalent to status {@link #WAITING}.
         */
        TIMED_OUT,
    }

    /**
     * Cancel an operation.  The actual cancel may be synchronous or asynchronous.
     *
     * @return this {@code IoFuture} instance
     */
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
     * or the given time elapses.  If the time elapses before the operation is complete, {@link Status#TIMED_OUT} is
     * returned.
     *
     * @param timeUnit the time unit
     * @param time the amount of time to wait
     * @return the new status, or {@link Status#TIMED_OUT} if the timeout expired
     */
    Status await(TimeUnit timeUnit, long time);

    /**
     * Wait for the operation to complete.  This method will block until the status changes from {@link Status#WAITING},
     * or the current thread is interrupted.
     *
     * @return the new status
     * @throws InterruptedException if the operation is interrupted
     */
    Status awaitInterruptably() throws InterruptedException;

    /**
     * Wait for the operation to complete, with a timeout.  This method will block until the status changes from {@link Status#WAITING},
     * the given time elapses, or the current thread is interrupted.  If the time elapses before the operation is complete, {@link Status#TIMED_OUT} is
     * returned.
     *
     * @param timeUnit the time unit
     * @param time the amount of time to wait
     * @return the new status, or {@link Status#TIMED_OUT} if the timeout expired
     * @throws InterruptedException if the operation is interrupted
     */
    Status awaitInterruptably(TimeUnit timeUnit, long time) throws InterruptedException;

    /**
     * Get the result of the operation.  If the operation is not complete, blocks until the operation completes.  If
     * the operation fails, or has already failed at the time this method is called, the failure reason is thrown.
     *
     * @return the result of the operation
     * @throws IOException if the operation failed
     */
    T get() throws IOException;

    /**
     * Get the result of the operation.  If the operation is not complete, blocks until the operation completes.  If
     * the operation fails, or has already failed at the time this method is called, the failure reason is thrown.  If
     * the current thread is interrupted while waiting, an exception is thrown.
     *
     * @return the result of the operation
     * @throws IOException if the operation failed
     * @throws InterruptedException if the operation is interrupted
     */
    T getInterruptably() throws IOException, InterruptedException;

    /**
     * Get the failure reason.
     *
     * @return the failure reason
     * @throws IllegalStateException if the operation did not fail
     */
    IOException getException() throws IllegalStateException;

    /**
     * Add a notifier to be called when this operation is complete.  If the operation is already complete, the notifier
     * is called immediately, possibly in the caller's thread.
     *
     * @param notifier the notifier to be called
     */
    void addNotifier(Notifier<T> notifier);

    /**
     * A notifier that handles changes in the status of an {@code IoFuture}.
     */
    interface Notifier<T> {
        /**
         * Receive notification of the completion of an outstanding operation.
         *
         * @param ioFuture the future corresponding to this operation
         */
        void notify(IoFuture<T> ioFuture);
    }
}
