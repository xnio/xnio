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
     * Get the setter which can be used to change the accept listener for this channel.  When the listener is called,
     * additional notifications are automatically suspended.
     *
     * @return the setter
     */
    ChannelListener.Setter<? extends SuspendableAcceptChannel> getAcceptSetter();

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends SuspendableAcceptChannel> getCloseSetter();
}
