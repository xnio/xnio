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

package org.xnio;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import org.junit.Test;


/**
 * Test for {@link ByteString}.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 *
 */
public class ByteStringTestCase {

    @Test
    public void outOfBoundsByteString() {
        final byte[] bytes = "illegal".getBytes();

        Exception exception = null;
        try {
            ByteString.copyOf(bytes, 2, 6);
        } catch (IndexOutOfBoundsException e) {
            exception = e;
        }
        assertNull(exception);

        exception = null;
        try {
            ByteString.copyOf(bytes, -2, 5);
        } catch (IndexOutOfBoundsException e) {
            exception = e;
        }
        assertNotNull(exception);

        exception = null;
        try {
            ByteString.copyOf(bytes, 2, -5);
        } catch (IllegalArgumentException e) {
            exception = e;
        }
        assertNotNull(exception);
    }

    @Test
    public void bytesRetrieval() {
        final ByteString byteString = ByteString.of("bytes retrieval".getBytes());
        final byte[] bytes1 = byteString.getBytes();
        assertEquals(15, bytes1.length);
        checkEqual("bytes retrieval", bytes1);

        final byte[] bytes2 = new byte[byteString.length()];
        byteString.getBytes(bytes2);
        checkEqual("bytes retrieval", bytes2);

        final byte[] bytes3 = new byte[5];
        byteString.getBytes(bytes3);
        checkEqual("bytes", bytes3);

        final byte[] bytes4 = new byte[0];
        byteString.getBytes(bytes4);

        final byte[] bytes5 = new byte[20];
        byteString.getBytes(bytes5);
        checkEqual("bytes retrieval", bytes5, 0);

        final byte[] bytes6 = new byte[10];
        byteString.getBytes(bytes6, 9);
        assertEquals('b', bytes6[9]);

        final byte[] bytes7 = new byte[30];
        byteString.getBytes(bytes7, 10);
        checkEqual("bytes retrieval", bytes7, 10);

        final byte[] bytes8 = new byte[15];
        byteString.getBytes(bytes8, 3, 6);
        checkEqual("bytes ", bytes8, 3);
    }

    @Test
    public void substring() throws UnsupportedEncodingException {
        final ByteString byteString = ByteString.getBytes("abcdeftextghijk", Charset.defaultCharset());
        assertEquals("abcdeftextghijk", byteString.toString("UTF-8"));
        assertEquals(15, byteString.length());

        final ByteString byteStringSuffix = byteString.substring(6);
        assertEquals("textghijk", byteStringSuffix.toString("UTF-8"));
        assertEquals(9, byteStringSuffix.length());

        final ByteString byteStringInnerText = byteString.substring(6, 4);
        assertEquals("text", byteStringInnerText.toString("UTF-8"));
        assertEquals(4, byteStringInnerText.length());

        IndexOutOfBoundsException expected = null;
        try {
            byteString.substring(5, 11);
        } catch (IndexOutOfBoundsException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    @Test
    public void compareTo() {
        final ByteString byteString1 = ByteString.getBytes("abcde", Charset.defaultCharset());
        final ByteString byteString2 = ByteString.getBytes("abcde", Charset.defaultCharset());
        assertEquals(0, byteString1.compareTo(byteString1));
        assertEquals(0, byteString2.compareTo(byteString2));
        assertEquals(0, byteString1.compareTo(byteString2));
        assertEquals(0, byteString2.compareTo(byteString1));

        final ByteString byteString3 = ByteString.getBytes("abcdefgh", Charset.defaultCharset());
        assertTrue(byteString3.compareTo(byteString1) > 0);
        assertTrue(byteString1.compareTo(byteString3) < 0);

        final ByteString byteString4 = ByteString.getBytes("fghij", Charset.defaultCharset());
        assertTrue(byteString4.compareTo(byteString1) > 0);
        assertTrue(byteString1.compareTo(byteString4) < 0);

        assertTrue(byteString3.compareTo(byteString4) < 0);
        assertTrue(byteString4.compareTo(byteString3) > 0);
    }

    @Test
    public void equality() throws UnsupportedEncodingException {
        final ByteBuffer byteBuffer = ByteBuffer.allocate(5);
        byteBuffer.put("abcde".getBytes()).flip();
        final ByteString[] equalByteStrings = {
                ByteString.of("abcde".getBytes()),
                ByteString.copyOf("abcde".getBytes(), 0, 5),
                ByteString.copyOf("#@$abcde".getBytes(), 3, 5),
                ByteString.copyOf("abcdefghij".getBytes(), 0, 5),
                ByteString.copyOf("12345abcde67890".getBytes(), 5, 5),
                ByteString.getBytes("abcde", "UTF-8"),
                ByteString.getBytes("abcde", Charset.defaultCharset()),
                ByteString.getBytes(byteBuffer), null, null, null};
        byteBuffer.limit(4);
        byteBuffer.position(0);
        final ByteString[] differentByteStrings = {ByteString.of("abcdef".getBytes()),
                ByteString.of("xyzabcde".getBytes()),
                ByteString.of("yzabcdefg".getBytes()),
                ByteString.copyOf(new byte[0], 0, 0),
                ByteString.getBytes("12345", "UTF-8"),
                ByteString.getBytes("abcda", Charset.defaultCharset()),
            ByteString.getBytes(byteBuffer)}; // "abcd"
        equalByteStrings[8] = differentByteStrings[0].substring(0, 5);
        equalByteStrings[9] = differentByteStrings[1].substring(3);
        equalByteStrings[10] = differentByteStrings[2].substring(2, 5);

        checkAllAreEqual(equalByteStrings);
        for (ByteString byteString: differentByteStrings) {
            checkAllAreNotEqual(byteString, differentByteStrings);
        }
        assertFalse(equalByteStrings[0].equals("abcde"));
    }

    @Test
    public void readAndWriteByteString() throws IOException, ClassNotFoundException {
        final ByteString byteString1 = ByteString.getBytes("", Charset.defaultCharset());
        final ByteString byteString2 = ByteString.getBytes("read me", Charset.defaultCharset());
        final ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        final ObjectOutput output = new ObjectOutputStream(new BufferedOutputStream(byteOutputStream));
        try{
          output.writeObject(byteString1);
          output.writeObject(byteString2);
        }
        finally{
          output.close();
        }
 
        final ByteArrayInputStream byteInputStream = new ByteArrayInputStream(byteOutputStream.toByteArray());
        final ObjectInput input = new ObjectInputStream(new BufferedInputStream(byteInputStream));
        final ByteString recoveredByteString1, recoveredByteString2;
        try{
          recoveredByteString1 = (ByteString) input.readObject();
          recoveredByteString2 = (ByteString) input.readObject();
        }
        finally{
          input.close();
        }
        assertEquals(byteString1, recoveredByteString1);
        assertEquals(byteString2, recoveredByteString2);
    }

    private static void checkEqual(String message, byte[] bytes) {
        assertArrayEquals(bytes, message.getBytes());
    }

    private static void checkEqual(String message, byte[] bytes, int offset) {
        String finalMessage = "";
        for (int i = 0; i < offset; i++) {
            finalMessage += '\0';
        }
        finalMessage += message;
        for (int i = offset + message.length(); i < bytes.length; i++) {
            finalMessage += '\0';
        }
        checkEqual(finalMessage, bytes);
    }

    private void checkAllAreEqual(ByteString...byteStrings) {
        for (ByteString byteString: byteStrings) {
            for (ByteString compareTo: byteStrings) {
                assertEquals(byteString, compareTo);
                // two calls to hashCode must return the same result
                assertEquals(byteString.hashCode(), byteString.hashCode());
                assertEquals(compareTo.hashCode(), compareTo.hashCode());
                // if byteString equals to compareTo, they must have the same hashCode
                assertEquals(byteString.hashCode(), compareTo.hashCode());
            }
        }
    }

    private void checkAllAreNotEqual(ByteString firstByteString, ByteString...byteStrings) {
        ByteString byteString = firstByteString;
        int i = -1;
        do {
            for (ByteString compareTo: byteStrings) {
                if (byteString != compareTo) {
                    assertFalse(byteString.toString() + " is equal to " + compareTo, byteString.equals((Object)compareTo));
                    assertFalse(byteString.toString() + " is equal to " + compareTo, byteString.equals(compareTo));
                    // two calls to hashCode must return the same result
                    assertEquals(byteString.hashCode(), byteString.hashCode());
                    assertEquals(compareTo.hashCode(), compareTo.hashCode());
                }
            }
        } while(++i < byteStrings.length && (byteString = byteStrings[i]) != null);
    }
}
