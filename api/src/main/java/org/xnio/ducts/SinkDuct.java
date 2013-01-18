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

package org.xnio.ducts;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.xnio.XnioExecutor;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface SinkDuct extends Duct {

    void terminateWrites() throws IOException;

    /**
     * Determine whether writes have been <em>fully</em> shut down on this duct.
     *
     * @return {@code true} if writes are fully shut down, {@code false} otherwise
     */
    boolean isWriteShutdown();

    void resumeWrites();

    void suspendWrites();

    void wakeupWrites();

    boolean isWriteResumed();

    void awaitWritable() throws IOException;

    void awaitWritable(long time, TimeUnit timeUnit) throws IOException;

    XnioExecutor getWriteThread();

    /**
     * Set the handler which should receive readiness notifications.  A filter may
     * pass this invocation on to the filter it wraps, or it may substitute itself.
     *
     * @param next the filter to receive readiness notifications
     */
    void setWriteReadyHandler(WriteReadyHandler handler);

    /**
     * Terminate writes and discard any outstanding write data.  The duct is terminated and flushed regardless
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
