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
package org.xnio.channels;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.xnio.ChannelListener;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.mock.ConnectedStreamChannelMock;

/**
 * Test for {@link SocketAddressBuffer}.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 *
 */
public class TranslatingSuspendableChannelTestCase {

    private ConnectedStreamChannelMock innerChannel;
    private DummyTranslatingSuspendableChannel channel;

    @Before
    public void createChannel() {
        innerChannel = new ConnectedStreamChannelMock();
        channel = new DummyTranslatingSuspendableChannel(innerChannel);
        assertSame(innerChannel, channel.getChannel());
        assertSame(innerChannel.getWorker(), channel.getWorker());
        assertNotNull(channel.toString());
    }

    @Test
    public void simpleRead() {
        DummyChannelListener readListener = new DummyChannelListener();
        channel.getReadSetter().set(readListener);
        channel.resumeReads();
        invokeReadListener();
        assertTrue(readListener.isInvoked());
    }

    @Test
    public void readWithNullListener() {
        channel.resumeReads();
        assertTrue(channel.isReadResumed());

        invokeReadListener();
        assertFalse(channel.isReadResumed());
    }

    @Test
    public void readWithReadNotRequested() {
        final DummyChannelListener readListener = new DummyChannelListener();
        channel.getReadSetter().set(readListener);
        channel.resumeReads();
        assertTrue(innerChannel.isReadResumed());
        invokeReadListener();
        assertTrue(readListener.isInvoked());
    }

    @Test
    public void readWithWriteRequiresRead() {
        channel.setWriteRequiresRead();
        channel.resumeReads();
        final DummyChannelListener readListener = new DummyChannelListener();
        channel.getReadSetter().set(readListener);
        invokeReadListener();
        assertTrue(readListener.isInvoked());
    }

    @Test
    public void readWithWriteRequiresReadWithWaiter() throws InterruptedException {
        channel.setWriteRequiresRead();
        channel.resumeReads();
        final DummyChannelListener readListener = new DummyChannelListener();
        channel.getReadSetter().set(readListener);
        // write waiter
        final WriteWaiter writeWaiter = new WriteWaiter(channel);
        Thread writeWaiterThread = new Thread(writeWaiter);
        writeWaiterThread.start();
        writeWaiterThread.join(100);
        while (!writeWaiterThread.isAlive()) {
            Thread.sleep(100);
            writeWaiterThread = new Thread(writeWaiter);
            writeWaiterThread.start();
            writeWaiterThread.join(100);
        }

        invokeReadListener();
        assertTrue(readListener.isInvoked());
        writeWaiterThread.join();
    }

    @Test
    public void readWithWriteRequiresReadWithTimeoutWaiter() throws InterruptedException {
        channel.setWriteRequiresRead();
        channel.resumeReads();
        final DummyChannelListener readListener = new DummyChannelListener();
        channel.getReadSetter().set(readListener);
        // write waiter
        final WriteWaiter writeWaiter = new WriteWaiter(channel, 10, TimeUnit.MICROSECONDS);
        final Thread writeWaiterThread1 = new Thread(writeWaiter);
        writeWaiterThread1.start();
        writeWaiterThread1.join();

        invokeReadListener();
        assertTrue(readListener.isInvoked());

        channel.setWriteReady();

        // if we try again to wait on write, we can't, as write is already awaken and is not executed yet
        final Thread readWaiterThread2 = new Thread(writeWaiter);
        readWaiterThread2.start();
        readWaiterThread2.join();
    }

    @Test
    public void readWithWriteRequiresReadWithWaiters() throws InterruptedException {
        channel.setWriteRequiresRead();
        channel.resumeReads();
        final DummyChannelListener readListener = new DummyChannelListener();
        channel.getReadSetter().set(readListener);
        // write waiter
        final WriteWaiter writeWaiter1 = new WriteWaiter(channel);
        final WriteWaiter writeWaiter2 = new WriteWaiter(channel, 10, TimeUnit.MICROSECONDS);
        Thread writeWaiterThread1 = new Thread(writeWaiter1);
        final Thread writeWaiterThread2 = new Thread(writeWaiter2);
        writeWaiterThread1.start();
        writeWaiterThread2.start();
        writeWaiterThread1.join(100);
        writeWaiterThread2.join(100);
        assertFalse(writeWaiterThread2.isAlive());
        while (!writeWaiterThread1.isAlive()) {
            Thread.sleep(100);
            writeWaiterThread1 = new Thread(writeWaiter1);
            writeWaiterThread1.start();
            writeWaiterThread1.join(100);
        }

        invokeReadListener();
        assertTrue(readListener.isInvoked());
        writeWaiterThread1.join();
    }

    @Test
    public void readWithWriteRequiresReadAndWriteRequestedSingleWaiter() throws Exception {
        channel.setWriteRequiresRead();
        channel.resumeReads();
        channel.resumeWrites();
        final WriteWaiter writeWaiter = new WriteWaiter(channel);
        Thread writeWaiterThread = new Thread(writeWaiter);
        writeWaiterThread.start();
        writeWaiterThread.join(100);
        while (!writeWaiterThread.isAlive()) {
            Thread.sleep(100);
            writeWaiterThread = new Thread(writeWaiter);
            writeWaiterThread.start();
            writeWaiterThread.join(100);
        }

        final DummyChannelListener readListener = new DummyChannelListener();
        channel.getReadSetter().set(readListener);
        invokeReadListener();
        writeWaiterThread.join();
        assertTrue(readListener.isInvoked());
        assertTrue(innerChannel.isWriteAwaken());
    }

    @Test
    public void readWithWriteRequiresReadAndWriteRequestedSingleTimeoutWaiter() throws Exception {
        channel.setWriteRequiresRead();
        channel.resumeReads();
        channel.resumeWrites();
        final WriteWaiter writeWaiter = new WriteWaiter(channel, 50, TimeUnit.MILLISECONDS);
        Thread writeWaiterThread = new Thread(writeWaiter);
        writeWaiterThread.start();
        writeWaiterThread.join();

        final DummyChannelListener readListener = new DummyChannelListener();
        channel.getReadSetter().set(readListener);
        invokeReadListener();
        assertTrue(readListener.isInvoked());
        assertTrue(innerChannel.isWriteAwaken());
    }

    @Test
    public void readWithWriteRequiresReadAndWriteRequested() throws Exception {
        channel.setWriteRequiresRead();
        channel.resumeReads();
        channel.resumeWrites();
        final WriteWaiter writeWaiter1 = new WriteWaiter(channel);
        final WriteWaiter writeWaiter2 = new WriteWaiter(channel, 50, TimeUnit.MILLISECONDS);
        Thread writeWaiterThread1 = new Thread(writeWaiter1);
        Thread writeWaiterThread2 = new Thread(writeWaiter1);
        final Thread writeWaiterThread3 = new Thread(writeWaiter2);
        writeWaiterThread1.start();
        writeWaiterThread2.start();
        writeWaiterThread1.join(100);
        writeWaiterThread2.join(100);
        writeWaiterThread3.join(100);
        assertFalse(writeWaiterThread3.isAlive());
        while (!writeWaiterThread1.isAlive() || !writeWaiterThread2.isAlive()) {
            Thread.sleep(100);
            writeWaiterThread1 = new Thread(writeWaiter1);
            writeWaiterThread2 = new Thread(writeWaiter1);
            writeWaiterThread1.start();
            writeWaiterThread2.start();
            writeWaiterThread1.join(100);
            writeWaiterThread2.join(100);
        }

        assertTrue(writeWaiterThread1.isAlive());
        assertTrue(writeWaiterThread2.isAlive());

        final DummyChannelListener readListener = new DummyChannelListener();
        channel.getReadSetter().set(readListener);
        invokeReadListener();
        writeWaiterThread1.join();
        writeWaiterThread2.join();
        assertTrue(readListener.isInvoked());
        assertTrue(innerChannel.isWriteAwaken());
    }

    @Test
    public void readWithReadRequiresWrite() {
        channel.setReadRequiresWrite();
        final DummyChannelListener readListener = new DummyChannelListener();
        channel.getReadSetter().set(readListener);
        invokeReadListener();
        assertFalse(readListener.isInvoked());
    }

    @Test
    public void readWithReadRequiresWriteAfterReadSingleWaiter() throws InterruptedException {
        final DummyChannelListener readListener = new DummyChannelListener(Action.READ_REQUIRES_WRITE, Action.READ_READY);
        channel.getReadSetter().set(readListener);
        channel.resumeReads();
        assertFalse(channel.readRequiresWrite());
        // create read waiter
        final ReadWaiter readWaiter = new ReadWaiter(channel);
        Thread readWaiterThread = new Thread(readWaiter);
        readWaiterThread.start();
        readWaiterThread.join(100);
        while (!readWaiterThread.isAlive()) {
            Thread.sleep(100);
            readWaiterThread = new Thread(readWaiter);
            readWaiterThread.start();
            readWaiterThread.join(100);
        }

        // invoke read listener
        invokeReadListener();
        assertTrue(readListener.isInvoked());
        assertTrue(channel.readRequiresWrite());
        assertTrue(innerChannel.isReadAwaken());
    }

    @Test
    public void readWithReadRequiresWriteAfterReadSingleTimeoutWaiter() throws InterruptedException {
        final DummyChannelListener readListener = new DummyChannelListener(Action.READ_REQUIRES_WRITE, Action.READ_READY);
        channel.getReadSetter().set(readListener);
        channel.resumeReads();
        assertFalse(channel.readRequiresWrite());
        // create read waiter
        final ReadWaiter readWaiter = new ReadWaiter(channel, 30, TimeUnit.MILLISECONDS);
        final Thread readWaiterThread = new Thread(readWaiter);
        readWaiterThread.start();
        readWaiterThread.join();

        // invoke read listener
        invokeReadListener();
        assertTrue(readListener.isInvoked());
        assertTrue(channel.readRequiresWrite());
        assertTrue(innerChannel.isReadAwaken());
    }

    @Test
    public void readWithReadRequiresWriteAfterRead() throws InterruptedException {
        final DummyChannelListener readListener = new DummyChannelListener(Action.READ_REQUIRES_WRITE, Action.READ_READY);
        channel.getReadSetter().set(readListener);
        channel.resumeReads();
        assertFalse(channel.readRequiresWrite());
        // create read waiter
        final ReadWaiter readWaiter1 = new ReadWaiter(channel);
        final ReadWaiter readWaiter2 = new ReadWaiter(channel, 30, TimeUnit.MILLISECONDS);
        Thread readWaiterThread1 = new Thread(readWaiter1);
        final Thread readWaiterThread2 = new Thread(readWaiter2);
        readWaiterThread1.start();
        readWaiterThread2.start();
        readWaiterThread1.join(100);
        readWaiterThread2.join(100);
        assertFalse(readWaiterThread2.isAlive());
        while (!readWaiterThread1.isAlive()) {
            Thread.sleep(100);
            readWaiterThread1 = new Thread(readWaiter1);
            readWaiterThread1.start();
            readWaiterThread1.join(100);
        }

        // invoke read listener
        invokeReadListener();
        assertTrue(readListener.isInvoked());
        assertTrue(channel.readRequiresWrite());
        assertTrue(innerChannel.isReadAwaken());
        readWaiterThread1.join();
    }
    
    @Test
    public void readWithWriteRequiresReadAfterReadSingleWaiter() throws InterruptedException {
        final DummyChannelListener readListener = new DummyChannelListener(Action.WRITE_REQUIRES_READ, Action.READ_READY);
        channel.getReadSetter().set(readListener);
        channel.resumeReads();
        channel.setWriteRequiresRead();
        //assertFalse(channel.writeRequiresRead());
        // create write waiter
        final WriteWaiter writeWaiter = new WriteWaiter(channel);
        Thread writeWaiterThread1 = new Thread(writeWaiter);
        writeWaiterThread1.start();
        writeWaiterThread1.join(100);
        while (!writeWaiterThread1.isAlive()) {
            Thread.sleep(100);
            writeWaiterThread1 = new Thread(writeWaiter);
            writeWaiterThread1.start();
            writeWaiterThread1.join(100);
        }

        // invoke read listener
        Thread.sleep(100);
        invokeReadListener();
        assertTrue(readListener.isInvoked());
        assertFalse(channel.writeRequiresRead());
        assertTrue(innerChannel.isWriteAwaken());
        writeWaiterThread1.join();

        // if we try again to wait on write, we can't, as write is already awaken and is not executed yet
        final Thread writeWaiterThread2 = new Thread(writeWaiter);
        writeWaiterThread2.start();
        writeWaiterThread2.join(100);
        assertTrue(writeWaiterThread2.isAlive());

        channel.setWriteReady();
        writeWaiterThread2.join();
    }

    @Test
    public void readWithWriteRequiresReadAfterReadSingleTimeoutWaiter() throws InterruptedException {
        final DummyChannelListener readListener = new DummyChannelListener(Action.WRITE_REQUIRES_READ, Action.READ_READY);
        channel.getReadSetter().set(readListener);
        channel.resumeReads();
        channel.setWriteRequiresRead();
        //assertFalse(channel.writeRequiresRead());
        // create write waiter
        final WriteWaiter writeWaiter = new WriteWaiter(channel, 50, TimeUnit.MILLISECONDS);
        final Thread writeWaiterThread1 = new Thread(writeWaiter);
        writeWaiterThread1.start();
        writeWaiterThread1.join();

        // invoke read listener
        invokeReadListener();
        assertTrue(readListener.isInvoked());
        assertFalse(channel.writeRequiresRead());
        assertTrue(innerChannel.isWriteAwaken());

        // if we try again to wait on write, we can't, as write is already awaken and is not executed yet
        final Thread writeWaiterThread2 = new Thread(writeWaiter);
        writeWaiterThread2.start();
        writeWaiterThread2.join();
    }

    @Test
    public void readWithWriteRequiresReadAfterRead() throws InterruptedException {
        final DummyChannelListener readListener = new DummyChannelListener(Action.WRITE_REQUIRES_READ, Action.READ_READY);
        channel.getReadSetter().set(readListener);
        channel.resumeReads();
        channel.setWriteRequiresRead();
        //assertFalse(channel.writeRequiresRead());
        // create write waiter
        final WriteWaiter writeWaiter1 = new WriteWaiter(channel);
        final WriteWaiter writeWaiter2 = new WriteWaiter(channel, 50, TimeUnit.MILLISECONDS);
        Thread writeWaiterThread1 = new Thread(writeWaiter1);
        final Thread writeWaiterThread2 = new Thread(writeWaiter2);
        writeWaiterThread1.start();
        writeWaiterThread2.start();
        writeWaiterThread1.join(100);
        writeWaiterThread2.join(200);
        assertFalse(writeWaiterThread2.isAlive());
        while (!writeWaiterThread1.isAlive()) {
            Thread.sleep(100);
            writeWaiterThread1 = new Thread(writeWaiter1);
            writeWaiterThread1.start();
            writeWaiterThread1.join(100);
        }

        // invoke read listener
        invokeReadListener();
        assertTrue(readListener.isInvoked());
        assertFalse(channel.writeRequiresRead());
        assertTrue(innerChannel.isWriteAwaken());
        writeWaiterThread1.join();

        // if we try again to wait on write, we can't, as write is already awaken and is not executed yet
        final Thread writeWaiterThread3 = new Thread(writeWaiter1);
        final Thread writeWaiterThread4 = new Thread(writeWaiter2);
        writeWaiterThread3.start();
        writeWaiterThread4.start();
        writeWaiterThread3.join();
        writeWaiterThread4.join();
    }

    @Test
    public void cantWaitReadWhenReadReady() throws IOException {
        channel.setReadReady();
        channel.awaitReadable();
        channel.awaitReadable(10, TimeUnit.MINUTES);
        // setReadReady is idempotent
        channel.setReadReady();
        channel.awaitReadable();
        channel.awaitReadable(10, TimeUnit.HOURS);
    }

    @Test
    public void simpleWrite() {
        final DummyChannelListener writeListener = new DummyChannelListener();
        channel.getWriteSetter().set(writeListener);
        channel.resumeWrites();
        invokeWriteListener();
        assertTrue(writeListener.isInvoked());
    }

    @Test
    public void writeWithNullListener() {
        channel.resumeWrites();
        assertTrue(channel.isWriteResumed());

        invokeWriteListener();
        assertFalse(channel.isWriteResumed());
    }

    @Test
    public void writeWithWriteNotRequested() {
        final DummyChannelListener writeListener = new DummyChannelListener();
        channel.getWriteSetter().set(writeListener);
        channel.resumeWrites();
        assertTrue(innerChannel.isWriteResumed());
        invokeWriteListener();
        assertTrue(writeListener.isInvoked());
    }

    @Test
    public void writeWithReadRequiresWrite() {
        channel.setReadRequiresWrite();
        channel.resumeWrites();
        final DummyChannelListener writeListener = new DummyChannelListener();
        channel.getWriteSetter().set(writeListener);
        invokeWriteListener();
        assertTrue(writeListener.isInvoked());
    }
    
    @Test
    public void writeWithReadRequiresWriteWithWaiter() throws InterruptedException {
        channel.setReadRequiresWrite();
        channel.resumeWrites();
        final DummyChannelListener writeListener = new DummyChannelListener();
        channel.getWriteSetter().set(writeListener);
        // read waiter
        final ReadWaiter readWaiter = new ReadWaiter(channel);
        Thread readWaiterThread1 = new Thread(readWaiter);
        readWaiterThread1.start();
        readWaiterThread1.join(100);
        while (!readWaiterThread1.isAlive()) {
            Thread.sleep(100);
            readWaiterThread1 = new Thread(readWaiter);
            readWaiterThread1.start();
            readWaiterThread1.join(100);
        }

        invokeWriteListener();
        assertTrue(writeListener.isInvoked());
        readWaiterThread1.join();

        // if we try again to wait on read, we can't, as read is already awaken and is not executed yet
        final Thread readWaiterThread2 = new Thread(readWaiter);
        readWaiterThread2.start();
        readWaiterThread2.join(100);
        assertTrue(readWaiterThread2.isAlive());

        channel.setReadReady();
        readWaiterThread2.join();
    }

    @Test
    public void writeWithReadRequiresWriteWithTimeoutWaiter() throws InterruptedException {
        channel.setReadRequiresWrite();
        channel.resumeWrites();
        final DummyChannelListener writeListener = new DummyChannelListener();
        channel.getWriteSetter().set(writeListener);
        // read waiter
        final ReadWaiter readWaiter = new ReadWaiter(channel, 30, TimeUnit.MILLISECONDS);
        Thread readWaiterThread1 = new Thread(readWaiter);
        readWaiterThread1.start();
        readWaiterThread1.join();

        invokeWriteListener();
        assertTrue(writeListener.isInvoked());

        channel.setReadReady();

        // if we try again to wait on read, we can't, as read is already awaken and is not executed yet
        final Thread readWaiterThread2 = new Thread(readWaiter);
        readWaiterThread2.start();
        readWaiterThread2.join();
    }

    @Test
    public void writeWithReadRequiresWriteWithWaiters() throws InterruptedException {
        channel.setReadRequiresWrite();
        channel.resumeWrites();
        final DummyChannelListener writeListener = new DummyChannelListener();
        channel.getWriteSetter().set(writeListener);
        // read waiter
        final ReadWaiter readWaiter1 = new ReadWaiter(channel);
        final ReadWaiter readWaiter2 = new ReadWaiter(channel, 30, TimeUnit.MILLISECONDS);
        Thread readWaiterThread1 = new Thread(readWaiter1);
        Thread readWaiterThread2 = new Thread(readWaiter1);
        Thread readWaiterThread3 = new Thread(readWaiter1);
        final Thread readWaiterThread4 = new Thread(readWaiter2);
        final Thread readWaiterThread5 = new Thread(readWaiter2);
        readWaiterThread1.start();
        readWaiterThread2.start();
        readWaiterThread3.start();
        readWaiterThread4.start();
        readWaiterThread5.start();
        readWaiterThread1.join(100);
        readWaiterThread2.join(100);
        readWaiterThread3.join(100);
        readWaiterThread4.join(100);
        readWaiterThread5.join(100);
        assertFalse(readWaiterThread4.isAlive());
        assertFalse(readWaiterThread5.isAlive());
        while (!readWaiterThread1.isAlive() || !readWaiterThread2.isAlive() || !readWaiterThread3.isAlive()) {
            Thread.sleep(100);
            readWaiterThread1 = new Thread(readWaiter1);
            readWaiterThread2 = new Thread(readWaiter1);
            readWaiterThread3 = new Thread(readWaiter1);
            readWaiterThread1.start();
            readWaiterThread2.start();
            readWaiterThread3.start();
            readWaiterThread1.join(100);
            readWaiterThread2.join(100);
            readWaiterThread3.join(100);
        }

        invokeWriteListener();
        assertTrue(writeListener.isInvoked());
        readWaiterThread1.join();
        readWaiterThread2.join();
        readWaiterThread3.join();

        // if we try again to wait on read, we can't, as read is already awaken and is not executed yet
        final Thread readWaiterThread6 = new Thread(readWaiter1);
        final Thread readWaiterThread7 = new Thread(readWaiter1);
        final Thread readWaiterThread8 = new Thread(readWaiter2);
        readWaiterThread6.start();
        readWaiterThread7.start();
        readWaiterThread8.start();
        readWaiterThread6.join();
        readWaiterThread7.join();
        readWaiterThread8.join();
    }

    @Test
    public void writeWithReadRequiresWriteAndReadRequested() throws Exception {
        channel.setReadRequiresWrite();
        channel.resumeReads();
        channel.resumeWrites();
        final ReadWaiter readWaiter = new ReadWaiter(channel);
        Thread readWaiterThread = new Thread(readWaiter);
        readWaiterThread.start();
        readWaiterThread.join(100);
        while (!readWaiterThread.isAlive()) {
            Thread.sleep(100);
            readWaiterThread = new Thread(readWaiter);
            readWaiterThread.start();
            readWaiterThread.join(100);
        }
        assertTrue(readWaiterThread.isAlive());

        final DummyChannelListener writeListener = new DummyChannelListener();
        channel.getWriteSetter().set(writeListener);
        invokeWriteListener();
        readWaiterThread.join();
        assertTrue(writeListener.isInvoked());
        assertTrue(innerChannel.isReadAwaken());
    }

    @Test
    public void writeWithWriteRequiresRead() {
        channel.setWriteRequiresRead();
        final DummyChannelListener writeListener = new DummyChannelListener();
        channel.getWriteSetter().set(writeListener);
        invokeWriteListener();
        assertFalse(writeListener.isInvoked());
    }

    @Test
    public void writeWithWriteRequiresReadAfterWriteSingleWaiter() throws InterruptedException {
        final DummyChannelListener writeListener = new DummyChannelListener(Action.WRITE_REQUIRES_READ, Action.WRITE_READY);
        channel.getWriteSetter().set(writeListener);
        channel.resumeWrites();
        assertFalse(channel.writeRequiresRead());
        // create write waiter
        final WriteWaiter writeWaiter = new WriteWaiter(channel);
        Thread writeWaiterThread = new Thread(writeWaiter);
        writeWaiterThread.start();
        writeWaiterThread.join(100);
        while (!writeWaiterThread.isAlive()) {
            Thread.sleep(100);
            writeWaiterThread = new Thread(writeWaiter);
            writeWaiterThread.start();
            writeWaiterThread.join(100);
        }

        // invoke write listener
        invokeWriteListener();
        assertTrue(writeListener.isInvoked());
        assertTrue(channel.writeRequiresRead());
        assertTrue(innerChannel.isWriteAwaken());
        writeWaiterThread.join();
    }

    @Test
    public void writeWithWriteRequiresReadAfterWriteSingleTimeoutWaiter() throws InterruptedException {
        final DummyChannelListener writeListener = new DummyChannelListener(Action.WRITE_REQUIRES_READ, Action.WRITE_READY);
        channel.getWriteSetter().set(writeListener);
        channel.resumeWrites();
        assertFalse(channel.writeRequiresRead());
        // create write waiter
        final WriteWaiter writeWaiter = new WriteWaiter(channel, 50, TimeUnit.MILLISECONDS);
        final Thread writeWaiterThread = new Thread(writeWaiter);
        writeWaiterThread.start();
        writeWaiterThread.join();

        // invoke write listener
        invokeWriteListener();
        assertTrue(writeListener.isInvoked());
        assertTrue(channel.writeRequiresRead());
        assertTrue(innerChannel.isWriteAwaken());
    }

    @Test
    public void writeWithWriteRequiresReadAfterWrite() throws InterruptedException {
        final DummyChannelListener writeListener = new DummyChannelListener(Action.WRITE_REQUIRES_READ, Action.WRITE_READY);
        channel.getWriteSetter().set(writeListener);
        channel.resumeWrites();
        assertFalse(channel.writeRequiresRead());
        // create write waiter
        final WriteWaiter writeWaiter1 = new WriteWaiter(channel);
        final WriteWaiter writeWaiter2 = new WriteWaiter(channel, 50, TimeUnit.MILLISECONDS);
        Thread writeWaiterThread1 = new Thread(writeWaiter1);
        final Thread writeWaiterThread2 = new Thread(writeWaiter2);
        writeWaiterThread1.start();
        writeWaiterThread2.start();
        writeWaiterThread1.join(100);
        writeWaiterThread2.join(200);
        assertFalse(writeWaiterThread2.isAlive());
        while (!writeWaiterThread1.isAlive()) {
            Thread.sleep(100);
            writeWaiterThread1 = new Thread(writeWaiter1);
            writeWaiterThread1.start();
            writeWaiterThread1.join(100);
        }

        // invoke write listener
        invokeWriteListener();
        assertTrue(writeListener.isInvoked());
        assertTrue(channel.writeRequiresRead());
        assertTrue(innerChannel.isWriteAwaken());
        writeWaiterThread1.join();
    }

    @Test
    public void writeWithReadRequiresWriteAfterWriteSingleWaiter() throws InterruptedException {
        final DummyChannelListener writeListener = new DummyChannelListener(Action.READ_REQUIRES_WRITE, Action.WRITE_READY);
        channel.getWriteSetter().set(writeListener);
        channel.resumeWrites();
        assertFalse(channel.readRequiresWrite());
        // create read waiter
        final ReadWaiter readWaiter = new ReadWaiter(channel);
        Thread readWaiterThread = new Thread(readWaiter);
        readWaiterThread.start();
        readWaiterThread.join(100);
        while (!readWaiterThread.isAlive()) {
            Thread.sleep(100);
            readWaiterThread = new Thread(readWaiter);
            readWaiterThread.start();
            readWaiterThread.join(100);
        }

        // invoke write listener
        invokeWriteListener();
        assertTrue(writeListener.isInvoked());
        assertFalse(channel.readRequiresWrite());
        assertTrue(innerChannel.isReadAwaken());
    }

    @Test
    public void writeWithReadRequiresWriteAfterWriteSingleTimeoutWaiter() throws InterruptedException {
        final DummyChannelListener writeListener = new DummyChannelListener(Action.READ_REQUIRES_WRITE, Action.WRITE_READY);
        channel.getWriteSetter().set(writeListener);
        channel.resumeWrites();
        assertFalse(channel.readRequiresWrite());
        // create read waiter
        final ReadWaiter readWaiter = new ReadWaiter(channel, 30, TimeUnit.MILLISECONDS);
        final Thread readWaiterThread = new Thread(readWaiter);
        readWaiterThread.start();
        readWaiterThread.join();

        // invoke write listener
        invokeWriteListener();
        assertTrue(writeListener.isInvoked());
        assertFalse(channel.readRequiresWrite());
        assertTrue(innerChannel.isReadAwaken());
    }

    @Test
    public void writeWithReadRequiresWriteAfterWrite() throws InterruptedException {
        final DummyChannelListener writeListener = new DummyChannelListener(Action.READ_REQUIRES_WRITE, Action.WRITE_READY);
        channel.getWriteSetter().set(writeListener);
        channel.resumeWrites();
        assertFalse(channel.readRequiresWrite());
        // create read waiter
        final ReadWaiter readWaiter1 = new ReadWaiter(channel);
        final ReadWaiter readWaiter2 = new ReadWaiter(channel, 30, TimeUnit.MILLISECONDS);
        Thread readWaiterThread1 = new Thread(readWaiter1);
        final Thread readWaiterThread2 = new Thread(readWaiter2);
        readWaiterThread1.start();
        readWaiterThread2.start();
        readWaiterThread1.join(100);
        readWaiterThread2.join(100);
        assertFalse(readWaiterThread2.isAlive());
        while (!readWaiterThread1.isAlive()) {
            Thread.sleep(100);
            readWaiterThread1 = new Thread(readWaiter1);
            readWaiterThread1.start();
            readWaiterThread1.join(100);
        }

        // invoke write listener
        invokeWriteListener();
        assertTrue(writeListener.isInvoked());
        assertFalse(channel.readRequiresWrite());
        assertTrue(innerChannel.isReadAwaken());
        readWaiterThread1.join();
    }

    @Test
    public void cantWaitWriteWhenWriteReady() throws IOException {
        channel.setWriteReady();
        channel.awaitWritable();
        channel.awaitWritable(1, TimeUnit.HOURS);
        // set write rady is idempotent
        channel.setWriteReady();
        channel.awaitWritable();
        channel.awaitWritable(1, TimeUnit.DAYS);
    }

    @Test
    public void wakeupReads() throws IOException{
        channel.wakeupReads();
        assertTrue(channel.isReadResumed());
        assertTrue(innerChannel.isReadResumed());
        assertTrue(innerChannel.isReadAwaken());
    }

    @Test
    public void wakeupWrites() throws IOException{
        channel.wakeupWrites();
        assertTrue(channel.isWriteResumed());
        assertTrue(innerChannel.isWriteResumed());
        assertTrue(innerChannel.isWriteAwaken());
    }

    @Test
    public void readRequiresWrite() {
        channel.resumeReads();
        assertTrue(channel.isReadResumed());
        assertTrue(innerChannel.isReadResumed());
        assertFalse(innerChannel.isReadAwaken());
        assertFalse(channel.isWriteResumed());
        assertFalse(innerChannel.isWriteResumed());

        channel.setReadRequiresWrite();
        assertTrue(channel.isReadResumed());
        assertTrue(innerChannel.isReadResumed());
        assertFalse(innerChannel.isReadAwaken());
        assertFalse(channel.isWriteResumed());
        assertTrue(innerChannel.isWriteResumed());

        channel.clearReadRequiresWrite();
        assertTrue(channel.isReadResumed());
        assertTrue(innerChannel.isReadResumed());
        assertFalse(innerChannel.isReadAwaken());
        assertFalse(channel.isWriteResumed());
        assertTrue(innerChannel.isWriteResumed());

        invokeWriteListener();
        assertTrue(channel.isReadResumed());
        assertTrue(innerChannel.isReadResumed());
        assertFalse(innerChannel.isReadAwaken());
        assertFalse(channel.isWriteResumed());
        assertFalse(innerChannel.isWriteResumed());

        channel.setReadReady();
        channel.setReadRequiresWrite();
        assertTrue(channel.isReadResumed());
        assertTrue(innerChannel.isReadResumed());
        assertFalse(channel.isWriteResumed());
        assertFalse(channel.isWriteResumed());
        assertFalse(innerChannel.isWriteResumed());

        channel.clearReadRequiresWrite();
        assertTrue(channel.isReadResumed());
        assertTrue(innerChannel.isReadResumed());
        assertTrue(innerChannel.isReadAwaken());
        assertFalse(channel.isWriteResumed());
        assertFalse(innerChannel.isWriteResumed());

        channel.clearReadRequiresWrite();
        assertTrue(channel.isReadResumed());
        assertTrue(innerChannel.isReadResumed());
        assertTrue(innerChannel.isReadAwaken());
        assertFalse(channel.isWriteResumed());
        assertFalse(innerChannel.isWriteResumed());
    }

    @Test
    public void writeRequiresRead() {
        channel.resumeWrites();
        assertTrue(channel.isWriteResumed());
        assertTrue(innerChannel.isWriteResumed());
        assertFalse(innerChannel.isWriteAwaken());
        assertFalse(channel.isReadResumed());
        assertFalse(innerChannel.isReadResumed());

        channel.setWriteRequiresRead();
        assertTrue(channel.isWriteResumed());
        assertTrue(innerChannel.isWriteResumed());
        assertFalse(channel.isReadResumed());
        assertFalse(innerChannel.isWriteAwaken());
        assertTrue(innerChannel.isReadResumed());

        channel.clearWriteRequiresRead();
        assertTrue(channel.isWriteResumed());
        assertTrue(innerChannel.isWriteResumed());
        assertFalse(innerChannel.isWriteAwaken());
        assertFalse(channel.isReadResumed());
        assertTrue(innerChannel.isReadResumed());

        invokeReadListener();
        assertTrue(channel.isWriteResumed());
        assertTrue(innerChannel.isWriteResumed());
        assertFalse(innerChannel.isWriteAwaken());
        assertFalse(channel.isReadResumed());
        assertFalse(innerChannel.isReadResumed());

        channel.setWriteReady();
        channel.setWriteRequiresRead();
        assertTrue(channel.isWriteResumed());
        assertTrue(innerChannel.isWriteResumed());
        assertFalse(channel.isReadResumed());
        assertFalse(channel.isReadResumed());
        assertFalse(innerChannel.isReadResumed());

        channel.clearWriteRequiresRead();
        assertTrue(channel.isWriteResumed());
        assertTrue(innerChannel.isWriteResumed());
        assertTrue(innerChannel.isWriteAwaken());
        assertFalse(channel.isReadResumed());
        assertFalse(innerChannel.isReadResumed());

        channel.clearWriteRequiresRead();
        assertTrue(channel.isWriteResumed());
        assertTrue(innerChannel.isWriteResumed());
        assertTrue(innerChannel.isWriteAwaken());
        assertFalse(channel.isReadResumed());
        assertFalse(innerChannel.isReadResumed());
    }

    @Test
    public void readRequiresExt() {
        channel.resumeReads();
        assertTrue(channel.isReadResumed());
        assertTrue(innerChannel.isReadResumed());
        assertFalse(innerChannel.isReadAwaken());
        assertFalse(channel.isWriteResumed());
        assertFalse(innerChannel.isWriteResumed());

        for (int i = 0; i < 31; i++) {
            assertTrue(channel.tryAddReadRequiresExternal());
        }
        assertFalse(channel.tryAddReadRequiresExternal());
        //nothing changes
        assertTrue(channel.isReadResumed());
        assertTrue(innerChannel.isReadResumed());
        assertFalse(innerChannel.isReadAwaken());
        assertFalse(channel.isWriteResumed());
        assertFalse(innerChannel.isWriteResumed());

        // setReadRequiresWrite
        channel.setReadRequiresWrite();
        //nothing changes
        assertTrue(channel.isReadResumed());
        assertTrue(innerChannel.isReadResumed());
        assertFalse(innerChannel.isReadAwaken());
        assertFalse(channel.isWriteResumed());
        assertFalse(innerChannel.isWriteResumed());

        // clearReadRequiresWrite 
        channel.clearReadRequiresWrite();
        //nothing changes
        assertTrue(channel.isReadResumed());
        assertTrue(innerChannel.isReadResumed());
        assertFalse(innerChannel.isReadAwaken());
        assertFalse(channel.isWriteResumed());
        assertFalse(innerChannel.isWriteResumed());

        // suspend and resume reads
        channel.suspendReads();
        channel.resumeReads();
        //nothing changes
        assertTrue(channel.isReadResumed());
        assertTrue(innerChannel.isReadResumed());
        assertFalse(innerChannel.isReadAwaken());
        assertFalse(channel.isWriteResumed());
        assertFalse(innerChannel.isWriteResumed());

        channel.removeReadRequiresExternal();
        //nothing changes
        assertTrue(channel.isReadResumed());
        assertTrue(innerChannel.isReadResumed());
        assertFalse(innerChannel.isReadAwaken());
        assertFalse(channel.isWriteResumed());
        assertFalse(innerChannel.isWriteResumed());

        for (int i = 0; i < 30; i++) {
            channel.removeReadRequiresExternal();
        }
        //nothing changes
        assertTrue(channel.isReadResumed());
        assertTrue(innerChannel.isReadResumed());
        assertFalse(innerChannel.isReadAwaken());
        assertFalse(channel.isWriteResumed());
        assertFalse(innerChannel.isWriteResumed());
    }

    @Test
    public void writeRequiresExt() {
        channel.resumeWrites();
        assertTrue(channel.isWriteResumed());
        assertTrue(innerChannel.isWriteResumed());
        assertFalse(innerChannel.isWriteAwaken());
        assertFalse(channel.isReadResumed());
        assertFalse(innerChannel.isReadResumed());

        for (int i = 0; i < 31; i++) {
            assertTrue(channel.tryAddWriteRequiresExternal());
        }
        assertFalse(channel.tryAddWriteRequiresExternal());
        //nothing changes
        assertTrue(channel.isWriteResumed());
        assertTrue(innerChannel.isWriteResumed());
        assertFalse(innerChannel.isWriteAwaken());
        assertFalse(channel.isReadResumed());
        assertFalse(innerChannel.isReadResumed());

        // setWriteRequiresRead
        channel.setWriteRequiresRead();
        //nothing changes
        assertTrue(channel.isWriteResumed());
        assertTrue(innerChannel.isWriteResumed());
        assertFalse(innerChannel.isWriteAwaken());
        assertFalse(channel.isReadResumed());
        assertFalse(innerChannel.isReadResumed());

        // clearWriteRequiresRead
        channel.clearWriteRequiresRead();
        //nothing changes
        assertTrue(channel.isWriteResumed());
        assertTrue(innerChannel.isWriteResumed());
        assertFalse(innerChannel.isWriteAwaken());
        assertFalse(channel.isReadResumed());
        assertFalse(innerChannel.isReadResumed());

        // suspend and resume writes
        channel.suspendWrites();
        channel.resumeWrites();
        //nothing changes
        assertTrue(channel.isWriteResumed());
        assertTrue(innerChannel.isWriteResumed());
        assertFalse(innerChannel.isWriteAwaken());
        assertFalse(channel.isReadResumed());
        assertFalse(innerChannel.isReadResumed());

        channel.removeWriteRequiresExternal();
        //nothing changes
        assertTrue(channel.isWriteResumed());
        assertTrue(innerChannel.isWriteResumed());
        assertFalse(innerChannel.isWriteAwaken());
        assertFalse(channel.isReadResumed());
        assertFalse(innerChannel.isReadResumed());

        for (int i = 0; i < 30; i++) {
            channel.removeWriteRequiresExternal();
        }
        //nothing changes
        assertTrue(channel.isWriteResumed());
        assertTrue(innerChannel.isWriteResumed());
        assertFalse(innerChannel.isWriteAwaken());
        assertFalse(channel.isReadResumed());
        assertFalse(innerChannel.isReadResumed());
    }

    @Test
    public void getAndSetOption() throws IOException {
        assertFalse(channel.supportsOption(Options.SASL_POLICY_PASS_CREDENTIALS));
        assertFalse(channel.supportsOption(Options.SEND_BUFFER));
        assertFalse(channel.supportsOption(Options.TCP_NODELAY));
        assertNull(channel.getOption(Options.SASL_POLICY_PASS_CREDENTIALS));
        assertNull(channel.getOption(Options.SEND_BUFFER));
        assertNull(channel.getOption(Options.TCP_NODELAY));

        innerChannel.setOptionMap(OptionMap.create(Options.SASL_POLICY_PASS_CREDENTIALS, true, Options.SEND_BUFFER, 50000));
        assertTrue(channel.supportsOption(Options.SASL_POLICY_PASS_CREDENTIALS));
        assertTrue(channel.supportsOption(Options.SEND_BUFFER));
        assertFalse(channel.supportsOption(Options.TCP_NODELAY));
        assertTrue(channel.getOption(Options.SASL_POLICY_PASS_CREDENTIALS));
        Assert.assertEquals(50000, (int) channel.getOption(Options.SEND_BUFFER));
        assertNull(channel.getOption(Options.TCP_NODELAY));

        channel.setOption(Options.TCP_NODELAY, true);
        assertTrue(channel.supportsOption(Options.TCP_NODELAY));
        assertTrue(channel.getOption(Options.TCP_NODELAY));
    }


    @Test
    public void getReadAndWriteThread() {
        assertSame(innerChannel.getReadThread(), channel.getReadThread());
        assertSame(innerChannel.getWriteThread(), channel.getWriteThread());
    }

    @Test
    public void flush() throws IOException {
        final ByteBuffer dummyMessage = ByteBuffer.allocate(5);
        dummyMessage.put("abc".getBytes("UTF-8")).flip();
        innerChannel.write(dummyMessage);
        innerChannel.enableFlush(false);
        assertFalse(channel.flush());
        assertFalse(channel.isWriteComplete());

        innerChannel.enableFlush(true);
        assertTrue(channel.flush());
        assertFalse(channel.isWriteComplete());
    }

    @Test
    public void closeListener() throws IOException {
        final DummyChannelListener listener = new DummyChannelListener();
        final DummyChannelListener closeListener = new DummyChannelListener();
        channel.getReadSetter().set(listener);
        channel.getWriteSetter().set(listener);
        channel.getCloseSetter().set(closeListener);

        assertTrue(channel.isOpen());
        assertFalse(channel.isReadShutDown());
        assertFalse(channel.isWriteComplete());
        assertFalse(channel.isWriteShutDown());
        invokeCloseListener();
        assertTrue(closeListener.isInvoked());
        assertFalse(channel.isOpen());
        assertTrue(channel.isReadShutDown());
        assertTrue(channel.isWriteComplete());
        assertTrue(channel.isWriteShutDown());

        // try to read
        invokeReadListener();
        assertFalse(listener.isInvoked());
        invokeWriteListener();
        assertFalse(listener.isInvoked());

        closeListener.clearInvocation();

        // invoking close listener is idempotent
        invokeCloseListener();
        assertFalse(channel.isOpen());
        assertTrue(channel.isReadShutDown());
        assertTrue(channel.isWriteComplete());
        assertTrue(channel.isWriteShutDown());
        invokeReadListener();
        assertFalse(listener.isInvoked());
        invokeWriteListener();
        assertFalse(listener.isInvoked());
        channel.wakeupReads();
        assertFalse(channel.isReadResumed());
        assertFalse(innerChannel.isReadResumed());
        channel.wakeupWrites();
        assertFalse(channel.isWriteResumed());
        assertFalse(innerChannel.isWriteResumed());
    }

    @Test
    public void close() throws IOException {
        final DummyChannelListener listener = new DummyChannelListener();
        final DummyChannelListener closeListener = new DummyChannelListener();
        channel.getReadSetter().set(listener);
        channel.getWriteSetter().set(listener);
        channel.getCloseSetter().set(closeListener);

        assertTrue(channel.isOpen());
        assertFalse(channel.isReadShutDown());
        assertFalse(channel.isWriteComplete());
        assertFalse(channel.isWriteShutDown());
        channel.close();
        assertTrue(closeListener.isInvoked());
        assertFalse(channel.isOpen());
        assertTrue(channel.isReadShutDown());
        assertTrue(channel.isWriteComplete());
        assertTrue(channel.isWriteShutDown());

        // try to read
        invokeReadListener();
        assertFalse(listener.isInvoked());
        invokeWriteListener();
        assertFalse(listener.isInvoked());

        closeListener.clearInvocation();

        // close is idempotent
        channel.close();
        assertFalse(channel.isOpen());
        assertTrue(channel.isReadShutDown());
        assertTrue(channel.isWriteComplete());
        assertTrue(channel.isWriteShutDown());
        assertFalse(channel.isReadResumed());
        assertFalse(channel.isWriteResumed());
        invokeReadListener();
        assertFalse(listener.isInvoked());
        invokeWriteListener();
        assertFalse(listener.isInvoked());
        channel.wakeupReads();
        assertFalse(channel.isReadResumed());
        assertFalse(innerChannel.isReadResumed());
        channel.wakeupWrites();
        assertFalse(channel.isWriteResumed());
        assertFalse(innerChannel.isWriteResumed());
    }

    @Test
    public void setClosed() throws IOException {
        final DummyChannelListener listener = new DummyChannelListener();
        final DummyChannelListener closeListener = new DummyChannelListener();
        channel.getReadSetter().set(listener);
        channel.getWriteSetter().set(listener);
        channel.getCloseSetter().set(closeListener);

        assertTrue(channel.isOpen());
        assertFalse(channel.isReadShutDown());
        assertFalse(channel.isWriteComplete());
        assertFalse(channel.isWriteShutDown());
        assertTrue(channel.setClosed());
        // idempotent
        assertFalse(channel.setClosed());
        assertFalse(closeListener.isInvoked());
        assertTrue(channel.isOpen());
        assertTrue(channel.isReadShutDown());
        assertFalse(channel.isWriteComplete());
        assertTrue(channel.isWriteShutDown());

        // try to read
        invokeReadListener();
        assertFalse(listener.isInvoked());
        invokeWriteListener();
        assertFalse(listener.isInvoked());

        channel.flush();
        assertTrue(closeListener.isInvoked());
        assertFalse(channel.isOpen());
        assertTrue(channel.isReadShutDown());
        assertTrue(channel.isWriteComplete());
        assertTrue(channel.isWriteShutDown());

        closeListener.clearInvocation();

        // setClosed is idempotent
        channel.setClosed();
        assertFalse(channel.isOpen());
        assertTrue(channel.isReadShutDown());
        assertTrue(channel.isWriteComplete());
        assertTrue(channel.isWriteShutDown());
        invokeReadListener();
        assertFalse(listener.isInvoked());
        invokeWriteListener();
        assertFalse(listener.isInvoked());
        channel.wakeupReads();
        assertFalse(channel.isReadResumed());
        assertFalse(innerChannel.isReadResumed());
        channel.wakeupWrites();
        assertFalse(channel.isWriteResumed());
        assertFalse(innerChannel.isWriteResumed());
    }

    @Test
    public void shutdownReadsAndWrites() throws IOException {
        final DummyChannelListener listener = new DummyChannelListener();
        final DummyChannelListener closeListener = new DummyChannelListener();
        channel.getReadSetter().set(listener);
        channel.getWriteSetter().set(listener);
        channel.getCloseSetter().set(closeListener);

        assertTrue(channel.isOpen());
        assertFalse(channel.isReadShutDown());
        assertFalse(channel.isWriteComplete());
        assertFalse(channel.isWriteShutDown());
        channel.shutdownReads();

        assertTrue(channel.isOpen());
        assertTrue(channel.isReadShutDown());
        assertFalse(channel.isWriteComplete());
        assertFalse(channel.isWriteShutDown());

        // idempotent
        channel.shutdownReads();
        assertTrue(channel.isOpen());
        assertTrue(channel.isReadShutDown());
        assertFalse(channel.isWriteComplete());
        assertFalse(channel.isWriteShutDown());

        // try to read
        invokeReadListener();
        assertFalse(listener.isInvoked());

        channel.shutdownWrites();
        assertTrue(channel.isReadShutDown());
        assertFalse(channel.isWriteComplete());
        assertTrue(channel.isWriteShutDown());
        assertTrue(channel.isOpen());

        // idempotent
        channel.shutdownWrites();
        assertTrue(channel.isReadShutDown());
        assertFalse(channel.isWriteComplete());
        assertTrue(channel.isWriteShutDown());
        assertTrue(channel.isOpen());

        // try to write
        invokeWriteListener();
        assertFalse(listener.isInvoked());

        // close listener hasn't been invoked so far
        assertFalse(closeListener.isInvoked());

        channel.flush();
        assertTrue(channel.isReadShutDown());
        assertTrue(channel.isWriteComplete());
        assertTrue(channel.isWriteShutDown());
        assertFalse(channel.isOpen());
        assertTrue(closeListener.isInvoked());

        // flush is idempotent
        closeListener.clearInvocation();
        channel.flush();
        assertTrue(channel.isReadShutDown());
        assertTrue(channel.isWriteComplete());
        assertTrue(channel.isWriteShutDown());
        assertFalse(channel.isOpen());
        assertFalse(closeListener.isInvoked());
        channel.wakeupReads();
        assertFalse(channel.isReadResumed());
        assertFalse(innerChannel.isReadResumed());
        channel.wakeupWrites();
        assertFalse(channel.isWriteResumed());
        assertFalse(innerChannel.isWriteResumed());
    }

    @Test
    public void setReadShutDownAndSetWriteShutDown() throws IOException {
        final DummyChannelListener listener = new DummyChannelListener();
        final DummyChannelListener closeListener = new DummyChannelListener();
        channel.getReadSetter().set(listener);
        channel.getWriteSetter().set(listener);
        channel.getCloseSetter().set(closeListener);

        assertTrue(channel.isOpen());
        assertFalse(channel.isReadShutDown());
        assertFalse(channel.isWriteComplete());
        assertFalse(channel.isWriteShutDown());

        assertFalse(channel.setReadShutDown());

        assertTrue(channel.isOpen());
        assertTrue(channel.isReadShutDown());
        assertFalse(channel.isWriteComplete());
        assertFalse(channel.isWriteShutDown());

        // idempotent
        assertFalse(channel.setReadShutDown());
        assertTrue(channel.isOpen());
        assertTrue(channel.isReadShutDown());
        assertFalse(channel.isWriteComplete());
        assertFalse(channel.isWriteShutDown());

        // try to read
        invokeReadListener();
        assertFalse(listener.isInvoked());

        assertTrue(channel.setWriteShutDown());
        assertTrue(channel.isReadShutDown());
        assertFalse(channel.isWriteComplete());
        assertTrue(channel.isWriteShutDown());
        assertTrue(channel.isOpen());

        // idempotent
        assertFalse(channel.setWriteShutDown());
        assertTrue(channel.isReadShutDown());
        assertFalse(channel.isWriteComplete());
        assertTrue(channel.isWriteShutDown());
        assertTrue(channel.isOpen());

        // try to write
        invokeWriteListener();
        assertFalse(listener.isInvoked());

        // close listener hasn't been invoked so far
        assertFalse(closeListener.isInvoked());

        channel.flush();
        assertTrue(channel.isReadShutDown());
        assertTrue(channel.isWriteComplete());
        assertTrue(channel.isWriteShutDown());
        assertFalse(channel.isOpen());
        assertTrue(closeListener.isInvoked());

        // flush is idempotent
        closeListener.clearInvocation();
        channel.flush();
        assertTrue(channel.isReadShutDown());
        assertTrue(channel.isWriteComplete());
        assertTrue(channel.isWriteShutDown());
        assertFalse(channel.isOpen());
        assertFalse(closeListener.isInvoked());
        channel.wakeupReads();
        assertFalse(channel.isReadResumed());
        assertFalse(innerChannel.isReadResumed());
        channel.wakeupWrites();
        assertFalse(channel.isWriteResumed());
        assertFalse(innerChannel.isWriteResumed());
    }

    @Test
    public void shutdownWritesAndReads() throws IOException {
        final DummyChannelListener listener = new DummyChannelListener();
        final DummyChannelListener closeListener = new DummyChannelListener();
        channel.getReadSetter().set(listener);
        channel.getWriteSetter().set(listener);
        channel.getCloseSetter().set(closeListener);

        channel.flush();

        assertTrue(channel.isOpen());
        assertFalse(channel.isReadShutDown());
        assertFalse(channel.isWriteComplete());
        assertFalse(channel.isWriteShutDown());
        channel.shutdownWrites();

        // try to write
        invokeWriteListener();
        assertFalse(listener.isInvoked());

        assertTrue(channel.isOpen());
        assertFalse(channel.isReadShutDown());
        assertFalse(channel.isWriteComplete());
        assertTrue(channel.isWriteShutDown());

        channel.flush();
        assertTrue(channel.isOpen());
        assertFalse(channel.isReadShutDown());
        assertTrue(channel.isWriteComplete());
        assertTrue(channel.isWriteShutDown());

        channel.shutdownReads();
        assertTrue(channel.isReadShutDown());
        assertTrue(channel.isWriteComplete());
        assertTrue(channel.isWriteShutDown());
        assertFalse(channel.isOpen());
        assertTrue(closeListener.isInvoked());

        // try to read
        invokeReadListener();
        assertFalse(listener.isInvoked());

        // methods idempotent
        closeListener.clearInvocation();
        channel.shutdownWrites();
        channel.shutdownReads();
        assertTrue(channel.isReadShutDown());
        assertTrue(channel.isWriteComplete());
        assertTrue(channel.isWriteShutDown());
        assertFalse(channel.isOpen());
        assertFalse(closeListener.isInvoked());
        channel.wakeupReads();
        assertFalse(channel.isReadResumed());
        assertFalse(innerChannel.isReadResumed());
        channel.wakeupWrites();
        assertFalse(channel.isWriteResumed());
        assertFalse(innerChannel.isWriteResumed());
    }

    @Test
    public void setWriteShutDownAndSetReadShutDown() throws IOException {
        final DummyChannelListener listener = new DummyChannelListener();
        final DummyChannelListener closeListener = new DummyChannelListener();
        channel.getReadSetter().set(listener);
        channel.getWriteSetter().set(listener);
        channel.getCloseSetter().set(closeListener);

        channel.flush();

        assertTrue(channel.isOpen());
        assertFalse(channel.isReadShutDown());
        assertFalse(channel.isWriteComplete());
        assertFalse(channel.isWriteShutDown());

        assertFalse(channel.setWriteShutDown());

        // try to write
        invokeWriteListener();
        assertFalse(listener.isInvoked());

        assertTrue(channel.isOpen());
        assertFalse(channel.isReadShutDown());
        assertFalse(channel.isWriteComplete());
        assertTrue(channel.isWriteShutDown());

        channel.flush();
        assertTrue(channel.isOpen());
        assertFalse(channel.isReadShutDown());
        assertTrue(channel.isWriteComplete());
        assertTrue(channel.isWriteShutDown());

        assertTrue(channel.setReadShutDown());
        assertTrue(channel.isReadShutDown());
        assertTrue(channel.isWriteComplete());
        assertTrue(channel.isWriteShutDown());
        assertFalse(channel.isOpen());
        assertFalse(closeListener.isInvoked()); // listener is not invoked because we are using internal methods

        // try to read
        invokeReadListener();
        assertFalse(listener.isInvoked());

        // methods idempotent
        closeListener.clearInvocation();
        assertFalse(channel.setWriteShutDown());
        assertFalse(channel.setReadShutDown());
        assertTrue(channel.isReadShutDown());
        assertTrue(channel.isWriteComplete());
        assertTrue(channel.isWriteShutDown());
        assertFalse(channel.isOpen());
        assertFalse(closeListener.isInvoked());
        channel.wakeupReads();
        assertFalse(channel.isReadResumed());
        assertFalse(innerChannel.isReadResumed());
        channel.wakeupWrites();
        assertFalse(channel.isWriteResumed());
        assertFalse(innerChannel.isWriteResumed());
    }

    private final void invokeReadListener() {
        innerChannel.getReadListener().handleEvent(innerChannel);
    }

    private final void invokeWriteListener() {
        innerChannel.getWriteListener().handleEvent(innerChannel);
    }

    private final void invokeCloseListener() {
        innerChannel.getCloseListener().handleEvent(innerChannel);
    }

    private static class DummyTranslatingSuspendableChannel extends TranslatingSuspendableChannel<SuspendableChannel, ConnectedStreamChannelMock> {

        protected DummyTranslatingSuspendableChannel(ConnectedStreamChannelMock channel) {
            super(channel);
        }

        @Override
        public void setReadReady() {
            super.setReadReady();
        }

        @Override
        public void clearReadReady() {
            super.clearReadReady();
        }

        @Override
        public void setReadRequiresWrite() {
            super.setReadRequiresWrite();
        }

        @Override
        public void clearReadRequiresWrite() {
            super.clearReadRequiresWrite();
        }

        @Override
        public boolean tryAddReadRequiresExternal() {
            return super.tryAddReadRequiresExternal();
        }

        @Override
        public void removeReadRequiresExternal() {
            super.removeReadRequiresExternal();
        }

        @Override
        public boolean setReadShutDown() {
            return super.setReadShutDown();
        }

        @Override
        public void setWriteReady() {
            super.setWriteReady();
        }

        @Override
        public void clearWriteReady() {
            super.clearWriteReady();
        }

        @Override
        public void setWriteRequiresRead() {
            super.setWriteRequiresRead();
        }

        @Override
        public void clearWriteRequiresRead() {
            super.clearWriteRequiresRead();
        }

        @Override
        public boolean tryAddWriteRequiresExternal() {
            return super.tryAddWriteRequiresExternal();
        }

        @Override
        public void removeWriteRequiresExternal() {
            super.removeWriteRequiresExternal();
        }

        @Override
        public boolean setWriteShutDown() {
            return super.setWriteShutDown();
        }

        @Override
        public boolean setClosed() {
            return super.setClosed();
        }
    }

    private static class DummyChannelListener implements ChannelListener<SuspendableChannel> {

        private boolean invoked = false;
        private Action[] actions;

        public DummyChannelListener(Action... a) {
            actions = a;
        }

        @Override
        public void handleEvent(SuspendableChannel channel) {
            if (invoked) {
                throw new IllegalStateException("duplicate call to same listener!");
            }
            if (actions != null) {
                Action reverseActionRequired = null;
                for (Action a: actions) {
                    a.perform(channel);
                    if (a.requiredReverseAction() != null) {
                        reverseActionRequired = a.requiredReverseAction();
                    }
                }
                if (reverseActionRequired != null) {
                    actions = new Action[] {reverseActionRequired};
                    return;
                }
            }
            invoked = true;
        }

        public boolean isInvoked() {
            return invoked;
        }

        public void clearInvocation() {
            invoked = false;
        }
    }

    private static class ReadWaiter implements Runnable {

        private final DummyTranslatingSuspendableChannel channel;
        private final int timeout;
        private final TimeUnit timeoutUnit;

        public ReadWaiter(DummyTranslatingSuspendableChannel c) {
            this(c, -1, null);
        }

        public ReadWaiter(DummyTranslatingSuspendableChannel c, int t, TimeUnit tu) {
            channel = c;
            timeout = t;
            timeoutUnit = tu;
        }

        @Override
        public void run() {
            try {
                if (timeoutUnit == null) {
                    channel.awaitReadable();
                } else {
                    channel.awaitReadable(timeout, timeoutUnit);
                    // idempotent
                    channel.awaitReadable(timeout, timeoutUnit);
                }
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }

    private static class WriteWaiter implements Runnable {

        private final DummyTranslatingSuspendableChannel channel;
        private final int timeout;
        private final TimeUnit timeoutUnit;

        public WriteWaiter(DummyTranslatingSuspendableChannel c) {
            this(c, -1, null);
        }

        public WriteWaiter(DummyTranslatingSuspendableChannel c, int t, TimeUnit tu) {
            channel = c;
            timeout = t;
            timeoutUnit = tu;
        }

        @Override
        public void run() {
            try {
                if (timeoutUnit == null) {
                    channel.awaitWritable();
                } else {
                    channel.awaitWritable(timeout, timeoutUnit);
                    // idempotent
                    channel.awaitWritable(timeout, timeoutUnit);
                }
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }

    private static enum Action {
        READ_READY {
            @Override
            public void perform(SuspendableChannel c) {
                ((DummyTranslatingSuspendableChannel) c).setReadReady();
            }
            
            @Override
            public Action requiredReverseAction() {
                return CLEAR_READ_READY;
            }
        },
        CLEAR_READ_READY {
            @Override
            public void perform(SuspendableChannel c) {
                ((DummyTranslatingSuspendableChannel) c).clearReadReady();
            }
        },
        WRITE_READY {
            @Override
            public void perform(SuspendableChannel c) {
                ((DummyTranslatingSuspendableChannel) c).setWriteReady();
            }
            
            @Override
            public Action requiredReverseAction() {
                return CLEAR_WRITE_READY;
            }
        },
        CLEAR_WRITE_READY {
            @Override
            public void perform(SuspendableChannel c) {
                ((DummyTranslatingSuspendableChannel) c).clearWriteReady();
            }
        },
        READ_REQUIRES_WRITE {
            @Override
            public void perform(SuspendableChannel c) {
                ((DummyTranslatingSuspendableChannel) c).setReadRequiresWrite();
            }
        },
        WRITE_REQUIRES_READ {
            @Override
            public void perform(SuspendableChannel c) {
                ((DummyTranslatingSuspendableChannel) c).setWriteRequiresRead();
            }
        };
    
        public abstract void perform(SuspendableChannel c);

        public Action requiredReverseAction() {
            return null;
        }
    }
}
