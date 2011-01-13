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
     * Execute a task after the given interval.  More time than the given interval may elapse before
     * the task is called.
     *
     * @param command the command to execute
     * @param time the approximate time to delay, in milliseconds
     * @return the execution key
     */
    Key executeAfter(Runnable command, long time);

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
     * A task key for a timeout task.
     */
    interface Key {

        /**
         * Remove a previously-submitted task.
         *
         * @return {@code true} if the task was cancelled; {@code false} if it has already been accepted to run
         */
        boolean remove();

        /**
         * An immediate key.  When the time delay is <= 0, this may be returned and the task immediately run.
         */
        Key IMMEDIATE = new Key() {
            public boolean remove() {
                return false;
            }
        };
    }
}
