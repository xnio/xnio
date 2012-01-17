/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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
import java.nio.channels.ClosedChannelException;

import org.jmock.Expectations;
import org.junit.After;
import org.junit.Test;
import org.xnio.BufferAllocator;
import org.xnio.ByteBufferSlicePool;
import org.xnio.Pool;
import org.xnio.ssl.mock.SSLEngineMock;

/**
 * Test invalid scenarios involving JsseConnectedSslStreamChannel.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 *
 */
public class SslChannelValidationTestCase extends AbstractJsseConnectedSslStreamChannelTest {

    @After @Override
    public void checkContext() {
        // do not check context... not all methods will be invoked on sessions created for invalid scenarios
    }

    @Test
    public void invalidConstructorParameters() {
        final Pool<ByteBuffer> socketBufferPool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 17000, 17000 * 16);
        final Pool<ByteBuffer> applicationBufferPool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 17000, 17000 * 16);
        // null connected stream channel
        boolean failed = false;
        try {
            new JsseConnectedSslStreamChannel(null, engineMock, socketBufferPool, applicationBufferPool, false);
        } catch (IllegalArgumentException e) {
            failed = true;
        }
        assertTrue(failed);
        // null ssl engine
        failed = false;
        try {
            new JsseConnectedSslStreamChannel(connectedChannelMock, null, socketBufferPool, applicationBufferPool, false);
        } catch (IllegalArgumentException e) {
            failed = true;
        }
        assertTrue(failed);
        // small length for socket buffer pool
        failed = false;
        
        final Pool<ByteBuffer> smallBufferPool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 16000, 16000 * 16);
        try {
            new JsseConnectedSslStreamChannel(connectedChannelMock, engineMock, smallBufferPool, applicationBufferPool, false);
        } catch (IllegalArgumentException e) {
            failed = true;
        }
        assertTrue(failed);
        // socket buffer pool will create a good sized receiveBuffer, but a small sized sendBuffer
        failed = false;
        @SuppressWarnings("unchecked")
        final Pool<ByteBuffer> invalidReadBufferPool = context.mock(Pool.class, "PoolByteBuffer allocates different buffer sizes");
        context.checking(new Expectations() {{
            oneOf(invalidReadBufferPool).allocate();
            will(returnValue(socketBufferPool.allocate()));
            oneOf(invalidReadBufferPool).allocate();
            will(returnValue(smallBufferPool.allocate()));
        }});
        try {
            new JsseConnectedSslStreamChannel(connectedChannelMock, engineMock, invalidReadBufferPool, applicationBufferPool, false);
        } catch (IllegalArgumentException e) {
            failed = true;
        }
        assertTrue(failed);
        // applicationBufferPool creates buffers too short
        failed = false;
        try {
            new JsseConnectedSslStreamChannel(connectedChannelMock, engineMock, socketBufferPool, smallBufferPool, false);
        } catch (IllegalArgumentException e) {
            failed = true;
        }
        assertTrue(failed);
        // applicationBufferPool will produce a buffer that is greater than SslEngine.session's packet buffer size,
        // but smaller than SslEngine.session's application buffer size
        failed = false;
        final Pool<ByteBuffer> mediumBufferPool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 16920, 16920 * 16);
        try {
            new JsseConnectedSslStreamChannel(connectedChannelMock, engineMock, socketBufferPool, mediumBufferPool, false);
        } catch (IllegalArgumentException e) {
            failed = true;
        }
        assertTrue(failed);
    }

    @Test
    public void writeAfterShutdown() throws IOException {
        connectedChannelMock.setReadData(SSLEngineMock.CLOSE_MSG);
        connectedChannelMock.enableRead(true);
        sslChannel.shutdownWrites();

        final ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put("MSG!!!!!!!".getBytes("UTF-8")).flip();
        boolean failed = false;
        try {
            sslChannel.write(buffer);
        } catch (ClosedChannelException e) {
            failed = true;
        }
        assertTrue(failed);
    }

    @Test
    public void invalidWriteBufferArray() throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put("invalid".getBytes("UTF-8")).flip();
        // the channel should simply return 0 if length is 0 or a negative number
        assertEquals(0, sslChannel.write(new ByteBuffer[]{buffer}, 0, 0));
        assertEquals(0, sslChannel.write(new ByteBuffer[]{buffer}, 0, -1));
        // channel should throw ArrayIndexOutOfBoundsException if position is < 0, or if length is larger than it should
        boolean failed = false;
        try {
            assertEquals(0, sslChannel.write(new ByteBuffer[]{buffer}, -1, 1));
        } catch (ArrayIndexOutOfBoundsException e) {
            failed = true;
        }
        assertTrue(failed);
        failed = false;
        try {
            assertEquals(0, sslChannel.write(new ByteBuffer[]{buffer}, 0, 2));
        } catch (ArrayIndexOutOfBoundsException e) {
            failed = true;
        }
        assertTrue(failed);
    }

    @Test
    public void writeNullBuffer() throws IOException {
        // null buffer
        boolean failed = false;
        try {
            sslChannel.write((ByteBuffer) null);
        } catch (NullPointerException e) {
            failed = true;
        }
        assertTrue(failed);
        // null buffer array
        failed = false;
        try {
            sslChannel.write((ByteBuffer[]) null);
        } catch (NullPointerException e) {
            failed = true;
        }
        assertTrue(failed);
        // null buffer in buffer array
        failed = false;
        try {
            sslChannel.write(new ByteBuffer[] {null});
        } catch (NullPointerException e) {
            failed = true;
        }
        assertTrue(failed);
    }

    @Test
    public void invalidReadBufferArray() throws IOException {
        connectedChannelMock.setReadData("invalid read buffer array");
        connectedChannelMock.enableRead(true);
        final ByteBuffer buffer = ByteBuffer.allocate(10);
        // the channel should simply return 0 if length is 0 or a negative number
        assertEquals(0, sslChannel.read(new ByteBuffer[]{buffer}, 0, 0));
        assertEquals(0, sslChannel.read(new ByteBuffer[]{buffer}, 0, -1));
        // channel should throw ArrayIndexOutOfBoundsException if position is < 0, or if length is larger than it should
        boolean failed = false;
        try {
            assertEquals(0, sslChannel.read(new ByteBuffer[]{buffer}, -1, 1));
        } catch (ArrayIndexOutOfBoundsException e) {
            failed = true;
        }
        assertTrue(failed);
        failed = false;
        try {
            assertEquals(0, sslChannel.read(new ByteBuffer[]{buffer}, 0, 2));
        } catch (ArrayIndexOutOfBoundsException e) {
            failed = true;
        }
        assertTrue(failed);
        // length parameter is ignored if buffer length is 0
        assertEquals(0, sslChannel.read(new ByteBuffer[0], 0, 50));
    }

    @Test
    public void readToNullBuffer() throws IOException {
        connectedChannelMock.setReadData("null read buffer");
        connectedChannelMock.enableRead(true);
        // read to a null buffer
        boolean failed = false;
        try {
            sslChannel.read((ByteBuffer) null);
        } catch (NullPointerException e) {
            failed = true;
        }
        assertTrue(failed);
        // read to a null buffer array
        failed = false;
        try {
            sslChannel.read((ByteBuffer[]) null);
        } catch (NullPointerException e) {
            failed = true;
        }
        assertTrue(failed);
        // read to a null buffer in a buffer array
        failed = false;
        try {
            sslChannel.read(new ByteBuffer[]{null});
        } catch (NullPointerException e) {
            failed = true;
        }
        assertTrue(failed);
    }

    // FIXME @Test
    public void closeCantFlush() throws IOException {
        connectedChannelMock.enableFlush(false);
        ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put("abc".getBytes("UTF-8")).flip();
        sslChannel.write(buffer);
        try {
            sslChannel.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        assertWrittenMessage("abc");
    }
}
