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

import java.nio.channels.WritableByteChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.FileChannel;
import java.io.IOException;
import org.xnio.ChannelListener;

/**
 * A stream sink channel.  This type of channel is a writable destination for bytes.
 */
public interface StreamSinkChannel extends WritableByteChannel, GatheringByteChannel, SuspendableWriteChannel {

    /**
     * Transfer bytes into this channel from the given file.  Using this method in preference to {@link FileChannel#transferTo(long, long, WritableByteChannel)}
     * may provide a performance advantage on some platforms.
     *
     * @param src the file to read from
     * @param position the position within the file from which the transfer is to begin
     * @param count the number of bytes to be transferred
     * @return the number of bytes (possibly 0) that were actually transferred
     * @throws IOException if an I/O error occurs
     */
    long transferFrom(FileChannel src, long position, long count) throws IOException;

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends StreamSinkChannel> getWriteSetter();

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends StreamSinkChannel> getCloseSetter();
}
