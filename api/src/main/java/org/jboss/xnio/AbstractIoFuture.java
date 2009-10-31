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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import org.jboss.xnio.log.Logger;

/**
 * An abstract base class for {@code IoFuture} objects.  Used to easily produce implementations.
 *
 * @param <T> the type of result that this operation produces
 */
public abstract class AbstractIoFuture<T> implements IoFuture<T> {
    private static final Logger log = Logger.getLogger("org.jboss.xnio.future");

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
                while (status == Status.WAITING) try {
                    lock.wait();
                } catch (InterruptedException e) {
                    intr = true;
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
        long duration = timeUnit.toMillis(time);
        long deadline = duration + System.currentTimeMillis();
        if (deadline < 0L) {
            deadline = Long.MAX_VALUE;
        }
        synchronized (lock) {
            boolean intr = Thread.interrupted();
            try {
                while (status == Status.WAITING) try {
                    lock.wait(duration);
                } catch (InterruptedException e) {
                    intr = true;
                }
                duration = deadline - System.currentTimeMillis();
                if (duration <= 0L) {
                    return Status.WAITING;
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
            while (status == Status.WAITING) {
                lock.wait();
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
        long duration = timeUnit.toMillis(time);
        long deadline = duration + System.currentTimeMillis();
        if (deadline < 0L) {
            deadline = Long.MAX_VALUE;
        }
        synchronized (lock) {
            while (status == Status.WAITING) {
                lock.wait(duration);
                duration = deadline - System.currentTimeMillis();
                if (duration <= 0L) {
                    return Status.WAITING;
                }
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
                case CANCELLED: throw new CancellationException("Operation was cancelled");
                default: throw new IllegalStateException("Unexpected state " + status);
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
                case CANCELLED: throw new CancellationException("Operation was cancelled");
                default: throw new IllegalStateException("Unexpected state " + status);
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
                throw new IllegalStateException("getException() when state is not FAILED");
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
                    log.warn(t, "Running notifier failed");
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
     * Cancel an operation.  The actual cancel may be synchronous or asynchronous.  Implementors will use this method
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
     * Add a cancellation handler.  The argument will be cancelled whenever this instance's {@code cancel()} method
     * is invoked.  The argument may be cancelled more than once, in the event that this {@code IoFuture} instance
     * is cancelled more than once; the handler should be prepared to handle this situation.
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
