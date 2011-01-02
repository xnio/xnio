/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc., and individual contributors as indicated
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
import org.xnio.ReadChannelThread;

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
     * Set the read thread.
     *
     * @param thread the read thread
     * @throws IllegalArgumentException if the read thread comes from a different provider than this channel
     */
    void setReadThread(ReadChannelThread thread) throws IllegalArgumentException;

    /**
     * Get the setter which can be used to change the read listener for this channel.  When the listener is called,
     * additional notifications are automatically suspended.
     *
     * @return the setter
     *
     * @since 2.0
     */
    ChannelListener.Setter<? extends SuspendableReadChannel> getReadSetter();

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends SuspendableReadChannel> getCloseSetter();
}
