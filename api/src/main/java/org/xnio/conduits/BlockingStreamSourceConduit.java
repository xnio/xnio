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
 * A stream source conduit which can switch into and out of blocking mode.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class BlockingStreamSourceConduit extends AbstractStreamSourceConduit<StreamSourceConduit> {
    private boolean resumed;

    /**
     * Construct a new instance.
     *
     * @param next the delegate conduit to set
     */
    public BlockingStreamSourceConduit(final StreamSourceConduit next) {
        super(next);
    }

    public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        if (resumed) return next.transferTo(position, count, target);
        long res;
        while ((res = next.transferTo(position, count, target)) == 0L) {
            next.awaitReadable();
        }
        return res;
    }

    public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
        if (resumed) return next.transferTo(count, throughBuffer, target);
        long res;
        while ((res = next.transferTo(count, throughBuffer, target)) == 0L && ! throughBuffer.hasRemaining()) {
            next.awaitReadable();
        }
        return res;
    }

    public int read(final ByteBuffer dst) throws IOException {
        if (resumed) return next.read(dst);
        int res;
        while ((res = next.read(dst)) == 0) {
            next.awaitReadable();
        }
        return res;
    }

    public long read(final ByteBuffer[] dsts, final int offs, final int len) throws IOException {
        if (resumed) return next.read(dsts, offs, len);
        long res;
        while ((res = next.read(dsts, offs, len)) == 0L) {
            next.awaitReadable();
        }
        return res;
    }

    public void resumeReads() {
        resumed = true;
        next.resumeReads();
    }

    public void wakeupReads() {
        resumed = true;
        next.wakeupReads();
    }

    public void suspendReads() {
        resumed = false;
        next.suspendReads();
    }

    public boolean isReadResumed() {
        return resumed;
    }
}
