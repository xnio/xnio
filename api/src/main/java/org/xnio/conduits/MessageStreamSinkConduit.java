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
import org.xnio.Buffers;
import org.xnio.channels.StreamSourceChannel;

/**
 * A stream sink conduit which wraps each write into a single message.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class MessageStreamSinkConduit extends AbstractSinkConduit<MessageSinkConduit> implements StreamSinkConduit {

    /**
     * Construct a new instance.
     *
     * @param next the delegate conduit to set
     */
    public MessageStreamSinkConduit(final MessageSinkConduit next) {
        super(next);
    }

    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        return src.transferTo(position, count, new ConduitWritableByteChannel(this));
    }

    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        return Conduits.transfer(source, count, throughBuffer, this);
    }

    public int write(final ByteBuffer src) throws IOException {
        final int remaining = src.remaining();
        return next.send(src) ? remaining : 0;
    }

    public long write(final ByteBuffer[] srcs, final int offs, final int len) throws IOException {
        final long remaining = Buffers.remaining(srcs, offs, len);
        return next.send(srcs, offs, len) ? remaining : 0L;
    }

    @Override
    public int writeFinal(ByteBuffer src) throws IOException {
        return Conduits.writeFinalBasic(this, src);
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs, int offset, int length) throws IOException {
        return Conduits.writeFinalBasic(this, srcs, offset, length);
    }
}
