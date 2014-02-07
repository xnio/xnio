/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008 Red Hat, Inc. and/or its affiliates.
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

package org.xnio.channels;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.TimeUnit;
import org.xnio.ChannelListener;
import org.xnio.XnioExecutor;

/**
 * A suspendable readable channel.  This type of channel is associated with a listener which can suspend and resume
 * reads as needed.
 */
public interface SuspendableReadChannel extends CloseableChannel {
    /**
     * Suspend further read notifications on this channel.
     */
    void suspendReads();

    /**
     * Resume reads on this channel.  The read listener will be
     * called as soon as there is data available to be read.
     */
    void resumeReads();

    /**
     * Determine whether reads are resumed.
     *
     * @return {@code true} if reads are resumed, {@code false} if reads are suspended
     */
    boolean isReadResumed();

    /**
     * {@link #resumeReads() Resume reads} on this channel, and force the read listener to be triggered even if the
     * channel isn't actually readable.
     *
     * @deprecated Users should instead submit {@code Runnable} tasks to the channel thread when this functionality is needed.
     */
    @Deprecated
    void wakeupReads();

    /**
     * Places this readable channel at "end of stream".  Further reads will result in EOF.
     * Shutting down all directions of a channel will cause {@link #close()} to be called automatically.
     *
     * @throws IOException if an I/O error occurs
     */
    void shutdownReads() throws IOException;

    /**
     * Block until this channel becomes readable again.  This method may return spuriously
     * before the channel becomes readable.
     *
     * @throws InterruptedIOException if the operation is interrupted; the thread's interrupt flag will be set as well
     * @throws IOException if an I/O error occurs
     *
     * @since 1.2
     */
    void awaitReadable() throws IOException;

    /**
     * Block until this channel becomes readable again, or until the timeout expires.  This method may return spuriously
     * before the channel becomes readable or the timeout expires.
     *
     * @param time the time to wait
     * @param timeUnit the time unit
     * @throws InterruptedIOException if the operation is interrupted; the thread's interrupt flag will be set as well
     * @throws IOException if an I/O error occurs
     *
     * @since 1.2
     */
    void awaitReadable(long time, TimeUnit timeUnit) throws IOException;

    /**
     * Get the read thread for this channel.
     *
     * @return the thread, or {@code null} if none is configured or available
     * @deprecated The {@link #getIoThread()} method should be used instead.
     */
    @Deprecated
    XnioExecutor getReadThread();

    /**
     * Get the setter which can be used to change the read listener for this channel.
     *
     * @return the setter
     *
     * @since 2.0
     */
    ChannelListener.Setter<? extends SuspendableReadChannel> getReadSetter();

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends SuspendableReadChannel> getCloseSetter();
}
