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
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;

import static org.xnio.Messages.futureMsg;

/**
 * An abstract base class for {@code IoFuture} objects.  Used to easily produce implementations.
 *
 * @param <T> the type of result that this operation produces
 */
public abstract class AbstractIoFuture<T> implements IoFuture<T> {
    private final Object lock = new Object();
    private Status status = Status.WAITING;
    private Object result;
    private List<Runnable> notifierList;
    private List<Cancellable> cancellables;

    private static final List<Cancellable> CANCEL_REQUESTED = Collections.emptyList();

    /**
     * Construct a new instance.
     */
    protected AbstractIoFuture() {
    }

    /**
     * {@inheritDoc}
     */
    public Status getStatus() {
        synchronized (lock) {
            return status;
        }
    }

    /**
     * {@inheritDoc}
     */
    public Status await() {
        synchronized (lock) {
            boolean intr = Thread.interrupted();
            try {
                if (status == Status.WAITING) {
                    Xnio.checkBlockingAllowed();
                    do {
                        try {
                            lock.wait();
                        } catch (InterruptedException e) {
                            intr = true;
                        }
                    } while (status == Status.WAITING);
                }
            } finally {
                if (intr) {
                    Thread.currentThread().interrupt();
                }
            }
            return status;
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
        long waitTime;
        Status status;
        synchronized (lock) {
            boolean intr = Thread.interrupted();
            try {
                if ((status = this.status) == Status.WAITING && (waitTime = duration / 1000000L) > 0L) {
                    Xnio.checkBlockingAllowed();
                    do {
                        try {
                            lock.wait(waitTime);
                        } catch (InterruptedException e) {
                            intr = true;
                        } finally {
                            // decrease duration by the elapsed time
                            duration += now - (now = System.nanoTime());
                        }
                    } while ((status = this.status) == Status.WAITING && (waitTime = duration / 1000000L) > 0L);
                }
            } finally {
                if (intr) {
                    Thread.currentThread().interrupt();
                }
            }
            return status;
        }
    }

    /**
     * {@inheritDoc}
     */
    public Status awaitInterruptibly() throws InterruptedException {
        synchronized (lock) {
            if (status == Status.WAITING) {
                Xnio.checkBlockingAllowed();
                do {
                    lock.wait();
                } while (status == Status.WAITING);
            }
            return status;
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
        long waitTime;
        Status status;
        synchronized (lock) {
            if ((status = this.status) == Status.WAITING && (waitTime = duration / 1000000L) > 0L) {
                Xnio.checkBlockingAllowed();
                do {
                    lock.wait(waitTime);
                    // decrease duration by the elapsed time
                    duration += now - (now = System.nanoTime());
                } while ((status = this.status) == Status.WAITING && (waitTime = duration / 1000000L) > 0L);
            }
            return status;
        }
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings({"unchecked"})
    public T get() throws IOException, CancellationException {
        synchronized (lock) {
            switch (await()) {
                case DONE: return (T) result;
                case FAILED: throw (IOException) result;
                case CANCELLED: throw futureMsg.opCancelled();
                default: throw new IllegalStateException();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings({"unchecked"})
    public T getInterruptibly() throws IOException, InterruptedException, CancellationException {
        synchronized (lock) {
            switch (awaitInterruptibly()) {
                case DONE: return (T) result;
                case FAILED: throw (IOException) result;
                case CANCELLED: throw futureMsg.opCancelled();
                default: throw new IllegalStateException();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public IOException getException() throws IllegalStateException {
        synchronized (lock) {
            if (status == Status.FAILED) {
                return (IOException) result;
            } else {
                throw new IllegalStateException();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public <A> IoFuture<T> addNotifier(final Notifier<? super T, A> notifier, final A attachment) {
        final Runnable runnable = new Runnable() {
            public void run() {
                try {
                    notifier.notify(AbstractIoFuture.this, attachment);
                } catch (Throwable t) {
                    futureMsg.notifierFailed(t, notifier);
                }
            }
        };
        synchronized (lock) {
            if (status == Status.WAITING) {
                if (notifierList == null) {
                    notifierList = new ArrayList<Runnable>();
                }
                notifierList.add(runnable);
                return this;
            }
        }
        runNotifier(runnable);
        return this;
    }

    private void runAllNotifiers() {
        if (notifierList != null) {
            for (final Runnable runnable : notifierList) {
                runNotifier(runnable);
            }
            notifierList = null;
        }
    }

    /**
     * Set the exception for this operation.  Any threads blocking on this instance will be unblocked.
     *
     * @param exception the exception to set
     * @return {@code false} if the operation was already completed, {@code true} otherwise
     */
    protected boolean setException(IOException exception) {
        synchronized (lock) {
            if (status == Status.WAITING) {
                status = Status.FAILED;
                result = exception;
                cancellables = null;
                runAllNotifiers();
                lock.notifyAll();
                return true;
            } else {
                return false;
            }
        }
    }

    /**
     * Set the result for this operation.  Any threads blocking on this instance will be unblocked.
     *
     * @param result the result to set
     * @return {@code false} if the operation was already completed, {@code true} otherwise
     */
    protected boolean setResult(T result) {
        synchronized (lock) {
            if (status == Status.WAITING) {
                status = Status.DONE;
                this.result = result;
                cancellables = null;
                runAllNotifiers();
                lock.notifyAll();
                return true;
            } else {
                return false;
            }
        }
    }

    /**
     * Acknowledge the cancellation of this operation.
     *
     * @return {@code false} if the operation was already completed, {@code true} otherwise
     */
    protected boolean setCancelled() {
        synchronized (lock) {
            if (status == Status.WAITING) {
                status = Status.CANCELLED;
                cancellables = null;
                runAllNotifiers();
                lock.notifyAll();
                return true;
            } else {
                return false;
            }
        }
    }

    /**
     * Cancel an operation.  The actual cancel may be synchronous or asynchronous.  Implementers will use this method
     * to initiate the cancel; use the {@link #setCancelled()} method to indicate that the cancel was successful.  The
     * default implementation calls any registered cancel handlers.
     *
     * @return this {@code IoFuture} instance
     */
    public IoFuture<T> cancel() {
        final List<Cancellable> cancellables;
        synchronized (lock) {
            cancellables = this.cancellables;
            if (cancellables == null || cancellables == CANCEL_REQUESTED) {
                return this;
            }
            this.cancellables = CANCEL_REQUESTED;
        }
        for (Cancellable cancellable : cancellables) {
            cancellable.cancel();
        }
        return this;
    }

    /**
     * Add a cancellation handler.  The argument will be cancelled whenever this {@code IoFuture} is cancelled.  If
     * the {@code IoFuture} is already cancelled when this method is called, the handler will be called directly.
     *
     * @param cancellable the cancel handler
     */
    protected void addCancelHandler(final Cancellable cancellable) {
        synchronized (lock) {
            switch (status) {
                case CANCELLED:
                    break;
                case WAITING:
                    final List<Cancellable> cancellables = this.cancellables;
                    if (cancellables == CANCEL_REQUESTED) {
                        break;
                    } else {
                        ((cancellables == null) ? (this.cancellables = new ArrayList<Cancellable>()) : cancellables).add(cancellable);
                    }
                default:
                    return;
            }
        }
        cancellable.cancel();
    }

    /**
     * Run a notifier.  Implementors will run the notifier, preferably in another thread.  The default implementation
     * runs the notifier using the {@code Executor} retrieved via {@link #getNotifierExecutor()}.
     *
     * @param runnable
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
}
