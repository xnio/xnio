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
import java.util.concurrent.TimeUnit;
import org.xnio.Buffers;
import org.xnio.Pooled;

/**
 * A message source conduit which implements a simple message framing protocol over a stream conduit.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class FramingMessageSourceConduit extends AbstractSourceConduit<StreamSourceConduit> implements MessageSourceConduit {
    private final Pooled<ByteBuffer> receiveBuffer;
    private boolean ready;

    /**
     * Construct a new instance.
     *
     * @param next the delegate conduit to set
     * @param receiveBuffer the transmit buffer to use
     */
    public FramingMessageSourceConduit(final StreamSourceConduit next, final Pooled<ByteBuffer> receiveBuffer) {
        super(next);
        this.receiveBuffer = receiveBuffer;
    }

    public void resumeReads() {
        if (ready) next.wakeupReads(); else next.resumeReads();
    }

    public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
        if (! ready) next.awaitReadable(time, timeUnit);
    }

    public void awaitReadable() throws IOException {
        if (! ready) next.awaitReadable();
    }

    public void terminateReads() throws IOException {
        receiveBuffer.free();
        next.terminateReads();
    }

    public int receive(final ByteBuffer dst) throws IOException {
        final ByteBuffer receiveBuffer = this.receiveBuffer.getResource();
        int res;
        do {
            res = next.read(receiveBuffer);
        } while (res > 0);
        if (receiveBuffer.position() < 4) {
            if (res == -1) {
                receiveBuffer.clear();
            }
            ready = false;
            return res;
        }
        receiveBuffer.flip();
        try {
            final int length = receiveBuffer.getInt();
            if (length < 0 || length > receiveBuffer.capacity() - 4) {
                Buffers.unget(receiveBuffer, 4);
                throw msg.recvInvalidMsgLength(length);
            }
            if (receiveBuffer.remaining() < length) {
                if (res == -1) {
                    receiveBuffer.clear();
                } else {
                    Buffers.unget(receiveBuffer, 4);
                }
                ready = false;
                // must be <= 0
                return res;
            }
            if (dst.hasRemaining()) {
                return Buffers.copy(length, dst, receiveBuffer);
            } else {
                Buffers.skip(receiveBuffer, length);
                return 0;
            }
        } finally {
            if (res != -1) {
                receiveBuffer.compact();
                if (receiveBuffer.position() >= 4 && receiveBuffer.position() >= 4 + receiveBuffer.getInt(0)) {
                    // there's another packet ready to go
                    ready = true;
                }
            }
        }
    }

    public long receive(final ByteBuffer[] dsts, final int offs, final int len) throws IOException {
        final ByteBuffer receiveBuffer = this.receiveBuffer.getResource();
        int res;
        do {
            res = next.read(receiveBuffer);
        } while (res > 0);
        if (receiveBuffer.position() < 4) {
            if (res == -1) {
                receiveBuffer.clear();
            }
            ready = false;
            return res;
        }
        receiveBuffer.flip();
        try {
            final int length = receiveBuffer.getInt();
            if (length < 0 || length > receiveBuffer.capacity() - 4) {
                Buffers.unget(receiveBuffer, 4);
                throw msg.recvInvalidMsgLength(length);
            }
            if (receiveBuffer.remaining() < length) {
                if (res == -1) {
                    receiveBuffer.clear();
                } else {
                    Buffers.unget(receiveBuffer, 4);
                }
                ready = false;
                // must be <= 0
                return res;
            }
            if (Buffers.hasRemaining(dsts, offs, len)) {
                return Buffers.copy(length, dsts, offs, len, receiveBuffer);
            } else {
                Buffers.skip(receiveBuffer, length);
                return 0;
            }
        } finally {
            if (res != -1) {
                receiveBuffer.compact();
                if (receiveBuffer.position() >= 4 && receiveBuffer.position() >= 4 + receiveBuffer.getInt(0)) {
                    // there's another packet ready to go
                    ready = true;
                }
            }
        }
    }
}
