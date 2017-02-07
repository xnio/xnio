/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2010 Red Hat, Inc. and/or its affiliates, and individual
 * contributors as indicated by the @author tags.
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

package org.xnio.streams;

import static org.xnio._private.Messages.msg;

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
            throw msg.markNotSet();
        }
        delegate.reset();
        remaining = mark;
    }

    /** {@inheritDoc} */
    public boolean markSupported() {
        return delegate.markSupported();
    }
}
