/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2011 Red Hat, Inc. and/or its affiliates, and individual
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
 * Test for read operations on  {@link #JsseConnectedSslStreamChannel}.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
@RunWith(JMock.class)
public class JsseConnectedSslStreamChannelReadTestCase extends AbstractJsseConnectedSslStreamChannelTest{

    @Test
    public void readWithoutHandshake() throws IOException {
        // no handshake actions for engineMock this time, meaning it will just wrap and unwrap without any handshake
        // the message we want to write
        connectedChannelMock.setReadData("MockTest", CLOSE_MSG);
        connectedChannelMock.enableRead(true);
        final ByteBuffer buffer = ByteBuffer.allocate(100);
        // attempt to read... channel is expected to read the entire message without any issues
        assertEquals(8, sslChannel.read(buffer));
        // shutdown reads
        sslChannel.shutdownReads();
        // close channel
        sslChannel.close();
        // data expected to have been copied to buffer by channel
        assertReadMessage(buffer, "MockTest");
    }

    @Test
    public void readWithoutHandshakeMappedUnwrap() throws IOException {
        // map the wrap
        engineMock.addWrapEntry("MockTest", "WRAPPED_MOCK_TEST");
        // no handshake actions for engineMock this time, meaning it will just wrap and unwrap without any handshake
        // the message we want to read
        connectedChannelMock.setReadData("WRAPPED_MOCK_TEST", CLOSE_MSG);
        connectedChannelMock.enableRead(true);
        // attempt to read... channel is expected to read the entire message without any handshake issues
        final ByteBuffer buffer = ByteBuffer.allocate(100);
        assertEquals(8, sslChannel.read(buffer)); 
        // shutdown reads
        sslChannel.shutdownReads();
        // close channel
        sslChannel.close();
        // data expected to have been read to 'buffer' by 'sslChannel'
        assertReadMessage(buffer, "MockTest");
    }

    @Test
    public void readWithSimpleHandshake() throws IOException {
        // map all data to be read and written
        engineMock.addWrapEntry(HANDSHAKE_MSG, "handshake");
        engineMock.addWrapEntry("MockTest", "mock test works!");
        engineMock.addWrapEntry(CLOSE_MSG, "channel closed");
        // set the handshake actions that engineMock will emulate
        engineMock.setHandshakeActions(NEED_WRAP, NEED_UNWRAP, NEED_TASK, FINISH);
        // set ReadData on connectedChannelMock, including the wrapped version of message we want to read
        connectedChannelMock.setReadData("handshake", "mock test works!", "channel closed");
        connectedChannelMock.enableRead(true);

        // channel is expected to read all data from connectedChannelMock
        final ByteBuffer buffer = ByteBuffer.allocate(100);
        assertEquals(8, sslChannel.read(buffer));

        // shutdown reads
        sslChannel.shutdownReads();
        // close channel
        sslChannel.close();

        assertReadMessage(buffer, "MockTest");
        // data expected to have been written to 'connectedChannelMock' by 'sslChannel'
        assertWrittenMessage("handshake", "channel closed");
    }

    @Test
    public void readWithTasklessHandshake() throws IOException {
        // map data to be read and written
        engineMock.addWrapEntry("Mock Test", "{testReadWithTasklessHandshake}");
        engineMock.addWrapEntry(CLOSE_MSG, " _ ");

        // set the handshake actions that engineMock will emulate
        engineMock.setHandshakeActions(NEED_WRAP, NEED_UNWRAP, FINISH);
        // set ReadData on connectedChannelMock
        connectedChannelMock.setReadData(SSLEngineMock.HANDSHAKE_MSG, "{testReadWithTasklessHandshake}", " _ ");
        connectedChannelMock.enableRead(true);

        // channel is expected to read "Mock Test" from connectedChannelMock
        final ByteBuffer buffer = ByteBuffer.allocate(100);
        assertEquals(9, sslChannel.read(buffer));

        // channel should be able to shutdown writes
        sslChannel.shutdownReads();
        // close channel
        sslChannel.close();
        // data expected to have been read from 'connectedChannelMock' by 'sslChannel'
        assertReadMessage(buffer, "Mock Test");

        // data expected to have been written to 'connectedChannelMock' by 'sslChannel'
        assertWrittenMessage(HANDSHAKE_MSG, " _ ");
    }

    @Test
    public void multipleReadsWithSimpleHandshake() throws IOException {
        // map data to be read and written
        engineMock.addWrapEntry(HANDSHAKE_MSG, "{handshake data}");
        engineMock.addWrapEntry("Mock Read Test", "{data}");
        engineMock.addWrapEntry("it works!", "{more data}");
        engineMock.addWrapEntry(CLOSE_MSG, "{message closed}");
        // set the handshake actions that engineMock will emulate
        engineMock.setHandshakeActions(NEED_WRAP, NEED_UNWRAP, NEED_TASK, FINISH);
        // set ReadData on connectedChannelMock
        connectedChannelMock.setReadData("{handshake data}", "{data}", "{data}", "{more data}");
        // enable read on connectedChannelMock, meaning that data above will be available to be read right away
        connectedChannelMock.enableRead(true);
        // try to read message
        final ByteBuffer buffer = ByteBuffer.allocate(30);
        assertEquals(30, sslChannel.read(buffer));
        // data expected to have been read from 'connectedChannelMock' by 'sslChannel' so far
        assertReadMessage(buffer, "Mock Read Test", "Mock Read Test", "it");
        buffer.clear();

        // set more read data
        connectedChannelMock.setReadData("{data}", "{more data}");
        connectedChannelMock.enableRead(true);
        // and read again
        assertEquals(30, sslChannel.read(buffer));
        // data expected to have been read from 'connectedChannelMock' by 'sslChannel'
        assertReadMessage(buffer,  " works!", "Mock Read Test", "it works!");
        buffer.clear();

        // set more read data
        connectedChannelMock.setReadData("{more data}", "{message closed}");
        connectedChannelMock.enableRead(true);
        // and read again
        assertEquals(9, sslChannel.read(buffer));

        // data expected to have been read from 'connectedChannelMock' by 'sslChannel'
        assertReadMessage(buffer, "it works!");
        buffer.clear();

        // channel should be able to shutdown reads
        sslChannel.shutdownReads();
        // close channel
        sslChannel.close();

        // data expected to have been written to 'connectedChannelMock' by 'sslChannel'
        assertWrittenMessage("{handshake data}", "{message closed}");
    }

    @Test
    public void readWithAbruptClose() throws IOException {
        // set the handshake actions that engineMock will emulate
        engineMock.setHandshakeActions(NEED_UNWRAP, NEED_WRAP, NEED_UNWRAP, NEED_WRAP, NEED_TASK, FINISH);
        // message buffer
        final ByteBuffer buffer = ByteBuffer.allocate(100);

        connectedChannelMock.setReadData(HANDSHAKE_MSG, CLOSE_MSG);
        connectedChannelMock.enableRead(true);
        // attempt to read several times... channel is expected to stop on the second NEED_UNWRAP actoin, as
        // connectedChannelMock will read an abrupt CLOSE_MSG
        assertEquals(-1, sslChannel.read(buffer));
        assertEquals(-1, sslChannel.read(buffer));
        assertEquals(-1, sslChannel.read(buffer));
        // channel should be able to shutdown writes
        sslChannel.shutdownReads();
        // close channel
        sslChannel.close();
        // data expected to have been written to 'connectedChannelMock' by 'sslChannel'
        assertWrittenMessage(HANDSHAKE_MSG, CLOSE_MSG);
    }

    @Test
    public void readWithConstantHandshake() throws IOException {
        // set the handshake actions that engineMock will emulate
        engineMock.setHandshakeActions(NEED_WRAP, NEED_UNWRAP, NEED_TASK, FINISH, PERFORM_REQUESTED_ACTION,
                NEED_UNWRAP, NEED_WRAP, NEED_TASK, FINISH, PERFORM_REQUESTED_ACTION,
                NEED_WRAP, NEED_WRAP, NEED_WRAP, NEED_UNWRAP, NEED_TASK, NEED_TASK, NEED_TASK, NEED_TASK, FINISH,
                PERFORM_REQUESTED_ACTION, NEED_WRAP, NEED_UNWRAP, NEED_TASK, FINISH, PERFORM_REQUESTED_ACTION);
        // set ReadData on connectedChannelMock
        connectedChannelMock.setReadData(HANDSHAKE_MSG, "read a lot", HANDSHAKE_MSG, " read a lot ",
                HANDSHAKE_MSG, "read a lot ", HANDSHAKE_MSG, " read a lot", CLOSE_MSG);
        // enable read on connectedChannelMock, meaning that data above will be available to be read right away
        connectedChannelMock.enableRead(true);
        // try to read message
        final ByteBuffer buffer = ByteBuffer.allocate(10);
        assertEquals(10, sslChannel.read(buffer));
        assertReadMessage(buffer, "read a lot");
        buffer.clear();

        assertEquals(10, sslChannel.read(buffer));
        assertReadMessage(buffer, " read a lo");
        buffer.clear();

        assertEquals(10, sslChannel.read(buffer));
        assertReadMessage(buffer, "t read a l");
        buffer.clear();

        assertEquals(10, sslChannel.read(buffer));
        assertReadMessage(buffer, "ot  read a");
        buffer.clear();

        assertEquals(4, sslChannel.read(buffer));
        assertReadMessage(buffer, " lot");

        // make sure that channel managed to do the WRAP and there is no more handshake actions left
        assertSame(HandshakeStatus.NOT_HANDSHAKING, engineMock.getHandshakeStatus());
        // shutdown reads
        sslChannel.shutdownReads();
        // close channel
        sslChannel.close();
        // data expected to have been written to 'connectedChannelMock' by 'sslChannel'
        assertWrittenMessage(HANDSHAKE_MSG, HANDSHAKE_MSG, HANDSHAKE_MSG, HANDSHAKE_MSG, HANDSHAKE_MSG, HANDSHAKE_MSG,
                CLOSE_MSG);
    }

    @Test
    public void readWithConstantHandshakeAndMappedData() throws IOException {
        // map data to be read and written
        engineMock.addWrapEntry(HANDSHAKE_MSG, "HANDSHAKE_MSG");
        engineMock.addWrapEntry("a lot", "read a lot");
        engineMock.addWrapEntry("read", "read");
        engineMock.addWrapEntry("lot", "a lot");
        engineMock.addWrapEntry("nothing", "read nothing");
        engineMock.addWrapEntry(CLOSE_MSG, "CLOSE_MSG");
        // set the handshake actions that engineMock will emulate
        engineMock.setHandshakeActions(NEED_WRAP, NEED_UNWRAP, NEED_TASK, FINISH, PERFORM_REQUESTED_ACTION,
                NEED_UNWRAP, NEED_WRAP, NEED_TASK, FINISH, PERFORM_REQUESTED_ACTION,
                NEED_WRAP, NEED_WRAP, NEED_WRAP, NEED_UNWRAP, NEED_TASK, NEED_TASK, NEED_TASK, NEED_TASK, FINISH,
                PERFORM_REQUESTED_ACTION, NEED_WRAP, NEED_UNWRAP, NEED_TASK, FINISH, PERFORM_REQUESTED_ACTION);
        // set ReadData on connectedChannelMock
        connectedChannelMock.setReadData("HANDSHAKE_MSG", "read a lot", "a lot", "read", "HANDSHAKE_MSG", "a lot",
                "read a lot", "HANDSHAKE_MSG", "read nothing", "read a lot", "HANDSHAKE_MSG", "read nothing",
                "CLOSE_MSG");
        // enable read on connectedChannelMock, meaning that data above will be available to be read right away
        connectedChannelMock.enableRead(true);
        // try to read message
        final ByteBuffer buffer = ByteBuffer.allocate(10);
        assertEquals(10, sslChannel.read(buffer));
        assertReadMessage(buffer, "a lot", "lot", "re");
        buffer.clear();

        assertEquals(10, sslChannel.read(buffer));
        assertReadMessage(buffer, "ad", "lot", "a lot");
        buffer.clear();

        assertEquals(10, sslChannel.read(buffer));
        assertReadMessage(buffer, "nothing", "a l");
        buffer.clear();

        assertEquals(9, sslChannel.read(buffer));
        assertReadMessage(buffer, "ot", "nothing");
        buffer.clear();

        // make sure that channel managed to do the WRAP and there is no more handshake actions left
        assertSame(HandshakeStatus.NOT_HANDSHAKING, engineMock.getHandshakeStatus());
        // shutdown reads
        sslChannel.shutdownReads();
        // close channel
        sslChannel.close();
        // data expected to have been written to 'connectedChannelMock' by 'sslChannel'
        assertWrittenMessage("HANDSHAKE_MSG", "HANDSHAKE_MSG", "HANDSHAKE_MSG", "HANDSHAKE_MSG", "HANDSHAKE_MSG",
                "HANDSHAKE_MSG", "CLOSE_MSG");
    }

    @Test
    public void readWithIntercalatedHandshake() throws IOException {
        // set the handshake actions that engineMock will emulate
        engineMock.setHandshakeActions(NEED_WRAP, NEED_UNWRAP, NEED_TASK, FINISH, PERFORM_REQUESTED_ACTION,
                NEED_WRAP, NEED_UNWRAP, PERFORM_REQUESTED_ACTION, NEED_UNWRAP, PERFORM_REQUESTED_ACTION, FINISH);
        // set ReadData on connectedChannelMock
        connectedChannelMock.setReadData(HANDSHAKE_MSG, "read this", "read this", "read this", "read this",
                HANDSHAKE_MSG, "read this", HANDSHAKE_MSG, "read this", CLOSE_MSG);
        // enable read on connectedChannelMock, meaning that data above will be available to be read right away
        connectedChannelMock.enableRead(true);
        final ByteBuffer buffer = ByteBuffer.allocate(100);
        // attempt to read... channel is expected to read the message 
        assertEquals(54, sslChannel.read(buffer));
        // make sure that channel managed to do the WRAP and there is no more handshake actions left
        assertSame(HandshakeStatus.NOT_HANDSHAKING, engineMock.getHandshakeStatus());
        // shutdown reads
        sslChannel.shutdownReads();
        // close channel
        sslChannel.close();
        // data expected to have been read from 'connectedChannelMock' by 'sslChannel'
        assertReadMessage(buffer, "read this", "read this", "read this", "read this", "read this", "read this");
        // data expected to have been written to 'connectedChannelMock' by 'sslChannel'
        assertWrittenMessage(HANDSHAKE_MSG, HANDSHAKE_MSG, CLOSE_MSG);
    }

    @Test
    public void readWithIntercalatedHandshakeAndMappedData() throws IOException {
        // map all data to be read and written
        engineMock.addWrapEntry(HANDSHAKE_MSG, "[!@#$%^&*()_]");
        engineMock.addWrapEntry("this", "read this");
        engineMock.addWrapEntry(CLOSE_MSG, "[_)(*&^%$#@!]");

        // set the handshake actions that engineMock will emulate
        engineMock.setHandshakeActions(NEED_WRAP, NEED_UNWRAP, NEED_TASK, FINISH, PERFORM_REQUESTED_ACTION,
                NEED_WRAP, NEED_UNWRAP, PERFORM_REQUESTED_ACTION, NEED_UNWRAP, PERFORM_REQUESTED_ACTION, FINISH);
        // set ReadData on connectedChannelMock
        connectedChannelMock.setReadData("[!@#$%^&*()_]", "read this", "read this", "read this", "read this",
                "[!@#$%^&*()_]", "read this", "[!@#$%^&*()_]", "read this", "[_)(*&^%$#@!]");
        // enable read on connectedChannelMock, meaning that data above will be available to be read right away
        connectedChannelMock.enableRead(true);
        final ByteBuffer buffer = ByteBuffer.allocate(100);
        // attempt to read... channel is expected to read the message 
        assertEquals(24, sslChannel.read(buffer));
        // make sure that channel managed to do the WRAP and there is no more handshake actions left
        assertSame(HandshakeStatus.NOT_HANDSHAKING, engineMock.getHandshakeStatus());
        // shutdown reads
        sslChannel.shutdownReads();
        // close channel
        sslChannel.close();
        // data expected to have been read from 'connectedChannelMock' by 'sslChannel'
        assertReadMessage(buffer, "this", "this", "this", "this", "this", "this");
        // data expected to have been written to 'connectedChannelMock' by 'sslChannel'
        assertWrittenMessage("[!@#$%^&*()_]", "[!@#$%^&*()_]", "[_)(*&^%$#@!]");
    }

    @Test
    public void attemptToReadWithFaultyTask() throws IOException {
        // set the handshake actions that engineMock will emulate
        engineMock.setHandshakeActions(NEED_FAULTY_TASK);
        // message we plan to write
        final ByteBuffer buffer = ByteBuffer.allocate(21);
        // try to write a bunch of times, we will get an IOException at all of the times
        for (int i = 0; i < 10; i ++) {
            boolean failed = false;
            try {
                sslChannel.read(buffer);
            } catch (IOException expected) {
                failed = true;
            }
            assertTrue(failed);
        }
    }
}
