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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.xnio.AssertReadWrite.assertReadMessage;
import static org.xnio.AssertReadWrite.assertWrittenMessage;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import org.jmock.lib.concurrent.DeterministicExecutor;
import org.junit.Test;
import org.xnio.channels.AcceptingChannel;
import org.xnio.mock.AcceptingChannelMock2;
import org.xnio.mock.ConnectedStreamChannelMock;
import org.xnio.mock.MessageChannelMock;
import org.xnio.mock.StreamConnectionMock;

/**
 * Test for {@link ChannelListeners}.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 *
 */
public class ChannelListenersTestCase {

    @Test
    public void invokeChannelListener() {
        final ConnectedStreamChannelMock channel = new ConnectedStreamChannelMock();
        final TestChannelListener<ConnectedStreamChannelMock> listener = new TestChannelListener<ConnectedStreamChannelMock>();
        assertTrue(ChannelListeners.invokeChannelListener(channel, listener));
        assertTrue(listener.isInvoked());
        assertSame(channel, listener.getTargetChannel());
    }

    @Test
    public void invokeChannelListenerWithException() {
        final ConnectedStreamChannelMock channel = new ConnectedStreamChannelMock();
        final TestChannelListener<ConnectedStreamChannelMock> listener = new TestChannelListener<ConnectedStreamChannelMock>();
        listener.throwExceptionOnHandle();
        assertFalse(ChannelListeners.invokeChannelListener(channel, listener));
        assertTrue(listener.isInvoked());
        assertSame(channel, listener.getTargetChannel());
    }

    @Test
    public void invokeNullChannelListener() {
        final ConnectedStreamChannelMock channel = new ConnectedStreamChannelMock();
        assertTrue(ChannelListeners.invokeChannelListener(channel, null));
    }

    @Test
    public void invokeChannelListenerWithExecutor() {
        final DeterministicExecutor executor = new DeterministicExecutor();
        final ConnectedStreamChannelMock channel = new ConnectedStreamChannelMock();
        final TestChannelListener<ConnectedStreamChannelMock> listener = new TestChannelListener<ConnectedStreamChannelMock>();
        ChannelListeners.invokeChannelListener(executor, channel, listener);
        assertFalse(listener.isInvoked());
        executor.runPendingCommands();
        assertTrue(listener.isInvoked());
        assertSame(channel, listener.getTargetChannel());
    }

    @Test
    public void invokeChannelListenerWithExecutorAndException() {
        final DeterministicExecutor executor = new DeterministicExecutor();
        final ConnectedStreamChannelMock channel = new ConnectedStreamChannelMock();
        final TestChannelListener<ConnectedStreamChannelMock> listener = new TestChannelListener<ConnectedStreamChannelMock>();
        listener.throwExceptionOnHandle();
        ChannelListeners.invokeChannelListener(executor, channel, listener);
        assertFalse(listener.isInvoked());
        executor.runPendingCommands();
        assertTrue(listener.isInvoked());
        assertSame(channel, listener.getTargetChannel());
    }

    @Test
    public void invokeNullChannelListenerWithExecutor() {
        final DeterministicExecutor executor = new DeterministicExecutor();
        final ConnectedStreamChannelMock channel = new ConnectedStreamChannelMock();
        ChannelListeners.invokeChannelListener(executor, channel, null);
        executor.runPendingCommands();
    }

    @Test
    public void invokeChannelListenerWithRejectedExecution() {
        final ExecutionRejector executor = new ExecutionRejector();
        final ConnectedStreamChannelMock channel = new ConnectedStreamChannelMock();
        final TestChannelListener<ConnectedStreamChannelMock> listener = new TestChannelListener<ConnectedStreamChannelMock>();
        ChannelListeners.invokeChannelListener(executor, channel, listener);
        assertTrue(listener.isInvoked());
        assertSame(channel, listener.getTargetChannel());
    }

    @Test
    public void invokeChannelListenerWithRejectedExecutionAndListenerException() {
        final ExecutionRejector executor = new ExecutionRejector();
        final ConnectedStreamChannelMock channel = new ConnectedStreamChannelMock();
        final TestChannelListener<ConnectedStreamChannelMock> listener = new TestChannelListener<ConnectedStreamChannelMock>();
        listener.throwExceptionOnHandle();
        ChannelListeners.invokeChannelListener(executor, channel, listener);
        assertTrue(listener.isInvoked());
        assertSame(channel, listener.getTargetChannel());
    }

    @Test
    public void invokeNullChannelListenerWithRejectedExecution() {
        final ExecutionRejector executor = new ExecutionRejector();
        final ConnectedStreamChannelMock channel = new ConnectedStreamChannelMock();
        ChannelListeners.invokeChannelListener(executor, channel, null);
    }

    @Test
    public void closingChannelListener() {
        final ChannelListener<Channel> closingListener = ChannelListeners.closingChannelListener();
        assertNotNull(closingListener);
        final ConnectedStreamChannelMock channel = new ConnectedStreamChannelMock();
        assertTrue(channel.isOpen());

        closingListener.handleEvent(channel);
        assertFalse(channel.isOpen());

        // nothing happens
        closingListener.handleEvent(null);
    }

    @Test
    public void nullChannelListener() {
        final ChannelListener<Channel> nullListener = ChannelListeners.nullChannelListener();
        assertNotNull(nullListener);
        final ConnectedStreamChannelMock channel = new ConnectedStreamChannelMock();
        assertTrue(channel.getWrittenText().isEmpty());
        assertTrue(channel.isOpen());

        // nothing happens
        nullListener.handleEvent(channel);
        assertTrue(channel.getWrittenText().isEmpty());
        assertTrue(channel.isOpen());

        nullListener.handleEvent(null);
    }

    @Test
    public void openListenerAdapter() {
        final AcceptingChannelMock2 acceptingChannelMock = new AcceptingChannelMock2();

        IllegalArgumentException expected = null;
        try {
            ChannelListeners.openListenerAdapter(null);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        final TestChannelListener<StreamConnection> testListener = new TestChannelListener<StreamConnection>();
        final ChannelListener<AcceptingChannel<StreamConnection>> acceptingListener = ChannelListeners.openListenerAdapter(testListener);
        assertNotNull(acceptingListener);
        assertNotNull(acceptingListener.toString());

        assertFalse(testListener.isInvoked());

        acceptingChannelMock.enableAcceptance(false);
        acceptingListener.handleEvent(acceptingChannelMock);
        assertFalse(testListener.isInvoked());

        acceptingChannelMock.enableAcceptance(true);
        acceptingListener.handleEvent(acceptingChannelMock);
        assertTrue(testListener.isInvoked());
        assertNotNull(testListener.getTargetChannel());

        final AcceptingChannelMock2 failingAcceptingChannel = new AcceptingChannelMock2() {
            @Override
            public StreamConnectionMock accept() throws IOException {
                throw new IOException("Test exception");
            }
        };
        // nothing should happen
        acceptingListener.handleEvent(failingAcceptingChannel);
    }

    @Test
    public void channelListenerSetterByFieldUpdater() {
        final ListenerSetterTesterChannel channel = new ListenerSetterTesterChannel();
        @SuppressWarnings("rawtypes")
        final AtomicReferenceFieldUpdater<ListenerSetterTesterChannel, ChannelListener> fieldUpdater =
            channel.getFieldUpdater();
        final TestChannelListener<ConnectedStreamChannelMock> listener1 = new TestChannelListener<ConnectedStreamChannelMock>();
        final TestChannelListener<ConnectedStreamChannelMock> listener2 = new TestChannelListener<ConnectedStreamChannelMock>();
        final TestChannelListener<ConnectedStreamChannelMock> listener3 = new TestChannelListener<ConnectedStreamChannelMock>();

        @SuppressWarnings("deprecation")
        final ChannelListener.Setter<ConnectedStreamChannelMock> setter = ChannelListeners.getSetter(channel, fieldUpdater);
        setter.set(listener1);
        assertSame(listener1, channel.getListener());

        setter.set(listener2);
        assertSame(listener2, channel.getListener());

        setter.set(listener3);
        assertSame(listener3, channel.getListener());

        setter.set(null);
        assertSame(null, channel.getListener());
    }

    @Test
    public void channelListenerSetterByAtomicReference() {
        final AtomicReference<ChannelListener<? super ConnectedStreamChannelMock>> atomicReference = new AtomicReference<ChannelListener<? super ConnectedStreamChannelMock>>();
        final TestChannelListener<ConnectedStreamChannelMock> listener1 = new TestChannelListener<ConnectedStreamChannelMock>();
        final TestChannelListener<ConnectedStreamChannelMock> listener2 = new TestChannelListener<ConnectedStreamChannelMock>();
        final TestChannelListener<ConnectedStreamChannelMock> listener3 = new TestChannelListener<ConnectedStreamChannelMock>();

        final ChannelListener.Setter<ConnectedStreamChannelMock> setter = ChannelListeners.
            <ConnectedStreamChannelMock>getSetter(atomicReference);
        setter.set(listener1);
        assertSame(listener1, atomicReference.get());

        setter.set(listener2);
        assertSame(listener2, atomicReference.get());

        setter.set(listener3);
        assertSame(listener3, atomicReference.get());

        setter.set(null);
        assertSame(null, atomicReference.get());
    }

    @Test
    public void channelListenerDelegatingSetter() {
        final AtomicReference<ChannelListener<? super ConnectedStreamChannelMock>> atomicReference = new AtomicReference<ChannelListener<? super ConnectedStreamChannelMock>>();
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        final TestChannelListener<ConnectedStreamChannelMock> listener1 = new TestChannelListener<ConnectedStreamChannelMock>();
        final TestChannelListener<ConnectedStreamChannelMock> listener2 = new TestChannelListener<ConnectedStreamChannelMock>();
        final TestChannelListener<ConnectedStreamChannelMock> listener3 = new TestChannelListener<ConnectedStreamChannelMock>();

        final ChannelListener.Setter<ConnectedStreamChannelMock> setter = ChannelListeners.
            <ConnectedStreamChannelMock>getSetter(atomicReference);
        final ChannelListener.Setter<ConnectedStreamChannelMock> delegatingSetter = ChannelListeners.getDelegatingSetter(setter, channelMock);
        delegatingSetter.set(listener1);
        final ChannelListener<? super ConnectedStreamChannelMock> delegatingListener1 = atomicReference.get();
        assertNotNull(delegatingListener1);

        delegatingSetter.set(listener2);
        final ChannelListener<? super ConnectedStreamChannelMock> delegatingListener2 = atomicReference.get();
        assertNotNull(delegatingListener2);

        delegatingSetter.set(listener3);
        final ChannelListener<? super ConnectedStreamChannelMock> delegatingListener3 = atomicReference.get();
        assertNotNull(delegatingListener2);

        delegatingSetter.set(null);
        assertSame(null, atomicReference.get());

        assertFalse(listener1.isInvoked());
        delegatingListener1.handleEvent(null);
        assertTrue(listener1.isInvoked());
        assertSame(channelMock, listener1.getTargetChannel());

        assertFalse(listener2.isInvoked());
        delegatingListener2.handleEvent(new ConnectedStreamChannelMock());
        assertTrue(listener2.isInvoked());
        assertSame(channelMock, listener2.getTargetChannel());

        assertFalse(listener3.isInvoked());
        delegatingListener3.handleEvent(channelMock);
        assertTrue(listener3.isInvoked());
        assertSame(channelMock, listener3.getTargetChannel());

        assertNull(ChannelListeners.getDelegatingSetter(null, channelMock));
    }

    @Test
    public void nullSetter() {
        final ChannelListener.Setter<ConnectedStreamChannelMock> nullSetter = ChannelListeners.<ConnectedStreamChannelMock>nullSetter();
        assertNotNull(nullSetter);
        // nothing should happen
        nullSetter.set(null);
        nullSetter.set(new TestChannelListener<ConnectedStreamChannelMock>());
    }

    @Test
    public void executorChanneListener() {
        final TestChannelListener<ConnectedStreamChannelMock> listener = new TestChannelListener<ConnectedStreamChannelMock>();
        final DeterministicExecutor executor = new DeterministicExecutor();
        final ConnectedStreamChannelMock channel = new ConnectedStreamChannelMock();
        final ChannelListener<ConnectedStreamChannelMock> executorListener = ChannelListeners.executorChannelListener(listener, executor);

        assertNotNull(executorListener);
        executorListener.handleEvent(channel);
        assertFalse(listener.isInvoked());
        executor.runPendingCommands();
        assertTrue(listener.isInvoked());
        assertSame(channel, listener.getTargetChannel());

        final Executor failingExecutor = new ExecutionRejector();
        final ChannelListener<ConnectedStreamChannelMock> failingExecutorListener = ChannelListeners.executorChannelListener(listener, failingExecutor);
        assertNotNull(failingExecutor);
        listener.clear();
        assertTrue(channel.isOpen());
        failingExecutorListener.handleEvent(channel);
        assertFalse(listener.isInvoked());
        assertFalse(channel.isOpen());
    }

    @Test
    public void flushingChannelListener() throws IOException {
        final TestChannelListener<ConnectedStreamChannelMock> listener = new TestChannelListener<ConnectedStreamChannelMock>();
        final FailingChannel channel = new FailingChannel();
        final TestExceptionHandler<ConnectedStreamChannelMock> exceptionHandler = new TestExceptionHandler<ConnectedStreamChannelMock>();
        final ChannelListener<ConnectedStreamChannelMock> flushingListener = ChannelListeners.
            flushingChannelListener(listener, exceptionHandler);
 
        assertNotNull(flushingListener);
        assertNotNull(flushingListener.toString());

        final ByteBuffer tempBuffer = ByteBuffer.allocate(10);
        tempBuffer.put("anything".getBytes()).flip();
        channel.write(tempBuffer);
        assertFalse(channel.isFlushed());
        channel.enableFlush(false);

        // try to flush, but flush will return false
        assertFalse(channel.isWriteResumed());
        assertNull(channel.getWriteListener());
        flushingListener.handleEvent(channel);
        assertSame(flushingListener, channel.getWriteListener());
        assertTrue(channel.isWriteResumed());
        assertFalse(channel.isFlushed());

        // try to flush again, this time flush will throw an IOException
        final IOException flushFailure = new IOException("Test exception");
        channel.throwExceptionOnFlush(flushFailure);
        assertFalse(exceptionHandler.isInvoked());
        flushingListener.handleEvent(channel);
        assertFalse(channel.isWriteResumed());
        assertTrue(exceptionHandler.isInvoked());
        assertSame(channel, exceptionHandler.getFailingChannel());
        assertSame(flushFailure, exceptionHandler.getFailure());

        // try to flush again, this time flush will return true
        channel.enableFlush(true);
        flushingListener.handleEvent(channel);
        assertSame(listener, channel.getWriteListener());
        assertTrue(listener.isInvoked());
        assertSame(channel, listener.getTargetChannel());
    }

    @Test
    public void writeShutdownListener() {
        final FailingChannel channel = new FailingChannel();
        final TestChannelListener<ConnectedStreamChannelMock> listener = new TestChannelListener<ConnectedStreamChannelMock>();
        final TestExceptionHandler<ConnectedStreamChannelMock> exceptionHandler = new TestExceptionHandler<ConnectedStreamChannelMock>();

        final ChannelListener<ConnectedStreamChannelMock> writeShutdownListener = ChannelListeners.writeShutdownChannelListener(listener, exceptionHandler);
        assertNotNull(writeShutdownListener);

        // try to handle event, shutdownWrites will throw an exception
        final IOException exception = new IOException("Test exception");
        channel.throwExceptionOnShutdownWrites(exception);
        writeShutdownListener.handleEvent(channel);
        assertFalse(listener.isInvoked());
        assertTrue(exceptionHandler.isInvoked());
        assertSame(channel, exceptionHandler.getFailingChannel());
        assertSame(exception, exceptionHandler.getFailure());

        // try again, this time no exception will be thrown
        assertFalse(channel.isShutdownWrites());
        writeShutdownListener.handleEvent(channel);
        assertTrue(channel.isShutdownWrites());
        assertTrue(listener.isInvoked());
        assertSame(channel, listener.getTargetChannel());
    }

    @Test
    public void writingChannelListener() {
        final Pool<ByteBuffer> pool = new ByteBufferSlicePool(15, 15);
        final Pooled<ByteBuffer> pooledBuffer1 = pool.allocate();
        final TestChannelListener<ConnectedStreamChannelMock> listener = new TestChannelListener<ConnectedStreamChannelMock>();
        final FailingChannel channel = new FailingChannel();
        final TestExceptionHandler<ConnectedStreamChannelMock> exceptionHandler = new TestExceptionHandler<ConnectedStreamChannelMock>();

        final ChannelListener<ConnectedStreamChannelMock> writingListener1 = ChannelListeners.writingChannelListener(pooledBuffer1, listener, exceptionHandler);
        assertNotNull(writingListener1);
        assertNotNull(writingListener1.toString());

        // attempt to write will fail
        final IOException writeException = new IOException("Test exception");
        channel.throwExceptionOnWrite(writeException);
        channel.resumeWrites();

        writingListener1.handleEvent(channel);
        assertFalse(channel.isWriteResumed());
        assertTrue(exceptionHandler.isInvoked());
        assertSame(channel, exceptionHandler.getFailingChannel());
        assertSame(writeException, exceptionHandler.getFailure());
        IllegalStateException expected = null;
        try {
            pooledBuffer1.getResource();
        } catch (IllegalStateException e) {
            expected = e;
        }
        assertNotNull(expected);

        final Pooled<ByteBuffer> pooledBuffer2 = pool.allocate();
        pooledBuffer2.getResource().put("abc".getBytes()).flip();
        final ChannelListener<ConnectedStreamChannelMock> writingListener2 = ChannelListeners.writingChannelListener(pooledBuffer2, listener, exceptionHandler);
        assertNotNull(writingListener2);
        assertNotNull(writingListener2.toString());

        // attempt again to write... this time channel will refuse to perform write
        channel.enableWrite(false);
        writingListener2.handleEvent(channel);
        assertSame(writingListener2, channel.getWriteListener());
        assertTrue(channel.isWriteResumed());
        assertFalse(listener.isInvoked());

        // attempt again to write... this time channel will perform the write
        channel.getWriteSetter().set(null);
        channel.suspendWrites();
        channel.enableWrite(true);
        assertWrittenMessage(channel);
        writingListener2.handleEvent(channel);
        assertWrittenMessage(channel, "abc");
        assertNull(channel.getWriteListener());
        assertFalse(channel.isWriteResumed());
        assertTrue(listener.isInvoked());
        assertSame(channel, listener.getTargetChannel());

        final Pooled<ByteBuffer> pooledBuffer3 = pool.allocate();
        pooledBuffer3.getResource().put("defghij".getBytes()).flip();
        listener.clear();
        final ChannelListener<ConnectedStreamChannelMock> writingListener3 = ChannelListeners.writingChannelListener(pooledBuffer3, listener, exceptionHandler);
        assertNotNull(writingListener3);
        assertNotNull(writingListener3.toString());

        // attempt again to write... this time channel will perform a write of part of the bytes and listener
        // will have to make several requests to write, in order to write the 7 bytes
        channel.limitWrite(3);
        assertWrittenMessage(channel, "abc");
        writingListener3.handleEvent(channel);
        assertWrittenMessage(channel, "abc", "defghij");
        assertNull(channel.getWriteListener());
        assertFalse(channel.isWriteResumed());
        assertTrue(listener.isInvoked());
        assertSame(channel, listener.getTargetChannel());
    }

    @Test
    public void sendingChannelListener() {
        final Pool<ByteBuffer> pool = new ByteBufferSlicePool(15, 15);
        final Pooled<ByteBuffer> pooledBuffer1 = pool.allocate();
        final TestChannelListener<MessageChannelMock> listener = new TestChannelListener<MessageChannelMock>();
        final FailingChannel connectedChannel = new FailingChannel();
        final MessageChannelMock channel = new MessageChannelMock(connectedChannel);
        final TestExceptionHandler<MessageChannelMock> exceptionHandler = new TestExceptionHandler<MessageChannelMock>();

        final ChannelListener<MessageChannelMock> sendingListener1 = ChannelListeners.
            <MessageChannelMock>sendingChannelListener(pooledBuffer1, listener, exceptionHandler);
        assertNotNull(sendingListener1);
        assertNotNull(sendingListener1.toString());

        // attempt to write will fail
        final IOException writeException = new IOException("Test exception");
        connectedChannel.throwExceptionOnWrite(writeException);
        connectedChannel.resumeWrites();
        channel.resumeWrites();

        sendingListener1.handleEvent(channel);
        assertFalse(channel.isWriteResumed());
        assertTrue(exceptionHandler.isInvoked());
        assertSame(channel, exceptionHandler.getFailingChannel());
        assertSame(writeException, exceptionHandler.getFailure());
        IllegalStateException expected = null;
        try {
            pooledBuffer1.getResource();
        } catch (IllegalStateException e) {
            expected = e;
        }
        assertNotNull(expected);

        final Pooled<ByteBuffer> pooledBuffer2 = pool.allocate();
        final ChannelListener<MessageChannelMock> sendingListener2 = ChannelListeners.
            <MessageChannelMock>sendingChannelListener(pooledBuffer2, listener, exceptionHandler);
        assertNotNull(sendingListener2);
        assertNotNull(sendingListener2.toString());

        // attempt to write will fail again, this time with a runtime exception
        final RuntimeException runtimeWriteException = new RuntimeException("Test exception");
        connectedChannel.throwExceptionOnWrite(runtimeWriteException);
        connectedChannel.resumeWrites();
        channel.resumeWrites();
        exceptionHandler.clear();

        RuntimeException expectedWriteException = null;
        try {
            sendingListener2.handleEvent(channel);
        } catch (RuntimeException e) {
            expectedWriteException = e;
        }
        assertSame(runtimeWriteException, expectedWriteException);

        assertTrue(channel.isWriteResumed());
        assertFalse(exceptionHandler.isInvoked());
        expected = null;
        try {
            pooledBuffer2.getResource();
        } catch (IllegalStateException e) {
            expected = e;
        }
        assertNotNull(expected);

        final Pooled<ByteBuffer> pooledBuffer3 = pool.allocate();
        pooledBuffer3.getResource().put("abc".getBytes()).flip();
        final ChannelListener<MessageChannelMock> sendingListener3 = ChannelListeners.
            <MessageChannelMock>sendingChannelListener(pooledBuffer3, listener, exceptionHandler);
        assertNotNull(sendingListener3);
        assertNotNull(sendingListener3.toString());

        // attempt again to write... this time channel will refuse to perform write
        connectedChannel.enableWrite(false);
        sendingListener3.handleEvent(channel);
        assertSame(sendingListener3, channel.getWriteListener());
        assertTrue(channel.isWriteResumed());
        assertFalse(listener.isInvoked());

        // attempt again to write... this time channel will perform the write
        channel.getWriteSetter().set(null);
        channel.suspendWrites();
        connectedChannel.enableWrite(true);
        assertWrittenMessage(connectedChannel);
        sendingListener3.handleEvent(channel);
        assertWrittenMessage(connectedChannel, "abc");
        assertNull(channel.getWriteListener());
        assertFalse(channel.isWriteResumed());
        assertTrue(listener.isInvoked());
        assertSame(channel, listener.getTargetChannel());
    }

    @Test
    public void fileSendingChannelListener() throws IOException {
        final TestChannelListener<ConnectedStreamChannelMock> listener = new TestChannelListener<ConnectedStreamChannelMock>();
        final TestExceptionHandler<ConnectedStreamChannelMock> exceptionHandler = new TestExceptionHandler<ConnectedStreamChannelMock>();
        final FailingChannel channel = new FailingChannel();
        final File file = File.createTempFile("test", ".txt");
        file.deleteOnExit();
        final RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
        final FileChannel fileChannel = randomAccessFile.getChannel();
        try {
            final ByteBuffer buffer = ByteBuffer.allocate(10);
            buffer.put("test".getBytes("UTF-8")).flip();
            assertEquals(4, fileChannel.write(buffer));
            fileChannel.position(0);
    
            final ChannelListener<ConnectedStreamChannelMock> fileSendingChannelListener1 = ChannelListeners.
                <ConnectedStreamChannelMock>fileSendingChannelListener(fileChannel, 0, 4, listener, exceptionHandler);
    
            // attempt to transfer, it will fail because writing is disabled on the channel
            channel.enableWrite(false);
            assertNull(channel.getWriteListener());
            assertFalse(channel.isWriteResumed());
            fileSendingChannelListener1.handleEvent(channel);
            assertSame(fileSendingChannelListener1, channel.getWriteListener());
            assertTrue(channel.isWriteResumed());
    
            // attempt again, this time with write enabled
            channel.enableWrite(true);
            assertFalse(listener.isInvoked());
            assertTrue(channel.getWrittenText().isEmpty());
            fileSendingChannelListener1.handleEvent(channel);
            assertTrue(listener.isInvoked());
            assertSame(channel, listener.getTargetChannel());
            assertWrittenMessage(channel, "test");
    
            listener.clear();
            final ChannelListener<ConnectedStreamChannelMock> fileSendingChannelListener2 = ChannelListeners.
                <ConnectedStreamChannelMock>fileSendingChannelListener(fileChannel, 0, 0, listener, exceptionHandler);
            fileSendingChannelListener2.handleEvent(channel);
            assertTrue(listener.isInvoked());
            assertSame(channel, listener.getTargetChannel());
    
            final ChannelListener<ConnectedStreamChannelMock> fileSendingChannelListener3 = ChannelListeners.
            <ConnectedStreamChannelMock>fileSendingChannelListener(fileChannel, 0, 10, listener, exceptionHandler);
            buffer.clear();
            buffer.put("1234567890".getBytes()).flip();
            assertEquals(10, fileChannel.write(buffer));
    
            final IOException writeFailure = new IOException("Test exception");
            channel.throwExceptionOnWrite(writeFailure);
            assertFalse(exceptionHandler.isInvoked());
            fileSendingChannelListener3.handleEvent(channel);
            assertTrue(exceptionHandler.isInvoked());
            assertSame(channel, exceptionHandler.getFailingChannel());
            assertSame(writeFailure, exceptionHandler.getFailure());
    
            channel.limitWrite(3);
            listener.clear();
            assertWrittenMessage(channel, "test");
            fileSendingChannelListener3.handleEvent(channel);
            assertTrue(listener.isInvoked());
            assertSame(channel, listener.getTargetChannel());
            assertWrittenMessage(channel, "test", "1234567890");
        } finally {
            randomAccessFile.close();
            fileChannel.close();
        }
    }

    @Test
    public void fileReceivingChannelListener() throws IOException {
        final TestChannelListener<ConnectedStreamChannelMock> listener = new TestChannelListener<ConnectedStreamChannelMock>();
        final TestExceptionHandler<ConnectedStreamChannelMock> exceptionHandler = new TestExceptionHandler<ConnectedStreamChannelMock>();
        final FailingChannel channel = new FailingChannel();
        channel.setReadData("test");
        final File file = File.createTempFile("test", ".txt");
        file.deleteOnExit();
        final RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
        final FileChannelWrapper fileChannel = new FileChannelWrapper(randomAccessFile.getChannel());
        try {
            final ChannelListener<ConnectedStreamChannelMock> fileReceivingChannelListener1 = ChannelListeners.
                <ConnectedStreamChannelMock>fileReceivingChannelListener(fileChannel, 0, 4, listener, exceptionHandler);
    
            // attempt to transfer, it will fail because reading is disabled on the channel
            channel.enableRead(false);
            assertNull(channel.getReadListener());
            assertFalse(channel.isReadResumed());
            fileReceivingChannelListener1.handleEvent(channel);
            assertSame(fileReceivingChannelListener1, channel.getReadListener());
            assertTrue(channel.isReadResumed());
    
            // attempt again, this time with read enabled
            channel.enableRead(true);
            assertFalse(listener.isInvoked());
            final ByteBuffer buffer = ByteBuffer.allocate(15);
            fileChannel.read(buffer);
            assertReadMessage(buffer);
            fileReceivingChannelListener1.handleEvent(channel);
            assertTrue(listener.isInvoked());
            assertSame(channel, listener.getTargetChannel());
            buffer.clear();
            fileChannel.read(buffer);
            assertReadMessage(buffer, "test");
    
            listener.clear();
            final ChannelListener<ConnectedStreamChannelMock> fileReceivingChannelListener2 = ChannelListeners.
                <ConnectedStreamChannelMock>fileReceivingChannelListener(fileChannel, 0, 0, listener, exceptionHandler);
            fileReceivingChannelListener2.handleEvent(channel);
            assertTrue(listener.isInvoked());
            assertSame(channel, listener.getTargetChannel());
    
            final ChannelListener<ConnectedStreamChannelMock> fileReceivingChannelListener3 = ChannelListeners.
                <ConnectedStreamChannelMock>fileReceivingChannelListener(fileChannel, 4, 10, listener, exceptionHandler);
            channel.setReadData("1234567890");
    
            final IOException readFailure = new IOException("Test exception");
            channel.throwExceptionOnRead(readFailure);
            assertFalse(exceptionHandler.isInvoked());
            fileReceivingChannelListener3.handleEvent(channel);
            assertTrue(exceptionHandler.isInvoked());
            assertSame(channel, exceptionHandler.getFailingChannel());
            assertSame(readFailure, exceptionHandler.getFailure());
    
            fileChannel.limitTransfer(3);
            listener.clear();
            buffer.clear();
            fileChannel.position(0);
            fileChannel.read(buffer);
            assertReadMessage(buffer, "test");
            fileReceivingChannelListener3.handleEvent(channel);
            assertTrue(listener.isInvoked());
            assertSame(channel, listener.getTargetChannel());
            buffer.clear();
            fileChannel.position(0);
            fileChannel.read(buffer);
            assertReadMessage(buffer, "test", "1234567890");
        } finally {
            randomAccessFile.close();
            fileChannel.close();
        }
    }

    @Test
    public void delegatingChannelListener() {
        final TestChannelListener<ConnectedStreamChannelMock> listener = new TestChannelListener<ConnectedStreamChannelMock>();
        final ConnectedStreamChannelMock channel = new ConnectedStreamChannelMock();
        final ChannelListener<ConnectedStreamChannelMock> delegatingListener = ChannelListeners.
            <ConnectedStreamChannelMock>delegatingChannelListener(listener);
        assertNotNull(delegatingListener);

        assertFalse(listener.isInvoked());
        delegatingListener.handleEvent(channel);
        assertTrue(listener.isInvoked());
        assertSame(channel, listener.getTargetChannel());
    }

    @Test
    public void writeSuspendingChannelListener() {
        final TestChannelListener<ConnectedStreamChannelMock> listener = new TestChannelListener<ConnectedStreamChannelMock>();
        final ConnectedStreamChannelMock channel = new ConnectedStreamChannelMock();
        final ChannelListener<ConnectedStreamChannelMock> writeSuspendingListener = ChannelListeners.
            <ConnectedStreamChannelMock>writeSuspendingChannelListener(listener);
        assertNotNull(writeSuspendingListener);

        assertFalse(listener.isInvoked());
        channel.resumeWrites();
        writeSuspendingListener.handleEvent(channel);
        assertFalse(channel.isWriteResumed());
        assertTrue(listener.isInvoked());
        assertSame(channel, listener.getTargetChannel());
    }

    @Test
    public void readSuspendingChannelListener() {
        final TestChannelListener<ConnectedStreamChannelMock> listener = new TestChannelListener<ConnectedStreamChannelMock>();
        final ConnectedStreamChannelMock channel = new ConnectedStreamChannelMock();
        final ChannelListener<ConnectedStreamChannelMock> readSuspendingListener = ChannelListeners.
            <ConnectedStreamChannelMock>readSuspendingChannelListener(listener);
        assertNotNull(readSuspendingListener);

        assertFalse(listener.isInvoked());
        channel.resumeReads();
        readSuspendingListener.handleEvent(channel);
        assertFalse(channel.isReadResumed());
        assertTrue(listener.isInvoked());
        assertSame(channel, listener.getTargetChannel());
    }

    private static class TestChannelListener<C extends Channel> implements ChannelListener<C> {

        private boolean invoked = false;
        private Channel channel = null;
        private boolean throwException = false;

        public void throwExceptionOnHandle() {
            throwException = true;
        }


        @Override
        public void handleEvent(C c) {
            invoked = true;
            channel = c;
            if (throwException) {
                throw new RuntimeException("Test exception");
            }
        }

        public boolean isInvoked() {
            return invoked;
        }

        public Channel getTargetChannel() {
            return channel;
        }

        public void clear() {
            invoked = false;
            channel = null;
        }
    }

    private static class ExecutionRejector implements Executor {

        @Override
        public void execute(Runnable command) {
            throw new RejectedExecutionException("Test execption");
        }
        
    }

    private static class ListenerSetterTesterChannel extends ConnectedStreamChannelMock {
        private volatile ChannelListener<ConnectedStreamChannelMock> listener = null;

        @SuppressWarnings("rawtypes")
        public AtomicReferenceFieldUpdater<ListenerSetterTesterChannel, ChannelListener> getFieldUpdater() {
            return AtomicReferenceFieldUpdater.newUpdater(ListenerSetterTesterChannel.class, ChannelListener.class, "listener");
        }

        public ChannelListener<ConnectedStreamChannelMock> getListener() {
            return listener;
        }
    }

    private static class FailingChannel extends ConnectedStreamChannelMock {
        private IOException flushException = null;
        private IOException writeShutdownException = null;
        private IOException writeException = null;
        private RuntimeException runtimeWriteException = null;
        private IOException readException = null;
        private int writeLimit = -1;

        public void throwExceptionOnFlush(IOException exception) {
            flushException = exception;
        }

        @Override
        public boolean flush() throws IOException {
            if (flushException != null) {
                try {
                    throw flushException;
                } finally {
                    flushException = null;
                }
            }
            return super.flush();
        }

        public void throwExceptionOnShutdownWrites(IOException exception) {
            writeShutdownException = exception;
        }

        @Override
        public void shutdownWrites() throws IOException {
            if (writeShutdownException != null) {
                try {
                    throw writeShutdownException;
                } finally {
                    writeShutdownException = null;
                }
            }
            super.shutdownWrites();
        }

        public void throwExceptionOnWrite(IOException exception) {
            writeException = exception;
        }

        public void throwExceptionOnWrite(RuntimeException exception) {
            runtimeWriteException = exception;
        }

        @Override
        public synchronized int write(ByteBuffer src) throws IOException {
            if (writeException != null) {
                try {
                    throw writeException;
                } finally {
                    writeException = null;
                }
            }
            if (runtimeWriteException != null) {
                try {
                    throw runtimeWriteException;
                } finally {
                    runtimeWriteException = null;
                }
            }
            if (writeLimit == -1) {
                return super.write(src);
            }
            int originalLimit = src.limit();
            if (src.remaining() > writeLimit) {
                src.limit(originalLimit - src.remaining() + writeLimit);
            }
            try {
                return super.write(src);
            } finally {
                src.limit(originalLimit);
            }
        }

        @Override
        public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
            if (writeException != null) {
                try {
                    throw writeException;
                } finally {
                    writeException = null;
                }
            }
            if (runtimeWriteException != null) {
                try {
                    throw runtimeWriteException;
                } finally {
                    runtimeWriteException = null;
                }
            }
            if (writeLimit == -1) {
                return super.write(srcs, offset, length);
            }
            int extraByteLength = (int) Buffers.remaining(srcs, offset, length) - writeLimit;
            int originalLimit = -1;
            if (extraByteLength > 0) {
                for (int i = offset + length - 1; extraByteLength > 0; i--) {
                    if (srcs[i].remaining() > extraByteLength) {
                        extraByteLength -= srcs[i].remaining();
                    } else {
                        length = i - offset + 1;
                        originalLimit = srcs[i].limit();
                        srcs[i].limit(originalLimit - extraByteLength);
                        extraByteLength = 0;
                    }
                }
            }
            try {
                return super.write(srcs, offset, length);
            } finally {
                if (originalLimit != -1) {
                    srcs[length + offset - 1].limit(originalLimit);
                }
            }
        }

        @Override
        public long write(ByteBuffer[] srcs) throws IOException {
            if (writeException != null) {
                try {
                    throw writeException;
                } finally {
                    writeException = null;
                }
            }
            if (runtimeWriteException != null) {
                try {
                    throw runtimeWriteException;
                } finally {
                    runtimeWriteException = null;
                }
            }
            if (writeLimit == -1) {
                return super.write(srcs);
            }
            int extraByteLength = (int) Buffers.remaining(srcs) - writeLimit;
            int originalLimit = -1;
            int length = srcs.length;
            if (extraByteLength > 0) {
                for (int i = srcs.length - 1; extraByteLength > 0; i--) {
                    if (srcs[i].remaining() > extraByteLength) {
                        extraByteLength -= srcs[i].remaining();
                    } else {
                        length = i + 1;
                        originalLimit = srcs[i].limit();
                        srcs[i].limit(originalLimit - extraByteLength);
                        extraByteLength = 0;
                    }
                }
            }
            try {
                return super.write(srcs, 0, length);
            } finally {
                if (originalLimit != -1) {
                    srcs[length - 1].limit(originalLimit);
                }
            }
        }

        public void limitWrite(int limit) {
            writeLimit = limit;
        }

        public void throwExceptionOnRead(IOException exception) {
            readException = exception;
        }

        @Override
        public synchronized int read(ByteBuffer dst) throws IOException {
            if (readException != null) {
                try {
                    throw readException;
                } finally {
                    readException = null;
                }
            }
            return super.read(dst);
        }

        @Override
        public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
            if (readException != null) {
                try {
                    throw readException;
                } finally {
                    readException = null;
                }
            }
            return super.read(dsts, offset, length);
        }

        @Override
        public long read(ByteBuffer[] dsts) throws IOException {
            if (readException != null) {
                try {
                    throw readException;
                } finally {
                    readException = null;
                }
            }
            return super.read(dsts);
        }
    }

    private static class TestExceptionHandler<C extends Channel> implements ChannelExceptionHandler<C> {
        private boolean invoked = false;
        private C channel;
        private IOException exception;

        @Override
        public void handleException(C c, IOException e) {
            invoked = true;
            channel = c;
            exception = e;
        }

        public boolean isInvoked() {
            return invoked;
        }

        public C getFailingChannel() {
            return channel;
        }

        public IOException getFailure() {
            return exception;
        }

        public void clear() {
            invoked = false;
            channel = null;
            exception = null;
        }
    }

    private static class FileChannelWrapper extends FileChannel {
        private final FileChannel delegate;
        private int transferLimit = -1;

        public FileChannelWrapper(FileChannel d) {
            delegate = d;
        }

        public void limitTransfer(int limit) {
            transferLimit = limit;
        }

        @Override
        public int read(ByteBuffer dst) throws IOException {
            return delegate.read(dst);
        }

        @Override
        public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
            return delegate.read(dsts, offset, length);
        }

        @Override
        public int write(ByteBuffer src) throws IOException {
            return delegate.write(src);
        }

        @Override
        public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
            return delegate.write(srcs, offset, length);
        }

        @Override
        public long position() throws IOException {
            return delegate.position();
        }

        @Override
        public FileChannel position(long newPosition) throws IOException {
            return delegate.position(newPosition);
        }

        @Override
        public long size() throws IOException {
            return delegate.size();
        }

        @Override
        public FileChannel truncate(long size) throws IOException {
            return delegate.truncate(size);
        }

        @Override
        public void force(boolean metaData) throws IOException {
            delegate.force(metaData);
        }

        @Override
        public long transferTo(long position, long count, WritableByteChannel target) throws IOException {
            if (transferLimit != -1 && count > transferLimit) {
                count = transferLimit;
            }
            return delegate.transferTo(position, count, target);
        }

        @Override
        public long transferFrom(ReadableByteChannel src, long position, long count) throws IOException {
            if (transferLimit != -1 && count > transferLimit) {
                count = transferLimit;
            }
            return delegate.transferFrom(src, position, count);
        }

        @Override
        public int read(ByteBuffer dst, long position) throws IOException {
            return delegate.read(dst, position);
        }

        @Override
        public int write(ByteBuffer src, long position) throws IOException {
            return delegate.write(src, position);
        }

        @Override
        public MappedByteBuffer map(MapMode mode, long position, long size) throws IOException {
            return delegate.map(mode, position, size);
        }

        @Override
        public FileLock lock(long position, long size, boolean shared) throws IOException {
            return delegate.lock(position, size, shared);
        }

        @Override
        public FileLock tryLock(long position, long size, boolean shared) throws IOException {
            return delegate.tryLock(position, size, shared);
        }

        @Override
        protected void implCloseChannel() throws IOException {}
    }
}
