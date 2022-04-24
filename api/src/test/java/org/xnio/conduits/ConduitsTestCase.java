/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2022 Red Hat, Inc. and/or its affiliates, and individual
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

package org.xnio.conduits;

import org.junit.Before;
import org.junit.Test;
import org.xnio.mock.ConduitMock;
import org.xnio.mock.MessageConduitMock;
import org.xnio.mock.XnioIoThreadMock;
import org.xnio.mock.XnioWorkerMock;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.wildfly.common.Assert.assertTrue;
import static org.xnio.AssertReadWrite.assertReadMessage;
import static org.xnio.AssertReadWrite.assertWrittenMessage;

/**
 * Test for {@link Conduits}.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 */
public class ConduitsTestCase {

    private ConduitMock conduitMock;
    private MessageConduitMock messageConduitMock;


    @Before
    public void init() {
        final XnioWorkerMock worker = new XnioWorkerMock();
        final XnioIoThreadMock threadMock = worker.chooseThread();
        threadMock.start();
        conduitMock = new ConduitMock(worker, threadMock);
        messageConduitMock = new MessageConduitMock(worker, threadMock);
    }

    @Test
    public void transferToFile1() throws IOException {
        conduitMock.setReadData("test");
        conduitMock.enableReads(true);
        final File file = File.createTempFile("test", ".txt");
        file.deleteOnExit();
        final RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
        final FileChannel fileChannel = randomAccessFile.getChannel();
        final ByteBuffer buffer = ByteBuffer.allocate(10);
        try {
            assertEquals(0, Conduits.transfer(conduitMock, 0, buffer, fileChannel));
            fileChannel.position(0);
            assertEquals(buffer.position(), buffer.limit());
            buffer.compact();
            fileChannel.read(buffer);
            assertReadMessage(buffer, "");
            assertEquals(4, Conduits.transfer(conduitMock, 4, buffer, fileChannel));
            fileChannel.position(0);
            assertEquals(buffer.position(), buffer.limit());
            buffer.compact();
            fileChannel.read(buffer);
            assertReadMessage(buffer, "test");
        } finally {
            fileChannel.close();
            randomAccessFile.close();
        }
    }

    @Test
    public void transferToFile2() throws IOException {
        conduitMock.setReadData("test", "12345");
        final File file = File.createTempFile("test", ".txt");
        file.deleteOnExit();
        final RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
        final FileChannel fileChannel = randomAccessFile.getChannel();
        final ByteBuffer buffer = ByteBuffer.allocate(10);
        try {
            assertEquals(0, Conduits.transfer(conduitMock, 8, buffer, fileChannel));
            fileChannel.position(0);
            assertEquals(buffer.position(), buffer.limit());
            buffer.compact();
            fileChannel.read(buffer);
            assertReadMessage(buffer, "");
            conduitMock.enableReads(true);
            assertEquals(8, Conduits.transfer(conduitMock, 8, buffer, fileChannel));
            fileChannel.position(0);
            buffer.compact();
            fileChannel.read(buffer);
            assertReadMessage(buffer, "test", "1234");
        } finally {
            fileChannel.close();
            randomAccessFile.close();
        }
    }

    @Test
    public void transferFromFile1() throws IOException {
        final File file = File.createTempFile("test", ".txt");
        file.deleteOnExit();
        final RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
        final FileChannel fileChannel = randomAccessFile.getChannel();
        try {
            final ByteBuffer buffer = ByteBuffer.allocate(10);
            buffer.put("test".getBytes(UTF_8)).flip();
            assertEquals(4, fileChannel.write(buffer));
            buffer.compact();
            fileChannel.position(0);
            assertEquals(4, Conduits.transfer(fileChannel, 4, buffer, conduitMock));
            assertFalse(buffer.hasRemaining());
            assertWrittenMessage(conduitMock, "test");
        } finally {
            fileChannel.close();
            randomAccessFile.close();
        }
    }

    @Test
    public void transferFromFile2() throws IOException {
        conduitMock.enableWrites(false);
        final File file = File.createTempFile("test", ".txt");
        file.deleteOnExit();
        final RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
        final FileChannel fileChannel = randomAccessFile.getChannel();
        try {
            final ByteBuffer buffer = ByteBuffer.allocate(10);
            buffer.put("test12345".getBytes(UTF_8)).flip();
            assertEquals(9, fileChannel.write(buffer));
            fileChannel.position(0);
            buffer.compact();
            assertEquals(0, Conduits.transfer(fileChannel, 8, buffer, conduitMock));
            assertWrittenMessage(conduitMock, "");
            conduitMock.enableWrites(true);
            assertEquals(8, buffer.remaining());
            conduitMock.write(buffer);
            assertWrittenMessage(conduitMock, "test", "1234");
            buffer.compact();
            assertEquals(1, Conduits.transfer(fileChannel, 8, buffer, conduitMock));
            assertWrittenMessage(conduitMock, "test", "12345");
        } finally {
            fileChannel.close();
            randomAccessFile.close();
        }
    }

    @Test
    public void writeFinalBasic() throws IOException {
        conduitMock.enableWrites(false);
        final ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put("write this".getBytes(UTF_8)).flip();
        assertEquals(0, Conduits.writeFinalBasic(conduitMock, buffer));
        assertFalse(conduitMock.isWriteShutdown());
        conduitMock.enableWrites(true);
        assertEquals(10, Conduits.writeFinalBasic(conduitMock, buffer));
        assertWrittenMessage(conduitMock, "write this");
        assertTrue(conduitMock.isWriteShutdown());
        assertFalse(conduitMock.isFlushed());
    }

    @Test
    public void writeFinalBasicBufferArray() throws IOException {
        conduitMock.enableWrites(false);
        ByteBuffer[] bufferArray = new ByteBuffer[]{ByteBuffer.allocate(5), ByteBuffer.allocate(4)};
        bufferArray[0].put("write".getBytes(UTF_8)).flip();
        bufferArray[1].put("this".getBytes(UTF_8)).flip();
        assertEquals(0, Conduits.writeFinalBasic(conduitMock, bufferArray, 0, 2));
        assertFalse(conduitMock.isWriteShutdown());
        conduitMock.enableWrites(true);
        assertEquals(9, Conduits.writeFinalBasic(conduitMock, bufferArray, 0, 2));
        assertWrittenMessage(conduitMock, "writethis");
        assertTrue(conduitMock.isWriteShutdown());
        assertFalse(conduitMock.isFlushed());
    }

    @Test
    public void sendFinalBasic() throws IOException {
        messageConduitMock.enableWrites(false);
        final ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put("send this".getBytes(UTF_8)).flip();
        assertFalse(Conduits.sendFinalBasic(messageConduitMock, buffer));
        assertFalse(messageConduitMock.isWriteShutdown());
        messageConduitMock.enableWrites(true);
        assertTrue(Conduits.sendFinalBasic(messageConduitMock, buffer));
        assertWrittenMessage(messageConduitMock, "send this");
        assertTrue(messageConduitMock.isWriteShutdown());
        assertFalse(messageConduitMock.isFlushed());
    }

    @Test
    public void sendFinalBasicBufferArray() throws IOException {
        messageConduitMock.enableWrites(false);
        ByteBuffer[] bufferArray = new ByteBuffer[]{ByteBuffer.allocate(4), ByteBuffer.allocate(4)};
        bufferArray[0].put("send".getBytes(UTF_8)).flip();
        bufferArray[1].put("this".getBytes(UTF_8)).flip();
        assertFalse(Conduits.sendFinalBasic(messageConduitMock, bufferArray, 0, 2));
        messageConduitMock.enableWrites(true);
        assertTrue(Conduits.sendFinalBasic(messageConduitMock, bufferArray, 0, 2));
        assertWrittenMessage(messageConduitMock, "sendthis");
        assertTrue(messageConduitMock.isWriteShutdown());
        assertFalse(messageConduitMock.isFlushed());
    }

    @Test
    public void drain() throws IOException {
        // test drain 0
        conduitMock.setReadData("read", "data");
        conduitMock.enableReads(true);
        assertEquals(0, Conduits.drain(conduitMock, 0));
        ByteBuffer buffer = ByteBuffer.allocate(10);
        conduitMock.read(buffer);
        assertReadMessage(buffer, "read", "data");

        // test drain negative
        conduitMock.setReadData("read", "data");
        conduitMock.enableReads(true);
        buffer.clear();
        boolean failed = false;
        try {
            Conduits.drain(conduitMock, -5);
        } catch (IllegalArgumentException illegalArgumentException) {
            failed = true;
        }
        assertTrue(failed);
        assertEquals(8, conduitMock.read(buffer));
        assertReadMessage(buffer, "read", "data");

        // test drain 2
        conduitMock.setReadData("read", "data");
        conduitMock.enableReads(true);
        assertEquals(2, Conduits.drain(conduitMock, 2));
        buffer.clear();
        assertEquals(6, conduitMock.read(buffer));
        assertReadMessage(buffer, "ad", "data");

        // test drain little by little
        conduitMock.setReadData("read", "data");
        conduitMock.enableReads(true);
        buffer.clear();
        assertEquals(1, Conduits.drain(conduitMock, 1));
        assertEquals(2, Conduits.drain(conduitMock, 2));
        assertEquals(3, Conduits.drain(conduitMock, 3));
        assertEquals(2, conduitMock.read(buffer));
        assertReadMessage(buffer, "ta");

        // test drain the exact amount of bytes left
        conduitMock.setReadData("read", "data");
        conduitMock.enableReads(true);
        buffer.clear();
        assertEquals(8, Conduits.drain(conduitMock, 8));
        assertEquals(0, conduitMock.read(buffer));

        // test drain more bytes than available
        conduitMock.setReadData("read", "data");
        conduitMock.enableReads(true);
        buffer.clear();
        assertEquals(8, Conduits.drain(conduitMock, 9));
        assertEquals(0, conduitMock.read(buffer));

        // test drain the exact amount of bytes left without reading the EOF
        conduitMock.setReadData("read", "data");
        conduitMock.enableReads(true);
        conduitMock.setEof();
        buffer.clear();
        assertEquals(8, Conduits.drain(conduitMock, 8));
        assertEquals(-1, conduitMock.read(buffer));

        // test drain more bytes than available with eof
        conduitMock.setReadData("read", "data");
        conduitMock.enableReads(true);
        buffer.clear();
        assertEquals(8, Conduits.drain(conduitMock, 9));
        assertEquals(-1, conduitMock.read(buffer));

        // test drain with long max (Undertow usage)
        conduitMock.setReadData("read", "data");
        conduitMock.enableReads(true);
        buffer.clear();
        assertEquals(8, Conduits.drain(conduitMock, Long.MAX_VALUE));
        assertEquals(-1, conduitMock.read(buffer));

        // test drain an already drained channel
        assertEquals(-1, Conduits.drain(conduitMock, Long.MAX_VALUE));
        assertEquals(-1, conduitMock.read(buffer));
    }
}
