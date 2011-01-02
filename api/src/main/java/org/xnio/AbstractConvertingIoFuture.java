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
