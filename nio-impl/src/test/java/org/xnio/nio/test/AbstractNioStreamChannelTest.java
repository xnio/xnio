/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2013 Red Hat, Inc. and/or its affiliates.
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
package org.xnio.nio.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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
import org.xnio.XnioWorker;
import org.xnio.channels.StreamChannel;

/**
 * Tests a pair of connected stream channels.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
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

    @SuppressWarnings("deprecation")
    @Test
    public void awaitReadableAndWritable() throws IOException, InterruptedException {
        initChannels();
        final TestChannelListener<StreamChannel> readListener1 = new TestChannelListener<StreamChannel>();
        final TestChannelListener<StreamChannel> writeListener1 = new TestChannelListener<StreamChannel>();
        final TestChannelListener<StreamChannel> readListener2 = new TestChannelListener<StreamChannel>();
        final TestChannelListener<StreamChannel> writeListener2 = new TestChannelListener<StreamChannel>();

        channel1.getReadSetter().set(readListener1);
        channel1.getWriteSetter().set(writeListener1);
        channel2.getReadSetter().set(readListener2);
        channel2.getWriteSetter().set(writeListener2);

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
        assertNotNull(channel1.getIoThread());
        assertNotNull(channel2.getWriteThread());
        assertNotNull(channel2.getIoThread());
    }

    @Test
    public void awaitWritable() throws IOException, InterruptedException {
        initChannels();
        final TestChannelListener<StreamChannel> readListener1 = new TestChannelListener<StreamChannel>();
        final TestChannelListener<StreamChannel> writeListener1 = new TestChannelListener<StreamChannel>();
        final TestChannelListener<StreamChannel> readListener2 = new TestChannelListener<StreamChannel>();
        final TestChannelListener<StreamChannel> writeListener2 = new TestChannelListener<StreamChannel>();

        channel1.getReadSetter().set(readListener1);
        channel1.getWriteSetter().set(writeListener1);
        channel2.getReadSetter().set(readListener2);
        channel2.getWriteSetter().set(writeListener2);

        channel1.shutdownWrites();
        do {
            channel1.awaitWritable();
        } while (! channel1.flush());
    }
}
