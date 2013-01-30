/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2013 Red Hat, Inc. and/or its affiliates.
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

package org.xnio.conduits;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.TimeUnit;
import org.xnio.XnioIoThread;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface SourceConduit extends Conduit {

    /**
     * Indicate that no more data will be read from this conduit.  If unread data exists, an exception
     * <em>may</em> be thrown.
     *
     * @throws IOException if there was a problem
     */
    void terminateReads() throws IOException;

    /**
     * Determine whether reads have been shut down on this conduit.
     *
     * @return {@code true} if writes are shut down, {@code false} otherwise
     */
    boolean isReadShutdown();

    /**
     * Indicate that the conduit's {@link ReadReadyHandler} should be invoked as soon as data can be read
     * without blocking.
     */
    void resumeReads();

    /**
     * Indicate that calling the conduit's {@link ReadReadyHandler} should be suspended.
     */
    void suspendReads();

    /**
     * Indicate that the conduit's {@link ReadReadyHandler} should be invoked immediately, and then again as soon
     * as data can be read without blocking.
     */
    void wakeupReads();

    /**
     * Determine whether read notifications are currently enabled.
     *
     * @return {@code true} if read notifications are enabled
     */
    boolean isReadResumed();

    /**
     * Block until this channel becomes readable again.  This method may return spuriously before the channel becomes
     * readable.
     *
     * @throws InterruptedIOException if the operation is interrupted; the thread's interrupt flag will be set as well
     * @throws IOException if an I/O error occurs
     */
    void awaitReadable() throws IOException;

    /**
     * Block until this conduit becomes readable again, or until the timeout expires.  This method may return
     * spuriously before the conduit becomes readable or the timeout expires.
     *
     * @param time the time to wait
     * @param timeUnit the time unit
     *
     * @throws InterruptedIOException if the operation is interrupted; the thread's interrupt flag will be set as well
     * @throws IOException if an I/O error occurs
     */
    void awaitReadable(long time, TimeUnit timeUnit) throws IOException;

    /**
     * Get the XNIO read thread.
     *
     * @return the XNIO read thread
     */
    XnioIoThread getReadThread();

    /**
     * Set the handler which should receive readiness notifications.  A filter may
     * pass this invocation on to the filter it wraps, or it may substitute itself.
     *
     * @param next the filter to receive readiness notifications
     */
    void setReadReadyHandler(ReadReadyHandler handler);
}
