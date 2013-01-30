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
 * A conduit which is a target or output for data.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface SinkConduit extends Conduit {

    /**
     * Signal that no more write data is forthcoming.  The conduit must be {@link #flush()}ed before it is considered
     * to be shut down.
     *
     * @throws IOException
     */
    void terminateWrites() throws IOException;

    /**
     * Determine whether writes have been <em>fully</em> shut down on this conduit.
     *
     * @return {@code true} if writes are fully shut down, {@code false} otherwise
     */
    boolean isWriteShutdown();

    /**
     * Indicate that the conduit's {@link WriteReadyHandler} should be invoked as soon as data can be written
     * without blocking.
     */
    void resumeWrites();

    /**
     * Indicate that calling the conduit's {@link WriteReadyHandler} should be suspended.
     */
    void suspendWrites();

    /**
     * Indicate that the conduit's {@link WriteReadyHandler} should be invoked immediately, and then again as soon
     * as data can be written without blocking.
     */
    void wakeupWrites();

    /**
     * Determine whether write notifications are currently enabled.
     *
     * @return {@code true} if write notifications are enabled
     */
    boolean isWriteResumed();

    /**
     * Block until this channel becomes writable again.  This method may return spuriously before the channel becomes
     * writable.
     *
     * @throws InterruptedIOException if the operation is interrupted; the thread's interrupt flag will be set
     * as well
     * @throws IOException if an I/O error occurs
     */
    void awaitWritable() throws IOException;

    /**
     * Block until this conduit becomes writable again, or until the timeout expires.  This method may return
     * spuriously before the conduit becomes writable or the timeout expires.
     *
     * @param time the time to wait
     * @param timeUnit the time unit
     *
     * @throws InterruptedIOException if the operation is interrupted; the thread's interrupt flag will be set
     * as well
     * @throws IOException if an I/O error occurs
     */
    void awaitWritable(long time, TimeUnit timeUnit) throws IOException;

    /**
     * Get the write thread for this conduit.
     *
     * @return the thread, or {@code null} if none is configured or available
     */
    XnioIoThread getWriteThread();

    /**
     * Set the handler which should receive readiness notifications.  A filter may
     * pass this invocation on to the filter it wraps, or it may substitute itself.
     *
     * @param next the filter to receive readiness notifications
     */
    void setWriteReadyHandler(WriteReadyHandler handler);

    /**
     * Terminate writes and discard any outstanding write data.  The conduit is terminated and flushed regardless
     * of the outcome of this method.
     *
     * @throws java.io.IOException if channel termination failed for some reason
     */
    void truncateWrites() throws IOException;

    /**
     * Flush out any unwritten, buffered output.
     *
     * @return {@code true} if everything is flushed, {@code false} otherwise
     * @throws java.io.IOException if flush fails
     */
    boolean flush() throws IOException;
}
