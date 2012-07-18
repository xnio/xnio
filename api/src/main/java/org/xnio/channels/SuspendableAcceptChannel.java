/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2010 Red Hat, Inc. and/or its affiliates.
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

/**
 * A suspendable accept channel.  This type of channel is associated with a listener which can suspend and resume
 * accepting connections as needed.
 *
 * @since 3.0
 */
public interface SuspendableAcceptChannel extends CloseableChannel {
    /**
     * Suspend further read notifications on this channel.
     */
    void suspendAccepts();

    /**
     * Resume reads on this channel.  The accept listener will be
     * called as soon as there is a connection available to be accepted.
     */
    void resumeAccepts();

    /**
     * {@link #resumeAccepts()} Resume accepts} on this channel, and force the accept listener to be triggered even if the
     * channel isn't actually ready.
     */
    void wakeupAccepts();

    /**
     * Block until this channel becomes acceptable again.  This method may return spuriously
     * before the channel becomes acceptable.
     *
     * @throws InterruptedIOException if the operation is interrupted; the thread's interrupt flag will be set as well
     * @throws IOException if an I/O error occurs
     */
    void awaitAcceptable() throws IOException;

    /**
     * Block until this channel becomes acceptable again, or until the timeout expires.  This method may return spuriously
     * before the channel becomes acceptable or the timeout expires.
     *
     * @param time the time to wait
     * @param timeUnit the time unit
     * @throws InterruptedIOException if the operation is interrupted; the thread's interrupt flag will be set as well
     * @throws IOException if an I/O error occurs
     */
    void awaitAcceptable(long time, TimeUnit timeUnit) throws IOException;

    /**
     * Get the setter which can be used to change the accept listener for this channel.
     *
     * @return the setter
     */
    ChannelListener.Setter<? extends SuspendableAcceptChannel> getAcceptSetter();

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends SuspendableAcceptChannel> getCloseSetter();
}
