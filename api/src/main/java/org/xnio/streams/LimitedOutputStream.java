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
