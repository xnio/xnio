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

import org.junit.After;
import org.junit.Before;
import org.xnio.AssertReadWrite;
import org.xnio.BufferAllocator;
import org.xnio.ByteBufferSlicePool;
import org.xnio.Pool;
import org.xnio.channels.AssembledConnectedSslStreamChannel;
import org.xnio.channels.ConnectedSslStreamChannel;
import org.xnio.mock.ConduitMock;
import org.xnio.mock.StreamConnectionMock;
import org.xnio.mock.XnioIoThreadMock;
import org.xnio.mock.XnioWorkerMock;

/**
 * Abstract test.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 */
public abstract class AbstractConnectedSslStreamChannelTest extends AbstractSslTest {
    // the channel to be tested
    protected ConnectedSslStreamChannel sslChannel;
    // the underlying connection used as a delegate by the test target
    protected StreamConnectionMock connectionMock;
    // the underlying conduit used as a delegate by the test target
    protected ConduitMock conduitMock;
    // the xnio IO thread
    protected XnioIoThreadMock threadMock;

    @Override @Before
    public void createChannelMock() throws IOException {
        super.createChannelMock();
        final XnioWorkerMock worker = new XnioWorkerMock();
        threadMock = worker.chooseThread();
        threadMock.start();
        conduitMock = new ConduitMock(worker, threadMock);
        connectionMock = new StreamConnectionMock(conduitMock);
        sslChannel = createSslChannel();
    }

    @After
    public void closeIoThread() {
        threadMock.closeIoThread();
    }

    protected ConnectedSslStreamChannel createSslChannel() {
        final Pool<ByteBuffer> socketBufferPool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 17000, 17000 * 16);
        final Pool<ByteBuffer> applicationBufferPool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 17000, 17000 * 16);
        final SslConnection connection = new JsseSslConnection(connectionMock, engineMock, socketBufferPool, applicationBufferPool);
        try {
            connection.startHandshake();
        } catch (IOException e) {
            throw new IOError(e);
        }
        return new AssembledConnectedSslStreamChannel(connection, connection.getSourceChannel(), connection.getSinkChannel());
    }

    @Override
    protected void assertWrittenMessage(String... message) {
        AssertReadWrite.assertWrittenMessage(conduitMock, message);
     }

    protected final void assertWrittenMessage(String[]... interwovenMessages) {
        super.tempAssertWrittenMessage(conduitMock.getWrittenText(), interwovenMessages);
    }
}
