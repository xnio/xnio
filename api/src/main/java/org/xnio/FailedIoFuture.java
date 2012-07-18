
package org.xnio;

import java.io.IOException;

/**
 * An implementation of {@link IoFuture} that represents an immediately-failed operation.
 *
 * @param <T> the type of result that this operation produces
 */
public class FailedIoFuture<T> extends AbstractIoFuture<T> {
    /**
     * Create an instance.
     *
     * @param e the failure cause
     */
    public FailedIoFuture(IOException e) {
        setException(e);
    }

    /**
     * Cancel the operation.  Since this operation is always complete, this is a no-op.
     *
     * @return this instance
     */
    public IoFuture<T> cancel() {
        return this;
    }
}
