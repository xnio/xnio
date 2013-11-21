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
     * Execute a command repeatedly at a time interval until it is cancelled.  At least the amount of time given in
     * {@code time} will have elapsed when the task is first run, and again for each subsequent run.  The returned key
     * may be used to cancel the task before it runs.
     *
     * @param command the command to execute
     * @param time the amount of time to delay, or {@code 0} to run immediately
     * @param unit the time unit to apply to {@code time}
     * @return a key which may be used to cancel this task before it executes
     */
    Key executeAtInterval(Runnable command, long time, TimeUnit unit);

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
