/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2009 Red Hat, Inc. and/or its affiliates.
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

import java.util.concurrent.Executor;
import java.io.IOException;

/**
 * A result with a corresponding {@link org.xnio.IoFuture} instance.
 *
 * @param <T> the {@code IoFuture} result type
 */
public class FutureResult<T> implements Result<T> {

    private final AbstractIoFuture<T> ioFuture;

    /**
     * Construct a new instance.
     *
     * @param executor the executor to use to execute listener notifiers.
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
