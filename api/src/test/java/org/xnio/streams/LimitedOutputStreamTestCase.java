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
