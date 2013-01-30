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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import org.xnio.channels.StreamSinkChannel;

/**
 * A stream source conduit which limits the length of input.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class FixedLengthStreamSourceConduit extends AbstractStreamSourceConduit<StreamSourceConduit> implements StreamSourceConduit {
    private long remaining;

    /**
     * Construct a new instance.
     *
     * @param next the conduit to limit
     * @param remaining the number of bytes to limit to
     */
    public FixedLengthStreamSourceConduit(final StreamSourceConduit next, final long remaining) {
        super(next);
        this.remaining = remaining;
    }

    public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        long length = this.remaining;
        if (length > 0) {
            final long res = next.transferTo(position, Math.min(count, length), target);
            if (res > 0L) {
                this.remaining = length - res;
            }
            return res;
        } else {
            return 0;
        }
    }

    public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
        long length = this.remaining;
        if (length > 0) {
            final long res = next.transferTo(Math.min(count, length), throughBuffer, target);
            if (res > 0L) {
                this.remaining = length - res;
            }
            return res;
        } else {
            return -1L;
        }
    }

    public int read(final ByteBuffer dst) throws IOException {
        final int limit = dst.limit();
        final int pos = dst.position();
        final int res;
        final long length = this.remaining;
        if (length == 0L) {
            return -1;
        }
        if (limit - pos > length) {
            dst.limit(pos + (int) length);
            try {
                res = next.read(dst);
            } finally {
                dst.limit(limit);
            }
        } else {
            res = next.read(dst);
        }
        if (res > 0L) {
            this.remaining = length - res;
        }
        return res;
    }

    public long read(final ByteBuffer[] dsts, final int offs, final int len) throws IOException {
        if (len == 0) {
            return 0L;
        } else if (len == 1) {
            return read(dsts[offs]);
        }
        final long length = this.remaining;
        if (length == 0L) {
            return -1L;
        }
        long res;
        int lim;
        // The total amount of buffer space discovered so far.
        long t = 0L;
        for (int i = 0; i < length; i ++) {
            final ByteBuffer buffer = dsts[i + offs];
            // Grow the discovered buffer space by the remaining size of the current buffer.
            // We want to capture the limit so we calculate "remaining" ourselves.
            t += (lim = buffer.limit()) - buffer.position();
            if (t > length) {
                // only read up to this point, and trim the last buffer by the number of extra bytes
                buffer.limit(lim - (int) (t - length));
                try {
                    res = next.read(dsts, offs, i + 1);
                    if (res > 0L) {
                        this.remaining = length - res;
                    }
                    return res;
                } finally {
                    // restore the original limit
                    buffer.limit(lim);
                }
            }
        }
        // the total buffer space is less than the remaining count.
        res = t == 0L ? 0L : next.read(dsts, offs, len);
        if (res > 0L) {
            this.remaining = length - res;
        }
        return res;
    }

    /**
     * Get the number of bytes which remain available to read.
     *
     * @return the number of bytes which remain available to read
     */
    public long getRemaining() {
        return remaining;
    }
}
