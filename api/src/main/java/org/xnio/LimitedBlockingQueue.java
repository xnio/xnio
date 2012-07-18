/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2011 Red Hat, Inc. and/or its affiliates, and individual
 * contributors as indicated by the @author tags.
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

import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class LimitedBlockingQueue<E> extends AbstractQueue<E> implements BlockingQueue<E> {
    private final BlockingQueue<E> delegate;
    @SuppressWarnings("unused")
    private volatile int count;

    private static final AtomicIntegerFieldUpdater<LimitedBlockingQueue> countUpdater = AtomicIntegerFieldUpdater.newUpdater(LimitedBlockingQueue.class, "count");

    LimitedBlockingQueue(final BlockingQueue<E> delegate, final int count) {
        this.delegate = delegate;
        this.count = count;
    }

    public boolean add(final E e) {
        if (! offer(e)) {
            throw new IllegalStateException();
        }
        return true;
    }

    public boolean offer(final E e) {
        int count = countUpdater.getAndDecrement(this);
        if (count <= 0) {
            countUpdater.getAndIncrement(this);
            return false;
        }
        if (! delegate.offer(e)) {
            countUpdater.getAndIncrement(this);
            return false;
        }
        return true;
    }

    public boolean offerUnchecked(final E e) {
        countUpdater.decrementAndGet(this);
        if (! delegate.offer(e)) {
            countUpdater.getAndIncrement(this);
            return false;
        }
        return true;
    }

    public void put(final E e) throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    public boolean offer(final E e, final long timeout, final TimeUnit unit) throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    public E poll() {
        final E result = delegate.poll();
        if (result != null) countUpdater.getAndIncrement(this);
        return result;
    }

    public E peek() {
        return delegate.peek();
    }

    public E take() throws InterruptedException {
        final E result = delegate.take();
        if (result != null) countUpdater.getAndIncrement(this);
        return result;
    }

    public Iterator<E> iterator() {
        final Iterator<E> iterator = delegate.iterator();
        return new Iterator<E>() {
            public boolean hasNext() {
                return iterator.hasNext();
            }

            public E next() {
                return iterator.next();
            }

            public void remove() {
                iterator.remove();
                countUpdater.getAndIncrement(LimitedBlockingQueue.this);
            }
        };
    }

    public int size() {
        return delegate.size();
    }

    public E poll(final long timeout, final TimeUnit unit) throws InterruptedException {
        final E result = delegate.poll(timeout, unit);
        if (result != null) countUpdater.getAndIncrement(this);
        return result;
    }

    public int remainingCapacity() {
        return countUpdater.get(this);
    }

    public int drainTo(final Collection<? super E> c) {
        final int res = delegate.drainTo(c);
        countUpdater.getAndAdd(this, res);
        return res;
    }

    public int drainTo(final Collection<? super E> c, final int maxElements) {
        final int res = delegate.drainTo(c, maxElements);
        countUpdater.getAndAdd(this, res);
        return res;
    }
}
