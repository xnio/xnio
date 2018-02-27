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

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static org.xnio._private.Messages.futureMsg;

/**
 * An abstract base class for {@code IoFuture} objects.  Used to easily produce implementations.
 *
 * @param <T> the type of result that this operation produces
 */
public abstract class AbstractIoFuture<T> implements IoFuture<T> {

    @SuppressWarnings("unchecked")
    private final AtomicReference<State<T>> stateRef = new AtomicReference<>((State<T>) ST_INITIAL);

    private static final State<?> ST_INITIAL = new InitialState<>();
    private static final State<?> ST_CANCELLED = new CancelledState<>();

    static abstract class State<T> {
        abstract Status getStatus();

        abstract void notifyDone(AbstractIoFuture<T> future, T result);

        abstract void notifyFailed(AbstractIoFuture<T> future, IOException exception);

        abstract void notifyCancelled(AbstractIoFuture<T> future);

        abstract void cancel();

        abstract boolean cancelRequested();

        State<T> withWaiter(final Thread thread) {
            return new WaiterState<T>(this, thread);
        }

        <A> State<T> withNotifier(final Executor executor, final AbstractIoFuture<T> future, final Notifier<? super T, A> notifier, final A attachment) {
            return new NotifierState<T, A>(this, notifier, attachment);
        }

        State<T> withCancelHandler(final Cancellable cancellable) {
            return new CancellableState<T>(this, cancellable);
        }

        T getResult() {
            throw new IllegalStateException();
        }

        IOException getException() {
            throw new IllegalStateException();
        }
    }

    static final class InitialState<T> extends State<T> {

        Status getStatus() {
            return Status.WAITING;
        }

        void notifyDone(final AbstractIoFuture<T> future, final T result) {
        }

        void notifyFailed(final AbstractIoFuture<T> future, final IOException exception) {
        }

        void notifyCancelled(final AbstractIoFuture<T> future) {
        }

        void cancel() {
        }

        boolean cancelRequested() {
            return false;
        }
    }

    static final class CompleteState<T> extends State<T> {
        private final T result;

        CompleteState(final T result) {
            this.result = result;
        }

        Status getStatus() {
            return Status.DONE;
        }

        void notifyDone(final AbstractIoFuture<T> future, final T result) {
        }

        void notifyFailed(final AbstractIoFuture<T> future, final IOException exception) {
        }

        void notifyCancelled(final AbstractIoFuture<T> future) {
        }

        void cancel() {
        }

        State<T> withCancelHandler(final Cancellable cancellable) {
            return this;
        }

        State<T> withWaiter(final Thread thread) {
            return this;
        }

        <A> State<T> withNotifier(final Executor executor, final AbstractIoFuture<T> future, final Notifier<? super T, A> notifier, final A attachment) {
            future.runNotifier(new NotifierRunnable<T, A>(notifier, future, attachment));
            return this;
        }

        T getResult() {
            return result;
        }

        boolean cancelRequested() {
            return false;
        }
    }

    static final class FailedState<T> extends State<T> {
        private final IOException exception;

        FailedState(final IOException exception) {
            this.exception = exception;
        }

        Status getStatus() {
            return Status.FAILED;
        }

        void notifyDone(final AbstractIoFuture<T> future, final T result) {
        }

        void notifyFailed(final AbstractIoFuture<T> future, final IOException exception) {
        }

        void notifyCancelled(final AbstractIoFuture<T> future) {
        }

        void cancel() {
        }

        State<T> withCancelHandler(final Cancellable cancellable) {
            return this;
        }

        State<T> withWaiter(final Thread thread) {
            return this;
        }

        <A> State<T> withNotifier(final Executor executor, final AbstractIoFuture<T> future, final Notifier<? super T, A> notifier, final A attachment) {
            future.runNotifier(new NotifierRunnable<T, A>(notifier, future, attachment));
            return this;
        }

        IOException getException() {
            return exception;
        }

        boolean cancelRequested() {
            return false;
        }
    }

    static final class CancelledState<T> extends State<T> {

        CancelledState() {
        }

        Status getStatus() {
            return Status.CANCELLED;
        }

        void notifyDone(final AbstractIoFuture<T> future, final T result) {
        }

        void notifyFailed(final AbstractIoFuture<T> future, final IOException exception) {
        }

        void notifyCancelled(final AbstractIoFuture<T> future) {
        }

        void cancel() {
        }

        State<T> withCancelHandler(final Cancellable cancellable) {
            try {
                cancellable.cancel();
            } catch (Throwable ignored) {}
            return this;
        }

        <A> State<T> withNotifier(final Executor executor, final AbstractIoFuture<T> future, final Notifier<? super T, A> notifier, final A attachment) {
            future.runNotifier(new NotifierRunnable<T, A>(notifier, future, attachment));
            return this;
        }

        State<T> withWaiter(final Thread thread) {
            return this;
        }

        boolean cancelRequested() {
            return true;
        }
    }

    static final class NotifierState<T, A> extends State<T> {
        final State<T> next;
        final Notifier<? super T, A> notifier;
        final A attachment;

        NotifierState(final State<T> next, final Notifier<? super T, A> notifier, final A attachment) {
            this.next = next;
            this.notifier = notifier;
            this.attachment = attachment;
        }

        Status getStatus() {
            return Status.WAITING;
        }

        void notifyDone(final AbstractIoFuture<T> future, final T result) {
            doNotify(future);
            next.notifyDone(future, result);
        }

        void notifyFailed(final AbstractIoFuture<T> future, final IOException exception) {
            doNotify(future);
            next.notifyFailed(future, exception);
        }

        void notifyCancelled(final AbstractIoFuture<T> future) {
            doNotify(future);
            next.notifyCancelled(future);
        }

        void cancel() {
            next.cancel();
        }

        private void doNotify(final AbstractIoFuture<T> future) {
            future.runNotifier(new NotifierRunnable<T, A>(notifier, future, attachment));
        }

        boolean cancelRequested() {
            return next.cancelRequested();
        }
    }

    static final class WaiterState<T> extends State<T> {
        final State<T> next;
        final Thread waiter;

        WaiterState(final State<T> next, final Thread waiter) {
            this.next = next;
            this.waiter = waiter;
        }

        Status getStatus() {
            return Status.WAITING;
        }

        void notifyDone(final AbstractIoFuture<T> future, final T result) {
            LockSupport.unpark(waiter);
            next.notifyDone(future, result);
        }

        void notifyFailed(final AbstractIoFuture<T> future, final IOException exception) {
            LockSupport.unpark(waiter);
            next.notifyFailed(future, exception);
        }

        void notifyCancelled(final AbstractIoFuture<T> future) {
            LockSupport.unpark(waiter);
            next.notifyCancelled(future);
        }

        void cancel() {
            next.cancel();
        }

        boolean cancelRequested() {
            return next.cancelRequested();
        }
    }

    static final class CancellableState<T> extends State<T> {
        final State<T> next;
        final Cancellable cancellable;

        CancellableState(final State<T> next, final Cancellable cancellable) {
            this.next = next;
            this.cancellable = cancellable;
        }

        Status getStatus() {
            return Status.WAITING;
        }

        void notifyDone(final AbstractIoFuture<T> future, final T result) {
            next.notifyDone(future, result);
        }

        void notifyFailed(final AbstractIoFuture<T> future, final IOException exception) {
            next.notifyFailed(future, exception);
        }

        void notifyCancelled(final AbstractIoFuture<T> future) {
            next.notifyCancelled(future);
        }

        void cancel() {
            try {
                cancellable.cancel();
            } catch (Throwable ignored) {}
            next.cancel();
        }

        boolean cancelRequested() {
            return next.cancelRequested();
        }
    }

    static final class CancelRequestedState<T> extends State<T> {
        final State<T> next;

        CancelRequestedState(final State<T> next) {
            this.next = next;
        }

        Status getStatus() {
            return Status.WAITING;
        }

        void notifyDone(final AbstractIoFuture<T> future, final T result) {
            next.notifyDone(future, result);
        }

        void notifyFailed(final AbstractIoFuture<T> future, final IOException exception) {
            next.notifyFailed(future, exception);
        }

        void notifyCancelled(final AbstractIoFuture<T> future) {
            next.notifyCancelled(future);
        }

        void cancel() {
            // terminate
        }

        boolean cancelRequested() {
            return true;
        }
    }

    /**
     * Construct a new instance.
     */
    protected AbstractIoFuture() {
    }

    /**
     * {@inheritDoc}
     */
    public Status getStatus() {
        return getState().getStatus();
    }

    private State<T> getState() {
        return stateRef.get();
    }

    private boolean compareAndSetState(State<T> expect, State<T> update) {
        return stateRef.compareAndSet(expect, update);
    }

    /**
     * {@inheritDoc}
     */
    public Status await() {
        final Thread thread = Thread.currentThread();
        State<T> state;
        for (;;) {
            state = getState();
            if (state.getStatus() != Status.WAITING) {
                return state.getStatus();
            }
            Xnio.checkBlockingAllowed();
            State<T> withWaiter = state.withWaiter(thread);
            if (compareAndSetState(state, withWaiter)) {
                boolean intr = Thread.interrupted();
                try {
                    do {
                        LockSupport.park(this);
                        if (Thread.interrupted()) intr = true;
                        state = getState();
                    } while (state.getStatus() == Status.WAITING);
                    return state.getStatus();
                } finally {
                    if (intr) thread.interrupt();
                }
            }
            // retry
        }
    }

    /**
     * {@inheritDoc}
     */
    public Status await(long time, final TimeUnit timeUnit) {
        if (time < 0L) {
            time = 0L;
        }
        long duration = timeUnit.toNanos(time);
        long now = System.nanoTime();
        long tick;
        final Thread thread = Thread.currentThread();
        State<T> state;
        for (;;) {
            state = getState();
            if (state.getStatus() != Status.WAITING || duration == 0L) {
                return state.getStatus();
            }
            Xnio.checkBlockingAllowed();
            State<T> withWaiter = state.withWaiter(thread);
            if (compareAndSetState(state, withWaiter)) {
                boolean intr = Thread.interrupted();
                try {
                    do {
                        LockSupport.parkNanos(this, duration);
                        if (Thread.interrupted()) intr = true;
                        state = getState();
                        duration -= (tick = System.nanoTime()) - now;
                        now = tick;
                    } while (state.getStatus() == Status.WAITING && duration > 0L);
                    return state.getStatus();
                } finally {
                    if (intr) thread.interrupt();
                }
            }
            // retry
        }
    }

    /**
     * {@inheritDoc}
     */
    public Status awaitInterruptibly() throws InterruptedException {
        final Thread thread = Thread.currentThread();
        State<T> state;
        for (;;) {
            state = getState();
            if (state.getStatus() != Status.WAITING) {
                return state.getStatus();
            }
            Xnio.checkBlockingAllowed();
            if (Thread.interrupted()) throw new InterruptedException();
            State<T> withWaiter = state.withWaiter(thread);
            if (compareAndSetState(state, withWaiter)) {
                do {
                    LockSupport.park(this);
                    if (Thread.interrupted()) throw new InterruptedException();
                    state = getState();
                } while (state.getStatus() == Status.WAITING);
                return state.getStatus();
            }
            // retry
        }
    }

    /**
     * {@inheritDoc}
     */
    public Status awaitInterruptibly(long time, final TimeUnit timeUnit) throws InterruptedException {
        if (time < 0L) {
            time = 0L;
        }
        long duration = timeUnit.toNanos(time);
        long now = System.nanoTime();
        long tick;
        final Thread thread = Thread.currentThread();
        State<T> state;
        for (;;) {
            state = getState();
            if (state.getStatus() != Status.WAITING || duration == 0L) {
                return state.getStatus();
            }
            Xnio.checkBlockingAllowed();
            if (Thread.interrupted()) throw new InterruptedException();
            State<T> withWaiter = state.withWaiter(thread);
            if (compareAndSetState(state, withWaiter)) {
                do {
                    LockSupport.parkNanos(this, duration);
                    if (Thread.interrupted()) throw new InterruptedException();
                    state = getState();
                    duration -= (tick = System.nanoTime()) - now;
                    now = tick;
                } while (state.getStatus() == Status.WAITING && duration > 0L);
                return state.getStatus();
            }
            // retry
        }
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings({"unchecked"})
    public T get() throws IOException, CancellationException {
        switch (await()) {
            case DONE: return getState().getResult();
            case FAILED: throw getState().getException();
            case CANCELLED: throw futureMsg.opCancelled();
            default: throw new IllegalStateException();
        }
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings({"unchecked"})
    public T getInterruptibly() throws IOException, InterruptedException, CancellationException {
        switch (awaitInterruptibly()) {
            case DONE: return getState().getResult();
            case FAILED: throw getState().getException();
            case CANCELLED: throw futureMsg.opCancelled();
            default: throw new IllegalStateException();
        }
    }

    /**
     * {@inheritDoc}
     */
    public IOException getException() throws IllegalStateException {
        return getState().getException();
    }

    /**
     * {@inheritDoc}
     */
    public <A> IoFuture<T> addNotifier(final Notifier<? super T, A> notifier, final A attachment) {
        State<T> oldState, newState;
        do {
            oldState = getState();
            newState = oldState.withNotifier(getNotifierExecutor(), this, notifier, attachment);
        } while (! compareAndSetState(oldState, newState));
        return this;
    }

    /**
     * Set the exception for this operation.  Any threads blocking on this instance will be unblocked.
     *
     * @param exception the exception to set
     * @return {@code false} if the operation was already completed, {@code true} otherwise
     */
    protected boolean setException(IOException exception) {
        State<T> oldState;
        oldState = getState();
        if (oldState.getStatus() != Status.WAITING) {
            return false;
        } else {
            State<T> newState = new FailedState<T>(exception);
            while (! compareAndSetState(oldState, newState)) {
                oldState = getState();
                if (oldState.getStatus() != Status.WAITING) {
                    return false;
                }
            }
        }
        oldState.notifyFailed(this, exception);
        return true;
    }

    /**
     * Set the result for this operation.  Any threads blocking on this instance will be unblocked.
     *
     * @param result the result to set
     * @return {@code false} if the operation was already completed, {@code true} otherwise
     */
    protected boolean setResult(T result) {
        State<T> oldState;
        oldState = getState();
        if (oldState.getStatus() != Status.WAITING) {
            return false;
        } else {
            State<T> newState = new CompleteState<>(result);
            while (! compareAndSetState(oldState, newState)) {
                oldState = getState();
                if (oldState.getStatus() != Status.WAITING) {
                    return false;
                }
            }
        }
        oldState.notifyDone(this, result);
        return true;
    }

    /**
     * Acknowledge the cancellation of this operation.
     *
     * @return {@code false} if the operation was already completed, {@code true} otherwise
     */
    protected boolean setCancelled() {
        State<T> oldState;
        oldState = getState();
        if (oldState.getStatus() != Status.WAITING) {
            return false;
        } else {
            @SuppressWarnings("unchecked")
            State<T> newState = (State<T>) ST_CANCELLED;
            while (! compareAndSetState(oldState, newState)) {
                oldState = getState();
                if (oldState.getStatus() != Status.WAITING) {
                    return false;
                }
            }
        }
        oldState.notifyCancelled(this);
        return true;
    }

    /**
     * Cancel an operation.  The actual cancel may be synchronous or asynchronous.  Implementers will use this method
     * to initiate the cancel; use the {@link #setCancelled()} method to indicate that the cancel was successful.  The
     * default implementation calls any registered cancel handlers.
     *
     * @return this {@code IoFuture} instance
     */
    public IoFuture<T> cancel() {
        State<T> state;
        do {
            state = getState();
            if (state.getStatus() != Status.WAITING || state.cancelRequested()) return this;
        } while (! compareAndSetState(state, new CancelRequestedState<T>(state)));
        state.cancel();
        return this;
    }

    /**
     * Add a cancellation handler.  The argument will be cancelled whenever this {@code IoFuture} is cancelled.  If
     * the {@code IoFuture} is already cancelled when this method is called, the handler will be called directly.
     *
     * @param cancellable the cancel handler
     */
    protected void addCancelHandler(final Cancellable cancellable) {
        State<T> oldState, newState;
        do {
            oldState = getState();
            if (oldState.getStatus() != Status.WAITING || oldState.cancelRequested()) {
                try {
                    cancellable.cancel();
                } catch (Throwable ignored) {
                }
                return;
            }
            newState = oldState.withCancelHandler(cancellable);
            if (oldState == newState) return;
        } while (! compareAndSetState(oldState, newState));
    }

    /**
     * Run a notifier.  Implementors will run the notifier, preferably in another thread.  The default implementation
     * runs the notifier using the {@code Executor} retrieved via {@link #getNotifierExecutor()}.
     *
     * @param runnable the runnable task
     */
    protected void runNotifier(final Runnable runnable) {
        getNotifierExecutor().execute(runnable);
    }

    /**
     * Get the executor used to run asynchronous notifiers.  By default, this implementation simply returns the direct
     * executor.
     *
     * @return the executor to use
     */
    protected Executor getNotifierExecutor() {
        return IoUtils.directExecutor();
    }

    static class NotifierRunnable<T, A> implements Runnable {

        private final Notifier<? super T, A> notifier;
        private final IoFuture<T> future;
        private final A attachment;

        NotifierRunnable(final Notifier<? super T, A> notifier, final IoFuture<T> future, final A attachment) {
            this.notifier = notifier;
            this.future = future;
            this.attachment = attachment;
        }

        public void run() {
            try {
                notifier.notify(future, attachment);
            } catch (Throwable t) {
                futureMsg.notifierFailed(t, notifier);
            }
        }
    }
}
