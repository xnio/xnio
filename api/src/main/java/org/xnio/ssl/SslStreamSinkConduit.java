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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLEngine;

import org.xnio.conduits.AbstractStreamSinkConduit;
import org.xnio.conduits.StreamSinkConduit;

/**
 * An SSL source conduit implementation based on {@link SSLEngine}.
 * 
 * @author <a href="mailto:flavia.rainone@redhat.com">Flavia Rainone</a>
 *
 */
public class SslStreamSinkConduit extends AbstractStreamSinkConduit<StreamSinkConduit> {

    private final SslConduitEngine sslEngine;

    protected SslStreamSinkConduit(StreamSinkConduit next, SslConduitEngine sslEngine) {
        super(next);
        this.sslEngine = sslEngine;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        final ByteBuffer wrapBuffer = sslEngine.getWrappedBuffer();
        while(sslEngine.wrap(src)) {
            // there's some data in there, so send it first
            wrapBuffer.flip();
            try {
                while (wrapBuffer.hasRemaining()) {
                    final int res = super.write(wrapBuffer);
                    if (res == 0) {
                        break;
                    }
                }
            } finally {
                wrapBuffer.compact();
            }
        }
        return (int) sslEngine.resetBytesConsumed();
    }

    @Override
    public long write(ByteBuffer[] srcs, int offs, int len) throws IOException {
        final ByteBuffer wrapBuffer = sslEngine.getWrappedBuffer();
        while(sslEngine.wrap(srcs, offs, len)) {
            // there's some data in there, so send it first
            wrapBuffer.flip();
            try {
                while (wrapBuffer.hasRemaining()) {
                    final int res = super.write(wrapBuffer);
                    if (res == 0) {
                        break;
                    }
                }
            } finally {
                wrapBuffer.compact();
            }
        }
        return (int) sslEngine.resetBytesConsumed();
    }

    @Override
    public void terminateWrites() throws IOException {
        sslEngine.closeOutbound();
        super.terminateWrites();
    }

    @Override
    public void awaitWritable() throws IOException {
        sslEngine.awaitWritable();
    }

    @Override
    public void awaitWritable(long time, TimeUnit timeUnit) throws IOException {
        sslEngine.awaitWritable(time, timeUnit);
    }

    @Override
    public void truncateWrites() throws IOException {
        sslEngine.closeOutbound();
        super.truncateWrites();
    }

    @Override
    public boolean flush() throws IOException {
        return sslEngine.flush();
    }

}
