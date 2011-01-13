/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, JBoss Inc., and individual contributors as indicated
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
import java.util.concurrent.Future;

/**
 * A channel thread.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface ChannelThread extends Executor {

    /**
     * Execute a runnable task on this channel thread.
     *
     * @param command the task to execute
     */
    void execute(Runnable command);

    /**
     * Submit a parameterized task on this channel thread, collecting the result.
     *
     * @param task the task
     * @param parameter the parameter to send to the task
     * @param <P> the parameter type
     * @param <R> the return type
     * @return the future result
     */
    <P, R> Future<R> submit(Task<P, R> task, P parameter);

    /**
     * Execute a task on this channel thread, ignoring the result.
     *
     * @param task the task to run
     * @param parameter the parameter to send to the task
     * @param <P> the parameter type
     */
    <P> void execute(Task<P, ?> task, P parameter);

    /**
     * Run a task in the channel thread, waiting for the result.
     *
     * @param task the task
     * @param parameter the parameter to send to the task
     * @param <P> the parameter type
     * @param <R> the return type
     * @return the result
     */
    <P, R> R run(Task<P, R> task, P parameter);

    /**
     * Get the approximate load on this thread, in channels.
     *
     * @return the approximate load
     */
    int getLoad();

    /**
     * Initiate shutdown of this thread.  The thread will accept no new channel associations, but will
     * continue running until all existing channel associations have terminated.
     */
    void shutdown();

    /**
     * Wait for this thread to terminate.
     *
     * @throws InterruptedException if the calling thread was interrupted while waiting
     */
    void awaitTermination() throws InterruptedException;

    /**
     * Add a task to run upon termination of this thread.  If the thread is already terminated,
     * the task will be called immediately from the calling thread.
     *
     * @param listener the termination task
     */
    void addTerminationListener(Listener listener);

    /**
     * Remove a termination listener.
     *
     * @param listener the listener to remove
     */
    void removeTerminationListener(Listener listener);

    /**
     * A listener for a channel thread.
     */
    interface Listener {

        /**
         * Handle the termination initiation of the given channel thread.  This method is called before termination
         * begins, allowing users and thread pools to remove the thread as needed.
         *
         * @param thread the thread that is being terminated
         */
        void handleTerminationInitiated(ChannelThread thread);

        /**
         * Handle the termination completion of the given channel thread.  This method is called after the thread
         * has finished all shutdown activities before the thread itself exits.
         *
         * @param thread the thread that was terminated
         */
        void handleTerminationComplete(ChannelThread thread);
    }

    /**
     * A task to run on a channel thread.
     *
     * @param <P> the task parameter type
     * @param <R> the task result type
     */
    interface Task<P, R> {

        /**
         * Run the task.
         *
         * @param parameter the passed-in parameter
         * @return the result
         */
        R run(P parameter);
    }
}
