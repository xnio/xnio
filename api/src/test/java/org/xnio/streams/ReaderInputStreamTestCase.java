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
