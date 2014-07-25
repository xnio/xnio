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

package org.xnio.channels;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.xnio.ChannelListener;
import org.xnio.FileAccess;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.mock.ConnectedStreamChannelMock;
import org.xnio.mock.XnioIoThreadMock;

/**
 * Test for {@link EmptySourceStreamChannel}.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 */
public class EmptyStreamSourceChannelTestCase {

    private EmptyStreamSourceChannel channel;
    private XnioIoThreadMock threadMock;

    @Before
    public void createChannel() throws Exception {
        threadMock = new XnioIoThreadMock(null);
        threadMock.start();
        this.channel = new EmptyStreamSourceChannel(threadMock);
    }

    @After
    public void closeIoThread() {
        threadMock.closeIoThread();
    }

    @SuppressWarnings("deprecation")
    @Test
    public void getWorkerAndExecutor() throws Exception {
        final Xnio xnio = Xnio.getInstance("xnio-mock");
        final XnioWorker worker = xnio.createWorker(OptionMap.EMPTY);
        final XnioIoThread executor = new XnioIoThreadMock(worker);
        this.channel = new EmptyStreamSourceChannel(executor);
        assertSame(worker, channel.getWorker());
        assertSame(executor, channel.getReadThread());
        assertSame(executor, channel.getIoThread());
    }

    @Test
    public void setAndGetOption() throws IOException {
        assertNull(channel.getOption(Options.ALLOW_BLOCKING));
        assertNull(channel.setOption(Options.ALLOW_BLOCKING, true));
        assertNull(channel.getOption(Options.ALLOW_BLOCKING));

        assertNull(channel.getOption(Options.FILE_ACCESS));
        assertNull(channel.setOption(Options.FILE_ACCESS, FileAccess.READ_ONLY));
        assertNull(channel.getOption(Options.FILE_ACCESS));

        assertNull(channel.getOption(Options.SECURE));
        assertNull(channel.setOption(Options.SECURE, false));
        assertNull(channel.getOption(Options.SECURE));

        assertNull(channel.getOption(Options.MAX_INBOUND_MESSAGE_SIZE));
        assertNull(channel.setOption(Options.MAX_INBOUND_MESSAGE_SIZE, 5000));
        assertNull(channel.getOption(Options.MAX_INBOUND_MESSAGE_SIZE));

        assertNull(channel.getOption(Options.SEND_BUFFER));
        assertNull(channel.setOption(Options.SEND_BUFFER, 100000));
        assertNull(channel.getOption(Options.SEND_BUFFER));

        assertNull(channel.getOption(Options.WORKER_NAME));
        assertNull(channel.setOption(Options.WORKER_NAME, "dummy"));
        assertNull(channel.getOption(Options.WORKER_NAME));

        assertNull(channel.getOption(Options.WRITE_TIMEOUT));
        assertNull(channel.setOption(Options.WRITE_TIMEOUT, 700000));
        assertNull(channel.getOption(Options.WRITE_TIMEOUT));
    }

    @Test
    public void supportsOption() throws IOException {
        assertFalse(channel.supportsOption(Options.ALLOW_BLOCKING));
        assertFalse(channel.supportsOption(Options.FILE_ACCESS));
        assertFalse(channel.supportsOption(Options.SECURE));
        assertFalse(channel.supportsOption(Options.MAX_INBOUND_MESSAGE_SIZE));
        assertFalse(channel.supportsOption(Options.SEND_BUFFER));
        assertFalse(channel.supportsOption(Options.WORKER_NAME));
        assertFalse(channel.supportsOption(Options.WRITE_TIMEOUT));
    }

    @Test
    public void transferToFileChannel() throws Exception {
        final File file = File.createTempFile("test", ".txt");
        file.deleteOnExit();
        final RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
        final FileChannel fileChannel = randomAccessFile.getChannel();
        try {
            assertEquals(0, channel.transferTo(5, 0, fileChannel));
            assertEquals(0, channel.transferTo(0, 0, fileChannel));
            assertEquals(0, channel.transferTo(500, 5, fileChannel));
            assertEquals(0, channel.transferTo(300, 3, null));
        } finally {
            fileChannel.close();
            randomAccessFile.close();
        }
    }

    @Test
    public void transferToStreamSinkChannel() throws Exception {
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        assertEquals(-1, channel.transferTo(50, ByteBuffer.allocate(60), channelMock));
    }

    @Test
    public void simpleRead() throws Exception {
        final ReadListener listener = new ReadListener();
        channel.getReadSetter().set(listener);
        channel.resumeReads();
        assertTrue(channel.isReadResumed());
        listener.waitInvocation();
        assertEquals(-1, listener.getReadToBufferResult());
        assertEquals(-1, listener.getReadToBufferArrayResult());
        assertEquals(-1, listener.getReadToBufferArrayWithOffsetResult());
        listener.clearListenerInvocationData();
        // show resume reads is idempotent
        assertFalse(listener.isInvoked());
        channel.resumeReads();
        channel.suspendReads();
        assertFalse(channel.isReadResumed());
        // suspend reads is idempotent as well
        channel.suspendReads();
        assertFalse(channel.isReadResumed());
    }

    @Test
    public void resumeReadAfterEmptied() throws Exception {
        final ReadListener listener = new ReadListener();
        channel.getReadSetter().set(listener);
        channel.resumeReads();
        assertTrue(channel.isReadResumed());
        listener.waitInvocation();
        channel.suspendReads();
        assertFalse(channel.isReadResumed());
        listener.clearListenerInvocationData();
        channel.resumeReads();
        assertTrue(channel.isReadResumed());
        assertFalse(listener.isInvoked());
    } 

    @Test
    public void wrappedReadListener() throws Exception {
        final ReadListener listener = new ReadListener();
        final WrappedReadListener wrappedListener = new WrappedReadListener(listener);
        channel.getReadSetter().set(wrappedListener);
        channel.resumeReads();
        assertTrue(channel.isReadResumed());
        listener.waitInvocation();
        assertEquals(-1, listener.getReadToBufferResult());
        assertEquals(-1, listener.getReadToBufferArrayResult());
        assertEquals(-1, listener.getReadToBufferArrayWithOffsetResult());
        listener.clearListenerInvocationData();
        // show resume reads is idempotent
        assertFalse(listener.isInvoked());
        channel.resumeReads();
        channel.suspendReads();
        assertFalse(channel.isReadResumed());
        // suspend reads is idempotent as well
        channel.suspendReads();
        assertFalse(channel.isReadResumed());
    }

    @Test
    public void listenerSuspendReadsOnChannel() throws Exception {
        final SuspendReadListener listener = new SuspendReadListener();
        channel.getReadSetter().set(listener);
        channel.resumeReads();
        listener.waitInvocation();
        assertFalse(channel.isReadResumed());
    }

    @Test
    public void resumeReadsWithoutListener() {
        channel.resumeReads();
    }

    @Test
    public void awaitReadable() throws IOException {
        // should return immediately no matter what
        channel.awaitReadable();
        channel.awaitReadable(500, TimeUnit.DAYS);
        channel.resumeReads();
        channel.awaitReadable();
        channel.awaitReadable(1, TimeUnit.SECONDS);
        channel.suspendReads();
        channel.awaitReadable();
        channel.awaitReadable(30, TimeUnit.MILLISECONDS);
    }

    @Test
    public void shutdownReads() throws Exception {
        final EmptyListener listener = new EmptyListener();
        channel.getCloseSetter().set(listener);
        channel.resumeReads();
        channel.shutdownReads();
        listener.waitInvocation();
        assertFalse(channel.isReadResumed());
        assertFalse(channel.isOpen());
        // shutdownReads is idempotent
        listener.clearInvocationData();
        channel.shutdownReads();
        assertFalse(listener.isInvoked());
        assertFalse(channel.isReadResumed());
        assertFalse(channel.isOpen());
    }

    @Test
    public void close() throws Exception {
        final EmptyListener listener = new EmptyListener();
        channel.getCloseSetter().set(listener);
        channel.close();
        listener.waitInvocation();
        assertFalse(channel.isReadResumed());
        assertFalse(channel.isOpen());
        // close is idempotent
        listener.clearInvocationData();
        channel.close();
        assertFalse(listener.isInvoked());
        assertFalse(channel.isReadResumed());
        assertFalse(channel.isOpen());
        assertEquals(-1, channel.read(ByteBuffer.allocate(15)));
        final File file = File.createTempFile("test", ".txt");
        file.deleteOnExit();
        final RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
        final FileChannel fileChannel = randomAccessFile.getChannel();
        try {
            assertEquals(0, channel.transferTo(0, 10, fileChannel));
        } finally {
            fileChannel.close();
            randomAccessFile.close();
        }
    }

    @Test
    public void listenerClosesChannel() throws Exception {
    }

    private static class ReadListener implements ChannelListener<StreamSourceChannel> {

        private CountDownLatch countDownLatch = new CountDownLatch(1);
        private boolean listenerInvoked = false;
        private int readToBufferResult = 0;
        private long readToBufferArrayResult = 0;
        private long readToBufferArrayWithOffsetResult = 0;

        @Override
        public void handleEvent(StreamSourceChannel channel) {
            listenerInvoked = true;
            countDownLatch.countDown();
            final ByteBuffer[] buffer = new ByteBuffer[] {ByteBuffer.allocate(10)};
            try {
                readToBufferResult = channel.read(buffer[0]);
                readToBufferArrayResult = channel.read(buffer);
                readToBufferArrayWithOffsetResult = channel.read(buffer, 0, 1);
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }

        public void waitInvocation() throws InterruptedException {
            countDownLatch.await();
        }

        public boolean isInvoked() {
            return listenerInvoked;
        }

        public int getReadToBufferResult() {
            return readToBufferResult;
        }

        public long getReadToBufferArrayResult() {
            return readToBufferArrayResult;
        }

        public long getReadToBufferArrayWithOffsetResult() {
            return readToBufferArrayWithOffsetResult;
        }

        public void clearListenerInvocationData() {
            listenerInvoked = false;
            countDownLatch = new CountDownLatch(1);
        }

    }

    private static class SuspendReadListener implements ChannelListener<StreamSourceChannel> {

        private final CountDownLatch countDownLatch = new CountDownLatch(1);

        @Override
        public void handleEvent(StreamSourceChannel channel) {
            countDownLatch.countDown();
            channel.suspendReads();
        }

        public void waitInvocation() throws InterruptedException {
            countDownLatch.await();
        }
    }

    private static class WrappedReadListener implements ChannelListener<StreamSourceChannel> {

        private final ChannelListener<StreamSourceChannel> listener;

        public WrappedReadListener(ChannelListener<StreamSourceChannel> listener) {
            this.listener = listener;
        }

        @Override
        public void handleEvent(StreamSourceChannel channel) {
            channel.getReadSetter().set(listener);
        }
    }

    private static class EmptyListener implements ChannelListener<StreamSourceChannel> {

        private CountDownLatch countDownLatch = new CountDownLatch(1);
        private boolean invoked = false;

        @Override
        public void handleEvent(StreamSourceChannel channel) {
            countDownLatch.countDown();
            invoked = true;
        }

        public void waitInvocation() throws InterruptedException {
            countDownLatch.await();
        }

        public boolean isInvoked() {
            return invoked;
        }

        public void clearInvocationData() {
            invoked = false;
            countDownLatch = new CountDownLatch(1);
        }
    }
}
