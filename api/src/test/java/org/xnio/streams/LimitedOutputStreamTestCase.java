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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.xnio.AssertReadWrite.assertWrittenMessage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.Test;
import org.xnio.mock.ConnectedStreamChannelMock;

/**
 * Test for {@code LimitedOutputStream}.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
public class LimitedOutputStreamTestCase {

    @Test
    public void writeByteArray() throws Exception {
        final ByteArrayOutputStream delegateStream = new ByteArrayOutputStream();
        final LimitedOutputStream stream = new LimitedOutputStream(delegateStream, 5);
        stream.write("test".getBytes("UTF-8"));
        byte[] writtenBytes = delegateStream.toByteArray();
        assertEquals(4, writtenBytes.length);
        assertEquals('t', writtenBytes[0]);
        assertEquals('e', writtenBytes[1]);
        assertEquals('s', writtenBytes[2]);
        assertEquals('t', writtenBytes[3]);
    }

    @Test
    public void writeBytes() throws Exception {
        final ByteArrayOutputStream delegateStream = new ByteArrayOutputStream();
        final LimitedOutputStream stream = new LimitedOutputStream(delegateStream, 5);
        stream.write('t');
        stream.write('e');
        stream.write('s');
        stream.write('t');
        byte[] writtenBytes = delegateStream.toByteArray();
        assertEquals(4, writtenBytes.length);
        assertEquals('t', writtenBytes[0]);
        assertEquals('e', writtenBytes[1]);
        assertEquals('s', writtenBytes[2]);
        assertEquals('t', writtenBytes[3]);
    }

    @Test
    public void writeByteArrayOverflows() throws Exception {
        final ByteArrayOutputStream delegateStream = new ByteArrayOutputStream();
        final LimitedOutputStream stream = new LimitedOutputStream(delegateStream, 5);
        IOException expected = null;
        try {
            stream.write("overflow".getBytes("UTF-8"));
        } catch (IOException e) {
            expected = e;
        }
        assertNotNull(expected);
        assertEquals(0, delegateStream.toByteArray().length);
    }

    @Test
    public void writeByteOverflows() throws Exception {
        final ByteArrayOutputStream delegateStream = new ByteArrayOutputStream();
        final LimitedOutputStream stream = new LimitedOutputStream(delegateStream, 5);
        stream.write('o');
        stream.write('v');
        stream.write('e');
        stream.write('r');
        stream.write('f');
        IOException expected = null;
        try {
            stream.write('l');
        } catch (IOException e) {
            expected = e;
        }
        assertNotNull(expected);
        byte[] writtenBytes = delegateStream.toByteArray();
        assertEquals(5, writtenBytes.length);
        assertEquals('o', writtenBytes[0]);
        assertEquals('v', writtenBytes[1]);
        assertEquals('e', writtenBytes[2]);
        assertEquals('r', writtenBytes[3]);
        assertEquals('f', writtenBytes[4]);
        stream.flush();
        assertEquals(5, delegateStream.toByteArray().length);
    }

    @Test
    public void closeEmptyStream() throws IOException {
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        final ChannelOutputStream delegateStream = new ChannelOutputStream(channelMock);
        final LimitedOutputStream stream = new LimitedOutputStream(delegateStream, 5);
        stream.close();
        assertTrue(channelMock.isShutdownWrites());
        IOException expected = null;
        try {
            stream.write('a');
        } catch (IOException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            stream.write("bcd".getBytes("UTF-8"));
        } catch (IOException e) {
            expected = e;
        }
        assertNotNull(expected);
        stream.flush();
        assertWrittenMessage(channelMock);
        // idempotent
        stream.close();
        expected = null;
        try {
            stream.write('e');
        } catch (IOException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            stream.write("fgh".getBytes("UTF-8"));
        } catch (IOException e) {
            expected = e;
        }
        assertNotNull(expected);
        stream.flush();
        assertWrittenMessage(channelMock);
    }

    @Test
    public void closeStream() throws IOException {
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        final ChannelOutputStream delegateStream = new ChannelOutputStream(channelMock);
        final LimitedOutputStream stream = new LimitedOutputStream(delegateStream, 5);
        stream.write('a');
        stream.write('b');
        stream.write('c');
        // flush
        assertFalse(channelMock.isFlushed());
        stream.flush();
        assertTrue(channelMock.isFlushed());
        // close
        stream.close();
        assertTrue(channelMock.isShutdownWrites());
        IOException expected = null;
        try {
            stream.write('d');
        } catch (IOException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            stream.write("efg".getBytes("UTF-8"));
        } catch (IOException e) {
            expected = e;
        }
        assertNotNull(expected);
        stream.flush();
        assertWrittenMessage(channelMock, "abc");
        // idempotent
        stream.close();
        expected = null;
        try {
            stream.write('h');
        } catch (IOException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            stream.write("ijk".getBytes("UTF-8"));
        } catch (IOException e) {
            expected = e;
        }
        assertNotNull(expected);
        stream.flush();
        assertWrittenMessage(channelMock, "abc");
    }
}
