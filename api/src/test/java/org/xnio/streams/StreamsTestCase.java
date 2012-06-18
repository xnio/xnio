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
