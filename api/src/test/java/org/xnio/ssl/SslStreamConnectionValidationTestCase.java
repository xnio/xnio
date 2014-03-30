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
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;

import org.junit.After;
import org.junit.Test;
import org.xnio.ssl.mock.SSLEngineMock;

/**
 * Test invalid scenarios involving the SSL connection.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 *
 */
public class SslStreamConnectionValidationTestCase extends AbstractSslConnectionTest {

    @After @Override
    public void checkContext() {
        // do not check context... not all methods will be invoked on sessions created for invalid scenarios
    }

    @Test
    public void writeAfterShutdown() throws IOException {
        conduitMock.setReadData(SSLEngineMock.CLOSE_MSG);
        conduitMock.enableReads(true);
        sinkConduit.terminateWrites();

        final ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put("MSG!!!!!!!".getBytes("UTF-8")).flip();
        boolean failed = false;
        try {
            sinkConduit.write(buffer);
        } catch (ClosedChannelException e) {
            failed = true;
        }
        assertTrue(failed);
    }

    @Test
    public void invalidWriteBufferArray() throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put("invalid".getBytes("UTF-8")).flip();
        // the conduit should simply return 0 if length is 0 or a negative number
        assertEquals(0, sinkConduit.write(new ByteBuffer[]{buffer}, 0, 0));
        assertEquals(0, sinkConduit.write(new ByteBuffer[]{buffer}, 0, -1));
        // conduit should throw ArrayIndexOutOfBoundsException if position is < 0, or if length is larger than it should
        boolean failed = false;
        try {
            assertEquals(0, sinkConduit.write(new ByteBuffer[]{buffer}, -1, 1));
        } catch (ArrayIndexOutOfBoundsException e) {
            failed = true;
        }
        assertTrue(failed);
        failed = false;
        try {
            assertEquals(0, sinkConduit.write(new ByteBuffer[]{buffer}, 0, 2));
        } catch (ArrayIndexOutOfBoundsException e) {
            failed = true;
        }
        assertTrue(failed);
    }

    @Test
    public void writeNullBuffer() throws IOException {
        // null buffer
        boolean failed = false;
        try {
            sinkConduit.write((ByteBuffer) null);
        } catch (NullPointerException e) {
            failed = true;
        }
        assertTrue(failed);
        // null buffer array
        failed = false;
        try {
            sinkConduit.write((ByteBuffer[]) null, 4, 20);
        } catch (NullPointerException e) {
            failed = true;
        }
        assertTrue(failed);
        // null buffer in buffer array
        failed = false;
        try {
            sinkConduit.write(new ByteBuffer[] {null}, 0, 1);
        } catch (NullPointerException e) {
            failed = true;
        }
        assertTrue(failed);
    }

    @Test
    public void invalidReadBufferArray() throws IOException {
        conduitMock.setReadData("invalid read buffer array");
        conduitMock.enableReads(true);
        final ByteBuffer buffer = ByteBuffer.allocate(10);
        // the conduit should simply return 0 if length is 0 or a negative number
        assertEquals(0, sourceConduit.read(new ByteBuffer[]{buffer}, 0, 0));
        assertEquals(0, sourceConduit.read(new ByteBuffer[]{buffer}, 0, -1));
        // conduit should throw ArrayIndexOutOfBoundsException if position is < 0, or if length is larger than it should
        boolean failed = false;
        try {
            assertEquals(0, sourceConduit.read(new ByteBuffer[]{buffer}, -1, 1));
        } catch (ArrayIndexOutOfBoundsException e) {
            failed = true;
        }
        assertTrue(failed);
        failed = false;
        try {
            assertEquals(0, sourceConduit.read(new ByteBuffer[]{buffer}, 0, 2));
        } catch (ArrayIndexOutOfBoundsException e) {
            failed = true;
        }
        assertTrue(failed);
        failed = false;
        try {
            assertEquals(0, sourceConduit.read(new ByteBuffer[0], 0, 50));
        } catch (ArrayIndexOutOfBoundsException e) {
            failed = true;
        }
        assertTrue(failed);
    }

    @Test
    public void readToNullBuffer() throws IOException {
        conduitMock.setReadData("null read buffer");
        conduitMock.enableReads(true);
        // read to a null buffer
        boolean failed = false;
        try {
            sourceConduit.read((ByteBuffer) null);
        } catch (NullPointerException e) {
            failed = true;
        }
        assertTrue(failed);
        // read to a null buffer array
        failed = false;
        try {
            sourceConduit.read((ByteBuffer[]) null, 1, 3);
        } catch (NullPointerException e) {
            failed = true;
        }
        assertTrue(failed);
        // read to a null buffer in a buffer array
        failed = false;
        try {
            sourceConduit.read(new ByteBuffer[]{null}, 0, 1);
        } catch (NullPointerException e) {
            failed = true;
        }
        assertTrue(failed);
    }

    @Test
    public void terminateWritesCantFlush() throws IOException {
        conduitMock.enableFlush(false);
        ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put("abc".getBytes("UTF-8")).flip();
        sinkConduit.write(buffer);
        sinkConduit.terminateWrites();
        assertWrittenMessage("abc", SSLEngineMock.CLOSE_MSG);
    }
}
