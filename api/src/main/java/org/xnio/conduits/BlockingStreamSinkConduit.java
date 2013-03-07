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
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class BlockingStreamSinkConduit extends AbstractStreamSinkConduit<StreamSinkConduit> {
    private boolean resumed;

    public BlockingStreamSinkConduit(final StreamSinkConduit next) {
        super(next);
    }

    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        if (resumed) return next.transferFrom(src, position, count);
        long res;
        while ((res = next.transferFrom(src, position, count)) == 0L) {
            next.awaitWritable();
        }
        return res;
    }

    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        if (resumed) return next.transferFrom(source, count, throughBuffer);
        long res;
        while ((res = next.transferFrom(source, count, throughBuffer)) == 0L) {
            next.awaitWritable();
        }
        return res;
    }

    public int write(final ByteBuffer src) throws IOException {
        if (resumed) return next.write(src);
        int res;
        while ((res = next.write(src)) == 0L) {
            next.awaitWritable();
        }
        return res;
    }

    public long write(final ByteBuffer[] srcs, final int offs, final int len) throws IOException {
        if (resumed) return next.write(srcs, offs, len);
        long res;
        while ((res = next.write(srcs, offs, len)) == 0L) {
            next.awaitWritable();
        }
        return res;
    }

    public boolean flush() throws IOException {
        if (resumed) return next.flush();
        while (! next.flush()) {
            next.awaitWritable();
        }
        return true;
    }

    public void resumeWrites() {
        resumed = true;
        next.resumeWrites();
    }

    public void suspendWrites() {
        resumed = false;
        next.suspendWrites();
    }

    public void wakeupWrites() {
        resumed = true;
        next.wakeupWrites();
    }

    public boolean isWriteResumed() {
        return resumed;
    }
}
