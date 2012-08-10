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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.xnio.Buffers;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamChannel;

/**
 * Tests a pair of connected stream channels.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
public abstract class AbstractNioStreamChannelTest extends AbstractStreamSinkSourceChannelTest <StreamChannel, StreamChannel> {

    private StreamChannel channel1 = null;
    private StreamChannel channel2 = null;

    @Override
    protected void initChannels(XnioWorker worker, OptionMap optionMap) throws IOException {
        super.initChannels(worker, optionMap);
        // for a matter of code readability, it is better to name those channels sink/source in the super class
        // and create new fields in this subclass so we can use a more appropriate name here
        channel1 = super.sinkChannel;
        channel2 = super.sourceChannel;
    }

    @Test
    public void readWrite() throws IOException{
        initChannels();
        final ByteBuffer writeBuffer = ByteBuffer.allocate(15);
        final ByteBuffer readBuffer = ByteBuffer.allocate(15);
        writeBuffer.put("read and write".getBytes()).flip();
        assertEquals(0, channel1.read(readBuffer));
        assertEquals(14, channel1.write(writeBuffer));
        assertEquals(14, channel2.read(readBuffer));
        assertEquals(0, channel2.write(writeBuffer));
        assertEquals(0, channel2.read(readBuffer));
        readBuffer.flip();
        assertEquals("read and write", Buffers.getModifiedUtf8(readBuffer));
        channel1.close();
        assertEquals(-1, channel1.read(readBuffer));
        writeBuffer.flip();
        ClosedChannelException expected = null;
        try {
            channel1.write(writeBuffer);
        } catch (ClosedChannelException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    @Test
    public void readWriteMultipleBuffers() throws IOException{
        initChannels();
        final ByteBuffer[] writeBuffers = new ByteBuffer[] {ByteBuffer.allocate(3), ByteBuffer.allocate(9),
                ByteBuffer.allocate(13), Buffers.EMPTY_BYTE_BUFFER};
        final ByteBuffer[] readBuffers = new ByteBuffer[] {ByteBuffer.allocate(1), ByteBuffer.allocate(5),
                ByteBuffer.allocate(10), ByteBuffer.allocate(15), ByteBuffer.allocate(20)};
        writeBuffers[0].put("> 1".getBytes()).flip();
        writeBuffers[1].put("multiple".getBytes()).flip();
        writeBuffers[2].put("more than one".getBytes()).flip();
        assertEquals(0, channel1.read(readBuffers));
        assertEquals(0, channel1.write(writeBuffers, 3, 1));
        assertEquals(13, channel1.write(writeBuffers, 2, 2));
        assertEquals(0, channel1.write(writeBuffers, 2, 2));
        assertEquals(1, channel2.read(readBuffers, 0, 1));
        assertEquals('m', readBuffers[0].get(0));
        assertEquals(12, channel2.read(readBuffers));
        assertEquals(0, channel2.read(readBuffers, 1, 2));
        assertEquals(11, channel2.write(writeBuffers));
        assertEquals(11, channel1.read(readBuffers, 3, 2));
        readBuffers[1].flip();
        readBuffers[2].flip();
        readBuffers[3].flip();
        readBuffers[4].flip();
        assertEquals("ore t", Buffers.getModifiedUtf8(readBuffers[1]));
        assertEquals("han one", Buffers.getModifiedUtf8(readBuffers[2]));
        assertEquals("> 1multiple", Buffers.getModifiedUtf8(readBuffers[3]));
        assertEquals(0, readBuffers[4].remaining());
        channel1.close();
        assertEquals(-1, channel1.read(readBuffers));
        writeBuffers[0].flip();
        ClosedChannelException expected = null;
        try {
            channel1.write(writeBuffers);
        } catch (ClosedChannelException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    @Test
    public void suspendResumeReadsAndWrites() throws IOException, InterruptedException {
        initChannels();
        assertFalse(channel1.isReadResumed());
        assertFalse(channel1.isWriteResumed());

        final TestChannelListener<StreamChannel> readListener = new TestChannelListener<StreamChannel>();
        final TestChannelListener<StreamChannel> writeListener = new TestChannelListener<StreamChannel>();

        channel1.getReadSetter().set(readListener);
        assertFalse(readListener.isInvokedYet());
        channel1.getWriteSetter().set(writeListener);
        assertFalse(writeListener.isInvokedYet());

        channel1.awaitWritable();
        channel1.resumeReads();
        int count = 0;
        while(!channel1.isReadResumed()) {
            Thread.sleep(50);
            if (++ count == 10) {
                fail("Read is not resumed");
            }
        }
        assertTrue(channel1.isReadResumed());
        assertFalse(channel1.isWriteResumed());

        channel1.resumeWrites();
        count = 0;
        assertTrue(writeListener.isInvoked());
        assertTrue(channel1.isWriteResumed());
        writeListener.clear();
        assertTrue(channel1.isReadResumed());
        channel1.suspendReads();
        assertFalse(channel1.isReadResumed());
        assertTrue(channel1.isWriteResumed());

        channel1.suspendWrites();
        assertFalse(channel1.isReadResumed());
        assertFalse(channel1.isWriteResumed());

        channel1.wakeupWrites();
        assertTrue(writeListener.isInvoked());
        assertFalse(readListener.isInvokedYet());
        assertSame(channel1, writeListener.getChannel());

        channel1.wakeupReads();
        assertTrue(readListener.isInvoked());
        assertSame(channel1, readListener.getChannel());

        channel1.shutdownReads();
        channel1.shutdownWrites();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        assertFalse(channel1.isOpen());

        // idempotent operations
        channel1.shutdownReads();
        channel1.shutdownWrites();
        assertFalse(channel1.isOpen());
    }

    @Test
    public void awaitReadableAndWritable() throws IOException, InterruptedException {
        initChannels();
        final TestChannelListener<StreamChannel> readListener = new TestChannelListener<StreamChannel>();
        final TestChannelListener<StreamChannel> writeListener = new TestChannelListener<StreamChannel>();

        channel1.getReadSetter().set(readListener);
        channel1.getWriteSetter().set(writeListener);
        channel2.getReadSetter().set(readListener);
        channel2.getWriteSetter().set(writeListener);

        channel1.awaitWritable();
        channel1.awaitWritable(1, TimeUnit.HOURS);
        channel2.awaitWritable();
        channel2.awaitWritable(1, TimeUnit.HOURS);

        final ReadableAwaiter<StreamChannel> readableAwaiter1 = new ReadableAwaiter<StreamChannel>(channel1);
        final ReadableAwaiter<StreamChannel> readableAwaiter2 = new ReadableAwaiter<StreamChannel>(channel1, 10, TimeUnit.MICROSECONDS);
        final ReadableAwaiter<StreamChannel> readableAwaiter3 = new ReadableAwaiter<StreamChannel>(channel1, 10, TimeUnit.MINUTES);
        final WritableAwaiter<StreamChannel> writableAwaiter1 = new WritableAwaiter<StreamChannel>(channel1);
        final WritableAwaiter<StreamChannel> writableAwaiter2 = new WritableAwaiter<StreamChannel>(channel1, 5, TimeUnit.NANOSECONDS);
        final WritableAwaiter<StreamChannel> writableAwaiter3 = new WritableAwaiter<StreamChannel>(channel1, 2222222, TimeUnit.SECONDS);

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

        channel1.shutdownWrites();
        writableAwaiterThread1.start();
        writableAwaiterThread2.start();
        writableAwaiterThread3.start();
        writableAwaiterThread1.join();
        writableAwaiterThread2.join();
        writableAwaiterThread3.join();

        final ByteBuffer buffer = ByteBuffer.allocate(5);
        buffer.put("12345".getBytes()).flip();
        assertEquals(5, channel2.write(buffer));

        readableAwaiterThread1.join();
        readableAwaiterThread3.join();

        channel1.resumeWrites();
        writableAwaiterThread1.join();
        writableAwaiterThread3.join();

        assertNotNull(channel1.getWriteThread());
        assertNotNull(channel2.getWriteThread());
    }

    @Test
    public void readThreadOnlyChannel() throws IOException, InterruptedException {
        final XnioWorker readThreadOnlyWorker = xnio.createWorker(OptionMap.create(Options.WORKER_WRITE_THREADS, 0));
        initChannels(readThreadOnlyWorker, OptionMap.EMPTY);
        final TestChannelListener<StreamChannel> readListener = new TestChannelListener<StreamChannel>();
        final TestChannelListener<StreamChannel> writeListener = new TestChannelListener<StreamChannel>();
        channel1.getReadSetter().set(readListener);
        channel1.getWriteSetter().set(writeListener);

        channel1.awaitWritable();
        channel1.awaitWritable(1, TimeUnit.HOURS);

        IllegalArgumentException expected = null;
        try {
            channel1.resumeWrites();
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            channel1.wakeupWrites();
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        assertFalse(channel1.isWriteResumed());
        channel1.suspendWrites(); // nothing should happen
        assertFalse(channel1.isWriteResumed());

        channel1.resumeReads();
        channel1.wakeupReads();
        channel1.suspendReads();

        assertNull(channel1.getWriteThread());
        assertNotNull(channel1.getReadThread());


        channel1.shutdownWrites();
        channel1.shutdownReads();
    }

    @Test
    public void writeThreadOnlyChannel() throws IOException, InterruptedException {
        final XnioWorker writeThreadOnlyWorker = xnio.createWorker(OptionMap.create(Options.WORKER_READ_THREADS, 0));
        initChannels(writeThreadOnlyWorker, OptionMap.create(Options.WORKER_ESTABLISH_WRITING, true));
        final TestChannelListener<StreamChannel> readListener = new TestChannelListener<StreamChannel>();
        final TestChannelListener<StreamChannel> writeListener = new TestChannelListener<StreamChannel>();
        channel1.getReadSetter().set(readListener);
        channel1.getWriteSetter().set(writeListener);

        final ReadableAwaiter<StreamChannel> readableAwaiter = new ReadableAwaiter<StreamChannel>(channel1);
        final Thread readableAwaiterThread = new Thread(readableAwaiter);
        readableAwaiterThread.start();
        readableAwaiterThread.join(50);
        assertTrue(readableAwaiterThread.isAlive());

        final ByteBuffer buffer = ByteBuffer.allocate(5);
        buffer.put("12345".getBytes()).flip();
        assertEquals(5, channel2.write(buffer));
        readableAwaiterThread.join();

        IllegalArgumentException expected = null;
        try {
            channel1.resumeReads();
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            channel1.wakeupReads();
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        assertFalse(channel1.isReadResumed());
        channel1.suspendReads(); // nothing should happen
        assertFalse(channel1.isReadResumed());

        channel1.resumeWrites();
        channel1.wakeupWrites();
        channel1.suspendWrites();

        assertNull(channel1.getReadThread());
        assertNotNull(channel1.getWriteThread());

        channel1.shutdownWrites();
        channel1.shutdownReads();
    }
}
