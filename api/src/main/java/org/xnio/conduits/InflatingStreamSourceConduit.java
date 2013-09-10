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

import static org.xnio._private.Messages.msg;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import org.xnio.channels.StreamSinkChannel;

/**
 * A filtering stream source conduit which decompresses the source data.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class InflatingStreamSourceConduit extends AbstractStreamSourceConduit<StreamSourceConduit> implements StreamSourceConduit {

    private final Inflater inflater;
    private final ByteBuffer buffer;

    /**
     * Construct a new instance.
     *
     * @param next the underlying conduit for this channel
     * @param inflater the initialized inflater to use
     */
    public InflatingStreamSourceConduit(final StreamSourceConduit next, final Inflater inflater) {
        super(next);
        this.inflater = inflater;
        buffer = ByteBuffer.allocate(16384);
    }

    public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        return target.transferFrom(new ConduitReadableByteChannel(this), position, count);
    }

    public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
        return Conduits.transfer(this, count, throughBuffer, target);
    }

    public int read(final ByteBuffer dst) throws IOException {
        final int remaining = dst.remaining();
        final int position = dst.position();
        final Inflater inflater = this.inflater;
        int res;
        if (dst.hasArray()) {
            // fast path
            final byte[] array = dst.array();
            final int off = dst.arrayOffset();
            for (;;) {
                try {
                    res = inflater.inflate(array, off + position, remaining);
                } catch (DataFormatException e) {
                    throw new IOException(e);
                }
                if (res > 0) {
                    dst.position(position + res);
                    return res;
                }
                if (inflater.needsDictionary()) {
                    throw msg.inflaterNeedsDictionary();
                }
                final ByteBuffer buffer = this.buffer;
                buffer.clear();
                res = next.read(buffer);
                if (res > 0) {
                    inflater.setInput(buffer.array(), buffer.arrayOffset(), res);
                } else {
                    return res;
                }
            }
        } else {
            final byte[] space = new byte[remaining];
            for (;;) {
                try {
                    res = inflater.inflate(space);
                } catch (DataFormatException e) {
                    throw new IOException(e);
                }
                if (res > 0) {
                    dst.put(space, 0, res);
                    return res;
                }
                if (inflater.needsDictionary()) {
                    throw msg.inflaterNeedsDictionary();
                }
                final ByteBuffer buffer = this.buffer;
                buffer.clear();
                res = next.read(buffer);
                if (res > 0) {
                    inflater.setInput(buffer.array(), buffer.arrayOffset(), res);
                } else {
                    return res;
                }
            }
        }
    }

    public long read(final ByteBuffer[] dsts) throws IOException {
        return read(dsts, 0, dsts.length);
    }

    public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        for (int i = 0; i < length; i ++) {
            final ByteBuffer buffer = dsts[i + offset];
            if (buffer.hasRemaining()) {
                return read(buffer);
            }
        }
        return 0L;
    }

    public void terminateReads() throws IOException {
        inflater.end();
        next.terminateReads();
    }

    public void awaitReadable() throws IOException {
        if (! inflater.needsInput()) {
            return;
        }
        next.awaitReadable();
    }

    public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
        if (! inflater.needsInput()) {
            return;
        }
        next.awaitReadable(time, timeUnit);
    }
}
