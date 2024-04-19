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

import java.io.IOError;
import java.io.IOException;
import java.nio.ByteBuffer;

import javax.net.ssl.SSLSession;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.concurrent.Synchroniser;
import org.junit.Test;
import org.xnio.ByteBufferPool;
import org.xnio.channels.AssembledConnectedSslStreamChannel;
import org.xnio.channels.ConnectedSslStreamChannel;
import org.xnio.mock.ConnectedStreamChannelMock;
import org.xnio.ssl.mock.SSLEngineMock;
import org.xnio.ssl.mock.SSLEngineMock.HandshakeAction;


/**
 * Tests {@link AssembledConnectedSslStreamChannel} in buffer overflow scenarios.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 *
 */
public class ConnectedSslStreamChannelBufferOverflowTestCase extends AbstractConnectedSslStreamChannelTest {

    // mockery context
    protected Mockery context;

    // the underlying channel used by JsseConnectedSslStreamChannel above
    protected ConnectedStreamChannelMock connectedChannelMock;
    // the SSLEngine mock, allows to test different engine behavior with channel
    protected SSLEngineMock engineMock;

    @Override
    protected ConnectedSslStreamChannel createSslChannel() {
        context = new JUnit4Mockery() {{
            setThreadingPolicy(new Synchroniser());
        }};
        connectedChannelMock = new ConnectedStreamChannelMock();
        engineMock = new SSLEngineMockSmallPacketSize(context);
        final ByteBufferPool socketBufferPool = ByteBufferPool.SMALL_HEAP;
        final ByteBufferPool applicationBufferPool = ByteBufferPool.SMALL_HEAP;
        final SslConnection connection = new JsseSslConnection(connectionMock, engineMock, socketBufferPool, applicationBufferPool);
        try {
            connection.startHandshake();
        } catch (IOException e) {
            throw new IOError(e);
        }
        return new AssembledConnectedSslStreamChannel(connection, connection.getSourceChannel(), connection.getSinkChannel());
    }

    @Test
    public void bufferOverflowOnWrite() throws IOException {
        boolean fail = false;
        final ByteBuffer buffer = ByteBuffer.allocate(100);
        for (int i = 0; i < 10; i++) {
            buffer.put("1234567890".getBytes("UTF-8"));
        }
        buffer.flip();
        // attempt to write... channel is expected to write to throw an IOException
        // SSLEngine required a bigger send buffer but out buffer was already big enough 
        try {
        sslChannel.write(buffer);
        } catch (IOException e) {
            fail = true;
        }
        assertTrue(fail);
    }

    @Test
    public void bufferOverflowOnRead() throws IOException {
        engineMock.setHandshakeActions(HandshakeAction.NEED_UNWRAP, HandshakeAction.FINISH);
        connectedChannelMock.setReadData(SSLEngineMock.HANDSHAKE_MSG);
        connectedChannelMock.enableRead(true);
        // attempt to read... channel is expected to not be able to read the message because HANDSHAKE_MSG does not fit
        // in a 10 byte buffer
        assertEquals(0, sslChannel.read(ByteBuffer.allocate(50)));
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
