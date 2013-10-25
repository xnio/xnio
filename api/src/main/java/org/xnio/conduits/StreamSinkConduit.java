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

package org.xnio.conduits;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 * A sink (writable) conduit for byte streams.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface StreamSinkConduit extends SinkConduit {

    /**
     * Transfer bytes into this conduit from the given file.
     *
     * @param src the file to read from
     * @param position the position within the file from which the transfer is to begin
     * @param count the number of bytes to be transferred
     * @return the number of bytes (possibly 0) that were actually transferred
     * @throws IOException if an I/O error occurs
     */
    long transferFrom(FileChannel src, long position, long count) throws IOException;

    /**
     * Transfers bytes from the given channel source.  On entry, {@code throughBuffer} will be cleared.  On exit, the
     * buffer will be flipped for emptying, and may be empty or may contain data.  If this method returns a value less
     * than {@code count}, then the remaining data in {@code throughBuffer} may contain data read from {@code source}
     * which must be written to this channel to complete the operation.
     *
     * @param source the source to read from
     * @param count the number of bytes to be transferred
     * @param throughBuffer the buffer to copy through.
     * @return the number of bytes (possibly 0) that were actually transferred, or -1 if the end of input was reached
     * @throws IOException if an I/O error occurs
     */
    long transferFrom(StreamSourceChannel source, long count, ByteBuffer throughBuffer) throws IOException;

    /**
     * Writes a sequence of bytes to this conduit from the given buffer.
     *
     * @param src the buffer containing data to write
     * @return the number of bytes written, possibly 0
     * @throws ClosedChannelException if this conduit's {@link #terminateWrites()} method was previously called
     * @throws IOException if an error occurs
     */
    int write(ByteBuffer src) throws IOException;

    /**
     * Writes a sequence of bytes to this conduit from the given buffers.
     *
     * @param srcs the buffers containing data to write
     * @param offs the offset into the buffer array
     * @param len the number of buffers to write
     * @return the number of bytes written, possibly 0
     * @throws ClosedChannelException if this conduit's {@link #terminateWrites()} method was previously called
     * @throws IOException if an error occurs
     */
    long write(ByteBuffer[] srcs, int offs, int len) throws IOException;


    /**
     *
     * Writes some data to the conduit, with the same semantics as
     * {@link #write(java.nio.ByteBuffer)}. If all the data is written
     * out then the conduit will have its writes terminated. Semantically this
     * method is equivalent to:
     *
     * <code>
     *     int rem = src.remaining();
     *     int written = conduit.write(src);
     *     if(written == rem) {
     *         conduit.terminateWrites()
     *     }
     * </code>
     *
     * @param src The data to write
     * @return The amount of data that was actually written.
     */
    public int writeFinal(ByteBuffer src) throws IOException;

    /**
     *
     * Writes some data to the conduit, with the same semantics as
     * {@link #write(java.nio.ByteBuffer[], int, int)}. If all the data is written
     * out then the conduit will have its writes terminated.
     *
     *
     * @param  srcs
     *         The buffers from which bytes are to be retrieved
     *
     * @param  offset
     *         The offset within the buffer array of the first buffer from
     *         which bytes are to be retrieved; must be non-negative and no
     *         larger than <tt>srcs.length</tt>
     *
     * @param  length
     *         The maximum number of buffers to be accessed; must be
     *         non-negative and no larger than
     *         <tt>srcs.length</tt>&nbsp;-&nbsp;<tt>offset</tt>
     *
     * @return The amount of data that was actually written
     */
    public long writeFinal(ByteBuffer[] srcs, int offset, int length) throws IOException;
}
