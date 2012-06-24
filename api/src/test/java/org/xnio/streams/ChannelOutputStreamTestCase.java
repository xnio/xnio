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
import static org.junit.Assert.assertTrue;
import static org.xnio.AssertReadWrite.assertWrittenMessage;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.xnio.channels.WriteTimeoutException;
import org.xnio.mock.ConnectedStreamChannelMock;

/**
 * Test for {@link ChannelOutputStream}.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
public class ChannelOutputStreamTestCase extends AbstractChannelStreamTest<ChannelOutputStream> {

    @Test
    public void illegalConstructorArguments() {
        // try with null sinkChannel
        IllegalArgumentException expected = null;
        try {
            new ChannelOutputStream(null);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            new ChannelOutputStream(null, 10, TimeUnit.SECONDS);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        // try with null timeout unit
        final ConnectedStreamChannelMock sinkChannel = new ConnectedStreamChannelMock();
        expected = null;
        try {
            new ChannelOutputStream(sinkChannel, 5, null);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        // try with negative timeout
        expected = null;
        try {
            new ChannelOutputStream(sinkChannel, -1, TimeUnit.MICROSECONDS);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            new ChannelOutputStream(sinkChannel, -60, TimeUnit.SECONDS);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    @Test
    public void writeBytesAndByteArrays() throws IOException {
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        final ChannelOutputStream stream = new ChannelOutputStream(channelMock);
        channelMock.setReadData("data");
        channelMock.enableWrite(true);
        assertWrittenMessage(channelMock);
        stream.write('d');
        assertWrittenMessage(channelMock, "d");
        stream.write('a');
        assertWrittenMessage(channelMock, "da");
        stream.write('t');
        assertWrittenMessage(channelMock, "dat");
        stream.write('a');
        assertWrittenMessage(channelMock, "data");
        stream.write(" more data".getBytes("UTF-8"), 1, 9);
        assertWrittenMessage(channelMock, "data", "more data");
    }

    @Test
    public void writeByteArraysAndBytes() throws IOException {
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        final ChannelOutputStream stream = new ChannelOutputStream(channelMock);
        channelMock.enableWrite(true);
        stream.write("data".getBytes("UTF-8"));
        assertWrittenMessage(channelMock, "data");
        stream.write('m');
        assertWrittenMessage(channelMock, "data", "m");
        stream.write('o');
        assertWrittenMessage(channelMock, "data", "mo");
        stream.write('r');
        assertWrittenMessage(channelMock, "data", "mor");
        stream.write('e');
        assertWrittenMessage(channelMock, "data", "more");
        stream.write(' ');
        assertWrittenMessage(channelMock, "data", "more ");
        stream.write('d');
        assertWrittenMessage(channelMock, "data", "more d");
        stream.write('a');
        assertWrittenMessage(channelMock, "data", "more da");
        stream.write('t');
        assertWrittenMessage(channelMock, "data", "more dat");
        stream.write('a');
        assertWrittenMessage(channelMock, "data", "more data");
    }

    @Test
    public void writeBlocks() throws Exception {
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        channelMock.enableWrite(false);
        final ChannelOutputStream stream = new ChannelOutputStream(channelMock);
        // create and start write thread
        final WriteByteTask writeByteTask = new WriteByteTask(stream, (byte) 'w');
        final Thread writeByteThread = new Thread(writeByteTask);
        writeByteThread.start();
        // thread cant complete
        writeByteThread.join(200);
        assertTrue(writeByteThread.isAlive());
        // enable write, now thread can complete with a result
        channelMock.enableWrite(true);
        writeByteThread.join();
        assertWrittenMessage(channelMock, "w");
    }

    @Test
    public void writeByteArrayBlocks1() throws Exception {
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        channelMock.enableWrite(false);
        final ChannelOutputStream stream = new ChannelOutputStream(channelMock);
        // create and start write thread
        final WriteBytesTask writeBytesTask = new WriteBytesTask(stream, "stream write blocks until channel write is enabled".getBytes("UTF-8"));
        final Thread writeBytesThread = new Thread(writeBytesTask);
        writeBytesThread.start();
        // thread cant complete
        writeBytesThread.join(200);
        assertTrue(writeBytesThread.isAlive());
        // enable write, now thread can complete with a result
        channelMock.enableWrite(true);
        writeBytesThread.join();
        assertWrittenMessage(channelMock, "stream write blocks until channel write is enabled");
    }

    @Test
    public void writeByteArrayBlocks2() throws Exception {
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        channelMock.enableWrite(false);
        final ChannelOutputStream stream = new ChannelOutputStream(channelMock);
        // create and start write thread
        final WriteBytesTask writeBytesTask = new WriteBytesTask(stream, "write blocks".getBytes("UTF-8"));
        final Thread writeBytesThread = new Thread(writeBytesTask);
        writeBytesThread.start();
        // thread cant complete
        writeBytesThread.join(200);
        assertTrue(writeBytesThread.isAlive());
        // enable write, now thread can complete with a result
        channelMock.enableWrite(true);
        writeBytesThread.join();
        assertWrittenMessage(channelMock, "write blocks");
    }

    @Test
    public void flushBlocks() throws Exception {
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        channelMock.enableWrite(true);
        channelMock.enableFlush(false);
        final ChannelOutputStream stream = new ChannelOutputStream(channelMock);
        stream.write("flush".getBytes("UTF-8"));
        assertWrittenMessage(channelMock, "flush");
        assertFalse(channelMock.isFlushed());
        // try to flush
        final FlushTask flushTask = new FlushTask(stream);
        final Thread flushThread = new Thread(flushTask);
        flushThread.start();
        flushThread.join(200);
        assertTrue(flushThread.isAlive());
        assertFalse(channelMock.isFlushed());
        channelMock.enableFlush(true);
        flushThread.join();
        assertTrue(channelMock.isFlushed());
        assertWrittenMessage(channelMock, "flush");
    }

    @Test
    public void writeBytesAndByteArraysWithTimeout() throws IOException {
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        channelMock.enableWrite(false);
        final ChannelOutputStream stream = new ChannelOutputStream(channelMock, 0, TimeUnit.MICROSECONDS);
        assertEquals(0, stream.getWriteTimeout(TimeUnit.MICROSECONDS));
        assertEquals(0, stream.getWriteTimeout(TimeUnit.MILLISECONDS));
        stream.setWriteTimeout(100, TimeUnit.MILLISECONDS);
        assertEquals(100, stream.getWriteTimeout(TimeUnit.MILLISECONDS));
        // start write test
        WriteTimeoutException expectedException = null;
        try {
            stream.write('a');
        } catch (WriteTimeoutException e) {
            expectedException = e;
        }
        assertNotNull(expectedException);

        channelMock.setReadData("data");
        channelMock.enableWrite(true);
        assertWrittenMessage(channelMock);
        stream.write('d');
        stream.write('a');
        stream.write('t');
        stream.write('a');
        assertWrittenMessage(channelMock, "data");

        channelMock.enableWrite(false);
        expectedException = null;
        try {
            stream.write('a');
        } catch (WriteTimeoutException e) {
            expectedException = e;
        }
        assertNotNull(expectedException);
        expectedException = null;
        try {
            stream.write("abc".getBytes("UTF-8"));
        } catch (WriteTimeoutException e) {
            expectedException = e;
        }
        assertNotNull(expectedException);

        channelMock.enableWrite(true);
        stream.write("#more data".getBytes("UTF-8"), 1, 9);
        assertWrittenMessage(channelMock, "data", "more data");
    }

    @Test
    public void writeByteArraysAndBytesWithTimeout() throws IOException {
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        channelMock.enableWrite(false);
        // try using 10 microseconds, timeout value is not rounded up to 1 millisecond
        final ChannelOutputStream stream = new ChannelOutputStream(channelMock, 10, TimeUnit.MICROSECONDS);
        assertEquals(10, stream.getWriteTimeout(TimeUnit.MICROSECONDS));
        assertEquals(0, stream.getWriteTimeout(TimeUnit.MILLISECONDS));
        stream.setWriteTimeout(200, TimeUnit.MILLISECONDS);
        assertEquals(200, stream.getWriteTimeout(TimeUnit.MILLISECONDS));
        // start write test
        WriteTimeoutException expectedException = null;
        try {
            stream.write("abc".getBytes("UTF-8"));
        } catch (WriteTimeoutException e) {
            expectedException = e;
        }
        assertNotNull(expectedException);

        channelMock.setReadData("data");
        channelMock.enableWrite(true);

        final byte[] bytes = "data".getBytes("UTF-8");
        stream.write(bytes, 0, -3);
        assertWrittenMessage(channelMock);
        stream.write(bytes);
        assertWrittenMessage(channelMock, "data");

        channelMock.enableWrite(false);
        expectedException = null;
        try {
            stream.write('a');
        } catch (WriteTimeoutException e) {
            expectedException = e;
        }
        assertNotNull(expectedException);
        expectedException = null;
        try {
            stream.write("abc".getBytes("UTF-8"));
        } catch (WriteTimeoutException e) {
            expectedException = e;
        }
        assertNotNull(expectedException);

        channelMock.enableWrite(true);
        stream.write('m');
        assertWrittenMessage(channelMock, "data", "m");
        stream.write('o');
        assertWrittenMessage(channelMock, "data", "mo");
        stream.write('r');
        assertWrittenMessage(channelMock, "data", "mor");
        stream.write('e');
        assertWrittenMessage(channelMock, "data", "more");
    }

    @Test
    public void writeThrowsException() throws IOException {
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        channelMock.close(); // channel mock will always throw ClosedChannelException
        final ChannelOutputStream stream = new ChannelOutputStream(channelMock);
        // try to write, test twice to make sure that buffer is kept consistent
        ClosedChannelException expected = null;
        try {
            stream.write('a');
        } catch (ClosedChannelException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            stream.write('a');
        } catch (ClosedChannelException e) {
            expected = e;
        }
        assertNotNull(expected);
        // try to write bytes, test twice to make sure that buffer is kept consistent
        expected = null;
        try {
            stream.write("abc".getBytes("UTF-8"));
        } catch (ClosedChannelException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            stream.write("abc".getBytes("UTF-8"));
        } catch (ClosedChannelException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    @Test
    public void close() throws IOException {
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        final ChannelOutputStream stream = new ChannelOutputStream(channelMock);
        channelMock.enableWrite(true);

        // close!
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

        // close is idempotent
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
    }

    @Test
    public void closeStreamWithTimeout() throws IOException {
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        final ChannelOutputStream stream = new ChannelOutputStream(channelMock, 15, TimeUnit.MILLISECONDS);
        channelMock.enableWrite(true);

        // close!
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

        // close is idempotent
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
    }

    @Override
    protected long getOperationTimeout(ChannelOutputStream stream, TimeUnit timeUnit) {
        return stream.getWriteTimeout(timeUnit);
    }

    @Override
    protected void setOperationTimeout(ChannelOutputStream stream, int timeout, TimeUnit timeUnit) {
        stream.setWriteTimeout(timeout, timeUnit);
    }

    @Override
    protected ChannelOutputStream createChannelStream(long timeout, TimeUnit timeUnit) {
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        return new ChannelOutputStream(channelMock, timeout, timeUnit);
    }

    private class WriteByteTask implements Runnable {

        private final ChannelOutputStream stream;
        private final byte writeByte;

        public WriteByteTask(ChannelOutputStream s, byte b) {
            stream = s;
            writeByte = b;
        }

        @Override
        public void run() {
            try {
                stream.write(writeByte);
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }

    private class WriteBytesTask implements Runnable {

        private final ChannelOutputStream stream;
        private final byte[] bytes;

        public WriteBytesTask(ChannelOutputStream s, byte[] b) {
            stream = s;
            bytes = b;
        }

        @Override
        public void run() {
            try {
                stream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }

    private class FlushTask implements Runnable {

        private final ChannelOutputStream stream;

        public FlushTask(ChannelOutputStream s) {
            stream = s;
        }

        @Override
        public void run() {
            try {
                stream.flush();
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }
}
