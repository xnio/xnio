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
import java.nio.channels.FileChannel;
import org.xnio.channels.StreamSinkChannel;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface StreamSourceConduit extends SourceConduit {

    /**
     * Transfers bytes into the given file from this channel.
     *
     * @param position the position within the file from which the transfer is to begin
     * @param count the number of bytes to be transferred
     * @param target the file to write to
     * @return the number of bytes (possibly 0) that were actually transferred
     * @throws IOException if an I/O error occurs
     */
    long transferTo(long position, long count, FileChannel target) throws IOException;

    /**
     * Transfers bytes into the given channel target.  On entry, {@code throughBuffer} will be cleared.  On exit, the buffer will be
     * flipped for emptying, and may possibly be empty or may contain data.  If this method returns a value less than
     * {@code count}, then the remaining data in {@code throughBuffer} may contain data read from this channel which must
     * be written to {@code target} to complete the operation.
     *
     * @param count the number of bytes to be transferred
     * @param throughBuffer the buffer to copy through.
     * @param target the destination to write to
     * @return the number of bytes (possibly 0) that were actually transferred, or -1 if the end of input was reached
     * @throws IOException if an I/O error occurs
     */
    long transferTo(long count, ByteBuffer throughBuffer, StreamSinkChannel target) throws IOException;

    /**
     * Read a sequence of bytes from this conduit to the given buffer.
     *
     * @param src the buffer to fill with data from the conduit
     * @return the number of bytes (possibly 0) that were actually transferred, or -1 if the end of input was reached or
     * this conduit's {@link #terminateReads()} method was previously called
     * @throws IOException if an error occurs
     */
    int read(ByteBuffer dst) throws IOException;

    /**
     * Read a sequence of bytes from this conduit to the given buffers.
     *
     * @param srcs the buffers to fill with data from the conduit
     * @param offs the offset into the buffer array
     * @param len the number of buffers to fill
     * @return the number of bytes (possibly 0) that were actually transferred, or -1 if the end of input was reached or
     * this conduit's {@link #terminateReads()} method was previously called
     * @throws IOException if an error occurs
     */
    long read(ByteBuffer[] dsts, int offs, int len) throws IOException;
}
