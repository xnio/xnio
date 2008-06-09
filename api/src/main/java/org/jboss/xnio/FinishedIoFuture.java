package org.jboss.xnio;

/**
 * An implementation of {@link IoFuture} that represents an immediately-successful operation.
 *
 * @param <T> the type of result that this operation produces
 */
public class FinishedIoFuture<T> extends AbstractIoFuture<T> {
    /**
     * Create an instance.
     *
     * @param result the operation result
     */
    public FinishedIoFuture(T result) {
        setResult(result);
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