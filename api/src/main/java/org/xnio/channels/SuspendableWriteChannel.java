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
     * Determine whether writes are resumed.
     *
     * @return {@code true} if writes are resumed, {@code false} if writes are suspended
     */
    boolean isWriteResumed();

    /**
     * {@link #resumeWrites() Resume writes} on this channel, and force the write listener to be triggered even if the
     * channel isn't actually writable.
     *
     * @deprecated Users should instead submit {@code Runnable} tasks to the channel thread when this functionality is needed.
     */
    @Deprecated
    void wakeupWrites();

    /**
     * Indicate that writing is complete for this channel.  Further attempts to write data to this channel
     * after this method is invoked will result in an exception.  If this method was already called, calling this method
     * again will have no additional effect.  After this method is called, any remaining data still must be flushed out
     * via the {@link #flush()} method; once this is done, if the read side of the channel was shut down, the channel will
     * automatically close.
     *
     * @throws IOException if an I/O error occurs
     */
    void shutdownWrites() throws IOException;

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
     * Get the write thread for this channel.
     *
     * @return the thread, or {@code null} if none is configured or available
     * @deprecated The {@link #getIoThread()} method should be used instead.
     */
    @Deprecated
    XnioExecutor getWriteThread();

    /**
     * Get the setter which can be used to change the write listener for this channel.
     *
     * @return the setter
     *
     * @since 2.0
     */
    ChannelListener.Setter<? extends SuspendableWriteChannel> getWriteSetter();

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends SuspendableWriteChannel> getCloseSetter();

    /**
     * Flush any waiting partial send or write.  If there is no data to flush, or if the flush completed successfully,
     * this method will return {@code true}.  If there is data to flush which cannot be immediately written, this method
     * will return {@code false}.  If this method returns {@code true} after {@link #shutdownWrites()} was called on
     * this channel, the write listener will no longer be invoked on this channel.  If this is case and additionally
     * this is a write-only channel or the read side was previously shut down, then the channel will
     * automatically be closed.
     *
     * @return {@code true} if the message was flushed, or {@code false} if the result would block
     * @throws IOException if an I/O error occurs
     */
    boolean flush() throws IOException;

    /**
     * Determine whether this channel is open.  This method will return {@code false} if all directions are shut down,
     * even if there is unflushed write data pending.
     *
     * @return {@code true} if the channel is open, {@code false} otherwise
     */
    boolean isOpen();

    /**
     * Close this channel.  If data has been written but not flushed, that data may be discarded, depending on the
     * channel implementation.  When a channel is closed, its close listener is invoked.  Invoking this method more than
     * once has no additional effect.
     *
     * @throws IOException if the close failed
     */
    void close() throws IOException;
}
