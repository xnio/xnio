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

package org.xnio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.xnio.AssertReadWrite.assertReadMessage;
import static org.xnio.AssertReadWrite.assertWrittenMessage;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.xnio.channels.BlockingByteChannel;
import org.xnio.channels.PushBackStreamChannel;
import org.xnio.mock.ConnectedStreamChannelMock;

/**
 * Test for {@link XnioFileChannel}.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 *
 */
public class XnioFileChannelTestCase {

    private RandomAccessFile randomAccessFile;
    private FileChannel fileChannel;

    @Before
    public void initFileChannel() throws IOException {
        final File file = File.createTempFile("test", ".txt");
        file.deleteOnExit();
        randomAccessFile = new RandomAccessFile(file, "rw");
        fileChannel = new XnioFileChannel(randomAccessFile.getChannel());
    }

    @After
    public void closeFileChannel() throws IOException {
        fileChannel.close();
        randomAccessFile.close();
    }

    @Test
    public void simpleReadAndWrite() throws IOException {
        assertEquals(0, fileChannel.size());

        final ByteBuffer buffer = ByteBuffer.allocate(5);
        buffer.put("abcde".getBytes()).flip();
        assertEquals(5, fileChannel.write(buffer));

        assertEquals(5, fileChannel.size());
        assertEquals(5, fileChannel.position());
        fileChannel.position(0);
        assertEquals(0, fileChannel.position());
        assertEquals(5, fileChannel.size());

        final ByteBuffer readBuffer = ByteBuffer.allocate(10);
        assertEquals(5, fileChannel.read(readBuffer));
        assertEquals(5, fileChannel.position());
        assertReadMessage(readBuffer, "abcde");
        assertEquals(5, fileChannel.size());
    }

    @Test
    public void readAndWriteWithOffset() throws IOException {
        final ByteBuffer[] buffers = new ByteBuffer[] {ByteBuffer.allocate(3), ByteBuffer.allocate(3),
                ByteBuffer.allocate(3), ByteBuffer.allocate(3), ByteBuffer.allocate(3), ByteBuffer.allocate(3)};
        buffers[0].put("#$%".getBytes()).flip();
        buffers[1].put("abc".getBytes()).flip();
        buffers[2].put("de".getBytes()).flip();
        buffers[3].flip();
        buffers[4].put("ABC".getBytes()).flip();
        buffers[5].put("DEF".getBytes()).flip();
        assertEquals(5, fileChannel.write(buffers, 1, 3));

        assertEquals(5, fileChannel.position());
        fileChannel.position(0);
        assertEquals(0, fileChannel.position());

        final ByteBuffer[] readBuffers = new ByteBuffer[] {ByteBuffer.allocate(1), ByteBuffer.allocate(2),
                ByteBuffer.allocate(3), ByteBuffer.allocate(4)};
        assertEquals(2, fileChannel.read(readBuffers, 1, 1));
        assertEquals(2, fileChannel.position());
        assertReadMessage(readBuffers[1], "ab");

        assertEquals(3, fileChannel.read(readBuffers, 1, 3));
        assertEquals(5, fileChannel.position());
        assertReadMessage(readBuffers[2], "cde");
    }

    @Test
    public void readAndWriteWithPosition() throws IOException {
        assertEquals(0, fileChannel.size());

        final ByteBuffer buffer = ByteBuffer.allocate(5);
        buffer.put("abcde".getBytes()).flip();
        assertEquals(5, fileChannel.write(buffer, 3));

        assertEquals(8, fileChannel.size());
        assertEquals(0, fileChannel.position());
        fileChannel.position(3);
        assertEquals(3, fileChannel.position());
        assertEquals(8, fileChannel.size());

        final ByteBuffer readBuffer = ByteBuffer.allocate(10);
        assertEquals(4, fileChannel.read(readBuffer, 4));
        assertEquals(3, fileChannel.position());
        assertReadMessage(readBuffer, "bcde");
        assertEquals(8, fileChannel.size());
    }

    @Test
    public void truncate() throws IOException {
        assertEquals(0, fileChannel.size());

        final ByteBuffer buffer = ByteBuffer.allocate(20);
        buffer.put("message to truncate".getBytes()).flip();
        assertEquals(19, fileChannel.write(buffer));
        assertEquals(19, fileChannel.size());

        fileChannel.truncate(10);
        assertEquals(10, fileChannel.size());

        buffer.clear();
        fileChannel.position(0);
        assertEquals(10, fileChannel.read(buffer));
        assertReadMessage(buffer, "message to");

        buffer.clear();
        fileChannel.truncate(15);
        assertEquals(10, fileChannel.size());

        buffer.clear();
        fileChannel.position(0);
        assertEquals(10, fileChannel.read(buffer));
        assertReadMessage(buffer, "message to");

        buffer.clear();
        fileChannel.truncate(7);
        assertEquals(7, fileChannel.size());

        buffer.clear();
        fileChannel.position(0);
        assertEquals(7, fileChannel.read(buffer));
        assertReadMessage(buffer, "message");
        
        //test force... nothing noticeable should happen
        fileChannel.force(true);
        fileChannel.force(false);
    }

    @Test
    public void transferBlockingToFile1() throws IOException {
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        channelMock.setReadData("test");
        channelMock.enableRead(true);
        fileChannel.transferFrom(channelMock, 0, 4);
        fileChannel.position(0);
        ByteBuffer buffer = ByteBuffer.allocate(10);
        fileChannel.read(buffer);
        assertReadMessage(buffer, "test");
    }

    @Test
    public void transferBlockingToFile2() throws IOException, InterruptedException {
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        channelMock.setReadData("test", "12345");
        channelMock.enableRead(true);
        fileChannel.transferFrom(new PushBackStreamChannel(channelMock), 0, 8);
        fileChannel.position(0);
        ByteBuffer buffer = ByteBuffer.allocate(10);
        fileChannel.read(buffer);
        assertReadMessage(buffer, "test", "1234");
    }

    @Test
    public void transferBlockingToFile3() throws IOException {
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        channelMock.setReadData("test");
        channelMock.enableRead(true);
        fileChannel.transferFrom(new BlockingByteChannel(channelMock), 0, 4);
        fileChannel.position(0);
        ByteBuffer buffer = ByteBuffer.allocate(10);
        fileChannel.read(buffer);
        assertReadMessage(buffer, "test");
    }

    @Test
    public void transferBlockingFromFile1() throws IOException {
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        final ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put("test".getBytes("UTF-8")).flip();
        assertEquals(4, fileChannel.write(buffer));
        fileChannel.position(0);
        fileChannel.transferTo(0, 4, channelMock);
        assertWrittenMessage(channelMock, "test");
    }

    @Test
    public void transferBlockingFromFile2() throws IOException, InterruptedException {
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        channelMock.enableWrite(false);
        final ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put("test12345".getBytes("UTF-8")).flip();
        assertEquals(9, fileChannel.write(buffer));
        fileChannel.position(0);

        channelMock.enableWrite(true);
        fileChannel.transferTo(0, 8, channelMock);
        assertWrittenMessage(channelMock, "test", "1234");
    }

    @Test
    public void transferBlockingFromFile3() throws IOException {
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        final ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put("test".getBytes("UTF-8")).flip();
        assertEquals(4, fileChannel.write(buffer));
        fileChannel.position(0);
        fileChannel.transferTo(0, 4, new BlockingByteChannel(channelMock));
        assertWrittenMessage(channelMock, "test");
    }

    @Test
    public void map() throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put("1234567890".getBytes()).flip();
        assertEquals(10, fileChannel.write(buffer));

        MappedByteBuffer mappedBuffer = fileChannel.map(MapMode.READ_WRITE, 5, 2);
        assertNotNull(mappedBuffer);
        assertEquals(2, mappedBuffer.limit());
        mappedBuffer.position(2);
        assertReadMessage(mappedBuffer, "67");
    }

    @Test
    public void lock() throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put("1234567890".getBytes()).flip();
        assertEquals(10, fileChannel.write(buffer));

        FileLock lock = fileChannel.lock();
        assertNotNull(lock);

        OverlappingFileLockException expected = null;
        try {
            fileChannel.lock(3, 5, false);
        } catch (OverlappingFileLockException e) {
            expected = e;
        }
        assertNotNull(expected);
        lock.release();

        lock = fileChannel.lock(3, 5, false);
        assertNotNull(lock);

        expected = null;
        try {
            fileChannel.tryLock();
        } catch (OverlappingFileLockException e) {
            expected = e;
        }
        assertNotNull(expected);
        
        lock.release();
        lock = fileChannel.tryLock();
        assertNotNull(lock);
        lock.release();

        lock = fileChannel.tryLock(1, 3, true);
        assertNotNull(lock);

        expected = null;
        try {
            fileChannel.tryLock(1, 3, true);
        } catch (OverlappingFileLockException e) {
            expected = e;
        }
        assertNotNull(expected);
    }
}
