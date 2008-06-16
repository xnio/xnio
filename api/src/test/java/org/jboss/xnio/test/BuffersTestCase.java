/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc., and individual contributors as indicated
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

package org.jboss.xnio.test;

import junit.framework.TestCase;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.Arrays;
import org.jboss.xnio.Buffers;
import org.jboss.xnio.test.support.LoggingHelper;

/**
 *
 */
public final class BuffersTestCase extends TestCase {
    static {
        LoggingHelper.init();
    }

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

    private void doTestFillByte(final ByteBuffer buf) {
        assertSame(buf, Buffers.fill(buf, 5, 30));
        assertSame(buf, Buffers.fill(buf, 90, 20));
        assertEquals(50, buf.position());
        assertEquals(5, buf.get(3));
        assertEquals(5, buf.get(29));
        assertEquals(90, buf.get(30));
        assertEquals(90, buf.get(31));
        assertEquals(90, buf.get(49));
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
    }

    public void testFillChar() {
        final CharBuffer buf = CharBuffer.allocate(100);
        doTestFillChar(buf);
    }
}
