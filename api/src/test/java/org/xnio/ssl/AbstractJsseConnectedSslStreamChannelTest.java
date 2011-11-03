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
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.jmock.Mockery;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.After;
import org.junit.Before;
import org.xnio.BufferAllocator;
import org.xnio.Buffers;
import org.xnio.ByteBufferSlicePool;
import org.xnio.Pool;
import org.xnio.ssl.mock.ConnectedStreamChannelMock;
import org.xnio.ssl.mock.SSLEngineMock;

/**
 * Abstract test for {@link #JsseConnectedSslStreamChannel}
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
public abstract class AbstractJsseConnectedSslStreamChannelTest {
    // mockery context
    protected Mockery context;
    // the channel to be tested
    protected JsseConnectedSslStreamChannel sslChannel;
    // the underlying channel used by JsseConnectedSslStreamChannel above
    protected ConnectedStreamChannelMock connectedChannelMock;
    // the SSLEngine mock, allows to test different engine behavior with channel
    protected SSLEngineMock engineMock;

    @Before
    public void createChannelMock() throws IOException {
        context = new JUnit4Mockery();
        connectedChannelMock = new ConnectedStreamChannelMock();
        engineMock = new SSLEngineMock(context);
        final Pool<ByteBuffer> socketBufferPool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 17000, 17000 * 16);
        final Pool<ByteBuffer> applicationBufferPool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 17000, 17000 * 16);
        sslChannel = new JsseConnectedSslStreamChannel(connectedChannelMock, engineMock, true, socketBufferPool, applicationBufferPool, false);
    }

    @After
    public void checkContext() {
        context.assertIsSatisfied();
    }

    /**
     * Asserts that the message read by {@code sslChannel}, contained in {@code dst}, equals {@code message}.
     * @param dst     the buffer containing the read message 
     * @param message message expected to have been read into {@code dst}
     */
    protected final void assertReadMessage(ByteBuffer dst, String... message) {
        StringBuffer stringBuffer = new StringBuffer();
        for (String messageString: message) {
            stringBuffer.append(messageString);
        }
        dst.flip();
        assertEquals(stringBuffer.toString(), Buffers.getModifiedUtf8(dst));
    }

    /**
     * Asserts that {@code message} equals the data written by {@code sslChannel} to {@code connectedChannelMock}.
     * 
     * @param message the message expected to have been written to the channel mock
     */
    protected final void assertWrittenMessage(String... message) {
        StringBuffer stringBuffer = new StringBuffer();
        for (String messageString: message) {
            stringBuffer.append(messageString);
        }
        assertEquals("expected total size: "+ stringBuffer.length() + " actual length: " + connectedChannelMock.getWrittenText().length(),
                stringBuffer.toString(), connectedChannelMock.getWrittenText());
    }

    /**
     * Asserts that {@code interwovenMessages} have been written by {@code sslChannel} to {@code connectedChannelMock},
     * in an interwoven way. In other words, the sequence of messages inside each {@code String[]} message array has
     * to be kept between its components, but this array can be mixed with other {@code String[]} message arrays
     * contained in {@code interwovenMessages}.
     * <p>
     * A valid example of usage is when you expect to have two {@link SSLEngineMock#HANDSHAKE_MSG}s mixed with one
     * {@code "testMessage"}. In this case, calling this method like below:<br>
     * <code>assertWrittenMessage(new String[] {HANDSHAKE_MSG, HANDSHAKE_MSG}, new String[] {"testMessage"})</code>
     * <br>
     * will consider valid the sequences below:<br>
     * {@code HANDSHAKE_MSG, HANDSHAKE_MSG, "testMessage"}<br>
     * {@code HANDSHAKE_MSG, "testMessage", HANDSHAKE_MSG}<br>
     * {@code "testMessage", HANDSHAKE_MSG, HANDSHAKE_MSG}<br>
     * but will invalidate, for example, the next two sequences:<br>
     * {@code "testMessage, HANDSHAKE_MSG
     * <br>{@code HANDSHAKE_MSG, "testMessage", HANDSHAKE_MSG, HANDSHAKE_MSG}.
     * 
     * @param interwovenMessages  messages expected to have been written to {@code connectedChannelMock}
     */
    protected final void assertWrittenMessage(String[]... interwovenMessages) {
        String writtenMessage = connectedChannelMock.getWrittenText();
        for (String[] messages: interwovenMessages) {
            StringBuffer stringBuffer = new StringBuffer();
            String writtenMessageSuffix = writtenMessage;
            for (String message: messages) {
                int indexOfMessage = writtenMessageSuffix.indexOf(message);
                if (indexOfMessage == -1) {
                    fail("Couldn't find message " + message + " at " + writtenMessageSuffix + "\n Complete written message: "+ connectedChannelMock.getWrittenText());
                }
                stringBuffer.append(writtenMessageSuffix.substring(0, indexOfMessage));
                writtenMessageSuffix = writtenMessageSuffix.substring(indexOfMessage + message.length());
            }
            stringBuffer.append(writtenMessageSuffix);
            writtenMessage = stringBuffer.toString();
        }
        assertEquals(0, writtenMessage.length());
    }
}
