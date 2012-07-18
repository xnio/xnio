
package org.xnio;

import java.io.IOException;

/**
 * A handler for the result of an operation.  May be used to populate an {@link IoFuture}.
 */
public interface Result<T> {

    /**
     * Set the result for this operation.  Any threads blocking on this instance will be unblocked.
     *
     * @param result the result to set
     *
     * @return {@code false} if the operation was already completed, {@code true} otherwise
     */
    boolean setResult(T result);

    /**
     * Set the exception for this operation.  Any threads blocking on this instance will be unblocked.
     *
     * @param exception the exception to set
     *
     * @return {@code false} if the operation was already completed, {@code true} otherwise
     */
    boolean setException(IOException exception);

    /**
     * Acknowledge the cancellation of this operation.
     *
     * @return {@code false} if the operation was already completed, {@code true} otherwise
     */
    boolean setCancelled();
}
