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
package org.xnio.streams;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.xnio.AssertReadWrite.assertReadMessage;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.junit.Before;
import org.junit.Test;
import org.xnio.Buffers;
import org.xnio.ByteBufferSlicePool;
import org.xnio.Pooled;

/**
 * Test for {@link BufferPipeInputStream}.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
public class BufferPipeInputStreamTestCase {
    private TestInputHandler handler;
    private BufferPipeInputStream stream;

    @Before
    public void before() {
        handler = new TestInputHandler();
        stream = new BufferPipeInputStream(handler);
    }

    @Test
    public void pushEmptyBuffer() throws IOException {
        assertEquals(0, stream.available());
        stream.push(ByteBuffer.allocate(0));
        assertEquals(0, stream.available());
        final Pooled<ByteBuffer> pooledBuffer = new ByteBufferSlicePool(1, 1).allocate();
        pooledBuffer.getResource().flip();
        stream.push(pooledBuffer);
        assertEquals(0, stream.available());
    }

    @Test
    public void pushAfterFailure() throws IOException {
        stream.pushException(new IOException("test"));
        assertCantPush();
    }

    @Test
    public void readSimpleBuffer() throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocate(20);
        buffer.put("abc, def, ghi, jkl".getBytes("UTF-8")).flip();

        stream.push(buffer);
        assertEquals(18, stream.available());

        // can't read to empty buffer
        assertEquals(0, stream.read(new byte[0]));

        byte[] readBuffer = new byte[20];
        assertEquals(18, stream.read(readBuffer));
        assertReadMessage(readBuffer, "abc, ", "def, ", "ghi, ", "jkl");

        assertEquals(0, stream.available());
        assertHandledMessages(false, "abc, def, ghi, jkl");
    }

    @Test
    public void readPooledByteBuffer() throws IOException {
        final ByteBufferSlicePool pool = new ByteBufferSlicePool(30, 30);
        final Pooled<ByteBuffer> pooledBuffer = pool.allocate();
        pooledBuffer.getResource().put("abc, def, ghi, jkl".getBytes("UTF-8")).flip();

        stream.push(pooledBuffer);
        assertEquals(18, stream.available());

        byte[] readBuffer = new byte[20];
        assertEquals(18, stream.read(readBuffer));
        assertReadMessage(readBuffer, "abc, ", "def, ", "ghi, ", "jkl");

        assertEquals(0, stream.available());
        assertHandledMessages(false, "abc, def, ghi, jkl");
    }

    @Test
    public void readMultipleBuffers() throws IOException {
        final ByteBufferSlicePool pool = new ByteBufferSlicePool(5, 5);
        final Pooled<ByteBuffer> pooledBuffer1 = pool.allocate();
        final Pooled<ByteBuffer> pooledBuffer2 = pool.allocate();
        final ByteBuffer byteBuffer1 = ByteBuffer.allocate(1);
        final ByteBuffer byteBuffer2 = ByteBuffer.allocate(2);
        final ByteBuffer byteBuffer3 = ByteBuffer.allocate(3);
        pooledBuffer1.getResource().put("multi".getBytes("UTF-8")).flip();
        byteBuffer1.put("p".getBytes("UTF-8")).flip();
        pooledBuffer2.getResource().put("le bu".getBytes("UTF-8")).flip();
        byteBuffer2.put("ff".getBytes("UTF-8")).flip();
        byteBuffer3.put("ers".getBytes("UTF-8")).flip();

        assertEquals(0, stream.available());
        stream.push(pooledBuffer1);
        assertEquals(5, stream.available());
        stream.push(byteBuffer1);
        assertEquals(6, stream.available());
        stream.push(pooledBuffer2);
        assertEquals(11, stream.available());
        stream.push(byteBuffer2);
        assertEquals(13, stream.available());
        stream.push(byteBuffer3);
        assertEquals(16, stream.available());

        byte[] readBuffer = new byte[20];
        assertEquals(16, stream.read(readBuffer));
        assertReadMessage(readBuffer, "multiple buffers");

        assertEquals(0, stream.available());
        assertHandledMessages(false, "multi", "p", "le bu", "ff", "ers");
    }

    @Test
    public void readMultipleBuffersMultipleTimes() throws IOException {
        final ByteBufferSlicePool pool = new ByteBufferSlicePool(5, 5);
        final Pooled<ByteBuffer> pooledBuffer1 = pool.allocate();
        final Pooled<ByteBuffer> pooledBuffer2 = pool.allocate();
        final ByteBuffer byteBuffer1 = ByteBuffer.allocate(1);
        final ByteBuffer byteBuffer2 = ByteBuffer.allocate(2);
        final ByteBuffer byteBuffer3 = ByteBuffer.allocate(3);
        byteBuffer1.put("m".getBytes("UTF-8")).flip();
        pooledBuffer1.getResource().put("ultip".getBytes("UTF-8")).flip();
        pooledBuffer2.getResource().put("le bu".getBytes("UTF-8")).flip();
        byteBuffer2.put("ff".getBytes("UTF-8")).flip();
        byteBuffer3.put("ers".getBytes("UTF-8")).flip();

        assertEquals(0, stream.available());
        stream.push(byteBuffer1);
        assertEquals(1, stream.available());
        stream.push(pooledBuffer1);
        assertEquals(6, stream.available());
        stream.push(pooledBuffer2);
        assertEquals(11, stream.available());
        stream.push(byteBuffer2);
        assertEquals(13, stream.available());
        stream.push(byteBuffer3);
        assertEquals(16, stream.available());

        byte[] readBuffer = new byte[3];
        assertEquals(3, stream.read(readBuffer));
        assertReadMessage(readBuffer, "mul");
        assertEquals(13, stream.available());

        assertEquals(3, stream.read(readBuffer));
        assertReadMessage(readBuffer, "tip");
        assertEquals(10, stream.available());

        assertEquals(3, stream.read(readBuffer));
        assertReadMessage(readBuffer, "le ");
        assertEquals(7, stream.available());

        assertEquals(3, stream.read(readBuffer));
        assertReadMessage(readBuffer, "buf");
        assertEquals(4, stream.available());

        assertEquals(3, stream.read(readBuffer));
        assertReadMessage(readBuffer, "fer");
        assertEquals(1, stream.available());

        assertEquals(1, stream.read(readBuffer));
        assertReadMessage(readBuffer, "s");
        assertEquals(0, stream.available());
        
        assertHandledMessages(false, "m", "ultip", "le bu", "ff", "ers");
    }

    @Test
    public void readFailure() throws IOException {
        final IOException exception = new IOException("test");
        stream.pushException(exception);
        IOException failure = null;

        try {
            stream.read(new byte[5]);
        } catch (IOException e) {
            failure = e;
        }
        assertSame(exception, failure);

        assertCantPush();
        assertHandledMessages(false);
    }

    @Test
    public void messageIsNotTruncatedByFailure() throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocate(20);
        buffer.put("truncated message".getBytes("UTF-8")).flip();
        stream.push(buffer);
        assertEquals(17, stream.available());

        byte[] readBuffer = new byte[10];
        assertEquals(10, stream.read(readBuffer));
        assertReadMessage(readBuffer, "truncated ");
        assertEquals(7, stream.available());

        final IOException exception = new IOException("test");
        stream.pushException(exception);
        assertEquals(7, stream.read(readBuffer));
        assertReadMessage(readBuffer, "message");
        assertEquals(0, stream.available());

        IOException failure = null;
        try {
            stream.read(readBuffer);
        } catch (IOException e) {
            failure = e;
        }
        assertSame(exception, failure);
        assertEquals(0, stream.available());
        assertCantPush();

        assertHandledMessages(false, "truncated message");
    }

    @Test
    public void readWaitsForPush() throws Exception {
        final ByteBuffer buffer = ByteBuffer.allocate(5);
        buffer.put("read".getBytes("UTF-8")).flip();

        assertEquals(0, stream.available());

        final ReadTask read = new ReadTask(stream);
        final Thread readThread = new Thread(read);
        readThread.start();
        readThread.join(100);
        assertTrue(readThread.isAlive());

        stream.push(buffer);
        readThread.join();
        assertEquals(0, stream.available());
        assertEquals(4, read.getReadResult());
        assertReadMessage(read.getReadBuffer(), "read");

        assertHandledMessages(false, "read");
    }

    @Test
    public void readWaitsForFailure() throws Exception {
        assertEquals(0, stream.available());

        final ReadTask read = new ReadTask(stream);
        final Thread readThread = new Thread(read);
        readThread.start();
        readThread.join(100);
        assertTrue(readThread.isAlive());

        final IOException failure = new IOException("test");
        stream.pushException(failure);
        readThread.join();
        assertEquals(0, stream.available());
        assertEquals(0, read.getReadResult());
        assertReadMessage(read.getReadBuffer());
        assertSame(failure, read.getFailure());
        assertCantPush();

        assertHandledMessages(false);
    }

    @Test
    public void readWaitsForEof() throws Exception {
        assertEquals(0, stream.available());

        final ReadTask read = new ReadTask(stream);
        final Thread readThread = new Thread(read);
        readThread.start();
        readThread.join(100);
        assertTrue(readThread.isAlive());

        stream.pushEof();
        readThread.join();
        assertEquals(0, stream.available());
        assertEquals(-1, read.getReadResult());
        assertReadMessage(read.getReadBuffer());
        assertNull(read.getFailure());
        assertCantPush();

        assertHandledMessages(false);
    }

    @Test
    public void readWaitsForClose() throws Exception {
        assertEquals(0, stream.available());

        final ReadTask read = new ReadTask(stream);
        final Thread readThread = new Thread(read);
        readThread.start();
        readThread.join(100);
        assertTrue(readThread.isAlive());

        stream.close();
        readThread.join();
        assertEquals(0, stream.available());
        assertEquals(-1, read.getReadResult());
        assertReadMessage(read.getReadBuffer());
        assertNull(read.getFailure());
        assertCantPush();

        assertHandledMessages(true);
    }

    @Test
    public void concurrentReadBuffers() throws Exception {
        final ByteBuffer buffer = ByteBuffer.allocate(20);
        buffer.put("abcdefghijklmnopqrst".getBytes("UTF-8")).flip();
        stream.push(buffer);
        assertEquals(20, stream.available());
        final ReadTask read1 = new ReadTask(stream, 10);
        final ReadTask read2 = new ReadTask(stream, 10);
        final ReadTask read3 = new ReadTask(stream, 10);
        final PushTask push = new PushTask(stream, "uvwxyz");
        final Thread readThread1 = new Thread(read1, "READ1");
        final Thread readThread2 = new Thread(read2, "READ2");
        final Thread readThread3 = new Thread(read3, "READ3");
        final Thread pushThread = new Thread(push, "PUSH");

        readThread1.start();
        readThread2.start();
        readThread3.start();
        Thread.sleep(100);
        pushThread.start();
        readThread1.join();
        readThread2.join();
        readThread3.join();
        pushThread.join();

        byte[] readBuffer1 = read1.getReadBuffer();
        byte[] readBuffer2 = read2.getReadBuffer();
        byte[] readBuffer3 = read3.getReadBuffer();

        if (readBuffer1[0] == 'a') {
            if (readBuffer2[0] == 'k') {
                assertEquals(10, read1.getReadResult());
                assertReadMessage(readBuffer1, "abcdefghij");
                assertEquals(10, read2.getReadResult());
                assertReadMessage(readBuffer2, "klmnopqrst");
                assertEquals(6, read3.getReadResult());
                assertReadMessage(readBuffer3, "uvwxyz");
            } else if (readBuffer2[0] == 'u') {
                assertEquals(10, read1.getReadResult());
                assertReadMessage(readBuffer1, "abcdefghij");
                assertEquals(10, read3.getReadResult());
                assertReadMessage(readBuffer3, "klmnopqrst");
                assertEquals(6, read2.getReadResult());
                assertReadMessage(readBuffer2, "uvwxyz");
            } else {
                fail("Unexpected content for readBuffer2: " + Arrays.toString(readBuffer2));
            }
        } else if (readBuffer1[0] == 'k') {
            if (readBuffer2[0] == 'a') {
                assertEquals(10, read2.getReadResult());
                assertReadMessage(readBuffer2, "abcdefghij");
                assertEquals(10, read1.getReadResult());
                assertReadMessage(readBuffer1, "klmnopqrst");
                assertEquals(6, read3.getReadResult());
                assertReadMessage(readBuffer3, "uvwxyz");
            } else if (readBuffer2[0] == 'u') {
                assertEquals(10, read3.getReadResult());
                assertReadMessage(readBuffer3, "abcdefghij");
                assertEquals(10, read1.getReadResult());
                assertReadMessage(readBuffer1, "klmnopqrst");
                assertEquals(6, read2.getReadResult());
                assertReadMessage(readBuffer2, "uvwxyz");
            } else {
                fail("Unexpected content for readBuffer2: " + Arrays.toString(readBuffer2));
            }
        } else if (readBuffer1[0] == 'u') {
            if (readBuffer2[0] == 'a') {
                assertEquals(10, read2.getReadResult());
                assertReadMessage(readBuffer2, "abcdefghij");
                assertEquals(10, read3.getReadResult());
                assertReadMessage(readBuffer3, "klmnopqrst");
                assertEquals(6, read1.getReadResult());
                assertReadMessage(readBuffer1, "uvwxyz");
            } else if (readBuffer2[0] == 'ḱ') {
                assertEquals(10, read3.getReadResult());
                assertReadMessage(readBuffer3, "abcdefghij");
                assertEquals(10, read2.getReadResult());
                assertReadMessage(readBuffer2, "klmnopqrst");
                assertEquals(6, read1.getReadResult());
                assertReadMessage(readBuffer1, "uvwxyz");
            } else {
                fail("Unexpected content for readBuffer2: " + Arrays.toString(readBuffer2));
            }
        } else {
            fail("Unexpected content for readBuffer1: " + Arrays.toString(readBuffer1));
        }
        assertHandledMessages(false, "abcdefghijklmnopqrst", "uvwxyz");
    }

    @Test
    public void readBytesFromSimpleBuffer() throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put("abcdef".getBytes("UTF-8")).flip();

        stream.push(buffer);
        assertEquals(6, stream.available());

        assertEquals('a', stream.read());
        assertEquals(5, stream.available());
        assertEquals('b', stream.read());
        assertEquals(4, stream.available());
        assertEquals('c', stream.read());
        assertEquals(3, stream.available());
        assertEquals('d', stream.read());
        assertEquals(2, stream.available());
        assertEquals('e', stream.read());
        assertEquals(1, stream.available());
        assertEquals('f', stream.read());
        assertEquals(0, stream.available());

        assertHandledMessages(false, "abcdef");
    }

    @Test
    public void readBytesFromPooledByteBuffer() throws IOException {
        final ByteBufferSlicePool pool = new ByteBufferSlicePool(5, 5);
        final Pooled<ByteBuffer> pooledBuffer = pool.allocate();
        pooledBuffer.getResource().put("ghijk".getBytes("UTF-8")).flip();

        stream.push(pooledBuffer);
        assertEquals(5, stream.available());

        assertEquals('g', stream.read());
        assertEquals(4, stream.available());
        assertEquals('h', stream.read());
        assertEquals(3, stream.available());
        assertEquals('i', stream.read());
        assertEquals(2, stream.available());
        assertEquals('j', stream.read());
        assertEquals(1, stream.available());
        assertEquals('k', stream.read());
        assertEquals(0, stream.available());

        assertHandledMessages(false, "ghijk");
    }

    @Test
    public void readBytesFromMultipleBuffers() throws IOException {
        final ByteBufferSlicePool pool = new ByteBufferSlicePool(5, 5);
        final Pooled<ByteBuffer> pooledBuffer1 = pool.allocate();
        final Pooled<ByteBuffer> pooledBuffer2 = pool.allocate();
        final ByteBuffer byteBuffer1 = ByteBuffer.allocate(1);
        final ByteBuffer byteBuffer2 = ByteBuffer.allocate(2);
        final ByteBuffer byteBuffer3 = ByteBuffer.allocate(3);
        pooledBuffer1.getResource().put("multi".getBytes("UTF-8")).flip();
        byteBuffer1.put("p".getBytes("UTF-8")).flip();
        pooledBuffer2.getResource().put("le bu".getBytes("UTF-8")).flip();
        byteBuffer2.put("ff".getBytes("UTF-8")).flip();
        byteBuffer3.put("ers".getBytes("UTF-8")).flip();

        assertEquals(0, stream.available());
        stream.push(pooledBuffer1);
        assertEquals(5, stream.available());
        stream.push(byteBuffer1);
        assertEquals(6, stream.available());
        stream.push(pooledBuffer2);
        assertEquals(11, stream.available());
        stream.push(byteBuffer2);
        assertEquals(13, stream.available());
        stream.push(byteBuffer3);
        assertEquals(16, stream.available());

        assertEquals('m', stream.read());
        assertEquals(15, stream.available());
        assertEquals('u', stream.read());
        assertEquals(14, stream.available());
        assertEquals('l', stream.read());
        assertEquals(13, stream.available());
        assertEquals('t', stream.read());
        assertEquals(12, stream.available());
        assertEquals('i', stream.read());
        assertEquals(11, stream.available());
        assertEquals('p', stream.read());
        assertEquals(10, stream.available());
        assertEquals('l', stream.read());
        assertEquals(9, stream.available());
        assertEquals('e', stream.read());
        assertEquals(8, stream.available());
        assertEquals(' ', stream.read());
        assertEquals(7, stream.available());
        assertEquals('b', stream.read());
        assertEquals(6, stream.available());
        assertEquals('u', stream.read());
        assertEquals(5, stream.available());
        assertEquals('f', stream.read());
        assertEquals(4, stream.available());
        assertEquals('f', stream.read());
        assertEquals(3, stream.available());
        assertEquals('e', stream.read());
        assertEquals(2, stream.available());
        assertEquals('r', stream.read());
        assertEquals(1, stream.available());
        assertEquals('s', stream.read());
        assertEquals(0, stream.available());

        assertHandledMessages(false, "multi", "p", "le bu", "ff", "ers");
    }

    @Test
    public void readBytesFromMultipleBuffersMultipleTimes() throws IOException {
        final ByteBufferSlicePool pool = new ByteBufferSlicePool(5, 5);
        final Pooled<ByteBuffer> pooledBuffer1 = pool.allocate();
        final Pooled<ByteBuffer> pooledBuffer2 = pool.allocate();
        final ByteBuffer byteBuffer1 = ByteBuffer.allocate(1);
        final ByteBuffer byteBuffer2 = ByteBuffer.allocate(2);
        final ByteBuffer byteBuffer3 = ByteBuffer.allocate(3);
        byteBuffer1.put("m".getBytes("UTF-8")).flip();
        pooledBuffer1.getResource().put("ultip".getBytes("UTF-8")).flip();
        pooledBuffer2.getResource().put("le bu".getBytes("UTF-8")).flip();
        byteBuffer2.put("ff".getBytes("UTF-8")).flip();
        byteBuffer3.put("ers".getBytes("UTF-8")).flip();

        assertEquals(0, stream.available());
        stream.push(byteBuffer1);
        assertEquals(1, stream.available());
        stream.push(pooledBuffer1);
        assertEquals(6, stream.available());
        stream.push(pooledBuffer2);
        assertEquals(11, stream.available());
        stream.push(byteBuffer2);
        assertEquals(13, stream.available());
        stream.push(byteBuffer3);
        assertEquals(16, stream.available());

        assertEquals('m', stream.read());
        assertEquals(15, stream.available());
        assertEquals('u', stream.read());
        assertEquals(14, stream.available());
        assertEquals('l', stream.read());
        assertEquals(13, stream.available());
        assertEquals('t', stream.read());
        assertEquals(12, stream.available());
        assertEquals('i', stream.read());
        assertEquals(11, stream.available());
        assertEquals('p', stream.read());
        assertEquals(10, stream.available());
        assertEquals('l', stream.read());
        assertEquals(9, stream.available());
        assertEquals('e', stream.read());
        assertEquals(8, stream.available());
        assertEquals(' ', stream.read());
        assertEquals(7, stream.available());
        assertEquals('b', stream.read());
        assertEquals(6, stream.available());
        assertEquals('u', stream.read());
        assertEquals(5, stream.available());
        assertEquals('f', stream.read());
        assertEquals(4, stream.available());
        assertEquals('f', stream.read());
        assertEquals(3, stream.available());
        assertEquals('e', stream.read());
        assertEquals(2, stream.available());
        assertEquals('r', stream.read());
        assertEquals(1, stream.available());
        assertEquals('s', stream.read());
        assertEquals(0, stream.available());

        assertHandledMessages(false, "m", "ultip", "le bu", "ff", "ers");
    }

    @Test
    public void readByteReceivesFailure() throws IOException {
        final IOException exception = new IOException("test");
        stream.pushException(exception);
        IOException failure = null;

        try {
            stream.read();
        } catch (IOException e) {
            failure = e;
        }
        assertSame(exception, failure);
        assertCantPush();
        assertHandledMessages(false);
    }

    @Test
    public void messageBytesAreNotTruncatedByFailure() throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocate(20);
        buffer.put("truncated message".getBytes("UTF-8")).flip();
        stream.push(buffer);

        assertEquals(17, stream.available());
        assertEquals('t', stream.read());
        assertEquals(16, stream.available());
        assertEquals('r', stream.read());
        assertEquals(15, stream.available());
        assertEquals('u', stream.read());
        assertEquals(14, stream.available());
        assertEquals('n', stream.read());
        assertEquals(13, stream.available());
        assertEquals('c', stream.read());
        assertEquals(12, stream.available());
        assertEquals('a', stream.read());
        assertEquals(11, stream.available());
        assertEquals('t', stream.read());
        assertEquals(10, stream.available());
        assertEquals('e', stream.read());
        assertEquals(9, stream.available());
        assertEquals('d', stream.read());
        assertEquals(8, stream.available());
        assertEquals(' ', stream.read());
        assertEquals(7, stream.available());
        
        final IOException exception = new IOException("test");
        stream.pushException(exception);
        assertEquals('m', stream.read());
        assertEquals(6, stream.available());
        assertEquals('e', stream.read());
        assertEquals(5, stream.available());
        assertEquals('s', stream.read());
        assertEquals(4, stream.available());
        assertEquals('s', stream.read());
        assertEquals(3, stream.available());
        assertEquals('a', stream.read());
        assertEquals(2, stream.available());
        assertEquals('g', stream.read());
        assertEquals(1, stream.available());
        assertEquals('e', stream.read());
        assertEquals(0,  stream.available());

        IOException failure = null;
        try {
            stream.read();
        } catch (IOException e) {
            failure = e;
        }
        assertSame(exception, failure);
        assertEquals(0, stream.available());
        assertCantPush();

        assertHandledMessages(false, "truncated message");
    }

    @Test
    public void readByteWaitsForPush() throws Exception {
        final ByteBuffer buffer = ByteBuffer.allocate(5);
        buffer.put("read".getBytes("UTF-8")).flip();

        assertEquals(0, stream.available());

        final ReadByteTask read1 = new ReadByteTask(stream);
        final Thread readThread1 = new Thread(read1);
        final ReadByteTask read2 = new ReadByteTask(stream);
        final Thread readThread2 = new Thread(read2);
        final ReadByteTask read3 = new ReadByteTask(stream);
        final Thread readThread3 = new Thread(read3);
        final ReadByteTask read4 = new ReadByteTask(stream);
        final Thread readThread4 = new Thread(read4);
        readThread1.start();
        readThread1.join(100);
        assertTrue(readThread1.isAlive());

        stream.push(buffer);
        readThread1.join();
        assertEquals(3, stream.available());
        readThread2.start();
        readThread2.join();
        assertEquals(2, stream.available());
        readThread3.start();
        readThread3.join();
        assertEquals(1, stream.available());
        readThread4.start();
        readThread4.join();
        assertEquals(0, stream.available());

        assertEquals(0, stream.available());
        assertEquals('r', read1.getReadResult());
        assertEquals('e', read2.getReadResult());
        assertEquals('a', read3.getReadResult());
        assertEquals('d', read4.getReadResult());

        assertHandledMessages(false, "read");
    }

    @Test
    public void readByteWaitsForFailure() throws Exception {
        assertEquals(0, stream.available());

        final ReadByteTask read = new ReadByteTask(stream);
        final Thread readThread = new Thread(read);
        readThread.start();
        readThread.join(100);
        assertTrue(readThread.isAlive());

        final IOException failure = new IOException("test");
        stream.pushException(failure);
        readThread.join();
        assertEquals(0, stream.available());
        assertEquals(0, read.getReadResult());
        assertSame(failure, read.getFailure());
        assertCantPush();

        assertHandledMessages(false);
    }

    @Test
    public void readByteWaitsForEof() throws Exception {
        assertEquals(0, stream.available());

        final ReadByteTask read = new ReadByteTask(stream);
        final Thread readThread = new Thread(read);
        readThread.start();
        readThread.join(100);
        assertTrue(readThread.isAlive());

        stream.pushEof();
        readThread.join();
        assertEquals(0, stream.available());
        assertEquals(-1, read.getReadResult());
        assertNull(read.getFailure());
        assertCantPush();

        assertHandledMessages(false);
    }

    @Test
    public void readByteWaitsForClose() throws Exception {
        assertEquals(0, stream.available());

        final ReadByteTask read = new ReadByteTask(stream);
        final Thread readThread = new Thread(read);
        readThread.start();
        readThread.join(100);
        assertTrue(readThread.isAlive());

        stream.close();
        readThread.join();
        assertEquals(0, stream.available());
        assertEquals(-1, read.getReadResult());
        assertNull(read.getFailure());
        assertCantPush();

        assertHandledMessages(true);
    }

    @Test
    public void readBuffersAndBytes() throws IOException {
        final ByteBuffer buffer1 = ByteBuffer.allocate(10);
        buffer1.put("abcdefghij".getBytes("UTF-8")).flip();
        final ByteBuffer buffer2 = ByteBuffer.allocate(10);
        buffer2.put("klmnopqrst".getBytes("UTF-8")).flip();
        final byte[] readBuffer1 = new byte[3];
        final byte[] readBuffer2 = new byte[5];
        final byte[] readBuffer3 = new byte[2];
        stream.push(buffer1);
        stream.push(buffer2);

        assertEquals(20, stream.available());
        assertEquals(3, stream.read(readBuffer1));
        assertReadMessage(readBuffer1, "abc");
        assertEquals(17, stream.available());

        assertEquals('d', stream.read());
        assertEquals(16, stream.available());
        assertEquals('e', stream.read());
        assertEquals(15, stream.available());
        assertEquals('f', stream.read());
        assertEquals(14, stream.available());
        assertEquals('g', stream.read());
        assertEquals(13, stream.available());

        assertEquals(5, stream.read(readBuffer2));
        assertReadMessage(readBuffer2, "hijkl");
        assertEquals(8, stream.available());

        assertEquals('m', stream.read());
        assertEquals(7, stream.available());

        assertEquals(2, stream.read(readBuffer3));
        assertReadMessage(readBuffer3, "no");
        assertEquals(5, stream.available());

        assertEquals('p', stream.read());
        assertEquals(4, stream.available());
        assertEquals('q', stream.read());
        assertEquals(3, stream.available());
        assertEquals('r', stream.read());
        assertEquals(2, stream.available());
        assertEquals('s', stream.read());
        assertEquals(1, stream.available());
        assertEquals('t', stream.read());
        assertEquals(0, stream.available());

        assertHandledMessages(false, "abcdefghij", "klmnopqrst");
    }

    @Test
    public void concurrentReadBytes()  throws Exception {
        final ByteBuffer buffer = ByteBuffer.allocate(20);
        buffer.put("ab".getBytes("UTF-8")).flip();
        stream.push(buffer);
        assertEquals(2, stream.available());
        final ReadByteTask read1 = new ReadByteTask(stream);
        final ReadByteTask read2 = new ReadByteTask(stream);
        final ReadByteTask read3 = new ReadByteTask(stream);
        final PushTask push = new PushTask(stream, "cde");
        final Thread readThread1 = new Thread(read1, "READ1");
        final Thread readThread2 = new Thread(read2, "READ2");
        final Thread readThread3 = new Thread(read3, "READ3");
        final Thread pushThread = new Thread(push, "PUSH");

        readThread1.start();
        readThread2.start();
        readThread3.start();
        Thread.sleep(100);
        pushThread.start();
        readThread1.join();
        readThread2.join();
        readThread3.join();
        pushThread.join();

        int readByte1 = read1.getReadResult();
        int readByte2= read2.getReadResult();
        int readByte3 = read3.getReadResult();

        assertTrue("Unexpected values for read results: '" + (char) readByte1 + "', '" + (char) readByte2 + "', and '" + (char) readByte3 + "'",
                (readByte1 == 'a' && readByte2 == 'b' && readByte3 == 'c') ||
                (readByte1 == 'a' && readByte2 == 'c' && readByte3 == 'b') ||
                (readByte1 == 'b' && readByte2 == 'a' && readByte3 == 'c') ||
                (readByte1 == 'b' && readByte2 == 'c' && readByte3 == 'a') ||
                (readByte1 == 'c' && readByte2 == 'a' && readByte3 == 'b') ||
                (readByte1 == 'c' && readByte2 == 'b' && readByte3 == 'a'));

        // "cde" is not handled because it was not fully read, only the 'c' char was read from "cde" message
        assertHandledMessages(false, "ab");
    }

    @Test
    public void skipMessage() throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocate(3);
        buffer.put("abc".getBytes("UTF-8")).flip();

        stream.push(buffer);
        assertEquals(3, stream.available());
        assertEquals(3, stream.skip(3));
        assertEquals(0, stream.available());

        assertHandledMessages(false, "abc");
    }

    @Test
    public void skipPartOfMessage() throws IOException {
        final ByteBufferSlicePool bufferPool = new ByteBufferSlicePool(6, 6);
        final Pooled<ByteBuffer> pooledBuffer1 = bufferPool.allocate();
        final Pooled<ByteBuffer> pooledBuffer2 = bufferPool.allocate();
        pooledBuffer1.getResource().put("skip ".getBytes("UTF-8")).flip();
        pooledBuffer2.getResource().put("msg".getBytes("UTF-8")).flip();

        assertEquals(0, stream.available());
        stream.push(pooledBuffer1);
        assertEquals(5, stream.available());
        stream.push(pooledBuffer2);
        assertEquals(8, stream.available());

        assertEquals(5, stream.skip(4));
        assertEquals(3, stream.available());

        final byte[] readBuffer = new byte[6];
        assertEquals(3, stream.read(readBuffer));
        assertReadMessage(readBuffer, "msg");
        assertEquals(0, stream.available());

        assertHandledMessages(false, "skip ", "msg");
    }

    @Test
    public void skipWaitsForFailure() throws Exception {
        assertEquals(0, stream.available());

        final SkipTask skip = new SkipTask(stream, 3);
        final Thread skipThread = new Thread(skip);
        skipThread.start();
        skipThread.join(100);
        assertTrue(skipThread.isAlive());

        final IOException failure = new IOException("test");
        stream.pushException(failure);
        skipThread.join();
        assertEquals(0, stream.available());
        assertEquals(0, skip.getSkipResult());
        assertSame(failure, skip.getFailure());

        assertHandledMessages(false);
    }

    @Test
    public void skipWaitsForPush() throws Exception {
        final ByteBuffer buffer = ByteBuffer.allocate(5);
        buffer.put("skip".getBytes("UTF-8")).flip();

        assertEquals(0, stream.available());

        final SkipTask skip = new SkipTask(stream, 10);
        final Thread skipThread = new Thread(skip);
        skipThread.start();
        skipThread.join(100);
        assertTrue(skipThread.isAlive());

        stream.push(buffer);
        skipThread.join();
        assertEquals(0, stream.available());
        assertEquals(4, skip.getSkipResult());

        assertHandledMessages(false, "skip");
    }

    @Test
    public void skipWaitsForEof() throws Exception  {
        assertEquals(0, stream.available());

        final SkipTask skip = new SkipTask(stream, 0);
        final Thread skipThread = new Thread(skip);
        skipThread.start();
        skipThread.join(100);
        assertTrue(skipThread.isAlive());

        stream.pushEof();
        skipThread.join();
        assertEquals(0, stream.available());
        assertEquals(0, skip.getSkipResult());
        assertNull(skip.getFailure());

        assertCantPush();
        assertHandledMessages(false);
    }

    @Test
    public void skipWaitsForClose() throws Exception {
        assertEquals(0, stream.available());

        final SkipTask skip = new SkipTask(stream, 1);
        final Thread skipThread = new Thread(skip);
        skipThread.start();
        skipThread.join(100);
        assertTrue(skipThread.isAlive());

        stream.close();
        skipThread.join();
        assertEquals(0, stream.available());
        assertEquals(0, skip.getSkipResult());
        assertNull(skip.getFailure());

        assertCantPush();
        assertHandledMessages(true);
    }

    @Test
    public void messageIsTruncatedByClose() throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocate(20);
        buffer.put("truncated message".getBytes("UTF-8")).flip();
        stream.push(buffer);
        byte[] readBuffer = new byte[10];
        assertEquals(10, stream.read(readBuffer));
        assertReadMessage(readBuffer, "truncated ");

        stream.close();
        assertEquals(-1, stream.read(readBuffer));
        assertEquals(-1, stream.read());
        assertEquals(0, stream.skip(7));
        assertCantPush();
        assertHandledMessages(true);
    }

    @Test
    public void messageIsNotTruncatedByEof() throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocate(20);
        buffer.put("truncated message".getBytes("UTF-8")).flip();
        stream.push(buffer);
        byte[] readBuffer = new byte[10];
        assertEquals(10, stream.read(readBuffer));
        assertReadMessage(readBuffer, "truncated ");

        stream.pushEof();
        assertEquals(7, stream.read(readBuffer));
        assertReadMessage(readBuffer, "message");

        assertEquals(-1, stream.read(readBuffer));

        // now we can't push anymore
        buffer.flip();
        stream.push(buffer);
        assertEquals(0, stream.available());
        assertEquals(-1, stream.read());
        assertEquals(-1, stream.read(readBuffer));
        assertEquals(0, stream.skip(5));
        assertCantPush();

        // push eof is idempotent
        buffer.flip();
        stream.push(buffer);
        assertEquals(0, stream.available());
        assertEquals(-1, stream.read());
        assertEquals(-1, stream.read(readBuffer));
        assertEquals(0, stream.skip(10));
        assertCantPush();

        assertHandledMessages(false, "truncated message");
    }

    @Test
    public void closeEmptyStream() throws IOException {
        stream.close();
        byte[] readBuffer = new byte[10];
        assertEquals(-1, stream.read(readBuffer));

        final ByteBuffer buffer = ByteBuffer.allocate(20);
        buffer.put("can't push".getBytes("UTF-8")).flip();
        stream.push(buffer);
        assertEquals(0, stream.available());
        assertEquals(-1, stream.read());
        assertEquals(-1, stream.read(readBuffer));
        assertEquals(0, stream.skip(11));
        assertCantPush();

        // close is idempotent
        stream.close();
        assertEquals(-1, stream.read());
        assertEquals(-1, stream.read(readBuffer));
        assertEquals(0, stream.skip(1000));
        assertCantPush();
        stream.pushException(new IOException("test"));

        assertHandledMessages(true);
    }

    public static class TestInputHandler implements BufferPipeInputStream.InputHandler {

        private Collection<String> messages = new ArrayList<String>();
        private boolean closed = false;

        @Override
        public void acknowledge(Pooled<ByteBuffer> pooled) throws IOException {
            if (closed) {
                fail("Stream is closed already");
            }
            pooled.getResource().flip();
            messages.add(Buffers.getModifiedUtf8(pooled.getResource()));
        }

        @Override
        public void close() throws IOException {
            closed = true;
        }

        public Iterator<String> getHandledMessages() {
            return messages.iterator();
        }

        public boolean isClosed() {
            return closed;
        }
    }

    private void assertHandledMessages(boolean closed, String... messages) {
        final Iterator<String> handledMessages = handler.getHandledMessages();
        for (String message: messages) {
            try {
                assertEquals(message, handledMessages.next());
            } catch (NoSuchElementException e) {
                fail("Message " + message + " is not handled");
            }
        }
        assertFalse("There is one or more unexpected handled messages", handledMessages.hasNext());
        assertEquals(closed, handler.isClosed());
    }

    private void assertCantPush() throws IOException, UnsupportedEncodingException {
        // once a failure has been pushed, we can't push anything else
        assertEquals(0, stream.available());
        final ByteBufferSlicePool bufferPool = new ByteBufferSlicePool(5, 5);
        final Pooled<ByteBuffer> pooledBuffer = bufferPool.allocate();
        pooledBuffer.getResource().put("test".getBytes("UTF-8")).flip();
        stream.push(pooledBuffer.getResource());
        assertEquals(0, stream.available());
        stream.push(pooledBuffer);
        assertEquals(0, stream.available());
        // check pooledBuffer resource is freed
        IllegalStateException expected = null;
        try {
            pooledBuffer.getResource();
        } catch (IllegalStateException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    private static class PushTask implements Runnable {
        private final BufferPipeInputStream stream;
        private final String message;

        public PushTask(BufferPipeInputStream s, String m) {
            stream = s;
            message = m;
        }

        public void run() {
            final ByteBuffer buffer = ByteBuffer.allocate(message.length());
            try {
                buffer.put(message.getBytes("UTF-8")).flip();
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
            stream.push(buffer);
        }
    }

    private static class ReadTask implements Runnable {

        private final BufferPipeInputStream stream;
        private final int length;
        private int readResult;
        private byte[] readBuffer;
        private IOException failure;

        public ReadTask(BufferPipeInputStream s) {
            this(s, -1);
        }

        public ReadTask(BufferPipeInputStream s, int l) {
            stream = s;
            length = l;
        }

        @Override
        public void run() {
            readBuffer = length == -1? new byte[50]: new byte[length];
            try {
                readResult = stream.read(readBuffer);
            } catch (IOException e) {
                failure = e;
            }
        }

        public int getReadResult() {
            return readResult;
        }

        public byte[] getReadBuffer() {
            return readBuffer;
        }

        public IOException getFailure() {
            return failure;
        }
    }

    private static class ReadByteTask implements Runnable {

        private final BufferPipeInputStream stream;
        private int readResult;
        private IOException failure;

        public ReadByteTask(BufferPipeInputStream s) {
            stream = s;
        }

        @Override
        public void run() {
            try {
                readResult = stream.read();
            } catch (IOException e) {
                failure = e;
            }
        }

        public int getReadResult() {
            return readResult;
        }

        public IOException getFailure() {
            return failure;
        }
    }

    private static class SkipTask implements Runnable {

        private final BufferPipeInputStream stream;
        private final int howManyBytes;
        private long skipResult;
        private IOException failure;

        public SkipTask(BufferPipeInputStream s, int l) {
            stream = s;
            howManyBytes = l;
        }

        @Override
        public void run() {
            try {
                skipResult = stream.skip(howManyBytes);
            } catch (IOException e) {
                failure = e;
            }
        }

        public long getSkipResult() {
            return skipResult;
        }

        public IOException getFailure() {
            return failure;
        }
    }
}
