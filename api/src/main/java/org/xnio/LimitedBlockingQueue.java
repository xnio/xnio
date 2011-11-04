/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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
