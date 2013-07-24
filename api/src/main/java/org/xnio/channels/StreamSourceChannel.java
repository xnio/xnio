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

import java.nio.ByteBuffer;
import java.nio.channels.ScatteringByteChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.FileChannel;
import java.io.IOException;
import org.xnio.ChannelListener;

/**
 * A stream source channel.  This type of channel is a readable source for bytes.  While all channel types are
  * thread-safe, reading a stream from more than one thread concurrently will cause data corruption and may be
  * disallowed at the option of the implementation.
 */
public interface StreamSourceChannel extends ReadableByteChannel, ScatteringByteChannel, SuspendableReadChannel {

    /**
     * Transfers bytes into the given file from this channel.  Using this method in preference to {@link FileChannel#transferFrom(ReadableByteChannel, long, long)}
     * may provide a performance advantage on some platforms.
     * <p>
     * If the current thread is interrupted when this method is called, it may throw a {@link java.io.InterruptedIOException};
     * however, if this exception is thrown, the {@link java.io.InterruptedIOException#bytesTransferred} field is
     * guaranteed to be 0.
     *
     * @param position the position within the file from which the transfer is to begin
     * @param count the number of bytes to be transferred
     * @param target the file to write to
     * @return the number of bytes (possibly 0) that were actually transferred
     * @throws IOException if an I/O error occurs
     */
    long transferTo(long position, long count, FileChannel target) throws IOException;

    /**
     * Transfers bytes into the given channel target.  On some platforms, this may avoid copying bytes between user
     * and kernel space.  On other platforms, bytes are passed through the {@code throughBuffer} parameter's buffer
     * space.  On entry, {@code throughBuffer} will be cleared.  On exit, the buffer will be
     * flipped for emptying, and may possibly be empty or may contain data.  If this method returns a value less than
     * {@code count}, then the remaining data in {@code throughBuffer} may contain data read from this channel which must
     * be written to {@code target} to complete the operation.  Note that using a direct buffer may provide an
     * intermediate performance gain on platforms without zero-copy facilities.
     * <p>
     * If the current thread is interrupted when this method is called, it may throw a {@link java.io.InterruptedIOException};
     * however, if this exception is thrown, the {@link java.io.InterruptedIOException#bytesTransferred} field is
     * guaranteed to be 0.
     *
     * Note that the return value is the amount of data that was actually transferred to the {@link StreamSinkChannel}.
     * The actual amount of data read could be larger than this, and can be calculated by adding the return value and
     * the amount of data left in {@code throughBuffer}.
     *
     * @param count the number of bytes to be transferred
     * @param throughBuffer the buffer to copy through.
     * @param target the destination to write to
     * @return the number of bytes (possibly 0) that were actually transferred, or -1 if the end of input was reached
     * @throws IOException if an I/O error occurs
     */
    long transferTo(long count, ByteBuffer throughBuffer, StreamSinkChannel target) throws IOException;

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends StreamSourceChannel> getReadSetter();

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends StreamSourceChannel> getCloseSetter();
}
