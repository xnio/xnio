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
 * A synchronized stream source conduit.  All conduit operations are wrapped in synchronization blocks for simplified
 * thread safety.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class SynchronizedStreamSourceConduit extends AbstractSynchronizedSourceConduit<StreamSourceConduit> implements StreamSourceConduit {

    /**
     * Construct a new instance.  A new lock object is created.
     *
     * @param next the next conduit in the chain
     */
    public SynchronizedStreamSourceConduit(final StreamSourceConduit next) {
        super(next);
    }

    /**
     * Construct a new instance.
     *
     * @param next the next conduit in the chain
     * @param lock the lock object to use
     */
    public SynchronizedStreamSourceConduit(final StreamSourceConduit next, final Object lock) {
        super(next, lock);
    }

    public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        synchronized (lock) {
            return next.transferTo(position, count, target);
        }
    }

    public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
        synchronized (lock) {
            return next.transferTo(count, throughBuffer, target);
        }
    }

    public int read(final ByteBuffer dst) throws IOException {
        synchronized (lock) {
            return next.read(dst);
        }
    }

    public long read(final ByteBuffer[] dsts, final int offs, final int len) throws IOException {
        synchronized (lock) {
            return next.read(dsts, offs, len);
        }
    }
}
