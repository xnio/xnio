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

package org.xnio.test;

import junit.framework.TestCase;

import static org.xnio.Bits.*;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class BitsTestCase extends TestCase {
    public void testBitMask() {
        assertEquals(0x00FFFF00, intBitMask(8, 23));
        assertEquals(1, intBitMask(0, 0));
        assertEquals(0xFFFFFFFF, intBitMask(0, 31));
        assertEquals(0x80000000, intBitMask(31, 31));
        assertEquals(0x00FFFFFFFFFFFF00L, longBitMask(8, 55));
        assertEquals(1L, longBitMask(0, 0));
        assertEquals(0xFFFFFFFFFFFFFFFFL, longBitMask(0, 63));
        assertEquals(0x8000000000000000L, longBitMask(63, 63));
    }

    public void testAllAreClearInt() {
        assertTrue(allAreClear(0xFF00FF00, 0x00FF00FF));
        assertTrue(allAreClear(0xFF00FF00, 0x00230042));
        assertTrue(allAreClear(0xFF00FF00, 0));
        assertFalse(allAreClear(0xFF00FF00, 0xFF00FF00));
        assertFalse(allAreClear(0xFF00FF00, 0x3A00E300));
        assertFalse(allAreClear(0xFF00FF00, 0xFFFFFFFF));
        assertFalse(allAreClear(0xFF00FF00, 0x00000100));
        assertFalse(allAreClear(0xFF00FF00, 0x80000000));
    }

    public void testAllAreClearLong() {
        assertTrue(allAreClear(0xFF00FF00FF00FF00L, 0x00FF00FF00FF00FFL));
        assertTrue(allAreClear(0xFF00FF00FF00FF00L, 0x0023004200110055L));
        assertTrue(allAreClear(0xFF00FF00FF00FF00L, 0));
        assertFalse(allAreClear(0xFF00FF00FF00FF00L, 0xFF00FF00FF00FF00L));
        assertFalse(allAreClear(0xFF00FF00FF00FF00L, 0x3A00E3004D002200L));
        assertFalse(allAreClear(0xFF00FF00FF00FF00L, 0xFFFFFFFFFFFFFFFFL));
        assertFalse(allAreClear(0xFF00FF00FF00FF00L, 0x0000010000000000L));
        assertFalse(allAreClear(0xFF00FF00FF00FF00L, 0x8000000000000000L));
    }

    // anyAreSet is the inverse of allAreClear, so each test should be duplicated in both sections

    public void testAnyAreSetInt() {
        assertFalse(anyAreSet(0xFF00FF00, 0x00FF00FF));
        assertFalse(anyAreSet(0xFF00FF00, 0x00230042));
        assertFalse(anyAreSet(0xFF00FF00, 0));
        assertTrue(anyAreSet(0xFF00FF00, 0xFF00FF00));
        assertTrue(anyAreSet(0xFF00FF00, 0x3A00E300));
        assertTrue(anyAreSet(0xFF00FF00, 0xFFFFFFFF));
        assertTrue(anyAreSet(0xFF00FF00, 0x00000100));
        assertTrue(anyAreSet(0xFF00FF00, 0x80000000));
    }

    public void testAnyAreSetLong() {
        assertFalse(anyAreSet(0xFF00FF00FF00FF00L, 0x00FF00FF00FF00FFL));
        assertFalse(anyAreSet(0xFF00FF00FF00FF00L, 0x0023004200110055L));
        assertFalse(anyAreSet(0xFF00FF00FF00FF00L, 0));
        assertTrue(anyAreSet(0xFF00FF00FF00FF00L, 0xFF00FF00FF00FF00L));
        assertTrue(anyAreSet(0xFF00FF00FF00FF00L, 0x3A00E3004D002200L));
        assertTrue(anyAreSet(0xFF00FF00FF00FF00L, 0xFFFFFFFFFFFFFFFFL));
        assertTrue(anyAreSet(0xFF00FF00FF00FF00L, 0x0000010000000000L));
        assertTrue(anyAreSet(0xFF00FF00FF00FF00L, 0x8000000000000000L));
    }

    public void testAllAreSetInt() {
        assertTrue(allAreSet(0xFF00FF00, 0xFF00FF00));
        assertTrue(allAreSet(0xFF00FF00, 0x12003400));
        assertTrue(allAreSet(0xFF00FF00, 0));
        assertFalse(allAreSet(0xFF00FF00, 0x00FF00FF));
        assertFalse(allAreSet(0xFF00FF00, 0x00800000));
        assertFalse(allAreSet(0xFF00FF00, 0x00000001));
        assertFalse(allAreSet(0xFF00FF00, 0x00FF0000));
    }

    public void testAllAreSetLong() {
        assertTrue(allAreSet(0xFF00FF00FF00FF00L, 0xFF00FF00FF00FF00L));
        assertTrue(allAreSet(0xFF00FF00FF00FF00L, 0x1200340056007800L));
        assertTrue(allAreSet(0xFF00FF00FF00FF00L, 0L));
        assertFalse(allAreSet(0xFF00FF00FF00FF00L, 0x00FF00FF00FF00FFL));
        assertFalse(allAreSet(0xFF00FF00FF00FF00L, 0x0080000000000000L));
        assertFalse(allAreSet(0xFF00FF00FF00FF00L, 0x0000000000000001L));
        assertFalse(allAreSet(0xFF00FF00FF00FF00L, 0x00FF000000000000L));
    }

    // anyAreClear is the inverse of allAreSet, so each test should be duplicated in both sections

    public void testAnyAreClearInt() {
        assertFalse(anyAreClear(0xFF00FF00, 0xFF00FF00));
        assertFalse(anyAreClear(0xFF00FF00, 0x12003400));
        assertFalse(anyAreClear(0xFF00FF00, 0));
        assertTrue(anyAreClear(0xFF00FF00, 0x00FF00FF));
        assertTrue(anyAreClear(0xFF00FF00, 0x00800000));
        assertTrue(anyAreClear(0xFF00FF00, 0x00000001));
        assertTrue(anyAreClear(0xFF00FF00, 0x00FF0000));
    }

    public void testAnyAreClearLong() {
        assertFalse(anyAreClear(0xFF00FF00FF00FF00L, 0xFF00FF00FF00FF00L));
        assertFalse(anyAreClear(0xFF00FF00FF00FF00L, 0x1200340056007800L));
        assertFalse(anyAreClear(0xFF00FF00FF00FF00L, 0L));
        assertTrue(anyAreClear(0xFF00FF00FF00FF00L, 0x00FF00FF00FF00FFL));
        assertTrue(anyAreClear(0xFF00FF00FF00FF00L, 0x0080000000000000L));
        assertTrue(anyAreClear(0xFF00FF00FF00FF00L, 0x0000000000000001L));
        assertTrue(anyAreClear(0xFF00FF00FF00FF00L, 0x00FF000000000000L));
    }

    // byte array methods

    public void testByteArrayRead() {
        final byte[] bytes = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16 };
        assertEquals(0x01020304, intFromBytesBE(bytes, 0));
        assertEquals(0x04030201, intFromBytesLE(bytes, 0));
        assertEquals(0x03040506, intFromBytesBE(bytes, 2));
        assertEquals(0x06050403, intFromBytesLE(bytes, 2));
        assertEquals(0x0203, shortFromBytesBE(bytes, 1));
        assertEquals(0x0302, shortFromBytesLE(bytes, 1));
        assertEquals((char) 0x0203, charFromBytesBE(bytes, 1));
        assertEquals((char) 0x0302, charFromBytesLE(bytes, 1));
        assertEquals(0x00030405, mediumFromBytesBE(bytes, 2));
        assertEquals(0x00050403, mediumFromBytesLE(bytes, 2));
        assertEquals(0x060708090a0b0c0dL, longFromBytesBE(bytes, 5));
        assertEquals(0x0d0c0b0a09080706L, longFromBytesLE(bytes, 5));
    }
}
