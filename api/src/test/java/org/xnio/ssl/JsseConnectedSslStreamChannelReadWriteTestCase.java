/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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
package org.xnio.ssl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.xnio.ssl.mock.SSLEngineMock.CLOSE_MSG;
import static org.xnio.ssl.mock.SSLEngineMock.HANDSHAKE_MSG;
import static org.xnio.ssl.mock.SSLEngineMock.HandshakeAction.FINISH;
import static org.xnio.ssl.mock.SSLEngineMock.HandshakeAction.NEED_TASK;
import static org.xnio.ssl.mock.SSLEngineMock.HandshakeAction.NEED_UNWRAP;
import static org.xnio.ssl.mock.SSLEngineMock.HandshakeAction.NEED_WRAP;
import static org.xnio.ssl.mock.SSLEngineMock.HandshakeAction.PERFORM_REQUESTED_ACTION;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.SSLEngineResult.HandshakeStatus;

import org.jmock.integration.junit4.JMock;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.Buffers;
import org.xnio.ssl.mock.SSLEngineMock;


/**
 * Test for concurrent read and write operations on  {@link #JsseConnectedSslStreamChannel}.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
@RunWith(JMock.class)
public class JsseConnectedSslStreamChannelReadWriteTestCase extends AbstractJsseConnectedSslStreamChannelTest{

    @Test
    public void simpleReadAndWrite() throws Exception {
        // no handshake actions for engineMock this time, meaning it will just wrap and unwrap without any handshake
        // the message we want to write
        connectedChannelMock.setReadData("read data");
        connectedChannelMock.enableRead(true);
        final Future<ByteBuffer> readFuture = triggerReadThread(9);
        final Future<Void> writeFuture = triggerWriteThread("write data");
        writeFuture.get();
        connectedChannelMock.setReadData(CLOSE_MSG);
        final ByteBuffer readBuffer = readFuture.get();
        // FIXME: move shutdownWrites to write thread, and shutdown reads to read thread after the issue involving
        // those operations is worked around 
        // MORE INFO: we have to shutdown write and read only after channel has read and written everything...
        // the mock here only mimics the behavior we find in SSLEngine implementation: the channel cannot do read
        // nor write after either read or write have been shutdown
        sslChannel.shutdownWrites();
        assertTrue(sslChannel.flush());
        sslChannel.shutdownReads();
        // close channel
        sslChannel.close();
        // data expected to have been copied to buffer by channel
        assertReadMessage(readBuffer, "read data");
        assertWrittenMessage("write data", CLOSE_MSG);
    }

    @Test
    public void readAndWriteMappedWrap() throws Exception {
        // map the wrap
        engineMock.addWrapEntry("a very long message", "BLABLABLABLABLABLABLA");
        engineMock.addWrapEntry("short msg", "MSG");
        // no handshake actions for engineMock this time, meaning that it will just wrap and unwrap without any handshake
        // the message we want to read
        connectedChannelMock.setReadData("BLABLABLABLABLABLABLA");
        connectedChannelMock.enableRead(true);
        // attempt to read and write
        final Future<Void> writeFuture = triggerWriteThread("short msg", "short msg");
        final Future<ByteBuffer> readFuture = triggerReadThread(19);
        writeFuture.get();
        connectedChannelMock.setReadData(CLOSE_MSG);
        final ByteBuffer readBuffer = readFuture.get();
        // channel should be able to shutdown reads and writes
        sslChannel.shutdownReads();
        sslChannel.shutdownWrites();
        assertTrue(sslChannel.flush());
        // close channel
        sslChannel.close();
        // data expected to have been read to 'buffer' by 'channel'
        assertReadMessage(readBuffer, "a very long message");
        assertWrittenMessage("MSG", "MSG", CLOSE_MSG);
    }

    @Test
    public void readAndWriteWithSimpleHandshake() throws Exception {
        // map all data to be read and written
        engineMock.addWrapEntry(HANDSHAKE_MSG, "handshake");
        engineMock.addWrapEntry("MockTest", "mock test works!");
        engineMock.addWrapEntry(CLOSE_MSG, "channel closed");
        // set the handshake actions that engineMock will emulate
        engineMock.setHandshakeActions(NEED_WRAP, NEED_UNWRAP, NEED_TASK, FINISH);
        // set ReadData on connectedChannelMock, including the wrapped version of message we want to read
        connectedChannelMock.setReadData("handshake", "mock test works!", "mock test works!");
        connectedChannelMock.enableRead(true);

        // attempt to read and write
        final Future<Void> writeFuture = triggerWriteThread("MockTest");
        final Future<ByteBuffer> readFuture = triggerReadThread(16);
        writeFuture.get();
        connectedChannelMock.setReadData("channel closed");
        final ByteBuffer readBuffer = readFuture.get();

        // channel should be able to shutdown reads and writes
        sslChannel.shutdownReads();
        sslChannel.shutdownWrites();
        assertTrue(sslChannel.flush());
        // close channel
        sslChannel.close();

        assertReadMessage(readBuffer, "MockTest", "MockTest");
        // data expected to have been written to 'connectedChannelMock' by 'channel'
        assertWrittenMessage("handshake", "mock test works!", "channel closed");
    }

    @Test
    public void readAndWriteWithTasklessHandshake() throws Exception {
        // map data to be read and written
        engineMock.addWrapEntry("Mock Test", "{testReadWriteWithTasklessHandshake}");
        engineMock.addWrapEntry(CLOSE_MSG, " _ ");

        // set the handshake actions that engineMock will emulate
        engineMock.setHandshakeActions(NEED_WRAP, NEED_UNWRAP, FINISH);
        // set ReadData on connectedChannelMock
        connectedChannelMock.setReadData(SSLEngineMock.HANDSHAKE_MSG, "{testReadWriteWithTasklessHandshake}");
        connectedChannelMock.enableRead(true);

        // attempt to read and write
        final Future<Void> writeFuture = triggerWriteThread("Mock Test", "Mock Test", "Mock Test", "Mock Test");
        final Future<ByteBuffer> readFuture = triggerReadThread(9);
        writeFuture.get();
        connectedChannelMock.setReadData(" _ ");
        final ByteBuffer readBuffer = readFuture.get();

        // channel should be able to shutdown reads and writes
        sslChannel.shutdownReads();
        sslChannel.shutdownWrites();
        assertTrue(sslChannel.flush());
        // close channel
        sslChannel.close();
        // data expected to have been read from 'connectedChannelMock' by 'channel'
        assertReadMessage(readBuffer, "Mock Test");

        // data expected to have been written to 'connectedChannelMock' by 'channel'
        assertWrittenMessage(HANDSHAKE_MSG, "{testReadWriteWithTasklessHandshake}",
                "{testReadWriteWithTasklessHandshake}", "{testReadWriteWithTasklessHandshake}",
                "{testReadWriteWithTasklessHandshake}", " _ ");
    }

    @Test
    public void multipleFeedReadAndWriteWithSimpleHandshake() throws Exception {
        // map data to be read and written
        engineMock.addWrapEntry(HANDSHAKE_MSG, "{handshake data}");
        engineMock.addWrapEntry("Mock Read/Write Test", "{data}");
        engineMock.addWrapEntry("it works!", "{more data}");
        engineMock.addWrapEntry(CLOSE_MSG, "{message closed}");
        // set the handshake actions that engineMock will emulate
        engineMock.setHandshakeActions(NEED_UNWRAP, NEED_WRAP, NEED_TASK, FINISH);
        // set ReadData on connectedChannelMock
        connectedChannelMock.setReadData();
        // enable read on connectedChannelMock, meaning that data above will be available to be read right away
        connectedChannelMock.enableRead(true);

        // attempt to read and write
        final Future<Void> writeFuture = triggerWriteThread("it works!", "Mock Read/Write Test", "it works!",
                "Mock Read/Write Test", "it works!", "it works!", "Mock Read/Write Test");
        final Future<ByteBuffer> readFuture = triggerReadThread(67);

        Thread.sleep(20);
        connectedChannelMock.setReadData("{handshake data}");
        Thread.sleep(10);
        connectedChannelMock.setReadData( "{data}", "{data}", "{more data}");
        Thread.sleep(10);
        connectedChannelMock.setReadData("{more data}", "{more data}");
        Thread.sleep(10);

        writeFuture.get();
        connectedChannelMock.setReadData("{message closed}");
        final ByteBuffer readBuffer = readFuture.get();
        assertNotNull(readBuffer);

        // channel should be able to shutdown reads and writes
        sslChannel.shutdownReads();
        sslChannel.shutdownWrites();
        assertTrue(sslChannel.flush());
        // close channel
        sslChannel.close();

        // data expected to have been read from 'connectedChannelMock' by 'channel' so far
        assertReadMessage(readBuffer, "Mock Read/Write Test", "Mock Read/Write Test", "it works!", "it works!", "it works!");

        // data expected to have been written to 'connectedChannelMock' by 'channel'
        assertWrittenMessage("{handshake data}", "{more data}", "{data}", "{more data}", "{data}", "{more data}",
                "{more data}", "{data}", "{message closed}");
    }

    @Test
    public void readAndWriteWithConstantHandshake() throws Exception {
        // set the handshake actions that engineMock will emulate
        engineMock.setHandshakeActions(NEED_WRAP, NEED_UNWRAP, NEED_TASK, FINISH, PERFORM_REQUESTED_ACTION,
                NEED_UNWRAP, NEED_WRAP, NEED_TASK, FINISH, PERFORM_REQUESTED_ACTION,
                NEED_WRAP, NEED_WRAP, NEED_WRAP, NEED_UNWRAP, NEED_TASK, NEED_TASK, NEED_TASK, NEED_TASK, FINISH,
                PERFORM_REQUESTED_ACTION, NEED_WRAP, NEED_UNWRAP, NEED_TASK, FINISH, PERFORM_REQUESTED_ACTION);
        // set ReadData on connectedChannelMock
        connectedChannelMock.setReadData(HANDSHAKE_MSG, "read a lot");
        // enable read on connectedChannelMock, meaning that data above will be available to be read right away
        connectedChannelMock.enableRead(true);

        // attempt to read and write
        final Future<Void> writeFuture = triggerMultipleWriteThread("write a lot", "write a lot", "write a lot",
                "write it", "a lot", "write it down", "a lot");
        final Future<ByteBuffer> readFuture = triggerReadThread(54);
        Thread.sleep(20);
        connectedChannelMock.setReadData(HANDSHAKE_MSG, HANDSHAKE_MSG);
        Thread.sleep(10);
        connectedChannelMock.setReadData( "this is a lot", "lot", "lot", "lot", "lot", "lot", "lot", "lot");
        Thread.sleep(10);
        connectedChannelMock.setReadData("read a lot", HANDSHAKE_MSG);
        Thread.sleep(10);

        writeFuture.get();
        sslChannel.shutdownWrites();
        assertFalse(sslChannel.flush());
        connectedChannelMock.setReadData(CLOSE_MSG);
        final ByteBuffer readBuffer = readFuture.get();
        assertNotNull(readBuffer);


        // make sure that channel managed to do the WRAP and there is no more handshake actions left
        assertSame(HandshakeStatus.NOT_HANDSHAKING, engineMock.getHandshakeStatus());
        // shutdown reads
        sslChannel.shutdownReads();
        sslChannel.shutdownWrites();
        assertTrue(sslChannel.flush());
        // close channel
        sslChannel.close();

        // data expected to have been read from 'connectedChannelMock' by 'channel' so far
        assertReadMessage(readBuffer, "read a lot", "this is a lot", "lot", "lot", "lot" , "lot", "lot", "lot", "lot",
                "read a lot");
        // data expected to have been written to 'connectedChannelMock' by 'channel'
        assertWrittenMessage(new String[]{HANDSHAKE_MSG, HANDSHAKE_MSG, HANDSHAKE_MSG, HANDSHAKE_MSG, HANDSHAKE_MSG,
                HANDSHAKE_MSG, CLOSE_MSG}, new String[] {"write a lot", "write a lot", "write a lot", "write it",
                "a lot", "write it down", "a lot"});
    }

    @Test
    public void readAndWriteWithConstantHandshakeAndMappedData() throws Exception {
        // map data to be read and written
        engineMock.addWrapEntry(HANDSHAKE_MSG, "HANDSHAKE_MSG");
        engineMock.addWrapEntry("MockTest1", "MOCK 1");
        engineMock.addWrapEntry("MockTest2", "MOCK 2");
        engineMock.addWrapEntry("MockTest3", "MOCK 3");
        engineMock.addWrapEntry("MockTest4", "MOCK 4");
        engineMock.addWrapEntry(CLOSE_MSG, "CLOSE_MSG");
        // set the handshake actions that engineMock will emulate
        engineMock.setHandshakeActions(NEED_WRAP, NEED_UNWRAP, NEED_TASK, FINISH, PERFORM_REQUESTED_ACTION,
                NEED_UNWRAP, NEED_WRAP, NEED_TASK, FINISH, PERFORM_REQUESTED_ACTION,
                NEED_WRAP, NEED_WRAP, NEED_WRAP, NEED_UNWRAP, NEED_TASK, NEED_TASK, NEED_TASK, NEED_TASK, FINISH,
                PERFORM_REQUESTED_ACTION, NEED_WRAP, NEED_UNWRAP, NEED_TASK, FINISH, PERFORM_REQUESTED_ACTION,
                NEED_UNWRAP, NEED_WRAP, NEED_TASK, FINISH, PERFORM_REQUESTED_ACTION, NEED_UNWRAP, NEED_WRAP,
                NEED_TASK, FINISH, PERFORM_REQUESTED_ACTION, NEED_WRAP, NEED_UNWRAP, NEED_TASK, FINISH,
                PERFORM_REQUESTED_ACTION);
        // set ReadData on connectedChannelMock
        connectedChannelMock.setReadData("HANDSHAKE_MSG", "MOCK 3");
        // enable read on connectedChannelMock, meaning that data above will be available to be read right away
        connectedChannelMock.enableRead(true);

        // attempt to read and write
        final Future<Void> writeFuture = triggerMultipleWriteThread("MockTest1", "MockTest2", "MockTest2", "MockTest1", "MockTest2", "MockTest3", "MockTest4");
        final Future<ByteBuffer> readFuture = triggerMultipleReadThread(99);
        Thread.sleep(40);
        connectedChannelMock.setReadData("HANDSHAKE_MSG", "HANDSHAKE_MSG", "MOCK 3", "HANDSHAKE_MSG");
        Thread.sleep(10);
        connectedChannelMock.setReadData( "MOCK 2", "MOCK 2", "HANDSHAKE_MSG", "MOCK 4");
        Thread.sleep(10);
        connectedChannelMock.setReadData("MOCK 4", "MOCK 1", "MOCK 3", "MOCK 2", "MOCK 4");
        Thread.sleep(10);
        connectedChannelMock.setReadData("MOCK 1", "HANDSHAKE_MSG", "HANDSHAKE_MSG");
        Thread.sleep(10);

        writeFuture.get();
        sslChannel.shutdownWrites();
        assertFalse(sslChannel.flush());
        connectedChannelMock.setReadData("CLOSE_MSG");
        final ByteBuffer readBuffer = readFuture.get();
        assertNotNull(readBuffer);

        // make sure that channel managed to do the WRAP and there is no more handshake actions left
        assertSame(HandshakeStatus.NOT_HANDSHAKING, engineMock.getHandshakeStatus());
        // shutdown reads
        sslChannel.shutdownReads();
        sslChannel.shutdownWrites();
        assertTrue(sslChannel.flush());
        // close channel
        sslChannel.close();
        // make sure that channel managed to do the WRAP and there is no more handshake actions left
        assertSame(HandshakeStatus.NOT_HANDSHAKING, engineMock.getHandshakeStatus());

        // data expected to have been read from 'connectedChannelMock' by 'channel' so far
        assertReadMessage(readBuffer, "MockTest3", "MockTest3", "MockTest2", "MockTest2", "MockTest4", "MockTest4",
                "MockTest1", "MockTest3", "MockTest2", "MockTest4", "MockTest1");
        // data expected to have been written to 'connectedChannelMock' by 'channel'
        assertWrittenMessage(new String[]{"HANDSHAKE_MSG", "HANDSHAKE_MSG", "HANDSHAKE_MSG", "HANDSHAKE_MSG",
                "HANDSHAKE_MSG", "HANDSHAKE_MSG", "HANDSHAKE_MSG", "HANDSHAKE_MSG", "HANDSHAKE_MSG", "CLOSE_MSG"},
                new String[] {"MOCK 1", "MOCK 2", "MOCK 2", "MOCK 1", "MOCK 2", "MOCK 3", "MOCK 4"});
    }

    @Test
    public void readAndWriteWithIntercalatedHandshake() throws Exception {
        // set the handshake actions that engineMock will emulate
        engineMock.setHandshakeActions(NEED_WRAP, NEED_UNWRAP, NEED_TASK, FINISH, PERFORM_REQUESTED_ACTION,
                NEED_WRAP, NEED_UNWRAP, PERFORM_REQUESTED_ACTION, NEED_UNWRAP, PERFORM_REQUESTED_ACTION, FINISH);
        // set ReadData on connectedChannelMock
        connectedChannelMock.setReadData(HANDSHAKE_MSG, "read this", "read this", "read this", "read this",
                HANDSHAKE_MSG, "read this", HANDSHAKE_MSG, "read this");

        // attempt to read and write
        final Future<Void> writeFuture = triggerMultipleWriteThread("write this", "write this", "write this", "write this",
                "write this");
        final Future<ByteBuffer> readFuture = triggerReadThread(54);
        // enable read on connectedChannelMock, meaning that data above will be available to be read right away
        connectedChannelMock.enableRead(true);

        writeFuture.get();
        sslChannel.shutdownWrites();
        assertFalse(sslChannel.flush());
        connectedChannelMock.setReadData(CLOSE_MSG);
        final ByteBuffer readBuffer = readFuture.get();
        assertNotNull(readBuffer);

        // make sure that channel managed to do the WRAP and there is no more handshake actions left
        assertSame(HandshakeStatus.NOT_HANDSHAKING, engineMock.getHandshakeStatus());
        // shutdown reads
        sslChannel.shutdownReads();
        sslChannel.shutdownWrites();
        assertTrue(sslChannel.flush());
        // close channel
        sslChannel.close();
        // make sure that channel managed to do the WRAP and there is no more handshake actions left
        assertSame(HandshakeStatus.NOT_HANDSHAKING, engineMock.getHandshakeStatus());

        // data expected to have been read from 'connectedChannelMock' by 'channel' so far
        assertReadMessage(readBuffer, "read this", "read this", "read this", "read this", "read this", "read this");
        // data expected to have been written to 'connectedChannelMock' by 'channel'
        assertWrittenMessage(new String[]{HANDSHAKE_MSG, HANDSHAKE_MSG, CLOSE_MSG},
                new String[] {"write this", "write this", "write this", "write this", "write this"});
    }

    @Test
    public void readAndWriteWithIntercalatedHandshakeAndMappedData() throws Exception {
        // map all data to be read and written
        engineMock.addWrapEntry(HANDSHAKE_MSG, "[!@#$%^&*()_]");
        engineMock.addWrapEntry("this", "read this");
        engineMock.addWrapEntry("write this", "this");
        engineMock.addWrapEntry(CLOSE_MSG, "[_)(*&^%$#@!]");

        // set the handshake actions that engineMock will emulate
        engineMock.setHandshakeActions(NEED_WRAP, NEED_UNWRAP, NEED_TASK, FINISH, PERFORM_REQUESTED_ACTION,
                NEED_WRAP, NEED_UNWRAP, PERFORM_REQUESTED_ACTION, NEED_UNWRAP, PERFORM_REQUESTED_ACTION, FINISH);
        // set ReadData on connectedChannelMock
        connectedChannelMock.setReadData("[!@#$%^&*()_]", "read this", "read this", "read this", "read this",
                "[!@#$%^&*()_]", "read this", "[!@#$%^&*()_]", "read this");
        // enable read on connectedChannelMock, meaning that data above will be available to be read right away
        connectedChannelMock.enableRead(true);

        // attempt to read and write
        final Future<Void> writeFuture = triggerMultipleWriteThread("write this", "write this", "write this", "write this",
                "write this");
        final Future<ByteBuffer> readFuture = triggerMultipleReadThread(24);

        writeFuture.get();
        sslChannel.shutdownWrites();
        assertFalse(sslChannel.flush());
        connectedChannelMock.setReadData("[_)(*&^%$#@!]");
        final ByteBuffer readBuffer = readFuture.get();
        assertNotNull(readBuffer);

        // make sure that channel managed to do the WRAP and there is no more handshake actions left
        assertSame(HandshakeStatus.NOT_HANDSHAKING, engineMock.getHandshakeStatus());
        // shutdown reads
        sslChannel.shutdownReads();
        sslChannel.shutdownWrites();
        assertTrue(sslChannel.flush());
        // close channel
        sslChannel.close();
        // make sure that channel managed to do the WRAP and there is no more handshake actions left
        assertSame(HandshakeStatus.NOT_HANDSHAKING, engineMock.getHandshakeStatus());

        // data expected to have been read from 'connectedChannelMock' by 'channel' so far
        assertReadMessage(readBuffer, "this", "this", "this", "this", "this", "this");
        // data expected to have been written to 'connectedChannelMock' by 'channel'
        assertWrittenMessage(new String[]{"[!@#$%^&*()_]", "[!@#$%^&*()_]", "[_)(*&^%$#@!]"},
                new String[] {"this", "this", "this", "this", "this"});
    }

    private Future<ByteBuffer> triggerReadThread(int expectedReadLength) {
        ReadRunnable readRunnable = new ReadRunnable(expectedReadLength);
        Thread newThread = new Thread(readRunnable);
        newThread.start();
        return readRunnable.getResultFuture();
    }

    private Future<Void> triggerWriteThread(String... text) {
        WriteRunnable writeRunnable = new WriteRunnable(text);
        Thread newThread = new Thread(writeRunnable);
        newThread.start();
        return writeRunnable.getResultFuture();
    }

    private Future<ByteBuffer> triggerMultipleReadThread(int expectedReadLength) {
        MultipleReadRunnable readRunnable = new MultipleReadRunnable(expectedReadLength);
        Thread newThread = new Thread(readRunnable);
        newThread.start();
        return readRunnable.getResultFuture();
    }

    private Future<Void> triggerMultipleWriteThread(String... text) {
        MultipleWriteRunnable writeRunnable = new MultipleWriteRunnable(text);
        Thread newThread = new Thread(writeRunnable);
        newThread.start();
        return writeRunnable.getResultFuture();
    }

    private class ReadRunnable implements Runnable {
        private ResultFuture<ByteBuffer> resultFuture;
        private int expectedReadLength;

        public ReadRunnable(int expectedReadLength) {
            this.expectedReadLength = expectedReadLength;
            this.resultFuture = new ResultFuture<ByteBuffer>();
        }
        
        public Future<ByteBuffer> getResultFuture() {
            return resultFuture;
        }

        @Override
        public void run() {
            final ByteBuffer buffer = ByteBuffer.allocate(100);
            final ByteBuffer[] buffers = new ByteBuffer[10];
            for (int i = 0; i < 10; i++) {
                buffers[i] = ByteBuffer.allocate(10);
            }
            // attempt to read... channel is expected to read the entire message without any issues
            try {
                int totalLength = 0;
                long length = 0;
                while ((length  = sslChannel.read(buffers)) >= 0) {
                    totalLength += length;
                }
                assertEquals(-1, sslChannel.read(buffers, 0, 10));
                assertEquals(-1, sslChannel.read(buffers, 0, 10));
                assertEquals(-1, sslChannel.read(buffers, 0, 10));
                for (ByteBuffer b: buffers) {
                    b.flip();
                }
                Buffers.copy(buffer, buffers, 0, 10);
                buffer.flip();
                assertEquals("This is what we read '" + Buffers.getModifiedUtf8(buffer) + "'", expectedReadLength, totalLength);
                
            } catch (IOException e) {
                throw new RuntimeException("Unexpected IOException while reading", e);
            }
            resultFuture.setResult(buffer);
        }
    }

    private class WriteRunnable implements Runnable {
        private ResultFuture<Void> resultFuture;
        private String[] text;

        public WriteRunnable(String... text) {
            this.text = text;
            this.resultFuture = new ResultFuture<Void>();
        }

        public Future<Void> getResultFuture() {
            return resultFuture;
        }

        @Override
        public void run() {
            final ByteBuffer[] buffer = new ByteBuffer[text.length];
            int totalBytes = 0;
            try {
                for (int i = 0; i < text.length; i++) {
                // attempt to write... channel is expected to write the entire message without any issues
                    buffer[i] = ByteBuffer.allocate(50);
                    buffer[i].put(text[i].getBytes("UTF-8")).flip();
                    totalBytes += text[i].length();
                }
                final int attemptsLimit = 10000;
                int attempts = 0;
                long bytes = 0;
                while ((bytes += sslChannel.write(buffer)) < totalBytes && (++ attempts) < attemptsLimit);
                assertEquals(totalBytes, bytes);
            } catch (IOException e) {
                throw new RuntimeException("Unexpected exception while writing", e);
            }
            resultFuture.setResult(null);
        }
    }

    private class MultipleReadRunnable implements Runnable {
        private ResultFuture<ByteBuffer> resultFuture;
        private int expectedReadLength;

        public MultipleReadRunnable(int expectedReadLength) {
            this.expectedReadLength = expectedReadLength;
            this.resultFuture = new ResultFuture<ByteBuffer>();
        }
        
        public Future<ByteBuffer> getResultFuture() {
            return resultFuture;
        }

        @Override
        public void run() {
            final ByteBuffer buffer = ByteBuffer.allocate(100);
            // attempt to read... channel is expected to read the entire message without any issues
            try {
                int totalLength = 0;
                int length = 0;
                while ((length  = sslChannel.read(buffer)) >= 0) {
                    totalLength += length;
                }
                assertEquals(-1, sslChannel.read(buffer));
                assertEquals(-1, sslChannel.read(buffer));
                assertEquals(-1, sslChannel.read(buffer));
                buffer.flip();
                assertEquals("This is what we read '" + Buffers.getModifiedUtf8(buffer) + "'", expectedReadLength, totalLength);
            } catch (IOException e) {
                throw new RuntimeException("Unexpected IOException while reading", e);
            }
            resultFuture.setResult(buffer);
        }
    }

    private class MultipleWriteRunnable implements Runnable {
        private ResultFuture<Void> resultFuture;
        private String[] text;

        public MultipleWriteRunnable(String... text) {
            this.text = text;
            this.resultFuture = new ResultFuture<Void>();
        }

        public Future<Void> getResultFuture() {
            return resultFuture;
        }

        @Override
        public void run() {
            final ByteBuffer buffer = ByteBuffer.allocate(100);
            try {
                // attempt to write... channel is expected to write the entire message without any issues
                for (String textStr: text) {
                    buffer.put(textStr.getBytes("UTF-8")).flip();
                    int bytes = 0;
                    while ((bytes = sslChannel.write(buffer)) == 0);
                    assertEquals(textStr.length(), bytes);
                    buffer.compact();
                }
            } catch (IOException e) {
                throw new RuntimeException("Unexpected exception while writing", e);
            }
            resultFuture.setResult(null);
        }
    }

    private static class ResultFuture<T> implements Future<T> {
        private T result;
        private final CountDownLatch countDownLatch = new CountDownLatch(1);
        private final long delay;
        private final TimeUnit delayTimeUnit;

        ResultFuture() {
            delay = 500000L;
            delayTimeUnit = TimeUnit.MILLISECONDS;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return result != null;
        }

        @Override
        public T get() throws InterruptedException, ExecutionException {
            try {
                return get(delay, delayTimeUnit);
            } catch (TimeoutException e) {
                throw new RuntimeException("Could not get start exception in " + delay + " " + delayTimeUnit + " timeout.");
            }
        }

        @Override
        public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            countDownLatch.await(timeout, unit);
            return result;
        }

        private void setResult(final T result) {
            this.result = result;
            countDownLatch.countDown();
        }
    }
}
