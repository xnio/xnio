/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, JBoss Inc., and individual contributors as indicated
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

package org.xnio.streams;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;

/**
 * An output stream which truncates the writable output to the given length.  Attempting to exceed the
 * fixed limit will result in an exception.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class LimitedOutputStream extends OutputStream {
    private final OutputStream delegate;
    private long remaining;

    /**
     * Construct a new instance.
     *
     * @param delegate the delegate output stream
     * @param size the number of bytes to allow
     */
    public LimitedOutputStream(final OutputStream delegate, final long size) {
        this.delegate = delegate;
        remaining = size;
    }

    /** {@inheritDoc} */
    public void write(final int b) throws IOException {
        final long remaining = this.remaining;
        if (remaining < 1) {
            throw notEnoughSpace();
        }
        delegate.write(b);
        this.remaining = remaining - 1;
    }

    /** {@inheritDoc} */
    public void write(final byte[] b, final int off, final int len) throws IOException {
        final long remaining = this.remaining;
        if (remaining < len) {
            throw notEnoughSpace();
        }
        try {
            delegate.write(b, off, len);
            this.remaining = remaining - len;
        } catch (InterruptedIOException e) {
            this.remaining = remaining - (e.bytesTransferred & 0xFFFFFFFFL);
            throw e;
        }
    }

    /** {@inheritDoc} */
    public void flush() throws IOException {
        delegate.flush();
    }

    /** {@inheritDoc} */
    public void close() throws IOException {
        delegate.close();
    }

    private static IOException notEnoughSpace() {
        return new IOException("Not enough space in output stream");
    }
}
