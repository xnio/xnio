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

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * An executor with the capability to run timed, cancellable tasks.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@SuppressWarnings("unused")
public interface XnioExecutor extends Executor {

    /**
     * Execute a task in this executor.
     *
     * @param command the command to run
     */
    void execute(Runnable command);

    /**
     * Execute a command after a period of time.  At least the amount of time given in {@code time} will
     * have elapsed when the task is run.  The returned key may be used to cancel the task before it runs.
     *
     * @param command the command to execute
     * @param time the amount of time to delay, or {@code 0} to run immediately
     * @param unit the time unit to apply to {@code time}
     * @return a key which may be used to cancel this task before it executes
     */
    Key executeAfter(Runnable command, long time, TimeUnit unit);

    /**
     * A task key for a timeout task.
     */
    interface Key {

        /**
         * Remove a previously-submitted task.
         *
         * @return {@code true} if the task was cancelled; {@code false} if it already ran
         */
        boolean remove();

        /**
         * An immediate key.  When the time delay is <= 0, this may be returned and the task immediately run.
         */
        XnioExecutor.Key IMMEDIATE = new XnioExecutor.Key() {
            public boolean remove() {
                return false;
            }

            public String toString() {
                return "Immediate key";
            }
        };
    }

}
