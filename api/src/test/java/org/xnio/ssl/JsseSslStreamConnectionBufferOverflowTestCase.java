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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;

import javax.net.ssl.SSLSession;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.concurrent.Synchroniser;
import org.junit.Before;
import org.junit.Test;
import org.xnio.BufferAllocator;
import org.xnio.ByteBufferSlicePool;
import org.xnio.Pool;
import org.xnio.conduits.StreamSinkConduit;
import org.xnio.conduits.StreamSourceConduit;
import org.xnio.mock.ConduitMock;
import org.xnio.mock.StreamConnectionMock;
import org.xnio.ssl.mock.SSLEngineMock;
import org.xnio.ssl.mock.SSLEngineMock.HandshakeAction;


/**
 * Test the SSL connection in buffer overflow scenarios.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 *
 */
public class JsseSslStreamConnectionBufferOverflowTestCase {

    // mockery context
    protected Mockery context;
    // the pair of conduits to be tested
    protected StreamSinkConduit sinkConduit;
    protected StreamSourceConduit sourceConduit;
    // the underlying conduit used as a delegate by the conduits above
    protected ConduitMock conduitMock;
    // the SSLEngine mock, allows to test different engine behavior with channel
    protected SSLEngineMock engineMock;

    @Before
    public void createChannelMock() throws IOException {
        context = new JUnit4Mockery() {{
                    setThreadingPolicy(new Synchroniser());
                }};
        engineMock = new SSLEngineMockSmallPacketSize(context);
        final Pool<ByteBuffer> socketBufferPool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 10, 160);
        final Pool<ByteBuffer> applicationBufferPool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 10, 160);
        conduitMock = new ConduitMock();
        final StreamConnectionMock connectionMock = new StreamConnectionMock(conduitMock);
        final JsseSslStreamConnection connection = new JsseSslStreamConnection(connectionMock, engineMock, socketBufferPool, applicationBufferPool, false);
        this.sinkConduit = connection.getSinkChannel().getConduit();
        this.sourceConduit = connection.getSourceChannel().getConduit();
    }

    @Test
    public void bufferOverflowOnWrite() throws IOException {
        boolean fail = false;
        final ByteBuffer buffer = ByteBuffer.allocate(100);
        buffer.put("12345678901234567890".getBytes("UTF-8")).flip();
        // attempt to write... conduit is expected to write to throw an IOException
        // SSLEngine required a bigger send buffer but out buffer was already big enough 
        try {
        sinkConduit.write(buffer);
        } catch (IOException e) {
            fail = true;
        }
        assertTrue(fail);
    }

    @Test
    public void bufferOverflowOnRead() throws IOException {
        engineMock.setHandshakeActions(HandshakeAction.NEED_UNWRAP, HandshakeAction.FINISH);
        conduitMock.setReadData(SSLEngineMock.HANDSHAKE_MSG);
        conduitMock.enableReads(true);
        // attempt to read... conduit is expected to not be able to read the message because HANDSHAKE_MSG does not fit
        // in a 10 byte buffer
        assertEquals(0, sourceConduit.read(ByteBuffer.allocate(50)));
    }

    private static class SSLEngineMockSmallPacketSize extends SSLEngineMock {
        public SSLEngineMockSmallPacketSize (Mockery mockery) {
            super(mockery);
        }

        @Override
        public SSLSession getSession() {
            synchronized (mockery) {
                final SSLSession sessionMock = mockery.mock(SSLSession.class, "SmallPacketsSession");
                mockery.checking(new Expectations() {{
                    oneOf(sessionMock).getPacketBufferSize();
                    will(returnValue(10));
                    oneOf(sessionMock).getApplicationBufferSize();
                    will(returnValue(5));
                }});
                return sessionMock;
            }
        }
    }
}
