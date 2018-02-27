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

import org.xnio.channels.StreamSinkChannel;
import org.xnio.conduits.AbstractStreamSourceConduit;
import org.xnio.conduits.ConduitReadableByteChannel;
import org.xnio.conduits.Conduits;
import org.xnio.conduits.StreamSourceConduit;

/**
 * Jsse SSL source conduit implementation based on {@link JsseSslConduitEngine}.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 *
 */
final class JsseSslStreamSourceConduit extends AbstractStreamSourceConduit<StreamSourceConduit> {

    private final JsseSslConduitEngine sslEngine;
    private volatile boolean tls;

    protected JsseSslStreamSourceConduit(StreamSourceConduit next, JsseSslConduitEngine sslEngine, boolean tls) {
        super(next);
        if (sslEngine == null) {
            throw msg.nullParameter("sslEngine");
        }
        this.sslEngine = sslEngine;
        this.tls = tls;
    }

    void enableTls() {
        tls = true;
        if (isReadResumed()) {
            wakeupReads();
        }
    }

    @Override
    public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        return target.transferFrom(new ConduitReadableByteChannel(this), position, count);
    }

    @Override
    public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
        return Conduits.transfer(this, count, throughBuffer, target);
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        if (!tls) {
            final int res = super.read(dst);
            if (res == -1) {
                terminateReads();
            }
            return res;
        }
        if ((!sslEngine.isDataAvailable() && sslEngine.isInboundClosed()) || sslEngine.isClosed()) {
            return -1;
        }
        final int readResult;
        final int unwrapResult;
        synchronized(sslEngine.getUnwrapLock()) {
            final ByteBuffer unwrapBuffer = sslEngine.getUnwrapBuffer().compact();
            try {
                readResult = super.read(unwrapBuffer);
            } finally {
                unwrapBuffer.flip();
            }
        }
        unwrapResult = sslEngine.unwrap(dst);
        if (unwrapResult == 0 && readResult == -1) {
            terminateReads();
            return -1;
        }
        return unwrapResult;
    }

    @Override
    public long read(ByteBuffer[] dsts, int offs, int len) throws IOException {
        if (!tls) {
            final long res = super.read(dsts, offs, len);
            if (res == -1) {
                terminateReads();
            }
            return res;
        }
        if (offs < 0 || offs > len || len < 0 || offs + len > dsts.length) {
            throw new ArrayIndexOutOfBoundsException();
        }
        if ((!sslEngine.isDataAvailable() && sslEngine.isInboundClosed()) || sslEngine.isClosed()) {
            return -1;
        }
        final int readResult;
        final long unwrapResult;
        synchronized (sslEngine.getUnwrapLock()) {
            // retrieve buffer from sslEngine, to save some memory space
            final ByteBuffer unwrapBuffer = sslEngine.getUnwrapBuffer().compact();
            try {
                readResult = super.read(unwrapBuffer);
            } finally {
                unwrapBuffer.flip();
            }
        }
        unwrapResult = sslEngine.unwrap(dsts, offs, len);
        if (unwrapResult == 0 && readResult == -1) {
            terminateReads();
            return -1;
        }
        return unwrapResult;
    }

    @Override
    public void resumeReads() {
        if (tls && sslEngine.isFirstHandshake()) {
            super.wakeupReads();
        } else {
            super.resumeReads();
        }
    }

    @Override
    public void terminateReads() throws IOException {
        if (tls) {
            try {
                sslEngine.closeInbound();
            } catch (IOException ex) {
                try {
                    super.terminateReads();
                } catch (IOException e2) {
                    e2.addSuppressed(ex);
                    throw e2;
                }
                throw ex;
            }
        }
        super.terminateReads();
    }

    @Override
    public void awaitReadable() throws IOException {
        if (tls) {
            sslEngine.awaitCanUnwrap();
        }
        if(sslEngine.isDataAvailable()) {
            return;
        }
        super.awaitReadable();
    }

    @Override
    public void awaitReadable(long time, TimeUnit timeUnit) throws IOException {
        if (!tls) {
            super.awaitReadable(time, timeUnit);
            return;
        }
        synchronized (sslEngine.getUnwrapLock()) {
            if(sslEngine.getUnwrapBuffer().hasRemaining()) {
                return;
            }
        }
        long duration = timeUnit.toNanos(time);
        long awaited = System.nanoTime();
        sslEngine.awaitCanUnwrap(time, timeUnit);
        awaited = System.nanoTime() - awaited;
        if (awaited > duration) {
            return;
        }
        super.awaitReadable(duration - awaited, TimeUnit.NANOSECONDS);
    }
}
