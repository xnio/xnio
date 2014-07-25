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

package org.xnio.streams;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.xnio.AssertReadWrite.assertReadMessage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

import org.junit.Test;

/**
 * Test for {@link Pipe}.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
public class PipeTestCase {

    @Test
    public void readAndWriteBytes() throws IOException {
        final Pipe pipe = new Pipe(100);
        final InputStream inputStream = pipe.getIn();
        assertNotNull(inputStream);
        assertNotNull(inputStream.toString());
        final OutputStream outputStream = pipe.getOut();
        assertNotNull(outputStream);
        assertNotNull(outputStream.toString());
        outputStream.write('a');
        assertEquals('a', inputStream.read());
        close(outputStream);
        close(inputStream);
    }

    @Test
    public void readAndWriteByteArray() throws IOException {
        final Pipe pipe = new Pipe(100);
        final InputStream inputStream = pipe.getIn();
        assertNotNull(inputStream);
        assertNotNull(inputStream.toString());
        final OutputStream outputStream = pipe.getOut();
        assertNotNull(outputStream);
        assertNotNull(outputStream.toString());
        outputStream.write("array".getBytes());
        byte[] bytes = new byte[10];
        assertEquals(5, inputStream.read(bytes));
        assertReadMessage(bytes, "array");
        close(outputStream);
        close(inputStream);
    }

    @Test
    public void readAndWriteBytesWithBufferOverflow() throws IOException {
        final Pipe pipe = new Pipe(3);
        final InputStream inputStream = pipe.getIn();
        assertNotNull(inputStream);
        assertNotNull(inputStream.toString());
        final OutputStream outputStream = pipe.getOut();
        assertNotNull(outputStream);
        assertNotNull(outputStream.toString());
        outputStream.write('a');
        outputStream.write('b');
        outputStream.write('c');
        assertEquals('a', inputStream.read());
        assertEquals('b', inputStream.read());
        assertEquals('c', inputStream.read());
        outputStream.write('d');
        outputStream.write('e');
        outputStream.write('f');
        assertEquals('d', inputStream.read());
        assertEquals('e', inputStream.read());
        assertEquals('f', inputStream.read());
        close(outputStream);
        close(inputStream);
    }

    @Test
    public void readAndWriteByteArrayWithBufferOverflow() throws IOException {
        final Pipe pipe = new Pipe(3);
        final InputStream inputStream = pipe.getIn();
        assertNotNull(inputStream);
        assertNotNull(inputStream.toString());
        final OutputStream outputStream = pipe.getOut();
        assertNotNull(outputStream);
        assertNotNull(outputStream.toString());
        outputStream.write("abc".getBytes());
        byte[] bytes = new byte[10];
        assertEquals(3, inputStream.read(bytes));
        assertReadMessage(bytes, "abc");
        outputStream.write("def".getBytes());
        inputStream.read(bytes, 3, 3);
        assertReadMessage(bytes, "abc", "def");
        close(outputStream);
        close(inputStream);
    }

    @Test
    public void readByteFromClosedStream() throws IOException {
        final Pipe pipe = new Pipe(100);
        final InputStream inputStream = pipe.getIn();
        assertNotNull(inputStream);
        assertNotNull(inputStream.toString());
        final OutputStream outputStream = pipe.getOut();
        assertNotNull(outputStream);
        assertNotNull(outputStream.toString());
        outputStream.write('a');
        close(outputStream);
        assertEquals('a', inputStream.read());
        assertEquals(-1, inputStream.read());
        close(inputStream);
    }

    @Test
    public void readByteArrayFromClosedStream() throws IOException {
        final Pipe pipe = new Pipe(100);
        final InputStream inputStream = pipe.getIn();
        assertNotNull(inputStream);
        assertNotNull(inputStream.toString());
        final OutputStream outputStream = pipe.getOut();
        assertNotNull(outputStream);
        assertNotNull(outputStream.toString());
        outputStream.write('a');
        close(outputStream);
        byte[] bytes = new byte[5];
        assertEquals(1, inputStream.read(bytes));
        assertEquals('a', bytes[0]);
        assertEquals(-1, inputStream.read(bytes, 1, 4));
        close(inputStream);
    }

    @Test
    public void readFromEmptyClosedStream() throws IOException {
        final Pipe pipe = new Pipe(100);
        final InputStream inputStream = pipe.getIn();
        final OutputStream outputStream = pipe.getOut();
        assertNotNull(outputStream);
        assertNotNull(outputStream.toString());
        close(outputStream);
        assertEquals(-1, inputStream.read());
        assertEquals(-1, inputStream.read(new byte[3]));
        close(inputStream);
    }

    @Test
    public void readByteBlocksUntilWrite() throws Exception {
        final Pipe pipe = new Pipe(100);
        final InputStream inputStream = pipe.getIn();
        final OutputStream outputStream = pipe.getOut();
        final ReadByteTask readTask = new ReadByteTask(inputStream);
        final Thread readThread = new Thread(readTask);
        readThread.start();
        readThread.join(200);
        assertTrue(readThread.isAlive());
        outputStream.write('b');
        readThread.join();
        assertEquals('b', readTask.getReadResult());
    }

    @Test
    public void readByteArrayBlocksUntilWrite1() throws Exception {
        final Pipe pipe = new Pipe(100);
        final InputStream inputStream = pipe.getIn();
        final OutputStream outputStream = pipe.getOut();
        byte[] bytes = new byte[5];
        final ReadByteArrayTask readTask = new ReadByteArrayTask(inputStream, bytes);
        final Thread readThread = new Thread(readTask);
        readThread.start();
        readThread.join(200);
        assertTrue(readThread.isAlive());
        outputStream.write("block".getBytes());
        readThread.join();
        assertEquals(5, readTask.getReadResult());
        assertReadMessage(bytes, "block");
    }

    @Test
    public void readByteArrayBlocksUntilWrite2() throws Exception {
        final Pipe pipe = new Pipe(10);
        final InputStream inputStream = pipe.getIn();
        final OutputStream outputStream = pipe.getOut();
        byte[] bytes1 = new byte[15];
        byte[] bytes2 = new byte[15];
        final ReadByteArrayTask readTask1 = new ReadByteArrayTask(inputStream, bytes1);
        final ReadByteArrayTask readTask2 = new ReadByteArrayTask(inputStream, bytes2);
        final Thread readThread1 = new Thread(readTask1);
        final Thread readThread2 = new Thread(readTask2);
        readThread1.start();
        readThread1.join(200);
        readThread2.start();
        readThread2.join(200);
        assertTrue(readThread1.isAlive());
        assertTrue(readThread2.isAlive());
        outputStream.write("abcdefghij".getBytes());
        outputStream.write("klmnopqrst".getBytes());
        readThread1.join();
        readThread2.join();
        outputStream.write("klmnopqrst".getBytes());
        assertEquals(10, readTask1.getReadResult());
        if (bytes1[0] == 'a') {
            assertReadMessage(bytes1, "abcdefghij");
            assertReadMessage(bytes2, "klmnopqrst");
        } else {
            assertReadMessage(bytes1, "klmnopqrst");
            assertReadMessage(bytes2, "abcdefghij");
        }
    }

    @Test
    public void readBytesBlocksUntilCloseOutput() throws Exception {
        final Pipe pipe = new Pipe(100);
        final InputStream inputStream = pipe.getIn();
        final OutputStream outputStream = pipe.getOut();
        final ReadByteTask readTask = new ReadByteTask(inputStream);
        final Thread readThread = new Thread(readTask);
        readThread.start();
        readThread.join(200);
        assertTrue(readThread.isAlive());
        outputStream.close();
        readThread.join();
        assertEquals(-1, readTask.getReadResult());
    }

    @Test
    public void readByteArrayBlocksUntilCloseOutput() throws Exception {
        final Pipe pipe = new Pipe(100);
        final InputStream inputStream = pipe.getIn();
        final OutputStream outputStream = pipe.getOut();
        byte[] bytes = new byte[5];
        final ReadByteArrayTask readTask = new ReadByteArrayTask(inputStream, bytes);
        final Thread readThread = new Thread(readTask);
        readThread.start();
        readThread.join(200);
        assertTrue(readThread.isAlive());
        outputStream.close();
        readThread.join();
        assertEquals(-1, readTask.getReadResult());
    }

    @Test
    public void readBytesBlocksUntilWriteAndCloseOutput() throws Exception {
        final Pipe pipe = new Pipe(100);
        final InputStream inputStream = pipe.getIn();
        final OutputStream outputStream = pipe.getOut();
        final ReadByteTask readTask = new ReadByteTask(inputStream);
        final Thread readThread = new Thread(readTask);
        readThread.start();
        readThread.join(200);
        assertTrue(readThread.isAlive());
        outputStream.write('a');
        outputStream.close();
        readThread.join();
        assertEquals('a', readTask.getReadResult());
        assertEquals(-1, inputStream.read());
    }

    @Test
    public void readByteArrayBlocksUntilWriteAndCloseOutput() throws Exception {
        final Pipe pipe = new Pipe(100);
        final InputStream inputStream = pipe.getIn();
        final OutputStream outputStream = pipe.getOut();
        byte[] bytes = new byte[5];
        final ReadByteArrayTask readTask = new ReadByteArrayTask(inputStream, bytes);
        final Thread readThread = new Thread(readTask);
        readThread.start();
        readThread.join(200);
        assertTrue(readThread.isAlive());
        outputStream.write('a');
        outputStream.close();
        readThread.join();
        assertEquals(1, readTask.getReadResult());
        assertReadMessage(bytes, "a");
        assertEquals(-1, inputStream.read(bytes));
    }

    @Test
    public void readBlocksUntilCloseInput() throws Exception {
        final Pipe pipe = new Pipe(100);
        final InputStream inputStream = pipe.getIn();
        final ReadByteTask readTask1 = new ReadByteTask(inputStream);
        final ReadByteArrayTask readTask2 = new ReadByteArrayTask(inputStream, new byte[3]);
        final Thread readThread1 = new Thread(readTask1);
        final Thread readThread2 = new Thread(readTask2);
        readThread1.start();
        readThread2.start();
        readThread1.join(200);
        assertTrue(readThread1.isAlive());
        readThread2.join(200);
        assertTrue(readThread2.isAlive());
        inputStream.close();
        readThread1.join();
        readThread2.join();
        assertEquals(-1, readTask1.getReadResult());
        assertEquals(-1, readTask2.getReadResult());
    }

    @Test
    public void writeByteBlocksUntilRead() throws Exception {
        final Pipe pipe = new Pipe(5);
        final InputStream inputStream = pipe.getIn();
        final OutputStream outputStream = pipe.getOut();
        outputStream.write("input".getBytes());
        final WriteByteTask writeTask = new WriteByteTask(outputStream, (byte) '0');
        final Thread writeThread = new Thread(writeTask);
        writeThread.start();
        writeThread.join(200);
        assertTrue(writeThread.isAlive());
        assertEquals('i', inputStream.read());
        writeThread.join();
        assertNull(writeTask.getWriteException());
        assertEquals('n', inputStream.read());
        assertEquals('p', inputStream.read());
        assertEquals('u', inputStream.read());
        assertEquals('t', inputStream.read());
        assertEquals('0', inputStream.read());
        close(inputStream);
        close(outputStream);
    }

    @Test
    public void writeByteArrayBlocksUntilRead() throws Exception {
        final Pipe pipe = new Pipe(5);
        final InputStream inputStream = pipe.getIn();
        final OutputStream outputStream = pipe.getOut();
        outputStream.write("12345".getBytes());
        final WriteByteArrayTask writeTask = new WriteByteArrayTask(outputStream, "67890".getBytes());
        final Thread writeThread = new Thread(writeTask);
        writeThread.start();
        writeThread.join(200);
        assertTrue(writeThread.isAlive());
        assertEquals('1', inputStream.read());
        assertEquals('2', inputStream.read());
        final byte[] bytes = new byte[5];
        int read = inputStream.read(bytes);
        writeThread.join();
        assertNull(writeTask.getWriteException());
        assertReadMessage(bytes, "34567".substring(0, read)); // assert we actually got the amount of bytes returned by read operation
        switch (read) {
            case 0:
                assertEquals('3', inputStream.read());
            case 1:
                assertEquals('4', inputStream.read());
            case 2:
                assertEquals('5', inputStream.read());
            case 3:
                assertEquals('6', inputStream.read());
            case 4:
                assertEquals('7', inputStream.read());
                break;
            default:
                assertFalse("Should've read up to 5 bytes into 5-length bytes array, but read: " + read, read > 5);
        }
        assertEquals('8', inputStream.read());
        assertEquals('9', inputStream.read());
        assertEquals('0', inputStream.read());
        close(inputStream);
        close(outputStream);
    }

    @Test
    public void writeByteBlocksUntilCloseInput() throws Exception {
        final Pipe pipe = new Pipe(1);
        final InputStream inputStream = pipe.getIn();
        final OutputStream outputStream = pipe.getOut();
        outputStream.write('1');
        final WriteByteTask writeTask = new WriteByteTask(outputStream, (byte) '2');
        final Thread writeThread = new Thread(writeTask);
        writeThread.start();
        writeThread.join(200);
        assertTrue(writeThread.isAlive());
        close(inputStream);
        writeThread.join();
        assertNotNull(writeTask.getWriteException());
        assertEquals(-1, inputStream.read());
    }

    @Test
    public void writeByteArrayBlocksUntilCloseInput() throws Exception {
        final Pipe pipe = new Pipe(1);
        final InputStream inputStream = pipe.getIn();
        final OutputStream outputStream = pipe.getOut();
        outputStream.write('1');
        final WriteByteArrayTask writeTask = new WriteByteArrayTask(outputStream, "2".getBytes());
        final Thread writeThread = new Thread(writeTask);
        writeThread.start();
        writeThread.join(200);
        assertTrue(writeThread.isAlive());
        close(inputStream);
        writeThread.join();
        assertNotNull(writeTask.getWriteException());
        assertEquals(-1, inputStream.read());
    }

    @Test
    public void writeByteBlocksUntilCloseOutput() throws Exception {
        final Pipe pipe = new Pipe(3);
        final InputStream inputStream = pipe.getIn();
        final OutputStream outputStream = pipe.getOut();
        outputStream.write("123".getBytes());
        final WriteByteTask writeTask = new WriteByteTask(outputStream, (byte) '4');
        final Thread writeThread = new Thread(writeTask);
        writeThread.start();
        writeThread.join(200);
        assertTrue(writeThread.isAlive());
        close(outputStream);
        writeThread.join();
        assertNotNull(writeTask.getWriteException());
        final byte[] bytes = new byte[5];
        assertEquals(3, inputStream.read(bytes));
        assertReadMessage(bytes, "123");
    }

    @Test
    public void writeByteArrayBlocksUntilCloseOutput() throws Exception {
        final Pipe pipe = new Pipe(3);
        final InputStream inputStream = pipe.getIn();
        final OutputStream outputStream = pipe.getOut();
        outputStream.write("123".getBytes());
        final WriteByteArrayTask writeTask = new WriteByteArrayTask(outputStream, "45".getBytes());
        final Thread writeThread = new Thread(writeTask);
        writeThread.start();
        writeThread.join(200);
        assertTrue(writeThread.isAlive());
        close(outputStream);
        writeThread.join();
        assertNotNull(writeTask.getWriteException());
        final byte[] bytes = new byte[5];
        assertEquals(3, inputStream.read(bytes));
        assertReadMessage(bytes, "123");
    }

    @Test
    public void awaitReadCloses() throws Exception {
        final Pipe pipe = new Pipe(100);
        final Thread awaitThread = new Thread(new AwaitTask(pipe));
        awaitThread.start();
        awaitThread.join(200);
        assertTrue(awaitThread.isAlive());
        close(pipe.getIn());
        awaitThread.join();
    }

    @Test
    public void awaitReadClosesAfterWriteCloses() throws Exception {
        final Pipe pipe = new Pipe(100);
        final Thread awaitThread = new Thread(new AwaitTask(pipe));
        awaitThread.start();
        awaitThread.join(200);
        assertTrue(awaitThread.isAlive());
        close(pipe.getOut());
        awaitThread.join(200);
        assertTrue(awaitThread.isAlive());
        close(pipe.getIn());
        awaitThread.join();
    }

    @Test
    public void wrappedBytes() throws IOException {
        final Pipe pipe = new Pipe(10);
        final InputStream inputStream = pipe.getIn();
        final OutputStream outputStream = pipe.getOut();
        outputStream.write("12345".getBytes());
        byte[] bytes = new byte[20];
        assertEquals(5, inputStream.read(bytes));
        assertReadMessage(bytes, "12345");

        outputStream.write("6789012345".getBytes());
        assertEquals(10, inputStream.read(bytes, 5, 15));
        assertReadMessage(bytes, "12345", "67890", "12345");

        outputStream.write("12345678".getBytes());
        assertEquals(0, inputStream.read(bytes, 15, 0));
        assertEquals(5, inputStream.read(bytes, 15, 5));
        assertReadMessage(bytes, "12345", "67890", "12345", "12345");

        assertEquals('6', inputStream.read());
        assertEquals('7', inputStream.read());
        assertEquals('8', inputStream.read());

        outputStream.write("9012345".getBytes());
        outputStream.write("678".getBytes());
        assertEquals(10, inputStream.read(bytes));
        outputStream.write("0123456".getBytes());
        assertEquals(5, inputStream.read(bytes, 10, 5));
        assertReadMessage(bytes, "9012345", "678", "01234");
        outputStream.write("7890123".getBytes());
        assertEquals(9, inputStream.read(bytes));
        assertReadMessage(bytes, "56", "7890123");
    }

    @Test
    public void closeOnlyInputStream() throws UnsupportedEncodingException, IOException {
        final Pipe pipe = new Pipe(100);
        final InputStream inputStream = pipe.getIn();
        final OutputStream outputStream = pipe.getOut();
        close(inputStream);
        IOException expected = null;
        try {
            outputStream.write("abc".getBytes("UTF-8"));
        } catch (IOException e) {
            expected = e;
        }
        assertNotNull(expected);
        assertEquals(-1, inputStream.read());
        assertEquals(-1, inputStream.read(new byte[1]));
    }

    private void close(OutputStream stream) throws IOException {
        stream.close();
        IOException expected = null;
        try {
            stream.write('a');
        } catch (IOException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            stream.write("abc".getBytes("UTF-8"));
        } catch (IOException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            stream.write("#@abc#$%^".getBytes("UTF-8"), 2, 3);
        } catch (IOException e) {
            expected = e;
        }
        assertNotNull(expected);
        // close must be idempotent
        stream.close();
        expected = null;
        try {
            stream.write('a');
        } catch (IOException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            stream.write("abc".getBytes("UTF-8"));
        } catch (IOException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            stream.write("#@abc#$%^".getBytes("UTF-8"), 2, 3);
        } catch (IOException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    private void close(InputStream stream) throws IOException {
        stream.close();
        assertEquals(-1, stream.read());
        assertEquals(-1, stream.read(new byte[10]));
        assertEquals(-1, stream.read(new byte[10], 1, 7));
        // close must be idempotent
        stream.close();
        assertEquals(-1, stream.read());
        assertEquals(-1, stream.read(new byte[10]));
        assertEquals(-1, stream.read(new byte[10], 1, 7));
    }


    private static class ReadByteTask implements Runnable {

        private final InputStream stream;
        private int readResult;

        public ReadByteTask(InputStream s) {
            stream = s;
        }

        @Override
        public void run() {
            try {
                readResult = stream.read();
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }

        public int getReadResult() {
            return readResult;
        }
    }

    private static class ReadByteArrayTask implements Runnable {

        private final InputStream stream;
        private final byte[] bytes;
        private int readResult;

        public ReadByteArrayTask(InputStream s, byte[] b) {
            stream = s;
            bytes = b;
        }

        @Override
        public void run() {
            try {
                readResult = stream.read(bytes);
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }

        public int getReadResult() {
            return readResult;
        }
    }

    private static class WriteByteTask implements Runnable {

        private final OutputStream stream;
        private final byte writeByte;
        private IOException exception;

        public WriteByteTask(OutputStream s, byte b) {
            stream = s;
            writeByte = b;
        }

        @Override
        public void run() {
            try {
                stream.write(writeByte);
            } catch (IOException e) {
                exception = e;
            }
            
        }

        public IOException getWriteException() {
            return exception;
        }
    }

    private static class WriteByteArrayTask implements Runnable {

        private final OutputStream stream;
        private final byte[] bytes;
        private IOException exception;

        public WriteByteArrayTask(OutputStream s, byte[] b) {
            stream = s;
            bytes = b;
        }

        @Override
        public void run() {
            try {
                stream.write(bytes);
            } catch (IOException e) {
                exception = e;
            }
            
        }

        public IOException getWriteException() {
            return exception;
        }
    }

    private static class AwaitTask implements Runnable {

        private final Pipe pipe;

        public AwaitTask(Pipe p) {
            pipe = p;
        }

        @Override
        public void run() {
            pipe.await();
        }
    }
}
