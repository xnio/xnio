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

package org.xnio.ssl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.xnio.ssl.mock.SSLEngineMock.CLOSE_MSG;
import static org.xnio.ssl.mock.SSLEngineMock.HANDSHAKE_MSG;
import static org.xnio.ssl.mock.SSLEngineMock.HandshakeAction.FINISH;
import static org.xnio.ssl.mock.SSLEngineMock.HandshakeAction.NEED_FAULTY_TASK;
import static org.xnio.ssl.mock.SSLEngineMock.HandshakeAction.NEED_TASK;
import static org.xnio.ssl.mock.SSLEngineMock.HandshakeAction.NEED_UNWRAP;
import static org.xnio.ssl.mock.SSLEngineMock.HandshakeAction.NEED_WRAP;
import static org.xnio.ssl.mock.SSLEngineMock.HandshakeAction.PERFORM_REQUESTED_ACTION;

import java.io.IOException;
import java.nio.ByteBuffer;

import javax.net.ssl.SSLEngineResult.HandshakeStatus;

import org.jmock.integration.junit4.JMock;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.ssl.mock.SSLEngineMock;


/**
 * Test write operations on {@link JsseConnectedSslStreamChannel}.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 */
@RunWith(JMock.class)
public class ConnectedSslStreamChannelWriteTestCase extends AbstractConnectedSslStreamChannelTest {

    @Test
    public void writeWithoutHandshake() throws IOException {
        // no handshake actions for engineMock this time, meaning that it will just wrap and unwrap without any handshake
        // the message we want to write
        final ByteBuffer buffer = ByteBuffer.allocate(100);
        buffer.put("MockTest".getBytes("UTF-8")).flip();
        // attempt to write... channel is expected to write the entire message without any issues
        assertEquals(8, sslChannel.write(buffer));
        assertFalse(buffer.hasRemaining());
        // channel should not be able to shutdown writes... for that, it must receive a close message
        sslChannel.shutdownWrites();
        assertFalse(sslChannel.flush());
        // send the close message
        conduitMock.setReadData(CLOSE_MSG);
        conduitMock.enableReads(true);
        sslChannel.shutdownWrites();
        assertTrue(sslChannel.flush());

        // close channel
        sslChannel.close();
        // data expected to have been written to 'conduitMock' by 'sslChannel'
        assertWrittenMessage("MockTest", CLOSE_MSG);
    }

    @Test
    public void writeWithoutHandshakeMappedWrap() throws IOException {
        // map the wrap
        engineMock.addWrapEntry("MockTest", "WRAPPED_MOCK_TEST");

        // no handshake actions for engineMock this time, meaning that it will just wrap and unwrap without any handshake
        // the message we want to write
        final ByteBuffer buffer = ByteBuffer.allocate(100);
        buffer.put("MockTest".getBytes("UTF-8")).flip();
        // attempt to write... channel is expected to write the entire message without any issues
        assertEquals(8, sslChannel.write(buffer));
        assertFalse(buffer.hasRemaining());
        // channel should not be able to shutdown writes... for that, it must receive a close message
        sslChannel.shutdownWrites();
        assertFalse(sslChannel.flush());
        // send the close message
        conduitMock.setReadData(CLOSE_MSG);
        conduitMock.enableReads(true);
        sslChannel.shutdownWrites();
        assertTrue(sslChannel.flush());

        // close channel
        sslChannel.close();
        // data expected to have bee written to 'conduitMock' by 'sslChannel'
        assertWrittenMessage("WRAPPED_MOCK_TEST", CLOSE_MSG);
    }

    @Test
    public void writeWithSimpleHandshake() throws IOException {
        // map all data to be read and written
        engineMock.addWrapEntry(HANDSHAKE_MSG, "handshake");
        engineMock.addWrapEntry("MockTest", "mock test works!");
        engineMock.addWrapEntry(CLOSE_MSG, "channel closed");
        // set the handshake actions that engineMock will emulate
        engineMock.setHandshakeActions(NEED_WRAP, NEED_UNWRAP, NEED_TASK, FINISH);
        // set ReadData on conduitMock
        conduitMock.setReadData("handshake", "channel closed");
        conduitMock.enableFlush(false);
        // message we want to write
        final ByteBuffer buffer = ByteBuffer.allocate(100);
        final ByteBuffer[] buffers = new ByteBuffer[]{buffer};
        buffer.put("MockTest".getBytes("UTF-8")).flip();

        // attempt to write... channel is expected to stop on NEED_WRAP, as flush is disabled on conduitMock
        assertEquals(0, sslChannel.write(buffers, 0, 1));
        assertFalse(conduitMock.isFlushed());
        conduitMock.enableFlush(true);
        assertSame(HandshakeStatus.NEED_UNWRAP, engineMock.getHandshakeStatus());
        // attempt to write... channel is expected to stop on NEED_UNWRAP, as read on conduitMock is not available
        assertEquals(0, sslChannel.write(buffers, 0, 1));
        assertSame(HandshakeStatus.NEED_UNWRAP, engineMock.getHandshakeStatus());
        assertTrue(conduitMock.isFlushed());

        // enable read, now read data will be available to JsseConnectedSslStreamChannel
        conduitMock.enableReads(true);
        // channel is expected to write all data from buffer
        assertEquals(8, sslChannel.write(new ByteBuffer[]{buffer, ByteBuffer.allocate(0)}, 0, 2));
        assertFalse(buffer.hasRemaining());

        // for coverage purposes, attempt to write empty buffers
        assertEquals(0, sslChannel.write(new ByteBuffer[]{buffer, ByteBuffer.allocate(0)}, 0, 2));

        // channel should be able to shutdown writes
        sslChannel.shutdownWrites();
        assertTrue(sslChannel.flush());
        // close channel
        sslChannel.close();
        // data expected to have been written to 'conduitMock' by 'sslChannel'
        assertWrittenMessage("handshake", "mock test works!", "channel closed");
    }

    @Test
    public void writeWithTasklessHandshake() throws IOException {
        // map data to be read and written
        engineMock.addWrapEntry("MockTest", "{testWriteWithTasklessHandshake}");
        engineMock.addWrapEntry(CLOSE_MSG, " _ ");

        // set the handshake actions that engineMock will emulate
        engineMock.setHandshakeActions(NEED_WRAP, NEED_UNWRAP, FINISH);
        // set ReadData on conduitMock
        conduitMock.setReadData(SSLEngineMock.HANDSHAKE_MSG, " _ ");
        // message we want to write
        final ByteBuffer buffer = ByteBuffer.allocate(100);
        buffer.put("MockTest".getBytes("UTF-8")).flip();

        // attempt to write... channel is expected to stop on NEED_UNWRAP, as read on conduitMock is not available
        assertEquals(0, sslChannel.write(buffer));
        assertSame(HandshakeStatus.NEED_UNWRAP, engineMock.getHandshakeStatus());

        // enable read, now read data will be available to JsseConnectedSslStreamChannel
        conduitMock.enableReads(true);
        // channel is expected to write all data from buffer
        assertEquals(8, sslChannel.write(buffer));
        assertFalse(buffer.hasRemaining());

        // channel should be able to shutdown writes
        sslChannel.shutdownWrites();
        assertTrue(sslChannel.flush());
        // close channel
        sslChannel.close();
        // data expected to have been written to 'conduitMock' by 'sslChannel'
        assertWrittenMessage(HANDSHAKE_MSG, "{testWriteWithTasklessHandshake}", " _ ");
    }

    @Test
    public void multipleWritesWithSimpleHandshake() throws IOException {
        // map data to be read and written
        engineMock.addWrapEntry(HANDSHAKE_MSG, "{handshake data}");
        engineMock.addWrapEntry("MockTest", "{data}");
        engineMock.addWrapEntry(CLOSE_MSG, "{message closed}");
        // set the handshake actions that engineMock will emulate
        engineMock.setHandshakeActions(NEED_WRAP, NEED_UNWRAP, NEED_TASK, FINISH);
        // set ReadData on conduitMock
        conduitMock.setReadData("{handshake data}");
        // enable read on conduitMock, meaning that data above will be available to be read right away
        conduitMock.enableReads(true);
        // message we want to write
        final ByteBuffer buffer = ByteBuffer.allocate(100);
        buffer.put("MockTest".getBytes("UTF-8")).flip();

        // attempt to write... channel is expected to write all messages without any issues
        for (int i = 0; i < 10; i++) {
            while(buffer.hasRemaining()) {
                assertEquals(8, sslChannel.write(buffer));
                assertFalse(buffer.hasRemaining());
            }
            buffer.flip();
        }

        // channel should not be able to shutdown writes... for that, it must receive a close message
        sslChannel.shutdownWrites();
        assertFalse(sslChannel.flush());
        // send the close message
        conduitMock.setReadData("{message closed}");
        conduitMock.enableReads(true);
        sslChannel.shutdownWrites();
        assertTrue(sslChannel.flush());

        // close channel
        sslChannel.close();
        // data expected to have been written to 'conduitMock' by 'sslChannel'
        assertWrittenMessage("{handshake data}", "{data}", "{data}", "{data}", "{data}", "{data}", "{data}", "{data}",
                "{data}", "{data}", "{data}", "{message closed}");
    }

    @Test
    public void writeWithConnectionWithoutReadData() throws IOException {
        // set the handshake actions that engineMock will emulate
        engineMock.setHandshakeActions(NEED_WRAP, NEED_UNWRAP, NEED_TASK, FINISH);
        // message we want to write
        final ByteBuffer buffer = ByteBuffer.allocate(100);
        buffer.put("MockTest".getBytes("UTF-8")).flip();
        // attempt to write several times... channel is expected to stop on NEED_UNWRAP, as read on conduitMock is disabled
        assertEquals(0, sslChannel.write(buffer));
        assertEquals(0, sslChannel.write(buffer));
        assertEquals(0, sslChannel.write(buffer));
        // make sure that channel managed to do the WRAP and got stalled on NEED_UNWRAP handshake status
        assertSame(HandshakeStatus.NEED_UNWRAP, engineMock.getHandshakeStatus());
        // channel should not be able to shutdown writes... for that, it must receive a close message
        sslChannel.shutdownWrites();
        assertFalse(sslChannel.flush());
        // close channel
        sslChannel.close();
        // data expected to have been written to 'conduitMock' by 'sslChannel'
        assertWrittenMessage(HANDSHAKE_MSG, CLOSE_MSG);
    }

    @Test
    public void writeWithConstantHandshake() throws IOException {
        // set the handshake actions that engineMock will emulate
        engineMock.setHandshakeActions(NEED_WRAP, NEED_UNWRAP, NEED_TASK, FINISH, PERFORM_REQUESTED_ACTION,
                NEED_UNWRAP, NEED_WRAP, NEED_TASK, FINISH, PERFORM_REQUESTED_ACTION,
                NEED_WRAP, NEED_WRAP, NEED_WRAP, NEED_UNWRAP, NEED_TASK, NEED_TASK, NEED_TASK, NEED_TASK, FINISH,
                PERFORM_REQUESTED_ACTION, NEED_WRAP, NEED_UNWRAP, NEED_TASK, FINISH, PERFORM_REQUESTED_ACTION);
        // set ReadData on conduitMock
        conduitMock.setReadData(HANDSHAKE_MSG, HANDSHAKE_MSG, HANDSHAKE_MSG, HANDSHAKE_MSG, CLOSE_MSG);
        // enable read on conduitMock, meaning that data above will be available to be read right away
        conduitMock.enableReads(true);
        // messages we plan to write
        final ByteBuffer buffer1 = ByteBuffer.allocate(10);
        buffer1.put("MockTest1".getBytes("UTF-8")).flip();
        final ByteBuffer buffer2 = ByteBuffer.allocate(10);
        buffer2.put("MockTest2".getBytes("UTF-8")).flip();
        final ByteBuffer buffer3 = ByteBuffer.allocate(10);
        buffer3.put("MockTest3".getBytes("UTF-8")).flip();
        final ByteBuffer buffer4 = ByteBuffer.allocate(10);
        buffer4.put("MockTest4".getBytes("UTF-8")).flip();
        // attempt to write... channel is expected to write all messages without any issues despite the constant handshaking action
        assertEquals(9, sslChannel.write(buffer1));
        assertFalse(buffer1.hasRemaining());
        assertSame(HandshakeStatus.NEED_UNWRAP, engineMock.getHandshakeStatus()); // next handshake action is NEED_UNWRAP
        assertEquals(9, sslChannel.write(buffer2));
        assertFalse(buffer2.hasRemaining());
        assertSame(HandshakeStatus.NEED_WRAP, engineMock.getHandshakeStatus()); // next handshake action is NEED_WRAP
        assertEquals(9, sslChannel.write(buffer3));
        assertFalse(buffer3.hasRemaining());
        assertSame(HandshakeStatus.NEED_WRAP, engineMock.getHandshakeStatus()); // next handshake action is NEED_WRAP
        assertEquals(9, sslChannel.write(buffer4));
        assertFalse(buffer4.hasRemaining());
        // make sure that channel managed to do the WRAP and there is no more handshake actions left
        assertSame(HandshakeStatus.NOT_HANDSHAKING, engineMock.getHandshakeStatus());
        // channel should be able to shutdown writes
        sslChannel.shutdownWrites();
        assertTrue(sslChannel.flush());
        // close channel
        sslChannel.close();
        // data expected to have been written to 'conduitMock' by 'sslChannel'
        assertWrittenMessage(HANDSHAKE_MSG, "MockTest1", HANDSHAKE_MSG, "MockTest2", HANDSHAKE_MSG, HANDSHAKE_MSG,
                HANDSHAKE_MSG, "MockTest3", HANDSHAKE_MSG, "MockTest4", CLOSE_MSG);
    }

    @Test
    public void writeWithConstantHandshakeAndMappedData() throws IOException {
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
                PERFORM_REQUESTED_ACTION, NEED_WRAP, NEED_UNWRAP, NEED_TASK, FINISH, PERFORM_REQUESTED_ACTION);
        // set ReadData on conduitMock
        conduitMock.setReadData("HANDSHAKE_MSG", "HANDSHAKE_MSG", "HANDSHAKE_MSG", "HANDSHAKE_MSG", "CLOSE_MSG");
        // enable read on conduitMock, meaning that data above will be available to be read right away
        conduitMock.enableReads(true);
        // messages we plan to write
        final ByteBuffer buffer1 = ByteBuffer.allocate(10);
        buffer1.put("MockTest1".getBytes("UTF-8")).flip();
        final ByteBuffer buffer2 = ByteBuffer.allocate(10);
        buffer2.put("MockTest2".getBytes("UTF-8")).flip();
        final ByteBuffer buffer3 = ByteBuffer.allocate(10);
        buffer3.put("MockTest3".getBytes("UTF-8")).flip();
        final ByteBuffer buffer4 = ByteBuffer.allocate(10);
        buffer4.put("MockTest4".getBytes("UTF-8")).flip();
        // attempt to write... channel is expected to write all messages without any issues despite the constant handshaking action
        assertEquals(9, sslChannel.write(buffer1));
        assertFalse(buffer1.hasRemaining());
        assertSame(HandshakeStatus.NEED_UNWRAP, engineMock.getHandshakeStatus()); // next handshake action is NEED_UNWRAP
        assertEquals(9, sslChannel.write(buffer2));
        assertFalse(buffer2.hasRemaining());
        assertSame(HandshakeStatus.NEED_WRAP, engineMock.getHandshakeStatus()); // next handshake action is NEED_WRAP
        assertEquals(9, sslChannel.write(buffer3));
        assertFalse(buffer3.hasRemaining());
        assertSame(HandshakeStatus.NEED_WRAP, engineMock.getHandshakeStatus()); // next handshake action is NEED_WRAP
        assertEquals(9, sslChannel.write(buffer4));
        assertFalse(buffer4.hasRemaining());
        // make sure that channel managed to do the WRAP and there is no more handshake actions left
        assertSame(HandshakeStatus.NOT_HANDSHAKING, engineMock.getHandshakeStatus());
        // channel should be able to shutdown writes
        sslChannel.shutdownWrites();
        assertTrue(sslChannel.flush());
        // close channel
        sslChannel.close();
        // data expected to have been written to 'conduitMock' by 'sslChannel'
        assertWrittenMessage("HANDSHAKE_MSG", "MOCK 1", "HANDSHAKE_MSG", "MOCK 2", "HANDSHAKE_MSG", "HANDSHAKE_MSG",
                "HANDSHAKE_MSG", "MOCK 3", "HANDSHAKE_MSG", "MOCK 4", "CLOSE_MSG");
    }

    @Test
    public void writeWithIntercalatedHandshake() throws IOException {
        // set the handshake actions that engineMock will emulate
        engineMock.setHandshakeActions(NEED_WRAP, NEED_UNWRAP, NEED_TASK, FINISH, PERFORM_REQUESTED_ACTION,
                NEED_WRAP, PERFORM_REQUESTED_ACTION, NEED_WRAP, PERFORM_REQUESTED_ACTION, NEED_WRAP, PERFORM_REQUESTED_ACTION,
                NEED_UNWRAP, PERFORM_REQUESTED_ACTION, PERFORM_REQUESTED_ACTION, NEED_UNWRAP, PERFORM_REQUESTED_ACTION,
                NEED_TASK, PERFORM_REQUESTED_ACTION, PERFORM_REQUESTED_ACTION, FINISH);
        // set ReadData on conduitMock
        conduitMock.setReadData(HANDSHAKE_MSG, HANDSHAKE_MSG, HANDSHAKE_MSG, CLOSE_MSG);
        // enable read on conduitMock, meaning that data above will be available to be read right away
        conduitMock.enableReads(true);
        // message we plan to write
        final ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put("write this".getBytes("UTF-8")).flip();
        // attempt to write... channel is expected to write all messages without any issues despite the constant handshaking action
        for (int i = 0; i < 10; i++) {
            assertEquals("Failed at attempt to write number " + i, 10, sslChannel.write(buffer));
            assertFalse(buffer.hasRemaining());
            buffer.flip();
        }
        // make sure that channel managed to do the WRAP and there is no more handshake actions left
        assertSame(HandshakeStatus.NOT_HANDSHAKING, engineMock.getHandshakeStatus());
        // channel should be able to shutdown writes
        sslChannel.shutdownWrites();
        assertTrue(sslChannel.flush());
        // close channel
        sslChannel.close();
        // data expected to have been written to 'conduitMock' by 'sslChannel'
        assertWrittenMessage(HANDSHAKE_MSG, "write this", HANDSHAKE_MSG, "write this",
                HANDSHAKE_MSG, "write this", HANDSHAKE_MSG, "write this", "write this", "write this", "write this",
                "write this", "write this", "write this", CLOSE_MSG);
    }

    @Test
    public void writeWithIntercalatedHandshakeAndMappedData() throws IOException {
        // map all data to be read and written
        engineMock.addWrapEntry(HANDSHAKE_MSG, "[!@#$%^&*()_]");
        engineMock.addWrapEntry("write this", "this");
        engineMock.addWrapEntry(CLOSE_MSG, "[_)(*&^%$#@!]");

        // set the handshake actions that engineMock will emulate
        engineMock.setHandshakeActions(NEED_WRAP, NEED_UNWRAP, NEED_TASK, FINISH, PERFORM_REQUESTED_ACTION,
                NEED_WRAP, PERFORM_REQUESTED_ACTION, NEED_WRAP, PERFORM_REQUESTED_ACTION, NEED_WRAP, PERFORM_REQUESTED_ACTION,
                NEED_UNWRAP, PERFORM_REQUESTED_ACTION, PERFORM_REQUESTED_ACTION, NEED_UNWRAP, PERFORM_REQUESTED_ACTION,
                NEED_TASK, PERFORM_REQUESTED_ACTION, PERFORM_REQUESTED_ACTION, FINISH);
        // set ReadData on conduitMock
        conduitMock.setReadData("[!@#$%^&*()_]", "[!@#$%^&*()_]", "[!@#$%^&*()_]", "[_)(*&^%$#@!]");
        // enable read on conduitMock, meaning that data above will be available to be read right away
        conduitMock.enableReads(true);
        // message we plan to write
        final ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put("write this".getBytes("UTF-8")).flip();
        // attempt to write... channel is expected to write all messages without any issues despite the constant handshaking action
        for (int i = 0; i < 10; i++) {
            assertEquals("Failed at attempt to write number " + i, 10, sslChannel.write(buffer));
            assertFalse(buffer.hasRemaining());
            buffer.flip();
        }
        // make sure that channel managed to do the WRAP and there is no more handshake actions left
        assertSame(HandshakeStatus.NOT_HANDSHAKING, engineMock.getHandshakeStatus());
        // channel should be able to shutdown writes
        sslChannel.shutdownWrites();
        assertTrue(sslChannel.flush());
        // close channel
        sslChannel.close();
        // data expected to have been written to 'conduitMock' by 'sslChannel'
        assertWrittenMessage("[!@#$%^&*()_]", "this", "[!@#$%^&*()_]", "this", "[!@#$%^&*()_]", "this", "[!@#$%^&*()_]",
                "this", "this", "this", "this", "this", "this", "this", "[_)(*&^%$#@!]");
    }

    @Test
    public void attemptToWriteWithFaultyTask() throws IOException {
        // set the handshake actions that engineMock will emulate
        engineMock.setHandshakeActions(NEED_FAULTY_TASK);
        // message we plan to write
        final ByteBuffer buffer = ByteBuffer.allocate(21);
        buffer.put("write this if you can".getBytes("UTF-8")).flip();
        // try to write a bunch of times, we will get an IOException at all of the times
        for (int i = 0; i < 10; i ++) {
            boolean failed = false;
            try {
                sslChannel.write(buffer);
            } catch (IOException expected) {
                failed = true;
            }
            assertTrue(failed);
        }
    }

    // FIXME @Test
    public void closeWithoutFlushing() throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put("abc".getBytes("UTF-8")).flip();
        sslChannel.write(buffer);
        sslChannel.close();
        assertWrittenMessage("abc");
    }
}
