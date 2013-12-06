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
import org.xnio.Buffers;
import org.xnio.Pooled;

/**
 * A message sink conduit which implements a simple message framing protocol over a stream conduit.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class FramingMessageSinkConduit extends AbstractSinkConduit<StreamSinkConduit> implements MessageSinkConduit {

    private final boolean longLengths;
    private final Pooled<ByteBuffer> transmitBuffer;

    /**
     * Construct a new instance.
     *
     * @param next the delegate conduit to set
     * @param longLengths {@code true} to use 4-byte lengths, {@code false} to use 2-byte lengths
     * @param transmitBuffer the transmit buffer to use
     */
    public FramingMessageSinkConduit(final StreamSinkConduit next, final boolean longLengths, final Pooled<ByteBuffer> transmitBuffer) {
        super(next);
        this.longLengths = longLengths;
        this.transmitBuffer = transmitBuffer;
    }

    public boolean send(final ByteBuffer src) throws IOException {
        if (!src.hasRemaining()) {
            // no zero messages
            return false;
        }
        final ByteBuffer transmitBuffer = this.transmitBuffer.getResource();
        final int remaining = src.remaining();
        final boolean longLengths = this.longLengths;
        final int lengthFieldSize = longLengths ? 4 : 2;
        if (remaining > transmitBuffer.capacity() - lengthFieldSize || ! longLengths && remaining > 65535) {
            throw msg.txMsgTooLarge();
        }
        if (transmitBuffer.remaining() < lengthFieldSize + remaining && ! writeBuffer()) {
            return false;
        }
        if (longLengths) {
            transmitBuffer.putInt(remaining);
        } else {
            transmitBuffer.putShort((short) remaining);
        }
        transmitBuffer.put(src);
        writeBuffer();
        return true;
    }

    public boolean send(final ByteBuffer[] srcs, final int offs, final int len) throws IOException {
        if (len == 1) {
            return send(srcs[offs]);
        } else if (! Buffers.hasRemaining(srcs, offs, len)) {
            return false;
        }
        final ByteBuffer transmitBuffer = this.transmitBuffer.getResource();
        final long remaining = Buffers.remaining(srcs, offs, len);
        final boolean longLengths = this.longLengths;
        final int lengthFieldSize = longLengths ? 4 : 2;
        if (remaining > transmitBuffer.capacity() - lengthFieldSize || ! longLengths && remaining > 65535) {
            throw msg.txMsgTooLarge();
        }
        if (transmitBuffer.remaining() < lengthFieldSize + remaining && ! writeBuffer()) {
            return false;
        }
        if (longLengths) {
            transmitBuffer.putInt((int) remaining);
        } else {
            transmitBuffer.putShort((short) remaining);
        }
        Buffers.copy(transmitBuffer, srcs, offs, len);
        writeBuffer();
        return true;
    }

    @Override
    public boolean sendFinal(ByteBuffer src) throws IOException {
        //TODO: non-naive implementation
        return Conduits.sendFinalBasic(this, src);
    }

    @Override
    public boolean sendFinal(ByteBuffer[] srcs, int offs, int len) throws IOException {
        return Conduits.sendFinalBasic(this, srcs, offs, len);
    }

    private boolean writeBuffer() throws IOException {
        final ByteBuffer buffer = transmitBuffer.getResource();
        if (buffer.position() > 0) buffer.flip();
        try {
            while (buffer.hasRemaining()) {
                final int res = next.write(buffer);
                if (res == 0) {
                    return false;
                }
            }
            return true;
        } finally {
            buffer.compact();
        }
    }

    public boolean flush() throws IOException {
        return writeBuffer() && next.flush();
    }

    public void terminateWrites() throws IOException {
        transmitBuffer.free();
        next.terminateWrites();
    }

    public void truncateWrites() throws IOException {
        transmitBuffer.free();
        next.truncateWrites();
    }
}
