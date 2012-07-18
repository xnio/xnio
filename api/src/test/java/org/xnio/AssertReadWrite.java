/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2012 Red Hat, Inc. and/or its affiliates, and individual
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

import java.nio.ByteBuffer;

import org.xnio.mock.ConnectedStreamChannelMock;

/**
 * This class contains common assertions performed by tests after a read or a write operation.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 *
 */
public class AssertReadWrite {
    /**
     * Asserts that the message read by {@code sslChannel}, contained in {@code dst}, equals {@code message}.
     * @param dst     the buffer containing the read message 
     * @param message message expected to have been read into {@code dst}
     */
    public static final void assertReadMessage(ByteBuffer dst, String... message) {
        final StringBuffer stringBuffer = new StringBuffer();
        for (String messageString: message) {
            stringBuffer.append(messageString);
        }
        dst.flip();
        assertEquals(stringBuffer.toString(), Buffers.getModifiedUtf8(dst));
    }

    /**
     * Asserts that the message read by {@code sslChannel}, contained in {@code dst}, equals {@code message}.
     * @param dst     the byte array containing the read message
     * @param message message expected to have been read into {@code dst}
     */
    public static final void assertReadMessage(byte[] dst, String... message) {
        final StringBuffer stringBuffer = new StringBuffer();
        for (String messageString: message) {
            stringBuffer.append(messageString);
        }
        final ByteBuffer buffer = ByteBuffer.wrap(dst);
        buffer.limit(stringBuffer.length());
        assertEquals(stringBuffer.toString(), Buffers.getModifiedUtf8(buffer));
    }

    /**
     * Asserts that {@code message} equals the data written by {@code sslChannel} to {@code connectedChannelMock}.
     * 
     * @param connectedChannelMock the channel mock where {@code message} should have been written to
     * @param message              the message expected to have been written to the channel mock
     */
    public static final void assertWrittenMessage(ConnectedStreamChannelMock connectedChannelMock, String... message) {
        final StringBuffer stringBuffer = new StringBuffer();
        for (String messageString: message) {
            stringBuffer.append(messageString);
        }
        assertEquals("expected total size: "+ stringBuffer.length() + " actual length: " + connectedChannelMock.getWrittenText().length(),
                stringBuffer.toString(), connectedChannelMock.getWrittenText());
    }
}
