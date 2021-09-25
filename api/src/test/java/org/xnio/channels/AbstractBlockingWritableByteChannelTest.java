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
import static org.junit.Assert.assertTrue;
import static org.xnio.AssertReadWrite.assertWrittenMessage;

import java.io.Flushable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.GatheringByteChannel;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.xnio.mock.ConnectedStreamChannelMock;

/**
 * Abstract class that contains test cases applicable to any blocking {@code GatheringByteChannel}, {@code Flushable}.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
public abstract class AbstractBlockingWritableByteChannelTest<T extends GatheringByteChannel & Flushable> {
    private ConnectedStreamChannelMock channelMock;

    @Before
    public void initChannelMock() {
        channelMock = new ConnectedStreamChannelMock();
        channelMock.enableRead(true);
        channelMock.enableWrite(false);
    }

    protected abstract T createBlockingWritableByteChannel(ConnectedStreamChannelMock channelMock);
    protected abstract T createBlockingWritableByteChannel(ConnectedStreamChannelMock channelMock, long timeout, TimeUnit timeoutUnit);
    protected abstract T createBlockingWritableByteChannel(ConnectedStreamChannelMock channelMock, long readTimeout, TimeUnit readTimeoutUnit, long writeTimeout, TimeUnit writeTimeoutUnit);
    protected abstract void setWriteTimeout(T channel, long writeTimeout, TimeUnit writeTimeoutUnit);

    @Test
    public void simpleWrite() throws Exception {
        final T blockingChannel = createBlockingWritableByteChannel(channelMock);
        channelMock.enableWrite(true);
        final ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put("write".getBytes("UTF-8")).flip();
        assertEquals(5, blockingChannel.write(buffer));
        assertWrittenMessage(channelMock, "write");
    }

    @Test
    public void simpleWriteWithTimeout() throws Exception {
        final T blockingChannel = createBlockingWritableByteChannel(channelMock);
        setWriteTimeout(blockingChannel, 100, TimeUnit.MILLISECONDS);
        channelMock.enableWrite(true);
        final ByteBuffer buffer = ByteBuffer.allocate(20);
        buffer.put("write with timeout".getBytes("UTF-8")).flip();
        assertEquals(18, blockingChannel.write(buffer));
        assertWrittenMessage(channelMock, "write with timeout");
    }

    @Test
    public void simpleEmptyWriteWithTimeout() throws Exception {
        final T blockingChannel = createBlockingWritableByteChannel(channelMock);
        setWriteTimeout(blockingChannel, 15, TimeUnit.MILLISECONDS);
        channelMock.enableWrite(true);
        final ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.flip();
        assertEquals(0, blockingChannel.write(buffer));
        assertWrittenMessage(channelMock);
    }

    @Test
    public void writeBlocks() throws Exception {
        final T blockingChannel = createBlockingWritableByteChannel(channelMock);
        final Write writeRunnable = new Write(blockingChannel, "message");
        final Thread writeThread = new Thread(writeRunnable);
        writeThread.start();
        Thread.sleep(30);
        channelMock.enableWrite(true);
        writeThread.join();
        assertEquals(7, writeRunnable.getWriteResult());
        assertWrittenMessage(channelMock, "message");
    }

    @Test
    public void writeBlocksWithTimeout1() throws Exception {
        final T blockingChannel = createBlockingWritableByteChannel(channelMock, Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        final Write writeRunnable = new Write(blockingChannel, "j");
        final Thread writeThread = new Thread(writeRunnable);
        writeThread.start();
        Thread.sleep(50);
        channelMock.enableWrite(true);
        writeThread.join();
        assertEquals(1, writeRunnable.getWriteResult());
        assertWrittenMessage(channelMock, "j");
    }

    @Test
    public void writeBlocksUntilTimeout2() throws Exception {
        final T blockingChannel = createBlockingWritableByteChannel(channelMock, 0, TimeUnit.MILLISECONDS, 0, TimeUnit.MINUTES);
        final Write writeRunnable = new Write(blockingChannel, "");
        final Thread writeThread = new Thread(writeRunnable);
        writeThread.start();
        Thread.sleep(50);
        channelMock.enableWrite(true);
        writeThread.join();
        assertEquals(0, writeRunnable.getWriteResult());
        assertWrittenMessage(channelMock, "");
    }

    @Test
    public void writeBlocksUntilTimeout3() throws Exception {
        final T blockingChannel = createBlockingWritableByteChannel(channelMock, 0, TimeUnit.NANOSECONDS, 300000000, TimeUnit.NANOSECONDS);
        final Write writeRunnable = new Write(blockingChannel, "write... this");
        final Thread writeThread = new Thread(writeRunnable);
        writeThread.start();
        Thread.sleep(20);
        channelMock.enableWrite(true);
        writeThread.join();
        assertEquals(13, writeRunnable.getWriteResult());
        assertWrittenMessage(channelMock, "write... this");
    }

    @Test
    public void writeBlocksUntilTimeout4() throws Exception {
        final T blockingChannel = createBlockingWritableByteChannel(channelMock);
        setWriteTimeout(blockingChannel, 0, TimeUnit.DAYS);
        final Write writeRunnable = new Write(blockingChannel, "123456");
        final Thread writeThread = new Thread(writeRunnable);
        writeThread.start();
        Thread.sleep(50);
        channelMock.enableWrite(true);
        writeThread.join();
        assertEquals(6, writeRunnable.getWriteResult());
        assertWrittenMessage(channelMock, "123456");
    }

    @Test
    public void writeBlocksUntilTimeout5() throws Exception {
        final T blockingChannel = createBlockingWritableByteChannel(channelMock);
        setWriteTimeout(blockingChannel, 2, TimeUnit.MICROSECONDS);
        final Write writeRunnable = new Write(blockingChannel, "try with 1 microsecond");
        final Thread writeThread = new Thread(writeRunnable);
        writeThread.start();
        channelMock.enableWrite(true);
        writeThread.join();
        assertEquals(22, writeRunnable.getWriteResult());
        assertWrittenMessage(channelMock, "try with 1 microsecond");
    }

    @Test
    public void writeBlocksUntilTimeout6() throws Exception {
        final T blockingChannel = createBlockingWritableByteChannel(channelMock);
        setWriteTimeout(blockingChannel, 10, TimeUnit.MILLISECONDS);
        final Write writeRunnable = new Write(blockingChannel, "wait just 10 milliseconds");
        final Thread writeThread = new Thread(writeRunnable);
        writeThread.start();
        channelMock.enableWrite(true);
        writeThread.join();
        assertEquals(25, writeRunnable.getWriteResult());
        assertWrittenMessage(channelMock, "wait just 10 milliseconds");
    }

    @Test
    public void writeBlocksUntilTimeout7() throws Exception {
        final T blockingChannel = createBlockingWritableByteChannel(channelMock, 0, TimeUnit.MILLISECONDS);
        final Write writeRunnable = new Write(blockingChannel, "wait nothing");
        final Thread writeThread = new Thread(writeRunnable);
        writeThread.start();
        channelMock.enableWrite(true);
        writeThread.join();
        assertEquals(12, writeRunnable.getWriteResult());
        assertWrittenMessage(channelMock, "wait nothing");
    }

    @Test
    public void simpleWriteWithBufferArray() throws Exception {
        final T blockingChannel = createBlockingWritableByteChannel(channelMock);
        channelMock.enableWrite(true);
        final ByteBuffer[] buffer = new ByteBuffer[]{null, ByteBuffer.allocate(10), ByteBuffer.allocate(0)};
        buffer[1].put("write".getBytes("UTF-8")).flip();
        assertEquals(5, blockingChannel.write(buffer, 1, 2));
        assertWrittenMessage(channelMock, "write");
    }

    @Test
    public void simpleEmptyWriteWithBufferArray() throws Exception {
        final T blockingChannel = createBlockingWritableByteChannel(channelMock);
        channelMock.enableWrite(true);
        final ByteBuffer[] buffer = new ByteBuffer[]{ByteBuffer.allocate(10), ByteBuffer.allocate(0)};
        buffer[0].flip();
        buffer[1].flip();
        assertEquals(0, blockingChannel.write(buffer, 0, 1));
        assertWrittenMessage(channelMock);
    }

    @Test
    public void simpleWriteBufferArrayWithTimeou() throws Exception {
        final T blockingChannel = createBlockingWritableByteChannel(channelMock);
        setWriteTimeout(blockingChannel, 100, TimeUnit.MILLISECONDS);
        channelMock.enableWrite(true);
        final ByteBuffer[] buffer = new ByteBuffer[] {ByteBuffer.allocate(10), ByteBuffer.allocate(10), ByteBuffer.allocate(10)};
        buffer[0].put("write with".getBytes("UTF-8")).flip();
        buffer[1].put(" timeout".getBytes("UTF-8")).flip();
        buffer[2].flip();
        assertEquals(18, blockingChannel.write(buffer));
        assertWrittenMessage(channelMock, "write with timeout");
    }

    @Test
    public void simpleEmptyWriteWithBufferArrayWithTimeout() throws Exception {
        final T blockingChannel = createBlockingWritableByteChannel(channelMock);
        setWriteTimeout(blockingChannel, 5, TimeUnit.MILLISECONDS);
        channelMock.enableWrite(true);
        final ByteBuffer[] buffer = new ByteBuffer[]{ByteBuffer.allocate(10), ByteBuffer.allocate(0)};
        buffer[0].flip();
        buffer[1].flip();
        assertEquals(0, blockingChannel.write(buffer, 0, 1));
        assertWrittenMessage(channelMock);
    }

    @Test
    public void writeBufferArrayBlocks() throws Exception {
        final T blockingChannel = createBlockingWritableByteChannel(channelMock);
        final WriteBufferArray writeRunnable = new WriteBufferArray(blockingChannel, "mess", "age");
        final Thread writeThread = new Thread(writeRunnable);
        writeThread.start();
        Thread.sleep(30);
        channelMock.enableWrite(true);
        writeThread.join();
        assertEquals(7, writeRunnable.getWriteResult());
        assertWrittenMessage(channelMock, "message");
    }

    @Test
    public void writeBufferArrayBlocksWithTimeout1() throws Exception {
        final T blockingChannel = createBlockingWritableByteChannel(channelMock, 50000, TimeUnit.SECONDS);
        final WriteBufferArray writeRunnable = new WriteBufferArray(blockingChannel, "a");
        final Thread writeThread = new Thread(writeRunnable);
        writeThread.start();
        Thread.sleep(10);
        channelMock.enableWrite(true);
        writeThread.join();
        assertEquals(1, writeRunnable.getWriteResult());
        assertWrittenMessage(channelMock, "a");
    }

    @Test
    public void writeBufferArrayBlocksUntilTimeout2() throws Exception {
        final T blockingChannel = createBlockingWritableByteChannel(channelMock, 0, TimeUnit.MILLISECONDS, 0, TimeUnit.MINUTES);
        final WriteBufferArray writeRunnable = new WriteBufferArray(blockingChannel);
        final Thread writeThread = new Thread(writeRunnable);
        writeThread.start();
        Thread.sleep(50);
        channelMock.enableWrite(true);
        writeThread.join();
        assertEquals(0, writeRunnable.getWriteResult());
        assertWrittenMessage(channelMock, "");
    }

    @Test
    public void writeBufferArrayBlocksUntilTimeout3() throws Exception {
        final T blockingChannel = createBlockingWritableByteChannel(channelMock, 0, TimeUnit.NANOSECONDS, 30, TimeUnit.MINUTES);
        final WriteBufferArray writeRunnable = new WriteBufferArray(blockingChannel, "write", "anything", "...", "like", "this");
        final Thread writeThread = new Thread(writeRunnable);
        writeThread.start();
        Thread.sleep(5);
        channelMock.enableWrite(true);
        writeThread.join();
        assertEquals(24, writeRunnable.getWriteResult());
        assertWrittenMessage(channelMock, "write", "anything", "...", "like", "this");
    }

    @Test
    public void writeBuffeArrayBlocksUntilTimeout4() throws Exception {
        final T blockingChannel = createBlockingWritableByteChannel(channelMock);
        setWriteTimeout(blockingChannel, 0, TimeUnit.DAYS);
        final WriteBufferArray writeRunnable = new WriteBufferArray(blockingChannel, "78910");
        final Thread writeThread = new Thread(writeRunnable);
        writeThread.start();
        Thread.sleep(50);
        channelMock.enableWrite(true);
        writeThread.join();
        assertEquals(5, writeRunnable.getWriteResult());
        assertWrittenMessage(channelMock, "78910");
    }

    @Test
    public void writeBufferArrayBlocksUntilTimeout5() throws Exception {
        final T blockingChannel = createBlockingWritableByteChannel(channelMock);
        setWriteTimeout(blockingChannel, 20000, TimeUnit.MICROSECONDS);
        final WriteBufferArray writeRunnable = new WriteBufferArray(blockingChannel, "2", "microseconds");
        final Thread writeThread = new Thread(writeRunnable);
        writeThread.start();
        channelMock.enableWrite(true);
        writeThread.join();
        assertEquals(13, writeRunnable.getWriteResult());
        assertWrittenMessage(channelMock, "2", "microseconds");
    }

    @Test
    public void writeBufferArrayBlocksUntilTimeout6() throws Exception {
        final T blockingChannel = createBlockingWritableByteChannel(channelMock);
        setWriteTimeout(blockingChannel, 10, TimeUnit.MILLISECONDS);
        final WriteBufferArray writeRunnable = new WriteBufferArray(blockingChannel, "10", "milliseconds");
        final Thread writeThread = new Thread(writeRunnable);
        writeThread.start();
        channelMock.enableWrite(true);
        writeThread.join();
        assertEquals(14, writeRunnable.getWriteResult());
        assertWrittenMessage(channelMock, "10", "milliseconds");
    }

    @Test
    public void writeBufferArrayBlocksUntilTimeout7() throws Exception {
        final T blockingChannel = createBlockingWritableByteChannel(channelMock, 11000000, TimeUnit.NANOSECONDS);
        final WriteBufferArray writeRunnable = new WriteBufferArray(blockingChannel, "wait almost"," nothing");
        final Thread writeThread = new Thread(writeRunnable);
        writeThread.start();
        channelMock.enableWrite(true);
        writeThread.join();
        assertEquals(19, writeRunnable.getWriteResult());
        assertWrittenMessage(channelMock, "wait almost", " nothing");
    }

    // TODO wait until questions are cleared before doing this one
    public void writeTimeOut() {}

    @Test
    public void flush() throws IOException {
        channelMock.enableWrite(true);
        final T blockingChannel = createBlockingWritableByteChannel(channelMock);
        assertTrue(channelMock.isFlushed());
        blockingChannel.flush();
        assertTrue(channelMock.isFlushed());
        final ByteBuffer buffer = ByteBuffer.allocate(5);
        buffer.put("5".getBytes("UTF-8")).flip();
        assertEquals(1, blockingChannel.write(buffer));
        assertWrittenMessage(channelMock, "5");
        assertFalse(channelMock.isFlushed());
        blockingChannel.flush();
        assertTrue(channelMock.isFlushed());
    }

    @Test
    public void flushWithTimeout() throws IOException {
        channelMock.enableWrite(true);
        final T blockingChannel = createBlockingWritableByteChannel(channelMock);
        setWriteTimeout(blockingChannel, 500, TimeUnit.HOURS);
        assertTrue(channelMock.isFlushed());
        blockingChannel.flush();
        assertTrue(channelMock.isFlushed());
        final ByteBuffer buffer = ByteBuffer.allocate(5);
        buffer.put("500".getBytes("UTF-8")).flip();
        assertEquals(3, blockingChannel.write(buffer));
        assertWrittenMessage(channelMock, "500");
        assertFalse(channelMock.isFlushed());
        blockingChannel.flush();
        assertTrue(channelMock.isFlushed());
    }

    @Test
    public void flushBlocks() throws Exception {
        final T blockingChannel = createBlockingWritableByteChannel(channelMock);
        final Write writeRunnable = new Write(blockingChannel, "test", true);
        final Thread writeThread = new Thread(writeRunnable);
        writeThread.start();
        Thread.sleep(50);
        channelMock.enableFlush(false);
        channelMock.enableWrite(true);
        Thread.sleep(50);
        channelMock.enableFlush(true);
        writeThread.join();
        assertEquals(4, writeRunnable.getWriteResult());
        assertWrittenMessage(channelMock, "test");
        assertTrue(channelMock.isFlushed());
    }

    @Test
    public void flushWithTimeoutBlocks() throws Exception {
        channelMock.enableFlush(false);
        channelMock.enableWrite(true);
        final T blockingChannel = createBlockingWritableByteChannel(channelMock);
        setWriteTimeout(blockingChannel, 1, TimeUnit.HOURS);
        final Write writeRunnable = new Write(blockingChannel, "test2", true);
        final Thread writeThread = new Thread(writeRunnable);
        writeThread.start();
        Thread.sleep(50);
        channelMock.enableFlush(true);
        writeThread.join();
        assertEquals(5, writeRunnable.getWriteResult());
        assertWrittenMessage(channelMock, "test2");
        assertTrue(channelMock.isFlushed());
    }

    @Test
    public void illegalTimeout() throws Exception {
        boolean illegal = false;
        try {
            createBlockingWritableByteChannel(channelMock, 10, TimeUnit.MICROSECONDS, -3, TimeUnit.DAYS);
        } catch (IllegalArgumentException e) {
            illegal = true;
        }
        assertTrue(illegal);
        illegal = false;
        try {
            createBlockingWritableByteChannel(channelMock, -8, TimeUnit.SECONDS);
        } catch (IllegalArgumentException e) {
            illegal = true;
        }
        assertTrue(illegal);
        illegal = false;
        final T blockingChannel = createBlockingWritableByteChannel(channelMock);
        illegal = false;
        try {
            setWriteTimeout(blockingChannel, -20, TimeUnit.MINUTES);
        } catch (IllegalArgumentException e) {
            illegal = true;
        }
        assertTrue(illegal);
    }

    @Test
    public void close() throws IOException {
        final T blockingChannel = createBlockingWritableByteChannel(channelMock);
        assertTrue(channelMock.isOpen());
        assertTrue(blockingChannel.isOpen());
        blockingChannel.close();
        assertFalse(channelMock.isOpen());
        assertFalse(blockingChannel.isOpen());
    }

    private class Write implements Runnable {
        private final String message;
        private final T channel;
        private final boolean flush;
        private int writeResult;

        public Write(T c, String m) {
            this(c, m, false);
        }

        public Write(T c, String m, boolean f) {
            channel = c;
            message = m;
            flush = f;
        }

        @Override
        public void run() {
            ByteBuffer buffer = ByteBuffer.allocate(30);
            try {
                buffer.put(message.getBytes("UTF-8")).flip();
                writeResult = channel.write(buffer);
                if (flush) {
                    channel.flush();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public int getWriteResult() {
            return writeResult;
        }
    }

    private class WriteBufferArray implements Runnable {
        private final String[] message;
        private final T channel;
        private long writeResult;

        public WriteBufferArray(T c, String... m) {
            channel = c;
            message = m;
        }

        @Override
        public void run() {
            final ByteBuffer[] buffer = new ByteBuffer[message.length];
            try {
                for (int i = 0; i < buffer.length; i++) {
                    buffer[i] = ByteBuffer.allocate(message[i].length());
                    buffer[i].put(message[i].getBytes("UTF-8")).flip();
                }
                writeResult = channel.write(buffer);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public long getWriteResult() {
            return writeResult;
        }
    }
}
