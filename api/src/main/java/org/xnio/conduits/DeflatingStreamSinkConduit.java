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
import java.util.zip.Deflater;
import org.xnio.Buffers;
import org.xnio.channels.StreamSourceChannel;

/**
 * A filtering stream sink conduit which compresses the written data.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class DeflatingStreamSinkConduit extends AbstractStreamSinkConduit<StreamSinkConduit> implements StreamSinkConduit {

    private static final byte[] NO_BYTES = new byte[0];
    private final Deflater deflater;
    private final ByteBuffer outBuffer;

    /**
     * Construct a new instance.
     *
     * @param next the delegate conduit to set
     * @param deflater the initialized deflater to use
     */
    public DeflatingStreamSinkConduit(final StreamSinkConduit next, final Deflater deflater) {
        super(next);
        this.deflater = deflater;
        outBuffer = ByteBuffer.allocate(16384);
    }

    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        return src.transferTo(position, count, new ConduitWritableByteChannel(this));
    }

    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        return Conduits.transfer(source, count, throughBuffer, this);
    }

    public int write(final ByteBuffer src) throws IOException {
        final ByteBuffer outBuffer = this.outBuffer;
        final byte[] outArray = outBuffer.array();
        final Deflater deflater = this.deflater;
        assert outBuffer.arrayOffset() == 0;
        int cnt = 0;
        int rem;
        int c1, t;
        int pos;
        while ((rem = src.remaining()) > 0) {
            if (! outBuffer.hasRemaining()) {
                outBuffer.flip();
                try {
                    if (next.write(outBuffer) == 0) {
                        return cnt;
                    }
                } finally {
                    outBuffer.compact();
                }
            }
            pos = src.position();
            if (src.hasArray()) {
                final byte[] array = src.array();
                final int arrayOffset = src.arrayOffset();
                deflater.setInput(array, arrayOffset + pos, rem);
                c1 = deflater.getTotalIn();
                final int dc = deflater.deflate(outArray, outBuffer.position(), outBuffer.remaining());
                outBuffer.position(outBuffer.position() + dc);
                t = deflater.getTotalIn() - c1;
                src.position(pos + t);
                cnt += t;
            } else {
                final byte[] bytes = Buffers.take(src);
                deflater.setInput(bytes);
                c1 = deflater.getTotalIn();
                final int dc = deflater.deflate(outArray, outBuffer.position(), outBuffer.remaining());
                outBuffer.position(outBuffer.position() + dc);
                t = deflater.getTotalIn() - c1;
                src.position(pos + t);
                cnt += t;
            }
        }
        return cnt;
    }

    public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        final ByteBuffer outBuffer = this.outBuffer;
        final byte[] outArray = outBuffer.array();
        final Deflater deflater = this.deflater;
        assert outBuffer.arrayOffset() == 0;
        long cnt = 0;
        int rem;
        int c1, t;
        int pos;
        for (int i = 0; i < length; i ++) {
            final ByteBuffer src = srcs[i + offset];
            while ((rem = src.remaining()) > 0) {
                if (! outBuffer.hasRemaining()) {
                    outBuffer.flip();
                    try {
                        if (next.write(outBuffer) == 0) {
                            return cnt;
                        }
                    } finally {
                        outBuffer.compact();
                    }
                }
                pos = src.position();
                if (src.hasArray()) {
                    final byte[] array = src.array();
                    final int arrayOffset = src.arrayOffset();
                    deflater.setInput(array, arrayOffset + pos, rem);
                    c1 = deflater.getTotalIn();
                    final int dc = deflater.deflate(outArray, outBuffer.position(), outBuffer.remaining());
                    outBuffer.position(outBuffer.position() + dc);
                    t = deflater.getTotalIn() - c1;
                    src.position(pos + t);
                    cnt += t;
                } else {
                    final byte[] bytes = Buffers.take(src);
                    deflater.setInput(bytes);
                    c1 = deflater.getTotalIn();
                    final int dc = deflater.deflate(outArray, outBuffer.position(), outBuffer.remaining());
                    outBuffer.position(outBuffer.position() + dc);
                    t = deflater.getTotalIn() - c1;
                    src.position(pos + t);
                    cnt += t;
                }
            }
        }
        return cnt;
    }

    public boolean flush() throws IOException {
        final ByteBuffer outBuffer = this.outBuffer;
        final byte[] outArray = outBuffer.array();
        final Deflater deflater = this.deflater;
        assert outBuffer.arrayOffset() == 0;
        int res;
        int rem;
        int pos;
        deflater.setInput(NO_BYTES);
        for (;;) {
            rem = outBuffer.remaining();
            pos = outBuffer.position();
            res = deflater.deflate(outArray, pos, rem, Deflater.SYNC_FLUSH);
            if (pos == 0 && res == rem) {
                // hmm, we're stuck (shouldn't be possible)
                throw msg.flushSmallBuffer();
            } else {
                if (res > 0) {
                    outBuffer.flip();
                    try {
                        if (next.write(outBuffer) == 0) {
                            return false;
                        }
                    } finally {
                        outBuffer.compact();
                    }
                } else if (deflater.needsInput() && pos == 0) {
                    if (deflater.finished()) {
                        // idempotent
                        next.terminateWrites();
                    }
                    return next.flush();
                } else {
                    throw msg.deflaterState();
                }
            }
        }
    }

    @Override
    public int writeFinal(ByteBuffer src) throws IOException {
        //todo: non-naive implementations of this
        return Conduits.writeFinalBasic(this, src);
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs, int offset, int length) throws IOException {
        //todo: non-naive implementations of this
        return Conduits.writeFinalBasic(this, srcs, offset, length);
    }

    public void terminateWrites() throws IOException {
        deflater.finish();
    }

    public void truncateWrites() throws IOException {
        deflater.finish();
        next.truncateWrites();
    }
}
