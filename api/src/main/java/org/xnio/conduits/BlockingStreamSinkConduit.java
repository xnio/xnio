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

    @Override
    public int write(final ByteBuffer src) throws IOException {
        return write(src, false);
    }

    @Override
    public long write(final ByteBuffer[] srcs, final int offs, final int len) throws IOException {
        return write(srcs, offs, len, false);
    }

    @Override
    public int writeFinal(ByteBuffer src) throws IOException {
        return write(src, true);
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs, int offset, int length) throws IOException {
        return write(srcs, offset, length, true);
    }

    private int write(final ByteBuffer src, final boolean writeFinal) throws IOException {
        if (resumed) {
            return doWrite(src, writeFinal);
        }
        int res;
        while ((res = doWrite(src, writeFinal)) == 0L) {
            next.awaitWritable();
        }
        return res;
    }

    private long write(final ByteBuffer[] srcs, final int offs, final int len, final boolean writeFinal) throws IOException {
        if (resumed) return doWrite(srcs, offs, len, writeFinal);
        long res;
        while ((res = next.write(srcs, offs, len)) == 0L) {
            next.awaitWritable();
        }
        return res;
    }

    private int doWrite(ByteBuffer src, boolean writeFinal) throws IOException {
        if(writeFinal) {
            return next.writeFinal(src);
        }
        return next.write(src);
    }

    private long doWrite(ByteBuffer[] srcs,final int offs, final int len,  boolean writeFinal) throws IOException {
        if(writeFinal) {
            return next.writeFinal(srcs, offs, len);
        }
        return next.write(srcs, offs, len);
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
