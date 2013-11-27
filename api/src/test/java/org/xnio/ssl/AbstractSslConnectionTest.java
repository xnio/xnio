/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2013 Red Hat, Inc. and/or its affiliates, and individual
 * contributors as indicated by the @author tags.
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

import java.io.IOError;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.junit.Before;
import org.xnio.AssertReadWrite;
import org.xnio.BufferAllocator;
import org.xnio.ByteBufferSlicePool;
import org.xnio.Pool;
import org.xnio.conduits.StreamSinkConduit;
import org.xnio.conduits.StreamSourceConduit;
import org.xnio.mock.ConduitMock;
import org.xnio.mock.StreamConnectionMock;

/**
 * Abstract test for the SSL connection and its conduits.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 */
public abstract class AbstractSslConnectionTest extends AbstractSslTest {
    // the conduits to be tested
    protected ConduitMock conduitMock;
    protected SslConnection connection;
    protected StreamSinkConduit sinkConduit;
    protected StreamSourceConduit sourceConduit;

    @Override @Before
    public void createChannelMock() throws IOException {
        super.createChannelMock();
        conduitMock = new ConduitMock();
        connection = createSslConnection();
        this.sinkConduit = connection.getSinkChannel().getConduit();
        this.sourceConduit = connection.getSourceChannel().getConduit();
    }

    protected SslConnection createSslConnection() {
        final Pool<ByteBuffer> socketBufferPool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 17000, 17000 * 16);
        final Pool<ByteBuffer> applicationBufferPool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 17000, 17000 * 16);
        final StreamConnectionMock connectionMock = new StreamConnectionMock(conduitMock);
        final SslConnection connection = new JsseSslConnection(connectionMock, engineMock, socketBufferPool, applicationBufferPool);
        try {
            connection.startHandshake();
        } catch (IOException e) {
            throw new IOError(e);
        }
        return connection;
    }

    @Override
    protected final void assertWrittenMessage(String... message) {
       AssertReadWrite.assertWrittenMessage(conduitMock, message);
    }

    protected final void assertWrittenMessage(String[]... interwovenMessages) {
        super.tempAssertWrittenMessage(conduitMock.getWrittenText(), interwovenMessages);
    }
}
