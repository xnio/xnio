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
package org.xnio.nio.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xnio.Buffers;
import org.xnio.ChannelPipe;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 * Tests a pair of connected source/sink channels.
 *
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 *
 * @param <S> the sink channel type
 * @param <T> the source channel type
 */
public abstract class AbstractStreamSinkSourceChannelTest<S extends StreamSinkChannel, T extends StreamSourceChannel> {

    protected static XnioWorker worker;
    protected static Xnio xnio;

    /** Sink channel targeted by this test. It must be connected to {@code sourceChanel}. */
    protected S sinkChannel = null;
    /** Source channel targeted by this test. It must be connected to {@code sinkChanel}. */
    protected T sourceChannel = null;

    /**
     * Creates the pair of sink/source channels that should be tested.
     *  
     * @param xnioWorker            the worker
     * @param optionMap             contains channels options 
     * @param sinkChannelListener   handles sink channel creation
     * @param sourceChannelListener handles source channel creation 
     */
    protected abstract void initChannels(XnioWorker xnioWorker, OptionMap optionMap, TestChannelListener<S> sinkChannelListener, TestChannelListener<T> sourceChannelListener) throws IOException; 

    @BeforeClass
    public static void createWorker() throws IOException {
        xnio = Xnio.getInstance("nio", AbstractStreamSinkSourceChannelTest.class.getClassLoader());
        worker = xnio.createWorker(OptionMap.EMPTY);
    }

    @AfterClass
    public static void shutdownWorker() throws IOException {
        worker.shutdownNow();
    }

    /**
     * Creates the channels and sets their values to the fields {@link sinkChannel} and {@link sourceChannel}.
     * <p>This method must be invoked by the subclasses in order to enable automatic channel closing after the test.
     * 
     * @param worker       the worker
     * @param optionMap    contains channels options
     */
    protected void initChannels(XnioWorker worker, OptionMap optionMap) throws IOException {
        if (sinkChannel != null) {
            closeChannels();
        }
        final TestChannelListener<S> sinkChannelListener = new TestChannelListener<S>();
        final TestChannelListener<T> sourceChannelListener = new TestChannelListener<T>();
        initChannels(worker, optionMap, sinkChannelListener, sourceChannelListener);
        assertTrue("Subclass must invoke the channel listeners to setup sink and source channels", sinkChannelListener.isInvokedYet());
        assertTrue("Subclass must invoke the channel listeners to setup sink and source channels", sourceChannelListener.isInvokedYet());
        sinkChannel = sinkChannelListener.getChannel();
        assertNotNull(sinkChannel);
        assertTrue(sinkChannel.isOpen());
        sourceChannel = sourceChannelListener.getChannel();
        assertNotNull(sourceChannel);
        assertTrue(sourceChannel.isOpen());
    }

    /**
     * Creates the channels and sets their values to the fields {@link sinkChannel} and {@link sourceChannel}.
     * <p>This method must be invoked by the subclasses in order to enable automatic channel closing after the test.
     */
    protected void initChannels() throws IOException {
        initChannels(worker, OptionMap.EMPTY);
    }

    @After
    public void closeChannels() throws IOException {
        if (sinkChannel != null) {
            sinkChannel.close();
            assertFalse(sinkChannel.isOpen());
            sourceChannel.close();
            assertFalse(sourceChannel.isOpen());
        }
    }

    @Test
    public void writeToSinkAndReadFromSource() throws IOException{
        initChannels();
        final ByteBuffer writeBuffer = ByteBuffer.allocate(35);
        final ByteBuffer readBuffer = ByteBuffer.allocate(35);
        writeBuffer.put("write to sink and read from source".getBytes()).flip();
        assertEquals(0, sourceChannel.read(readBuffer));
        assertEquals(34, sinkChannel.write(writeBuffer));
        assertEquals(34, sourceChannel.read(readBuffer));
        assertEquals(0, sinkChannel.write(writeBuffer));
        assertEquals(0, sourceChannel.read(readBuffer));
        readBuffer.flip();
        assertEquals("write to sink and read from source", Buffers.getModifiedUtf8(readBuffer));
        sinkChannel.close();
        assertEquals(0, sourceChannel.read(readBuffer));
        writeBuffer.flip();
        ClosedChannelException expected = null;
        try {
            sinkChannel.write(writeBuffer);
        } catch (ClosedChannelException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    @Test
    public void writeToSinkAndReadFromSourceWithMultipleBuffers() throws IOException{
        initChannels();
        final ByteBuffer[] writeBuffers = new ByteBuffer[] {ByteBuffer.allocate(4), ByteBuffer.allocate(9),
                ByteBuffer.allocate(13), Buffers.EMPTY_BYTE_BUFFER};
        final ByteBuffer[] readBuffers = new ByteBuffer[] {ByteBuffer.allocate(1), ByteBuffer.allocate(5),
                ByteBuffer.allocate(10), ByteBuffer.allocate(15), ByteBuffer.allocate(20)};
        writeBuffers[0].put(">= 2".getBytes()).flip();
        writeBuffers[1].put("several".getBytes()).flip();
        writeBuffers[2].put("+ than 1".getBytes()).flip();
        assertEquals(0, sourceChannel.read(readBuffers));
        assertEquals(0, sinkChannel.write(writeBuffers, 3, 1));
        assertEquals(8, sinkChannel.write(writeBuffers, 2, 2));
        assertEquals(0, sinkChannel.write(writeBuffers, 2, 2));
        assertEquals(1, sourceChannel.read(readBuffers, 0, 1));
        assertEquals('+', readBuffers[0].get(0));
        assertEquals(7, sourceChannel.read(readBuffers));
        assertEquals(0, sourceChannel.read(readBuffers, 1, 2));
        assertEquals(11, sinkChannel.write(writeBuffers));
        assertEquals(11, sourceChannel.read(readBuffers, 3, 2));
        readBuffers[1].flip();
        readBuffers[2].flip();
        readBuffers[3].flip();
        readBuffers[4].flip();
        assertEquals(" than", Buffers.getModifiedUtf8(readBuffers[1]));
        assertEquals(" 1", Buffers.getModifiedUtf8(readBuffers[2]));
        assertEquals(">= 2several", Buffers.getModifiedUtf8(readBuffers[3]));
        assertEquals(0, readBuffers[4].remaining());
        sinkChannel.close();
        assertEquals(0, sourceChannel.read(readBuffers));
        writeBuffers[0].flip();
        ClosedChannelException expected = null;
        try {
            sinkChannel.write(writeBuffers);
        } catch (ClosedChannelException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    @Test
    public void transferTo() throws IOException {
        initChannels();
        final ByteBuffer buffer = ByteBuffer.allocate(20);
        final ByteBuffer transferedMessage1 = ByteBuffer.allocate(6);
        final ByteBuffer transferedMessage2 = ByteBuffer.allocate(16);
        buffer.put("transfered message".getBytes()).flip();
        final File file = File.createTempFile("test", ".txt");
        file.deleteOnExit();
        final FileChannel fileChannel = new RandomAccessFile(file, "rw").getChannel();
        try {
            assertEquals (0, sourceChannel.transferTo(0, 6, fileChannel));

            sinkChannel.write(buffer);
            assertEquals (8, sourceChannel.transferTo(0, 8, fileChannel));
            assertEquals (10, sourceChannel.transferTo(8, 11, fileChannel));

            fileChannel.position(0);
            assertEquals(6, fileChannel.read(transferedMessage1));
            assertEquals(12, fileChannel.read(transferedMessage2));
            transferedMessage1.flip();
            transferedMessage2.flip();
            assertEquals("transf", Buffers.getModifiedUtf8(transferedMessage1));
            assertEquals("ered message", Buffers.getModifiedUtf8(transferedMessage2));

            assertEquals (0, sourceChannel.transferTo(0, 6, fileChannel));

            sourceChannel.close();

            ClosedChannelException expected = null;
            try {
                sourceChannel.transferTo(0, 6, fileChannel);
            } catch (ClosedChannelException e) {
                expected = e;
            }
            assertNotNull(expected);
        } finally {
            fileChannel.close();
        }
    }

    @Test
    public void transferFrom() throws IOException {
        initChannels();
        final ByteBuffer buffer = ByteBuffer.allocate(20);
        final ByteBuffer transferedMessage1 = ByteBuffer.allocate(6);
        final ByteBuffer transferedMessage2 = ByteBuffer.allocate(16);
        buffer.put("transferred message".getBytes()).flip();
        final File file = File.createTempFile("test", ".txt");
        file.deleteOnExit();
        final FileChannel fileChannel = new RandomAccessFile(file, "rw").getChannel();
        try {
            assertEquals (0, sinkChannel.transferFrom(fileChannel, 0, 6));

            fileChannel.write(buffer);
            assertEquals (8, sinkChannel.transferFrom(fileChannel, 0, 8));
            assertEquals (11, sinkChannel.transferFrom(fileChannel, 8, 11));

            assertEquals(6, sourceChannel.read(transferedMessage1));
            assertEquals(13, sourceChannel.read(transferedMessage2));
            transferedMessage1.flip();
            transferedMessage2.flip();
            assertEquals("transf", Buffers.getModifiedUtf8(transferedMessage1));
            assertEquals("erred message", Buffers.getModifiedUtf8(transferedMessage2));

            assertEquals (0, sinkChannel.transferFrom(fileChannel, 19, 6));

            sinkChannel.close();

            ClosedChannelException expected = null;
            try {
                sinkChannel.transferFrom(fileChannel, 0, 6);
            } catch (ClosedChannelException e) {
                expected = e;
            }
            assertNotNull(expected);
        } finally {
            fileChannel.close();
        }
    }

    @Test
    public void transferThroughBuffer() throws IOException {
        initChannels();
        final ChannelPipe<StreamSourceChannel, StreamSinkChannel> channelPipe = worker.createHalfDuplexPipe();
        final StreamSourceChannel leftChannel = channelPipe.getLeftSide();
        final StreamSinkChannel rightChannel = channelPipe.getRightSide();

        try {
            final ByteBuffer readBuffer = ByteBuffer.allocate(50);
            final ByteBuffer writeBuffer = ByteBuffer.allocate(50);
            final ByteBuffer throughBuffer = ByteBuffer.allocate(10);
    
            // Step 1: write to sink channel and transfer from source channel to left channel; read from right channel
            writeBuffer.put("looooooooooooooooooooooooooong daaaaaaaaaaaata".getBytes()).flip();
            assertEquals(46, sinkChannel.write(writeBuffer));
            assertEquals(46, sourceChannel.transferTo(50, throughBuffer, rightChannel));
    
            readBuffer.clear();
            assertEquals(46, leftChannel.read(readBuffer));
            readBuffer.flip();
            assertEquals("looooooooooooooooooooooooooong daaaaaaaaaaaata", Buffers.getModifiedUtf8(readBuffer));
    
            // Step 2: write to sink channel, transfer from source channel to left channel, then transfer from left channel to sink again
            // finally, read from source channel
            throughBuffer.clear();
            writeBuffer.flip();
            assertEquals(46, sinkChannel.write(writeBuffer));
    
            assertEquals(46, sourceChannel.transferTo(50, throughBuffer, rightChannel));
    
            assertEquals(46, sinkChannel.transferFrom(leftChannel, 50, throughBuffer));

            readBuffer.clear();
            assertEquals(46, sourceChannel.read(readBuffer));
            readBuffer.flip();
            assertEquals("looooooooooooooooooooooooooong daaaaaaaaaaaata", Buffers.getModifiedUtf8(readBuffer));
        } finally {
            IoUtils.safeClose(leftChannel);
            IoUtils.safeClose(rightChannel);
        }
    }

    @Test
    public void suspendResumeReadsAndWrites() throws IOException, InterruptedException {
        initChannels();
        assertFalse(sourceChannel.isReadResumed());
        assertFalse(sinkChannel.isWriteResumed());

        final TestChannelListener<StreamSourceChannel> readListener = new TestChannelListener<StreamSourceChannel>();
        final TestChannelListener<StreamSinkChannel> writeListener = new TestChannelListener<StreamSinkChannel>();

        sourceChannel.getReadSetter().set(readListener);
        assertFalse(readListener.isInvokedYet());
        sinkChannel.getWriteSetter().set(writeListener);
        assertFalse(writeListener.isInvokedYet());

        sinkChannel.awaitWritable();
        sourceChannel.resumeReads();
        int count = 0;
        while(!sourceChannel.isReadResumed()) {
            Thread.sleep(50);
            if (++ count == 10) {
                fail("Read is not resumed");
            }
        }
        assertTrue(sourceChannel.isReadResumed());
        assertFalse(sinkChannel.isWriteResumed());

        sinkChannel.resumeWrites();
        count = 0;
        assertTrue(writeListener.isInvoked());
        assertTrue(sinkChannel.isWriteResumed());
        writeListener.clear();
        assertTrue(sourceChannel.isReadResumed());
        sourceChannel.suspendReads();
        assertFalse(sourceChannel.isReadResumed());
        assertTrue(sinkChannel.isWriteResumed());

        sinkChannel.suspendWrites();
        assertFalse(sourceChannel.isReadResumed());
        assertFalse(sinkChannel.isWriteResumed());

        sinkChannel.wakeupWrites();
        assertTrue(writeListener.isInvoked());
        assertFalse(readListener.isInvokedYet());
        assertSame(sinkChannel, writeListener.getChannel());

        sourceChannel.wakeupReads();
        assertTrue(readListener.isInvoked());
        assertSame(sourceChannel, readListener.getChannel());

        sourceChannel.shutdownReads();
        sinkChannel.shutdownWrites();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        assertFalse(sourceChannel.isOpen());
        assertFalse(sinkChannel.isOpen());
    }

    @Test
    public void awaitReadableAndWritable() throws IOException, InterruptedException {
        initChannels();
        final TestChannelListener<StreamSourceChannel> readListener = new TestChannelListener<StreamSourceChannel>();
        final TestChannelListener<StreamSinkChannel> writeListener = new TestChannelListener<StreamSinkChannel>();

        sourceChannel.getReadSetter().set(readListener);
        sinkChannel.getWriteSetter().set(writeListener);

        sinkChannel.awaitWritable();
        sinkChannel.awaitWritable(1, TimeUnit.HOURS);

        final ReadableAwaiter<T> readableAwaiter1 = new ReadableAwaiter<T>(sourceChannel);
        final ReadableAwaiter<T> readableAwaiter2 = new ReadableAwaiter<T>(sourceChannel, 10, TimeUnit.MICROSECONDS);
        final ReadableAwaiter<T> readableAwaiter3 = new ReadableAwaiter<T>(sourceChannel, 10, TimeUnit.MINUTES);
        final WritableAwaiter<S> writableAwaiter1 = new WritableAwaiter<S>(sinkChannel);
        final WritableAwaiter<S> writableAwaiter2 = new WritableAwaiter<S>(sinkChannel, 5, TimeUnit.NANOSECONDS);
        final WritableAwaiter<S> writableAwaiter3 = new WritableAwaiter<S>(sinkChannel, 2222222, TimeUnit.SECONDS);

        final Thread readableAwaiterThread1 = new Thread(readableAwaiter1);
        final Thread readableAwaiterThread2 = new Thread(readableAwaiter2);
        final Thread readableAwaiterThread3 = new Thread(readableAwaiter3);

        final Thread writableAwaiterThread1 = new Thread(writableAwaiter1);
        final Thread writableAwaiterThread2 = new Thread(writableAwaiter2);
        final Thread writableAwaiterThread3 = new Thread(writableAwaiter3);

        readableAwaiterThread1.start();
        readableAwaiterThread2.start();
        readableAwaiterThread3.start();
        readableAwaiterThread1.join(50);
        readableAwaiterThread2.join();
        readableAwaiterThread3.join(50);
        assertTrue(readableAwaiterThread1.isAlive());
        assertTrue(readableAwaiterThread3.isAlive());

        sinkChannel.shutdownWrites();
        writableAwaiterThread1.start();
        writableAwaiterThread2.start();
        writableAwaiterThread3.start();
        writableAwaiterThread1.join();
        writableAwaiterThread2.join();
        writableAwaiterThread3.join();

        final ByteBuffer buffer = ByteBuffer.allocate(5);
        buffer.put("12345".getBytes()).flip();
        ClosedChannelException expected = null;
        try {
            sinkChannel.write(buffer);
        } catch (ClosedChannelException e) {
            expected = e;
        }
        assertNotNull(expected);

        readableAwaiterThread1.join();
        readableAwaiterThread3.join();

        sinkChannel.resumeWrites();
        writableAwaiterThread1.join();
        writableAwaiterThread3.join();

        assertNotNull(sinkChannel.getWriteThread());
        assertNotNull(sinkChannel.getWriteThread());
    }

    protected static class ReadableAwaiter<T extends StreamSourceChannel> implements Runnable {
        private final T channel;
        private final long timeout;
        private final TimeUnit timeoutUnit;

        public ReadableAwaiter(T c) {
            this(c, -1, null);
        }

        public ReadableAwaiter(T c, long t, TimeUnit tu) {
            channel = c;
            timeout = t;
            timeoutUnit = tu;
        }

        public void run() {
            try {
                if (timeout == -1) {
                    channel.awaitReadable();
                } else {
                    channel.awaitReadable(timeout, timeoutUnit);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    protected static class WritableAwaiter<S extends StreamSinkChannel> implements Runnable {
        private final S channel;
        private final long timeout;
        private final TimeUnit timeoutUnit;

        public WritableAwaiter(S c) {
            this(c, -1, null);
        }

        public WritableAwaiter(S c, long t, TimeUnit tu) {
            channel = c;
            timeout = t;
            timeoutUnit = tu;
        }

        public void run() {
            try {
                if (timeout == -1) {
                    channel.awaitWritable();
                } else {
                    channel.awaitWritable(timeout, timeoutUnit);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
