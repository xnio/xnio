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

import org.xnio.conduits.AbstractStreamSourceConduit;
import org.xnio.conduits.StreamSourceConduit;

/**
 * An SSL source conduit implementation based on {@link SSLEngine}.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 *
 */
public class SslStreamSourceConduit extends AbstractStreamSourceConduit<StreamSourceConduit> {

    private final SslConduitEngine sslEngine;

    protected SslStreamSourceConduit(StreamSourceConduit next, SslConduitEngine sslEngine) {
        super(next);
        this.sslEngine = sslEngine;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        // retrieve buffer from sslEngine, to save some memory space
        final ByteBuffer unwrapBuffer = sslEngine.getUnwrapBuffer();
        super.read(unwrapBuffer);
        return sslEngine.unwrap(dst);
    }

    @Override
    public long read(ByteBuffer[] dsts, int offs, int len) throws IOException {
        // retrieve buffer from sslEngine, to save some memory space
        final ByteBuffer unwrapBuffer = sslEngine.getUnwrapBuffer();
        super.read(unwrapBuffer);
        return sslEngine.unwrap(dsts, offs, len);
    }

    @Override
    public void terminateReads() throws IOException {
        sslEngine.closeInbound();
        super.terminateReads();
    }

    @Override
    public void awaitReadable() throws IOException {
        sslEngine.awaitReadable();
    }

    @Override
    public void awaitReadable(long time, TimeUnit timeUnit) throws IOException {
        sslEngine.awaitReadable(time, timeUnit);
    }
}
