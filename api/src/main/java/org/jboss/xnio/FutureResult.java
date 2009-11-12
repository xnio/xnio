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
 * A result with a corresponding {@link org.jboss.xnio.IoFuture} instance.
 *
 * @param <T> the {@code IoFuture} result type
 */
public class FutureResult<T> implements Result<T> {

    private final AbstractIoFuture<T> ioFuture;

    /**
     * Construct a new instance.
     *
     * @param executor the executor to use to execute handler notifiers.
     */
    public FutureResult(final Executor executor) {
        ioFuture = new AbstractIoFuture<T>() {
            protected Executor getNotifierExecutor() {
                return executor;
            }
        };
    }

    /**
     * Construct a new instance.  The direct executor will be used to execute handler notifiers.
     */
    public FutureResult() {
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
     * Add a cancellation handler.  The argument will be cancelled whenever the {@code IoFuture} is cancelled.  If
     * the {@code IoFuture} is already cancelled when this method is called, the handler will be called directly.
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
    public boolean setCancelled() {
        return ioFuture.setCancelled();
    }
}
