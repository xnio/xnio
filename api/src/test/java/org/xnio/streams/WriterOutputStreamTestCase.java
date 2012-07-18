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
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

import org.junit.Test;

/**
 * Test for {@link WriterOutputStream}.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
public class WriterOutputStreamTestCase {

    @Test
    public void invalidConstructorArguments() throws IOException {
        // null writer
        Exception expected = null;
        try {
            new WriterOutputStream(null);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            new WriterOutputStream(null, Charset.defaultCharset());
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            new WriterOutputStream(null, Charset.defaultCharset().newDecoder());
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            new WriterOutputStream(null, "UTF-8");
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            new WriterOutputStream(null, Charset.defaultCharset().newDecoder(), 10000);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        // null char set
        final StringWriter writer = new StringWriter();
        expected = null;
        try {
            new WriterOutputStream(writer, (Charset) null);
        } catch (NullPointerException e) {
            expected = e;
        }
        assertNotNull(expected);
        // null char set name
        expected = null;
        try {
            new WriterOutputStream(writer, (String) null);
        } catch (IllegalArgumentException e) {
            expected= e;
        }
        assertNotNull(expected);
        // null decoder
        expected = null;
        try {
            new WriterOutputStream(writer, (CharsetDecoder) null);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            new WriterOutputStream(writer, null, 10000);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        // negative buffer size
        expected = null;
        try {
            new WriterOutputStream(writer, Charset.defaultCharset().newDecoder(), -15000);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        // zero buffer length
        expected = null;
        try {
            new WriterOutputStream(writer, Charset.defaultCharset().newDecoder(), 0);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    @Test
    public void writeBytes() throws IOException {
        final StringWriter writer = new StringWriter();
        final WriterOutputStream stream = new WriterOutputStream(writer);
        stream.write('w');
        stream.write('r');
        stream.write('i');
        stream.write('t');
        stream.write('e');
        assertEquals("", writer.getBuffer().toString());
        stream.flush();
        assertEquals("write", writer.getBuffer().toString());
        closeStream(stream, writer, "write");
    }

    @Test
    public void writeByteArray() throws IOException {
        final StringWriter writer = new StringWriter();
        final WriterOutputStream stream = new WriterOutputStream(writer, "UTF-8");
        stream.write("write".getBytes("UTF-8"));
        assertEquals("", writer.getBuffer().toString());
        stream.flush();
        assertEquals("write", writer.getBuffer().toString());
        closeStream(stream, writer, "write");
    }

    @Test
    public void writeOverflowsInternalBuffer() throws IOException {
        final StringWriter writer = new StringWriter();
        final WriterOutputStream stream = new WriterOutputStream(writer, Charset.defaultCharset().newDecoder(), 3);
        stream.write('w');
        stream.write('r');
        stream.write('i');
        assertEquals("", writer.getBuffer().toString());
        stream.write('t');
        assertEquals("wri", writer.getBuffer().toString());
        stream.write('e');
        assertEquals("wri", writer.getBuffer().toString());
        stream.flush();
        assertEquals("write", writer.getBuffer().toString());
        closeStream(stream, writer, "write");
    }

    @Test
    public void writeByteArrayOverflowsInternalBuffer() throws IOException {
        final StringWriter writer = new StringWriter();
        final WriterOutputStream stream = new WriterOutputStream(writer, Charset.defaultCharset().newDecoder(), 3);
        stream.write("w r i t e".getBytes("UTF-8"), 2, 5);
        assertEquals("r i", writer.getBuffer().toString());
        stream.flush();
        assertEquals("r i t", writer.getBuffer().toString());
        closeStream(stream, writer, "r i t");
    }

    @Test
    public void closeStream() throws IOException {
        final StringWriter writer = new StringWriter();
        final WriterOutputStream stream = new WriterOutputStream(writer, Charset.defaultCharset().newDecoder(), 10);
        stream.write('a');
        stream.write("bcd".getBytes("UTF-8"));
        assertEquals("", writer.getBuffer().toString());
        stream.flush();
        assertEquals("abcd", writer.getBuffer().toString());
        stream.write("eefghijklm".getBytes("UTF-8"), 1, 8);
        assertEquals("abcd", writer.getBuffer().toString());
        stream.write('m');
        assertEquals("abcd", writer.getBuffer().toString());
        stream.write('n');
        assertEquals("abcd", writer.getBuffer().toString());
        closeStream(stream, writer, "abcdefghijklmn");
    }

    @Test
    public void closeEmptyStream() throws IOException {
        final StringWriter writer = new StringWriter();
        final WriterOutputStream stream = new WriterOutputStream(writer, Charset.defaultCharset().newDecoder(), 10);
        closeStream(stream, writer, "");
    }

    private void closeStream(WriterOutputStream stream, StringWriter writer, String writtenString) throws IOException {
        stream.close();
        assertEquals(writtenString, writer.getBuffer().toString());
        IOException expected = null;
        try {
            stream.write('a');
        } catch (IOException e) {
            expected = e;
        }
        assertNotNull(expected);
        assertEquals(writtenString, writer.getBuffer().toString());
        expected = null;
        try {
            stream.write("abc".getBytes("UTF-8"));
        } catch (IOException e) {
            expected = e;
        }
        assertNotNull(expected);
        assertEquals(writtenString, writer.getBuffer().toString());
        expected = null;
        try {
            stream.write("abc".getBytes("UTF-8"), 1, 1);
        } catch (IOException e) {
            expected = e;
        }
        assertNotNull(expected);
        assertEquals(writtenString, writer.getBuffer().toString());
        expected = null;
        try {
            stream.flush();
        } catch (IOException e) {
            expected = e;
        }
        assertNotNull(expected);
        assertEquals(writtenString, writer.getBuffer().toString());
        assertNotNull(stream.toString());
    }
}
