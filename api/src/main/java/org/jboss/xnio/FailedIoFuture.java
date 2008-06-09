package org.jboss.xnio;

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
     * Run a notifier.  The base implemenation runs the notifier in the current thread, always.
     *
     * @param notifier the notifier to run
     */
    protected void runNotifier(final Notifier<T> notifier) {
        notifier.notify(this);
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
