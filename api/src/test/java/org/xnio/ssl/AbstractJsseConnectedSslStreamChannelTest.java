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

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import org.junit.Before;
import org.xnio.AssertReadWrite;
import org.xnio.BufferAllocator;
import org.xnio.ByteBufferSlicePool;
import org.xnio.Pool;
import org.xnio.channels.TranslatingSuspendableChannel;
import org.xnio.mock.ConnectedStreamChannelMock;

/**
 * Abstract test for {@link #JsseConnectedSslStreamChannel}.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 */
public abstract class AbstractJsseConnectedSslStreamChannelTest extends AbstractSslTest {
    // the channel to be tested
    protected JsseConnectedSslStreamChannel sslChannel;
    // the underlying channel used as a delegate by the test target
    protected ConnectedStreamChannelMock connectedChannelMock;

    @Override @Before
    public void createChannelMock() throws IOException {
        super.createChannelMock();
        connectedChannelMock = new ConnectedStreamChannelMock();
        sslChannel = createSslChannel();
    }

    protected JsseConnectedSslStreamChannel createSslChannel() {
        final Pool<ByteBuffer> socketBufferPool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 17000, 17000 * 16);
        final Pool<ByteBuffer> applicationBufferPool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 17000, 17000 * 16);
        return new JsseConnectedSslStreamChannel(connectedChannelMock, engineMock, socketBufferPool, applicationBufferPool, false);
    }

    protected void sslChannelHandleWritable() throws Exception {
        Method handleWritableMethod = TranslatingSuspendableChannel.class.getDeclaredMethod("handleWritable");
        handleWritableMethod.setAccessible(true);
        handleWritableMethod.invoke(sslChannel);
    }

    @Override
    protected void assertWrittenMessage(String... message) {
        AssertReadWrite.assertWrittenMessage(connectedChannelMock, message);
     }

    protected final void assertWrittenMessage(String[]... interwovenMessages) {
        super.tempAssertWrittenMessage(connectedChannelMock.getWrittenText(), interwovenMessages);
    }
}
