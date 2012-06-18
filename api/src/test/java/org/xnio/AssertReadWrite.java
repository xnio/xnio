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
