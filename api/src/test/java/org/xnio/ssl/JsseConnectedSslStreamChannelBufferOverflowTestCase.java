/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
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
import org.junit.Before;
import org.junit.Test;
import org.xnio.BufferAllocator;
import org.xnio.ByteBufferSlicePool;
import org.xnio.Pool;
import org.xnio.mock.ConnectedStreamChannelMock;
import org.xnio.ssl.mock.SSLEngineMock;
import org.xnio.ssl.mock.SSLEngineMock.HandshakeAction;


/**
 * Tests {@link JsseConnectedSslStreamChannel} in buffer overflow scenarios.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 *
 */
public class JsseConnectedSslStreamChannelBufferOverflowTestCase {

    // mockery context
    protected Mockery context;
    // the channel to be tested
    protected JsseConnectedSslStreamChannel sslChannel;
    // the underlying channel used by JsseConnectedSslStreamChannel above
    protected ConnectedStreamChannelMock connectedChannelMock;
    // the SSLEngine mock, allows to test different engine behavior with channel
    protected SSLEngineMock engineMock;

    @Before
    public void createChannelMock() throws IOException {
        context = new JUnit4Mockery();
        connectedChannelMock = new ConnectedStreamChannelMock();
        engineMock = new SSLEngineMockSmallPacketSize(context);
        final Pool<ByteBuffer> socketBufferPool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 10, 160);
        final Pool<ByteBuffer> applicationBufferPool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 10, 160);
        sslChannel = new JsseConnectedSslStreamChannel(connectedChannelMock, engineMock, socketBufferPool, applicationBufferPool, false);
    }

    @Test
    public void bufferOverflowOnWrite() throws IOException {
        boolean fail = false;
        final ByteBuffer buffer = ByteBuffer.allocate(100);
        buffer.put("12345678901234567890".getBytes("UTF-8")).flip();
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
