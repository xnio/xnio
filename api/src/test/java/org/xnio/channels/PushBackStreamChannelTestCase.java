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
import static org.xnio.AssertReadWrite.assertReadMessage;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.xnio.ByteBufferPool;
import org.xnio.ChannelListener;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.mock.ConnectedStreamChannelMock;

/**
 * Test for {@link PushBackStreamChannel}.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 *
 */
public class PushBackStreamChannelTestCase {
    private PushBackStreamChannel channel;
    private ConnectedStreamChannelMock firstChannel;

    @Before
    public void init() {
        firstChannel = new ConnectedStreamChannelMock();
        firstChannel.enableRead(true);
        channel = new PushBackStreamChannel(firstChannel);
    }

    @Test
    public void readToEmptyBuffer() throws IOException {
        final ByteBuffer emptyBuffer = ByteBuffer.allocate(0);
        assertEquals(0, channel.read(emptyBuffer));

        final ByteBufferPool messagePool = ByteBufferPool.SMALL_HEAP;
        final ByteBuffer messageBuffer = messagePool.allocate();
        messageBuffer.put("dummy".getBytes("UTF-8")).flip();
        channel.unget(messageBuffer);
        assertEquals(0, channel.read(emptyBuffer));

        firstChannel.setReadData("dummy");
        assertEquals(0, channel.read(emptyBuffer));
    }

    @Test
    public void readPushedMessage() throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocate(10);
        final ByteBufferPool messagePool = ByteBufferPool.SMALL_HEAP;
        final ByteBuffer messageBuffer = messagePool.allocate();
        messageBuffer.put("new read data".getBytes("UTF-8")).flip();

        assertEquals(0, channel.read(buffer));
        channel.unget(messageBuffer);
        assertEquals (10, channel.read(buffer));
        assertReadMessage(buffer, "new read d");

        buffer.clear();
        assertEquals (3, channel.read(buffer));
        assertReadMessage(buffer, "ata");
    }

    @Test
    public void readFirstChannelData() throws IOException {
        firstChannel.setReadData("data", "123");
        final ByteBuffer buffer = ByteBuffer.allocate(15);
        assertEquals(7, channel.read(buffer));
        assertReadMessage(buffer, "data", "123");
    }

    @Test
    public void readFirstChannelDataAndPushedMessage1() throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocate(15);
        final ByteBufferPool messagePool = ByteBufferPool.SMALL_HEAP;
        final ByteBuffer messageBuffer = messagePool.allocate();
        firstChannel.setReadData("E=");
        messageBuffer.put("mc".getBytes("UTF-8")).flip();
        assertEquals(2, channel.read(buffer));
        assertEquals(0, channel.read(buffer));
        firstChannel.setReadData("2");
        channel.unget(messageBuffer);
        assertEquals(2, channel.read(buffer));
        assertEquals(1, channel.read(buffer));
        assertEquals(0, channel.read(buffer));
        assertReadMessage(buffer, "E=mc2");
    }

    @Test
    public void readFirstChannelDataAndPushedMessage2() throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocate(15);
        final ByteBufferPool messagePool = ByteBufferPool.SMALL_HEAP;
        final ByteBuffer messageBuffer1 = messagePool.allocate();
        final ByteBuffer messageBuffer2 = messagePool.allocate();
        firstChannel.setReadData("E=");
        messageBuffer1.put("c".getBytes("UTF-8")).flip();
        messageBuffer2.put("m".getBytes("UTF-8")).flip();
        assertEquals(2, channel.read(buffer));
        assertEquals(0, channel.read(buffer));
        firstChannel.setReadData("2");
        channel.unget(messageBuffer1);
        channel.unget(messageBuffer2);
        assertEquals(2, channel.read(buffer));
        assertEquals(1, channel.read(buffer));
        assertReadMessage(buffer, "E=mc2");
    }

    @Test
    public void readToEmptyBufferArray() throws IOException {
        final ByteBuffer[] emptyBufferArray = new ByteBuffer[0];
        assertEquals(0, channel.read(emptyBufferArray));

        final ByteBufferPool messagePool = ByteBufferPool.SMALL_HEAP;
        final ByteBuffer messageBuffer = messagePool.allocate();
        messageBuffer.put("dummy".getBytes("UTF-8")).flip();
        channel.unget(messageBuffer);
        assertEquals(0, channel.read(emptyBufferArray));

        firstChannel.setReadData("dummy");
        assertEquals(0, channel.read(emptyBufferArray));
    }

    @Test
    public void readPushedMessageToByteArray() throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocate(15);
        final ByteBufferPool messagePool = ByteBufferPool.SMALL_HEAP;
        final ByteBuffer messageBuffer = messagePool.allocate();
        messageBuffer.put("pushed read data".getBytes("UTF-8")).flip();

        assertEquals(0, channel.read(new ByteBuffer[] {buffer}));
        channel.unget(messageBuffer);
        assertEquals (15, channel.read(new ByteBuffer[] {buffer}));
        assertReadMessage(buffer, "pushed read dat");
    }

    @Test
    public void readFirstChannelDataToByteArray() throws IOException {
        firstChannel.setReadData("data", "for", "array");
        final ByteBuffer[] buffer = new ByteBuffer[] {ByteBuffer.allocate(2), ByteBuffer.allocate(2),
                ByteBuffer.allocate(2), ByteBuffer.allocate(2), ByteBuffer.allocate(2), ByteBuffer.allocate(2),
                ByteBuffer.allocate(2)};
        assertEquals(12, channel.read(buffer));
        assertReadMessage(buffer[0], "da");
        assertReadMessage(buffer[1], "ta");
        assertReadMessage(buffer[2], "fo");
        assertReadMessage(buffer[3], "ra");
        assertReadMessage(buffer[4], "rr");
        assertReadMessage(buffer[5], "ay");
        assertReadMessage(buffer[6]);
    }

    @Test
    public void readFirstChannelDataAndPushedMessageToByteArray1() throws IOException {
        final ByteBuffer[] buffer = new ByteBuffer[] {ByteBuffer.allocate(3), ByteBuffer.allocate(10)};
        final ByteBufferPool messagePool = ByteBufferPool.SMALL_HEAP;
        final ByteBuffer messageBuffer = messagePool.allocate();
        firstChannel.setReadData("JBoss");
        messageBuffer.put("Xnio".getBytes("UTF-8")).flip();
        assertEquals(5, channel.read(buffer));
        assertEquals(0, channel.read(buffer));
        firstChannel.setReadData("Api");
        channel.unget(messageBuffer);
        assertEquals(4, channel.read(buffer));
        assertEquals(3, channel.read(buffer));
        assertReadMessage(buffer[0], "JBo");
        assertReadMessage(buffer[1], "ss", "Xnio", "Api");
    }

    @Test
    public void readFirstChannelDataAndPushedMessageToByteArray2() throws IOException {
        final ByteBuffer[] buffer = new ByteBuffer[] {ByteBuffer.allocate(3), ByteBuffer.allocate(10)};
        final ByteBufferPool messagePool = ByteBufferPool.SMALL_HEAP;
        final ByteBuffer messageBuffer1 = messagePool.allocate();
        final ByteBuffer messageBuffer2 = messagePool.allocate();
        firstChannel.setReadData("JBoss");
        messageBuffer1.put("io".getBytes("UTF-8")).flip();
        messageBuffer2.put("Xn".getBytes("UTF-8")).flip();
        assertEquals(5, channel.read(buffer));
        assertEquals(0, channel.read(buffer));
        firstChannel.setReadData("Api");
        channel.unget(messageBuffer1);
        channel.unget(messageBuffer2);
        assertEquals(4, channel.read(buffer));
        assertEquals(3, channel.read(buffer));
        assertReadMessage(buffer[0], "JBo");
        assertReadMessage(buffer[1], "ss", "Xnio", "Api");
    }

    @Test
    public void readPushedMessageToByteArrayWithOffset() throws IOException {
        final ByteBuffer[] buffer = new ByteBuffer[] {null, ByteBuffer.allocate(50)};
        final ByteBufferPool messagePool = ByteBufferPool.SMALL_HEAP;
        final ByteBuffer messageBuffer = messagePool.allocate();
        messageBuffer.put("read data for array with offset".getBytes("UTF-8")).flip();

        assertEquals(0, channel.read(buffer, 1, 1));
        channel.unget(messageBuffer);
        assertEquals (31, channel.read(buffer, 1, 2));
        assertReadMessage(buffer[1], "read data for array with offset");
    }

    @Test
    public void readFirstChannelDataToByteArrayWithOffset() throws IOException {
        firstChannel.setReadData("read to byte array [1]");
        final ByteBuffer[] buffer = new ByteBuffer[] {null, ByteBuffer.allocate(50)};

        assertEquals(22, channel.read(buffer, 1, 1));
        assertReadMessage(buffer[1], "read to byte array [1]");
    }

    @Test
    public void readFirstChannelDataAndPushedMessageToByteArrayWithOffset1() throws IOException {
        final ByteBuffer[] buffer = new ByteBuffer[] {ByteBuffer.allocate(20), ByteBuffer.allocate(20),
                ByteBuffer.allocate(20), ByteBuffer.allocate(20)};
        final ByteBufferPool messagePool = ByteBufferPool.SMALL_HEAP;
        final ByteBuffer messageBuffer = messagePool.allocate();
        firstChannel.setReadData("123456789");
        messageBuffer.put("10111213141516171819".getBytes("UTF-8")).flip();
        assertEquals(9, channel.read(buffer, 1, 2));
        assertEquals(0, channel.read(buffer, 1, 2));
        firstChannel.setReadData("20212223242526272829");
        channel.unget(messageBuffer);
        assertEquals(20, channel.read(buffer, 1, 2));
        assertEquals(11, channel.read(buffer, 1, 2));
        assertReadMessage(buffer[0]);
        assertReadMessage(buffer[1], "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "1");
        assertReadMessage(buffer[2], "5", "16", "17", "18", "19", "20", "21", "22", "23", "24", "2");
        assertReadMessage(buffer[3]);
    }

    @Test
    public void readFirstChannelDataAndPushedMessageToByteArrayWithOffset2() throws IOException {
        final ByteBuffer[] buffer = new ByteBuffer[] {ByteBuffer.allocate(20), ByteBuffer.allocate(20),
                ByteBuffer.allocate(20), ByteBuffer.allocate(20)};
        final ByteBufferPool messagePool = ByteBufferPool.SMALL_HEAP;
        final ByteBuffer messageBuffer1 = messagePool.allocate();
        final ByteBuffer messageBuffer2 = messagePool.allocate();
        firstChannel.setReadData("123456789");
        messageBuffer1.put("16171819".getBytes("UTF-8")).flip();
        messageBuffer2.put("101112131415".getBytes("UTF-8")).flip();
        assertEquals(9, channel.read(buffer, 1, 2));
        assertEquals(0, channel.read(buffer, 1, 2));
        firstChannel.setReadData("20212223242526272829");
        channel.unget(messageBuffer1);
        channel.unget(messageBuffer2);
        assertEquals(20, channel.read(buffer, 1, 2));
        assertEquals(11, channel.read(buffer, 1, 2));
        assertReadMessage(buffer[0]);
        assertReadMessage(buffer[1], "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "1");
        assertReadMessage(buffer[2], "5", "16", "17", "18", "19", "20", "21", "22", "23", "24", "2");
        assertReadMessage(buffer[3]);
    }

    @Test
    public void transferPushedMessageToFileChannel() throws IOException {
        final ByteBufferPool messagePool = ByteBufferPool.SMALL_HEAP;
        final ByteBuffer messageBuffer = messagePool.allocate();
        messageBuffer.put("pushed message".getBytes("UTF-8")).flip();
        final File file = File.createTempFile("test", ".txt");
        file.deleteOnExit();
        final FileChannel fileChannel = new RandomAccessFile(file, "rw").getChannel();
        try {
            assertEquals(0, channel.transferTo(0, 6, fileChannel));
            channel.unget(messageBuffer);
            assertEquals (6, channel.transferTo(0, 6, fileChannel));
            fileChannel.position(0);
            final ByteBuffer transferedMessage = ByteBuffer.allocate(6);
            assertEquals(6, fileChannel.read(transferedMessage));
            assertReadMessage(transferedMessage, "pushed");
        } finally {
            fileChannel.close();
        }
    }

    @Test
    public void transferFirstChannelDataToFileChannel() throws IOException {
        firstChannel.setReadData("data", "from", "first", "channel");
        final File file = File.createTempFile("test", ".txt");
        file.deleteOnExit();
        final FileChannel fileChannel = new RandomAccessFile(file, "rw").getChannel();
        try {
            assertEquals(20, channel.transferTo(0, 30, fileChannel));
            fileChannel.position(0);
            final ByteBuffer transferedMessage = ByteBuffer.allocate(30);
            assertEquals(20, fileChannel.read(transferedMessage));
            assertReadMessage(transferedMessage, "data", "from", "first", "channel");
        } finally {
            fileChannel.close();
        }
    }

    @Test
    public void transferFirstChannelDataAndPushedMessageToFileChannel1() throws IOException {
        firstChannel.setReadData("x");
        final ByteBufferPool messagePool = ByteBufferPool.SMALL_HEAP;
        final ByteBuffer messageBuffer = messagePool.allocate();
        messageBuffer.put("nio".getBytes("UTF-8")).flip();
        final File file = File.createTempFile("test", ".txt");
        file.deleteOnExit();
        final FileChannel fileChannel = new RandomAccessFile(file, "rw").getChannel();
        try {
            assertEquals(1, channel.transferTo(0, 6, fileChannel));
            assertEquals(0, channel.transferTo(1, 6, fileChannel));
            firstChannel.setReadData("-api");
            channel.unget(messageBuffer);
            assertEquals (6, channel.transferTo(1, 6, fileChannel));
            assertEquals (1, channel.transferTo(7, 6, fileChannel));
            fileChannel.position(0);
            final ByteBuffer transferedMessage = ByteBuffer.allocate(10);
            assertEquals(8, fileChannel.read(transferedMessage));
            assertReadMessage(transferedMessage, "xnio-api");
        } finally {
            fileChannel.close();
        }
    }

    @Test
    public void transferFirstChannelDataAndPushedMessageToFileChannel2() throws IOException {
        firstChannel.setReadData("x");
        final ByteBufferPool messagePool = ByteBufferPool.SMALL_HEAP;
        final ByteBuffer messageBuffer1 = messagePool.allocate();
        messageBuffer1.put("io".getBytes("UTF-8")).flip();
        final ByteBuffer messageBuffer2 = messagePool.allocate();
        messageBuffer2.put("n".getBytes("UTF-8")).flip();
        final File file = File.createTempFile("test", ".txt");
        file.deleteOnExit();
        final FileChannel fileChannel = new RandomAccessFile(file, "rw").getChannel();
        try {
            assertEquals(1, channel.transferTo(0, 6, fileChannel));
            assertEquals(0, channel.transferTo(1, 6, fileChannel));
            firstChannel.setReadData("-api");
            channel.unget(messageBuffer1);
            channel.unget(messageBuffer2);
            assertEquals (6, channel.transferTo(1, 6, fileChannel));
            assertEquals (1, channel.transferTo(7, 6, fileChannel));
            fileChannel.position(0);
            final ByteBuffer transferedMessage = ByteBuffer.allocate(10);
            assertEquals(8, fileChannel.read(transferedMessage));
            assertReadMessage(transferedMessage, "xnio-api");
        } finally {
            fileChannel.close();
        }
    }

    @Test
    public void transferToStreamSinkChannel() throws Exception {
        final ByteBufferPool messagePool = ByteBufferPool.SMALL_HEAP;
        final ByteBuffer messageBuffer = messagePool.allocate();
        messageBuffer.put("pushed message".getBytes("UTF-8")).flip();
        final ConnectedStreamChannelMock sinkChannel = new ConnectedStreamChannelMock();

        assertEquals(0, channel.transferTo(15, ByteBuffer.allocate(60), sinkChannel));
        channel.unget(messageBuffer);
        assertEquals(14, channel.transferTo(15, ByteBuffer.allocate(60), sinkChannel));
        assertReadMessage(sinkChannel.getWrittenBytes(), "pushed", " ", "message");
    }

    @Test
    public void transferFirstChannelDataToSinkChannel() throws IOException {
        firstChannel.setReadData("data!", "from@", "first#", "channel$");
        final ConnectedStreamChannelMock sinkChannel = new ConnectedStreamChannelMock();
        assertEquals(24, channel.transferTo(30, ByteBuffer.allocate(60), sinkChannel));
        assertReadMessage(sinkChannel.getWrittenBytes(), "data!", "from@", "first#", "channel$");
    }

    @Test
    public void transferFirstChannelDataAndPushedMessageToSinkChannel1() throws IOException {
        firstChannel.setReadData("1+");
        final ByteBufferPool messagePool = ByteBufferPool.SMALL_HEAP;
        final ByteBuffer messageBuffer = messagePool.allocate();
        messageBuffer.put("2=".getBytes("UTF-8")).flip();
        final ConnectedStreamChannelMock sinkChannel = new ConnectedStreamChannelMock();
        final ByteBuffer throughBuffer = ByteBuffer.allocate(10);
        assertEquals(1, channel.transferTo(1, throughBuffer, sinkChannel));
        assertEquals(1, channel.transferTo(1, throughBuffer, sinkChannel));
        assertEquals(0, channel.transferTo(1, throughBuffer, sinkChannel));
        firstChannel.setReadData("3");
        channel.unget(messageBuffer);
        assertEquals (1, channel.transferTo(1, throughBuffer, sinkChannel));
        assertEquals (1, channel.transferTo(1, throughBuffer, sinkChannel));
        assertEquals (1, channel.transferTo(1, throughBuffer, sinkChannel));
        assertEquals (0, channel.transferTo(1, throughBuffer, sinkChannel));
        assertReadMessage(sinkChannel.getWrittenBytes(), "1+2=3");
    }

    @Test
    public void transferFirstChannelDataAndPushedMessageToSinkChannel2() throws IOException {
        firstChannel.setReadData("1+");
        final ByteBufferPool messagePool = ByteBufferPool.SMALL_HEAP;
        final ByteBuffer messageBuffer1 = messagePool.allocate();
        messageBuffer1.put("30".getBytes("UTF-8")).flip();
        final ByteBuffer messageBuffer2 = messagePool.allocate();
        messageBuffer2.put("2+".getBytes("UTF-8")).flip();
        final ConnectedStreamChannelMock sinkChannel = new ConnectedStreamChannelMock();
        final ByteBuffer throughBuffer = ByteBuffer.allocate(10);
        assertEquals(1, channel.transferTo(1, throughBuffer, sinkChannel));
        assertEquals(1, channel.transferTo(1, throughBuffer, sinkChannel));
        assertEquals(0, channel.transferTo(1, throughBuffer, sinkChannel));
        firstChannel.setReadData("=33");
        channel.unget(messageBuffer1);
        channel.unget(messageBuffer2);
        assertEquals (3, channel.transferTo(3, throughBuffer, sinkChannel));
        assertEquals (3, channel.transferTo(3, throughBuffer, sinkChannel));
        assertEquals (1, channel.transferTo(3, throughBuffer, sinkChannel));
        assertEquals (0, channel.transferTo(3, throughBuffer, sinkChannel));
        assertReadMessage(sinkChannel.getWrittenBytes(), "1+2+30=33");
    }

    @Test
    public void suspendResumeReads() throws Exception {
        channel.resumeReads();
        assertTrue(firstChannel.isReadResumed());
        Thread t = new Thread(new Runnable() {
            public void run() {
                firstChannel.setReadData("test");
            }
        });
        t.start();
        channel.awaitReadable();
        t.join();

        channel.suspendReads();
        assertFalse(firstChannel.isReadResumed());
        t = new Thread(new Runnable() {
            public void run() {
                channel.resumeReads();
            }
        });
        t.start();
        channel.awaitReadable(10, TimeUnit.SECONDS);
        t.join();

        // push data
        final ByteBufferPool messagePool = ByteBufferPool.SMALL_HEAP;
        final ByteBuffer messageBuffer = messagePool.allocate();
        messageBuffer.put("pushed message".getBytes("UTF-8")).flip();
        channel.unget(messageBuffer);

        channel.resumeReads();
        assertTrue(firstChannel.isReadResumed());
        channel.awaitReadable();
        channel.suspendReads();
        assertFalse(firstChannel.isReadResumed());
        channel.awaitReadable(10, TimeUnit.DAYS);
    }

    @Test
    public void shutdownReads() throws IOException {
        channel.shutdownReads();
        assertTrue(firstChannel.isShutdownReads());
        // shutdownReads is idempotent
        channel.shutdownReads();
        assertTrue(firstChannel.isShutdownReads());
        // cannot unget after shutdown
        final ByteBufferPool messagePool = ByteBufferPool.SMALL_HEAP;
        final ByteBuffer messageBuffer = messagePool.allocate();
        boolean bufferCleared = false;
        messageBuffer.put("a".getBytes("UTF-8")).flip();
        channel.unget(messageBuffer);
        // cannot read
        final ByteBuffer buffer = ByteBuffer.allocate(30);
        assertEquals(-1, channel.read(buffer));
        assertEquals(-1, channel.read(new ByteBuffer[] {buffer}, 0, 1));
        // cannot transfer
        final File file = File.createTempFile("test", ".txt");
        file.deleteOnExit();
        final FileChannel fileChannel = new RandomAccessFile(file, "rw").getChannel();
        try {
            assertEquals(0, channel.transferTo(0, 10, null));
        } finally {
            fileChannel.close();
        }
        assertEquals(-1L, channel.transferTo(10, ByteBuffer.allocate(10), new ConnectedStreamChannelMock()));
    }

    @Test
    public void shutdownReadsWithPushedData() throws IOException {
        final ByteBufferPool messagePool = ByteBufferPool.SMALL_HEAP;
        final ByteBuffer messageBuffer1 = messagePool.allocate();
        messageBuffer1.put("1".getBytes("UTF-8")).flip();
        channel.unget(messageBuffer1);
        final ByteBuffer messageBuffer2 = messagePool.allocate();
        messageBuffer2.put("2".getBytes("UTF-8")).flip();
        channel.unget(messageBuffer2);
        channel.shutdownReads();
        assertTrue(firstChannel.isShutdownReads());
        // shutdownReads is idempotent
        channel.shutdownReads();
        assertTrue(firstChannel.isShutdownReads());
        // cannot unget after shutdown
        final ByteBuffer messageBuffer3 = messagePool.allocate();
        boolean bufferCleared = false;
        messageBuffer3.put("3".getBytes("UTF-8")).flip();
        channel.unget(messageBuffer3);
        // cannot read
        final ByteBuffer buffer = ByteBuffer.allocate(30);
        assertEquals(-1, channel.read(buffer));
        assertEquals(-1, channel.read(new ByteBuffer[] {buffer}, 0, 1));
        // cannot transfer
        final File file = File.createTempFile("test", ".txt");
        file.deleteOnExit();
        final FileChannel fileChannel = new RandomAccessFile(file, "rw").getChannel();
        try {
            assertEquals(0, channel.transferTo(0, 10, null));
        } finally {
            fileChannel.close();
        }
        assertEquals(-1L, channel.transferTo(10, ByteBuffer.allocate(10), new ConnectedStreamChannelMock()));
    }

    @Test
    public void closeEmptyChannel() throws IOException {
        channel.close();
        assertFalse(channel.isOpen());
        assertFalse(firstChannel.isOpen());
        // close is idempotent
        channel.close();
        assertFalse(channel.isOpen());
        assertFalse(firstChannel.isOpen());
        // close is idempotent
        channel.close();
        assertFalse(channel.isOpen());
        assertFalse(firstChannel.isOpen());
        // cannot unget after closed
        final ByteBufferPool messagePool = ByteBufferPool.SMALL_HEAP;
        final ByteBuffer messageBuffer = messagePool.allocate();
        boolean bufferCleared = false;
        messageBuffer.put("a".getBytes("UTF-8")).flip();
        channel.unget(messageBuffer);
        // cannot read
        final ByteBuffer buffer = ByteBuffer.allocate(30);
        assertEquals(-1, channel.read(buffer));
        assertEquals(-1, channel.read(new ByteBuffer[] {buffer}));
        assertEquals(-1, channel.read(new ByteBuffer[] {buffer}, 0, 1));
        // cannot transfer
        final File file = File.createTempFile("test", ".txt");
        file.deleteOnExit();
        final FileChannel fileChannel = new RandomAccessFile(file, "rw").getChannel();
        try {
            assertEquals(0, channel.transferTo(0, 10, null));
        } finally {
            fileChannel.close();
        }
        assertEquals(-1L, channel.transferTo(10, ByteBuffer.allocate(10), new ConnectedStreamChannelMock()));
        // await readable does nothing
        channel.awaitReadable();
        channel.awaitReadable(1, TimeUnit.DAYS);
    }

    @Test
    public void closeChannelWithPushedData() throws IOException {
        final ByteBufferPool messagePool = ByteBufferPool.SMALL_HEAP;
        final ByteBuffer messageBuffer1 = messagePool.allocate();
        messageBuffer1.put("1".getBytes("UTF-8")).flip();
        channel.unget(messageBuffer1);
        final ByteBuffer messageBuffer2 = messagePool.allocate();
        messageBuffer2.put("2".getBytes("UTF-8")).flip();
        channel.unget(messageBuffer2);
        channel.close();
        assertFalse(channel.isOpen());
        assertFalse(firstChannel.isOpen());
        // close is idempotent
        channel.close();
        assertFalse(channel.isOpen());
        assertFalse(firstChannel.isOpen());
        // cannot unget after closed
        final ByteBuffer messageBuffer3 = messagePool.allocate();
        boolean bufferCleared = false;
        messageBuffer3.put("3".getBytes("UTF-8")).flip();
        channel.unget(messageBuffer3);
        // cannot read
        final ByteBuffer buffer = ByteBuffer.allocate(30);
        assertEquals(-1, channel.read(buffer));
        assertEquals(-1, channel.read(new ByteBuffer[] {buffer}));
        assertEquals(-1, channel.read(new ByteBuffer[] {buffer}, 0, 1));
        // cannot transfer
        final File file = File.createTempFile("test", ".txt");
        file.deleteOnExit();
        final FileChannel fileChannel = new RandomAccessFile(file, "rw").getChannel();
        try {
            assertEquals(0, channel.transferTo(0, 10, null));
        } finally {
            fileChannel.close();
        }
        assertEquals(-1L, channel.transferTo(10, ByteBuffer.allocate(10), new ConnectedStreamChannelMock()));
        // await readable does nothing
        channel.awaitReadable();
        channel.awaitReadable(1, TimeUnit.DAYS);
    }

    @Test
    public void delegateOperationsToFirstChannel() throws IOException {
        assertSame(firstChannel, channel.getChannel());
        channel.resumeReads();
        assertTrue(firstChannel.isReadResumed());
        assertTrue(channel.isReadResumed());
        channel.suspendReads();
        assertFalse(firstChannel.isReadResumed());
        assertFalse(channel.isReadResumed());
        channel.wakeupReads();
        assertTrue(firstChannel.isReadAwaken());
        assertTrue(channel.isReadResumed());
        assertSame(firstChannel.getReadThread(), channel.getReadThread());
        assertSame(firstChannel.getWorker(), channel.getWorker());
        assertSame(firstChannel.isOpen(), channel.isOpen());

        firstChannel.setOptionMap(OptionMap.create(Options.KEEP_ALIVE, true, Options.READ_TIMEOUT, 3000));
        assertSame(firstChannel.supportsOption(Options.BACKLOG), channel.supportsOption(Options.BACKLOG));
        assertSame(firstChannel.supportsOption(Options.KEEP_ALIVE), channel.supportsOption(Options.KEEP_ALIVE));
        assertSame(firstChannel.supportsOption(Options.READ_TIMEOUT), channel.supportsOption(Options.READ_TIMEOUT));

        assertSame(firstChannel.getOption(Options.BACKLOG), channel.getOption(Options.BACKLOG));
        assertSame(firstChannel.getOption(Options.KEEP_ALIVE), channel.getOption(Options.KEEP_ALIVE));
        assertSame(firstChannel.getOption(Options.READ_TIMEOUT), channel.getOption(Options.READ_TIMEOUT));

        channel.setOption(Options.BACKLOG, 5000);
        assertEquals(5000, (int) firstChannel.getOption(Options.BACKLOG));
        assertEquals(5000, (int) channel.getOption(Options.BACKLOG));
    }

    @Test
    public void getListenerSetter() {
        final DummyListener readListener = new DummyListener();
        final DummyListener closeListener = new DummyListener();
        channel.getReadSetter().set(readListener);
        channel.getCloseSetter().set(closeListener);

        assertFalse(readListener.isInvoked());
        firstChannel.getReadListener().handleEvent(firstChannel);
        assertTrue(readListener.isInvoked());

        assertFalse(closeListener.isInvoked());
        firstChannel.getCloseListener().handleEvent(firstChannel);
        assertTrue(closeListener.isInvoked());
    }

    private static class  DummyListener implements ChannelListener<PushBackStreamChannel> {

        private boolean invoked = false;

        @Override
        public void handleEvent(PushBackStreamChannel channel) {
            invoked = true;
        }

        public boolean isInvoked() {
            return invoked;
        }
    };
}
