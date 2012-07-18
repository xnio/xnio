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
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.junit.Test;

/**
 * Test for {@link LimitedInputStream}.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
public class LimitedInputStreamTestCase {

    @Test
    public void limitSizeIsLessThenAvailable1() throws IOException {
        final ByteArrayInputStream delegateStream = new ByteArrayInputStream(new byte[] {'t', 'e', 's', 't'});
        final LimitedInputStream stream = new LimitedInputStream(delegateStream, 2);
        assertEquals(2, stream.available());
        assertEquals('t', stream.read());
        assertEquals('e', stream.read());
        assertEquals(-1, stream.read());
    }

    @Test
    public void limitSizeIsLessThenAvailable2() throws IOException {
        final ByteArrayInputStream delegateStream = new ByteArrayInputStream(new byte[] {'a', 'r', 'r', 'a', 'y'});
        final LimitedInputStream stream = new LimitedInputStream(delegateStream, 4);
        final byte[] bytes = new byte[10];
        assertEquals(4, stream.available());
        assertEquals(4, stream.read(bytes));
        assertEquals('a', bytes[0]);
        assertEquals('r', bytes[1]);
        assertEquals('r', bytes[2]);
        assertEquals('a', bytes[3]);
        assertEquals(-1, stream.read(bytes));
    }

    @Test
    public void limitSizeIsEqualToAvailable1() throws IOException {
        final ByteArrayInputStream delegateStream = new ByteArrayInputStream(new byte[] {'t', 'e', 's', 't'});
        final LimitedInputStream stream = new LimitedInputStream(delegateStream, 4);
        assertEquals(4, stream.available());
        assertEquals('t', stream.read());
        assertEquals('e', stream.read());
        assertEquals('s', stream.read());
        assertEquals('t', stream.read());
        assertEquals(-1, stream.read());
    }

    @Test
    public void limitSizeIsEqualToAvailable2() throws IOException {
        final ByteArrayInputStream delegateStream = new ByteArrayInputStream(new byte[] {'a', 'b', 'c'});
        final LimitedInputStream stream = new LimitedInputStream(delegateStream, 3);
        final byte[] bytes = new byte[5];
        assertEquals(3, stream.available());
        assertEquals(3, stream.read(bytes));
        assertEquals('a', bytes[0]);
        assertEquals('b', bytes[1]);
        assertEquals('c', bytes[2]);
        assertEquals(-1, stream.read(bytes));
    }

    @Test
    public void limitSizeIsMoreThanAvailable1() throws IOException {
        final ByteArrayInputStream delegateStream = new ByteArrayInputStream(new byte[] {'t', 'e', 's', 't'});
        final LimitedInputStream stream = new LimitedInputStream(delegateStream, 10);
        assertEquals(4, stream.available());
        assertEquals('t', stream.read());
        assertEquals('e', stream.read());
        assertEquals('s', stream.read());
        assertEquals('t', stream.read());
        assertEquals(-1, stream.read());
    }

    @Test
    public void limitSizeIsMoreThanAvailable2() throws IOException {
        final ByteArrayInputStream delegateStream = new ByteArrayInputStream(new byte[] {'m', 'o', 'r', 'e'});
        final LimitedInputStream stream = new LimitedInputStream(delegateStream, 5);
        final byte[] bytes = new byte[5];
        assertEquals(4, stream.available());
        assertEquals(4, stream.read(bytes));
        assertEquals('m', bytes[0]);
        assertEquals('o', bytes[1]);
        assertEquals('r', bytes[2]);
        assertEquals('e', bytes[3]);
        assertEquals(-1, stream.read(bytes));
    }

    @Test
    public void skipWithLimitSizeLessThenAvailable() throws IOException {
        final ByteArrayInputStream delegateStream = new ByteArrayInputStream(new byte[] {'s', 'k', 'i', 'p',
                'p', 'i', 'k', 's'});
        final LimitedInputStream stream = new LimitedInputStream(delegateStream, 6);
        assertEquals(0, stream.skip(-5));
        assertEquals(6, stream.available());
        assertEquals(2, stream.skip(2));
        assertEquals(4, stream.available());
        assertEquals(4, stream.skip(10));
        assertEquals(0, stream.available());
        assertEquals(0, stream.skip(10));
    }

    @Test
    public void skipWithLimitSizeEqualToAvailable() throws IOException {
        final ByteArrayInputStream delegateStream = new ByteArrayInputStream(new byte[] {'s', 'k', 'i', 'p',
                'p', 'i', 'k', 's'});
        final LimitedInputStream stream = new LimitedInputStream(delegateStream, 8);
        assertEquals(0, stream.skip(-1));
        assertEquals(8, stream.available());
        assertEquals(8, stream.skip(8));
        assertEquals(0, stream.available());
        assertEquals(0, stream.skip(3));
        assertEquals(0, stream.available());
    }

    @Test
    public void skipWithLimitSizeMoreThanAvailable() throws IOException {
        final ByteArrayInputStream delegateStream = new ByteArrayInputStream(new byte[] {'s', 'k', 'i', 'p',
                'p', 'i', 'k', 's'});
        final LimitedInputStream stream = new LimitedInputStream(delegateStream, 9);
        assertEquals(0, stream.skip(0));
        assertEquals(8, stream.available());
        assertEquals(5, stream.skip(5));
        assertEquals(3, stream.available());
        assertEquals(3, stream.skip(10));
        assertEquals(0, stream.available());
        assertEquals(0, stream.skip(10));
    }

    @Test
    public void closeStream() throws IOException {
        final ByteArrayInputStream delegateStream = new ByteArrayInputStream(new byte[] {'c', 'l', 'o', 's', 'e'});
        final LimitedInputStream stream = new LimitedInputStream(delegateStream, 10);
        assertEquals(5, stream.available());
        stream.close();
        assertEquals(0, stream.available());
        assertEquals(-1, stream.read());
        assertEquals(-1, stream.read(new byte[3]));
    }

    @Test
    public void markAndReset() throws IOException {
        final ByteArrayInputStream delegateStream = new ByteArrayInputStream(new byte[] {'m', 'a', 'r', 'k',
                'r', 'e', 's', 'e', 't'});
        final LimitedInputStream stream = new LimitedInputStream(delegateStream, 8);
        IOException expected = null;
        try {
            stream.reset();
        } catch (IOException e) {
            expected = e;
        }
        assertNotNull(expected);
        assertTrue(stream.markSupported());
        assertEquals('m', stream.read());
        assertEquals('a', stream.read());
        stream.mark(2);
        assertEquals('r', stream.read());
        assertEquals('k', stream.read());
        stream.reset();
        assertEquals('r', stream.read());
        assertEquals('k', stream.read());
        stream.mark(3);
        assertEquals('r', stream.read());
        assertEquals('e', stream.read());
        assertEquals('s', stream.read());
        assertEquals('e', stream.read());
        assertEquals(-1, stream.read());
        stream.reset();
        assertEquals('r', stream.read());
    }
}
