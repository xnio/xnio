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

import java.util.concurrent.TimeUnit;
import java.io.IOException;

/**
 * An {@code IoFuture} implementation that wraps a different type of {@code IoFuture}.  Used to create general wrappers
 * that convert one channel type to another.
 *
 * @param <T> the type of this future result
 * @param <D> the type of the delegate result
 */
public abstract class AbstractConvertingIoFuture<T, D> implements IoFuture<T> {
    /**
     * The delegate future result.
     */
    protected final IoFuture<? extends D> delegate;

    protected AbstractConvertingIoFuture(final IoFuture<? extends D> delegate) {
        this.delegate = delegate;
    }

    protected IoFuture<? extends D> getDelegate() {
        return delegate;
    }

    public IoFuture<T> cancel() {
        delegate.cancel();
        return this;
    }

    public Status getStatus() {
        return delegate.getStatus();
    }

    public Status await() {
        return delegate.await();
    }

    public Status await(final long time, final TimeUnit timeUnit) {
        return delegate.await(time, timeUnit);
    }

    public Status awaitInterruptibly() throws InterruptedException {
        return delegate.awaitInterruptibly();
    }

    public Status awaitInterruptibly(final long time, final TimeUnit timeUnit) throws InterruptedException {
        return delegate.awaitInterruptibly(time, timeUnit);
    }

    public IOException getException() throws IllegalStateException {
        return delegate.getException();
    }

    public T get() throws IOException {
        return convert(delegate.get());
    }

    public T getInterruptibly() throws IOException, InterruptedException {
        return convert(delegate.getInterruptibly());
    }

    abstract protected T convert(D arg) throws IOException;

    public <A> IoFuture<T> addNotifier(final Notifier<? super T, A> notifier, A attachment) {
        delegate.addNotifier(new Notifier<D, A>() {
            public void notify(final IoFuture<? extends D> future, A attachment) {
                notifier.notify(AbstractConvertingIoFuture.this, attachment);
            }
        }, attachment);
        return this;
    }
}
