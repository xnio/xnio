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
package org.xnio.ssl;

import static org.xnio._private.Messages.msg;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;

import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.AbstractStreamSinkConduit;
import org.xnio.conduits.ConduitWritableByteChannel;
import org.xnio.conduits.Conduits;
import org.xnio.conduits.StreamSinkConduit;

/**
 * Jsse SSL source conduit implementation based on {@link JsseSslConduitEngine}.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 *
 */
final class JsseSslStreamSinkConduit extends AbstractStreamSinkConduit<StreamSinkConduit> {

    private final JsseSslConduitEngine sslEngine;
    private volatile boolean tls;

    protected JsseSslStreamSinkConduit(StreamSinkConduit next, JsseSslConduitEngine sslEngine, boolean tls) {
        super(next);
        if (sslEngine == null) {
            throw msg.nullParameter("sslEngine");
        }
        this.sslEngine = sslEngine;
        this.tls = tls;
    }

    public void enableTls() {
        tls = true;
        if (isWriteResumed()) {
            wakeupWrites();
        }
    }

    @Override
    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        return src.transferTo(position, count, new ConduitWritableByteChannel(this));
    }

    @Override
    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        return Conduits.transfer(source, count, throughBuffer, this);
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        if (!tls) {
            return super.write(src);
        }
        final int wrappedBytes = sslEngine.wrap(src);
        if (wrappedBytes > 0) {
                writeWrappedBuffer();
        }
        return wrappedBytes;
    }

    @Override
    public long write(ByteBuffer[] srcs, int offs, int len) throws IOException {
        if (!tls) {
            return super.write(srcs, offs, len);
        }
        final long wrappedBytes = sslEngine.wrap(srcs, offs, len);
        if (wrappedBytes > 0) {
            writeWrappedBuffer();
        }
        return wrappedBytes;
    }

    @Override
    public void resumeWrites() {
        if (tls && sslEngine.isFirstHandshake()) {
            super.wakeupWrites();
        } else {
            super.resumeWrites();
        }
    }

    @Override
    public void terminateWrites() throws IOException {
        if (!tls) {
            super.terminateWrites();
            return;
        }
        sslEngine.closeOutbound();
        flush();
    }

    @Override
    public void awaitWritable() throws IOException {
        if (tls) {
            sslEngine.awaitCanWrap();
        }
        super.awaitWritable();
    }

    @Override
    public void awaitWritable(long time, TimeUnit timeUnit) throws IOException {
        if (!tls) {
            super.awaitWritable(time, timeUnit);
            return;
        }
        long duration = timeUnit.toNanos(time);
        long awaited = System.nanoTime();
        sslEngine.awaitCanWrap(time, timeUnit);
        awaited = System.nanoTime() - awaited;
        if (awaited > duration) {
            return;
        }
        super.awaitWritable(duration - awaited, TimeUnit.NANOSECONDS);
    }

    @Override
    public void truncateWrites() throws IOException {
        if (tls) {
            sslEngine.closeOutbound();
        }
        super.truncateWrites();
    }

    @Override
    public boolean flush() throws IOException {
        if (!tls) {
            return super.flush();
        }
        if (sslEngine.isOutboundClosed()) {
            if (sslEngine.flush() && writeWrappedBuffer() && super.flush()) {
                super.terminateWrites();
                return true;
            } else {
                return false;
            }
        }
        return sslEngine.flush() && writeWrappedBuffer() && super.flush();
    }

    private boolean writeWrappedBuffer() throws IOException {
        synchronized (sslEngine.getWrapLock()) {
            final ByteBuffer wrapBuffer = sslEngine.getWrappedBuffer();
            for (;;) {
                try {
                    if (!wrapBuffer.flip().hasRemaining()) {
                        return true;
                    }
                    if (super.write(wrapBuffer) == 0) {
                        return false;
                    }
                } finally {
                    wrapBuffer.compact();
                }
            }
        }
    }

}
