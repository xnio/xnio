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

import java.io.InputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;

/**
 * An input stream which reads from a stream source channel.  All read operations are directly
 * performed upon the channel, so for optimal performance, a buffering input stream should be
 * used to wrap this class.
 *
 * @apiviz.exclude
 * 
 * @since 1.2
 */
public class ChannelInputStream extends InputStream {
    protected final StreamSourceChannel channel;
    protected volatile boolean closed;

    /**
     * Construct a new instance.
     *
     * @param channel the channel to wrap
     */
    public ChannelInputStream(final StreamSourceChannel channel) {
        this.channel = channel;
    }

    /** {@inheritDoc} */
    public int read() throws IOException {
        if (closed) return -1;
        final byte[] array = new byte[1];
        final ByteBuffer buffer = ByteBuffer.wrap(array);
        for (;;) {
            final int res = channel.read(buffer);
            if (res == -1) {
                return -1;
            }
            if (res == 1) {
                return array[0] & 0xff;
            }
            channel.awaitReadable();
            if (closed) return -1;
        }
    }

    /** {@inheritDoc} */
    public int read(final byte b[]) throws IOException {
        if (closed) return -1;
        final ByteBuffer buffer = ByteBuffer.wrap(b);
        for (;;) {
            final int res = channel.read(buffer);
            if (res == -1) {
                return -1;
            }
            if (res > 0) {
                return res;
            }
            try {
                channel.awaitReadable();
            } catch (InterruptedIOException e) {
                e.bytesTransferred = buffer.position();
                throw e;
            }
            if (closed) return -1;
        }
    }

    /** {@inheritDoc} */
    public int read(final byte b[], final int off, final int len) throws IOException {
        if (closed) return -1;
        final ByteBuffer buffer = ByteBuffer.wrap(b, off, len);
        for (;;) {
            final int res = channel.read(buffer);
            if (res == -1) {
                return -1;
            }
            if (res > 0) {
                return res;
            }
            try {
                channel.awaitReadable();
            } catch (InterruptedIOException e) {
                e.bytesTransferred = buffer.position();
                throw e;
            }
            if (closed) return -1;
        }
    }

    /** {@inheritDoc} */
    public void close() throws IOException {
        closed = true;
        channel.close();
    }
}
