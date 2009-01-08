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

import java.io.OutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;

/**
 * An output stream which writes to a stream sink channel.  All write operations are directly
 * performed upon the channel, so for optimal performance, a buffering output stream should be
 * used to wrap this class.
 *
 * @apiviz.exclude
 * 
 * @since 1.2
 */
public class ChannelOutputStream extends OutputStream {
    protected volatile boolean closed;
    protected final StreamSinkChannel channel;

    /**
     * Construct a new instance.
     *
     * @param channel the channel to wrap
     */
    public ChannelOutputStream(StreamSinkChannel channel) {
        this.channel = channel;
    }

    private static IOException closed() {
        return new IOException("The output stream is closed");
    }

    private static InterruptedIOException interrupted(int cnt) {
        final InterruptedIOException ex = new InterruptedIOException();
        ex.bytesTransferred = cnt;
        return ex;
    }

    /** {@inheritDoc} */
    public void write(final int b) throws IOException {
        if (closed) throw closed();
        final ByteBuffer buffer = ByteBuffer.wrap(new byte[] { (byte) b });
        while (channel.write(buffer) == 0) {
            channel.awaitWritable();
            if (Thread.interrupted()) {
                throw interrupted(0);
            }
            if (closed) throw closed();
        }
    }

    /** {@inheritDoc} */
    public void write(final byte[] b) throws IOException {
        if (closed) throw closed();
        final ByteBuffer buffer = ByteBuffer.wrap(b);
        while (buffer.hasRemaining()) {
            while (channel.write(buffer) == 0) {
                channel.awaitWritable();
                if (Thread.interrupted()) {
                    throw interrupted(buffer.position());
                }
                if (closed) throw closed();
            }
        }
    }

    /** {@inheritDoc} */
    public void write(final byte[] b, final int off, final int len) throws IOException {
        if (closed) throw closed();
        final ByteBuffer buffer = ByteBuffer.wrap(b, off, len);
        while (buffer.hasRemaining()) {
            while (channel.write(buffer) == 0) {
                channel.awaitWritable();
                if (Thread.interrupted()) {
                    throw interrupted(buffer.position());
                }
                if (closed) throw closed();
            }
        }
    }

    /** {@inheritDoc} */
    public void close() throws IOException {
        closed = true;
        super.close();
    }
}
