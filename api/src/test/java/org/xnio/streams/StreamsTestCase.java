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

package org.xnio.streams;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.junit.Test;
import org.xnio.mock.ConnectedStreamChannelMock;

/**
 * Test for {@link Streams}.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
public class StreamsTestCase {

    @Test
    public void defaultCopy() throws IOException {
        final InputStream inputStream = new ByteArrayInputStream("copy".getBytes());
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        final OutputStream outputStream = new ChannelOutputStream(channelMock);
        Streams.copyStream(inputStream, outputStream);
        assertEquals("copy", channelMock.getWrittenText());
        // check the streams are closed
        assertEquals(-1, inputStream.read());
        assertFalse(channelMock.isWriteResumed());
    }

    @Test
    public void copyWithClosedInputStream() throws IOException {
        final InputStream inputStream = new ByteArrayInputStream("copy".getBytes());
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        final OutputStream outputStream = new ChannelOutputStream(channelMock);
        inputStream.close();
        Streams.copyStream(inputStream, outputStream, false);
        assertEquals("copy", channelMock.getWrittenText());
        // check the streams are closed
        assertEquals(-1, inputStream.read());
        assertFalse(channelMock.isWriteResumed());
    }

    @Test
    public void copyWithoutClose() throws IOException {
        final InputStream inputStream = new ByteArrayInputStream("don't close".getBytes());
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        final OutputStream outputStream = new ChannelOutputStream(channelMock);
        Streams.copyStream(inputStream, outputStream, false);
        assertEquals("don't close", channelMock.getWrittenText());
        // check the streams are closed
        assertEquals(-1, inputStream.read());
        assertFalse(channelMock.isWriteResumed());
    }

    @Test
    public void copyWithSmallBuffer() throws IOException {
        final InputStream inputStream = new ByteArrayInputStream("this wont fit in the small buffer".getBytes());
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        final OutputStream outputStream = new ChannelOutputStream(channelMock);
        Streams.copyStream(inputStream, outputStream, false, 3);
        assertEquals("this wont fit in the small buffer", channelMock.getWrittenText());
        // check the streams are closed
        assertEquals(-1, inputStream.read());
        assertFalse(channelMock.isWriteResumed());
    }

}
