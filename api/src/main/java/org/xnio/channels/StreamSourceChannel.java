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
