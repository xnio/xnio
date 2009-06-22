/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, JBoss Inc., and individual contributors as indicated
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

import java.nio.channels.ScatteringByteChannel;
import java.nio.ByteBuffer;
import java.io.IOException;

/**
 * A blocking wrapper for a {@code StreamSourceChannel}.  Read operations will block until some data may be transferred.
 * Once any amount of data is read, the operation will return.
 */
public class BlockingReadableByteChannel implements ScatteringByteChannel {
    private final StreamSourceChannel delegate;

    /**
     * Construct a new instance.
     *
     * @param delegate the channel to forward I/O operations to
     */
    public BlockingReadableByteChannel(final StreamSourceChannel delegate) {
        this.delegate = delegate;
    }

    /**
     * Perform a blocking, scattering read operation.
     *
     * @param dsts the destination buffers
     * @param offset the offset into the destination buffer array
     * @param length the number of buffers to read into
     * @return the number of bytes actually read (will be greater than zero)
     * @throws IOException if an I/O error occurs
     */
    public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        final StreamSourceChannel delegate = this.delegate;
        long res;
        while ((res = delegate.read(dsts, offset, length)) == 0L) {
            delegate.awaitReadable();
        }
        return res;
    }

    /**
     * Perform a blocking, scattering read operation.
     *
     * @param dsts the destination buffers
     * @return the number of bytes actually read (will be greater than zero)
     * @throws IOException if an I/O error occurs
     */
    public long read(final ByteBuffer[] dsts) throws IOException {
        final StreamSourceChannel delegate = this.delegate;
        long res;
        while ((res = delegate.read(dsts)) == 0L) {
            delegate.awaitReadable();
        }
        return res;
    }

    /**
     * Perform a blocking read operation.
     *
     * @param dst the destination buffer
     * @return the number of bytes actually read (will be greater than zero)
     * @throws IOException if an I/O error occurs
     */
    public int read(final ByteBuffer dst) throws IOException {
        final StreamSourceChannel delegate = this.delegate;
        int res;
        while ((res = delegate.read(dst)) == 0) {
            delegate.awaitReadable();
        }
        return res;
    }

    /** {@inheritDoc} */
    public boolean isOpen() {
        return delegate.isOpen();
    }

    /** {@inheritDoc} */
    public void close() throws IOException {
        delegate.close();
    }
}
