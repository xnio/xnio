/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2012 Red Hat, Inc. and/or its affiliates, and individual
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

package org.xnio.channels;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.EOFException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.xnio.BufferAllocator;
import org.xnio.Buffers;
import org.xnio.ByteBufferSlicePool;
import org.xnio.LocalSocketAddress;
import org.xnio.mock.ConnectedStreamChannelMock;

/**
 * Test for {@link FramedMessageChannel}.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
public class FramedMessageChannelTestCase {
    private ConnectedStreamChannelMock connectedChannel;

    @Before
    public void init() {
        connectedChannel = new ConnectedStreamChannelMock();
    }

    @Test
    public void receive() throws IOException {
        final FramedMessageChannel channel = new FramedMessageChannel(connectedChannel, ByteBuffer.allocate(15000), ByteBuffer.allocate(15000));
        connectedChannel.setReadDataWithLength("data");
        connectedChannel.enableRead(true);
        ByteBuffer buffer = ByteBuffer.allocate(20);
        assertEquals(4, channel.receive(buffer));
        assertEquals(0, channel.receive(buffer));
        assertReadMessage(buffer, "data");
    }

    @Test
    public void receiveDoubleMessage() throws IOException {
        final FramedMessageChannel channel = new FramedMessageChannel(connectedChannel, ByteBuffer.allocate(15000), ByteBuffer.allocate(15000));
        connectedChannel.setReadDataWithLength("message_1");
        connectedChannel.setReadDataWithLength("message_2");
        connectedChannel.enableRead(true);
        ByteBuffer buffer = ByteBuffer.allocate(20);
        assertEquals(9, channel.receive(buffer));
        assertEquals(9, channel.receive(buffer));
        assertEquals(0, channel.receive(buffer));
        assertReadMessage(buffer, "message_1", "message_2");
    }

    @Test
    public void bufferOverflowOnReceive() throws IOException {
        final FramedMessageChannel channel = new FramedMessageChannel(connectedChannel, ByteBuffer.allocate(15000), ByteBuffer.allocate(15000));
        connectedChannel.setReadDataWithLength("message");
        connectedChannel.setEof();
        connectedChannel.enableRead(true);
        ByteBuffer buffer = ByteBuffer.allocate(2);
        assertEquals(2, channel.receive(buffer));
        assertReadMessage(buffer, "me");
        // full buffer
        assertEquals(0, channel.receive(buffer));
        // empty full buffer
        buffer.clear();
        assertEquals(2, channel.receive(buffer));
        // FIXME
//        assertReadMessage(buffer, "ss");
//        buffer.clear();
//        assertEquals(2, channel.receive(buffer));
//        assertReadMessage(buffer, "ag");
//        buffer.clear();
//        assertEquals(1, channel.receive(buffer));
//        assertReadMessage(buffer, "e");
//        buffer.clear();
//        assertEquals(-1, channel.receive(buffer));
    }

    @Test
    public void receiveTruncatedMessage() throws IOException {
        final ByteBufferSlicePool pool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 1000, 1000 * 16);
        final FramedMessageChannel channel = new FramedMessageChannel(connectedChannel, pool.allocate(), pool.allocate());
        connectedChannel.setReadDataWithLength("1234");
        connectedChannel.setReadDataWithLength(4, "567890");
        connectedChannel.enableRead(true);
        ByteBuffer buffer = ByteBuffer.allocate(20);
        assertEquals(4, channel.receive(buffer));
        assertEquals(4, channel.receive(buffer));
        assertEquals(0, channel.receive(buffer));
        connectedChannel.setEof();
        assertEquals(-1, channel.receive(buffer));
        assertEquals(-1, channel.receive(buffer));
        assertReadMessage(buffer, "12345678");
    }

    @Test
    public void receiveIncompleteMessage() throws IOException {
        final ByteBufferSlicePool pool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 1000, 1000 * 16);
        final FramedMessageChannel channel = new FramedMessageChannel(connectedChannel, pool.allocate(), pool.allocate());
        connectedChannel.setReadDataWithLength(6, "hello");
        connectedChannel.enableRead(true);
        ByteBuffer buffer = ByteBuffer.allocate(20);
        assertEquals(0, channel.receive(buffer));
        assertEquals(0, channel.receive(buffer));
        connectedChannel.setEof();
        assertEquals(-1, channel.receive(buffer));
        assertEquals(-1, channel.receive(buffer));
    }

    @Test
    public void receiveInvalidLengthMessage() throws IOException {
        final ByteBufferSlicePool pool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 10, 10 * 16);
        final FramedMessageChannel channel = new FramedMessageChannel(connectedChannel, pool.allocate(), pool.allocate());
        connectedChannel.setReadDataWithLength(20, "12345678901234567890");
        connectedChannel.enableRead(true);
        ByteBuffer buffer = ByteBuffer.allocate(20);
        boolean failed = false;
        try {
            channel.receive(buffer);
        } catch (IOException e) {
            failed = true;
        }
        assertTrue(failed);
    }

    @Test
    public void receiveNegativeLengthMessage() throws IOException {
        final ByteBufferSlicePool pool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 10, 10 * 16);
        final FramedMessageChannel channel = new FramedMessageChannel(connectedChannel, pool.allocate(), pool.allocate());
        connectedChannel.setReadDataWithLength(-8, "abcdefgh");
        connectedChannel.enableRead(true);
        ByteBuffer buffer = ByteBuffer.allocate(10);
        boolean failed = false;
        try {
            channel.receive(buffer);
        } catch (IOException e) {
            failed = true;
        }
        assertTrue(failed);
    }

    @Test
    public void receiveBrokenMessage() throws IOException {
        final ByteBufferSlicePool pool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 1000, 1000 * 16);
        final FramedMessageChannel channel = new FramedMessageChannel(connectedChannel, pool.allocate(), pool.allocate());
        connectedChannel.setReadDataWithLength(2);
        connectedChannel.enableRead(true);
        ByteBuffer buffer = ByteBuffer.allocate(5);
        assertEquals(0, channel.receive(buffer));
        assertEquals(0, channel.receive(buffer));
        connectedChannel.setReadData("oh");
        assertEquals(2, channel.receive(buffer));
        assertEquals(0, channel.receive(buffer));
        assertReadMessage(buffer, "oh");
    }

    @Test
    public void receiveToByteBufferArray() throws IOException {
        final ByteBufferSlicePool pool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 1000, 1000 * 16);
        final FramedMessageChannel channel = new FramedMessageChannel(connectedChannel, pool.allocate(), pool.allocate());
        connectedChannel.setReadDataWithLength("receive");
        connectedChannel.setReadDataWithLength("this");
        connectedChannel.enableRead(true);
        ByteBuffer[] buffer = new ByteBuffer[] {ByteBuffer.allocate(4), ByteBuffer.allocate(4), ByteBuffer.allocate(4),ByteBuffer.allocate(4)};
        assertEquals(7, channel.receive(buffer));
        assertEquals(4, channel.receive(buffer));
        assertEquals(0, channel.receive(buffer));
        assertReadMessage(buffer[0], "rece");
        assertReadMessage(buffer[1], "ivet");
        assertReadMessage(buffer[2], "his");
        assertReadMessage(buffer[3]);
    }

    @Test
    public void bufferOverflowOnReceiveToByteArray() throws IOException {
        final FramedMessageChannel channel = new FramedMessageChannel(connectedChannel, ByteBuffer.allocate(15000), ByteBuffer.allocate(15000));
        connectedChannel.setReadDataWithLength("message");
        connectedChannel.setEof();
        connectedChannel.enableRead(true);
        ByteBuffer[] buffer = new ByteBuffer[] {ByteBuffer.allocate(1), ByteBuffer.allocate(1)};
        assertEquals(2, channel.receive(buffer));
        assertReadMessage(buffer[0], "m");
        assertReadMessage(buffer[1], "e");
        // full buffers
        assertEquals(0, channel.receive(buffer));
        // empty buffers
        buffer[0].clear();
        buffer[1].clear();
        assertEquals(2, channel.receive(buffer));
        // FIXME
//        assertReadMessage(buffer[0], "s");
//        assertReadMessage(buffer[1], "s");
//        buffer.clear();
//        assertEquals(2, channel.receive(buffer));
//        assertReadMessage(buffer, "ag");
//        buffer.clear();
//        assertEquals(1, channel.receive(buffer));
//        assertReadMessage(buffer, "e");
//        buffer.clear();
//        assertEquals(-1, channel.receive(buffer));
    }

    @Test
    public void receiveToByteBufferArrayWithOffset1() throws IOException {
        final ByteBufferSlicePool pool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 1000, 1000 * 16);
        final FramedMessageChannel channel = new FramedMessageChannel(connectedChannel, pool.allocate(), pool.allocate());
        connectedChannel.setReadDataWithLength("123");
        connectedChannel.setReadDataWithLength("456");
        connectedChannel.enableRead(true);
        ByteBuffer[] buffer = new ByteBuffer[] {ByteBuffer.allocate(3), ByteBuffer.allocate(3), ByteBuffer.allocate(3),ByteBuffer.allocate(3)};
        assertEquals(3, channel.receive(buffer, 1, 3));
        assertEquals(3, channel.receive(buffer, 1, 3));
        assertEquals(0, channel.receive(buffer, 1, 3));
        assertEquals(0, buffer[0].position());
        assertReadMessage(buffer[1], "123");
        assertReadMessage(buffer[2], "456");
        assertEquals(0, buffer[3].position());
    }

    @Test
    public void receiveToByteBufferArrayWithOffset2() throws IOException {
        final ByteBufferSlicePool pool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 1000, 1000 * 16);
        final FramedMessageChannel channel = new FramedMessageChannel(connectedChannel, pool.allocate(), pool.allocate());
        connectedChannel.setReadDataWithLength("1234");
        connectedChannel.setReadDataWithLength("567890");
        connectedChannel.enableRead(true);
        ByteBuffer[] buffer = new ByteBuffer[] {ByteBuffer.allocate(3), ByteBuffer.allocate(3), ByteBuffer.allocate(3),
                ByteBuffer.allocate(3)};
        assertEquals(4, channel.receive(buffer, 0, 3));
        assertEquals(5, channel.receive(buffer, 0, 3));
        assertEquals(0, channel.receive(buffer, 0, 3));
        assertReadMessage(buffer[0], "123");
        assertReadMessage(buffer[1], "456");
        assertReadMessage(buffer[2], "789");
        assertEquals(0, buffer[3].position());
    }

    @Test
    public void receiveTruncatedMessageToByteArray() throws IOException {
        final ByteBufferSlicePool pool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 1000, 1000 * 16);
        final FramedMessageChannel channel = new FramedMessageChannel(connectedChannel, pool.allocate(), pool.allocate());
        connectedChannel.setReadDataWithLength("1234");
        connectedChannel.setReadDataWithLength(4, "567890");
        connectedChannel.enableRead(true);
        ByteBuffer[] buffer = new ByteBuffer[] {ByteBuffer.allocate(3), ByteBuffer.allocate(3), ByteBuffer.allocate(3),
                ByteBuffer.allocate(3)};
        assertEquals(4, channel.receive(buffer, 0, 3));
        assertEquals(4, channel.receive(buffer, 0, 3));
        assertEquals(0, channel.receive(buffer, 0, 3));
        connectedChannel.setEof();
        assertEquals(-1, channel.receive(buffer, 0, 3));
        assertEquals(-1, channel.receive(buffer, 0, 3));
        assertEquals(-1, channel.receive(buffer, 0, 3));
        assertReadMessage(buffer[0], "123");
        assertReadMessage(buffer[1], "456");
        assertReadMessage(buffer[2], "78");
        assertEquals(0, buffer[3].position());
    }

    @Test
    public void receiveIncompleteMessageToByteArray() throws IOException {
        final ByteBufferSlicePool pool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 1000, 1000 * 16);
        final FramedMessageChannel channel = new FramedMessageChannel(connectedChannel, pool.allocate(), pool.allocate());
        connectedChannel.setReadDataWithLength(10, "hello");
        connectedChannel.enableRead(true);
        ByteBuffer[] buffer = new ByteBuffer[]{ByteBuffer.allocate(20)};
        assertEquals(0, channel.receive(buffer));
        assertEquals(0, channel.receive(buffer));
        connectedChannel.setEof();
        assertEquals(-1, channel.receive(buffer));
        assertEquals(-1, channel.receive(buffer));
    }

    @Test
    public void receiveInvalidLengthMessageToByteArray() throws IOException {
        final ByteBufferSlicePool pool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 10, 10 * 16);
        final FramedMessageChannel channel = new FramedMessageChannel(connectedChannel, pool.allocate(), pool.allocate());
        connectedChannel.setReadDataWithLength(15, "987654321098765");
        connectedChannel.enableRead(true);
        ByteBuffer buffer = ByteBuffer.allocate(20);
        boolean failed = false;
        try {
            channel.receive(new ByteBuffer[] {buffer});
        } catch (IOException e) {
            failed = true;
        }
        assertTrue(failed);
    }

    @Test
    public void receiveNegativeLengthMessageToByteArray() throws IOException {
        final ByteBufferSlicePool pool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 10, 10 * 16);
        final FramedMessageChannel channel = new FramedMessageChannel(connectedChannel, pool.allocate(), pool.allocate());
        connectedChannel.setReadDataWithLength(-3, "abc");
        connectedChannel.enableRead(true);
        ByteBuffer buffer = ByteBuffer.allocate(5);
        boolean failed = false;
        try {
            channel.receive(new ByteBuffer[] {buffer});
        } catch (IOException e) {
            failed = true;
        }
        assertTrue(failed);
    }

    @Test
    public void send() throws IOException {
        final ByteBufferSlicePool pool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 1000, 1000 * 16);
        final FramedMessageChannel channel = new FramedMessageChannel(connectedChannel, pool.allocate(), pool.allocate());
        final ByteBuffer buffer = ByteBuffer.allocate(20);
        buffer.put("hello!".getBytes("UTF-8")).flip();
        assertTrue(channel.send(buffer));
        assertWrittenMessage("hello!");
    }

    @Test
    public void sendMultipleMessages() throws IOException {
        final ByteBufferSlicePool pool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 1000, 1000 * 16);
        final FramedMessageChannel channel = new FramedMessageChannel(connectedChannel, pool.allocate(), pool.allocate());
        final ByteBuffer buffer = ByteBuffer.allocate(20);
        buffer.put("jboss ".getBytes("UTF-8")).flip();
        assertTrue(channel.send(buffer));
        assertTrue(channel.send(buffer));
        buffer.clear();
        buffer.put("xnio".getBytes("UTF-8")).flip();
        assertTrue(channel.send(buffer));
        assertTrue(channel.send(buffer));
        buffer.clear();
        buffer.put("-api".getBytes("UTF-8")).flip();
        assertTrue(channel.send(buffer));
        assertTrue(channel.send(buffer));
        assertWrittenMessage("jboss ", "xnio", "-api");
    }

    @Test
    public void sendMessageTooLarge() throws IOException {
        final ByteBufferSlicePool pool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 2, 2 * 16);
        final FramedMessageChannel channel = new FramedMessageChannel(connectedChannel, pool.allocate(), pool.allocate());
        final ByteBuffer buffer = ByteBuffer.allocate(20);
        buffer.put("hello!".getBytes("UTF-8")).flip();
        boolean failed = false;
        try {
            assertTrue(channel.send(buffer));
        } catch (IOException e) {
            failed = true;
        }
        assertTrue(failed);
    }

    @Test
    public void bufferOverflowOnSend() throws IOException {
        final ByteBufferSlicePool pool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 28, 28 * 16);
        final FramedMessageChannel channel = new FramedMessageChannel(connectedChannel, pool.allocate(), pool.allocate());
        // framed message channel won't be able to flush because write is disabled at connectedChannel
        connectedChannel.enableWrite(false);
        final ByteBuffer buffer = ByteBuffer.allocate(20);
        buffer.put("hi!".getBytes("UTF-8")).flip();
        assertTrue(channel.send(buffer));
        buffer.flip();
        assertTrue(channel.send(buffer));
        buffer.flip();
        assertTrue(channel.send(buffer));
        buffer.flip();
        assertTrue(channel.send(buffer));
        buffer.flip();
        assertFalse(channel.send(buffer));
        assertWrittenMessage();
        // enable write
        connectedChannel.enableWrite(true);
        assertTrue(channel.send(buffer));
        assertWrittenMessage("hi!", "hi!", "hi!", "hi!", "hi!");
    }

    @Test
    public void sendByteBufferArray() throws IOException {
        final ByteBufferSlicePool pool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 1000, 1000 * 16);
        final FramedMessageChannel channel = new FramedMessageChannel(connectedChannel, pool.allocate(), pool.allocate());
        final ByteBuffer[] buffer = new ByteBuffer[]{ByteBuffer.allocate(20), ByteBuffer.allocate(20)};
        buffer[0].put("hello!".getBytes("UTF-8")).flip();
        buffer[1].put("world!".getBytes("UTF-8")).flip();
        assertTrue(channel.send(buffer));
        assertWrittenMessage("hello!world!");
    }

    @Test
    public void sendMultipleMessagesWithBufferByteArray() throws IOException {
        final ByteBufferSlicePool pool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 1000, 1000 * 16);
        final FramedMessageChannel channel = new FramedMessageChannel(connectedChannel, pool.allocate(), pool.allocate());
        final ByteBuffer[] buffer = new ByteBuffer[]{ByteBuffer.allocate(2), ByteBuffer.allocate(5)};
        buffer[0].put("jb".getBytes("UTF-8")).flip();
        buffer[1].put("oss ".getBytes("UTF-8")).flip();
        assertTrue(channel.send(buffer));
        assertTrue(channel.send(buffer));
        buffer[0].clear(); buffer[1].clear();
        buffer[0].put("xn".getBytes("UTF-8")).flip();
        buffer[1].put("io".getBytes("UTF-8")).flip();
        assertTrue(channel.send(buffer));
        assertTrue(channel.send(buffer));
        buffer[0].clear(); buffer[1].clear();
        buffer[0].put("-a".getBytes("UTF-8")).flip();
        buffer[1].put("pi".getBytes("UTF-8")).flip();
        assertTrue(channel.send(buffer));
        assertTrue(channel.send(buffer));
        assertWrittenMessage("jboss ", "xnio", "-api");
    }

    @Test
    public void sendMultipleMessagesWithBufferByteArrayAndOffset() throws IOException {
        final ByteBufferSlicePool pool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 1000, 1000 * 16);
        final FramedMessageChannel channel = new FramedMessageChannel(connectedChannel, pool.allocate(), pool.allocate());
        final ByteBuffer[] buffer = new ByteBuffer[]{ByteBuffer.allocate(2), ByteBuffer.allocate(5), ByteBuffer.allocate(2), ByteBuffer.allocate(5)};
        buffer[1].put("jboss".getBytes("UTF-8")).flip();
        buffer[2].put(" ".getBytes("UTF-8")).flip();
        assertTrue(channel.send(buffer, 1, 2));
        assertTrue(channel.send(buffer, 1, 2));
        buffer[1].clear();
        buffer[1].put("xnio".getBytes("UTF-8")).flip();
        assertTrue(channel.send(buffer, 1, 2));
        assertTrue(channel.send(buffer, 1, 2));
        buffer[1].clear();
        buffer[1].put("-api".getBytes("UTF-8")).flip();
        assertTrue(channel.send(buffer, 1, 2));
        assertTrue(channel.send(buffer, 1, 2));
        assertWrittenMessage("jboss ", "xnio", "-api");
    }

    @Test
    public void sendMessageTooLargeWithByteBufferArray() throws IOException {
        final ByteBufferSlicePool pool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 2, 2 * 16);
        final FramedMessageChannel channel = new FramedMessageChannel(connectedChannel, pool.allocate(), pool.allocate());
        final ByteBuffer[] buffer = new ByteBuffer[] {ByteBuffer.allocate(20)};
        buffer[0].put("hello!".getBytes("UTF-8")).flip();
        boolean failed = false;
        try {
            assertTrue(channel.send(buffer));
        } catch (IOException e) {
            failed = true;
        }
        assertTrue(failed);
    }

    @Test
    public void bufferOverflowOnSendByteBufferArray() throws IOException {
        final ByteBufferSlicePool pool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 40, 40 * 16);
        final FramedMessageChannel channel = new FramedMessageChannel(connectedChannel, pool.allocate(), pool.allocate());
        // framed message channel won't be able to flush because write is disabled at connectedChannel
        connectedChannel.enableWrite(false);
        final ByteBuffer[] buffer = new ByteBuffer[] {ByteBuffer.allocate(20)};
        buffer[0].put("hello!".getBytes("UTF-8")).flip();
        assertTrue(channel.send(buffer));
        buffer[0].flip();
        assertTrue(channel.send(buffer));
        buffer[0].flip();
        assertTrue(channel.send(buffer));
        buffer[0].flip();
        assertTrue(channel.send(buffer));
        buffer[0].flip();
        assertFalse(channel.send(buffer));
        assertWrittenMessage();
        // enable write
        connectedChannel.enableWrite(true);
        assertTrue(channel.send(buffer));
        assertWrittenMessage("hello!", "hello!", "hello!", "hello!", "hello!");
    }

    @Test
    public void shutdownWritesAndClose() throws IOException {
        final ByteBufferSlicePool pool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 40, 40 * 16);
        final FramedMessageChannel channel = new FramedMessageChannel(connectedChannel, pool.allocate(), pool.allocate());
        channel.shutdownWrites();
        assertTrue(channel.isWriteShutDown());
        channel.flush();
        final ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put("can't send".getBytes("UTF-8")).flip();
        boolean eofException = false;
        try {
            channel.send(buffer);
        } catch (EOFException e) {
            eofException = true;
        }
        assertTrue(eofException);
        eofException = false;
        try {
            channel.send(new ByteBuffer[]{buffer});
        } catch (EOFException e) {
            eofException = true;
        }
        assertTrue(eofException);
        assertFalse(channel.isReadShutDown());
        channel.close();
        assertTrue(channel.isReadShutDown());
    }

    @Test
    public void shutdownReadsAndClose() throws IOException {
        final ByteBufferSlicePool pool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 40, 40 * 16);
        final FramedMessageChannel channel = new FramedMessageChannel(connectedChannel, pool.allocate(), pool.allocate());
        channel.shutdownReads();
        assertTrue(channel.isReadShutDown());
        assertFalse(channel.isWriteShutDown());
        channel.close();
        assertTrue(channel.isWriteShutDown());
    }

    @Test
    public void close() throws IOException {
        final ByteBufferSlicePool pool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 40, 40 * 16);
        final FramedMessageChannel channel = new FramedMessageChannel(connectedChannel, pool.allocate(), pool.allocate());
        channel.close();
        assertTrue(channel.isWriteShutDown());
        assertTrue(channel.isReadShutDown());
    }

    @Test
    public void closeFailure() throws IOException {
        final ByteBufferSlicePool pool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 40, 40 * 16);
        final FramedMessageChannel channel = new FramedMessageChannel(connectedChannel, pool.allocate(), pool.allocate());
        connectedChannel.enableFlush(false);
        ByteBuffer buffer = ByteBuffer.allocate(5);
        buffer.put("a".getBytes("UTF-8")).flip();
        channel.send(buffer);
        boolean failed = false;
        try {
            channel.close();
        } catch (IOException e) {
            failed = true;
        }
        assertTrue(failed);
        //assertTrue(channel.isWriteShutDown());
        //assertTrue(channel.isReadShutDown());
    }

    @Test
    public void flush() throws IOException {
        final ByteBufferSlicePool pool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 40, 40 * 16);
        final FramedMessageChannel channel = new FramedMessageChannel(connectedChannel, pool.allocate(), pool.allocate());
        final ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put("test".getBytes("UTF-8")).flip();
        connectedChannel.enableWrite(false);
        connectedChannel.enableFlush(false);
        assertTrue(channel.send(buffer));
        assertFalse(channel.flush());
        connectedChannel.enableWrite(true);
        assertFalse(channel.flush());
        connectedChannel.enableFlush(true);
        assertTrue(channel.flush());
        connectedChannel.enableWrite(false);
        connectedChannel.enableFlush(false);
        buffer.flip();
        assertTrue(channel.send(buffer));
        channel.shutdownWrites();
        assertTrue(channel.flush()); // FIXME!
        // connectedChannel.enableWrite(true);
        // assertFalse(channel.flush());
        // connectedChannel.enableFlush(true);
        // assertTrue(channel.flush());
    }
    @Test
    public void getAddresses() throws IOException {
        final ByteBufferSlicePool pool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 40, 40 * 16);
        final FramedMessageChannel channel = new FramedMessageChannel(connectedChannel, pool.allocate(), pool.allocate());
        assertSame(connectedChannel, channel.getChannel());
        // getLocalAddress
        connectedChannel.setLocalAddress(new InetSocketAddress(10));
        assertEquals(connectedChannel.getLocalAddress(), channel.getLocalAddress());
        assertEquals(connectedChannel.getLocalAddress(InetSocketAddress.class), channel.getLocalAddress(InetSocketAddress.class));
        assertEquals(connectedChannel.getLocalAddress(LocalSocketAddress.class), channel.getLocalAddress(LocalSocketAddress.class));
        // getPeerAddress
        connectedChannel.setPeerAddress(new LocalSocketAddress("local"));
        assertEquals(connectedChannel.getPeerAddress(), channel.getPeerAddress());
        assertEquals(connectedChannel.getPeerAddress(LocalSocketAddress.class), channel.getPeerAddress(LocalSocketAddress.class));
        assertEquals(connectedChannel.getPeerAddress(InetSocketAddress.class), channel.getPeerAddress(InetSocketAddress.class));
    }

    protected final void assertReadMessage(ByteBuffer dst, String... message) {
        StringBuffer stringBuffer = new StringBuffer();
        for (String messageString: message) {
            stringBuffer.append(messageString);
        }
        dst.flip();
        assertEquals(stringBuffer.toString(), Buffers.getModifiedUtf8(dst));
    }

    protected final void assertWrittenMessage(String... message) throws UnsupportedEncodingException {
        int totalLength = 0;
        for (String m: message) {
            totalLength += 4 + m.length();
        }
        ByteBuffer byteBuffer = ByteBuffer.allocate(totalLength);
        for (String m: message) {
            byteBuffer.putInt(m.length());
            byteBuffer.put(m.getBytes("UTF-8"));
        }
        
        ByteBuffer written = connectedChannel.getWrittenBytes();
        written.flip();
        assertEquals(byteBuffer.limit(), written.limit());
        Assert.assertArrayEquals(byteBuffer.array(), Arrays.copyOf(written.array(), totalLength));
    }
}
