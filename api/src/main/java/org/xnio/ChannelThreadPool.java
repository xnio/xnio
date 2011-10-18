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

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * A channel thread pool.  This is simply a collection of channel threads.
 *
 * @param <T> the channel thread type
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface ChannelThreadPool<T extends ChannelThread> extends Executor {

    /**
     * Get a thread from this pool.  The thread returned is based upon the load-balancing policy
     * of the pool.  Note that getting a thread does not remove it from the pool.
     *
     * @return the thread
     */
    T getThread();

    /**
     * Add a thread to the pool.  The thread should not already be in the pool; adding an already pooled thread
     * has no effect.
     *
     * @param thread the thread to add to the pool
     */
    void addToPool(T thread);

    /**
     * Execute a task on one of the channel threads.
     *
     * @param task the task to execute
     * @throws RejectedExecutionException if a task cannot be accepted by the thread pool due to shutdown
     */
    void execute(Runnable task) throws RejectedExecutionException;

    /**
     * Execute a task after the given interval.  More time than the given interval may elapse before
     * the task is called.
     *
     * @param command the command to execute
     * @param time the approximate time to delay, in milliseconds
     * @return the execution key
     */
    ChannelThread.Key executeAfter(Runnable command, long time);
}
