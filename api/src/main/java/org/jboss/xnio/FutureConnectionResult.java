/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, JBoss Inc., and individual contributors as indicated
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

import java.util.concurrent.Executor;
import java.io.IOException;

/**
 * A result with a corresponding {@link FutureConnection} instance.
 *
 * @param <A> the {@code FutureConnection} address type
 * @param <T> the {@code FutureConnection} result type
 */
public final class FutureConnectionResult<A, T> implements Result<T> {

    private final AbstractFutureConnection<A, T> futureConnection;
    private volatile A localAddress;

    /**
     * Construct a new instance.
     *
     * @param executor the executor to use to execute handler notifiers.
     */
    public FutureConnectionResult(final Executor executor) {
        futureConnection = new AbstractFutureConnection<A, T>() {
            public A getLocalAddress() {
                return localAddress;
            }

            protected Executor getNotifierExecutor() {
                return executor;
            }
        };
    }

    /**
     * Construct a new instance.  The direct executor will be used to execute handler notifiers.
     */
    public FutureConnectionResult() {
        this(IoUtils.directExecutor());
    }

    /**
     * Set the local address.
     *
     * @param localAddress the local address
     */
    public void setLocalAddress(final A localAddress) {
        this.localAddress = localAddress;
    }

    /**
     * Get the {@code FutureConnection} for this result.
     *
     * @return the {@code FutureConnection}
     */
    public FutureConnection<A, T> getFutureConnection() {
        return futureConnection;
    }

    /**
     * Add a cancellation handler.  The argument will be cancelled whenever this instance's {@code cancel()} method
     * is invoked.  The argument may be cancelled more than once, in the event that this {@code IoFuture} instance
     * is cancelled more than once; the handler should be prepared to handle this situation.
     *
     * @param cancellable the cancel handler
     */
    public void addCancelHandler(final Cancellable cancellable) {
        futureConnection.addCancelHandler(cancellable);
    }

    /**
     * Set the result for this operation.  Any threads blocking on this instance will be unblocked.
     *
     * @param result the result to set
     * @return {@code false} if the operation was already completed, {@code true} otherwise
     */
    public boolean setResult(final T result) {
        return futureConnection.setResult(result);
    }

    /**
     * Set the exception for this operation.  Any threads blocking on this instance will be unblocked.
     *
     * @param exception the exception to set
     * @return {@code false} if the operation was already completed, {@code true} otherwise
     */
    public boolean setException(final IOException exception) {
        return futureConnection.setException(exception);
    }

    /**
     * Acknowledge the cancellation of this operation.
     *
     * @return {@code false} if the operation was already completed, {@code true} otherwise
     */
    public boolean setCancelled() {
        return futureConnection.setCancelled();
    }
}
