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
import java.nio.channels.WritableByteChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.FileChannel;
import java.io.IOException;
import org.xnio.ChannelListener;

/**
 * A stream sink channel.  This type of channel is a writable destination for bytes.  While all channel types are
 * thread-safe, writing a stream from more than one thread concurrently will cause data corruption and may be
 * disallowed at the option of the implementation.
 */
public interface StreamSinkChannel extends WritableByteChannel, GatheringByteChannel, SuspendableWriteChannel {

    /**
     * Transfer bytes into this channel from the given file.  Using this method in preference to {@link FileChannel#transferTo(long, long, WritableByteChannel)}
     * may provide a performance advantage on some platforms.
     * <p>
     * If the current thread is interrupted when this method is called, it may throw a {@link java.io.InterruptedIOException};
     * however, if this exception is thrown, the {@link java.io.InterruptedIOException#bytesTransferred} field is
     * guaranteed to be 0.
     *
     * @param src the file to read from
     * @param position the position within the file from which the transfer is to begin
     * @param count the number of bytes to be transferred
     * @return the number of bytes (possibly 0) that were actually transferred
     * @throws IOException if an I/O error occurs
     */
    long transferFrom(FileChannel src, long position, long count) throws IOException;

    /**
     * Transfers bytes from the given channel source.  On some platforms, this may avoid copying bytes between user
     * and kernel space.  On other platforms, bytes are passed through the {@code throughBuffer} parameter's buffer
     * space.  On entry, {@code throughBuffer} will be cleared.  On exit, the buffer will be
     * flipped for emptying, and may be empty or may contain data.  If this method returns a value less than
     * {@code count}, then the remaining data in {@code throughBuffer} may contain data read from {@code source} which must
     * be written to this channel to complete the operation.  Note that using a direct buffer may provide an
     * intermediate performance gain on platforms without zero-copy facilities.
     * <p>
     * If the current thread is interrupted when this method is called, it may throw a {@link java.io.InterruptedIOException};
     * however, if this exception is thrown, the {@link java.io.InterruptedIOException#bytesTransferred} field is
     * guaranteed to be 0.
     *
     * @param source the source to read from
     * @param count the number of bytes to be transferred
     * @param throughBuffer the buffer to copy through.
     * @return the number of bytes (possibly 0) that were actually transferred, or -1 if the end of input was reached
     * @throws IOException if an I/O error occurs
     */
    long transferFrom(StreamSourceChannel source, long count, ByteBuffer throughBuffer) throws IOException;

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends StreamSinkChannel> getWriteSetter();

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends StreamSinkChannel> getCloseSetter();
}
