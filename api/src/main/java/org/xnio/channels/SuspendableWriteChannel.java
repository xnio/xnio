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
import org.xnio.WriteChannelThread;

/**
 * A suspendable writable channel.  This type of channel is associated with a listener which can suspend and resume
 * writes as needed.
 */
public interface SuspendableWriteChannel extends CloseableChannel {
    /**
     * Suspend further write notifications on this channel.
     */
    void suspendWrites();

    /**
     * Resume writes on this channel.  The write listener will be
     * called as soon as the channel becomes writable.
     */
    void resumeWrites();

    /**
     * Indicate that writing is complete for this channel.  Further attempts to write after this method is invoked will
     * result in an exception; however, this method may have to be invoked multiple times in order to complete the
     * shutdown operation.  If writes were already shut down successfully, calling this method again will have no
     * additional effect.  Shutting down all directions of a channel will cause {@link #close()} to be called automatically.
     *
     * @return {@code true} if the write channel was closed, or {@code false} if the operation would have blocked
     *
     * @throws IOException if an I/O error occurs
     */
    boolean shutdownWrites() throws IOException;

    /**
     * Block until this channel becomes writable again.  This method may return spuriously
     * before the channel becomes writable.
     *
     * @throws InterruptedIOException if the operation is interrupted; the thread's interrupt flag will be set as well
     * @throws IOException if an I/O error occurs
     *
     * @since 1.2
     */
    void awaitWritable() throws IOException;

    /**
     * Block until this channel becomes writable again, or until the timeout expires.  This method may return spuriously
     * before the channel becomes writable or the timeout expires.
     *
     * @param time the time to wait
     * @param timeUnit the time unit
     * @throws InterruptedIOException if the operation is interrupted; the thread's interrupt flag will be set as well
     * @throws IOException if an I/O error occurs
     *
     * @since 1.2
     */
    void awaitWritable(long time, TimeUnit timeUnit) throws IOException;

    /**
     * Set the write thread.
     *
     * @param thread the write thread
     * @throws IllegalArgumentException if the write thread comes from a different provider than this channel
     */
    void setWriteThread(WriteChannelThread thread) throws IllegalArgumentException;

    /**
     * Get the setter which can be used to change the write listener for this channel.  When the listener is called,
     * additional notifications are automatically suspended.
     *
     * @return the setter
     *
     * @since 2.0
     */
    ChannelListener.Setter<? extends SuspendableWriteChannel> getWriteSetter();

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends SuspendableWriteChannel> getCloseSetter();

    /**
     * Flush any waiting partial send or write.  Flushing a channel for which output was shut down is permitted; this
     * method would simply return {@code true} in this case, since there is no outstanding data to flush.
     *
     * @return {@code true} if the message was flushed, or {@code false} if the result would block
     * @throws java.io.IOException if an I/O error occurs
     */
    boolean flush() throws IOException;
}
