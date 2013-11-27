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
import org.jmock.integration.junit4.JUnitRuleMockery;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.ssl.mock.SSLEngineMock;


/**
 * Test for read operations on  {@link #JsseSslStreamSourceConduit}.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 */
public class JsseSslStreamSourceConduitTestCase extends AbstractSslConnectionTest{
    @Rule
    public final JUnitRuleMockery context = new JUnitRuleMockery();

    @Test
    public void readWithoutHandshake() throws IOException {
        // no handshake actions for engineMock this time, meaning it will just wrap and unwrap without any handshake
        // the message we want to write
        conduitMock.setReadData("MockTest", CLOSE_MSG);
        conduitMock.enableReads(true);
        final ByteBuffer buffer = ByteBuffer.allocate(100);
        // attempt to read... sourceConduit is expected to read the entire message without any issues
        assertEquals(8, sourceConduit.read(buffer));
        // terminate reads
        sourceConduit.terminateReads();
        // close connection
        sinkConduit.terminateWrites();
        // data expected to have been copied to buffer by sourceConduit
        assertReadMessage(buffer, "MockTest");
    }

    @Test
    public void readWithoutHandshakeMappedUnwrap() throws IOException {
        // map the wrap
        engineMock.addWrapEntry("MockTest", "WRAPPED_MOCK_TEST");
        // no handshake actions for engineMock this time, meaning it will just wrap and unwrap without any handshake
        // the message we want to read
        conduitMock.setReadData("WRAPPED_MOCK_TEST", CLOSE_MSG);
        conduitMock.enableReads(true);
        // attempt to read... conduit is expected to read the entire message without any handshake issues
        final ByteBuffer buffer = ByteBuffer.allocate(100);
        assertEquals(8, sourceConduit.read(buffer)); 
        // terminate reads
        sourceConduit.terminateReads();
        // close connection
        sinkConduit.terminateWrites();
        // data expected to have been read to 'buffer' by 'sourceConduit'
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
        // set ReadData on conduitMock, including the wrapped version of message we want to read
        conduitMock.setReadData("handshake", "mock test works!", "channel closed");
        conduitMock.enableReads(true);

        // conduit is expected to read all data from conduitMock
        final ByteBuffer buffer = ByteBuffer.allocate(100);
        assertEquals(8, sourceConduit.read(buffer));

        // terminate reads
        sourceConduit.terminateReads();
        // close connection
        sinkConduit.terminateWrites();

        assertReadMessage(buffer, "MockTest");
        // data expected to have been written to 'conduitMock' by 'sourceConduit'
        assertWrittenMessage("handshake", "channel closed");
    }

    @Test
    public void readWithTasklessHandshake() throws IOException {
        // map data to be read and written
        engineMock.addWrapEntry("Mock Test", "{testReadWithTasklessHandshake}");
        engineMock.addWrapEntry(CLOSE_MSG, " _ ");

        // set the handshake actions that engineMock will emulate
        engineMock.setHandshakeActions(NEED_WRAP, NEED_UNWRAP, FINISH);
        // set ReadData on conduitMock
        conduitMock.setReadData(SSLEngineMock.HANDSHAKE_MSG, "{testReadWithTasklessHandshake}", " _ ");
        conduitMock.enableReads(true);

        // conduit is expected to read "Mock Test" from conduitMock
        final ByteBuffer buffer = ByteBuffer.allocate(100);
        assertEquals(9, sourceConduit.read(buffer));

        // conduit should be able to terminate reads
        sourceConduit.terminateReads();
        // close connection
        sinkConduit.terminateWrites();
        // data expected to have been read from 'conduitMock' by 'sourceConduit'
        assertReadMessage(buffer, "Mock Test");

        // data expected to have been written to 'conduitMock' by 'sinkConduit'
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
        // set ReadData on conduitMock
        conduitMock.setReadData("{handshake data}", "{data}", "{data}", "{more data}");
        // enable read on conduitMock, meaning that data above will be available to be read right away
        conduitMock.enableReads(true);
        // try to read message
        final ByteBuffer buffer = ByteBuffer.allocate(30);
        assertEquals(30, sourceConduit.read(buffer));
        // data expected to have been read from 'conduitMock' by 'sourceConduit' so far
        assertReadMessage(buffer, "Mock Read Test", "Mock Read Test", "it");
        buffer.clear();

        // set more read data
        conduitMock.setReadData("{data}", "{more data}");
        conduitMock.enableReads(true);
        // and read again
        assertEquals(30, sourceConduit.read(buffer));
        // data expected to have been read from 'conduitMock' by 'sourceConduit'
        assertReadMessage(buffer,  " works!", "Mock Read Test", "it works!");
        buffer.clear();

        // set more read data
        conduitMock.setReadData("{more data}", "{message closed}");
        conduitMock.enableReads(true);
        // and read again
        assertEquals(9, sourceConduit.read(buffer));

        // data expected to have been read from 'conduitMock' by 'sourceConduit'
        assertReadMessage(buffer, "it works!");
        buffer.clear();

        // conduit should be able to terminate reads
        sourceConduit.terminateReads();
        // close connection
        sinkConduit.terminateWrites();

        // data expected to have been written to 'conduitMock' by 'sinkConduit'
        assertWrittenMessage("{handshake data}", "{message closed}");
    }

    @Test
    public void readWithAbruptClose() throws IOException {
        // set the handshake actions that engineMock will emulate
        engineMock.setHandshakeActions(NEED_UNWRAP, NEED_WRAP, NEED_UNWRAP, NEED_WRAP, NEED_TASK, FINISH);
        // message buffer
        final ByteBuffer buffer = ByteBuffer.allocate(100);

        conduitMock.setReadData(HANDSHAKE_MSG, CLOSE_MSG);
        conduitMock.enableReads(true);
        // attempt to read several times... conduit is expected to stop on the second NEED_UNWRAP action, as
        // conduitMock will read an abrupt CLOSE_MSG
        assertEquals(-1, sourceConduit.read(buffer));
        assertEquals(-1, sourceConduit.read(buffer));
        assertEquals(-1, sourceConduit.read(buffer));
        // conduit should be able to terminate writes
        sourceConduit.terminateReads();
        // close connection
        sinkConduit.terminateWrites();
        // data expected to have been written to 'conduitMock' by 'sinkConduit'
        assertWrittenMessage(HANDSHAKE_MSG, CLOSE_MSG);
    }

    @Test
    public void readWithConstantHandshake() throws IOException {
        // set the handshake actions that engineMock will emulate
        engineMock.setHandshakeActions(NEED_WRAP, NEED_UNWRAP, NEED_TASK, FINISH, PERFORM_REQUESTED_ACTION,
                NEED_UNWRAP, NEED_WRAP, NEED_TASK, FINISH, PERFORM_REQUESTED_ACTION,
                NEED_WRAP, NEED_WRAP, NEED_WRAP, NEED_UNWRAP, NEED_TASK, NEED_TASK, NEED_TASK, NEED_TASK, FINISH,
                PERFORM_REQUESTED_ACTION, NEED_WRAP, NEED_UNWRAP, NEED_TASK, FINISH, PERFORM_REQUESTED_ACTION);
        // set ReadData on conduitMock
        conduitMock.setReadData(HANDSHAKE_MSG, "read a lot", HANDSHAKE_MSG, " read a lot ",
                HANDSHAKE_MSG, "read a lot ", HANDSHAKE_MSG, " read a lot", CLOSE_MSG);
        // enable read on conduitMock, meaning that data above will be available to be read right away
        conduitMock.enableReads(true);
        // try to read message
        final ByteBuffer buffer = ByteBuffer.allocate(10);
        assertEquals(10, sourceConduit.read(buffer));
        assertReadMessage(buffer, "read a lot");
        buffer.clear();

        assertEquals(10, sourceConduit.read(buffer));
        assertReadMessage(buffer, " read a lo");
        buffer.clear();

        assertEquals(2, sourceConduit.read(buffer));
        // tasks
        sourceConduit.awaitReadable();
        assertEquals(8, sourceConduit.read(buffer));
        assertReadMessage(buffer, "t read a l");
        buffer.clear();

        assertEquals(3, sourceConduit.read(buffer));
        // tasks
        sourceConduit.awaitReadable();
        assertEquals(7, sourceConduit.read(buffer));
        assertReadMessage(buffer, "ot  read a");
        buffer.clear();

        assertEquals(4, sourceConduit.read(buffer));
        assertReadMessage(buffer, " lot");

        // make sure that conduit managed to do the WRAP and there is no more handshake actions left
        assertSame(HandshakeStatus.NOT_HANDSHAKING, engineMock.getHandshakeStatus());
        // terminate reads
        sourceConduit.terminateReads();
        // close connection
        sinkConduit.terminateWrites();
        // data expected to have been written to 'conduitMock' by 'sinkConduit'
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
        // set ReadData on conduitMock
        conduitMock.setReadData("HANDSHAKE_MSG", "read a lot", "a lot", "read", "HANDSHAKE_MSG", "a lot",
                "read a lot", "HANDSHAKE_MSG", "read nothing", "read a lot", "HANDSHAKE_MSG", "read nothing",
                "CLOSE_MSG");
        // enable read on conduitMock, meaning that data above will be available to be read right away
        conduitMock.enableReads(true);
        // try to read message
        final ByteBuffer buffer = ByteBuffer.allocate(10);
        assertEquals(10, sourceConduit.read(buffer));
        assertReadMessage(buffer, "a lot", "lot", "re");
        buffer.clear();

        assertEquals(2, sourceConduit.read(buffer));
        sourceConduit.awaitReadable();
        assertEquals(8, sourceConduit.read(buffer));
        assertReadMessage(buffer, "ad", "lot", "a lot");
        buffer.clear();

        assertEquals(10, sourceConduit.read(buffer));
        assertReadMessage(buffer, "nothing", "a l");
        buffer.clear();

        assertEquals(2, sourceConduit.read(buffer));
        sourceConduit.awaitReadable();
        assertEquals(7, sourceConduit.read(buffer));
        assertReadMessage(buffer, "ot", "nothing");
        buffer.clear();

        // make sure that conduit managed to do the WRAP and there is no more handshake actions left
        assertSame(HandshakeStatus.NOT_HANDSHAKING, engineMock.getHandshakeStatus());
        // terminate reads
        sourceConduit.terminateReads();
        // close connection
        sinkConduit.terminateWrites();
        // data expected to have been written to 'conduitMock' by 'sinkConduit'
        assertWrittenMessage("HANDSHAKE_MSG", "HANDSHAKE_MSG", "HANDSHAKE_MSG", "HANDSHAKE_MSG", "HANDSHAKE_MSG",
                "HANDSHAKE_MSG", "CLOSE_MSG");
    }

    @Test
    public void readWithIntercalatedHandshake() throws IOException {
        // set the handshake actions that engineMock will emulate
        engineMock.setHandshakeActions(NEED_WRAP, NEED_UNWRAP, NEED_TASK, FINISH, PERFORM_REQUESTED_ACTION,
                NEED_WRAP, NEED_UNWRAP, PERFORM_REQUESTED_ACTION, NEED_UNWRAP, PERFORM_REQUESTED_ACTION, FINISH);
        // set ReadData on conduitMock
        conduitMock.setReadData(HANDSHAKE_MSG, "read this", "read this", "read this", "read this",
                HANDSHAKE_MSG, "read this", HANDSHAKE_MSG, "read this", CLOSE_MSG);
        // enable read on conduitMock, meaning that data above will be available to be read right away
        conduitMock.enableReads(true);
        final ByteBuffer buffer = ByteBuffer.allocate(100);
        // attempt to read... conduit is expected to read the message 
        assertEquals(54, sourceConduit.read(buffer));
        // make sure that conduit managed to do the WRAP and there is no more handshake actions left
        assertSame(HandshakeStatus.NOT_HANDSHAKING, engineMock.getHandshakeStatus());
        // terminate reads
        sourceConduit.terminateReads();
        // close connection
        sinkConduit.terminateWrites();
        // data expected to have been read from 'conduitMock' by 'sourceConduit'
        assertReadMessage(buffer, "read this", "read this", "read this", "read this", "read this", "read this");
        // data expected to have been written to 'conduitMock' by 'sinkConduit'
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
        // set ReadData on conduitMock
        conduitMock.setReadData("[!@#$%^&*()_]", "read this", "read this", "read this", "read this",
                "[!@#$%^&*()_]", "read this", "[!@#$%^&*()_]", "read this", "[_)(*&^%$#@!]");
        // enable read on conduitMock, meaning that data above will be available to be read right away
        conduitMock.enableReads(true);
        final ByteBuffer buffer = ByteBuffer.allocate(100);
        // attempt to read... conduit is expected to read the message 
        assertEquals(24, sourceConduit.read(buffer));
        // make sure that conduit managed to do the WRAP and there is no more handshake actions left
        assertSame(HandshakeStatus.NOT_HANDSHAKING, engineMock.getHandshakeStatus());
        // terminate reads
        sourceConduit.terminateReads();
        // close connection
        sinkConduit.terminateWrites();
        // data expected to have been read from 'conduitMock' by 'sourceConduit'
        assertReadMessage(buffer, "this", "this", "this", "this", "this", "this");
        // data expected to have been written to 'conduitMock' by 'sinkConduit'
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
                sourceConduit.read(buffer);
            } catch (IOException expected) {
                failed = true;
            }
            assertTrue(failed);
        }
    }
}
