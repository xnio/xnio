
package org.xnio;

import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;

import static org.xnio.IoFuture.Status.DONE;

/**
 * An implementation of {@link IoFuture} that represents an immediately-successful operation.
 *
 * @param <T> the type of result that this operation produces
 */
public class FinishedIoFuture<T> implements IoFuture<T> {

    private final T result;

    /**
     * Create an instance.
     *
     * @param result the operation result
     */
    public FinishedIoFuture(T result) {
        this.result = result;
    }

    /**
     * Cancel the operation.  Since this operation is always complete, this is a no-op.
     *
     * @return this instance
     */
    public IoFuture<T> cancel() {
        return this;
    }

    @Override
    public Status getStatus() {
        return DONE;
    }

    @Override
    public Status await() {
        return DONE;
    }

    @Override
    public Status await(final long time, final TimeUnit timeUnit) {
        return DONE;
    }

    @Override
    public Status awaitInterruptibly() throws InterruptedException {
        return DONE;
    }

    @Override
    public Status awaitInterruptibly(final long time, final TimeUnit timeUnit) throws InterruptedException {
        return DONE;
    }

    @Override
    public T get() throws IOException, CancellationException {
        return result;
    }

    @Override
    public T getInterruptibly() throws IOException, InterruptedException, CancellationException {
        return result;
    }

    @Override
    public IOException getException() throws IllegalStateException {
        throw new IllegalStateException();
    }

    @Override
    public <A> IoFuture<T> addNotifier(final Notifier<? super T, A> notifier, final A attachment) {
        notifier.notify(this, attachment);
        return this;
    }
}
