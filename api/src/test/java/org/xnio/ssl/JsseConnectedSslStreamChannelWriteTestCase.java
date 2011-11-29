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
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
@RunWith(JMock.class)
public class JsseConnectedSslStreamChannelWriteTestCase extends AbstractJsseConnectedSslStreamChannelTest {

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
        assertFalse(sslChannel.shutdownWrites());
        // send the close message
        connectedChannelMock.setReadData(CLOSE_MSG);
        connectedChannelMock.enableRead(true);
        assertTrue(sslChannel.shutdownWrites());

        // close channel
        sslChannel.close();
        // data expected to have been written to 'connectedChannelMock' by 'sslChannel'
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
        assertFalse(sslChannel.shutdownWrites());
        // send the close message
        connectedChannelMock.setReadData(CLOSE_MSG);
        connectedChannelMock.enableRead(true);
        assertTrue(sslChannel.shutdownWrites());

        // close channel
        sslChannel.close();
        // data expected to have bee written to 'connectedChannelMock' by 'sslChannel'
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
        // set ReadData on connectedChannelMock
        connectedChannelMock.setReadData("handshake", "channel closed");
        // message we want to write
        final ByteBuffer buffer = ByteBuffer.allocate(100);
        buffer.put("MockTest".getBytes("UTF-8")).flip();

        // attempt to write... channel is expected to stop on NEED_UNWRAP, as read on connectedChannelMock is not available
        assertEquals(0, sslChannel.write(buffer));
        assertSame(HandshakeStatus.NEED_UNWRAP, engineMock.getHandshakeStatus());

        // enable read, now read data will be available to JsseConnectedSslStreamChannel
        connectedChannelMock.enableRead(true);
        // channel is expected to write all data from buffer
        assertEquals(8, sslChannel.write(buffer));
        assertFalse(buffer.hasRemaining());

        // channel should be able to shutdown writes
        assertTrue(sslChannel.shutdownWrites());
        // close channel
        sslChannel.close();
        // data expected to have been written to 'connectedChannelMock' by 'sslChannel'
        assertWrittenMessage("handshake", "mock test works!", "channel closed");
    }

    @Test
    public void writeWithTasklessHandshake() throws IOException {
        // map data to be read and written
        engineMock.addWrapEntry("MockTest", "{testWriteWithTasklessHandshake}");
        engineMock.addWrapEntry(CLOSE_MSG, " _ ");

        // set the handshake actions that engineMock will emulate
        engineMock.setHandshakeActions(NEED_WRAP, NEED_UNWRAP, FINISH);
        // set ReadData on connectedChannelMock
        connectedChannelMock.setReadData(SSLEngineMock.HANDSHAKE_MSG, " _ ");
        // message we want to write
        final ByteBuffer buffer = ByteBuffer.allocate(100);
        buffer.put("MockTest".getBytes("UTF-8")).flip();

        // attempt to write... channel is expected to stop on NEED_UNWRAP, as read on connectedChannelMock is not available
        assertEquals(0, sslChannel.write(buffer));
        assertSame(HandshakeStatus.NEED_UNWRAP, engineMock.getHandshakeStatus());

        // enable read, now read data will be available to JsseConnectedSslStreamChannel
        connectedChannelMock.enableRead(true);
        // channel is expected to write all data from buffer
        assertEquals(8, sslChannel.write(buffer));
        assertFalse(buffer.hasRemaining());

        // channel should be able to shutdown writes
        assertTrue(sslChannel.shutdownWrites());
        // close channel
        sslChannel.close();
        // data expected to have been written to 'connectedChannelMock' by 'sslChannel'
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
        // set ReadData on connectedChannelMock
        connectedChannelMock.setReadData("{handshake data}");
        // enable read on connectedChannelMock, meaning that data above will be available to be read right away
        connectedChannelMock.enableRead(true);
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
        assertFalse(sslChannel.shutdownWrites());
        // send the close message
        connectedChannelMock.setReadData("{message closed}");
        connectedChannelMock.enableRead(true);
        assertTrue(sslChannel.shutdownWrites());

        // close channel
        sslChannel.close();
        // data expected to have been written to 'connectedChannelMock' by 'sslChannel'
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
        // attempt to write several times... channel is expected to stop on NEED_UNWRAP, as read on connectedChannelMock is disabled
        assertEquals(0, sslChannel.write(buffer));
        assertEquals(0, sslChannel.write(buffer));
        assertEquals(0, sslChannel.write(buffer));
        // make sure that channel managed to do the WRAP and got stalled on NEED_UNWRAP handshake status
        assertSame(HandshakeStatus.NEED_UNWRAP, engineMock.getHandshakeStatus());
        // channel should not be able to shutdown writes... for that, it must receive a close message
        assertFalse(sslChannel.shutdownWrites());
        // close channel
        sslChannel.close();
        // data expected to have been written to 'connectedChannelMock' by 'sslChannel'
        assertWrittenMessage(HANDSHAKE_MSG, CLOSE_MSG);
    }

    @Test
    public void writeWithConstantHandshake() throws IOException {
        // set the handshake actions that engineMock will emulate
        engineMock.setHandshakeActions(NEED_WRAP, NEED_UNWRAP, NEED_TASK, FINISH, PERFORM_REQUESTED_ACTION,
                NEED_UNWRAP, NEED_WRAP, NEED_TASK, FINISH, PERFORM_REQUESTED_ACTION,
                NEED_WRAP, NEED_WRAP, NEED_WRAP, NEED_UNWRAP, NEED_TASK, NEED_TASK, NEED_TASK, NEED_TASK, FINISH,
                PERFORM_REQUESTED_ACTION, NEED_WRAP, NEED_UNWRAP, NEED_TASK, FINISH, PERFORM_REQUESTED_ACTION);
        // set ReadData on connectedChannelMock
        connectedChannelMock.setReadData(HANDSHAKE_MSG, HANDSHAKE_MSG, HANDSHAKE_MSG, HANDSHAKE_MSG, CLOSE_MSG);
        // enable read on connectedChannelMock, meaning that data above will be available to be read right away
        connectedChannelMock.enableRead(true);
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
        assertTrue(sslChannel.shutdownWrites());
        // close channel
        sslChannel.close();
        // data expected to have been written to 'connectedChannelMock' by 'sslChannel'
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
        // set ReadData on connectedChannelMock
        connectedChannelMock.setReadData("HANDSHAKE_MSG", "HANDSHAKE_MSG", "HANDSHAKE_MSG", "HANDSHAKE_MSG", "CLOSE_MSG");
        // enable read on connectedChannelMock, meaning that data above will be available to be read right away
        connectedChannelMock.enableRead(true);
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
        assertTrue(sslChannel.shutdownWrites());
        // close channel
        sslChannel.close();
        // data expected to have been written to 'connectedChannelMock' by 'sslChannel'
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
        // set ReadData on connectedChannelMock
        connectedChannelMock.setReadData(HANDSHAKE_MSG, HANDSHAKE_MSG, HANDSHAKE_MSG, CLOSE_MSG);
        // enable read on connectedChannelMock, meaning that data above will be available to be read right away
        connectedChannelMock.enableRead(true);
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
        assertTrue(sslChannel.shutdownWrites());
        // close channel
        sslChannel.close();
        // data expected to have been written to 'connectedChannelMock' by 'sslChannel'
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
        // set ReadData on connectedChannelMock
        connectedChannelMock.setReadData("[!@#$%^&*()_]", "[!@#$%^&*()_]", "[!@#$%^&*()_]", "[_)(*&^%$#@!]");
        // enable read on connectedChannelMock, meaning that data above will be available to be read right away
        connectedChannelMock.enableRead(true);
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
        assertTrue(sslChannel.shutdownWrites());
        // close channel
        sslChannel.close();
        // data expected to have been written to 'connectedChannelMock' by 'sslChannel'
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
}
