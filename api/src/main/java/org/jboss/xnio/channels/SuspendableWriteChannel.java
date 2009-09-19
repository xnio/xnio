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

package org.jboss.xnio.channels;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.jboss.xnio.ChannelListener;

/**
 * A suspendable writable channel.  This type of channel is associated with a handler which can suspend and resume
 * writes as needed.
 */
public interface SuspendableWriteChannel extends CloseableChannel {
    /**
     * Suspend further write notifications on this channel.
     */
    void suspendWrites();

    /**
     * Resume writes on this channel.  The write handler channel listener will be
     * called as soon as the channel becomes writable.
     */
    void resumeWrites();

    /**
     * Indicate that writing is complete for this channel.  Further attempts to write after shutdown will result in an
     * exception.
     *
     * @throws IOException if an I/O error occurs
     */
    void shutdownWrites() throws IOException;

    /**
     * Block until this channel becomes writable again.  This method may return spuriously
     * before the channel becomes writable.
     *
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
     * @throws IOException if an I/O error occurs
     *
     * @since 1.2
     */
    void awaitWritable(long time, TimeUnit timeUnit) throws IOException;

    /**
     * Get the setter which can be used to change the write handler for this channel.  When the handler is called,
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
     * Flush any waiting partial send or write.  Should be called after the final send or write to ensure that
     * all buffers are cleared; some channels (especially encapsulated protocols) may experience "missing" or heavily
     * delayed delivery if a {@code true} result is not obtained from this method before the data is considered to be
     * sent by the application.
     *
     * @return {@code true} if the message was flushed, or {@code false} if the result would block
     * @throws java.io.IOException if an I/O error occurs
     */
    boolean flush() throws IOException;
}
