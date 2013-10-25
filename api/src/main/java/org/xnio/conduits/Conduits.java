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

import org.xnio.Buffers;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

/**
 * General utility methods for manipulating conduits.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class Conduits {

    /**
     * Platform-independent channel-to-channel transfer method.  Uses regular {@code read} and {@code write} operations
     * to move bytes from the {@code source} channel to the {@code sink} channel.  After this call, the {@code throughBuffer}
     * should be checked for remaining bytes; if there are any, they should be written to the {@code sink} channel before
     * proceeding.  This method may be used with NIO channels, XNIO channels, or a combination of the two.
     * <p>
     * If either or both of the given channels are blocking channels, then this method may block.
     *
     * @param source the source channel to read bytes from
     * @param count the number of bytes to transfer (must be >= {@code 0L})
     * @param throughBuffer the buffer to transfer through (must not be {@code null})
     * @param sink the sink channel to write bytes to
     * @return the number of bytes actually transferred (possibly 0)
     * @throws java.io.IOException if an I/O error occurs during the transfer of bytes
     */
    public static long transfer(final StreamSourceConduit source, final long count, final ByteBuffer throughBuffer, final WritableByteChannel sink) throws IOException {
        long res;
        long total = 0L;
        throughBuffer.limit(0);
        while (total < count) {
            throughBuffer.compact();
            try {
                if (count - total < (long) throughBuffer.remaining()) {
                    throughBuffer.limit((int) (count - total));
                }
                res = source.read(throughBuffer);
                if (res <= 0) {
                    return total == 0L ? res : total;
                }
            } finally {
                throughBuffer.flip();
            }
            res = sink.write(throughBuffer);
            if (res == 0) {
                return total;
            }
            total += res;
        }
        return total;
    }

    /**
     * Platform-independent channel-to-channel transfer method.  Uses regular {@code read} and {@code write} operations
     * to move bytes from the {@code source} channel to the {@code sink} channel.  After this call, the {@code throughBuffer}
     * should be checked for remaining bytes; if there are any, they should be written to the {@code sink} channel before
     * proceeding.  This method may be used with NIO channels, XNIO channels, or a combination of the two.
     * <p>
     * If either or both of the given channels are blocking channels, then this method may block.
     *
     * @param source the source channel to read bytes from
     * @param count the number of bytes to transfer (must be >= {@code 0L})
     * @param throughBuffer the buffer to transfer through (must not be {@code null})
     * @param sink the sink channel to write bytes to
     * @return the number of bytes actually transferred (possibly 0)
     * @throws java.io.IOException if an I/O error occurs during the transfer of bytes
     */
    public static long transfer(final ReadableByteChannel source, final long count, final ByteBuffer throughBuffer, final StreamSinkConduit sink) throws IOException {
        long res;
        long total = 0L;
        throughBuffer.limit(0);
        while (total < count) {
            throughBuffer.compact();
            try {
                if (count - total < (long) throughBuffer.remaining()) {
                    throughBuffer.limit((int) (count - total));
                }
                res = source.read(throughBuffer);
                if (res <= 0) {
                    return total == 0L ? res : total;
                }
            } finally {
                throughBuffer.flip();
            }
            res = sink.write(throughBuffer);
            if (res == 0) {
                return total;
            }
            total += res;
        }
        return total;
    }

    /**
     * Writes the buffer to the conduit, and terminates writes if all the data is written
     * @param conduit The conduit to write to
     * @param src The data to write
     * @return The number of bytes written
     * @throws IOException
     */
    public static int writeFinalBasic(StreamSinkConduit conduit, ByteBuffer src) throws IOException {
        int res = conduit.write(src);
        if(!src.hasRemaining()) {
            conduit.terminateWrites();
        }
        return res;
    }

    /**
     * Writes the buffer to the conduit, and terminates writes if all the data is written
     * @param conduit The conduit to write to
     * @param srcs The data to write
     * @param offset The offset into the data array
     * @param length The number of buffers to write
     * @return The number of bytes written
     * @throws IOException
     */
    public static long writeFinalBasic(StreamSinkConduit conduit, ByteBuffer[] srcs, int offset, int length) throws IOException {
        final long res = conduit.write(srcs, offset, length);
        if (!Buffers.hasRemaining(srcs, offset, length)) {
            conduit.terminateWrites();
        }
        return res;
    }

    /**
     * Writes a message to the conduit, and terminates writes if the send was successfully.
     * @param conduit The conduit
     * @param src The message buffer
     * @return <code>true</code> if the message was sent successfully
     */
    public static boolean sendFinalBasic(MessageSinkConduit conduit, ByteBuffer src) throws IOException {
        if(conduit.send(src)) {
            conduit.terminateWrites();
            return true;
        }
        return false;
    }

    /**
     * Writes a message to the conduit, and terminates writes if the send was successfully.
     * @param conduit The conduit
     * @param srcs The message buffers
     * @param offset The offset in the message buffers
     * @param length The number of buffers to send
     * @return <code>true</code> if the message was sent successfully
     */
    public static boolean sendFinalBasic(MessageSinkConduit conduit, ByteBuffer[] srcs, int offset, int length) throws IOException {
        if(conduit.send(srcs, offset, length)) {
            conduit.terminateWrites();
            return true;
        }
        return false;
    }
}
