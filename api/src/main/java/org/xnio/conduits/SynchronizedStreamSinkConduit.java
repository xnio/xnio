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

import org.xnio.channels.StreamSourceChannel;

/**
 * A synchronized stream sink conduit.  All conduit operations are wrapped in synchronization blocks for simplified
 * thread safety.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class SynchronizedStreamSinkConduit extends AbstractSynchronizedSinkConduit<StreamSinkConduit> implements StreamSinkConduit {

    /**
     * Construct a new instance.  A new lock object is created.
     *
     * @param next the next conduit in the chain
     */
    public SynchronizedStreamSinkConduit(final StreamSinkConduit next) {
        super(next);
    }

    /**
     * Construct a new instance.
     *
     * @param next the next conduit in the chain
     * @param lock the lock object to use
     */
    public SynchronizedStreamSinkConduit(final StreamSinkConduit next, final Object lock) {
        super(next, lock);
    }

    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        synchronized (lock) {
            return next.transferFrom(src, position, count);
        }
    }

    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        synchronized (lock) {
            return next.transferFrom(source, count, throughBuffer);
        }
    }

    public int write(final ByteBuffer src) throws IOException {
        synchronized (lock) {
            return next.write(src);
        }
    }

    public long write(final ByteBuffer[] srcs, final int offs, final int len) throws IOException {
        synchronized (lock) {
            return next.write(srcs, offs, len);
        }
    }

    public int writeFinal(final ByteBuffer src) throws IOException {
        synchronized (lock) {
            return next.writeFinal(src);
        }
    }

    public long writeFinal(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        synchronized (lock) {
            return next.writeFinal(srcs, offset, length);
        }
    }
}
