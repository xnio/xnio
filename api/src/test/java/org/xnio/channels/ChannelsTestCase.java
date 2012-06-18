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
package org.xnio.channels;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.xnio.AssertReadWrite.assertReadMessage;
import static org.xnio.AssertReadWrite.assertWrittenMessage;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.xnio.Buffers;
import org.xnio.ChannelListener;
import org.xnio.Option;
import org.xnio.Options;
import org.xnio.mock.AcceptingChannelMock;
import org.xnio.mock.ConnectedStreamChannelMock;
import org.xnio.mock.MessageChannelMock;

/**
 * Test for {@link Channels}.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
public class ChannelsTestCase {

    private ConnectedStreamChannelMock connectedChannelMock;
    private MessageChannelMock messageChannelMock;

    @Before
    public void init() {
        connectedChannelMock = new ConnectedStreamChannelMock();
        messageChannelMock = new MessageChannelMock(connectedChannelMock);
    }

    @Test
    public void flushBlocking() throws IOException, InterruptedException {
        assertTrue(connectedChannelMock.isFlushed());
        Channels.flushBlocking(connectedChannelMock);
        assertTrue(connectedChannelMock.isFlushed());
        connectedChannelMock.enableFlush(false);
        ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put("10".getBytes("UTF-8")).flip();
        assertEquals(2, connectedChannelMock.write(buffer));
        assertWrittenMessage(connectedChannelMock, "10");
        assertFalse(connectedChannelMock.isFlushed());
        FlushBlocking flushRunnable = new FlushBlocking(connectedChannelMock);
        Thread flushThread = new Thread(flushRunnable);
        flushThread.start();
        flushThread.join(50);
        assertTrue(flushThread.isAlive());
        Thread.sleep(100);
        connectedChannelMock.enableFlush(true);
        flushThread.join();
        assertFalse(flushThread.isAlive());
    }

    @Test
    public void shutdownWritesBlocking() throws IOException, InterruptedException {
        connectedChannelMock.enableFlush(false);
        ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put("shutdown".getBytes("UTF-8")).flip();
        assertEquals(8, connectedChannelMock.write(buffer));
        assertWrittenMessage(connectedChannelMock, "shutdown");
        assertFalse(connectedChannelMock.isShutdownWrites());
        assertFalse(connectedChannelMock.isFlushed());
        ShutdownWritesBlocking shutdownWritesRunnable = new ShutdownWritesBlocking(connectedChannelMock);
        Thread shutdownThread = new Thread(shutdownWritesRunnable);
        shutdownThread.start();
        shutdownThread.join(50);
        assertTrue(shutdownThread.isAlive());
        Thread.sleep(100);
        connectedChannelMock.enableFlush(true);
        shutdownThread.join();
        assertFalse(shutdownThread.isAlive());
        assertTrue(connectedChannelMock.isShutdownWrites());
        assertTrue(connectedChannelMock.isFlushed());
    }

    @Test
    public void writeBlocking() throws IOException, InterruptedException {
        connectedChannelMock.enableWrite(false);
        WriteBlocking writeRunnable = new WriteBlocking(connectedChannelMock, "write this");
        Thread writeThread = new Thread(writeRunnable);
        writeThread.start();
        writeThread.join(50);
        assertTrue(writeThread.isAlive());
        Thread.sleep(200);
        connectedChannelMock.enableWrite(true);
        writeThread.join();
        assertFalse(writeThread.isAlive());
        assertEquals(10, writeRunnable.getWriteResult());
        assertWrittenMessage(connectedChannelMock, "write this");
    }

    @Test
    public void writeBlockingWithTimeout() throws IOException, InterruptedException {
        connectedChannelMock.enableWrite(false);
        WriteBlocking writeRunnable = new WriteBlocking(connectedChannelMock, "write with timeout", 1000, TimeUnit.MICROSECONDS);
        Thread writeThread = new Thread(writeRunnable);
        writeThread.start();
        writeThread.join();
        assertWrittenMessage(connectedChannelMock);
        connectedChannelMock.enableWrite(true);
        writeThread = new Thread(writeRunnable);
        writeThread.start();
        writeThread.join();
        assertEquals(18, writeRunnable.getWriteResult());
        assertWrittenMessage(connectedChannelMock, "write with timeout");
    }

    @Test
    public void writeBufferArrayBlocking() throws IOException, InterruptedException {
        connectedChannelMock.enableWrite(false);
        WriteBufferArrayBlocking writeRunnable = new WriteBufferArrayBlocking(connectedChannelMock, "write", " this");
        Thread writeThread = new Thread(writeRunnable);
        writeThread.start();
        writeThread.join(50);
        assertTrue(writeThread.isAlive());
        Thread.sleep(200);
        connectedChannelMock.enableWrite(true);
        writeThread.join();
        assertFalse(writeThread.isAlive());
        assertEquals(10, writeRunnable.getWriteResult());
        assertWrittenMessage(connectedChannelMock, "write this");
    }

    @Test
    public void writeBufferArrayBlockingWithTimeout() throws IOException, InterruptedException {
        connectedChannelMock.enableWrite(false);
        WriteBufferArrayBlocking writeRunnable = new WriteBufferArrayBlocking(connectedChannelMock, 1000,
                TimeUnit.MILLISECONDS, "write", "with", "timeout");
        Thread writeThread = new Thread(writeRunnable);
        writeThread.start();
        writeThread.join();
        assertWrittenMessage(connectedChannelMock);
        connectedChannelMock.enableWrite(true);
        writeThread = new Thread(writeRunnable);
        writeThread.start();
        writeThread.join();
        assertEquals(16, writeRunnable.getWriteResult());
        assertWrittenMessage(connectedChannelMock, "write", "with", "timeout");
    }

    @Test
    public void sendBlocking() throws IOException, InterruptedException {
        connectedChannelMock.enableWrite(false);
        SendBlocking sendRunnable = new SendBlocking(messageChannelMock, "send this");
        Thread sendThread = new Thread(sendRunnable);
        sendThread.start();
        sendThread.join(50);
        assertTrue(sendThread.isAlive());
        Thread.sleep(200);
        connectedChannelMock.enableWrite(true);
        sendThread.join();
        assertFalse(sendThread.isAlive());
        assertWrittenMessage(connectedChannelMock, "send this");
    }

    @Test
    public void sendBlockingWithTimeout() throws IOException, InterruptedException {
        connectedChannelMock.enableWrite(false);
        SendBlocking sendRunnable = new SendBlocking(messageChannelMock, "send with timeout", 1000, TimeUnit.MICROSECONDS);
        Thread sendThread = new Thread(sendRunnable);
        sendThread.start();
        sendThread.join();
        assertFalse(sendRunnable.getSendResult());
        assertWrittenMessage(connectedChannelMock);
        connectedChannelMock.enableWrite(true);
        sendThread = new Thread(sendRunnable);
        sendThread.start();
        sendThread.join();
        assertTrue(sendRunnable.getSendResult());
        assertWrittenMessage(connectedChannelMock, "send with timeout");
    }

    @Test
    public void sendBufferArrayBlocking() throws IOException, InterruptedException {
        connectedChannelMock.enableWrite(false);
        SendBufferArrayBlocking sendRunnable = new SendBufferArrayBlocking(messageChannelMock, "send", " this");
        Thread sendThread = new Thread(sendRunnable);
        sendThread.start();
        sendThread.join(50);
        assertTrue(sendThread.isAlive());
        Thread.sleep(200);
        connectedChannelMock.enableWrite(true);
        sendThread.join();
        assertFalse(sendThread.isAlive());
        assertWrittenMessage(connectedChannelMock, "send this");
    }

    @Test
    public void sendBufferArrayBlockingWithTimeout() throws IOException, InterruptedException {
        connectedChannelMock.enableWrite(false);
        SendBufferArrayBlocking sendRunnable = new SendBufferArrayBlocking(messageChannelMock, 1000,
                TimeUnit.MILLISECONDS, "send", "with", "timeout");
        Thread sendThread = new Thread(sendRunnable);
        sendThread.start();
        sendThread.join();
        assertFalse(sendRunnable.getSendResult());
        assertWrittenMessage(connectedChannelMock);
        connectedChannelMock.enableWrite(true);
        sendThread = new Thread(sendRunnable);
        sendThread.start();
        sendThread.join();
        assertTrue(sendRunnable.getSendResult());
        assertWrittenMessage(connectedChannelMock, "send", "with", "timeout");
    }

    @Test
    public void readBlocking() throws IOException, InterruptedException {
        connectedChannelMock.setReadData("read this");
        ReadBlocking readRunnable = new ReadBlocking(connectedChannelMock);
        Thread readThread = new Thread(readRunnable);
        readThread.start();
        readThread.join(50);
        assertTrue(readThread.isAlive());
        Thread.sleep(200);
        connectedChannelMock.enableRead(true);
        readThread.join();
        assertFalse(readThread.isAlive());
        assertEquals(9, readRunnable.getReadResult());
        assertReadMessage(readRunnable.getReadBuffer(), "read this");
    }

    @Test
    public void readBlockingToEmptyBuffer() throws IOException, InterruptedException {
        connectedChannelMock.setReadData("can't read this");
        ReadBlocking readRunnable = new ReadBlocking(connectedChannelMock, Buffers.EMPTY_BYTE_BUFFER);
        Thread readThread = new Thread(readRunnable);
        readThread.start();
        readThread.join();
        assertFalse(readThread.isAlive());
        assertEquals(0, readRunnable.getReadResult());
    }

    @Test
    public void readBlockingWithTimeout() throws IOException, InterruptedException {
        connectedChannelMock.setReadData("read with timeout");
        ReadBlocking readRunnable = new ReadBlocking(connectedChannelMock, 100, TimeUnit.MILLISECONDS);
        Thread readThread = new Thread(readRunnable);
        readThread.start();
        readThread.join();
        assertEquals(0, readRunnable.getReadResult());
        connectedChannelMock.enableRead(true);
        readThread = new Thread(readRunnable);
        readThread.start();
        readThread.join();
        assertEquals(17, readRunnable.getReadResult());
        assertReadMessage(readRunnable.getReadBuffer(), "read with timeout");
    }

    @Test
    public void readBlockingWithTimeoutToEmptyBuffer() throws IOException, InterruptedException {
        connectedChannelMock.setReadData("can't read this");
        ReadBlocking readRunnable = new ReadBlocking(connectedChannelMock, 100, TimeUnit.MILLISECONDS, Buffers.EMPTY_BYTE_BUFFER);
        Thread readThread = new Thread(readRunnable);
        readThread.start();
        readThread.join();
        assertEquals(0, readRunnable.getReadResult());
        connectedChannelMock.enableRead(true);
        readThread = new Thread(readRunnable);
        readThread.start();
        readThread.join();
        assertEquals(0, readRunnable.getReadResult());
    }

    @Test
    public void readBlockingToBufferArray() throws IOException, InterruptedException {
        connectedChannelMock.setReadData("read", "this");
        ReadToBufferArrayBlocking readRunnable = new ReadToBufferArrayBlocking(connectedChannelMock);
        Thread readThread = new Thread(readRunnable);
        readThread.start();
        readThread.join(50);
        assertTrue(readThread.isAlive());
        Thread.sleep(200);
        connectedChannelMock.enableRead(true);
        readThread.join();
        assertFalse(readThread.isAlive());
        assertEquals(8, readRunnable.getReadResult());
        ByteBuffer[] readBuffer = readRunnable.getReadBuffer();
        assertReadMessage(readBuffer[0], "read", "t");
        assertReadMessage(readBuffer[1], "his");
        assertReadMessage(readBuffer[2]);
        assertReadMessage(readBuffer[3]);
    }

    @Test
    public void readBlockingToBufferArrayWithTimeout() throws IOException, InterruptedException {
        connectedChannelMock.setReadData("read", "with", "timeout");
        ReadToBufferArrayBlocking readRunnable = new ReadToBufferArrayBlocking(connectedChannelMock, 1000,
                TimeUnit.MILLISECONDS);
        Thread readThread = new Thread(readRunnable);
        readThread.start();
        readThread.join();
        assertEquals(0, readRunnable.getReadResult());
        connectedChannelMock.enableRead(true);
        readThread = new Thread(readRunnable);
        readThread.start();
        readThread.join();
        assertEquals(15, readRunnable.getReadResult());
        ByteBuffer[] readBuffer = readRunnable.getReadBuffer();
        assertReadMessage(readBuffer[0], "read", "w");
        assertReadMessage(readBuffer[1], "ith", "ti");
        assertReadMessage(readBuffer[2], "meout");
        assertReadMessage(readBuffer[3]);
    }

    @Test
    public void readBlockingToEmptyBufferArrayWithTimeout() throws IOException, InterruptedException {
        connectedChannelMock.setReadData("can't read this");
        assertEquals(0, Channels.readBlocking(connectedChannelMock, new ByteBuffer[0], 0, 0, 2, TimeUnit.MINUTES));
    }

    @Test
    public void receiveBlocking() throws IOException, InterruptedException {
        connectedChannelMock.setReadData("receive this");
        ReceiveBlocking receiveRunnable = new ReceiveBlocking(messageChannelMock);
        Thread receiveThread = new Thread(receiveRunnable);
        receiveThread.start();
        receiveThread.join(50);
        assertTrue(receiveThread.isAlive());
        Thread.sleep(200);
        connectedChannelMock.enableRead(true);
        receiveThread.join();
        assertFalse(receiveThread.isAlive());
        assertEquals(12, receiveRunnable.getReceiveResult());
        assertReadMessage(receiveRunnable.getReceiveBuffer(), "receive this");
    }

    @Test
    public void receiveBlockingWithTimeout() throws IOException, InterruptedException {
        connectedChannelMock.setReadData("receive with timeout");
        ReceiveBlocking receiveRunnable = new ReceiveBlocking(messageChannelMock, 100, TimeUnit.MILLISECONDS);
        Thread receiveThread = new Thread(receiveRunnable);
        receiveThread.start();
        receiveThread.join();
        assertEquals(0, receiveRunnable.getReceiveResult());
        connectedChannelMock.enableRead(true);
        receiveThread = new Thread(receiveRunnable);
        receiveThread.start();
        receiveThread.join();
        assertEquals(20, receiveRunnable.getReceiveResult());
        assertReadMessage(receiveRunnable.getReceiveBuffer(), "receive with timeout");
    }

    @Test
    public void receiveBufferArrayBlocking() throws IOException, InterruptedException {
        connectedChannelMock.setReadData("receive", "this");
        ReceiveBufferArrayBlocking receiveRunnable = new ReceiveBufferArrayBlocking(messageChannelMock);
        Thread receiveThread = new Thread(receiveRunnable);
        receiveThread.start();
        receiveThread.join(50);
        assertTrue(receiveThread.isAlive());
        Thread.sleep(200);
        connectedChannelMock.enableRead(true);
        receiveThread.join();
        assertFalse(receiveThread.isAlive());
        assertEquals(11, receiveRunnable.getReceiveResult());
        ByteBuffer[] receiveBuffer = receiveRunnable.getReceiveBuffer();
        assertReadMessage(receiveBuffer[0], "recei");
        assertReadMessage(receiveBuffer[1], "ve", "thi");
        assertReadMessage(receiveBuffer[2], "s");
        assertReadMessage(receiveBuffer[3]);
    }

    @Test
    public void receiveBufferArrayBlockingWithTimeout() throws IOException, InterruptedException {
        connectedChannelMock.setReadData("receive", "with", "timeout");
        ReceiveBufferArrayBlocking receiveRunnable = new ReceiveBufferArrayBlocking(messageChannelMock, 1000,
                TimeUnit.MILLISECONDS);
        Thread receiveThread = new Thread(receiveRunnable);
        receiveThread.start();
        receiveThread.join();
        assertEquals(0, receiveRunnable.getReceiveResult());
        connectedChannelMock.enableRead(true);
        receiveThread = new Thread(receiveRunnable);
        receiveThread.start();
        receiveThread.join();
        assertEquals(18, receiveRunnable.getReceiveResult());
        ByteBuffer[] receiveBuffer = receiveRunnable.getReceiveBuffer();
        assertReadMessage(receiveBuffer[0], "recei");
        assertReadMessage(receiveBuffer[1], "ve", "wit");
        assertReadMessage(receiveBuffer[2], "h", "time");
        assertReadMessage(receiveBuffer[3], "out");
    }

    @Test
    public void acceptBlocking() throws IOException, InterruptedException {
        final AcceptingChannelMock acceptingChannelMock = new AcceptingChannelMock();
        final AcceptBlocking<?> acceptBlockingRunnable = new AcceptBlocking<ConnectedStreamChannelMock>(acceptingChannelMock);
        final Thread acceptChannelThread = new Thread(acceptBlockingRunnable);
        assertNotNull(Channels.acceptBlocking(acceptingChannelMock));
        assertFalse(acceptingChannelMock.haveWaitedAcceptable());
        // try to accept in another thread, while acceptance has been disabled
        acceptingChannelMock.enableAcceptance(false);
        acceptChannelThread.start();
        acceptChannelThread.join(200);
        assertTrue(acceptChannelThread.isAlive());
        // enable acceptance so that acceptChannelThread can finish
        acceptingChannelMock.enableAcceptance(true);
        acceptChannelThread.join();
        // check that accepting channel received at least once call to waitAcceptable
        assertTrue(acceptingChannelMock.haveWaitedAcceptable());
        assertNotNull(acceptBlockingRunnable.getAcceptedChannel());
    }

    @Test
    public void acceptBlockingWithTimeout() throws IOException, InterruptedException {
        final AcceptingChannelMock acceptingChannelMock = new AcceptingChannelMock();
        final AcceptBlocking<?> acceptBlockingRunnable = new AcceptBlocking<ConnectedStreamChannelMock>(acceptingChannelMock, 10, TimeUnit.SECONDS);
        final Thread acceptChannelThread = new Thread(acceptBlockingRunnable);
        // try to accept blocking with acceptance enabled at accepting channel mock
        assertNotNull(Channels.acceptBlocking(acceptingChannelMock, 1, TimeUnit.SECONDS));
        assertFalse(acceptingChannelMock.haveWaitedAcceptable());
        // try to accept in another thread, while acceptance has been disabled
        acceptingChannelMock.enableAcceptance(false);
        acceptChannelThread.start();
        acceptChannelThread.join(200);
        assertFalse(acceptChannelThread.isAlive());
        // thread is supposed to have finished, after having invoked awaitAcceptable at acceptingchannelMock with 10s timeout
        assertTrue(acceptingChannelMock.haveWaitedAcceptable());
        assertEquals(10, acceptingChannelMock.getAwaitAcceptableTime());
        assertEquals(TimeUnit.SECONDS, acceptingChannelMock.getAwaitAcceptableTimeUnit());
        // a null channel has been returned by accept
        assertNull(acceptBlockingRunnable.getAcceptedChannel());
        // enable acceptance so that acceptBlocking can return a non-null value
        acceptingChannelMock.enableAcceptance(true);
        acceptingChannelMock.clearWaitedAcceptable();
        assertNotNull(Channels.acceptBlocking(acceptingChannelMock, 15, TimeUnit.SECONDS));
        assertFalse(acceptingChannelMock.haveWaitedAcceptable());
    }

    @Test
    public void transferBlockingToFile1() throws IOException {
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        channelMock.setReadData("test");
        channelMock.enableRead(true);
        final File file = File.createTempFile("test", ".txt");
        file.deleteOnExit();
        final FileChannel fileChannel = new RandomAccessFile(file, "rw").getChannel();
        try {
            Channels.transferBlocking(fileChannel, channelMock, 0, 4);
            fileChannel.position(0);
            ByteBuffer buffer = ByteBuffer.allocate(10);
            fileChannel.read(buffer);
            assertReadMessage(buffer, "test");
        } finally {
            fileChannel.close();
        }
    }

    @Test
    public void transferBlockingToFile2() throws IOException, InterruptedException {
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        channelMock.setReadData("test", "12345");
        final File file = File.createTempFile("test", ".txt");
        file.deleteOnExit();
        final FileChannel fileChannel = new RandomAccessFile(file, "rw").getChannel();
        try {
            final Thread transferBlockingThread = new Thread(new TransferBlockingToFileChannel(channelMock, fileChannel, 0, 8));
            transferBlockingThread.start();
            transferBlockingThread.join(200);
            assertTrue(transferBlockingThread.isAlive());
            channelMock.enableRead(true);
            transferBlockingThread.join();
            fileChannel.position(0);
            ByteBuffer buffer = ByteBuffer.allocate(10);
            fileChannel.read(buffer);
            assertReadMessage(buffer, "test", "1234");
        } finally {
            fileChannel.close();
        }
    }

    @Test
    public void transferBlockingFromFile1() throws IOException {
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        final File file = File.createTempFile("test", ".txt");
        file.deleteOnExit();
        final FileChannel fileChannel = new RandomAccessFile(file, "rw").getChannel();
        try {
            final ByteBuffer buffer = ByteBuffer.allocate(10);
            buffer.put("test".getBytes("UTF-8")).flip();
            assertEquals(4, fileChannel.write(buffer));
            fileChannel.position(0);
            Channels.transferBlocking(channelMock, fileChannel, 0, 4);
            assertWrittenMessage(channelMock, "test");
        } finally {
            fileChannel.close();
        }
    }

    @Test
    public void transferBlockingFromFile2() throws IOException, InterruptedException {
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        channelMock.enableWrite(false);
        final File file = File.createTempFile("test", ".txt");
        file.deleteOnExit();
        final FileChannel fileChannel = new RandomAccessFile(file, "rw").getChannel();
        try {
            final ByteBuffer buffer = ByteBuffer.allocate(10);
            buffer.put("test12345".getBytes("UTF-8")).flip();
            assertEquals(9, fileChannel.write(buffer));
            fileChannel.position(0);
            
            final Thread transferBlockingThread = new Thread(new TransferBlockingFromFileChannel(fileChannel, channelMock, 0, 8));
            transferBlockingThread.start();
            transferBlockingThread.join(200);
            assertTrue(transferBlockingThread.isAlive());
            channelMock.enableWrite(true);
            transferBlockingThread.join();
            assertWrittenMessage(channelMock, "test", "1234");
        } finally {
            fileChannel.close();
        }
    }

    @Test
    public void setChannelListeners() {
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        final ChannelListener<ConnectedStreamChannel> channelListener = new ChannelListener<ConnectedStreamChannel>() {
            public void handleEvent(final ConnectedStreamChannel channel) {}
        };

        // test setReadListener
        Channels.setReadListener(channelMock, channelListener);
        assertSame(channelListener, channelMock.getReadListener());
        Channels.setReadListener(channelMock, null);
        assertNull(channelMock.getReadListener());

        // test setWriteListener
        Channels.setWriteListener(channelMock, channelListener);
        assertSame(channelListener, channelMock.getWriteListener());
        Channels.setWriteListener(channelMock, null);
        assertNull(channelMock.getWriteListener());

        // test setCloseListener
        Channels.setCloseListener(channelMock, channelListener);
        assertSame(channelListener, channelMock.getCloseListener());
        Channels.setCloseListener(channelMock, null);
        assertNull(channelMock.getCloseListener());
    }

    @Test
    public void setAcceptListener() {
        final AcceptingChannelMock channelMock = new AcceptingChannelMock();
        final ChannelListener<AcceptingChannel<ConnectedStreamChannelMock>> channelListener = new ChannelListener<AcceptingChannel<ConnectedStreamChannelMock>>() {
            public void handleEvent(final AcceptingChannel<ConnectedStreamChannelMock> channel) {}
        };

        Channels.setAcceptListener(channelMock, channelListener);
        assertSame(channelListener, channelMock.getAcceptListener());
        Channels.setAcceptListener(channelMock, null);
        assertNull(channelMock.getAcceptListener());
    }

    @Test
    public void wrapChannel() throws IOException {
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        final ByteChannel wrappedByteChannel = Channels.wrapByteChannel(channelMock);
        // test isOpen
        assertSame(wrappedByteChannel.isOpen(), channelMock.isOpen());
        // test read(ByteBuffer)
        channelMock.setReadData("read", "data");
        channelMock.enableRead(true);
        ByteBuffer buffer = ByteBuffer.allocate(10);
        assertEquals(8, wrappedByteChannel.read(buffer));
        assertReadMessage(buffer, "read", "data");
        // test read(ByteBuffer[])
        channelMock.setReadData("read", "in  ", "four", "sizd", "blks");
        ByteBuffer[] bufferArray = new ByteBuffer[]{ByteBuffer.allocate(4), ByteBuffer.allocate(4), ByteBuffer.allocate(4),
                ByteBuffer.allocate(4), ByteBuffer.allocate(4), ByteBuffer.allocate(4)};
        wrappedByteChannel.read(bufferArray);
        assertReadMessage(bufferArray[0], "read");
        assertReadMessage(bufferArray[1], "in  ");
        assertReadMessage(bufferArray[2], "four");
        assertReadMessage(bufferArray[3], "sizd");
        assertReadMessage(bufferArray[4], "blks");
        assertReadMessage(bufferArray[5]);
        // test read(ByteBuffer[], int, int)
        for(ByteBuffer bufferItem: bufferArray) {
            bufferItem.clear();
        }
        channelMock.setReadData("read", "again");
        wrappedByteChannel.read(bufferArray, 2, 4);
        assertReadMessage(bufferArray[0]);
        assertReadMessage(bufferArray[1]);
        assertReadMessage(bufferArray[2], "read");
        assertReadMessage(bufferArray[3], "agai");
        assertReadMessage(bufferArray[4], "n");
        assertReadMessage(bufferArray[5]);
        // test write(ByteBuffer)
        buffer.clear();
        buffer.put("write".getBytes("UTF-8")).flip();
        wrappedByteChannel.write(buffer);
        assertWrittenMessage(channelMock, "write");
        // test write(ByteBuffer[])
        for(ByteBuffer bufferItem: bufferArray) {
            bufferItem.clear();
        }
        bufferArray[0].put("writ".getBytes("UTF-8")).flip();
        bufferArray[1].put("e_ag".getBytes("UTF-8")).flip();
        bufferArray[2].put("ain".getBytes("UTF-8")).flip();
        bufferArray[3].flip();
        bufferArray[4].flip();
        bufferArray[5].flip();
        wrappedByteChannel.write(bufferArray);
        assertWrittenMessage(channelMock, "write", "write", "_again");
        // test write(ByteBuffer, int, int)
        for (ByteBuffer bufferItem: bufferArray) {
            bufferItem.flip();
        }
        wrappedByteChannel.write(bufferArray, 1, 1);
        assertWrittenMessage(channelMock, "write", "write", "_again", "e_ag");
        // test close()
        wrappedByteChannel.close();
        assertFalse(channelMock.isOpen());
        assertFalse(wrappedByteChannel.isOpen());
    }

    @Test
    public void getOption() throws IllegalArgumentException, IOException {
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        final Configurable brokenConfigurable = new Configurable() {

            @Override
            public boolean supportsOption(Option<?> option) {
                return true;
            }

            @Override
            public <T> T getOption(Option<T> option) throws IOException {
                throw new IOException("broken configurable for tests");
            }

            @Override
            public <T> T setOption(Option<T> option, T value) throws IllegalArgumentException, IOException {
                throw new IOException("broken configurable for tests");
            }
        };
        // Object type option
        channelMock.setOption(Options.SSL_PEER_HOST_NAME, "peer host name");
        assertEquals("peer host name", Channels.getOption(channelMock, Options.SSL_PEER_HOST_NAME, null));
        assertEquals("default", Channels.getOption(channelMock, Options.SSL_PROVIDER, "default"));
        assertNull(Channels.getOption(brokenConfigurable, Options.SSL_PEER_HOST_NAME, null));
        // boolean type option
        channelMock.setOption(Options.ALLOW_BLOCKING, true);
        assertTrue(Channels.getOption(channelMock, Options.ALLOW_BLOCKING, false));
        assertTrue(Channels.getOption(channelMock, Options.BROADCAST, true));
        assertFalse(Channels.getOption(brokenConfigurable, Options.ALLOW_BLOCKING, false));
        // int type option
        channelMock.setOption(Options.SSL_CLIENT_SESSION_TIMEOUT, 3000);
        assertEquals(3000, Channels.getOption(channelMock, Options.SSL_CLIENT_SESSION_TIMEOUT, 5000));
        assertEquals(1000, Channels.getOption(channelMock, Options.MAX_OUTBOUND_MESSAGE_SIZE, 1000));
        assertEquals(5000, Channels.getOption(brokenConfigurable, Options.SSL_CLIENT_SESSION_TIMEOUT, 5000));
        // long type option
        assertEquals(1l, Channels.getOption(channelMock, Options.STACK_SIZE, 1l));
        channelMock.setOption(Options.STACK_SIZE, 50000l);
        assertEquals(50000l, Channels.getOption(channelMock, Options.STACK_SIZE, 100));
        assertEquals(100, Channels.getOption(brokenConfigurable, Options.STACK_SIZE, 100));
    }

    @Test
    public void unwrap() {
        assertNull(Channels.unwrap(ConnectedStreamChannelMock.class, null));
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        final FramedMessageChannel wrappedChannel = new FramedMessageChannel(channelMock, ByteBuffer.allocate(500), ByteBuffer.allocate(500));
        assertSame(channelMock, Channels.unwrap(ConnectedStreamChannelMock.class, channelMock));
        assertSame(channelMock, Channels.unwrap(ConnectedStreamChannelMock.class, wrappedChannel));
        assertNull(Channels.unwrap(FramedMessageChannel.class, channelMock));
    }

    public static class FlushBlocking implements Runnable {
        private final SuspendableWriteChannel channel;

        public FlushBlocking(SuspendableWriteChannel c) {
            channel = c;
        }

        @Override
        public void run() {
            try {
                Channels.flushBlocking(channel);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static class ShutdownWritesBlocking implements Runnable {
        private final SuspendableWriteChannel channel;

        public ShutdownWritesBlocking(SuspendableWriteChannel c) {
            channel = c;
        }

        @Override
        public void run() {
            try {
                Channels.shutdownWritesBlocking(channel);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static class WriteBlocking implements Runnable {
        private final String message;
        private final ConnectedStreamChannel channel;
        private final int timeout;
        private final TimeUnit timeoutUnit;
        private int writeResult = -1;

        public WriteBlocking(ConnectedStreamChannel c, String m) {
            this(c, m, 0, null);
        }

        public WriteBlocking(ConnectedStreamChannel  c, String m, int t, TimeUnit tu) {
            channel = c;
            message = m;
            timeout = t;
            timeoutUnit = tu;
            
        }

        @Override
        public void run() {
            ByteBuffer buffer = ByteBuffer.allocate(30);
            try {
                buffer.put(message.getBytes("UTF-8")).flip();
                if (timeoutUnit != null) {
                    writeResult = Channels.writeBlocking(channel, buffer, timeout, timeoutUnit);
                } else {
                    writeResult = Channels.writeBlocking(channel, buffer);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public int getWriteResult() {
            return writeResult;
        }
    }

    public static class WriteBufferArrayBlocking implements Runnable {
        private final String[] message;
        private final ConnectedStreamChannel channel;
        private final long timeout;
        private final TimeUnit timeoutUnit;
        private long writeResult = -1;

        public WriteBufferArrayBlocking(ConnectedStreamChannel c, String ...m) {
            this(c, 0, null, m);
        }

        public WriteBufferArrayBlocking(ConnectedStreamChannel  c, long t, TimeUnit tu, String ...m) {
            channel = c;
            message = m;
            timeout = t;
            timeoutUnit = tu;
        }

        @Override
        public void run() {
            final ByteBuffer[] buffer = new ByteBuffer[message.length];
            try {
                for (int i = 0; i < buffer.length; i++) {
                    buffer[i] = ByteBuffer.allocate(message[i].length());
                    buffer[i].put(message[i].getBytes("UTF-8")).flip();
                }
                if (timeoutUnit != null) {
                    writeResult = Channels.writeBlocking(channel, buffer, 0, buffer.length, timeout, timeoutUnit);
                } else {
                    writeResult = Channels.writeBlocking(channel, buffer, 0, buffer.length);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public long getWriteResult() {
            return writeResult;
        }
    }

    public static class SendBlocking implements Runnable {
        private final String message;
        private final WritableMessageChannel channel;
        private final int timeout;
        private final TimeUnit timeoutUnit;
        private boolean sendResult;

        public SendBlocking(WritableMessageChannel c, String m) {
            this(c, m, 0, null);
        }

        public SendBlocking(WritableMessageChannel  c, String m, int t, TimeUnit tu) {
            channel = c;
            message = m;
            timeout = t;
            timeoutUnit = tu;
        }

        @Override
        public void run() {
            ByteBuffer buffer = ByteBuffer.allocate(30);
            try {
                buffer.put(message.getBytes("UTF-8")).flip();
                if (timeoutUnit != null) {
                    sendResult = Channels.sendBlocking(channel, buffer, timeout, timeoutUnit);
                } else {
                    Channels.sendBlocking(channel, buffer);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public boolean getSendResult() {
            return sendResult;
        }
    }

    public static class SendBufferArrayBlocking implements Runnable {
        private final String[] message;
        private final WritableMessageChannel channel;
        private final long timeout;
        private final TimeUnit timeoutUnit;
        private boolean sendResult;

        public SendBufferArrayBlocking(WritableMessageChannel c, String ...m) {
            this(c, 0, null, m);
        }

        public SendBufferArrayBlocking(WritableMessageChannel  c, long t, TimeUnit tu, String ...m) {
            channel = c;
            message = m;
            timeout = t;
            timeoutUnit = tu;
        }

        @Override
        public void run() {
            final ByteBuffer[] buffer = new ByteBuffer[message.length];
            try {
                for (int i = 0; i < buffer.length; i++) {
                    buffer[i] = ByteBuffer.allocate(message[i].length());
                    buffer[i].put(message[i].getBytes("UTF-8")).flip();
                }
                if (timeoutUnit != null) {
                    sendResult = Channels.sendBlocking(channel, buffer, 0, buffer.length, timeout, timeoutUnit);
                } else {
                    Channels.sendBlocking(channel, buffer, 0, buffer.length);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public boolean getSendResult() {
            return sendResult;
        }
    }

    public static class ReadBlocking implements Runnable {
        private final ByteBuffer buffer;
        private final ConnectedStreamChannel channel;
        private final long timeout;
        private final TimeUnit timeoutUnit;
        private int readResult;

        public ReadBlocking(ConnectedStreamChannel c) {
            this(c, 0, null);
        }

        public ReadBlocking(ConnectedStreamChannel c, ByteBuffer b) {
            this(c, 0, null, b);
        }

        public ReadBlocking(ConnectedStreamChannel c, long t, TimeUnit tu) {
            this(c, t, tu, ByteBuffer.allocate(20));
        }

        public ReadBlocking(ConnectedStreamChannel c, long t, TimeUnit tu, ByteBuffer b) {
            channel = c;
            timeout = t;
            timeoutUnit = tu;
            buffer = b;
        }

        @Override
        public void run() {
            try {
                if (timeoutUnit == null) {
                    readResult = Channels.readBlocking(channel, buffer);
                } else {
                    readResult = Channels.readBlocking(channel, buffer, timeout, timeoutUnit);
                }
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

    public static class ReadToBufferArrayBlocking implements Runnable {
        private final ByteBuffer[] buffer;
        private final ConnectedStreamChannel channel;
        private final long timeout;
        private final TimeUnit timeoutUnit;
        private long readResult;

        public ReadToBufferArrayBlocking(ConnectedStreamChannel c) {
            this(c, 0, null);
        }

        public ReadToBufferArrayBlocking(ConnectedStreamChannel c, long t, TimeUnit tu) {
            channel = c;
            timeout = t;
            timeoutUnit = tu;
            buffer = new ByteBuffer[] {ByteBuffer.allocate(5), ByteBuffer.allocate(5), ByteBuffer.allocate(5), ByteBuffer.allocate(5)};
        }

        @Override
        public void run() {
            try {
                if (timeoutUnit == null) {
                    readResult = Channels.readBlocking(channel, buffer, 0, buffer.length);
                } else {
                    readResult = Channels.readBlocking(channel, buffer, 0, buffer.length, timeout, timeoutUnit);
                }
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

    public static class ReceiveBlocking implements Runnable {
        private final ByteBuffer buffer;
        private final ReadableMessageChannel channel;
        private final long timeout;
        private final TimeUnit timeoutUnit;
        private int receiveResult;

        public ReceiveBlocking(ReadableMessageChannel c) {
            this(c, 0, null);
        }

        public ReceiveBlocking(ReadableMessageChannel c, long t, TimeUnit tu) {
            channel = c;
            timeout = t;
            timeoutUnit = tu;
            buffer = ByteBuffer.allocate(20);
        }

        @Override
        public void run() {
            try {
                if (timeoutUnit == null) {
                    receiveResult = Channels.receiveBlocking(channel, buffer);
                } else {
                    receiveResult = Channels.receiveBlocking(channel, buffer, timeout, timeoutUnit);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public int getReceiveResult() {
            return receiveResult;
        }

        public ByteBuffer getReceiveBuffer() {
            return buffer;
        }
    }

    public static class ReceiveBufferArrayBlocking implements Runnable {
        private final ByteBuffer[] buffer;
        private final ReadableMessageChannel channel;
        private final long timeout;
        private final TimeUnit timeoutUnit;
        private long receiveResult;

        public ReceiveBufferArrayBlocking(ReadableMessageChannel c) {
            this(c, 0, null);
        }

        public ReceiveBufferArrayBlocking(ReadableMessageChannel c, long t, TimeUnit tu) {
            channel = c;
            buffer = new ByteBuffer[] {ByteBuffer.allocate(5), ByteBuffer.allocate(5), ByteBuffer.allocate(5), ByteBuffer.allocate(5)};
            timeout = t;
            timeoutUnit = tu;
        }

        @Override
        public void run() {
            try {
                if (timeoutUnit == null) {
                    receiveResult = Channels.receiveBlocking(channel, buffer, 0, buffer.length);
                } else {
                    receiveResult = Channels.receiveBlocking(channel, buffer, 0, buffer.length, timeout, timeoutUnit);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public long getReceiveResult() {
            return receiveResult;
        }

        public ByteBuffer[] getReceiveBuffer() {
            return buffer;
        }
    }

    public static class AcceptBlocking<C extends ConnectedChannel> implements Runnable {
        
        private final AcceptingChannel<C> acceptingChannel;
        private C acceptedChannel;
        private final int timeout;
        private final TimeUnit timeoutUnit;

        public AcceptBlocking(AcceptingChannel<C> c) {
            this(c, -1, null);
        }

        public AcceptBlocking(AcceptingChannel<C> c, int t, TimeUnit tu) {
            acceptingChannel = c;
            timeout = t;
            timeoutUnit = tu;
        }

        @Override
        public void run() {
            try {
                if (timeoutUnit == null) {
                    acceptedChannel = Channels.acceptBlocking(acceptingChannel);
                }else {
                    acceptedChannel = Channels.acceptBlocking(acceptingChannel, timeout, timeoutUnit);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        
        public C getAcceptedChannel() {
            return acceptedChannel;
        }
    }
    
    public static class TransferBlockingToFileChannel implements Runnable {

        private final StreamSourceChannel fromChannel;
        private final FileChannel fileChannel;
        private final long startPosition;
        private final long count;

        public TransferBlockingToFileChannel(StreamSourceChannel from, FileChannel to, long startPosition, long count) {
            fromChannel = from;
            fileChannel = to;
            this.startPosition = startPosition;
            this.count = count;
        }

        @Override
        public void run() {
            try {
                Channels.transferBlocking(fileChannel, fromChannel, startPosition, count);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static class TransferBlockingFromFileChannel implements Runnable {

        private final StreamSinkChannel toChannel;
        private final FileChannel fileChannel;
        private final long startPosition;
        private final long count;

        public TransferBlockingFromFileChannel(FileChannel from, StreamSinkChannel to, long startPosition, long count) {
            fileChannel = from;
            toChannel = to;
            this.startPosition = startPosition;
            this.count = count;
        }

        @Override
        public void run() {
            try {
                Channels.transferBlocking(toChannel, fileChannel, startPosition, count);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
