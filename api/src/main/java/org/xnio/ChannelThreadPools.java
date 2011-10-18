/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, JBoss Inc., and individual contributors as indicated
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import org.jboss.logging.Logger;

/**
 * Utility methods for channel thread pools.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ChannelThreadPools {

    private static final ChannelThread[] NO_THREADS = new ChannelThread[0];

    private ChannelThreadPools() {
    }

    /**
     * Create a thread pool using a random load-balancing strategy.  This pool access strategy has an O(1) access
     * time, using a PRNG to choose the thread.
     *
     * @param <T> the channel thread type
     * @return the thread pool
     */
    public static <T extends ChannelThread> ChannelThreadPool<T> createRandomPool() {
        return new Random<T>();
    }

    /**
     * Create a thread pool using a lightest-load algorithm.  The thread with the lightest channel load is always
     * returned.  This pool access strategy has an O(n) access time.
     *
     * @param <T> the channel thread type
     * @return the thread pool
     */
    public static <T extends ChannelThread> ChannelThreadPool<T> createLightestLoadPool() {
        return new LightestLoad<T>();
    }

    /**
     * Create a thread pool using a round-robin load-balancing strategy.  This pool access strategy has an O(1) access
     * time, using a counter to choose the thread.
     *
     * @param <T> the channel thread type
     * @return the thread pool
     */
    public static <T extends ChannelThread> ChannelThreadPool<T> createRoundRobinPool() {
        return new RoundRobin<T>();
    }

    /**
     * Create a singleton thread pool.  Such a pool always returns the same thread.
     *
     * @param thread the thread to return
     * @param <T> the thread type
     * @return the singleton thread pool
     */
    public static <T extends ChannelThread> ChannelThreadPool<T> singleton(final T thread) {
        return new Singleton<T>(thread);
    }

    /**
     * Create read threads and add them to a pool.  If thread creation fails, then all the added threads will be
     * shut down before the method returns.
     *
     * @param xnio the XNIO provider from which the threads should be created
     * @param pool the thread pool to add to
     * @param count the number of threads to create
     * @param optionMap the option map to apply to the created threads
     * @return the passed-in thread pool
     * @throws IOException if creation of a thread fails
     */
    public static ChannelThreadPool<ReadChannelThread> addReadThreadsToPool(Xnio xnio, ChannelThreadPool<ReadChannelThread> pool, int count, OptionMap optionMap) throws IOException {
        return addReadThreadsToPool(xnio, pool, null, count, optionMap);
    }

    /**
     * Create read threads and add them to a pool.  If thread creation fails, then all the added threads will be
     * shut down before the method returns.
     *
     * @param xnio the XNIO provider from which the threads should be created
     * @param pool the thread pool to add to
     * @param threadGroup the thread group for newly created threads
     * @param count the number of threads to create
     * @param optionMap the option map to apply to the created threads
     * @return the passed-in thread pool
     * @throws IOException if creation of a thread fails
     */
    public static ChannelThreadPool<ReadChannelThread> addReadThreadsToPool(Xnio xnio, ChannelThreadPool<ReadChannelThread> pool, ThreadGroup threadGroup, int count, OptionMap optionMap) throws IOException {
        boolean ok = false;
        final List<ReadChannelThread> threads = new ArrayList<ReadChannelThread>(count);
        try {
            for (int i = 0; i < count; i ++) {
                final ReadChannelThread thread = xnio.createReadChannelThread(threadGroup, optionMap);
                threads.add(thread);
            }
            ok = true;
        } finally {
            if (! ok) {
                for (ReadChannelThread thread : threads) {
                    thread.shutdown();
                }
            }
        }
        for (ReadChannelThread thread : threads) {
            pool.addToPool(thread);
        }
        return pool;
    }

    /**
     * Create write threads and add them to a pool.  If thread creation fails, then all the added threads will be
     * shut down before the method returns.
     *
     * @param xnio the XNIO provider from which the threads should be created
     * @param pool the thread pool to add to
     * @param count the number of threads to create
     * @param optionMap the option map to apply to the created threads
     * @return the passed-in thread pool
     * @throws IOException if creation of a thread fails
     */
    public static ChannelThreadPool<WriteChannelThread> addWriteThreadsToPool(Xnio xnio, ChannelThreadPool<WriteChannelThread> pool, int count, OptionMap optionMap) throws IOException {
        return addWriteThreadsToPool(xnio, pool, null, count, optionMap);
    }

    /**
     * Create write threads and add them to a pool.  If thread creation fails, then all the added threads will be
     * shut down before the method returns.
     *
     * @param xnio the XNIO provider from which the threads should be created
     * @param pool the thread pool to add to
     * @param threadGroup the thread group for newly created threads
     * @param count the number of threads to create
     * @param optionMap the option map to apply to the created threads
     * @return the passed-in thread pool
     * @throws IOException if creation of a thread fails
     */
    public static ChannelThreadPool<WriteChannelThread> addWriteThreadsToPool(Xnio xnio, ChannelThreadPool<WriteChannelThread> pool, ThreadGroup threadGroup, int count, OptionMap optionMap) throws IOException {
        boolean ok = false;
        final List<WriteChannelThread> threads = new ArrayList<WriteChannelThread>(count);
        try {
            for (int i = 0; i < count; i ++) {
                final WriteChannelThread thread = xnio.createWriteChannelThread(threadGroup, optionMap);
                threads.add(thread);
            }
            ok = true;
        } finally {
            if (! ok) {
                for (WriteChannelThread thread : threads) {
                    thread.shutdown();
                }
            }
        }
        for (WriteChannelThread thread : threads) {
            pool.addToPool(thread);
        }
        return pool;
    }

    /**
     * Shut down a whole thread pool.
     *
     * @param pool the thread pool
     * @see ChannelThread#shutdown()
     */
    public static void shutdown(final ChannelThreadPool<?> pool) {
        ChannelThread thread;
        while ((thread = pool.getThread()) != null) {
            thread.shutdown();
        }
    }

    private static final Logger poolLog = Logger.getLogger("org.xnio.thread-pools");

    private abstract static class SimpleThreadPool<T extends ChannelThread> implements ChannelThreadPool<T> {
        private final Set<T> threadSet = new HashSet<T>();

        private final ChannelThread.Listener listener = new ChannelThread.Listener() {
            @SuppressWarnings("unchecked")
            public void handleTerminationInitiated(final ChannelThread thread) {
                thread.removeTerminationListener(this);
                final Set<T> threadSet = SimpleThreadPool.this.threadSet;
                synchronized (threadSet) {
                    if (threadSet.remove(thread)) {
                        pool = threadSet.toArray((T[]) new ChannelThread[threadSet.size()]);
                    }
                }
            }

            public void handleTerminationComplete(final ChannelThread thread) {
            }
        };

        @SuppressWarnings("unchecked")
        volatile T[] pool = (T[]) NO_THREADS;

        public void addToPool(final T thread) {
            poolLog.tracef("Adding thread %s to pool %s", thread, this);
            final Set<T> threadSet = this.threadSet;
            synchronized (threadSet) {
                if (threadSet.add(thread)) {
                    final T[] pool = this.pool;
                    final int oldLen = pool.length;
                    final T[] newPool = Arrays.copyOf(pool, oldLen + 1);
                    newPool[oldLen] = thread;
                    thread.addTerminationListener(listener);
                    this.pool = newPool;
                }
            }
        }

        public void execute(final Runnable task) {
            getThread().execute(task);
        }

        public ChannelThread.Key executeAfter(final Runnable command, final long time) {
            return getThread().executeAfter(command, time);
        }
    }

    private static class RoundRobin<T extends ChannelThread> extends SimpleThreadPool<T> {

        @SuppressWarnings("unused")
        private volatile int idx;

        @SuppressWarnings("unchecked")
        private static final AtomicIntegerFieldUpdater<RoundRobin> idxUpdater = AtomicIntegerFieldUpdater.newUpdater(RoundRobin.class, "idx");

        public T getThread() {
            final T[] pool = this.pool;
            final int len = pool.length;
            if (len == 0) {
                return null;
            }
            return pool[idxUpdater.getAndIncrement(this) % len];
        }
    }

    private static class LightestLoad<T extends ChannelThread> extends SimpleThreadPool<T> {

        public T getThread() {
            final T[] pool = this.pool;
            final int len = pool.length;
            if (len == 0) {
                return null;
            }
            int best = Integer.MAX_VALUE;
            int bestIdx = -1;
            for (int i = 0; i < len; i++) {
                final int load = pool[i].getLoad();
                if (load < best) {
                    bestIdx = i;
                }
            }
            final T thread = pool[bestIdx];
            poolLog.tracef("Returning thread %s from pool %s", thread, this);
            return thread;
        }
    }

    private static class Random<T extends ChannelThread> extends SimpleThreadPool<T> {

        private final java.util.Random random;

        Random() {
            this(new java.util.Random());
        }

        Random(final java.util.Random random) {
            this.random = random;
        }

        public T getThread() {
            final T[] pool = this.pool;
            final int len = pool.length;
            if (len == 0) {
                return null;
            }
            final T thread = pool[random.nextInt(len)];
            poolLog.tracef("Returning thread %s from pool %s", thread, this);
            return thread;
        }
    }

    private static class Singleton<T extends ChannelThread> implements ChannelThreadPool<T> {

        private final T thread;

        Singleton(final T thread) {
            this.thread = thread;
        }

        public T getThread() {
            poolLog.tracef("Returning thread %s from pool %s", thread, this);
            return thread;
        }

        public void addToPool(final T thread) {
            throw new IllegalArgumentException("Pool is full");
        }

        public void execute(final Runnable task) throws RejectedExecutionException {
            thread.execute(task);
        }

        public ChannelThread.Key executeAfter(final Runnable command, final long time) {
            return thread.executeAfter(command, time);
        }
    }
}
