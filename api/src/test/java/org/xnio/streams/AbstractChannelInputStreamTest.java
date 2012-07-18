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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.xnio.channels.ReadTimeoutException;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.mock.ConnectedStreamChannelMock;

/**
 * Abstract test for channel input streams.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
public abstract class AbstractChannelInputStreamTest<T extends InputStream> extends AbstractChannelStreamTest<T> {

    /**
     * Creates the channel input stream.
     * 
     * @param sourceChannel       the source channel
     * @param internalBufferSize  the size of the stream's internal buffer size, if applicable
     * @return                    the created channel input stream
     */
    protected abstract T createChannelInputStream(StreamSourceChannel sourceChannel, int internalBufferSize);

    /**
     * Creates the channel input stream with read timeout enabled.
     * 
     * @param sourceChannel       the source channel
     * @param timeout             the read timeout
     * @param timeUnit            the read timeout unit
     * @param internalBufferSize  the size of the stream's internal buffer size, if applicable
     * @return                    the created channel input stream
     */
    protected abstract T createChannelInputStream(StreamSourceChannel sourceChannel, long timeout, TimeUnit timeUnit, int internalBufferSize);

    /**
     * Returns the read timeout of {@code stream}.
     * 
     * @param stream    the channel input stream
     * @param timeUnit  the timeout unit
     * @return          the read timeout of {@code stream}
     */
    protected abstract long getReadTimeout(T stream, TimeUnit timeUnit);

    /**
     * Sets the read timeout for {@code stream}.
     * 
     * @param stream   the channel input stream
     * @param timeout  the timeout
     * @param timeUnit the timeout unit
     */
    protected abstract void setReadTimeout(T stream, int timeout, TimeUnit timeUnit);

    /**
     * Asserts that the available bytes in {@code stream} are as expected.
     * 
     * @param stream             the channel input stream
     * @param availableInBuffer  expected amount of bytes available in the stream's internal buffer, if applicable
     * @param availableTotal     the total amount of bytes available for reading in {@code stream}
     * @throws IOException
     */
    protected abstract void assertAvailableBytes( T stream, int availableInBuffer, int availableTotal) throws IOException;

    @Test
    public void illegalConstructorArguments() {
        final ConnectedStreamChannelMock sourceChannel = new ConnectedStreamChannelMock();
        IllegalArgumentException expected = null;
        // null source channel
        try {
            createChannelInputStream(null, 10);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            createChannelInputStream(null, 5, TimeUnit.SECONDS, 10);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        // timeout < 0
        try {
            createChannelInputStream(sourceChannel, -20, TimeUnit.DAYS, 20);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        // time unit is null
        try {
            createChannelInputStream(sourceChannel, 3000, null, 20);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    @Test
    public void readBytesAfterAvailable() throws IOException {
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        final T stream = createChannelInputStream(channelMock, 10);
        channelMock.setReadData("load this message");
        channelMock.setEof();
        channelMock.enableRead(true);
        assertAvailableBytes(stream, 10, 17);
        assertEquals('l', stream.read());
        assertAvailableBytes(stream, 9, 16);
        assertEquals('o', stream.read());
        assertAvailableBytes(stream, 8, 15);
        assertEquals('a', stream.read());
        assertAvailableBytes(stream, 7, 14);
        assertEquals('d', stream.read());
        assertAvailableBytes(stream, 6, 13);
        assertEquals(' ', stream.read());
        assertAvailableBytes(stream, 5, 12);
        assertEquals('t', stream.read());
        assertAvailableBytes(stream, 4, 11);
        assertEquals('h', stream.read());
        assertAvailableBytes(stream, 3, 10);
        assertEquals('i', stream.read());
        assertAvailableBytes(stream, 2, 9);
        assertEquals('s', stream.read());
        assertAvailableBytes(stream, 1, 8);
        assertEquals(' ', stream.read());
        assertAvailableBytes(stream, 7, 7);
        assertEquals('m', stream.read());
        assertAvailableBytes(stream, 6, 6);
        assertEquals('e', stream.read());
        assertAvailableBytes(stream, 5, 5);
        assertEquals('s', stream.read());
        assertAvailableBytes(stream, 4, 4);
        assertEquals('s', stream.read());
        assertAvailableBytes(stream, 3, 3);
        assertEquals('a', stream.read());
        assertAvailableBytes(stream, 2, 2);
        assertEquals('g', stream.read());
        assertAvailableBytes(stream, 1, 1);
        assertEquals('e', stream.read());
        assertAvailableBytes(stream, 0, 0);
        assertEquals(-1, stream.read());
        assertAvailableBytes(stream, 0, 0);
    }

    @Test
    public void readByteArrayAfterAvailable() throws IOException {
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        final T stream = createChannelInputStream(channelMock, 10);
        channelMock.setReadData("load this message");
        channelMock.setEof();
        channelMock.enableRead(true);
        assertAvailableBytes(stream, 10, 17);
        byte[] bytes = new byte[20];
        assertEquals(17, stream.read(bytes));
        assertEquals('l', bytes[0]);
        assertEquals('o', bytes[1]);
        assertEquals('a', bytes[2]);
        assertEquals('d', bytes[3]);
        assertEquals(' ', bytes[4]);
        assertEquals('t', bytes[5]);
        assertEquals('h', bytes[6]);
        assertEquals('i', bytes[7]);
        assertEquals('s', bytes[8]);
        assertEquals(' ', bytes[9]);
        assertEquals('m', bytes[10]);
        assertEquals('e', bytes[11]);
        assertEquals('s', bytes[12]);
        assertEquals('s', bytes[13]);
        assertEquals('a', bytes[14]);
        assertEquals('g', bytes[15]);
        assertEquals('e', bytes[16]);
        assertEquals(-1, stream.read(bytes));
    }

    @Test
    public void readBytesAndByteArrays() throws IOException {
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        final T stream = createChannelInputStream(channelMock, 10);
        assertAvailableBytes(stream, 0, 0);
        channelMock.setReadData("data");
        channelMock.enableRead(true);
        assertEquals('d', stream.read());
        assertAvailableBytes(stream, 3, 3);
        assertEquals('a', stream.read());
        assertEquals('t', stream.read());
        assertEquals('a', stream.read());
        assertAvailableBytes(stream, 0, 0);
        channelMock.setReadData("more data");
        assertAvailableBytes(stream, 9, 9);
        byte[] bytes = new byte[10];
        assertEquals(9, stream.read(bytes, 1, 9));
        assertEquals(0, bytes[0]);
        assertEquals('m', bytes[1]);
        assertEquals('o', bytes[2]);
        assertEquals('r', bytes[3]);
        assertEquals('e', bytes[4]);
        assertEquals(' ', bytes[5]);
        assertEquals('d', bytes[6]);
        assertEquals('a', bytes[7]);
        assertEquals('t', bytes[8]);
        assertEquals('a', bytes[9]);
        channelMock.setEof();
        assertEquals(-1, stream.read());
        assertEquals(-1, stream.read(bytes, 0, 10));
        assertEquals(-1, stream.read());
        assertEquals(-1, stream.read(bytes, 0, 10));
    }

    @Test
    public void readByteArraysAndBytes() throws IOException {
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        final T stream = createChannelInputStream(channelMock, 10);
        assertAvailableBytes(stream, 0, 0);
        channelMock.setReadData("data");
        channelMock.enableRead(true);
        byte[] bytes = new byte[5];
        assertEquals(4, stream.read(bytes));
        assertEquals('d', bytes[0]);
        assertEquals('a', bytes[1]);
        assertEquals('t', bytes[2]);
        assertEquals('a', bytes[3]);
        assertAvailableBytes(stream, 0, 0);
        channelMock.setReadData("more data");
        assertAvailableBytes(stream, 9, 9);
        assertEquals('m', stream.read());
        assertAvailableBytes(stream, 8, 8);
        assertEquals('o', stream.read());
        assertAvailableBytes(stream, 7, 7);
        assertEquals('r', stream.read());
        assertAvailableBytes(stream, 6, 6);
        assertEquals('e', stream.read());
        assertAvailableBytes(stream, 5, 5);
        assertEquals(' ', stream.read());
        assertAvailableBytes(stream, 4, 4);
        assertEquals('d', stream.read());
        assertAvailableBytes(stream, 3, 3);
        assertEquals('a', stream.read());
        assertAvailableBytes(stream, 2, 2);
        assertEquals('t', stream.read());
        assertAvailableBytes(stream, 1, 1);
        assertEquals('a', stream.read());
        assertAvailableBytes(stream, 0, 0);
        channelMock.setEof();
        assertEquals(-1, stream.read());
        assertEquals(-1, stream.read());
        assertEquals(-1, stream.read());
        assertEquals(-1, stream.read(bytes));
        assertEquals(-1, stream.read(bytes));
        assertEquals(-1, stream.read(bytes));
    }

    @Test
    public void readBlocks() throws Exception {
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        final T stream = createChannelInputStream(channelMock, 20);
        channelMock.setReadData("stream read blocks until channel read is enabled");
        // create and start read threa
        final ReadByteTask readByteTask = new ReadByteTask(stream);
        final Thread readByteThread = new Thread(readByteTask);
        readByteThread.start();
        // thread cant complete
        readByteThread.join(200);
        assertTrue(readByteThread.isAlive());
        // enable read, now thread can complete with a result
        channelMock.enableRead(true);
        readByteThread.join();
        assertEquals('s', readByteTask.getReadResult());
        assertAvailableBytes(stream, 19, 19);
    }

    @Test
    public void readByteArrayBlocks1() throws Exception {
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        final T stream = createChannelInputStream(channelMock, 20);
        channelMock.setReadData("stream read blocks until channel read is enabled");
        // create and start read thread
        byte[] bytes = new byte[25];
        final ReadBytesTask readBytesTask = new ReadBytesTask(stream, bytes);
        final Thread readBytesThread = new Thread(readBytesTask);
        readBytesThread.start();
        // thread cant complete
        readBytesThread.join(200);
        assertTrue(readBytesThread.isAlive());
        // enable read, now thread can complete with a result
        channelMock.enableRead(true);
        readBytesThread.join();
        assertEquals(25, readBytesTask.getReadResult());
        assertEquals('s', bytes[0]);
        assertEquals('t', bytes[1]);
        assertEquals('r', bytes[2]);
        assertEquals('e', bytes[3]);
        assertEquals('a', bytes[4]);
        assertEquals('m', bytes[5]);
        assertEquals(' ', bytes[6]);
        assertEquals('r', bytes[7]);
        assertEquals('e', bytes[8]);
        assertEquals('a', bytes[9]);
        assertEquals('d', bytes[10]);
        assertEquals(' ', bytes[11]);
        assertEquals('b', bytes[12]);
        assertEquals('l', bytes[13]);
        assertEquals('o', bytes[14]);
        assertEquals('c', bytes[15]);
        assertEquals('k', bytes[16]);
        assertEquals('s', bytes[17]);
        assertEquals(' ', bytes[18]);
        assertEquals('u', bytes[19]);
        assertEquals('n', bytes[20]);
        assertEquals('t', bytes[21]);
        assertEquals('i', bytes[22]);
        assertEquals('l', bytes[23]);
        assertEquals(' ', bytes[24]);
        assertAvailableBytes(stream, 20, 23);
    }

    @Test
    public void readByteArrayBlocks2() throws Exception {
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        final T stream = createChannelInputStream(channelMock, 20);
        channelMock.setReadData("read blocks");
        // create and start read thread
        byte[] bytes = new byte[25];
        final ReadBytesTask readBytesTask = new ReadBytesTask(stream, bytes);
        final Thread readBytesThread = new Thread(readBytesTask);
        readBytesThread.start();
        // thread cant complete
        readBytesThread.join(200);
        assertTrue(readBytesThread.isAlive());
        // enable read, now thread can complete with a result
        channelMock.enableRead(true);
        readBytesThread.join();
        assertEquals(11, readBytesTask.getReadResult());
        assertEquals('r', bytes[0]);
        assertEquals('e', bytes[1]);
        assertEquals('a', bytes[2]);
        assertEquals('d', bytes[3]);
        assertEquals(' ', bytes[4]);
        assertEquals('b', bytes[5]);
        assertEquals('l', bytes[6]);
        assertEquals('o', bytes[7]);
        assertEquals('c', bytes[8]);
        assertEquals('k', bytes[9]);
        assertEquals('s', bytes[10]);
        assertAvailableBytes(stream, 0, 0);
    }

    @Test
    public void readBytesAfterAvailableWithTimeout() throws IOException {
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        final T stream = createChannelInputStream(channelMock, 100, TimeUnit.MILLISECONDS, 10);
        assertEquals(100, getReadTimeout(stream, TimeUnit.MILLISECONDS));
        channelMock.setReadData("load this message");
        channelMock.setEof();
        channelMock.enableRead(true);
        assertAvailableBytes(stream, 10, 17);
        assertEquals('l', stream.read());
        assertAvailableBytes(stream, 9, 16);
        assertEquals('o', stream.read());
        assertAvailableBytes(stream, 8, 15);
        assertEquals('a', stream.read());
        assertAvailableBytes(stream, 7, 14);
        assertEquals('d', stream.read());
        assertAvailableBytes(stream, 6, 13);
        assertEquals(' ', stream.read());
        assertAvailableBytes(stream, 5, 12);
        assertEquals('t', stream.read());
        assertAvailableBytes(stream, 4, 11);
        assertEquals('h', stream.read());
        assertAvailableBytes(stream, 3, 10);
        assertEquals('i', stream.read());
        assertAvailableBytes(stream, 2, 9);
        assertEquals('s', stream.read());
        assertAvailableBytes(stream, 1, 8);
        assertEquals(' ', stream.read());
        assertAvailableBytes(stream, 7, 7);
        assertEquals('m', stream.read());
        assertAvailableBytes(stream, 6, 6);
        assertEquals('e', stream.read());
        assertAvailableBytes(stream, 5, 5);
        assertEquals('s', stream.read());
        assertAvailableBytes(stream, 4, 4);
        assertEquals('s', stream.read());
        assertAvailableBytes(stream, 3, 3);
        assertEquals('a', stream.read());
        assertAvailableBytes(stream, 2, 2);
        assertEquals('g', stream.read());
        assertAvailableBytes(stream, 1, 1);
        assertEquals('e', stream.read());
        assertAvailableBytes(stream, 0, 0);
        assertEquals(-1, stream.read());
        assertAvailableBytes(stream, 0, 0);
    }

    @Test
    public void readByteArrayAfterAvailableWithTimeout() throws IOException {
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        final T stream = createChannelInputStream(channelMock, 200, TimeUnit.MILLISECONDS, 10);
        assertEquals(200, getReadTimeout(stream, TimeUnit.MILLISECONDS));
        channelMock.setReadData("load this message");
        channelMock.setEof();
        channelMock.enableRead(true);
        assertAvailableBytes(stream, 10, 17);
        byte[] bytes = new byte[20];
        assertEquals(17, stream.read(bytes));
        assertEquals('l', bytes[0]);
        assertEquals('o', bytes[1]);
        assertEquals('a', bytes[2]);
        assertEquals('d', bytes[3]);
        assertEquals(' ', bytes[4]);
        assertEquals('t', bytes[5]);
        assertEquals('h', bytes[6]);
        assertEquals('i', bytes[7]);
        assertEquals('s', bytes[8]);
        assertEquals(' ', bytes[9]);
        assertEquals('m', bytes[10]);
        assertEquals('e', bytes[11]);
        assertEquals('s', bytes[12]);
        assertEquals('s', bytes[13]);
        assertEquals('a', bytes[14]);
        assertEquals('g', bytes[15]);
        assertEquals('e', bytes[16]);
        assertEquals(-1, stream.read(bytes));
    }

    @Test
    public void readBytesAndByteArraysWithTimeout() throws IOException {
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        final T stream = createChannelInputStream(channelMock, 0, TimeUnit.MICROSECONDS, 10);
        assertEquals(0, getReadTimeout(stream, TimeUnit.MICROSECONDS));
        assertEquals(0, getReadTimeout(stream, TimeUnit.MILLISECONDS));
        setReadTimeout(stream, 100, TimeUnit.MILLISECONDS);
        assertEquals(100, getReadTimeout(stream, TimeUnit.MILLISECONDS));
        // start read test
        assertAvailableBytes(stream, 0, 0);
        ReadTimeoutException expectedException = null;
        try {
            stream.read();
        } catch (ReadTimeoutException e) {
            expectedException = e;
        }
        assertNotNull(expectedException);

        channelMock.setReadData("data");
        channelMock.enableRead(true);
        assertEquals('d', stream.read());
        assertAvailableBytes(stream, 3, 3);
        assertEquals('a', stream.read());
        assertEquals('t', stream.read());
        assertEquals('a', stream.read());

        assertAvailableBytes(stream, 0, 0);
        expectedException = null;
        try {
            stream.read();
        } catch (ReadTimeoutException e) {
            expectedException = e;
        }
        assertNotNull(expectedException);
        final byte[] bytes = new byte[10];
        expectedException = null;
        try {
            stream.read(bytes);
        } catch (ReadTimeoutException e) {
            expectedException = e;
        }
        assertNotNull(expectedException);
        channelMock.setReadData("more data");
        assertAvailableBytes(stream, 9, 9);

        assertEquals(9, stream.read(bytes, 1, 9));
        assertEquals(0, bytes[0]);
        assertEquals('m', bytes[1]);
        assertEquals('o', bytes[2]);
        assertEquals('r', bytes[3]);
        assertEquals('e', bytes[4]);
        assertEquals(' ', bytes[5]);
        assertEquals('d', bytes[6]);
        assertEquals('a', bytes[7]);
        assertEquals('t', bytes[8]);
        assertEquals('a', bytes[9]);

        assertAvailableBytes(stream, 0, 0);
        expectedException = null;
        try {
            stream.read(bytes);
        } catch (ReadTimeoutException e) {
            expectedException = e;
        }
        assertNotNull(expectedException);

        channelMock.setEof();
        assertEquals(-1, stream.read());
        assertEquals(-1, stream.read(bytes, 0, 10));
        assertEquals(-1, stream.read());
        assertEquals(-1, stream.read(bytes, 0, 10));
    }

    @Test
    public void readByteArraysAndBytesWithTimeout() throws IOException {
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        // try using 10 microseconds, timeout value is NOT rounded up
        final T stream = createChannelInputStream(channelMock, 10, TimeUnit.MICROSECONDS, 10);
        assertEquals(10, getReadTimeout(stream, TimeUnit.MICROSECONDS));
        assertEquals(10000, getReadTimeout(stream, TimeUnit.NANOSECONDS));
        // start read test
        final byte[] bytes = new byte[5];
        assertAvailableBytes(stream, 0, 0);
        ReadTimeoutException expectedException = null;
        try {
            stream.read(bytes);
        } catch (ReadTimeoutException e) {
            expectedException = e;
        }
        assertNotNull(expectedException);

        channelMock.setReadData("data");
        channelMock.enableRead(true);

        assertEquals(0, stream.read(bytes, 0, -3));
        assertEquals(4, stream.read(bytes));
        assertEquals('d', bytes[0]);
        assertEquals('a', bytes[1]);
        assertEquals('t', bytes[2]);
        assertEquals('a', bytes[3]);

        assertAvailableBytes(stream, 0, 0);
        expectedException = null;
        try {
            stream.read();
        } catch (ReadTimeoutException e) {
            expectedException = e;
        }
        assertNotNull(expectedException);
        expectedException = null;
        try {
            stream.read(bytes);
        } catch (ReadTimeoutException e) {
            expectedException = e;
        }
        assertNotNull(expectedException);

        channelMock.setReadData("more data");
        assertAvailableBytes(stream, 9, 9);
        assertEquals('m', stream.read());
        assertAvailableBytes(stream, 8, 8);
        assertEquals('o', stream.read());
        assertAvailableBytes(stream, 7, 7);
        assertEquals('r', stream.read());
        assertAvailableBytes(stream, 6, 6);
        assertEquals('e', stream.read());
        assertAvailableBytes(stream, 5, 5);
        assertEquals(' ', stream.read());
        assertAvailableBytes(stream, 4, 4);
        assertEquals('d', stream.read());
        assertAvailableBytes(stream, 3, 3);
        assertEquals('a', stream.read());
        assertAvailableBytes(stream, 2, 2);
        assertEquals('t', stream.read());
        assertAvailableBytes(stream, 1, 1);
        assertEquals('a', stream.read());

        assertAvailableBytes(stream, 0, 0);
        expectedException = null;
        try {
            stream.read();
        } catch (ReadTimeoutException e) {
            expectedException = e;
        }
        assertNotNull(expectedException);

        channelMock.setEof();
        assertEquals(-1, stream.read());
        assertEquals(-1, stream.read());
        assertEquals(-1, stream.read());
        assertEquals(-1, stream.read(bytes));
        assertEquals(-1, stream.read(bytes));
        assertEquals(-1, stream.read(bytes));
    }

    @Test
    public void skip() throws IOException {
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        final T stream = createChannelInputStream(channelMock, 10);
        assertEquals(0, stream.skip(-1));
        assertEquals(0, stream.skip(0));
        channelMock.setReadData("skip all this - data");
        channelMock.enableRead(true);
        assertEquals(16, stream.skip(16));
        assertAvailableBytes(stream, 4, 4);
        assertEquals('d', stream.read());
        assertEquals('a', stream.read());
        assertEquals('t', stream.read());
        assertEquals('a', stream.read());

        channelMock.setReadData("skip again");
        assertAvailableBytes(stream, 10, 10);
        assertEquals(4, stream.skip(4));
        assertAvailableBytes(stream, 6, 6);
        assertEquals(' ', stream.read());
        assertEquals('a', stream.read());
        assertEquals('g', stream.read());
        assertEquals('a', stream.read());
        assertEquals('i', stream.read());
        assertEquals('n', stream.read());

        channelMock.setReadData("skip");
        assertAvailableBytes(stream, 4, 4);
        assertEquals(4, stream.skip(4));

        channelMock.setReadData("skip once more");
        assertEquals(14, stream.skip(14));

        channelMock.setReadData("more");
        channelMock.setEof();
        assertEquals(4, stream.skip(Integer.MAX_VALUE));
        assertAvailableBytes(stream, 0, 0);
    }

    @Test
    public void readThrowsException() throws IOException {
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        channelMock.close(); // channel mock will always throw ClosedChannelException
        final T stream = createChannelInputStream(channelMock, 10);
        // try to skip, test twice to make sure that buffer is kept consistent
        ClosedChannelException expected = null;
        try {
            stream.skip(10);
        } catch (ClosedChannelException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            stream.skip(10);
        } catch (ClosedChannelException e) {
            expected = e;
        }
        assertNotNull(expected);
        // try to read, test twice to make sure that buffer is kept consistent
        expected = null;
        try {
            stream.read();
        } catch (ClosedChannelException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            stream.read();
        } catch (ClosedChannelException e) {
            expected = e;
        }
        assertNotNull(expected);
        // try to read bytes, test twice to make sure that buffer is kept consistent
        expected = null;
        try {
            stream.read(new byte[10]);
        } catch (ClosedChannelException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            stream.read(new byte[10]);
        } catch (ClosedChannelException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    private class ReadByteTask implements Runnable {

        private final T stream;
        private int readResult;

        public ReadByteTask(T s) {
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

    private class ReadBytesTask implements Runnable {

        private final T stream;
        private final byte[] bytes;
        private int readResult;

        public ReadBytesTask(T s, byte[] b) {
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

    protected class SkipBytesTask implements Runnable {

        private final T stream;
        private final int skip;
        private long skipResult;

        public SkipBytesTask(T s, int l) {
            stream = s;
            skip = l;
        }

        @Override
        public void run() {
            try {
                skipResult = stream.skip(skip);
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }

        public long getSkipResult() {
            return skipResult;
        }
    }

    @Override
    protected long getOperationTimeout(T stream, TimeUnit timeUnit) {
        return getReadTimeout(stream, timeUnit);
    }

    @Override
    protected void setOperationTimeout(T stream, int timeout, TimeUnit timeUnit) {
        setReadTimeout(stream, timeout, timeUnit);
    }

    @Override
    protected T createChannelStream(long timeout, TimeUnit timeUnit) {
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        return createChannelInputStream(channelMock, timeout, timeUnit, 10);
    }
}
