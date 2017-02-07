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
import java.io.InputStream;

/**
 * An input stream which truncates the underlying stream to the given length.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class LimitedInputStream extends InputStream {
    private final InputStream delegate;
    private long remaining;
    private long mark = -1L;

    /**
     * Construct a new instance.
     *
     * @param delegate the delegate stream
     * @param size the maximum size to pass
     */
    public LimitedInputStream(final InputStream delegate, final long size) {
        this.delegate = delegate;
        remaining = size;
    }

    /** {@inheritDoc} */
    public int read() throws IOException {
        final long remaining = this.remaining;
        if (remaining > 0) {
            final int b = delegate.read();
            if (b >= 0) this.remaining = remaining - 1;
            return b;
        } else {
            return -1;
        }
    }

    /** {@inheritDoc} */
    public int read(final byte[] b, final int off, final int len) throws IOException {
        final long remaining = this.remaining;
        if (remaining == 0L) {
            return -1;
        }
        final int cnt = delegate.read(b, off, (int) Math.min((long)len, remaining));
        if (cnt == -1) {
            return -1;
        }
        this.remaining = remaining - cnt;
        return cnt;
    }

    /** {@inheritDoc} */
    public long skip(final long n) throws IOException {
        final long remaining = this.remaining;
        if (remaining == 0L || n <= 0L) {
            return 0L;
        }
        final long cnt = delegate.skip(Math.min(n, remaining));
        if (cnt > 0L) {
            this.remaining = remaining - cnt;
        }
        return cnt;
    }

    /** {@inheritDoc} */
    public int available() throws IOException {
        return Math.min(delegate.available(), (int) Math.min((long)Integer.MAX_VALUE, remaining));
    }

    /** {@inheritDoc} */
    public void close() throws IOException {
        remaining = 0;
        delegate.close();
    }

    /** {@inheritDoc} */
    public void mark(final int limit) {
        if (markSupported()) {
            delegate.mark(limit);
            mark = remaining;
        }
    }

    /** {@inheritDoc} */
    public void reset() throws IOException {
        final long mark = this.mark;
        if (mark == -1L) {
            throw new IOException("Mark not set");
        }
        delegate.reset();
        remaining = mark;
    }

    /** {@inheritDoc} */
    public boolean markSupported() {
        return delegate.markSupported();
    }
}
