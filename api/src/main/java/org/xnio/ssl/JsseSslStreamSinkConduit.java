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
        return write(src, false);
    }

    @Override
    public long write(ByteBuffer[] srcs, int offs, int len) throws IOException {
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

    private int write(ByteBuffer src, boolean writeFinal) throws IOException {
        if (!tls) {
            if(writeFinal) {
                return next.writeFinal(src);
            } else {
                return next.write(src);
            }
        }
        final int wrappedBytes = sslEngine.wrap(src);
        if (wrappedBytes > 0) {
                writeWrappedBuffer(writeFinal);
        }
        return wrappedBytes;
    }

    private long write(ByteBuffer[] srcs, int offs, int len, boolean writeFinal) throws IOException {
        if (!tls) {
            if(writeFinal) {
                return super.writeFinal(srcs, offs, len);
            } else {
                return super.write(srcs, offs, len);
            }
        }
        final long wrappedBytes = sslEngine.wrap(srcs, offs, len);
        if (wrappedBytes > 0) {
            writeWrappedBuffer(writeFinal);
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
        try {
            sslEngine.closeOutbound();
            flush();
        } catch (IOException e) {
            try {
                super.truncateWrites();
            } catch (IOException e2) {
                e2.addSuppressed(e);
                throw e2;
            }
            throw e;
        }
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
        if (tls) try {
            sslEngine.closeOutbound();
        } finally {
            try {
                super.truncateWrites();
            } catch (IOException ignored) {
            }
        }
        super.truncateWrites();
    }

    @Override
    public boolean flush() throws IOException {
        if (!tls) {
            return super.flush();
        }
        if (sslEngine.isOutboundClosed()) {
            if (sslEngine.flush() && writeWrappedBuffer(false) && super.flush()) {
                super.terminateWrites();
                return true;
            } else {
                return false;
            }
        }
        return sslEngine.flush() && writeWrappedBuffer(false) && super.flush();
    }

    private boolean writeWrappedBuffer(boolean writeFinal) throws IOException {
        synchronized (sslEngine.getWrapLock()) {
            final ByteBuffer wrapBuffer = sslEngine.getWrappedBuffer();
            for (;;) {
                try {
                    if (!wrapBuffer.flip().hasRemaining()) {
                        if (writeFinal) {
                            terminateWrites();
                        }
                        return true;
                    }
                    if(writeFinal) {
                        if (super.writeFinal(wrapBuffer) == 0) {
                            return false;
                        }
                    } else {
                        if (super.write(wrapBuffer) == 0) {
                            return false;
                        }
                    }
                } finally {
                    wrapBuffer.compact();
                }
            }
        }
    }

}
