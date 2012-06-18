/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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

package org.xnio;

import static org.junit.Assert.assertArrayEquals;
import static org.xnio.AssertReadWrite.assertReadMessage;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.IntBuffer;
import java.nio.InvalidMarkException;
import java.nio.LongBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.ShortBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Random;

import junit.framework.TestCase;

import org.xnio.BufferAllocator;
import org.xnio.Buffers;
import org.xnio.ByteBufferSlicePool;
import org.xnio.Pool;
import org.xnio.Pooled;

/**
 * Test for {@link Buffers}.
 * 
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
public final class BuffersTestCase extends TestCase {

    private void doTestFlip(Buffer buffer) {
        final int pos = buffer.position();
        final int cap = buffer.capacity();
        assertEquals(cap, buffer.limit());
        assertSame(buffer, Buffers.flip(buffer));
        assertEquals(0, buffer.position());
        assertEquals(pos, buffer.limit());
        assertEquals(cap, buffer.capacity());
    }

    public void testFlipByte() {
        ByteBuffer buf = ByteBuffer.allocate(100);
        final byte[] data = {5, 4, 3, 2, 1, 0};
        final byte[] data2 = new byte[data.length];
        buf.put(data);
        doTestFlip(buf);
        buf.get(data2);
        assertTrue(Arrays.equals(data, data2));
        assertFalse(buf.hasRemaining());
    }

    public void testFlipChar() {
        CharBuffer buf = CharBuffer.allocate(100);
        final char[] data = {5, 4, 3, 2, 1, 0};
        final char[] data2 = new char[data.length];
        buf.put(data);
        doTestFlip(buf);
        buf.get(data2);
        assertTrue(Arrays.equals(data, data2));
        assertFalse(buf.hasRemaining());
    }

    private void doTestClear(Buffer buffer) {
        assertFalse(buffer.position() == 0);
        final int cap = buffer.capacity();
        assertFalse(buffer.limit() == cap);
        assertSame(buffer, Buffers.clear(buffer));
        assertEquals(0, buffer.position());
        assertEquals(cap, buffer.limit());
        assertEquals(cap, buffer.capacity());
    }

    public void testClearByte() {
        final int sz = 100;
        ByteBuffer buf = ByteBuffer.allocate(sz);
        final byte[] data = {5, 4, 3, 2, 1, 0};
        buf.put(data);
        buf.put(data);
        buf.put(data);
        buf.flip();
        buf.get();
        buf.get();
        doTestClear(buf);
    }

    public void testClearChar() {
        final int sz = 100;
        CharBuffer buf = CharBuffer.allocate(sz);
        final char[] data = {5, 4, 3, 2, 1, 0};
        buf.put(data);
        buf.put(data);
        buf.put(data);
        buf.flip();
        buf.get();
        buf.get();
        doTestClear(buf);
    }

    private void doTestLimit(Buffer buffer) {
        assertFalse(buffer.limit() == 50);
        assertSame(buffer, Buffers.limit(buffer, 50));
        assertEquals(50, buffer.limit());
    }

    public void testLimitByte() {
        final int sz = 100;
        ByteBuffer buf = ByteBuffer.allocate(sz);
        final byte[] data = {5, 4, 3, 2, 1, 0};
        buf.put(data);
        buf.put(data);
        buf.put(data);
        buf.flip();
        buf.get();
        buf.get();
        doTestLimit(buf);
    }

    public void testLimitChar() {
        final int sz = 100;
        CharBuffer buf = CharBuffer.allocate(sz);
        final char[] data = {5, 4, 3, 2, 1, 0};
        buf.put(data);
        buf.put(data);
        buf.put(data);
        buf.flip();
        buf.get();
        buf.get();
        doTestLimit(buf);
    }

    private void doTestMarkReset(Buffer buffer) {
        final int p = buffer.position();
        assertSame(buffer, Buffers.mark(buffer));
        assertSame(buffer, Buffers.skip(buffer, 10));
        assertFalse(buffer.position() == p);
        assertSame(buffer, Buffers.reset(buffer));
        assertTrue(buffer.position() == p);
        assertSame(buffer, Buffers.skip(buffer, 10));
        assertFalse(buffer.position() == p);
        assertSame(buffer, Buffers.reset(buffer));
        assertTrue(buffer.position() == p);
    }

    public void testMarkResetByte() {
        final int sz = 100;
        ByteBuffer buf = ByteBuffer.allocate(sz);
        final byte[] data = {5, 4, 3, 2, 1, 0};
        buf.put(data);
        buf.put(data);
        buf.put(data);
        buf.flip();
        buf.get();
        buf.get();
        doTestMarkReset(buf);
    }

    public void testMarkResetChar() {
        final int sz = 100;
        CharBuffer buf = CharBuffer.allocate(sz);
        final char[] data = {5, 4, 3, 2, 1, 0};
        buf.put(data);
        buf.put(data);
        buf.put(data);
        buf.flip();
        buf.get();
        buf.get();
        doTestMarkReset(buf);
    }

    private void doTestPosition(Buffer buffer) {
        assertSame(buffer, Buffers.position(buffer, 25));
        assertEquals(25, buffer.position());
        assertSame(buffer, Buffers.position(buffer, 0));
        assertEquals(0, buffer.position());
        assertSame(buffer, Buffers.position(buffer, 100));
        assertEquals(100, buffer.position());
    }

    public void testPositionByte() {
        doTestPosition(ByteBuffer.allocate(200));
    }

    public void testPositionChar() {
        doTestPosition(CharBuffer.allocate(200));
    }

    private void doTestRewind(Buffer buffer) {
        assertSame(buffer, Buffers.position(buffer, 45));
        assertEquals(45, buffer.position());
        assertSame(buffer, Buffers.rewind(buffer));
        assertEquals(0, buffer.position());
    }

    public void testRewindByte() {
        doTestRewind(ByteBuffer.allocate(200));
    }

    public void testRewindChar() {
        doTestRewind(CharBuffer.allocate(200));
    }

    public void tetsFlipClearLimitMarkPositionResetAndRewind() {
        final ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.position(5);
        Buffers.flip(buffer);
        assertEquals(0, buffer.position());
        assertEquals(5, buffer.limit());
        Buffers.clear(buffer);
        assertEquals(0, buffer.position());
        assertEquals(buffer.capacity(), buffer.limit());

        assertEquals(10, buffer.limit());
        Buffers.limit(buffer, 7);
        assertEquals(7, buffer.limit());
        buffer.limit(10);

        buffer.position(4);
        Buffers.mark(buffer);
        buffer.position(7);;
        Buffers.reset(buffer);
        assertEquals(4, buffer.position());

        Buffers.mark(buffer);
        Buffers.rewind(buffer);
        assertEquals(0, buffer.position());
        InvalidMarkException expected = null;
        try {
            buffer.reset();
        } catch (InvalidMarkException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    public void testPositiveSliceByte() {
        final ByteBuffer buf = ByteBuffer.allocate(200);
        final byte[] data = {5, 10, 15, 20, 0, 5, 10, 15};
        buf.put(data).put(data).put(data).put(data).put(data).put(data).put(data);
        buf.flip();
        final int lim = buf.limit();
        final int cap = buf.capacity();
        final int sz = 7;
        final ByteBuffer slice1 = Buffers.slice(buf, sz);
        assertEquals(0, slice1.position());
        assertEquals(sz, slice1.capacity());
        assertEquals(sz, slice1.limit());
        assertEquals(sz, buf.position());
        assertEquals(lim, buf.limit());
        assertEquals(cap, buf.capacity());
        final ByteBuffer slice2 = Buffers.slice(buf, sz * 2);
        assertEquals(0, slice2.position());
        assertEquals(sz * 2, slice2.capacity());
        assertEquals(sz * 2, slice2.limit());
        assertEquals(sz * 3, buf.position());
    }

    public void testPositiveSliceChar() {
        final CharBuffer buf = CharBuffer.allocate(200);
        final char[] data = {5, 10, 15, 20, 0, 5, 10, 15};
        buf.put(data).put(data).put(data).put(data).put(data).put(data).put(data);
        buf.flip();
        final int lim = buf.limit();
        final int cap = buf.capacity();
        final int sz = 7;
        final CharBuffer slice1 = Buffers.slice(buf, sz);
        assertEquals(0, slice1.position());
        assertEquals(sz, slice1.capacity());
        assertEquals(sz, slice1.limit());
        assertEquals(sz, buf.position());
        assertEquals(lim, buf.limit());
        assertEquals(cap, buf.capacity());
        final CharBuffer slice2 = Buffers.slice(buf, sz * 2);
        assertEquals(0, slice2.position());
        assertEquals(sz * 2, slice2.capacity());
        assertEquals(sz * 2, slice2.limit());
        assertEquals(sz * 3, buf.position());
    }

    public void testNegativeSliceByte() {
        final ByteBuffer buf = ByteBuffer.allocate(200);
        final byte[] data = {5, 10, 15, 20, 0, 5, 10, 15};
        buf.put(data).put(data).put(data).put(data).put(data).put(data).put(data);
        buf.flip();
        final int lim = buf.limit();
        final int cap = buf.capacity();
        final int sz = 7;
        final ByteBuffer slice1 = Buffers.slice(buf, sz - lim);
        assertEquals(0, slice1.position());
        assertEquals(sz, slice1.capacity());
        assertEquals(sz, slice1.limit());
        assertEquals(sz, buf.position());
        assertEquals(lim, buf.limit());
        assertEquals(cap, buf.capacity());
        final ByteBuffer slice2 = Buffers.slice(buf, sz * 2 - lim + sz);
        assertEquals(0, slice2.position());
        assertEquals(sz * 2, slice2.capacity());
        assertEquals(sz * 2, slice2.limit());
        assertEquals(sz * 3, buf.position());
    }

    public void testNegativeSliceChar() {
        final CharBuffer buf = CharBuffer.allocate(200);
        final char[] data = {5, 10, 15, 20, 0, 5, 10, 15};
        buf.put(data).put(data).put(data).put(data).put(data).put(data).put(data);
        buf.flip();
        final int lim = buf.limit();
        final int cap = buf.capacity();
        final int sz = 7;
        final CharBuffer slice1 = Buffers.slice(buf, sz - lim);
        assertEquals(0, slice1.position());
        assertEquals(sz, slice1.capacity());
        assertEquals(sz, slice1.limit());
        assertEquals(sz, buf.position());
        assertEquals(lim, buf.limit());
        assertEquals(cap, buf.capacity());
        final CharBuffer slice2 = Buffers.slice(buf, sz * 2 - lim + sz);
        assertEquals(0, slice2.position());
        assertEquals(sz * 2, slice2.capacity());
        assertEquals(sz * 2, slice2.limit());
        assertEquals(sz * 3, buf.position());
    }

    public void testMultipleByeSlices() {
        final ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put("abcdefghij".getBytes()).flip();
        final ByteBuffer slice1 = Buffers.slice(buffer, 3);
        assertEquals(3, slice1.limit());
        assertEquals('a', slice1.get());
        assertEquals('b', slice1.get());
        assertEquals('c', slice1.get());
        BufferUnderflowException expected = null;
        try {
            slice1.get();
        } catch (BufferUnderflowException e) {
            expected = e;
        }
        assertNotNull(expected);
        assertEquals(3, buffer.position());
        final ByteBuffer slice2 = Buffers.slice(buffer, 1);
        assertEquals('d', slice2.get());
        expected = null;
        try {
            slice2.get();
        } catch (BufferUnderflowException e) {
            expected = e;
        }
        assertNotNull(expected);
        assertEquals(4, buffer.position());
        final ByteBuffer slice3 = Buffers.slice(buffer, -2);
        assertEquals(8, buffer.position());
        assertEquals(10, buffer.limit());
        assertEquals('e', slice3.get());
        assertEquals('f', slice3.get());
        assertEquals('g', slice3.get());
        assertEquals('h', slice3.get());
        final ByteBuffer slice4 = Buffers.slice(buffer, 2);
        assertEquals(2, slice4.limit());
        assertEquals('i', slice4.get());
        assertEquals('j', slice4.get());
        expected = null;
        try {
            slice3.get();
        } catch (BufferUnderflowException e) {
            expected = e;
        }
        assertNotNull(expected);
        assertEquals(10, buffer.position());
        // invalid slice
        expected = null;
        try {
            Buffers.slice(buffer, 1);
        } catch(BufferUnderflowException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            Buffers.slice(buffer, -1);
        } catch(BufferUnderflowException e) {
            expected = e;
        }
        assertNotNull(expected);
        // check slice properties
        buffer.put(1, (byte) 'z');
        assertEquals('z', slice1.get(1));
        slice2.put(0, (byte) 'y');
        assertEquals('y', buffer.get(3));
        buffer.put(5, (byte) 'x');
        assertEquals('x', slice3.get(1));
        slice4.put(0, (byte) 'w');
        assertEquals('w', buffer.get(8));
    }

    public void testMultipleCharSlices() {
        final CharBuffer buffer = CharBuffer.allocate(10);
        buffer.put(new char[] {'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j'}).flip();
        final CharBuffer slice1 = Buffers.slice(buffer, 3);
        assertEquals(3, slice1.limit());
        assertEquals('a', slice1.get());
        assertEquals('b', slice1.get());
        assertEquals('c', slice1.get());
        BufferUnderflowException expected = null;
        try {
            slice1.get();
        } catch (BufferUnderflowException e) {
            expected = e;
        }
        assertNotNull(expected);
        assertEquals(3, buffer.position());
        final CharBuffer slice2 = Buffers.slice(buffer, 1);
        assertEquals('d', slice2.get());
        expected = null;
        try {
            slice2.get();
        } catch (BufferUnderflowException e) {
            expected = e;
        }
        assertNotNull(expected);
        assertEquals(4, buffer.position());
        final CharBuffer slice3 = Buffers.slice(buffer, -2);
        assertEquals(8, buffer.position());
        assertEquals(10, buffer.limit());
        assertEquals('e', slice3.get());
        assertEquals('f', slice3.get());
        assertEquals('g', slice3.get());
        assertEquals('h', slice3.get());
        final CharBuffer slice4 = Buffers.slice(buffer, 2);
        assertEquals(2, slice4.limit());
        assertEquals('i', slice4.get());
        assertEquals('j', slice4.get());
        expected = null;
        try {
            slice3.get();
        } catch (BufferUnderflowException e) {
            expected = e;
        }
        assertNotNull(expected);
        assertEquals(10, buffer.position());
        // invalid slice
        expected = null;
        try {
            Buffers.slice(buffer, 1);
        } catch(BufferUnderflowException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            Buffers.slice(buffer, -1);
        } catch(BufferUnderflowException e) {
            expected = e;
        }
        assertNotNull(expected);
        // check slice properties
        slice1.put(0, 'k');
        assertEquals('k', buffer.get(0));
        buffer.put(3, 'l');
        assertEquals('l', slice2.get(0));
        slice3.put(3, 'm');
        assertEquals('m', buffer.get(7));
        buffer.put(8, 'n');
        assertEquals('n', slice4.get(0));
    }

    public void testMultipleShortSlices() {
        final ShortBuffer buffer = ShortBuffer.allocate(10);
        buffer.put(new short[] {1, 2, 3, 4, 5, 6, 7, 8, 9}).flip();
        final ShortBuffer slice1 = Buffers.slice(buffer, 2);
        assertEquals(2, slice1.limit());
        assertEquals(1, slice1.get());
        assertEquals(2, slice1.get());
        BufferUnderflowException expected = null;
        try {
            slice1.get();
        } catch (BufferUnderflowException e) {
            expected = e;
        }
        assertNotNull(expected);
        assertEquals(2, buffer.position());
        final ShortBuffer slice2 = Buffers.slice(buffer, 1);
        assertEquals(3, slice2.get());
        expected = null;
        try {
            slice2.get();
        } catch (BufferUnderflowException e) {
            expected = e;
        }
        assertNotNull(expected);
        assertEquals(3, buffer.position());
        final ShortBuffer slice3 = Buffers.slice(buffer, -3);
        assertEquals(6, buffer.position());
        assertEquals(9, buffer.limit());
        assertEquals(3, slice3.limit());
        assertEquals(4, slice3.get());
        assertEquals(5, slice3.get());
        assertEquals(6, slice3.get());
        final ShortBuffer slice4 = Buffers.slice(buffer, 3);
        assertEquals(3, slice4.limit());
        assertEquals(9, buffer.position());
        assertEquals(7, slice4.get());
        assertEquals(8, slice4.get());
        assertEquals(9, slice4.get());
        expected = null;
        try {
            slice3.get();
        } catch (BufferUnderflowException e) {
            expected = e;
        }
        assertNotNull(expected);
        assertEquals(9, buffer.position());
        // invalid slice
        expected = null;
        try {
            Buffers.slice(buffer, 1);
        } catch(BufferUnderflowException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            Buffers.slice(buffer, -1);
        } catch(BufferUnderflowException e) {
            expected = e;
        }
        assertNotNull(expected);
        // check slice properties
        buffer.put(1, (short) 10);
        assertEquals(10, slice1.get(1));
        slice2.put(0, (short) 11);
        assertEquals(11, buffer.get(2));
        buffer.put(3, (short) 12);
        assertEquals(12, slice3.get(0));
        slice4.put(2, (short) 13);
        assertEquals(13, buffer.get(8));
    }

    public void testMultipleIntSlices() {
        final IntBuffer buffer = IntBuffer.allocate(15);
        buffer.put(new int[]{2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37}).flip();
        buffer.position(1);
        final IntBuffer slice1 = Buffers.slice(buffer, 3);
        assertEquals(3, slice1.limit());
        assertEquals(3, slice1.get());
        assertEquals(5, slice1.get());
        assertEquals(7, slice1.get());
        BufferUnderflowException expected = null;
        try {
            slice1.get();
        } catch (BufferUnderflowException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            Buffers.slice(buffer, 31);
        } catch (BufferUnderflowException e) {
            expected = e;
        }
        assertNotNull(expected);
        assertEquals(4, buffer.position());
        final IntBuffer slice2 = Buffers.slice(buffer, 5);
        assertEquals(5, slice2.limit());
        assertEquals(11, slice2.get());
        assertEquals(13, slice2.get());
        assertEquals(17, slice2.get());
        assertEquals(19, slice2.get());
        assertEquals(23, slice2.get());
        expected = null;
        try {
            Buffers.slice(buffer, -7);
        } catch (BufferUnderflowException e) {
            expected = e;
        }
        assertEquals(9, buffer.position());
        buffer.get();
        final IntBuffer slice3 = Buffers.slice(buffer, -2);
        expected = null;
        try {
            slice3.get();
        } catch (BufferUnderflowException e) {
            expected = e;
        }
        final IntBuffer slice4 = Buffers.slice(buffer, 2);
        assertEquals(2, slice4.limit());
        assertEquals(31, slice4.get());
        assertEquals(37, slice4.get());
        assertEquals(12, buffer.position());
        // check slice properties
        slice1.put(0, 41);
        assertEquals(41, buffer.get(1));
        buffer.put(4, 43);
        assertEquals(43, slice2.get(0));
        slice4.put(1, 47);
        assertEquals(47, buffer.get(11));
    }

    public void testMultipleLongSlices() {
        final LongBuffer buffer = LongBuffer.allocate(15);
        buffer.put(new long[]{1, 4, 9, 16, 25, 36, 49, 64, 81, 100, 121}).flip();
        buffer.position(1);
        final LongBuffer slice1 = Buffers.slice(buffer, 3);
        assertEquals(3, slice1.limit());
        assertEquals(4, slice1.get());
        assertEquals(9, slice1.get());
        assertEquals(16, slice1.get());
        BufferUnderflowException expected = null;
        try {
            slice1.get();
        } catch (BufferUnderflowException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            Buffers.slice(buffer, 144);
        } catch (BufferUnderflowException e) {
            expected = e;
        }
        assertNotNull(expected);
        assertEquals(4, buffer.position());
        final LongBuffer slice2 = Buffers.slice(buffer, 5);
        assertEquals(5, slice2.limit());
        assertEquals(25, slice2.get());
        assertEquals(36, slice2.get());
        assertEquals(49, slice2.get());
        assertEquals(64, slice2.get());
        assertEquals(81, slice2.get());
        expected = null;
        try {
            Buffers.slice(buffer, -169);
        } catch (BufferUnderflowException e) {
            expected = e;
        }
        assertEquals(9, buffer.position());
        buffer.get();
        final LongBuffer slice3 = Buffers.slice(buffer, -1);
        assertEquals(0, slice3.limit());
        final LongBuffer slice4 = Buffers.slice(buffer, 1);
        assertEquals(1, slice4.limit());
        assertEquals(121, slice4.get());
        // check slice properties
        buffer.put(1, 144);
        assertEquals(144, slice1.get(0));
        slice2.put(0, 169);
        assertEquals(169, buffer.get(4));
        buffer.put(10, 196);
        assertEquals(196, slice4.get(0));
    }

    private void assertBufferContent(ByteBuffer buffer, String content) {
        assertReadMessage(buffer, content);
    }

    public void testCopyFullBuffer() {
        final ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put("1234567890".getBytes()).flip();
        final ByteBuffer copy1 = Buffers.copy(buffer, 10, BufferAllocator.BYTE_BUFFER_ALLOCATOR);
        assertEquals(buffer.limit(), buffer.position());
        assertEquals(copy1.limit(), copy1.position());
        assertArrayEquals(buffer.array(), copy1.array());

        buffer.position(0);
        final ByteBuffer copy2 = ByteBuffer.allocate(10);
        assertEquals(10, Buffers.copy(copy2, buffer));
        assertEquals(buffer.limit(), buffer.position());
        assertEquals(copy2.limit(), copy2.position());
        assertArrayEquals(buffer.array(), copy2.array());

        buffer.position(0);
        final ByteBuffer[] copy3 = new ByteBuffer[] {ByteBuffer.allocate(5), ByteBuffer.allocate(5)};
        assertEquals(10 , Buffers.copy(copy3, 0, 2, buffer));
        assertEquals(buffer.limit(), buffer.position());
        assertEquals(copy3[0].limit(), copy3[0].position());
        assertEquals(copy3[1].limit(), copy3[1].position());
        assertBufferContent(copy3[0], "12345");
        assertBufferContent(copy3[1], "67890");

        buffer.position(0);
        final ByteBuffer copy4 = ByteBuffer.allocate(10);
        assertEquals(10, Buffers.copy(10, copy4, buffer));
        assertEquals(buffer.limit(), buffer.position());
        assertEquals(copy4.limit(), copy4.position());
        assertArrayEquals(buffer.array(), copy4.array());

        buffer.position(0);
        final ByteBuffer[] copy5 = new ByteBuffer[] {ByteBuffer.allocate(5), ByteBuffer.allocate(5), ByteBuffer.allocate(5)};
        assertEquals(10, Buffers.copy(10, copy5, 1, 2, buffer));
        assertEquals(buffer.limit(), buffer.position());
        assertEquals(copy5[1].limit(), copy5[1].position());
        assertEquals(copy5[2].limit(), copy5[2].position());
        assertBufferContent(copy5[1], "12345");
        assertBufferContent(copy5[2], "67890");

        buffer.position(0);
        final ByteBuffer[] copy6 = new ByteBuffer[] {ByteBuffer.allocate(5), ByteBuffer.allocate(5), ByteBuffer.allocate(5)};
        assertEquals(10, Buffers.copy(30, copy6, 0, 3, buffer));
        assertEquals(buffer.limit(), buffer.position());
        assertEquals(copy6[0].limit(), copy6[0].position());
        assertEquals(copy6[1].limit(), copy6[1].position());
        assertBufferContent(copy6[0], "12345");
        assertBufferContent(copy6[1], "67890");

        //check copy properties
        buffer.position(0);
        buffer.put((byte) '0');
        buffer.put((byte) '9');
        assertEquals('1', copy1.get(0));
        assertEquals('2', copy1.get(1));
        assertEquals('1', copy2.get(0));
        assertEquals('2', copy2.get(1));
        assertEquals('1', copy3[0].get(0));
        assertEquals('2', copy3[0].get(1));
        assertEquals('1', copy4.get(0));
        assertEquals('2', copy4.get(1));
        assertEquals('1', copy5[1].get(0));
        assertEquals('2', copy5[1].get(1));
        assertEquals('1', copy6[0].get(0));
        assertEquals('2', copy6[0].get(1));
        copy1.put(2, (byte) '8');
        copy2.put(3, (byte) '7');
        copy3[0].put(4, (byte) '6');
        copy4.put(5, (byte)'5');
        copy5[2].put(1, (byte)'4');
        copy6[1].put(2, (byte) '3');
        assertEquals('3', buffer.get(2));
        assertEquals('4', buffer.get(3));
        assertEquals('5', buffer.get(4));
        assertEquals('6', buffer.get(5));
        assertEquals('7', buffer.get(6));
        assertEquals('8', buffer.get(7));
    }

    public void testCopyPartialBuffer() {
        final ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put("1234567890".getBytes()).flip();
        final ByteBuffer copy1 = Buffers.copy(buffer, 5, BufferAllocator.BYTE_BUFFER_ALLOCATOR);
        assertEquals(5, buffer.position());
        assertEquals(copy1.limit(), copy1.position());
        assertBufferContent(copy1, "12345");

        buffer.position(0);
        final ByteBuffer copy2 = ByteBuffer.allocate(7);
        assertEquals(7, Buffers.copy(copy2, buffer));
        assertEquals(7, buffer.position());
        assertEquals(copy2.limit(), copy2.position());
        assertBufferContent(copy2, "1234567");

        buffer.position(0);
        final ByteBuffer[] copy3 = new ByteBuffer[] {ByteBuffer.allocate(2), ByteBuffer.allocate(2)};
        assertEquals(4, Buffers.copy(copy3, 0, 2, buffer));
        assertEquals(4, buffer.position());
        assertEquals(copy3[0].limit(), copy3[0].position());
        assertEquals(copy3[1].limit(), copy3[1].position());
        assertBufferContent(copy3[0], "12");
        assertBufferContent(copy3[1], "34");

        buffer.position(0);
        final ByteBuffer copy4 = ByteBuffer.allocate(10);
        assertEquals(1, Buffers.copy(1, copy4, buffer));
        assertEquals(1, buffer.position());
        assertEquals(1, copy4.position());
        assertEquals('1', copy4.get(0));

        buffer.position(0);
        final ByteBuffer[] copy5 = new ByteBuffer[] {ByteBuffer.allocate(3), ByteBuffer.allocate(4), ByteBuffer.allocate(6)};
        assertEquals(9, Buffers.copy(9, copy5, 1, 2, buffer));
        assertEquals(9, buffer.position());
        assertEquals(copy5[1].limit(), copy5[1].position());
        assertEquals(5, copy5[2].position());
        assertBufferContent(copy5[1], "1234");
        assertBufferContent(copy5[2], "56789");

        //check copy properties
        buffer.position(0);
        buffer.put((byte) '0');
        buffer.put((byte) '9');
        assertEquals('1', copy1.get(0));
        assertEquals('2', copy1.get(1));
        assertEquals('1', copy2.get(0));
        assertEquals('2', copy2.get(1));
        assertEquals('1', copy3[0].get(0));
        assertEquals('2', copy3[0].get(1));
        assertEquals('1', copy4.get(0));
        assertEquals('1', copy5[1].get(0));
        assertEquals('2', copy5[1].get(1));
        copy1.put(2, (byte) '8');
        copy2.put(3, (byte) '7');
        copy3[1].put(1, (byte) '6');
        copy4.put(5, (byte)'5');
        copy5[2].put(1, (byte)'4');
        assertEquals('3', buffer.get(2));
        assertEquals('4', buffer.get(3));
        assertEquals('5', buffer.get(4));
        assertEquals('6', buffer.get(5));
        assertEquals('7', buffer.get(6));
    }

    public void testCopyToSmallerBuffer() {
        final ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put("1234567890".getBytes()).flip();
        final ByteBuffer copy1 = ByteBuffer.allocate(7);
        assertEquals(7, Buffers.copy(copy1, buffer));
        assertEquals(7, buffer.position());
        assertEquals(copy1.limit(), copy1.position());
        assertBufferContent(copy1, "1234567");

        buffer.position(0);
        final ByteBuffer[] copy2 = new ByteBuffer[] {ByteBuffer.allocate(2)};
        assertEquals(2, Buffers.copy(copy2, 0, 1, buffer));
        assertEquals(2, buffer.position());
        assertEquals(copy2[0].limit(), copy2[0].position());
        assertBufferContent(copy2[0], "12");

        buffer.position(0);
        final ByteBuffer copy3 = ByteBuffer.allocate(1);
        assertEquals(1, Buffers.copy(10, copy3, buffer));
        assertEquals(1, buffer.position());
        assertEquals(1, copy3.position());
        assertEquals('1', copy3.get(0));

        buffer.position(0);
        final ByteBuffer[] copy4 = new ByteBuffer[] {ByteBuffer.allocate(3), ByteBuffer.allocate(5)};
        assertEquals(3, Buffers.copy(15, copy4, 0, 1, buffer));
        assertEquals(3, buffer.position());
        assertEquals(copy4[0].limit(), copy4[0].position());
        assertBufferContent(copy4[0], "123");

        //check copy properties
        buffer.position(0);
        buffer.put((byte) '0');
        buffer.put((byte) '9');
        assertEquals('1', copy1.get(0));
        assertEquals('2', copy1.get(1));
        assertEquals('1', copy2[0].get(0));
        assertEquals('2', copy2[0].get(1));
        assertEquals('1', copy3.get(0));
        assertEquals('1', copy4[0].get(0));
        assertEquals('2', copy4[0].get(1));
        copy1.put(2, (byte) '8');
        copy1.put(3, (byte) '7');
        copy2[0].put(0, (byte) '6');
        copy3.put(0, (byte)'5');
        copy4[1].put(2, (byte)'4');
        assertEquals('0', buffer.get(0));
        assertEquals('9', buffer.get(1));
        assertEquals('3', buffer.get(2));
        assertEquals('4', buffer.get(3));
        assertEquals('5', buffer.get(4));
        assertEquals('6', buffer.get(5));
        assertEquals('7', buffer.get(6));
    }

    public void testBufferUnderflowExceptionThrownByCopy() {
        final ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put("1234567890".getBytes()).flip();
        buffer.position(2);
        BufferUnderflowException expected = null;
        try {
            Buffers.copy(buffer, 20, BufferAllocator.DIRECT_BYTE_BUFFER_ALLOCATOR);
        } catch (BufferUnderflowException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            Buffers.copy(buffer, 9, BufferAllocator.DIRECT_BYTE_BUFFER_ALLOCATOR);
        } catch (BufferUnderflowException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            Buffers.copy(buffer, -15, BufferAllocator.DIRECT_BYTE_BUFFER_ALLOCATOR);
        } catch (BufferUnderflowException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            Buffers.copy(buffer, -9, BufferAllocator.DIRECT_BYTE_BUFFER_ALLOCATOR);
        } catch (BufferUnderflowException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    // FIXME XNIO-120
    public void testCopyWithNegativeSliceSize() {
        final ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put("1234567890".getBytes()).flip();
        final ByteBuffer copy1 = Buffers.copy(buffer, -2, BufferAllocator.DIRECT_BYTE_BUFFER_ALLOCATOR);
        assertEquals(8, buffer.position());
        assertEquals(2, copy1.limit());
        assertEquals(2, copy1.position());
        assertBufferContent(copy1, "90");

        buffer.position(0);
        final ByteBuffer copy2 = ByteBuffer.allocate(6);
        assertEquals(6, Buffers.copy(-4, copy2, buffer));
        assertEquals(6, buffer.position());
        assertEquals(6, copy2.limit());
        assertEquals(6, copy2.position());
        assertBufferContent(copy2, "123456");

        final ByteBuffer copy3 = ByteBuffer.allocate(10);
        assertEquals(0, Buffers.copy(-4, copy3, buffer));
        assertEquals(0, Buffers.copy(-40, copy3, buffer));

        buffer.position(0);
        final ByteBuffer[] copy4 = new ByteBuffer[] {ByteBuffer.allocate(3), ByteBuffer.allocate(2)};
        UnsupportedOperationException expected = null;
        try {
            assertEquals(5, Buffers.copy(-2, copy4, 0, 2, buffer));
        } catch (UnsupportedOperationException e) {
            expected = e;
        }
        assertNotNull(expected);
// XNIO-120 these test assertions are wrong, negative count must work just like negative slice size
//        assertEquals(5, buffer.position());
//        assertEquals(copy4[0].limit(), copy4[0].position());
//        assertEquals(copy4[1].limit(), copy4[1].position());
//        assertBufferContent(copy4[0], "123");
//        assertBufferContent(copy4[1], "45");

        buffer.position(0);
        final ByteBuffer[] copy5 = new ByteBuffer[] {ByteBuffer.allocate(3), ByteBuffer.allocate(2)};
        expected = null;
        try {
            assertEquals(4, Buffers.copy(-6, copy5, 0, 2, buffer));
        } catch (UnsupportedOperationException e) {
            expected = e;
        }
        assertNotNull(expected);
// XNIO-120 these test assertions are wrong, negative count must work just like negative slice size
//        assertEquals(4, buffer.position());
//        assertEquals(copy5[0].limit(), copy5[0].position());
//        assertEquals(1, copy5[1].position());
//        assertBufferContent(copy5[0], "123");
//        assertBufferContent(copy5[1], "4");

        final ByteBuffer[] copy6 = new ByteBuffer[] {ByteBuffer.allocate(5), ByteBuffer.allocate(5)};
        expected = null;
        try {
            assertEquals(0, Buffers.copy(-6, copy6, 0, 2, buffer));
        } catch (UnsupportedOperationException e) {
            expected = e;
        }
        expected = null;
        try {
            assertEquals(0, Buffers.copy(-10, copy6, 0, 2, buffer));
        } catch (UnsupportedOperationException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    public void testCopyMultipleBuffers() {
        final ByteBuffer[] buffer = new ByteBuffer[]{ByteBuffer.allocate(2), ByteBuffer.allocate(2),
                ByteBuffer.allocate(2), ByteBuffer.allocate(2), ByteBuffer.allocate(2)};
        buffer[0].put("12".getBytes()).flip();
        buffer[1].put("34".getBytes()).flip();
        buffer[2].put("56".getBytes()).flip();
        buffer[3].put("78".getBytes()).flip();
        buffer[4].put("90".getBytes()).flip();
        final ByteBuffer copy1 = ByteBuffer.allocate(10);
        assertEquals(10, Buffers.copy(copy1, buffer, 0, 5));
        for (int i = 0; i < 5; i++) {
            assertEquals(buffer[i].limit(), buffer[i].position());
            buffer[i].position(0);
        }
        assertEquals(copy1.limit(), copy1.position());
        assertBufferContent(copy1, "1234567890");

        final ByteBuffer[] copy2 = new ByteBuffer[] {ByteBuffer.allocate(5), ByteBuffer.allocate(5)};
        assertEquals(10 , Buffers.copy(copy2, 0, 2, buffer, 0, 5));
        for (int i = 0; i < 5; i++) {
            assertEquals(buffer[i].limit(), buffer[i].position());
            buffer[i].position(0);
        }
        assertEquals(copy2[0].limit(), copy2[0].position());
        assertEquals(copy2[1].limit(), copy2[1].position());
        assertBufferContent(copy2[0], "12345");
        assertBufferContent(copy2[1], "67890");

        final ByteBuffer copy3 = ByteBuffer.allocate(10);
        assertEquals(10, Buffers.copy(10, copy3, buffer, 0, 5));
        for (int i = 0; i < 5; i++) {
            assertEquals(buffer[i].limit(), buffer[i].position());
            buffer[i].position(0);
        }
        assertEquals(copy3.limit(), copy3.position());
        assertBufferContent(copy3, "1234567890");

        final ByteBuffer copy4 = ByteBuffer.allocate(50);
        assertEquals(10, Buffers.copy(40, copy4, buffer, 0, 5));
        for (int i = 0; i < 5; i++) {
            assertEquals(buffer[i].limit(), buffer[i].position());
            buffer[i].position(0);
        }
        assertEquals(10, copy4.position());
        assertBufferContent(copy4, "1234567890");

        final ByteBuffer[] copy5 = new ByteBuffer[] {ByteBuffer.allocate(5), ByteBuffer.allocate(5), ByteBuffer.allocate(5)};
        assertEquals(10, Buffers.copy(10, copy5, 1, 2, buffer, 0, 5));
        for (int i = 0; i < 5; i++) {
            assertEquals(buffer[i].limit(), buffer[i].position());
            buffer[i].position(0);
        }
        assertEquals(copy5[1].limit(), copy5[1].position());
        assertEquals(copy5[2].limit(), copy5[2].position());
        assertBufferContent(copy5[1], "12345");
        assertBufferContent(copy5[2], "67890");

        final ByteBuffer[] copy6 = new ByteBuffer[] {ByteBuffer.allocate(5), ByteBuffer.allocate(5), ByteBuffer.allocate(5)};
        assertEquals(10, Buffers.copy(30, copy6, 0, 3, buffer, 0, 5));
        for (int i = 0; i < 5; i++) {
            assertEquals(buffer[i].limit(), buffer[i].position());
            buffer[i].position(0);
        }
        assertEquals(copy6[0].limit(), copy6[0].position());
        assertEquals(copy6[1].limit(), copy6[1].position());
        assertBufferContent(copy6[0], "12345");
        assertBufferContent(copy6[1], "67890");

        //check copy properties
        buffer[0].put((byte) '0');
        buffer[0].put((byte) '9');
        assertEquals('1', copy1.get(0));
        assertEquals('2', copy1.get(1));
        assertEquals('1', copy2[0].get(0));
        assertEquals('2', copy2[0].get(1));
        assertEquals('1', copy3.get(0));
        assertEquals('2', copy3.get(1));
        assertEquals('1', copy4.get(0));
        assertEquals('2', copy4.get(1));
        assertEquals('1', copy5[1].get(0));
        assertEquals('2', copy5[1].get(1));
        assertEquals('1', copy6[0].get(0));
        assertEquals('2', copy6[0].get(1));
        copy1.put(2, (byte) '8');
        copy2[0].put(3, (byte) '7');
        copy3.put(4, (byte) '6');
        copy4.put(5, (byte)'5');
        copy5[2].put(1, (byte)'4');
        copy6[1].put(2, (byte) '3');
        assertEquals('3', buffer[1].get(0));
        assertEquals('4', buffer[1].get(1));
        assertEquals('5', buffer[2].get(0));
        assertEquals('6', buffer[2].get(1));
        assertEquals('7', buffer[3].get(0));
        assertEquals('8', buffer[3].get(1));
    }

    public void testPartiallyCopyMultipleBuffers() {
        final ByteBuffer[] buffer = new ByteBuffer[] {ByteBuffer.allocate(5), ByteBuffer.allocate(5), ByteBuffer.allocate(5), ByteBuffer.allocate(5)};
        buffer[0].put("09876".getBytes()).flip();
        buffer[1].put("12345".getBytes()).flip();
        buffer[2].put("67890".getBytes()).flip();
        buffer[3].put("54321".getBytes()).flip();
        final ByteBuffer copy1 = ByteBuffer.allocate(7);
        assertEquals(7, Buffers.copy(copy1, buffer, 1, 2));
        assertEquals(buffer[1].limit(), buffer[1].position());
        assertEquals(2, buffer[2].position());
        assertEquals(copy1.limit(), copy1.position());
        assertBufferContent(copy1, "1234567");

        buffer[1].position(0);
        buffer[2].position(0);
        final ByteBuffer[] copy2 = new ByteBuffer[] {ByteBuffer.allocate(2), ByteBuffer.allocate(2)};
        assertEquals(4, Buffers.copy(copy2, 0, 2, buffer, 1, 2));
        assertEquals(4, buffer[1].position());
        assertEquals(copy2[0].limit(), copy2[0].position());
        assertEquals(copy2[1].limit(), copy2[1].position());
        assertBufferContent(copy2[0], "12");
        assertBufferContent(copy2[1], "34");

        buffer[1].position(0);
        final ByteBuffer copy3 = ByteBuffer.allocate(10);
        assertEquals(1, Buffers.copy(1, copy3, buffer, 1, 2));
        assertEquals(1, buffer[1].position());
        assertEquals(1, copy3.position());
        assertEquals('1', copy3.get(0));

        buffer[1].position(0);
        final ByteBuffer[] copy4 = new ByteBuffer[] {ByteBuffer.allocate(3), ByteBuffer.allocate(4), ByteBuffer.allocate(6)};
        assertEquals(9, Buffers.copy(9, copy4, 1, 2, buffer, 1, 2));
        assertEquals(buffer[1].limit(), buffer[1].position());
        assertEquals(4, buffer[2].position());
        assertEquals(copy4[1].limit(), copy4[1].position());
        assertEquals(5, copy4[2].position());
        assertBufferContent(copy4[1], "1234");
        assertBufferContent(copy4[2], "56789");

        //check copy properties
        buffer[1].position(0);
        buffer[1].put((byte) '0');
        buffer[1].put((byte) '9');
        assertEquals('1', copy1.get(0));
        assertEquals('2', copy1.get(1));
        assertEquals('1', copy2[0].get(0));
        assertEquals('2', copy2[0].get(1));
        assertEquals('1', copy3.get(0));
        assertEquals('1', copy4[1].get(0));
        assertEquals('2', copy4[1].get(1));
        copy1.put(2, (byte) '8');
        copy2[1].put(1, (byte) '7');
        copy3.put(4, (byte) '6');
        copy4[2].put(1, (byte)'5');
        assertEquals('3', buffer[1].get(2));
        assertEquals('4', buffer[1].get(3));
        assertEquals('5', buffer[1].get(4));
        assertEquals('6', buffer[2].get(0));
    }

    public void testCopyMultipleBuffersToSmallerBuffer() {
        final ByteBuffer[] buffer = new ByteBuffer[] {ByteBuffer.allocate(5), ByteBuffer.allocate(5), ByteBuffer.allocate(5), ByteBuffer.allocate(5)};
        buffer[0].put("09876".getBytes()).flip();
        buffer[1].put("12345".getBytes()).flip();
        buffer[2].put("67890".getBytes()).flip();
        buffer[3].put("54321".getBytes()).flip();
        final ByteBuffer copy1 = ByteBuffer.allocate(7);
        assertEquals(0, Buffers.copy(copy1, buffer, 1, 0));
        assertEquals(7, Buffers.copy(copy1, buffer, 1, 2));
        assertEquals(buffer[1].limit(), buffer[1].position());
        assertEquals(2, buffer[2].position());
        assertEquals(copy1.limit(), copy1.position());
        assertBufferContent(copy1, "1234567");

        buffer[1].position(0);
        buffer[2].position(0);
        final ByteBuffer[] copy2 = new ByteBuffer[] {ByteBuffer.allocate(2)};
        assertEquals(0, Buffers.copy(copy2, 0, 0, buffer, 1, 2));
        assertEquals(0, Buffers.copy(copy2, 0, 1, buffer, 0, 0));
        assertEquals(2, Buffers.copy(copy2, 0, 1, buffer, 1, 2));
        assertEquals(2, buffer[1].position());
        assertEquals(copy2[0].limit(), copy2[0].position());
        assertBufferContent(copy2[0], "12");

        buffer[1].position(0);
        final ByteBuffer copy3 = ByteBuffer.allocate(1);
        assertEquals(0, Buffers.copy(10, copy3, buffer, 1, 0));
        assertEquals(1, Buffers.copy(10, copy3, buffer, 1, 2));
        assertEquals(1, buffer[1].position());
        assertEquals(1, copy3.position());
        assertEquals('1', copy3.get(0));

        buffer[1].position(0);
        final ByteBuffer[] copy4 = new ByteBuffer[] {ByteBuffer.allocate(3), ByteBuffer.allocate(5)};
        assertEquals(0, Buffers.copy(15, copy4, 0, 0, buffer, 1, 2));
        assertEquals(0, Buffers.copy(15, copy4, 0, 1, buffer, 1, 0));
        assertEquals(0, Buffers.copy(0, copy4, 0, 1, buffer, 1, 2));
        assertEquals(3, Buffers.copy(15, copy4, 0, 1, buffer, 1, 2));
        assertEquals(3, buffer[1].position());
        assertEquals(copy4[0].limit(), copy4[0].position());
        assertBufferContent(copy4[0], "123");

        //check copy properties
        buffer[1].position(0);
        buffer[1].put((byte) '0');
        buffer[1].put((byte) '9');
        assertEquals('1', copy1.get(0));
        assertEquals('2', copy1.get(1));
        assertEquals('1', copy2[0].get(0));
        assertEquals('2', copy2[0].get(1));
        assertEquals('1', copy3.get(0));
        assertEquals('1', copy4[0].get(0));
        assertEquals('2', copy4[0].get(1));
        copy1.put(2, (byte) '8');
        copy1.put(3, (byte) '7');
        copy2[0].put(0, (byte) '6');
        copy3.put(0, (byte)'5');
        copy4[1].put(2, (byte)'4');
        assertEquals('0', buffer[1].get(0));
        assertEquals('9', buffer[1].get(1));
        assertEquals('3', buffer[1].get(2));
        assertEquals('4', buffer[1].get(3));
        assertEquals('5', buffer[1].get(4));
        assertEquals('6', buffer[2].get(0));
    }

    // FIXME XNIO-120
    public void testCopyMultipleBuffersWithNegativeSliceSize() {
        final ByteBuffer[] buffer = new ByteBuffer[] {ByteBuffer.allocate(10), ByteBuffer.allocate(10)};
        buffer[0].put("1234567890".getBytes()).flip();
        buffer[1].put("1234567890".getBytes()).flip();
        final ByteBuffer copy1 = ByteBuffer.allocate(20);
        buffer[0].position(8);
        UnsupportedOperationException expected = null;
        try {
            assertEquals(0, Buffers.copy(-40, copy1, buffer, 0, 2));
        } catch (UnsupportedOperationException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            assertEquals(8, Buffers.copy(-4, copy1, buffer, 0, 2));
        } catch (UnsupportedOperationException e) {
            expected = e;
        }
        assertNotNull(expected);
// XNIO-120 these test assertions are wrong, negative count must work just like negative slice size
//        assertEquals(buffer[0].limit(), buffer[0].position());
//        assertEquals(6, buffer[1].position());
//        assertEquals(20, copy1.limit());
//        assertEquals(8, copy1.position());
//        assertBufferContent(copy1, "90123456");

        final ByteBuffer copy2 = ByteBuffer.allocate(10);
        expected = null;
        try {
            assertEquals(0, Buffers.copy(-4, copy2, buffer, 0, 2));
        } catch (UnsupportedOperationException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            assertEquals(0, Buffers.copy(-40, copy2, buffer, 0, 2));
        } catch (UnsupportedOperationException e) {
            expected = e;
        }
        assertNotNull(expected);

        buffer[1].position(0);
        final ByteBuffer[] copy3 = new ByteBuffer[] {ByteBuffer.allocate(3), ByteBuffer.allocate(2)};
        expected = null;
        try {
            assertEquals(0, Buffers.copy(-2, copy3, 0, 2, buffer, 0, 2)); // was 5
        } catch (UnsupportedOperationException e) {
            expected = e;
        }
        assertNotNull(expected);
// XNIO-120 these test assertions are wrong, negative count must work just like negative slice size
//        assertEquals(5, buffer[1].position());
//        assertEquals(copy3[0].limit(), copy3[0].position());
//        assertEquals(copy3[1].limit(), copy3[1].position());
//        assertBufferContent(copy3[0], "123");
//        assertBufferContent(copy3[1], "45");

        buffer[0].position(0);
        final ByteBuffer[] copy4 = new ByteBuffer[] {ByteBuffer.allocate(3), ByteBuffer.allocate(2), ByteBuffer.allocate(6)};
        expected = null;
        try {
            assertEquals(0, Buffers.copy(-6, copy4, 0, 3, buffer, 0, 2)); // was 9
        } catch (UnsupportedOperationException e) {
            expected = e;
        }
        assertNotNull(expected);
// XNIO-120 these test assertions are wrong, negative count must work just like negative slice size
//        assertEquals(9, buffer[0].position());
//        assertEquals(5, buffer[1].position());
//        assertEquals(copy4[0].limit(), copy4[0].position());
//        assertEquals(copy4[1].limit(), copy4[1].position());
//        assertEquals(4, copy4[2].position());
//        assertBufferContent(copy4[0], "123");
//        assertBufferContent(copy4[1], "45");
//        assertBufferContent(copy4[2], "6789");

        final ByteBuffer[] copy6 = new ByteBuffer[] {ByteBuffer.allocate(5), ByteBuffer.allocate(5)};
        expected = null;
        try {
            assertEquals(0, Buffers.copy(-6, copy6, 0, 2, buffer, 0, 2));
        } catch (UnsupportedOperationException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            assertEquals(0, Buffers.copy(-10, copy6, 0, 2, buffer, 0, 2));
        } catch (UnsupportedOperationException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    private void doTestFillByte(final ByteBuffer buf) {
        assertSame(buf, Buffers.fill(buf, 5, 30));
        assertSame(buf, Buffers.fill(buf, 90, 20));
        assertEquals(50, buf.position());
        assertEquals(5, buf.get(3));
        assertEquals(5, buf.get(29));
        assertEquals(90, buf.get(30));
        assertEquals(90, buf.get(31));
        assertEquals(90, buf.get(49));
        BufferUnderflowException expected = null;
        try {
            Buffers.fill(buf, 75, 300);
        } catch (BufferUnderflowException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            Buffers.fill(buf, 75, buf.remaining() + 1);
        } catch (BufferUnderflowException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    public void testFillByte() {
        final ByteBuffer buf = ByteBuffer.allocate(100);
        doTestFillByte(buf);
        final ByteBuffer dbuf = ByteBuffer.allocateDirect(100);
        doTestFillByte(dbuf);
    }

    private void doTestFillChar(final CharBuffer buf) {
        assertSame(buf, Buffers.fill(buf, 5, 30));
        assertSame(buf, Buffers.fill(buf, 90, 20));
        assertEquals(50, buf.position());
        assertEquals(5, buf.get(3));
        assertEquals(5, buf.get(29));
        assertEquals(90, buf.get(30));
        assertEquals(90, buf.get(31));
        assertEquals(90, buf.get(49));
        BufferUnderflowException expected = null;
        try {
            Buffers.fill(buf, 75, 300);
        } catch (BufferUnderflowException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            Buffers.fill(buf, 75, buf.remaining() + 1);
        } catch (BufferUnderflowException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    public void testFillChar() {
        final CharBuffer buf1 = CharBuffer.allocate(100);
        doTestFillChar(buf1);
        final ByteBuffer buf2 = ByteBuffer.allocateDirect(100 << 1);
        doTestFillChar(buf2.asCharBuffer());
    }

    private void doTestFillShort(final ShortBuffer buf) {
        assertSame(buf, Buffers.fill(buf, 5, 30));
        assertSame(buf, Buffers.fill(buf, 90, 20));
        assertEquals(50, buf.position());
        assertEquals(5, buf.get(3));
        assertEquals(5, buf.get(29));
        assertEquals(90, buf.get(30));
        assertEquals(90, buf.get(31));
        assertEquals(90, buf.get(49));
        BufferUnderflowException expected = null;
        try {
            Buffers.fill(buf, 75, 300);
        } catch (BufferUnderflowException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            Buffers.fill(buf, 75, buf.remaining() + 1);
        } catch (BufferUnderflowException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    public void testFillShort() {
        final ShortBuffer buf1 = ShortBuffer.allocate(100);
        doTestFillShort(buf1);
        final ByteBuffer buf2 = ByteBuffer.allocateDirect(100 << 1);
        doTestFillShort(buf2.asShortBuffer());
    }

    private void doTestFillInt(final IntBuffer buf) {
        assertSame(buf, Buffers.fill(buf, 5, 30));
        assertSame(buf, Buffers.fill(buf, 90, 20));
        assertEquals(50, buf.position());
        assertEquals(5, buf.get(3));
        assertEquals(5, buf.get(29));
        assertEquals(90, buf.get(30));
        assertEquals(90, buf.get(31));
        assertEquals(90, buf.get(49));
        BufferUnderflowException expected = null;
        try {
            Buffers.fill(buf, 75, 300);
        } catch (BufferUnderflowException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            Buffers.fill(buf, 75, buf.remaining() + 1);
        } catch (BufferUnderflowException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    public void testFillInt() {
        final IntBuffer buf1 = IntBuffer.allocate(100);
        doTestFillInt(buf1);
        final ByteBuffer buf2 = ByteBuffer.allocateDirect(100 << 2);
        doTestFillInt(buf2.asIntBuffer());
    }

    private void doTestFillLong(final LongBuffer buf) {
        assertSame(buf, Buffers.fill(buf, 5, 30));
        assertSame(buf, Buffers.fill(buf, 90, 20));
        assertEquals(50, buf.position());
        assertEquals(5, buf.get(3));
        assertEquals(5, buf.get(29));
        assertEquals(90, buf.get(30));
        assertEquals(90, buf.get(31));
        assertEquals(90, buf.get(49));
        BufferUnderflowException expected = null;
        try {
            Buffers.fill(buf, 75, 300);
        } catch (BufferUnderflowException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            Buffers.fill(buf, 75, buf.remaining() + 1);
        } catch (BufferUnderflowException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    public void testFillLong() {
        final LongBuffer buf1 = LongBuffer.allocate(100);
        doTestFillLong(buf1);
        final ByteBuffer buf2 = ByteBuffer.allocateDirect(100 << 3);
        doTestFillLong(buf2.asLongBuffer());
    }

    public void testSkip() {
        ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put("abcdefghij".getBytes()).flip();
        assertEquals(0, buffer.position());
        assertSame(buffer, Buffers.skip(buffer, 3));
        assertEquals(3, buffer.position());
        assertSame(buffer, Buffers.skip(buffer, 5));
        assertEquals(8, buffer.position());
        Exception expected = null;
        try {
            Buffers.skip(buffer, -1);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            Buffers.skip(buffer, -20);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            Buffers.skip(buffer, 3);
        } catch (BufferUnderflowException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            Buffers.skip(buffer, 5);
        } catch (BufferUnderflowException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    public void testTrySkip() {
        ByteBuffer buffer = ByteBuffer.allocate(20);
        buffer.put("abcdefghijklmnopqrst".getBytes()).flip();
        assertEquals(0, buffer.position());
        assertEquals(6, Buffers.trySkip(buffer, 6));
        assertEquals(6, buffer.position());
        assertEquals(10, Buffers.trySkip(buffer, 10));
        assertEquals(16, buffer.position());
        Exception expected = null;
        try {
            Buffers.trySkip(buffer, -1);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            Buffers.trySkip(buffer, -40);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        assertEquals(4, Buffers.trySkip(buffer, 6));
        assertEquals(buffer.limit(), buffer.position());
    }

    public void testTrySkipBufferArray() {
        ByteBuffer[] buffers = new ByteBuffer[] {ByteBuffer.allocate(2), ByteBuffer.allocate(3), ByteBuffer.allocate(4),
                ByteBuffer.allocate(5), ByteBuffer.allocate(1)};
        buffers[0].put(new byte[] {1, 2}).flip();
        buffers[1].put(new byte[] {3, 4, 5}).flip();
        buffers[2].put(new byte[] {5, 6, 7, 8}).flip();
        buffers[3].put(new byte[] {8, 9, 10, 11}).flip(); // 4 elements only
        buffers[4].put((byte) 12).flip();
        assertEquals(4, Buffers.trySkip(buffers, 1, 4, 4));
        assertEquals(0, buffers[0].position());
        assertEquals(3, buffers[1].position());
        assertEquals(1, buffers[2].position());
        assertEquals(9, Buffers.trySkip(buffers, 0, 4, 10));
        assertEquals(2, buffers[0].position());
        assertEquals(3, buffers[1].position());
        assertEquals(4, buffers[2].position());
        assertEquals(4, buffers[3].position());
        assertEquals(0, buffers[4].position());
        Exception expected = null;
        try {
            Buffers.trySkip(buffers, 0, 3, -1);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            Buffers.trySkip(buffers, 1, 4, -40);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            Buffers.trySkip(buffers, 1, -34, 40);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            Buffers.trySkip(buffers, -1, 34, 40);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            Buffers.trySkip(buffers, 34, 1, 40);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            Buffers.trySkip(buffers, 1, 5, 40);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        assertEquals(1, Buffers.trySkip(buffers, 2, 3, 6));
        assertEquals(2, buffers[0].position());
        assertEquals(3, buffers[1].position());
        assertEquals(4, buffers[2].position());
        assertEquals(4, buffers[3].position());
        assertEquals(1, buffers[4].position());
    }

    public void testUnget() {
        final ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put("123456".getBytes());
        Buffers.unget(buffer, 3);
        assertEquals('4', buffer.get());
        buffer.flip();
        Exception expected = null;
        try {
            Buffers.unget(buffer, 3);
        } catch (BufferUnderflowException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            Buffers.unget(buffer, -1);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    private void assertBufferContent(byte[] buffer, String content) {
        assertReadMessage(buffer, content);
    }

    public void testTakeBytes() {
        final ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put("1234567890".getBytes()).flip();
        byte[] takeResult = Buffers.take(buffer, 2);
        assertEquals(2, takeResult.length);
        assertBufferContent(takeResult, "12");

        Exception expected = null;
        try {
            Buffers.take(buffer, -5);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            Buffers.take((ByteBuffer) null, 1);
        } catch (NullPointerException e) {
            expected = e;
        }
        assertNotNull(expected);

        takeResult = Buffers.take(buffer, 5);
        assertEquals(5, takeResult.length);
        assertBufferContent(takeResult, "34567");

        expected = null;
        try {
            takeResult = Buffers.take(buffer, 5);
        } catch (BufferUnderflowException e) {
            expected = e;
        }
        assertNotNull(expected);

        takeResult = Buffers.take(buffer, 0);
        assertEquals(0, takeResult.length);

        takeResult = Buffers.take(buffer);
        assertEquals(3, takeResult.length);
        assertBufferContent(takeResult, "890");

        takeResult = Buffers.take(buffer);
        assertEquals(0, takeResult.length);

        expected = null;
        try {
            Buffers.take((ByteBuffer) null);
        } catch(NullPointerException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    public void testTakeChars() {
        final CharBuffer buffer = CharBuffer.allocate(10);
        buffer.put(new char[] {'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j'}).flip();
        char[] takeResult = Buffers.take(buffer, 1);
        assertEquals(1, takeResult.length);
        assertEquals('a', takeResult[0]);

        Exception expected = null;
        try {
            Buffers.take(buffer, -2);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            Buffers.take((CharBuffer) null, 11);
        } catch (NullPointerException e) {
            expected = e;
        }
        assertNotNull(expected);

        takeResult = Buffers.take(buffer, 5);
        assertEquals(5, takeResult.length);
        assertEquals('b', takeResult[0]);
        assertEquals('c', takeResult[1]);
        assertEquals('d', takeResult[2]);
        assertEquals('e', takeResult[3]);
        assertEquals('f', takeResult[4]);

        expected = null;
        try {
            takeResult = Buffers.take(buffer, 6);
        } catch (BufferUnderflowException e) {
            expected = e;
        }
        assertNotNull(expected);

        takeResult = Buffers.take(buffer, 0);
        assertEquals(0, takeResult.length);

        takeResult = Buffers.take(buffer);
        assertEquals(4, takeResult.length);
        assertEquals('g', takeResult[0]);
        assertEquals('h', takeResult[1]);
        assertEquals('i', takeResult[2]);
        assertEquals('j', takeResult[3]);

        takeResult = Buffers.take(buffer);
        assertEquals(0, takeResult.length);

        expected = null;
        try {
            Buffers.take((CharBuffer) null);
        } catch(NullPointerException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    public void testTakeShorts() {
        final ShortBuffer buffer = ShortBuffer.allocate(10);
        buffer.put(new short[] {1, 2, 3, 5, 7, 11, 13, 17, 19}).flip();
        short[] takeResult = Buffers.take(buffer, 4);
        assertEquals(4, takeResult.length);
        assertEquals(1, takeResult[0]);
        assertEquals(2, takeResult[1]);
        assertEquals(3, takeResult[2]);
        assertEquals(5, takeResult[3]);

        Exception expected = null;
        try {
            Buffers.take(buffer, -7);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            Buffers.take((ShortBuffer) null, 1);
        } catch (NullPointerException e) {
            expected = e;
        }
        assertNotNull(expected);

        takeResult = Buffers.take(buffer, 4);
        assertEquals(4, takeResult.length);
        assertEquals(7, takeResult[0]);
        assertEquals(11, takeResult[1]);
        assertEquals(13, takeResult[2]);
        assertEquals(17, takeResult[3]);

        expected = null;
        try {
            takeResult = Buffers.take(buffer, 5);
        } catch (BufferUnderflowException e) {
            expected = e;
        }
        assertNotNull(expected);

        takeResult = Buffers.take(buffer, 0);
        assertEquals(0, takeResult.length);

        takeResult = Buffers.take(buffer);
        assertEquals(1, takeResult.length);
        assertEquals(19, takeResult[0]);

        takeResult = Buffers.take(buffer);
        assertEquals(0, takeResult.length);

        expected = null;
        try {
            Buffers.take((ShortBuffer) null);
        } catch(NullPointerException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    public void testTakeInts() {
        final IntBuffer buffer = IntBuffer.allocate(10);
        buffer.put(new int[] {2, 4, 6, 8, 10, 12, 14, 16, 18, 20}).flip();
        int[] takeResult = Buffers.take(buffer, 2);
        assertEquals(2, takeResult.length);
        assertEquals(2, takeResult[0]);
        assertEquals(4, takeResult[1]);

        Exception expected = null;
        try {
            Buffers.take(buffer, -5);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            Buffers.take((IntBuffer) null, 1);
        } catch (NullPointerException e) {
            expected = e;
        }
        assertNotNull(expected);

        takeResult = Buffers.take(buffer, 5);
        assertEquals(5, takeResult.length);
        assertEquals(6, takeResult[0]);
        assertEquals(8, takeResult[1]);
        assertEquals(10, takeResult[2]);
        assertEquals(12, takeResult[3]);
        assertEquals(14, takeResult[4]);

        expected = null;
        try {
            takeResult = Buffers.take(buffer, 5);
        } catch (BufferUnderflowException e) {
            expected = e;
        }
        assertNotNull(expected);

        takeResult = Buffers.take(buffer, 0);
        assertEquals(0, takeResult.length);

        takeResult = Buffers.take(buffer);
        assertEquals(3, takeResult.length);
        assertEquals(16, takeResult[0]);
        assertEquals(18, takeResult[1]);
        assertEquals(20, takeResult[2]);

        takeResult = Buffers.take(buffer);
        assertEquals(0, takeResult.length);

        expected = null;
        try {
            Buffers.take((ByteBuffer) null);
        } catch(NullPointerException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    public void testTakeLongs() {
        final LongBuffer buffer = LongBuffer.allocate(10);
        buffer.put(new long[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9}).flip();
        long[] takeResult = Buffers.take(buffer, 5);
        assertEquals(5, takeResult.length);
        assertEquals(0, takeResult[0]);
        assertEquals(1, takeResult[1]);
        assertEquals(2, takeResult[2]);
        assertEquals(3, takeResult[3]);
        assertEquals(4, takeResult[4]);

        Exception expected = null;
        try {
            Buffers.take(buffer, -1000);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            Buffers.take((LongBuffer) null, 3);
        } catch (NullPointerException e) {
            expected = e;
        }
        assertNotNull(expected);

        takeResult = Buffers.take(buffer, 1);
        assertEquals(1, takeResult.length);
        assertEquals(5, takeResult[0]);

        expected = null;
        try {
            takeResult = Buffers.take(buffer, 5);
        } catch (BufferUnderflowException e) {
            expected = e;
        }
        assertNotNull(expected);

        takeResult = Buffers.take(buffer, 0);
        assertEquals(0, takeResult.length);

        takeResult = Buffers.take(buffer);
        assertEquals(4, takeResult.length);
        assertEquals(6, takeResult[0]);
        assertEquals(7, takeResult[1]);
        assertEquals(8, takeResult[2]);
        assertEquals(9, takeResult[3]);

        takeResult = Buffers.take(buffer);
        assertEquals(0, takeResult.length);

        expected = null;
        try {
            Buffers.take((LongBuffer) null);
        } catch(NullPointerException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    public void testDumpByteBuffer() throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocate(15);
        buffer.put("create dumper".getBytes()).flip();
        assertNotNull(Buffers.createDumper(buffer, 3, 3).toString());
        StringBuilder builder = new StringBuilder();
        Buffers.dump(buffer, builder, 5, 20);
        assertTrue(builder.toString().length() > 0);

        assertNotNull(Buffers.createDumper(buffer, 0, 5).toString());
        builder = new StringBuilder();
        Buffers.dump(buffer, builder, 0, 15);
        assertTrue(builder.toString().length() > 0);

        IllegalArgumentException expected = null;
        try {
            Buffers.createDumper(buffer, 3, 0);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            Buffers.createDumper(buffer, 3, -1);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            Buffers.createDumper(buffer, -1, 4);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            Buffers.dump(buffer, builder, 3, 0);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            Buffers.dump(buffer, builder, 3, -1);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            Buffers.dump(buffer, builder, -1, 4);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    public void testDumpCharBuffers() throws IOException {
        final CharBuffer buffer = CharBuffer.allocate(15);
        buffer.put(new char[] {'d', 'u', 'm', 'p', ' ', 'c', 'h', 'a', 'r', 's'}).flip();
        assertNotNull(Buffers.createDumper(buffer, 3, 3).toString());
        StringBuilder builder = new StringBuilder();
        Buffers.dump(buffer, builder, 5, 20);
        assertTrue(builder.toString().length() > 0);

        assertNotNull(Buffers.createDumper(buffer, 0, 10).toString());
        builder = new StringBuilder();
        Buffers.dump(buffer, builder, 0, 13);
        assertTrue(builder.toString().length() > 0);

        IllegalArgumentException expected = null;
        try {
            Buffers.createDumper(buffer, 10, 0);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            Buffers.createDumper(buffer, 30, -5);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            Buffers.createDumper(buffer, -5, 8);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            Buffers.dump(buffer, builder, 10, 0);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            Buffers.dump(buffer, builder, 30, -5);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            Buffers.dump(buffer, builder, -5, 8);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    public void testEmptyPooledByteBuffer() {
        assertEquals(0, Buffers.EMPTY_POOLED_BYTE_BUFFER.getResource().capacity());
        Buffers.EMPTY_POOLED_BYTE_BUFFER.discard();
        assertEquals(0, Buffers.EMPTY_POOLED_BYTE_BUFFER.getResource().capacity());
        Buffers.EMPTY_POOLED_BYTE_BUFFER.free();
        assertEquals(0, Buffers.EMPTY_POOLED_BYTE_BUFFER.getResource().capacity());
        assertNotNull(Buffers.EMPTY_POOLED_BYTE_BUFFER.toString());
    }

    public void testRemaining() {
        assertFalse(Buffers.hasRemaining(new Buffer[0]));
        assertEquals(0, Buffers.remaining(new Buffer[0]));

        final ByteBuffer[] buffer = new ByteBuffer[] {ByteBuffer.allocate(10)};
        assertTrue(Buffers.hasRemaining(buffer));
        assertEquals(10, Buffers.remaining(buffer));

        buffer[0].put("12345".getBytes());
        assertTrue(Buffers.hasRemaining(buffer));
        assertEquals(5, Buffers.remaining(buffer));

        buffer[0].put("67890".getBytes());
        assertFalse(Buffers.hasRemaining(buffer));
        assertEquals(0, Buffers.remaining(buffer));

        final ByteBuffer[] buffers = new ByteBuffer[] {ByteBuffer.allocate(2), ByteBuffer.allocate(2),
                ByteBuffer.allocate(2), ByteBuffer.allocate(2), ByteBuffer.allocate(2)};
        assertTrue(Buffers.hasRemaining(buffers));
        assertEquals(10, Buffers.remaining(buffers));
        assertEquals(8, Buffers.remaining(buffers, 1, 4));

        buffers[2].put("12".getBytes());
        assertTrue(Buffers.hasRemaining(buffers));
        assertEquals(8, Buffers.remaining(buffers));
        assertEquals(6, Buffers.remaining(buffers, 1, 4));
    }

    public void testPutModifiedUtf8() {
        final StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append('a');
        stringBuilder.append((char) -10);
        stringBuilder.append((char) 0x777);
        stringBuilder.append((char) 0xfff);
        stringBuilder.append((char) 0);
        final ByteBuffer buffer = ByteBuffer.allocate(15);
        Buffers.putModifiedUtf8(buffer, stringBuilder.toString());
        assertEquals(11, buffer.position());
        buffer.flip();

        assertEquals('a', buffer.get());

        assertEquals((byte)(0xe0 | 0x0f & ((char)-10) >> 12), buffer.get());
        assertEquals((byte)(0x80 | 0x3f & ((char)-10) >> 6), buffer.get());
        assertEquals((byte)(0x80 | 0x3f & ((char)-10)), buffer.get());

        assertEquals((byte) (0xc0 | 0x1f & ((char) 0x777) >> 6), buffer.get());
        assertEquals((byte)(0x80 | 0x3f & ((char) 0x777)), buffer.get());

        assertEquals((byte)(0xe0 | 0x0f & ((char) 0xfff) >> 12), buffer.get());
        assertEquals((byte)(0x80 | 0x3f & ((char) 0xfff) >> 6), buffer.get());
        assertEquals((byte)(0x80 | 0x3f & ((char) 0xfff)), buffer.get());

        assertEquals((byte) 0xc0, buffer.get());
        assertEquals((byte) 0x80, buffer.get());
    }

    private void fillBufferModifiedUtf8Test(ByteBuffer buffer) {
     // fist char: 'a'
        buffer.put((byte)'a');
        // second char: '?'
        buffer.put((byte) 0xb1);
        // third char: '?' 
        buffer.put((byte) 0xc0);
        buffer.put((byte) 0xcd);
        // fourth char: (char) 1215
        buffer.put((byte) 0xd2);
        buffer.put((byte) 0xfbf);
        // fifth char: (char) '?'
        buffer.put((byte) 0xe9);
        buffer.put((byte) 0xc1);
        // sixth char: (char) '?'
        buffer.put((byte) 0xe0);
        buffer.put((byte) 0xdef);
        // seventh char: (char)'?'
        buffer.put((byte) 0xee);
        buffer.put((byte) 0xebf);
        buffer.put((byte) 0x1);
        // eigth char: (char) 44730
        buffer.put((byte) 0xea);
        buffer.put((byte) 0xeba);
        buffer.put((byte) 0xeba);
        // ninth char: '?'
        buffer.put((byte) 426); //0xd0+ 0x80, it will be transformed to 170
        // tenth char: '?'
        buffer.put((byte) 0xff);
        // last char: 0
        buffer.put((byte) 0);
        // char that should not be read
        buffer.put((byte) 'z');
        buffer.flip();
    }

    private void assertModifiedUtf8Result(char[] modifiedUtf8) {
        assertEquals('a', modifiedUtf8[0]);
        assertEquals('?', modifiedUtf8[1]);
        assertEquals('?', modifiedUtf8[2]);
        assertEquals((char) 1215, modifiedUtf8[3]);
        assertEquals('?', modifiedUtf8[4]);
        assertEquals('?', modifiedUtf8[5]);
        assertEquals('?', modifiedUtf8[6]);
        assertEquals((char) 44730, modifiedUtf8[7]);
        assertEquals('?', modifiedUtf8[8]);
        assertEquals('?', modifiedUtf8[9]);
    }

    public void testGetMotifiedUtf8Z() {
        final ByteBuffer buffer = ByteBuffer.allocate(20);
        fillBufferModifiedUtf8Test(buffer);
        String result = Buffers.getModifiedUtf8Z(buffer);
        char[] resultChars = result.toCharArray();
        assertEquals(10, resultChars.length);
        assertModifiedUtf8Result(resultChars);
    }

    public void testGetMotifiedUtf8() {
        final ByteBuffer buffer = ByteBuffer.allocate(20);
        fillBufferModifiedUtf8Test(buffer);
        String result = Buffers.getModifiedUtf8(buffer);
        char[] resultChars = result.toCharArray();
        assertEquals(12, resultChars.length);
        assertModifiedUtf8Result(resultChars);
        assertEquals('\0', resultChars[10]);
        assertEquals('z', resultChars[11]);
    }

    public void testReadAsciiZ() {
        final ByteBuffer buffer = ByteBuffer.allocate(20);
        buffer.put((byte) 'a');
        buffer.put((byte) 'b');
        buffer.put((byte) 'c');
        buffer.put((byte) 0xeba);
        buffer.put((byte) -0xff);
        buffer.flip();
        StringBuilder builder = new StringBuilder();
        assertFalse(Buffers.readAsciiZ(buffer, builder));
        char[] result = builder.toString().toCharArray();
        assertEquals(5, result.length);
        assertEquals('a', result[0]);
        assertEquals('b', result[1]);
        assertEquals('c', result[2]);
        assertEquals('?', result[3]);
        assertEquals(1, result[4]);
        buffer.limit(buffer.capacity());
        buffer.put((byte) 0);
        buffer.put((byte) 'd');
        buffer.flip();
        builder = new StringBuilder();
        assertTrue(Buffers.readAsciiZ(buffer, builder));
        result = builder.toString().toCharArray();
        assertEquals(5, result.length);
        assertEquals('a', result[0]);
        assertEquals('b', result[1]);
        assertEquals('c', result[2]);
        assertEquals('?', result[3]);
        assertEquals(1, result[4]);
        assertFalse(Buffers.readAsciiZ(buffer, builder));
    }

    public void testReadAsciiZWithReplacement() {
        final ByteBuffer buffer = ByteBuffer.allocate(20);
        buffer.put((byte) 'D');
        buffer.put((byte) 'E');
        buffer.put((byte) '#');
        buffer.put((byte) 0xe0);
        buffer.put((byte) 0xaa);
        buffer.flip();
        StringBuilder builder = new StringBuilder();
        assertFalse(Buffers.readAsciiZ(buffer, builder, '#'));
        char[] result = builder.toString().toCharArray();
        assertEquals(5, result.length);
        assertEquals('D', result[0]);
        assertEquals('E', result[1]);
        assertEquals('#', result[2]);
        assertEquals('#', result[3]);
        assertEquals((char) '#', result[4]);
        buffer.limit(buffer.capacity());
        buffer.put((byte) 0);
        buffer.put((byte) 'F');
        buffer.flip();
        builder = new StringBuilder();
        assertTrue(Buffers.readAsciiZ(buffer, builder, '\''));
        result = builder.toString().toCharArray();
        assertEquals(5, result.length);
        assertEquals('D', result[0]);
        assertEquals('E', result[1]);
        assertEquals('#', result[2]);
        assertEquals('\'', result[3]);
        assertEquals('\'', result[4]);
        assertFalse(Buffers.readAsciiZ(buffer, builder, '0'));
    }

    public void testReadAsciiLine() {
        final ByteBuffer buffer = ByteBuffer.allocate(30);
        buffer.put((byte) 'R');
        buffer.put((byte) 'e');
        buffer.put((byte) 'a');
        buffer.put((byte) 'd');
        buffer.put((byte) '\n');
        buffer.put((byte) '\t');
        buffer.put((byte) 'w');
        buffer.put((byte) 'e');
        buffer.put((byte) 'i');
        buffer.put((byte) 'r');
        buffer.put((byte) 'd');
        buffer.put((byte) '\n');
        buffer.put((byte) '\n');
        buffer.put((byte) ' ');
        buffer.put((byte) 'c');
        buffer.put((byte) 'h');
        buffer.put((byte) 'a');
        buffer.put((byte) 'r');
        buffer.put((byte) 's');
        buffer.put((byte) ':');
        buffer.put((byte) 0xaa);
        buffer.put((byte) '\n');
        buffer.put((byte) 0xeba);
        buffer.put((byte) '\n');
        buffer.put((byte) 0xe0);
        buffer.put((byte) '\n');
        buffer.put((byte) 0);
        buffer.put((byte) '#');
        buffer.flip();

        StringBuilder builder = new StringBuilder();
        assertTrue(Buffers.readAsciiLine(buffer, builder));
        assertEquals("Read\n", builder.toString());
        builder = new StringBuilder();

        assertTrue(Buffers.readAsciiLine(buffer, builder, '+'));
        assertEquals("\tweird\n", builder.toString());
        builder = new StringBuilder();

        assertTrue(Buffers.readAsciiLine(buffer, builder, '=', '\n'));
        assertEquals("\n", builder.toString());
        builder = new StringBuilder();

        assertTrue(Buffers.readAsciiLine(buffer, builder, '=', ':'));
        assertEquals(" chars:", builder.toString());
        builder = new StringBuilder();

        assertTrue(Buffers.readAsciiLine(buffer, builder));
        assertEquals("?\n", builder.toString());
        builder = new StringBuilder();

        assertTrue(Buffers.readAsciiLine(buffer, builder, '1'));
        assertEquals("1\n", builder.toString());
        builder = new StringBuilder();

        assertTrue(Buffers.readAsciiLine(buffer, builder, '2'));
        assertEquals("2\n", builder.toString());
        builder = new StringBuilder();

        assertFalse(Buffers.readAsciiLine(buffer, builder, '3', '\n'));
        assertEquals("\0#", builder.toString());
        builder = new StringBuilder();

        assertFalse(Buffers.readAsciiLine(buffer, builder, '4', '\n'));
        assertTrue(builder.toString().isEmpty());
        assertFalse(Buffers.readAsciiLine(buffer, builder, '5', '\n'));
        assertTrue(builder.toString().isEmpty());
    }

    public void testReadAscii() {
        final ByteBuffer buffer = ByteBuffer.allocate(30);
        buffer.put("read".getBytes());
        buffer.put((byte) 0xeba);
        buffer.put("this".getBytes());
        buffer.put((byte)0xeba);
        buffer.put("until".getBytes());
        buffer.put((byte)0xaa);
        buffer.put("the".getBytes());
        buffer.put((byte)0xaa);
        buffer.put("end!!!".getBytes());
        buffer.flip();
        final StringBuilder builder = new StringBuilder();
        Buffers.readAscii(buffer, builder);
        assertEquals("read?this?until?the?end!!!", builder.toString());
    }

    public void testReadAsciiWithReplacement() {
        final ByteBuffer buffer = ByteBuffer.allocate(30);
        buffer.put("read".getBytes());
        buffer.put((byte) 0xeba);
        buffer.put("this".getBytes());
        buffer.put((byte)0xeba);
        buffer.put("until".getBytes());
        buffer.put((byte)0xaa);
        buffer.put("the".getBytes());
        buffer.put((byte)0xaa);
        buffer.put("end!!!".getBytes());
        buffer.flip();
        final StringBuilder builder = new StringBuilder();
        Buffers.readAscii(buffer, builder, '@');
        assertEquals("read@this@until@the@end!!!", builder.toString());
    }

    public void testReadAsciiWithLimit() {
        final ByteBuffer buffer = ByteBuffer.allocate(30);
        buffer.put("read".getBytes());
        buffer.put((byte) 0xeba);
        buffer.put("this".getBytes());
        buffer.put((byte)0xeba);
        buffer.put("with".getBytes());
        buffer.put((byte)0xaa);
        buffer.put("some".getBytes());
        buffer.put((byte)0xaa);
        buffer.put("limit!!!".getBytes());
        buffer.flip();
        final StringBuilder builder = new StringBuilder();
        Buffers.readAscii(buffer, builder, 10, '(');
        assertEquals("read(this(", builder.toString());
        Buffers.readAscii(buffer, builder, 10, ')');
        assertEquals("read(this(with)some)", builder.toString());
        Buffers.readAscii(buffer, builder, 5, ' ');
        assertEquals("read(this(with)some)limit", builder.toString());
        Buffers.readAscii(buffer, builder, 8, ' ');
        assertEquals("read(this(with)some)limit!!!", builder.toString());
    }

    public void testReadLatin1Z() {
        final ByteBuffer buffer = ByteBuffer.allocate(15);
        buffer.put((byte) 0xaaa);
        buffer.put((byte) 0xa01);
        buffer.put((byte) 0xa02);
        buffer.put((byte) 0xa03);
        buffer.put((byte) 0xa04);
        buffer.put((byte) 0xa05);
        buffer.put((byte) 0xa06);
        buffer.put((byte) 0x20);
        buffer.put((byte) 0x21);
        buffer.put((byte) 0x22);
        buffer.flip();
        StringBuilder builder = new StringBuilder();
        assertFalse(Buffers.readLatin1Z(buffer, builder));
        char[] chars = builder.toString().toCharArray();
        assertEquals(10, chars.length);
        assertEquals(0xaa, chars[0]);
        assertEquals(1, chars[1]);
        assertEquals(2, chars[2]);
        assertEquals(3, chars[3]);
        assertEquals(4, chars[4]);
        assertEquals(5, chars[5]);
        assertEquals(6, chars[6]);
        assertEquals(0x20, chars[7]);
        assertEquals(0x21, chars[8]);
        assertEquals(0x22, chars[9]);

        buffer.limit(buffer.capacity());
        buffer.put((byte) 0x23);
        buffer.put((byte) 0xa00);
        buffer.put((byte) 0x24);
        buffer.flip();
        builder = new StringBuilder();
        assertTrue(Buffers.readLatin1Z(buffer, builder));
        chars = builder.toString().toCharArray();
        assertEquals(11, chars.length);
        assertEquals(0xaa, chars[0]);
        assertEquals(1, chars[1]);
        assertEquals(2, chars[2]);
        assertEquals(3, chars[3]);
        assertEquals(4, chars[4]);
        assertEquals(5, chars[5]);
        assertEquals(6, chars[6]);
        assertEquals(0x20, chars[7]);
        assertEquals(0x21, chars[8]);
        assertEquals(0x22, chars[9]);
        assertEquals(0x23, chars[10]);
    }

    public void testReadLatin1Line() {
        final ByteBuffer buffer = ByteBuffer.allocate(15);
        buffer.put((byte) 0xaaa);
        buffer.put((byte) 0);
        buffer.put((byte) 0xa01);
        buffer.put((byte) 0xa02);
        buffer.put((byte) 0xa03);
        buffer.put((byte) 0xa04);
        buffer.put((byte) '\n');
        buffer.put((byte) '\n');
        buffer.put((byte) 0xa05);
        buffer.put((byte) 0xa06);
        buffer.put((byte) 0x20);
        buffer.put((byte) '\n');
        buffer.put((byte) 0x21);
        buffer.put((byte) '\n');
        buffer.put((byte) 0x22);
        buffer.flip();
        StringBuilder builder = new StringBuilder();
        assertTrue(Buffers.readLatin1Line(buffer, builder));
        char[] chars = builder.toString().toCharArray();
        assertEquals(7, chars.length);
        assertEquals(0xaa, chars[0]);
        assertEquals(0, chars[1]);
        assertEquals(1, chars[2]);
        assertEquals(2, chars[3]);
        assertEquals(3, chars[4]);
        assertEquals(4, chars[5]);
        assertEquals('\n', chars[6]);

        builder = new StringBuilder();
        assertTrue(Buffers.readLatin1Line(buffer, builder));
        chars = builder.toString().toCharArray();
        assertEquals(1, chars.length);
        assertEquals('\n', chars[0]);

        builder = new StringBuilder();
        assertTrue(Buffers.readLatin1Line(buffer, builder));
        chars = builder.toString().toCharArray();
        assertEquals(4, chars.length);
        assertEquals(5, chars[0]);
        assertEquals(6, chars[1]);
        assertEquals(0x20, chars[2]);
        assertEquals('\n', chars[3]);

        builder = new StringBuilder();
        assertTrue(Buffers.readLatin1Line(buffer, builder));
        chars = builder.toString().toCharArray();
        assertEquals(2, chars.length);
        assertEquals(0x21, chars[0]);
        assertEquals('\n', chars[1]);

        builder = new StringBuilder();
        assertFalse(Buffers.readLatin1Line(buffer, builder));
        chars = builder.toString().toCharArray();
        assertEquals(1, chars.length);
        assertEquals(0x22, chars[0]);
    }

    public void testReadLatin1LineWithDelimeter() {
        final ByteBuffer buffer = ByteBuffer.allocate(15);
        buffer.put((byte) 0xaaa);
        buffer.put((byte) 0);
        buffer.put((byte) 0xa01);
        buffer.put((byte) 0xa02);
        buffer.put((byte) 0xa03);
        buffer.put((byte) 0xa04);
        buffer.put((byte) '\t');
        buffer.put((byte) 'x');
        buffer.put((byte) 0xa05);
        buffer.put((byte) 0xa06);
        buffer.put((byte) 0x20);
        buffer.put((byte) 'y');
        buffer.put((byte) 0x21);
        buffer.put((byte) 'z');
        buffer.put((byte) 0x22);
        buffer.flip();
        StringBuilder builder = new StringBuilder();
        assertTrue(Buffers.readLatin1Line(buffer, builder, '\t'));
        char[] chars = builder.toString().toCharArray();
        assertEquals(7, chars.length);
        assertEquals(0xaa, chars[0]);
        assertEquals(0, chars[1]);
        assertEquals(1, chars[2]);
        assertEquals(2, chars[3]);
        assertEquals(3, chars[4]);
        assertEquals(4, chars[5]);
        assertEquals('\t', chars[6]);

        builder = new StringBuilder();
        assertTrue(Buffers.readLatin1Line(buffer, builder, 'x'));
        chars = builder.toString().toCharArray();
        assertEquals(1, chars.length);
        assertEquals('x', chars[0]);

        builder = new StringBuilder();
        assertTrue(Buffers.readLatin1Line(buffer, builder, 'y'));
        chars = builder.toString().toCharArray();
        assertEquals(4, chars.length);
        assertEquals(5, chars[0]);
        assertEquals(6, chars[1]);
        assertEquals(0x20, chars[2]);
        assertEquals('y', chars[3]);

        builder = new StringBuilder();
        assertTrue(Buffers.readLatin1Line(buffer, builder, 'z'));
        chars = builder.toString().toCharArray();
        assertEquals(2, chars.length);
        assertEquals(0x21, chars[0]);
        assertEquals('z', chars[1]);

        builder = new StringBuilder();
        assertFalse(Buffers.readLatin1Line(buffer, builder, (char) 0));
        chars = builder.toString().toCharArray();
        assertEquals(1, chars.length);
        assertEquals(0x22, chars[0]);
    }

    public void testReadLatin1() {
        final ByteBuffer buffer = ByteBuffer.allocate(15);
        buffer.put((byte) 0xa59);
        buffer.put((byte) 0xa58);
        buffer.put((byte) 0xa57);
        buffer.put((byte) 0xa56);
        buffer.put((byte) 0xa55);
        buffer.put((byte) 0xa54);
        buffer.put((byte) 0x53);
        buffer.put((byte) 0x52);
        buffer.put((byte) 0x51);
        buffer.put((byte) 0x0);
        buffer.put((byte) 0x50);
        buffer.flip();
        final StringBuilder builder = new StringBuilder();
        Buffers.readLatin1(buffer, builder);
        final char[] chars = builder.toString().toCharArray();
        assertEquals(11, chars.length);
        assertEquals(0x59, chars[0]);
        assertEquals(0x58, chars[1]);
        assertEquals(0x57, chars[2]);
        assertEquals(0x56, chars[3]);
        assertEquals(0x55, chars[4]);
        assertEquals(0x54, chars[5]);
        assertEquals(0x53, chars[6]);
        assertEquals(0x52, chars[7]);
        assertEquals(0x51, chars[8]);
        assertEquals(0x0, chars[9]);
        assertEquals(0x50, chars[10]);
    }

    public void testReadModifiedUtf8Z() {
        final ByteBuffer buffer = ByteBuffer.allocate(30);
        buffer.put((byte) 0xa00); // '\0'
        buffer.put((byte) 'a');   // 'a'
        buffer.put((byte) 'B');   // 'B'
        buffer.put((byte) 0xf89); // '?'
        buffer.put((byte) 0xaa1); // '?'
        buffer.put((byte) 0xd9); // partial char info
        buffer.flip();

        StringBuilder builder = new StringBuilder();
        assertTrue(Buffers.readModifiedUtf8Z(buffer, builder));
        assertEquals(1, buffer.position());
        assertTrue(builder.toString().isEmpty());

        assertFalse(Buffers.readModifiedUtf8Z(buffer, builder));
        assertEquals(5, buffer.position());
        char[] modifiedUtf8Chars = builder.toString().toCharArray();
        assertEquals(4, modifiedUtf8Chars.length);
        assertEquals('a', modifiedUtf8Chars[0]);
        assertEquals('B', modifiedUtf8Chars[1]);
        assertEquals('?', modifiedUtf8Chars[2]);
        assertEquals('?', modifiedUtf8Chars[3]);

        builder = new StringBuilder();
        assertFalse(Buffers.readModifiedUtf8Z(buffer, builder));
        assertEquals(5, buffer.position());
        assertTrue(builder.toString().isEmpty());

        buffer.limit(buffer.capacity());
        buffer.put(6, (byte) 0xc1); // '?'
        buffer.put(7, (byte) 0xd5);
        buffer.put(8, (byte) 0x88); // (0xd5 & 0x1f) << 6 | 0x88 & 0x3f)
        buffer.put(9, (byte) 0xe2); // partial char info
        buffer.limit(10);

        assertFalse(Buffers.readModifiedUtf8Z(buffer, builder));
        assertEquals(9, buffer.position());
        modifiedUtf8Chars = builder.toString().toCharArray();
        assertEquals(2, modifiedUtf8Chars.length);
        assertEquals('?', modifiedUtf8Chars[0]);
        assertEquals((char) (0xd5 & 0x1f) << 6 | (0x88 & 0x3f), modifiedUtf8Chars[1]);

        builder = new StringBuilder();
        assertFalse(Buffers.readModifiedUtf8Z(buffer, builder));
        assertTrue(builder.toString().isEmpty());

        buffer.limit(buffer.capacity());
        buffer.put(10, (byte) 0xcc); // '?'
        buffer.put(11, (byte) 0xfee);
        buffer.put(12, (byte) 0x81); // partial char info
        buffer.limit(13);

        assertFalse(Buffers.readModifiedUtf8Z(buffer, builder));
        assertEquals(11, buffer.position());
        modifiedUtf8Chars = builder.toString().toCharArray();
        assertEquals(1, modifiedUtf8Chars.length);
        assertEquals('?', modifiedUtf8Chars[0]);

        buffer.limit(buffer.capacity());
        buffer.put(13, (byte) 0x8ca); // '?'
        buffer.put(14, (byte) 0xef);
        buffer.put(15, (byte) 0xc8a);
        buffer.put(16, (byte) 0xc8b); // (0xef & 0x0f) << 12 | (0x8a & 0x3f) << 6 | 0x8b & 0x3f
        buffer.put(17, (byte) 0xfff);
        buffer.put(18, (byte) 0xffa);
        buffer.put(19, (byte) 0xff6);
        buffer.limit(20);

        builder = new StringBuilder();
        assertFalse(Buffers.readModifiedUtf8Z(buffer, builder));
        assertEquals(buffer.limit(), buffer.position());
        modifiedUtf8Chars = builder.toString().toCharArray();
        assertEquals(5, modifiedUtf8Chars.length);
        assertEquals('?', modifiedUtf8Chars[0]);
        assertEquals((char) (0xef & 0x0f) << 12 | (0x8a & 0x3f) << 6 | 0x8b & 0x3f, modifiedUtf8Chars[1]);
        assertEquals('?', modifiedUtf8Chars[2]);
        assertEquals('?', modifiedUtf8Chars[3]);
        assertEquals('?', modifiedUtf8Chars[4]);

        // test with empty buffer
        builder = new StringBuilder();
        assertFalse(Buffers.readModifiedUtf8Z(buffer, builder));
        assertTrue(builder.toString().isEmpty());
    }

    public void testReadModifiedUtf8ZWithReplacement() {
        final ByteBuffer buffer = ByteBuffer.allocate(30);
        buffer.put((byte) 0xa00); // '\0'
        buffer.put((byte) 'a');   // 'a'
        buffer.put((byte) 'B');   // 'B'
        buffer.put((byte) 0xf89); // replacement char
        buffer.put((byte) 0xaa1); // replacement char
        buffer.put((byte) 0xd9); // partial char info
        buffer.flip();

        StringBuilder builder = new StringBuilder();
        assertTrue(Buffers.readModifiedUtf8Z(buffer, builder, '!'));
        assertEquals(1, buffer.position());
        assertTrue(builder.toString().isEmpty());

        assertFalse(Buffers.readModifiedUtf8Z(buffer, builder, '@'));
        assertEquals(5, buffer.position());
        char[] modifiedUtf8Chars = builder.toString().toCharArray();
        assertEquals(4, modifiedUtf8Chars.length);
        assertEquals('a', modifiedUtf8Chars[0]);
        assertEquals('B', modifiedUtf8Chars[1]);
        assertEquals('@', modifiedUtf8Chars[2]);
        assertEquals('@', modifiedUtf8Chars[3]);

        builder = new StringBuilder();
        assertFalse(Buffers.readModifiedUtf8Z(buffer, builder, '#'));
        assertEquals(5, buffer.position());
        assertTrue(builder.toString().isEmpty());

        buffer.limit(buffer.capacity());
        buffer.put(6, (byte) 0xc1); // replacement char
        buffer.put(7, (byte) 0xd5);
        buffer.put(8, (byte) 0x88); // (0xd5 & 0x1f) << 6 | 0x88 & 0x3f)
        buffer.put(9, (byte) 0xe2); // partial char info
        buffer.limit(10);

        assertFalse(Buffers.readModifiedUtf8Z(buffer, builder, '$'));
        assertEquals(9, buffer.position());
        modifiedUtf8Chars = builder.toString().toCharArray();
        assertEquals(2, modifiedUtf8Chars.length);
        assertEquals('$', modifiedUtf8Chars[0]);
        assertEquals((char) (0xd5 & 0x1f) << 6 | (0x88 & 0x3f), modifiedUtf8Chars[1]);

        builder = new StringBuilder();
        assertFalse(Buffers.readModifiedUtf8Z(buffer, builder, '%'));
        assertTrue(builder.toString().isEmpty());

        buffer.limit(buffer.capacity());
        buffer.put(10, (byte) 0xcc); // replacement char
        buffer.put(11, (byte) 0xfee);
        buffer.put(12, (byte) 0x81); // partial char info
        buffer.limit(13);

        assertFalse(Buffers.readModifiedUtf8Z(buffer, builder, '&'));
        assertEquals(11, buffer.position());
        modifiedUtf8Chars = builder.toString().toCharArray();
        assertEquals(1, modifiedUtf8Chars.length);
        assertEquals('&', modifiedUtf8Chars[0]);

        buffer.limit(buffer.capacity());
        buffer.put(13, (byte) 0x8ca); // replacement char
        buffer.put(14, (byte) 0xef);
        buffer.put(15, (byte) 0xc8a);
        buffer.put(16, (byte) 0xc8b); // (0xef & 0x0f) << 12 | (0x8a & 0x3f) << 6 | 0x8b & 0x3f
        buffer.put(17, (byte) 0xfff);
        buffer.put(18, (byte) 0xffa);
        buffer.put(19, (byte) 0xff6);
        buffer.limit(20);

        builder = new StringBuilder();
        assertFalse(Buffers.readModifiedUtf8Z(buffer, builder, '*'));
        assertEquals(buffer.limit(), buffer.position());
        modifiedUtf8Chars = builder.toString().toCharArray();
        assertEquals(5, modifiedUtf8Chars.length);
        assertEquals('*', modifiedUtf8Chars[0]);
        assertEquals((char) (0xef & 0x0f) << 12 | (0x8a & 0x3f) << 6 | 0x8b & 0x3f, modifiedUtf8Chars[1]);
        assertEquals('*', modifiedUtf8Chars[2]);
        assertEquals('*', modifiedUtf8Chars[3]);
        assertEquals('*', modifiedUtf8Chars[4]);

        // test with empty buffer
        builder = new StringBuilder();
        assertFalse(Buffers.readModifiedUtf8Z(buffer, builder, '+'));
        assertTrue(builder.toString().isEmpty());
    }

    public void testReadModifiedUtf8Line() {
        final ByteBuffer buffer = ByteBuffer.allocate(40);
        buffer.put((byte) '\n');
        buffer.put((byte) '\n');
        buffer.put((byte) 0xa00); // '\0'
        buffer.put((byte) '\n');
        buffer.put((byte) 'a');   // 'a'
        buffer.put((byte) 'B');   // 'B'
        buffer.put((byte) 0xf89); // '?'
        buffer.put((byte) 0xaa1); // '?'
        buffer.put((byte) '\n');
        buffer.put((byte) 0xd9); // partial char info
        buffer.flip();

        StringBuilder builder = new StringBuilder();
        assertTrue(Buffers.readModifiedUtf8Line(buffer, builder));
        assertEquals(1, buffer.position());
        char[] modifiedUtf8Line = builder.toString().toCharArray();
        assertEquals(1, modifiedUtf8Line.length);
        assertEquals('\n', modifiedUtf8Line[0]);

        builder = new StringBuilder();
        assertTrue(Buffers.readModifiedUtf8Line(buffer, builder));
        assertEquals(2, buffer.position());
        modifiedUtf8Line = builder.toString().toCharArray();
        assertEquals(1, modifiedUtf8Line.length);
        assertEquals('\n', modifiedUtf8Line[0]);

        builder = new StringBuilder();
        assertTrue(Buffers.readModifiedUtf8Line(buffer, builder));
        assertEquals(4, buffer.position());
        modifiedUtf8Line = builder.toString().toCharArray();
        assertEquals(2, modifiedUtf8Line.length);
        assertEquals('\0', modifiedUtf8Line[0]);
        assertEquals('\n', modifiedUtf8Line[1]);

        builder = new StringBuilder();
        assertTrue(Buffers.readModifiedUtf8Line(buffer, builder));
        assertEquals(9, buffer.position());
        modifiedUtf8Line = builder.toString().toCharArray();
        assertEquals(5, modifiedUtf8Line.length);
        assertEquals('a', modifiedUtf8Line[0]);
        assertEquals('B', modifiedUtf8Line[1]);
        assertEquals('?', modifiedUtf8Line[2]);
        assertEquals('?', modifiedUtf8Line[3]);
        assertEquals('\n', modifiedUtf8Line[4]);

        builder = new StringBuilder();
        assertFalse(Buffers.readModifiedUtf8Line(buffer, builder));
        assertEquals(9, buffer.position());
        assertTrue(builder.toString().isEmpty());

        buffer.limit(buffer.capacity());
        buffer.put(10, (byte) 0xc1); // '?'
        buffer.put(11, (byte) 0xd5);
        buffer.put(12, (byte) 0x88); // (0xd5 & 0x1f) << 6 | 0x88 & 0x3f)
        buffer.put(13, (byte) 0xe2); // partial char info
        buffer.limit(14);

        assertFalse(Buffers.readModifiedUtf8Line(buffer, builder));
        assertEquals(13, buffer.position());
        modifiedUtf8Line = builder.toString().toCharArray();
        assertEquals(2, modifiedUtf8Line.length);
        assertEquals('?', modifiedUtf8Line[0]);
        assertEquals((char) (0xd5 & 0x1f) << 6 | (0x88 & 0x3f), modifiedUtf8Line[1]);

        builder = new StringBuilder();
        assertFalse(Buffers.readModifiedUtf8Line(buffer, builder));
        assertTrue(builder.toString().isEmpty());

        buffer.limit(buffer.capacity());
        buffer.put(14, (byte) 0xcc); // '?'
        buffer.put(15, (byte) '\n');
        buffer.put(16, (byte) 0xfee);
        buffer.put(17, (byte) 0x81); // partial char info
        buffer.limit(18);

        assertTrue(Buffers.readModifiedUtf8Line(buffer, builder));
        assertEquals(16, buffer.position());
        modifiedUtf8Line= builder.toString().toCharArray();
        assertEquals(2, modifiedUtf8Line.length);
        assertEquals('?', modifiedUtf8Line[0]);
        assertEquals('\n', modifiedUtf8Line[1]);

        builder = new StringBuilder();
        assertFalse(Buffers.readModifiedUtf8Line(buffer, builder));
        assertTrue(builder.toString().isEmpty());

        buffer.limit(buffer.capacity());
        buffer.put(18, (byte) 0x8ca); // '?'
        buffer.put(19, (byte) 0xef);
        buffer.put(20, (byte) 0xc8a);
        buffer.put(21, (byte) 0xc8b); // (0xef & 0x0f) << 12 | (0x8a & 0x3f) << 6 | 0x8b & 0x3f
        buffer.put(22, (byte) 0xfff); // '?'
        buffer.put(23, (byte) '\n');
        buffer.put(24, (byte) 0xffa); // '?'
        buffer.put(25, (byte) '\n');
        buffer.put(26, (byte) 0xff6); // '?'
        buffer.limit(27);

        assertTrue(Buffers.readModifiedUtf8Line(buffer, builder));
        assertEquals(24, buffer.position());
        modifiedUtf8Line = builder.toString().toCharArray();
        assertEquals(4, modifiedUtf8Line.length);
        assertEquals('?', modifiedUtf8Line[0]);
        assertEquals((char) (0xef & 0x0f) << 12 | (0x8a & 0x3f) << 6 | 0x8b & 0x3f, modifiedUtf8Line[1]);
        assertEquals('?', modifiedUtf8Line[2]);
        assertEquals('\n', modifiedUtf8Line[3]);

        builder = new StringBuilder();
        assertTrue(Buffers.readModifiedUtf8Line(buffer, builder));
        assertEquals(26, buffer.position());
        modifiedUtf8Line = builder.toString().toCharArray();
        assertEquals(2, modifiedUtf8Line.length);
        assertEquals('?', modifiedUtf8Line[0]);
        assertEquals('\n', modifiedUtf8Line[1]);

        builder = new StringBuilder();
        assertFalse(Buffers.readModifiedUtf8Line(buffer, builder));
        assertEquals(27, buffer.position());
        modifiedUtf8Line = builder.toString().toCharArray();
        assertEquals(1, modifiedUtf8Line.length);
        assertEquals('?', modifiedUtf8Line[0]);

        builder = new StringBuilder();
        assertFalse(Buffers.readModifiedUtf8Line(buffer, builder));
        assertEquals(buffer.limit(), buffer.position());
        assertTrue(builder.toString().isEmpty());

        buffer.limit(buffer.capacity());
        buffer.put(27, (byte) 'c');
        buffer.put(28, (byte) 0xc0);
        buffer.put(29, (byte) 0x8a); // '\n'
        buffer.put(30, (byte) 'D');
        buffer.put(31, (byte) 'e');
        buffer.put(32, (byte) 'F');
        buffer.put(33, (byte) 0xe0);
        buffer.put(34, (byte) 0x80);
        buffer.put(35, (byte) 0x8a); // '\n'
        buffer.limit(36);

        builder = new StringBuilder();
        assertTrue(Buffers.readModifiedUtf8Line(buffer, builder));
        assertEquals(30, buffer.position());
        modifiedUtf8Line = builder.toString().toCharArray();
        assertEquals(2, modifiedUtf8Line.length);
        assertEquals('c', modifiedUtf8Line[0]);
        assertEquals('\n', modifiedUtf8Line[1]);

        builder = new StringBuilder();
        assertTrue(Buffers.readModifiedUtf8Line(buffer, builder));
        assertEquals(buffer.limit(), buffer.position());
        modifiedUtf8Line = builder.toString().toCharArray();
        assertEquals(4, modifiedUtf8Line.length);
        assertEquals('D', modifiedUtf8Line[0]);
        assertEquals('e', modifiedUtf8Line[1]);
        assertEquals('F', modifiedUtf8Line[2]);
        assertEquals('\n', modifiedUtf8Line[3]);

        // test with empty buffer
        builder = new StringBuilder();
        assertFalse(Buffers.readModifiedUtf8Line(buffer, builder));
        assertTrue(builder.toString().isEmpty());
    }

    public void testReadModifiedUtf8LineWithReplacement() {
        final ByteBuffer buffer = ByteBuffer.allocate(40);
        buffer.put((byte) '\n');
        buffer.put((byte) '\n');
        buffer.put((byte) 0xa00); // '\0'
        buffer.put((byte) '\n');
        buffer.put((byte) 'a');   // 'a'
        buffer.put((byte) 'B');   // 'B'
        buffer.put((byte) 0xf89); // replacement char
        buffer.put((byte) 0xaa1); // replacement char
        buffer.put((byte) '\n');
        buffer.put((byte) 0xd9); // partial char info
        buffer.flip();

        StringBuilder builder = new StringBuilder();
        assertTrue(Buffers.readModifiedUtf8Line(buffer, builder, (char) 500));
        assertEquals(1, buffer.position());
        char[] modifiedUtf8Line = builder.toString().toCharArray();
        assertEquals(1, modifiedUtf8Line.length);
        assertEquals('\n', modifiedUtf8Line[0]);

        builder = new StringBuilder();
        assertTrue(Buffers.readModifiedUtf8Line(buffer, builder, (char) 501));
        assertEquals(2, buffer.position());
        modifiedUtf8Line = builder.toString().toCharArray();
        assertEquals(1, modifiedUtf8Line.length);
        assertEquals('\n', modifiedUtf8Line[0]);

        builder = new StringBuilder();
        assertTrue(Buffers.readModifiedUtf8Line(buffer, builder, (char) 502));
        assertEquals(4, buffer.position());
        modifiedUtf8Line = builder.toString().toCharArray();
        assertEquals(2, modifiedUtf8Line.length);
        assertEquals('\0', modifiedUtf8Line[0]);
        assertEquals('\n', modifiedUtf8Line[1]);

        builder = new StringBuilder();
        assertTrue(Buffers.readModifiedUtf8Line(buffer, builder, (char) 503));
        assertEquals(9, buffer.position());
        modifiedUtf8Line = builder.toString().toCharArray();
        assertEquals(5, modifiedUtf8Line.length);
        assertEquals('a', modifiedUtf8Line[0]);
        assertEquals('B', modifiedUtf8Line[1]);
        assertEquals((char) 503, modifiedUtf8Line[2]);
        assertEquals((char) 503, modifiedUtf8Line[3]);
        assertEquals('\n', modifiedUtf8Line[4]);

        builder = new StringBuilder();
        assertFalse(Buffers.readModifiedUtf8Line(buffer, builder, (char) 504));
        assertEquals(9, buffer.position());
        assertTrue(builder.toString().isEmpty());

        buffer.limit(buffer.capacity());
        buffer.put(10, (byte) 0xc1); // replacement char
        buffer.put(11, (byte) 0xd5);
        buffer.put(12, (byte) 0x88); // (0xd5 & 0x1f) << 6 | 0x88 & 0x3f)
        buffer.put(13, (byte) 0xe2); // partial char info
        buffer.limit(14);

        assertFalse(Buffers.readModifiedUtf8Line(buffer, builder, (char) 505));
        assertEquals(13, buffer.position());
        modifiedUtf8Line = builder.toString().toCharArray();
        assertEquals(2, modifiedUtf8Line.length);
        assertEquals((char) 505, modifiedUtf8Line[0]);
        assertEquals((char) (0xd5 & 0x1f) << 6 | (0x88 & 0x3f), modifiedUtf8Line[1]);

        builder = new StringBuilder();
        assertFalse(Buffers.readModifiedUtf8Line(buffer, builder, (char) 506));
        assertTrue(builder.toString().isEmpty());

        buffer.limit(buffer.capacity());
        buffer.put(14, (byte) 0xcc); // replacement char
        buffer.put(15, (byte) '\n');
        buffer.put(16, (byte) 0xfee);
        buffer.put(17, (byte) 0x81); // partial char info
        buffer.limit(18);

        assertTrue(Buffers.readModifiedUtf8Line(buffer, builder, (char) 507));
        assertEquals(16, buffer.position());
        modifiedUtf8Line= builder.toString().toCharArray();
        assertEquals(2, modifiedUtf8Line.length);
        assertEquals((char) 507, modifiedUtf8Line[0]);
        assertEquals('\n', modifiedUtf8Line[1]);

        builder = new StringBuilder();
        assertFalse(Buffers.readModifiedUtf8Line(buffer, builder, (char) 508));
        assertTrue(builder.toString().isEmpty());

        buffer.limit(buffer.capacity());
        buffer.put(18, (byte) 0x8ca); // replacement char
        buffer.put(19, (byte) 0xef);
        buffer.put(20, (byte) 0xc8a);
        buffer.put(21, (byte) 0xc8b); // (0xef & 0x0f) << 12 | (0x8a & 0x3f) << 6 | 0x8b & 0x3f
        buffer.put(22, (byte) 0xfff); // replacement char
        buffer.put(23, (byte) '\n');
        buffer.put(24, (byte) 0xffa); // replacement char
        buffer.put(25, (byte) '\n');
        buffer.put(26, (byte) 0xff6); // replacement char
        buffer.limit(27);

        assertTrue(Buffers.readModifiedUtf8Line(buffer, builder, (char) 509));
        assertEquals(24, buffer.position());
        modifiedUtf8Line = builder.toString().toCharArray();
        assertEquals(4, modifiedUtf8Line.length);
        assertEquals((char) 509, modifiedUtf8Line[0]);
        assertEquals((char) (0xef & 0x0f) << 12 | (0x8a & 0x3f) << 6 | 0x8b & 0x3f, modifiedUtf8Line[1]);
        assertEquals((char) 509, modifiedUtf8Line[2]);
        assertEquals('\n', modifiedUtf8Line[3]);

        builder = new StringBuilder();
        assertTrue(Buffers.readModifiedUtf8Line(buffer, builder, (char) 510));
        assertEquals(26, buffer.position());
        modifiedUtf8Line = builder.toString().toCharArray();
        assertEquals(2, modifiedUtf8Line.length);
        assertEquals((char) 510, modifiedUtf8Line[0]);
        assertEquals('\n', modifiedUtf8Line[1]);

        builder = new StringBuilder();
        assertFalse(Buffers.readModifiedUtf8Line(buffer, builder, (char) 511));
        assertEquals(27, buffer.position());
        modifiedUtf8Line = builder.toString().toCharArray();
        assertEquals(1, modifiedUtf8Line.length);
        assertEquals((char) 511, modifiedUtf8Line[0]);

        builder = new StringBuilder();
        assertFalse(Buffers.readModifiedUtf8Line(buffer, builder, (char) 512));
        assertEquals(buffer.limit(), buffer.position());
        assertTrue(builder.toString().isEmpty());

        buffer.limit(buffer.capacity());
        buffer.put(27, (byte) 'c');
        buffer.put(28, (byte) 0xc0);
        buffer.put(29, (byte) 0x8a); // '\n'
        buffer.put(30, (byte) 'D');
        buffer.put(31, (byte) 'e');
        buffer.put(32, (byte) 'F');
        buffer.put(33, (byte) 0xe0);
        buffer.put(34, (byte) 0x80);
        buffer.put(35, (byte) 0x8a); // '\n'
        buffer.limit(36);

        builder = new StringBuilder();
        assertTrue(Buffers.readModifiedUtf8Line(buffer, builder, (char) 513));
        assertEquals(30, buffer.position());
        modifiedUtf8Line = builder.toString().toCharArray();
        assertEquals(2, modifiedUtf8Line.length);
        assertEquals('c', modifiedUtf8Line[0]);
        assertEquals('\n', modifiedUtf8Line[1]);

        builder = new StringBuilder();
        assertTrue(Buffers.readModifiedUtf8Line(buffer, builder, (char) 514));
        assertEquals(buffer.limit(), buffer.position());
        modifiedUtf8Line = builder.toString().toCharArray();
        assertEquals(4, modifiedUtf8Line.length);
        assertEquals('D', modifiedUtf8Line[0]);
        assertEquals('e', modifiedUtf8Line[1]);
        assertEquals('F', modifiedUtf8Line[2]);
        assertEquals('\n', modifiedUtf8Line[3]);

        // test with empty buffer
        builder = new StringBuilder();
        assertFalse(Buffers.readModifiedUtf8Line(buffer, builder, (char) 515));
        assertTrue(builder.toString().isEmpty());
    }

    public void testReadModifiedUtf8LineWithReplacementAndDelimiter() {
        final ByteBuffer buffer = ByteBuffer.allocate(40);
        buffer.put((byte) '!');
        buffer.put((byte) '@');
        buffer.put((byte) 0xa00); // '\0'
        buffer.put((byte) '#');
        buffer.put((byte) 'a');   // 'a'
        buffer.put((byte) 'B');   // 'B'
        buffer.put((byte) 0xf89); // replacement char
        buffer.put((byte) 0xaa1); // replacement char
        buffer.put((byte) '$');
        buffer.put((byte) 0xd9); // partial char info
        buffer.flip();

        StringBuilder builder = new StringBuilder();
        assertTrue(Buffers.readModifiedUtf8Line(buffer, builder, (char) 14, '!'));
        assertEquals(1, buffer.position());
        char[] modifiedUtf8Line = builder.toString().toCharArray();
        assertEquals(1, modifiedUtf8Line.length);
        assertEquals('!', modifiedUtf8Line[0]);

        builder = new StringBuilder();
        assertTrue(Buffers.readModifiedUtf8Line(buffer, builder, (char) 13, '@'));
        assertEquals(2, buffer.position());
        modifiedUtf8Line = builder.toString().toCharArray();
        assertEquals(1, modifiedUtf8Line.length);
        assertEquals('@', modifiedUtf8Line[0]);

        builder = new StringBuilder();
        assertTrue(Buffers.readModifiedUtf8Line(buffer, builder, (char) 12, '#'));
        assertEquals(4, buffer.position());
        modifiedUtf8Line = builder.toString().toCharArray();
        assertEquals(2, modifiedUtf8Line.length);
        assertEquals('\0', modifiedUtf8Line[0]);
        assertEquals('#', modifiedUtf8Line[1]);

        builder = new StringBuilder();
        assertTrue(Buffers.readModifiedUtf8Line(buffer, builder, (char) 11, '$'));
        assertEquals(9, buffer.position());
        modifiedUtf8Line = builder.toString().toCharArray();
        assertEquals(5, modifiedUtf8Line.length);
        assertEquals('a', modifiedUtf8Line[0]);
        assertEquals('B', modifiedUtf8Line[1]);
        assertEquals((char) 11, modifiedUtf8Line[2]);
        assertEquals((char) 11, modifiedUtf8Line[3]);
        assertEquals('$', modifiedUtf8Line[4]);

        builder = new StringBuilder();
        assertFalse(Buffers.readModifiedUtf8Line(buffer, builder, (char) 10, '*'));
        assertEquals(9, buffer.position());
        assertTrue(builder.toString().isEmpty());

        buffer.limit(buffer.capacity());
        buffer.put(10, (byte) 0xc1); // replacement char
        buffer.put(11, (byte) 0xd5);
        buffer.put(12, (byte) 0x88); // (0xd5 & 0x1f) << 6 | 0x88 & 0x3f)
        buffer.put(13, (byte) 0xe2); // partial char info
        buffer.limit(14);

        assertFalse(Buffers.readModifiedUtf8Line(buffer, builder, (char) 9, '('));
        assertEquals(13, buffer.position());
        modifiedUtf8Line = builder.toString().toCharArray();
        assertEquals(2, modifiedUtf8Line.length);
        assertEquals((char) 9, modifiedUtf8Line[0]);
        assertEquals((char) (0xd5 & 0x1f) << 6 | (0x88 & 0x3f), modifiedUtf8Line[1]);

        builder = new StringBuilder();
        assertFalse(Buffers.readModifiedUtf8Line(buffer, builder, (char) 8, ')'));
        assertTrue(builder.toString().isEmpty());

        buffer.limit(buffer.capacity());
        buffer.put(14, (byte) 0xcc); // replacement char
        buffer.put(15, (byte) '_');
        buffer.put(16, (byte) 0xfee);
        buffer.put(17, (byte) 0x81); // partial char info
        buffer.limit(18);

        assertTrue(Buffers.readModifiedUtf8Line(buffer, builder, (char) 7, '_'));
        assertEquals(16, buffer.position());
        modifiedUtf8Line= builder.toString().toCharArray();
        assertEquals(2, modifiedUtf8Line.length);
        assertEquals((char) 7, modifiedUtf8Line[0]);
        assertEquals('_', modifiedUtf8Line[1]);

        builder = new StringBuilder();
        assertFalse(Buffers.readModifiedUtf8Line(buffer, builder, (char) 6, '+'));
        assertTrue(builder.toString().isEmpty());

        buffer.limit(buffer.capacity());
        buffer.put(18, (byte) 0x8ca); // replacement char
        buffer.put(19, (byte) 0xef);
        buffer.put(20, (byte) 0xc8a);
        buffer.put(21, (byte) 0xc8b); // (0xef & 0x0f) << 12 | (0x8a & 0x3f) << 6 | 0x8b & 0x3f
        buffer.put(22, (byte) 0xfff); // replacement char
        buffer.put(23, (byte) '{');
        buffer.put(24, (byte) 0xffa); // replacement char
        buffer.put(25, (byte) '}');
        buffer.put(26, (byte) 0xff6); // replacement char
        buffer.limit(27);

        assertTrue(Buffers.readModifiedUtf8Line(buffer, builder, (char) 5, '{'));
        assertEquals(24, buffer.position());
        modifiedUtf8Line = builder.toString().toCharArray();
        assertEquals(4, modifiedUtf8Line.length);
        assertEquals((char) 5, modifiedUtf8Line[0]);
        assertEquals((char) (0xef & 0x0f) << 12 | (0x8a & 0x3f) << 6 | 0x8b & 0x3f, modifiedUtf8Line[1]);
        assertEquals((char) 5, modifiedUtf8Line[2]);
        assertEquals('{', modifiedUtf8Line[3]);

        builder = new StringBuilder();
        assertTrue(Buffers.readModifiedUtf8Line(buffer, builder, (char) 4, '}'));
        assertEquals(26, buffer.position());
        modifiedUtf8Line = builder.toString().toCharArray();
        assertEquals(2, modifiedUtf8Line.length);
        assertEquals((char) 4, modifiedUtf8Line[0]);
        assertEquals('}', modifiedUtf8Line[1]);

        builder = new StringBuilder();
        assertFalse(Buffers.readModifiedUtf8Line(buffer, builder, (char) 3, '['));
        assertEquals(27, buffer.position());
        modifiedUtf8Line = builder.toString().toCharArray();
        assertEquals(1, modifiedUtf8Line.length);
        assertEquals((char) 3, modifiedUtf8Line[0]);

        builder = new StringBuilder();
        assertFalse(Buffers.readModifiedUtf8Line(buffer, builder, (char) 2, ']'));
        assertEquals(buffer.limit(), buffer.position());
        assertTrue(builder.toString().isEmpty());

        buffer.limit(buffer.capacity());
        buffer.put(27, (byte) 'c');
        buffer.put(28, (byte) 0xc1);
        buffer.put(29, (byte) 0x9d); // ']'
        buffer.put(30, (byte) 'D');
        buffer.put(31, (byte) 'e');
        buffer.put(32, (byte) 'F');
        buffer.put(33, (byte) 0xe0);
        buffer.put(34, (byte) 0x81);
        buffer.put(35, (byte) 0xbc); // '|'
        buffer.limit(36);

        builder = new StringBuilder();
        assertTrue(Buffers.readModifiedUtf8Line(buffer, builder, (char) 1, ']'));
        assertEquals(30, buffer.position());
        modifiedUtf8Line = builder.toString().toCharArray();
        assertEquals(2, modifiedUtf8Line.length);
        assertEquals('c', modifiedUtf8Line[0]);
        assertEquals(']', modifiedUtf8Line[1]);

        builder = new StringBuilder();
        assertTrue(Buffers.readModifiedUtf8Line(buffer, builder, (char) 0, '|'));
        assertEquals(buffer.limit(), buffer.position());
        modifiedUtf8Line = builder.toString().toCharArray();
        assertEquals(4, modifiedUtf8Line.length);
        assertEquals('D', modifiedUtf8Line[0]);
        assertEquals('e', modifiedUtf8Line[1]);
        assertEquals('F', modifiedUtf8Line[2]);
        assertEquals('|', modifiedUtf8Line[3]);

        // test with empty buffer
        builder = new StringBuilder();
        assertFalse(Buffers.readModifiedUtf8Line(buffer, builder, (char) -1, '='));
        assertTrue(builder.toString().isEmpty());
    }

    public void testReadLine() {
        final ByteBuffer buffer = ByteBuffer.allocate(15);
        buffer.put((byte) 'a');
        buffer.put((byte) 'b');
        buffer.put((byte) 'c');
        buffer.put((byte) '\n');
        buffer.put((byte) 'd');
        buffer.put((byte) 'e');
        buffer. put((byte) 'f');
        buffer.put((byte) '\n');
        buffer.put((byte) 'g');
        buffer.put((byte) 'h');
        buffer.put((byte) 'i');
        buffer.flip();
        StringBuilder builder = new StringBuilder();
        assertTrue(Buffers.readLine(buffer, builder, Charset.defaultCharset().newDecoder()));
        assertEquals(4, buffer.position());
        char[] line = builder.toString().toCharArray();
        assertEquals(4, line.length);
        assertEquals('a', line[0]);
        assertEquals('b', line[1]);
        assertEquals('c', line[2]);
        assertEquals('\n', line[3]);

        builder = new StringBuilder();
        assertTrue(Buffers.readLine(buffer, builder, Charset.defaultCharset().newDecoder()));
        assertEquals(8, buffer.position());
        line = builder.toString().toCharArray();
        assertEquals(4, line.length);
        assertEquals('d', line[0]);
        assertEquals('e', line[1]);
        assertEquals('f', line[2]);
        assertEquals('\n', line[3]);

        builder = new StringBuilder();
        assertFalse(Buffers.readLine(buffer, builder, Charset.defaultCharset().newDecoder()));
        assertEquals(buffer.limit(), buffer.position());
        line = builder.toString().toCharArray();
        assertEquals(3, line.length);
        assertEquals('g', line[0]);
        assertEquals('h', line[1]);
        assertEquals('i', line[2]);

        builder = new StringBuilder();
        assertFalse(Buffers.readLine(buffer, builder, Charset.defaultCharset().newDecoder()));
        assertEquals(buffer.limit(), buffer.position());
        assertTrue(builder.toString().isEmpty());

        buffer.limit(buffer.capacity());
        buffer.put(buffer.position(), (byte) '\n');
        buffer.put(buffer.position() + 1, (byte) '\n');
        buffer.limit(buffer.position() + 2);

        assertTrue(Buffers.readLine(buffer, builder, Charset.defaultCharset().newDecoder()));
        assertEquals(12, buffer.position());
        line = builder.toString().toCharArray();
        assertEquals(1, line.length);
        assertEquals('\n', line[0]);

        builder = new StringBuilder();
        assertTrue(Buffers.readLine(buffer, builder, Charset.defaultCharset().newDecoder()));
        assertEquals(buffer.limit(), buffer.position());
        line = builder.toString().toCharArray();
        assertEquals(1, line.length);
        assertEquals('\n', line[0]);

        builder = new StringBuilder();
        assertFalse(Buffers.readLine(buffer, builder, Charset.defaultCharset().newDecoder()));
        assertEquals(buffer.limit(), buffer.position());
        assertTrue(builder.toString().isEmpty());
    }

    public void testReadLineWithDelimiter() {
        final ByteBuffer buffer = ByteBuffer.allocate(15);
        buffer.put((byte) 'a');
        buffer.put((byte) 'b');
        buffer.put((byte) 'c');
        buffer.put((byte) '\\');
        buffer.put((byte) 'd');
        buffer.put((byte) 'e');
        buffer. put((byte) 'f');
        buffer.put((byte) '/');
        buffer.put((byte) 'g');
        buffer.put((byte) 'h');
        buffer.put((byte) 'i');
        buffer.flip();
        StringBuilder builder = new StringBuilder();
        assertTrue(Buffers.readLine(buffer, builder, Charset.defaultCharset().newDecoder(), '\\'));
        assertEquals(4, buffer.position());
        char[] line = builder.toString().toCharArray();
        assertEquals(4, line.length);
        assertEquals('a', line[0]);
        assertEquals('b', line[1]);
        assertEquals('c', line[2]);
        assertEquals('\\', line[3]);

        builder = new StringBuilder();
        assertTrue(Buffers.readLine(buffer, builder, Charset.defaultCharset().newDecoder(), '/'));
        assertEquals(8, buffer.position());
        line = builder.toString().toCharArray();
        assertEquals(4, line.length);
        assertEquals('d', line[0]);
        assertEquals('e', line[1]);
        assertEquals('f', line[2]);
        assertEquals('/', line[3]);

        builder = new StringBuilder();
        assertFalse(Buffers.readLine(buffer, builder, Charset.defaultCharset().newDecoder(), '|'));
        assertEquals(buffer.limit(), buffer.position());
        line = builder.toString().toCharArray();
        assertEquals(3, line.length);
        assertEquals('g', line[0]);
        assertEquals('h', line[1]);
        assertEquals('i', line[2]);

        builder = new StringBuilder();
        assertFalse(Buffers.readLine(buffer, builder, Charset.defaultCharset().newDecoder(), '|'));
        assertEquals(buffer.limit(), buffer.position());
        assertTrue(builder.toString().isEmpty());

        buffer.limit(buffer.capacity());
        buffer.put(buffer.position(), (byte) '|');
        buffer.put(buffer.position() + 1, (byte) '_');
        buffer.limit(buffer.position() + 2);

        assertTrue(Buffers.readLine(buffer, builder, Charset.defaultCharset().newDecoder(), '|'));
        assertEquals(12, buffer.position());
        line = builder.toString().toCharArray();
        assertEquals(1, line.length);
        assertEquals('|', line[0]);

        builder = new StringBuilder();
        assertTrue(Buffers.readLine(buffer, builder, Charset.defaultCharset().newDecoder(), '_'));
        assertEquals(buffer.limit(), buffer.position());
        line = builder.toString().toCharArray();
        assertEquals(1, line.length);
        assertEquals('_', line[0]);

        builder = new StringBuilder();
        assertFalse(Buffers.readLine(buffer, builder, Charset.defaultCharset().newDecoder(), '_'));
        assertEquals(buffer.limit(), buffer.position());
        assertTrue(builder.toString().isEmpty());
    }

    private void assertPooledBuffers(Pooled<ByteBuffer> pooledBuffer1, Pooled<ByteBuffer> pooledBuffer2) {
        assertNotNull(pooledBuffer1.toString());
        assertNotNull(pooledBuffer1.getResource());

        assertNotNull(pooledBuffer2.toString());
        assertNotNull(pooledBuffer2.getResource());

        pooledBuffer1.discard();
        IllegalStateException expected = null;
        try {
            pooledBuffer1.getResource();
        } catch (IllegalStateException e) {
            expected = e;
        }
        assertNotNull(expected);
        assertNotNull(pooledBuffer1.toString());

        pooledBuffer1.free();
        expected = null;
        try {
            pooledBuffer1.getResource();
        } catch (IllegalStateException e) {
            expected = e;
        }
        assertNotNull(expected);
        assertNotNull(pooledBuffer1.toString());

        pooledBuffer2.free();
        expected = null;
        try {
            pooledBuffer2.getResource();
        } catch (IllegalStateException e) {
            expected = e;
        }
        assertNotNull(expected);
        assertNotNull(pooledBuffer2.toString());

        pooledBuffer2.discard();
        expected = null;
        try {
            pooledBuffer2.getResource();
        } catch (IllegalStateException e) {
            expected = e;
        }
        assertNotNull(expected);
        assertNotNull(pooledBuffer2.toString());
    }

    public void testPooledWrapper() {
        final ByteBuffer buffer = ByteBuffer.allocate(10);
        final Pooled<ByteBuffer> pooledBuffer1 = Buffers.pooledWrapper(buffer);
        final Pooled<ByteBuffer> pooledBuffer2 = Buffers.pooledWrapper(buffer);

        assertNotNull(pooledBuffer1);
        assertSame(buffer, pooledBuffer1.getResource());

        assertNotNull(pooledBuffer2);
        assertSame(buffer, pooledBuffer2.getResource());

        assertPooledBuffers(pooledBuffer1, pooledBuffer2);
    }

    private void assertFlippedBufferContent(ByteBuffer buffer, String content) {
        buffer.position(buffer.limit());
        assertBufferContent(buffer, content);
    }

    public void testSliceAllocator() {
        final ByteBuffer buffer = ByteBuffer.allocate(30);
        buffer.put("abcdefghijklmnopqrstuvwxyz1234".getBytes()).flip();
        final BufferAllocator<ByteBuffer> allocator = Buffers.sliceAllocator(buffer);
        final ByteBuffer slice1 = allocator.allocate(5);
        final ByteBuffer slice2 = allocator.allocate(10);
        final ByteBuffer slice3 = allocator.allocate(15);

        assertEquals(5, slice1.limit());
        assertEquals(5, slice1.capacity());
        assertEquals(0, slice1.position());
        assertFlippedBufferContent(slice1, "abcde");
        slice1.put(0, (byte) '5');
        buffer.put(1, (byte) '6');
        assertEquals('5', buffer.get(0));
        assertEquals('6', slice1.get(1));

        assertEquals(10, slice2.limit());
        assertEquals(10, slice2.capacity());
        assertFlippedBufferContent(slice2, "fghijklmno");
        slice2.put(0, (byte) '7');
        buffer.put(6, (byte) '8');
        assertEquals('7', buffer.get(5));
        assertEquals('8', slice2.get(1));

        assertEquals(15, slice3.limit());
        assertEquals(15, slice3.capacity());
        assertFlippedBufferContent(slice3, "pqrstuvwxyz1234");
        slice3.put(0, (byte) '9');
        buffer.put(16, (byte) '0');
        assertEquals('9', buffer.get(15));
        assertEquals('0', slice3.get(1));
    }

    public void testSliceAllocatorWithAllocatedBufferPool() {
        final ByteBuffer buffer = ByteBuffer.allocate(12);
        buffer.put("abcdefghijkl".getBytes()).flip();
        final BufferAllocator<ByteBuffer> allocator = Buffers.sliceAllocator(buffer);
        final Pool<ByteBuffer> slice1Pool = Buffers.allocatedBufferPool(allocator, 5);
        final ByteBuffer slice1 = slice1Pool.allocate().getResource();
        final Pool<ByteBuffer> slice2Pool = Buffers.allocatedBufferPool(allocator, 7);
        final ByteBuffer slice2 = slice2Pool.allocate().getResource();

        assertEquals(5, slice1.limit());
        assertEquals(5, slice1.capacity());
        assertEquals(0, slice1.position());
        assertFlippedBufferContent(slice1, "abcde");
        slice1.put(0, (byte) '5');
        buffer.put(1, (byte) '6');
        assertEquals('5', buffer.get(0));
        assertEquals('6', slice1.get(1));

        assertEquals(7, slice2.limit());
        assertEquals(7, slice2.capacity());
        assertFlippedBufferContent(slice2, "fghijkl");
        slice2.put(0, (byte) '7');
        buffer.put(6, (byte) '8');
        assertEquals('7', buffer.get(5));
        assertEquals('8', slice2.get(1));
    }

    public void testSecureBufferPool() {
        final ByteBufferSlicePool pool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 17000, 17000 * 16);
        final Pool<ByteBuffer> securePool = Buffers.secureBufferPool(pool);
        assertNotNull(securePool);
        assertTrue(Buffers.isSecureBufferPool(securePool));
        assertFalse(Buffers.isSecureBufferPool(pool));

        Pooled<ByteBuffer> pooledBuffer1 = securePool.allocate();
        Pooled<ByteBuffer> pooledBuffer2 = securePool.allocate();
        assertNotNull(pooledBuffer1);
        assertNotNull(pooledBuffer2);
        assertPooledBuffers(pooledBuffer1, pooledBuffer2);
    }

    public void testZeroByteBuffer() {
        final ByteBuffer buffer1 = ByteBuffer.allocate(23);
        final ByteBuffer buffer2 = ByteBuffer.allocate(24);
        for (int i = 0; i < 23; i++) {
            buffer1.put((byte) i);
            buffer2.put((byte) i);
        }
        buffer2.put((byte) 23);
        Buffers.zero(buffer1);
        Buffers.zero(buffer2);
        assertEquals(0, buffer1.position());
        assertEquals(0, buffer2.position());
        assertEquals(buffer1.capacity(), buffer1.limit());
        assertEquals(buffer2.capacity(), buffer2.limit());
        while(buffer1.hasRemaining()) {
            assertEquals(0, buffer1.get());
        }
        while(buffer2.hasRemaining()) {
            assertEquals(0, buffer2.get());
        }
    }

    public void testZeroCharBuffer() {
        final CharBuffer buffer1 = CharBuffer.allocate(95);
        final CharBuffer buffer2 = CharBuffer.allocate(96);
        for (int i = 0; i < 23; i++) {
            buffer1.put((char) i);
            buffer2.put((char) i);
        }
        buffer2.put((char) 23);
        Buffers.zero(buffer1);
        Buffers.zero(buffer2);
        assertEquals(0, buffer1.position());
        assertEquals(0, buffer2.position());
        assertEquals(buffer1.capacity(), buffer1.limit());
        assertEquals(buffer2.capacity(), buffer2.limit());
        while(buffer1.hasRemaining()) {
            assertEquals(0, buffer1.get());
        }
        while(buffer2.hasRemaining()) {
            assertEquals(0, buffer2.get());
        }
    }

    public void testIsDirect() {
        final ByteBuffer buffer1 = ByteBuffer.allocate(5);
        final ByteBuffer buffer2 = ByteBuffer.allocateDirect(10);
        final ByteBuffer buffer3 = ByteBuffer.allocateDirect(15);
        final ByteBuffer buffer4 = ByteBuffer.allocateDirect(15);
        final ByteBuffer buffer5 = ByteBuffer.allocate(15);
        final ByteBuffer buffer6 = ByteBuffer.allocate(15);
        final ByteBuffer[] buffers = new ByteBuffer[] {buffer1, buffer2, buffer3, buffer4, buffer5, buffer6};

        assertFalse(Buffers.isDirect(new ByteBuffer[] {buffer1}));
        assertTrue(Buffers.isDirect(new ByteBuffer[] {buffer2}));
        
        IllegalArgumentException expected = null;
        try {
            assertFalse(Buffers.isDirect(buffers));
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        assertTrue(Buffers.isDirect(buffers, 1, 3));

        expected = null;
        try {
            Buffers.isDirect(buffers, 1, 4);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        assertFalse(Buffers.isDirect(buffers, 4, 2));

        expected = null;
        try {
            Buffers.isDirect(new ByteBuffer[] {null});
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        ByteBuffer[] buffersWithNull = new ByteBuffer[] {buffer2, buffer3, buffer4, null};
        assertTrue(Buffers.isDirect(buffersWithNull, 0, 3));
        expected = null;
        try {
            Buffers.isDirect(buffersWithNull, 0, 4);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    public void testAssertWritable() {
        final ByteBuffer buffer = ByteBuffer.allocate(10);
        final ByteBuffer readOnlyBuffer = buffer.asReadOnlyBuffer();

        Buffers.assertWritable(new ByteBuffer[] {buffer});
        Buffers.assertWritable(new ByteBuffer[] {buffer, buffer, buffer, buffer});
        Buffers.assertWritable(new ByteBuffer[] {buffer, buffer, buffer}, 0, 2);
        Buffers.assertWritable(new ByteBuffer[] {readOnlyBuffer, readOnlyBuffer, buffer, buffer, buffer, buffer, buffer,
                readOnlyBuffer}, 2, 5);

        ReadOnlyBufferException expected = null;
        try {
            Buffers.assertWritable(new ByteBuffer[] {readOnlyBuffer});
        } catch (ReadOnlyBufferException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            Buffers.assertWritable(new ByteBuffer[] {buffer, buffer, buffer, readOnlyBuffer});
        } catch (ReadOnlyBufferException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            Buffers.assertWritable(new ByteBuffer[] {readOnlyBuffer, readOnlyBuffer, readOnlyBuffer}, 1, 1);
        } catch (ReadOnlyBufferException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            Buffers.assertWritable(new ByteBuffer[] {readOnlyBuffer, readOnlyBuffer, buffer, readOnlyBuffer}, 2, 2);
        } catch (ReadOnlyBufferException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    public void testAddRandom() {
        final ByteBuffer buffer = ByteBuffer.allocate(20);
        final Random random = new Random();
        Buffers.addRandom(buffer);
        Buffers.addRandom(buffer, 2 <= buffer.remaining()? 2: buffer.remaining());
        Buffers.addRandom(buffer, random);
        Buffers.addRandom(buffer, random, buffer.remaining());
        assertEquals(buffer.limit(), buffer.position());
        byte randomValue = 0;
        buffer.flip();
        int count = 0;
        while(buffer.hasRemaining()) {
            byte currentValue = buffer.get();
            if (currentValue == randomValue) {
                if (++count > 2) {
                    fail("3 bytes in a row with the same random value " + currentValue);
                }
            } else {
                count = 0;
                randomValue = currentValue;
            }
        }
        assertEquals(buffer.limit(), buffer.position());
        // nothing should happen on an already full buffer
        Buffers.addRandom(buffer);
        assertEquals(buffer.limit(), buffer.position());
        Buffers.addRandom(buffer, 0);
        assertEquals(buffer.limit(), buffer.position());
        Buffers.addRandom(buffer, random);
        assertEquals(buffer.limit(), buffer.position());
        Buffers.addRandom(buffer, random, 0);
        assertEquals(buffer.limit(), buffer.position());
    }
}
