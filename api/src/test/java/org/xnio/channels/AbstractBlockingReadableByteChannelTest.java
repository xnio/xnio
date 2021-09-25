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
import static org.xnio.AssertReadWrite.assertReadMessage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ScatteringByteChannel;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.xnio.mock.ConnectedStreamChannelMock;

/**
 * Abstract class that contains test cases applicable to any blocking {@code ScatteringByteChannel}.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
public abstract class AbstractBlockingReadableByteChannelTest<T extends ScatteringByteChannel> {
    private ConnectedStreamChannelMock channelMock;

    @Before
    public void initChannelMock() {
        channelMock = new ConnectedStreamChannelMock();
        channelMock.enableRead(true);
        channelMock.enableWrite(false);
    }

    protected abstract T createBlockingReadableByteChannel(ConnectedStreamChannelMock channelMock);
    protected abstract T createBlockingReadableByteChannel(ConnectedStreamChannelMock channelMock, long timeout, TimeUnit timeoutUnit);
    protected abstract T createBlockingReadableByteChannel(ConnectedStreamChannelMock channelMock, long readTimeout, TimeUnit readTimeoutUnit, long writeTimeout, TimeUnit writeTimeoutUnit);
    protected abstract void setReadTimeout(T channel, long readTimeout, TimeUnit readTimeoutUnit);

    @Test
    public void simpleRead() throws Exception {
        final T blockingChannel = createBlockingReadableByteChannel(channelMock);
        channelMock.setReadData("read");
        channelMock.setEof();
        final ByteBuffer buffer = ByteBuffer.allocate(10);
        assertEquals(4, blockingChannel.read(buffer));
        assertEquals(-1, blockingChannel.read(buffer));
        assertReadMessage(buffer, "read");
    }

    @Test
    public void readBlocksUntilReadDataAvailable() throws Exception {
        final T blockingChannel = createBlockingReadableByteChannel(channelMock);
        final Read readRunnable = new Read(blockingChannel);
        final Thread readThread = new Thread(readRunnable);
        readThread.start();
        Thread.sleep(30);
        channelMock.setReadData("read", "all", "this", "message");
        readThread.join();
        assertEquals(18, readRunnable.getReadResult());
        assertReadMessage(readRunnable.getReadBuffer(), "read", "all", "this", "message");
    }

    @Test
    public void readBlocksWithTimeout1() throws Exception {
        final T blockingChannel = createBlockingReadableByteChannel(channelMock, Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        final Read readRunnable = new Read(blockingChannel);
        final Thread readThread = new Thread(readRunnable);
        readThread.start();
        Thread.sleep(50);
        channelMock.setReadData("a");
        channelMock.setEof();
        readThread.join();
        assertEquals(1, readRunnable.getReadResult());
        assertReadMessage(readRunnable.getReadBuffer(), "a");
    }

    @Test
    public void readBlocksUntilTimeout2() throws Exception {
        final T blockingChannel = createBlockingReadableByteChannel(channelMock, 400, TimeUnit.MILLISECONDS, 300, TimeUnit.MINUTES);
        final Read readRunnable = new Read(blockingChannel);
        final Thread readThread = new Thread(readRunnable);
        readThread.start();
        Thread.sleep(50);
        channelMock.setEof();
        readThread.join();
        assertEquals(-1, readRunnable.getReadResult());
        assertReadMessage(readRunnable.getReadBuffer());
    }

    @Test
    public void readBlocksUntilTimeout3() throws Exception {
        final T blockingChannel = createBlockingReadableByteChannel(channelMock, 400000000, TimeUnit.NANOSECONDS, 300, TimeUnit.NANOSECONDS);
        final Read readRunnable = new Read(blockingChannel);
        final Thread readThread = new Thread(readRunnable);
        readThread.start();
        Thread.sleep(50);
        channelMock.setReadData("read ");
        channelMock.setEof();
        readThread.join();
        assertEquals(5, readRunnable.getReadResult());
        assertReadMessage(readRunnable.getReadBuffer(), "read ");
    }

    @Test
    public void readBlocksUntilTimeout4() throws Exception {
        final T blockingChannel = createBlockingReadableByteChannel(channelMock);
        setReadTimeout(blockingChannel, 0, TimeUnit.DAYS);
        final Read readRunnable = new Read(blockingChannel);
        final Thread readThread = new Thread(readRunnable);
        readThread.start();
        Thread.sleep(50);
        channelMock.setReadData("testing...");
        channelMock.setEof();
        readThread.join();
        assertEquals(10, readRunnable.getReadResult());
        assertReadMessage(readRunnable.getReadBuffer(), "testing...");
    }

    @Test
    public void readBlocksUntilTimeout5() throws Exception {
        final T blockingChannel = createBlockingReadableByteChannel(channelMock);
        setReadTimeout(blockingChannel, 1, TimeUnit.SECONDS);
        final Read readRunnable = new Read(blockingChannel);
        final Thread readThread = new Thread(readRunnable);
        readThread.start();
        Thread.sleep(50);
        channelMock.setReadData("try with 1 nanoseconds");
        channelMock.setEof();
        readThread.join();
        assertEquals(20, readRunnable.getReadResult());
        assertReadMessage(readRunnable.getReadBuffer(), "try with 1 nanosecon");
    }

    @Test
    public void readBlocksUntilTimeout6() throws Exception {
        final T blockingChannel = createBlockingReadableByteChannel(channelMock);
        setReadTimeout(blockingChannel, 1, TimeUnit.SECONDS);
        final Read readRunnable = new Read(blockingChannel);
        final Thread readThread = new Thread(readRunnable);
        readThread.start();
        channelMock.setReadData("wait a little longer now");
        channelMock.setEof();
        readThread.join();
        assertEquals(20, readRunnable.getReadResult());
        assertReadMessage(readRunnable.getReadBuffer(), "wait a little longer");
    }

    @Test
    public void readBlocksUntilTimeout7() throws Exception {
        final T blockingChannel = createBlockingReadableByteChannel(channelMock, 0, TimeUnit.SECONDS);
        final Read readRunnable = new Read(blockingChannel);
        final Thread readThread = new Thread(readRunnable);
        readThread.start();
        channelMock.setReadData("wait nothing");
        channelMock.setEof();
        readThread.join();
        assertEquals(12, readRunnable.getReadResult());
        assertReadMessage(readRunnable.getReadBuffer(), "wait nothing");
    }

    @Test
    public void simpleReadWithBufferArray() throws Exception {
        final T blockingChannel = createBlockingReadableByteChannel(channelMock);
        channelMock.setReadData("a-b-c");
        channelMock.setEof();
        final ByteBuffer buffer = ByteBuffer.allocate(10);
        assertEquals(5, blockingChannel.read(new ByteBuffer[] {buffer}));
        assertEquals(-1, blockingChannel.read(buffer));
        assertReadMessage(buffer, "a-b-c");
    }

    @Test
    public void readBlocksUntilReadDataAvailableWithByteArray() throws Exception {
        final T blockingChannel = createBlockingReadableByteChannel(channelMock);
        final ReadToBufferArray readRunnable = new ReadToBufferArray(blockingChannel);
        final Thread readThread = new Thread(readRunnable);
        readThread.start();
        Thread.sleep(30);
        channelMock.setReadData("read", "this", "all");
        readThread.join();
        assertEquals(11, readRunnable.getReadResult());
        final ByteBuffer[] readBuffer = readRunnable.getReadBuffer();
        assertReadMessage(readBuffer[0], "read", "t");
        assertReadMessage(readBuffer[1], "his", "al");
        assertReadMessage(readBuffer[2], "l");
        assertReadMessage(readBuffer[3]);
    }

    @Test
    public void readBlocksWithTimeout1WithByteArray() throws Exception {
        final T blockingChannel = createBlockingReadableByteChannel(channelMock, 5, TimeUnit.DAYS);
        final ReadToBufferArray readRunnable = new ReadToBufferArray(blockingChannel);
        final Thread readThread = new Thread(readRunnable);
        readThread.start();
        Thread.sleep(50);
        channelMock.setReadData("array");
        channelMock.setEof();
        readThread.join();
        assertEquals(5, readRunnable.getReadResult());
        final ByteBuffer[] readBuffer = readRunnable.getReadBuffer();
        assertReadMessage(readBuffer[0], "array");
        assertReadMessage(readBuffer[1]);
        assertReadMessage(readBuffer[2]);
        assertReadMessage(readBuffer[3]);
    }

    @Test
    public void readBlocksUntilTimeoutWithByteArray2() throws Exception {
        final T blockingChannel = createBlockingReadableByteChannel(channelMock, 509900, TimeUnit.MICROSECONDS, 300, TimeUnit.MINUTES);
        final ReadToBufferArray readRunnable = new ReadToBufferArray(blockingChannel);
        final Thread readThread = new Thread(readRunnable);
        readThread.start();
        Thread.sleep(50);
        channelMock.setEof();
        readThread.join();
        assertEquals(-1, readRunnable.getReadResult());
        final ByteBuffer[] readBuffer = readRunnable.getReadBuffer();
        assertReadMessage(readBuffer[0]);
        assertReadMessage(readBuffer[1]);
        assertReadMessage(readBuffer[2]);
        assertReadMessage(readBuffer[3]);
    }

    @Test
    public void readBlocksUntilTimeoutWithByteArray3() throws Exception {
        final T blockingChannel = createBlockingReadableByteChannel(channelMock, 100, TimeUnit.HOURS, 3, TimeUnit.NANOSECONDS);
        final ReadToBufferArray readRunnable = new ReadToBufferArray(blockingChannel);
        final Thread readThread = new Thread(readRunnable);
        readThread.start();
        Thread.sleep(50);
        channelMock.setReadData("time", "out", "!");
        channelMock.setEof();
        readThread.join();
        assertEquals(8, readRunnable.getReadResult());
        final ByteBuffer[] readBuffer = readRunnable.getReadBuffer();
        assertReadMessage(readBuffer[0], "time", "o");
        assertReadMessage(readBuffer[1], "ut", "!");
        assertReadMessage(readBuffer[2]);
        assertReadMessage(readBuffer[3]);
    }

    @Test
    public void readBlocksUntilTimeoutWithByteArray4() throws Exception {
        final T blockingChannel = createBlockingReadableByteChannel(channelMock);
        setReadTimeout(blockingChannel, 0, TimeUnit.HOURS);
        final ReadToBufferArray readRunnable = new ReadToBufferArray(blockingChannel);
        final Thread readThread = new Thread(readRunnable);
        readThread.start();
        Thread.sleep(50);
        channelMock.setReadData("1, 2, 3...");
        channelMock.setEof();
        readThread.join();
        assertEquals(10, readRunnable.getReadResult());
        final ByteBuffer[] readBuffer = readRunnable.getReadBuffer();
        assertReadMessage(readBuffer[0], "1, 2,");
        assertReadMessage(readBuffer[1], " 3...");
        assertReadMessage(readBuffer[2]);
        assertReadMessage(readBuffer[3]);
    }

    @Test
    public void readBlocksUntilTimeoutWithBytearray5() throws Exception {
        final T blockingChannel = createBlockingReadableByteChannel(channelMock);
        setReadTimeout(blockingChannel, 300000, TimeUnit.MICROSECONDS);
        final ReadToBufferArray readRunnable = new ReadToBufferArray(blockingChannel);
        final Thread readThread = new Thread(readRunnable);
        readThread.start();
        Thread.sleep(10);
        channelMock.setReadData("try with 3 microseconds");
        channelMock.setEof();
        readThread.join();
        assertEquals(20, readRunnable.getReadResult());
        final ByteBuffer[] readBuffer = readRunnable.getReadBuffer();
        assertReadMessage(readBuffer[0], "try w");
        assertReadMessage(readBuffer[1], "ith 3");
        assertReadMessage(readBuffer[2], " micr");
        assertReadMessage(readBuffer[3], "oseco");
    }

    @Test
    public void readBlocksUntilTimeoutWithByteArray6() throws Exception {
        final T blockingChannel = createBlockingReadableByteChannel(channelMock);
        setReadTimeout(blockingChannel, 100, TimeUnit.MILLISECONDS);
        final ReadToBufferArray readRunnable = new ReadToBufferArray(blockingChannel);
        final Thread readThread = new Thread(readRunnable);
        readThread.start();
        channelMock.setReadData("wait a little longer now");
        channelMock.setEof();
        readThread.join();
        assertEquals(20, readRunnable.getReadResult());
        final ByteBuffer[] readBuffer = readRunnable.getReadBuffer();
        assertReadMessage(readBuffer[0], "wait ");
        assertReadMessage(readBuffer[1], "a lit");
        assertReadMessage(readBuffer[2], "tle l");
        assertReadMessage(readBuffer[3], "onger");
    }

    // TODO wait until questions are cleared before doing this one
    public void readTimeOut() {}

    @Test
    public void illegalTimeout() throws Exception {
        boolean illegal = false;
        try {
            createBlockingReadableByteChannel(channelMock, -1, TimeUnit.HOURS, 10, TimeUnit.MICROSECONDS);
        } catch (IllegalArgumentException e) {
            illegal = true;
        }
        assertTrue(illegal);
        illegal = false;
        try {
            createBlockingReadableByteChannel(channelMock, -8, TimeUnit.SECONDS);
        } catch (IllegalArgumentException e) {
            illegal = true;
        }
        assertTrue(illegal);
        illegal = false;
        final T blockingChannel = createBlockingReadableByteChannel(channelMock);
        illegal = false;
        try {
            setReadTimeout(blockingChannel, -1000, TimeUnit.MILLISECONDS);
        } catch (IllegalArgumentException e) {
            illegal = true;
        }
        assertTrue(illegal);
    }

    @Test
    public void close() throws IOException {
        final T blockingChannel = createBlockingReadableByteChannel(channelMock);
        assertTrue(channelMock.isOpen());
        assertTrue(blockingChannel.isOpen());
        blockingChannel.close();
        assertFalse(channelMock.isOpen());
        assertFalse(blockingChannel.isOpen());
    }

    private class Read implements Runnable {
        private final ByteBuffer buffer;
        private final T channel;
        private int readResult;

        public Read(T c) {
            channel = c;
            buffer = ByteBuffer.allocate(20);
        }

        @Override
        public void run() {
            try {
                readResult = channel.read(buffer);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public int getReadResult() {
            return readResult;
        }

        public ByteBuffer getReadBuffer() {
            return buffer;
        }
    }

    private class ReadToBufferArray implements Runnable {
        private final ByteBuffer[] buffer;
        private final T channel;
        private long readResult;

        public ReadToBufferArray(T c) {
            channel = c;
            buffer = new ByteBuffer[] {ByteBuffer.allocate(5), ByteBuffer.allocate(5), ByteBuffer.allocate(5), ByteBuffer.allocate(5)};
        }

        @Override
        public void run() {
            try {
                readResult = channel.read(buffer);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public long getReadResult() {
            return readResult;
        }

        public ByteBuffer[] getReadBuffer() {
            return buffer;
        }
    }
}
