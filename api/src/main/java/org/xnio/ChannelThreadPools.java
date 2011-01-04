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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

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
        return new SimpleThreadPool<T>() {

            private final Random random = new Random();

            public T getThread() {
                final T[] pool = this.pool;
                final int len = pool.length;
                if (len == 0) {
                    return null;
                }
                return pool[random.nextInt(len)];
            }
        };
    }

    /**
     * Create a thread pool using a lightest-load algorithm.  The thread with the lightest channel load is always
     * returned.  This pool access strategy has an O(n) access time.
     *
     * @param <T> the channel thread type
     * @return the thread pool
     */
    public static <T extends ChannelThread> ChannelThreadPool<T> createLightestLoadPool() {
        return new SimpleThreadPool<T>() {
            private final Set<T> threadSet = new HashSet<T>();

            @SuppressWarnings( { "unchecked" })
            private volatile T[] pool = (T[]) NO_THREADS;

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
                return pool[bestIdx];
            }
        };
    }

    /**
     * Create a singleton thread pool.  Such a pool always returns the same thread.
     *
     * @param thread the thread to return
     * @param <T> the thread type
     * @return the singleton thread pool
     */
    public static <T extends ChannelThread> ChannelThreadPool<T> singleton(final T thread) {
        return new ChannelThreadPool<T>() {
            public T getThread() {
                return thread;
            }

            public void addToPool(final T thread) {
                throw new IllegalArgumentException("Pool is full");
            }
        };
    }

    private static abstract class SimpleThreadPool<T extends ChannelThread> implements ChannelThreadPool<T> {
        private final Set<T> threadSet = new HashSet<T>();

        private final ChannelThread.Listener listener = new ChannelThread.Listener() {
            public void handleTerminationInitiated(final ChannelThread thread) {
                thread.removeTerminationListener(this);
                final Set<T> threadSet = SimpleThreadPool.this.threadSet;
                synchronized (threadSet) {
                    if (threadSet.remove(thread)) {
                        pool = (T[]) threadSet.toArray(NO_THREADS);
                    }
                }
            }

            public void handleTerminationComplete(final ChannelThread thread) {
            }
        };

        @SuppressWarnings( { "unchecked" })
        volatile T[] pool = (T[]) NO_THREADS;

        public void addToPool(final T thread) {
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
    }
}
