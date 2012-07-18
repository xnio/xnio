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
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;

import org.junit.Test;

/**
 * Test for {@link ReaderInputStream}.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
public class ReaderInputStreamTestCase {

    @Test
    public void invalidConstructorArguments() throws IOException {
        // null writer
        Exception expected = null;
        try {
            new ReaderInputStream(null);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            new ReaderInputStream(null, Charset.defaultCharset());
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            new ReaderInputStream(null, Charset.defaultCharset().newEncoder());
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            new ReaderInputStream(null, "UTF-8");
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            new ReaderInputStream(null, Charset.defaultCharset().newEncoder(), 10000);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        // null char set
        final Reader reader = new StringReader("foo");
        expected = null;
        try {
            new ReaderInputStream(reader, (Charset) null);
        } catch (NullPointerException e) {
            expected = e;
        }
        assertNotNull(expected);
        // null char set name
        expected = null;
        try {
            new ReaderInputStream(reader, (String) null);
        } catch (IllegalArgumentException e) {
            expected= e;
        }
        assertNotNull(expected);
        // null decoder
        expected = null;
        try {
            new ReaderInputStream(reader, (CharsetEncoder) null);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            new ReaderInputStream(reader, null, 10000);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        // negative buffer size
        expected = null;
        try {
            new ReaderInputStream(reader, Charset.defaultCharset().newEncoder(), -15000);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        // zero buffer length
        expected = null;
        try {
            new ReaderInputStream(reader, Charset.defaultCharset().newEncoder(), 0);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    @Test
    public void read1() throws IOException {
        final ReaderInputStream stream = new ReaderInputStream(new StringReader("test"));
        assertEquals('t', stream.read());
        assertEquals('e', stream.read());
        assertEquals('s', stream.read());
        assertEquals('t', stream.read());
        assertEquals(-1, stream.read());
        closeStream(stream);
    }

    @Test
    public void read2() throws IOException {
        final ReaderInputStream stream = new ReaderInputStream(new StringReader("test"), "UTF-8");
        byte[] bytes = new byte[10];
        assertEquals(4, stream.read(bytes));
        assertEquals('t', bytes[0]);
        assertEquals('e', bytes[1]);
        assertEquals('s', bytes[2]);
        assertEquals('t', bytes[3]);
        assertEquals(-1, stream.read(bytes));
        closeStream(stream);
    }

    @Test
    public void read3() throws IOException {
        final ReaderInputStream stream = new ReaderInputStream(new StringReader("test"),
                Charset.defaultCharset().newEncoder(), 1);
        assertEquals('t', stream.read());
        assertEquals('e', stream.read());
        assertEquals('s', stream.read());
        assertEquals('t', stream.read());
        assertEquals(-1, stream.read());
        closeStream(stream);
    }

    @Test
    public void read4() throws IOException {
        final ReaderInputStream stream = new ReaderInputStream(new StringReader("test"),
                Charset.defaultCharset().newEncoder(), 1);
        byte[] bytes = new byte[2];
        assertEquals(2, stream.read(bytes));
        assertEquals('t', bytes[0]);
        assertEquals('e', bytes[1]);
        assertEquals(2, stream.read(bytes));
        assertEquals('s', bytes[0]);
        assertEquals('t', bytes[1]);
        assertEquals(-1, stream.read(bytes));
        closeStream(stream);
    }

    @Test
    public void read5() throws IOException {
        final ReaderInputStream stream = new ReaderInputStream(new StringReader(""),
                Charset.defaultCharset().newEncoder(), 1);
        assertEquals(-1, stream.read(new byte[5]));
        assertEquals(-1, stream.read());
        closeStream(stream);
    }

    @Test
    public void readAfterAvailable1() throws IOException {
        final ReaderInputStream stream = new ReaderInputStream(new StringReader("test"));
        assertEquals(0, stream.available());
        assertEquals('t', stream.read());
        assertEquals(3, stream.available());
        assertEquals('e', stream.read());
        assertEquals(2, stream.available());
        assertEquals('s', stream.read());
        assertEquals(1, stream.available());
        assertEquals('t', stream.read());
        assertEquals(0, stream.available());
        assertEquals(-1, stream.read());
        closeStream(stream);
    }

    @Test
    public void readAfterAvailable2() throws IOException {
        final ReaderInputStream stream = new ReaderInputStream(new StringReader("test"), "UTF-8");
        byte[] bytes = new byte[10];
        assertEquals(0, stream.available());
        assertEquals(4, stream.read(bytes));
        assertEquals(0, stream.available());
        assertEquals('t', bytes[0]);
        assertEquals('e', bytes[1]);
        assertEquals('s', bytes[2]);
        assertEquals('t', bytes[3]);
        assertEquals(-1, stream.read(bytes));
        closeStream(stream);
    }

    @Test
    public void readAfterAvailable3() throws IOException {
        final ReaderInputStream stream = new ReaderInputStream(new StringReader("test"),
                Charset.defaultCharset().newEncoder(), 1);
        assertEquals(0, stream.available());
        assertEquals('t', stream.read());
        assertEquals(0, stream.available());
        assertEquals('e', stream.read());
        assertEquals(0, stream.available());
        assertEquals('s', stream.read());
        assertEquals(0, stream.available());
        assertEquals('t', stream.read());
        assertEquals(0, stream.available());
        assertEquals(-1, stream.read());
        closeStream(stream);
    }

    @Test
    public void readAfterAvailable4() throws IOException {
        final ReaderInputStream stream = new ReaderInputStream(new StringReader("test"),
                Charset.defaultCharset().newEncoder(), 1);
        byte[] bytes = new byte[2];
        assertEquals(0, stream.available());
        assertEquals(2, stream.read(bytes));
        assertEquals('t', bytes[0]);
        assertEquals('e', bytes[1]);
        assertEquals(0, stream.available());
        assertEquals(2, stream.read(bytes));
        assertEquals('s', bytes[0]);
        assertEquals('t', bytes[1]);
        assertEquals(0, stream.available());
        assertEquals(-1, stream.read(bytes));
        closeStream(stream);
    }

    @Test
    public void readAfterAvailable5() throws IOException {
        final ReaderInputStream stream = new ReaderInputStream(new StringReader(""),
                Charset.defaultCharset().newEncoder(), 1);
        assertEquals(0, stream.available());
        assertEquals(-1, stream.read(new byte[5]));
        assertEquals(-1, stream.read());
        closeStream(stream);
    }

    @Test
    public void skip1() throws IOException {
        final ReaderInputStream stream = new ReaderInputStream(new StringReader("skip all this - data"));
        assertEquals(16, stream.skip(16));
        assertEquals(4, stream.available());
        assertEquals('d', stream.read());
        assertEquals(3, stream.available());
        assertEquals('a', stream.read());
        assertEquals(2, stream.available());
        assertEquals('t', stream.read());
        assertEquals(1, stream.available());
        assertEquals('a', stream.read());
        assertEquals(0, stream.available());
        assertEquals(-1, stream.read());
        closeStream(stream);
    }

    @Test
    public void skip2() throws IOException {
        final ReaderInputStream stream = new ReaderInputStream(new StringReader("skip all this - data"));
        assertEquals(20, stream.skip(Long.MAX_VALUE));
        assertEquals(0, stream.available());
        assertEquals(-1, stream.read());
        closeStream(stream);
    }

    @Test
    public void skipEmpty() throws IOException {
        final ReaderInputStream stream = new ReaderInputStream(new StringReader(""));
        assertEquals(0, stream.skip(1000));
        assertEquals(0, stream.available());
        closeStream(stream);
    }

    @Test
    public void closeStreamBeforeRead() throws IOException {
        final ReaderInputStream stream = new ReaderInputStream(new StringReader("close before read"));
        assertEquals(0, stream.available());
        closeStream(stream);
    }

    @Test
    public void closeStreamBeforeReadIsDone() throws IOException {
        final ReaderInputStream stream = new ReaderInputStream(new StringReader("close before read"));
        assertEquals(0, stream.available());
        assertEquals('c', stream.read());
        assertEquals(16, stream.available());
        closeStream(stream);
    }

    private void closeStream(ReaderInputStream stream) throws IOException {
        stream.close();
        assertEquals(0, stream.available());
        IOException expected = null;
        try {
            stream.read();
        } catch (IOException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            stream.read(new byte[5]);
        } catch (IOException e) {
            expected = e;
        }
        assertNotNull(expected);
        // idempotent
        stream.close();
        assertEquals(0, stream.available());
        expected = null;
        try {
            stream.read(new byte[10]);
        } catch (IOException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            stream.read();
        } catch (IOException e) {
            expected = e;
        }
        assertNotNull(expected);
        assertNotNull(stream.toString());
    }
}
