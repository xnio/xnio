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

package org.xnio;

import java.io.DataInput;
import java.io.DataOutput;
import java.nio.Buffer;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;
import java.nio.BufferOverflowException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.util.Arrays;
import java.io.IOException;

/**
 * Buffer utility methods.
 *
 * @apiviz.exclude
 */
public final class Buffers {
    private Buffers() {}

    /**
     * Flip a buffer.
     *
     * @see Buffer#flip()
     * @param <T> the buffer type
     * @param buffer the buffer to flip
     * @return the buffer instance
     */
    public static <T extends Buffer> T flip(T buffer) {
        buffer.flip();
        return buffer;
    }

    /**
     * Clear a buffer.
     *
     * @see Buffer#clear()
     * @param <T> the buffer type
     * @param buffer the buffer to clear
     * @return the buffer instance
     */
    public static <T extends Buffer> T clear(T buffer) {
        buffer.clear();
        return buffer;
    }

    /**
     * Set the buffer limit.
     *
     * @see Buffer#limit(int)
     * @param <T> the buffer type
     * @param buffer the buffer to set
     * @param limit the new limit
     * @return the buffer instance
     */
    public static <T extends Buffer> T limit(T buffer, int limit) {
        buffer.limit(limit);
        return buffer;
    }

    /**
     * Set the buffer mark.
     *
     * @see Buffer#mark()
     * @param <T> the buffer type
     * @param buffer the buffer to mark
     * @return the buffer instance
     */
    public static <T extends Buffer> T mark(T buffer) {
        buffer.mark();
        return buffer;
    }

    /**
     * Set the buffer position.
     *
     * @see Buffer#position(int)
     * @param <T> the buffer type
     * @param buffer the buffer to set
     * @param position the new position
     * @return the buffer instance
     */
    public static <T extends Buffer> T position(T buffer, int position) {
        buffer.position(position);
        return buffer;
    }

    /**
     * Reset the buffer.
     *
     * @see Buffer#reset()
     * @param <T> the buffer type
     * @param buffer the buffer to reset
     * @return the buffer instance
     */
    public static <T extends Buffer> T reset(T buffer) {
        buffer.reset();
        return buffer;
    }

    /**
     * Rewind the buffer.
     *
     * @see Buffer#rewind()
     * @param <T> the buffer type
     * @param buffer the buffer to rewind
     * @return the buffer instance
     */
    public static <T extends Buffer> T rewind(T buffer) {
        buffer.rewind();
        return buffer;
    }

    /**
     * Slice the buffer.  The original buffer's position will be moved up past the slice that was taken.
     *
     * @see ByteBuffer#slice()
     * @param buffer the buffer to slice
     * @param sliceSize the size of the slice
     * @return the buffer slice
     */
    public static ByteBuffer slice(ByteBuffer buffer, int sliceSize) {
        if (sliceSize > buffer.remaining() || sliceSize < -buffer.remaining()) {
            throw new BufferUnderflowException();
        }
        final int oldPos = buffer.position();
        final int oldLim = buffer.limit();
        if (sliceSize < 0) {
            // count from end (sliceSize is NEGATIVE)
            buffer.limit(oldLim + sliceSize);
            try {
                return buffer.slice();
            } finally {
                buffer.limit(oldLim);
                buffer.position(oldLim + sliceSize);
            }
        } else {
            // count from start
            buffer.limit(oldPos + sliceSize);
            try {
                return buffer.slice();
            } finally {
                buffer.limit(oldLim);
                buffer.position(oldPos + sliceSize);
            }
        }
    }

    /**
     * Copy a portion of the buffer into a newly allocated buffer.  The original buffer's position will be moved up past the copy that was taken.
     *
     * @param buffer the buffer to slice
     * @param sliceSize the size of the copy
     * @param allocator the buffer allocator to use
     * @return the buffer slice
     */
    public static ByteBuffer copy(ByteBuffer buffer, int sliceSize, BufferAllocator<ByteBuffer> allocator) {
        if (sliceSize > buffer.remaining() || sliceSize < -buffer.remaining()) {
            throw new BufferUnderflowException();
        }
        final int oldPos = buffer.position();
        final int oldLim = buffer.limit();
        if (sliceSize < 0) {
            // count from end (sliceSize is NEGATIVE)
            final ByteBuffer target = allocator.allocate(-sliceSize);
            buffer.limit(oldLim + sliceSize);
            try {
                target.put(buffer);
                return target;
            } finally {
                buffer.limit(oldLim);
                buffer.position(oldLim + sliceSize);
            }
        } else {
            // count from start
            final ByteBuffer target = allocator.allocate(sliceSize);
            buffer.limit(oldPos + sliceSize);
            try {
                target.put(buffer);
                return target;
            } finally {
                buffer.limit(oldLim);
                buffer.position(oldPos + sliceSize);
            }
        }
    }

    /**
     * Copy as many bytes as possible from {@code source} into {@code destination}.
     *
     * @param destination the destination buffer
     * @param source the source buffer
     * @return the number of bytes put into the destination buffer
     */
    public static int copy(final ByteBuffer destination, final ByteBuffer source) {
        final int sr = source.remaining();
        final int dr = destination.remaining();
        if (dr > sr) {
            destination.put(source);
            return sr;
        } else {
            destination.put(slice(source, dr));
            return dr;
        }
    }

    /**
     * Copy as many bytes as possible from {@code sources} into {@code destinations} in a "scatter" fashion.
     *
     * @param destinations the destination buffers
     * @param offset the offset into the destination buffers array
     * @param length the number of buffers to update
     * @param source the source buffer
     * @return the number of bytes put into the destination buffers
     */
    public static long copy(final ByteBuffer[] destinations, final int offset, final int length, final ByteBuffer source) {
        long t = 0L;
        for (int i = 0; i < length; i ++) {
            final ByteBuffer buffer = destinations[i + offset];
            final int rem = buffer.remaining();
            if (rem == 0) {
                continue;
            } else if (rem < source.remaining()) {
                buffer.put(slice(source, rem));
                t += rem;
            } else {
                buffer.put(source);
                t += rem;
                return t;
            }
        }
        return t;
    }

    /**
     * Copy as many bytes as possible from {@code sources} into {@code destination} in a "gather" fashion.
     *
     * @param destination the destination buffer
     * @param sources the source buffers
     * @param offset the offset into the source buffers array
     * @param length the number of buffers to read from
     * @return the number of bytes put into the destination buffers
     */
    public static long copy(final ByteBuffer destination, final ByteBuffer[] sources, final int offset, final int length) {
        long t = 0L;
        for (int i = 0; i < length; i ++) {
            final ByteBuffer buffer = sources[i + offset];
            final int rem = buffer.remaining();
            if (rem == 0) {
                continue;
            } else if (rem > destination.remaining()) {
                destination.put(slice(buffer, rem));
                t += rem;
                return t;
            } else {
                destination.put(buffer);
                t += rem;
            }
        }
        return t;
    }

    /**
     * Copy as many bytes as possible from {@code sources} into {@code destinations} by a combined "scatter"/"gather" operation.
     *
     * @param destinations the destination buffers
     * @param destOffset the offset into the destination buffers array
     * @param destLength the number of buffers to write to
     * @param sources the source buffers
     * @param srcOffset the offset into the source buffers array
     * @param srcLength the number of buffers to read from
     * @return the number of bytes put into the destination buffers
     */
    public static long copy(final ByteBuffer[] destinations, final int destOffset, final int destLength, final ByteBuffer[] sources, final int srcOffset, final int srcLength) {
        long t = 0L;
        int s = 0, d = 0;
        if (destLength == 0 || srcLength == 0) {
            return 0L;
        }
        ByteBuffer source = sources[srcOffset];
        ByteBuffer dest = destinations[destOffset];
        while (s < srcLength && d < destLength) {
            final int sr = source.remaining();
            final int dr = dest.remaining();
            if (sr < dr) {
                dest.put(source);
                source = sources[srcOffset + ++s];
                t += sr;
            } else if (sr > dr) {
                dest.put(slice(source, dr));
                dest = destinations[destOffset + ++d];
                t += dr;
            } else {
                dest.put(source);
                source = sources[srcOffset + ++s];
                dest = destinations[destOffset + ++d];
                t += sr;
            }
        }
        return t;
    }

    /**
     * Fill a buffer with a repeated value.
     *
     * @param buffer the buffer to fill
     * @param value the value to fill
     * @param count the number of bytes to fill
     * @return the buffer instance
     */
    public static ByteBuffer fill(ByteBuffer buffer, int value, int count) {
        if (count > buffer.remaining()) {
            throw new BufferUnderflowException();
        }
        if (buffer.hasArray()) {
            final int offs = buffer.arrayOffset();
            Arrays.fill(buffer.array(), offs + buffer.position(), offs + buffer.limit(), (byte) value);
            skip(buffer, count);
        } else {
            for (int i = count; i > 0; i--) {
                buffer.put((byte)value);
            }
        }
        return buffer;
    }

    /**
     * Slice the buffer.  The original buffer's position will be moved up past the slice that was taken.
     *
     * @see CharBuffer#slice()
     * @param buffer the buffer to slice
     * @param sliceSize the size of the slice
     * @return the buffer slice
     */
    public static CharBuffer slice(CharBuffer buffer, int sliceSize) {
        if (sliceSize > buffer.remaining() || sliceSize < -buffer.remaining()) {
            throw new BufferUnderflowException();
        }
        final int oldPos = buffer.position();
        final int oldLim = buffer.limit();
        if (sliceSize < 0) {
            // count from end (sliceSize is NEGATIVE)
            buffer.limit(oldLim + sliceSize);
            try {
                return buffer.slice();
            } finally {
                buffer.limit(oldLim);
                buffer.position(oldLim + sliceSize);
            }
        } else {
            // count from start
            buffer.limit(oldPos + sliceSize);
            try {
                return buffer.slice();
            } finally {
                buffer.limit(oldLim);
                buffer.position(oldPos + sliceSize);
            }
        }
    }

    /**
     * Fill a buffer with a repeated value.
     *
     * @param buffer the buffer to fill
     * @param value the value to fill
     * @param count the number of chars to fill
     * @return the buffer instance
     */
    public static CharBuffer fill(CharBuffer buffer, int value, int count) {
        if (count > buffer.remaining()) {
            throw new BufferUnderflowException();
        }
        if (buffer.hasArray()) {
            final int offs = buffer.arrayOffset();
            Arrays.fill(buffer.array(), offs + buffer.position(), offs + buffer.limit(), (char) value);
            skip(buffer, count);
        } else {
            for (int i = count; i > 0; i--) {
                buffer.put((char)value);
            }
        }
        return buffer;
    }

    /**
     * Slice the buffer.  The original buffer's position will be moved up past the slice that was taken.
     *
     * @see ShortBuffer#slice()
     * @param buffer the buffer to slice
     * @param sliceSize the size of the slice
     * @return the buffer slice
     */
    public static ShortBuffer slice(ShortBuffer buffer, int sliceSize) {
        if (sliceSize > buffer.remaining() || sliceSize < -buffer.remaining()) {
            throw new BufferUnderflowException();
        }
        final int oldPos = buffer.position();
        final int oldLim = buffer.limit();
        if (sliceSize < 0) {
            // count from end (sliceSize is NEGATIVE)
            buffer.limit(oldLim + sliceSize);
            try {
                return buffer.slice();
            } finally {
                buffer.limit(oldLim);
                buffer.position(oldLim + sliceSize);
            }
        } else {
            // count from start
            buffer.limit(oldPos + sliceSize);
            try {
                return buffer.slice();
            } finally {
                buffer.limit(oldLim);
                buffer.position(oldPos + sliceSize);
            }
        }
    }

    /**
     * Fill a buffer with a repeated value.
     *
     * @param buffer the buffer to fill
     * @param value the value to fill
     * @param count the number of shorts to fill
     * @return the buffer instance
     */
    public static ShortBuffer fill(ShortBuffer buffer, int value, int count) {
        if (count > buffer.remaining()) {
            throw new BufferUnderflowException();
        }
        if (buffer.hasArray()) {
            final int offs = buffer.arrayOffset();
            Arrays.fill(buffer.array(), offs + buffer.position(), offs + buffer.limit(), (short) value);
            skip(buffer, count);
        } else {
            for (int i = count; i > 0; i--) {
                buffer.put((short)value);
            }
        }
        return buffer;
    }

    /**
     * Slice the buffer.  The original buffer's position will be moved up past the slice that was taken.
     *
     * @see IntBuffer#slice()
     * @param buffer the buffer to slice
     * @param sliceSize the size of the slice
     * @return the buffer slice
     */
    public static IntBuffer slice(IntBuffer buffer, int sliceSize) {
        if (sliceSize > buffer.remaining() || sliceSize < -buffer.remaining()) {
            throw new BufferUnderflowException();
        }
        final int oldPos = buffer.position();
        final int oldLim = buffer.limit();
        if (sliceSize < 0) {
            // count from end (sliceSize is NEGATIVE)
            buffer.limit(oldLim + sliceSize);
            try {
                return buffer.slice();
            } finally {
                buffer.limit(oldLim);
                buffer.position(oldLim + sliceSize);
            }
        } else {
            // count from start
            buffer.limit(oldPos + sliceSize);
            try {
                return buffer.slice();
            } finally {
                buffer.limit(oldLim);
                buffer.position(oldPos + sliceSize);
            }
        }
    }

    /**
     * Fill a buffer with a repeated value.
     *
     * @param buffer the buffer to fill
     * @param value the value to fill
     * @param count the number of ints to fill
     * @return the buffer instance
     */
    public static IntBuffer fill(IntBuffer buffer, int value, int count) {
        if (count > buffer.remaining()) {
            throw new BufferUnderflowException();
        }
        if (buffer.hasArray()) {
            final int offs = buffer.arrayOffset();
            Arrays.fill(buffer.array(), offs + buffer.position(), offs + buffer.limit(), value);
            skip(buffer, count);
        } else {
            for (int i = count; i > 0; i--) {
                buffer.put(value);
            }
        }
        return buffer;
    }

    /**
     * Slice the buffer.  The original buffer's position will be moved up past the slice that was taken.
     *
     * @see LongBuffer#slice()
     * @param buffer the buffer to slice
     * @param sliceSize the size of the slice
     * @return the buffer slice
     */
    public static LongBuffer slice(LongBuffer buffer, int sliceSize) {
        if (sliceSize > buffer.remaining() || sliceSize < -buffer.remaining()) {
            throw new BufferUnderflowException();
        }
        final int oldPos = buffer.position();
        final int oldLim = buffer.limit();
        if (sliceSize < 0) {
            // count from end (sliceSize is NEGATIVE)
            buffer.limit(oldLim + sliceSize);
            try {
                return buffer.slice();
            } finally {
                buffer.limit(oldLim);
                buffer.position(oldLim + sliceSize);
            }
        } else {
            // count from start
            buffer.limit(oldPos + sliceSize);
            try {
                return buffer.slice();
            } finally {
                buffer.limit(oldLim);
                buffer.position(oldPos + sliceSize);
            }
        }
    }

    /**
     * Fill a buffer with a repeated value.
     *
     * @param buffer the buffer to fill
     * @param value the value to fill
     * @param count the number of longs to fill
     * @return the buffer instance
     */
    public static LongBuffer fill(LongBuffer buffer, long value, int count) {
        if (count > buffer.remaining()) {
            throw new BufferUnderflowException();
        }
        if (buffer.hasArray()) {
            final int offs = buffer.arrayOffset();
            Arrays.fill(buffer.array(), offs + buffer.position(), offs + buffer.limit(), value);
            skip(buffer, count);
        } else {
            for (int i = count; i > 0; i--) {
                buffer.put(value);
            }
        }
        return buffer;
    }

    /**
     * Advance a buffer's position relative to its current position.
     *
     * @see Buffer#position(int)
     * @param <T> the buffer type
     * @param buffer the buffer to set
     * @param cnt the distance to skip
     * @return the buffer instance
     */
    public static <T extends Buffer> T skip(T buffer, int cnt) {
        if (cnt < 0) {
            throw new IllegalArgumentException();
        }
        if (cnt > buffer.remaining()) {
            throw new BufferUnderflowException();
        }
        buffer.position(buffer.position() + cnt);
        return buffer;
    }

    /**
     * Rewind a buffer's position relative to its current position.
     *
     * @see Buffer#position(int)
     * @param <T> the buffer type
     * @param buffer the buffer to set
     * @param cnt the distance to skip backwards
     * @return the buffer instance
     */
    public static <T extends Buffer> T unget(T buffer, int cnt) {
        if (cnt < 0) {
            throw new IllegalArgumentException();
        }
        if (cnt > buffer.position()) {
            throw new BufferUnderflowException();
        }
        buffer.position(buffer.position() - cnt);
        return buffer;
    }

    /**
     * Take a certain number of bytes from the buffer and return them in an array.
     *
     * @param buffer the buffer to read
     * @param cnt the number of bytes to take
     * @return the bytes
     */
    public static byte[] take(ByteBuffer buffer, int cnt) {
        final byte[] bytes = new byte[cnt];
        buffer.get(bytes);
        return bytes;
    }

    /**
     * Take a certain number of chars from the buffer and return them in an array.
     *
     * @param buffer the buffer to read
     * @param cnt the number of chars to take
     * @return the chars
     */
    public static char[] take(CharBuffer buffer, int cnt) {
        final char[] chars = new char[cnt];
        buffer.get(chars);
        return chars;
    }

    /**
     * Take a certain number of shorts from the buffer and return them in an array.
     *
     * @param buffer the buffer to read
     * @param cnt the number of shorts to take
     * @return the shorts
     */
    public static short[] take(ShortBuffer buffer, int cnt) {
        final short[] shorts = new short[cnt];
        buffer.get(shorts);
        return shorts;
    }

    /**
     * Take a certain number of ints from the buffer and return them in an array.
     *
     * @param buffer the buffer to read
     * @param cnt the number of ints to take
     * @return the ints
     */
    public static int[] take(IntBuffer buffer, int cnt) {
        final int[] ints = new int[cnt];
        buffer.get(ints);
        return ints;
    }

    /**
     * Take a certain number of longs from the buffer and return them in an array.
     *
     * @param buffer the buffer to read
     * @param cnt the number of longs to take
     * @return the longs
     */
    public static long[] take(LongBuffer buffer, int cnt) {
        final long[] longs = new long[cnt];
        buffer.get(longs);
        return longs;
    }

    /**
     * Take all of the remaining bytes from the buffer and return them in an array.
     *
     * @param buffer the buffer to read
     * @return the bytes
     */
    public static byte[] take(ByteBuffer buffer) {
        final byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }

    /**
     * Take all of the remaining chars from the buffer and return them in an array.
     *
     * @param buffer the buffer to read
     * @return the chars
     */
    public static char[] take(CharBuffer buffer) {
        final char[] chars = new char[buffer.remaining()];
        buffer.get(chars);
        return chars;
    }

    /**
     * Take all of the remaining shorts from the buffer and return them in an array.
     *
     * @param buffer the buffer to read
     * @return the shorts
     */
    public static short[] take(ShortBuffer buffer) {
        final short[] shorts = new short[buffer.remaining()];
        buffer.get(shorts);
        return shorts;
    }

    /**
     * Take all of the remaining ints from the buffer and return them in an array.
     *
     * @param buffer the buffer to read
     * @return the ints
     */
    public static int[] take(IntBuffer buffer) {
        final int[] ints = new int[buffer.remaining()];
        buffer.get(ints);
        return ints;
    }

    /**
     * Take all of the remaining longs from the buffer and return them in an array.
     *
     * @param buffer the buffer to read
     * @return the longs
     */
    public static long[] take(LongBuffer buffer) {
        final long[] longs = new long[buffer.remaining()];
        buffer.get(longs);
        return longs;
    }

    /**
     * Create an object that returns the dumped form of the given byte buffer when its {@code toString()} method is called.
     * Useful for logging byte buffers; if the {@code toString()} method is never called, the process of dumping the
     * buffer is never performed.
     *
     * @param buffer the buffer
     * @param indent the indentation to use
     * @param columns the number of 8-byte columns
     * @return a stringable object
     */
    public static Object createDumper(final ByteBuffer buffer, final int indent, final int columns) {
        return new Object() {
            public String toString() {
                StringBuilder b = new StringBuilder();
                try {
                    dump(buffer, b, indent, columns);
                } catch (IOException e) {
                    // ignore, not possible!
                }
                return b.toString();
            }
        };
    }

    /**
     * Dump a byte buffer to the given target.
     *
     * @param buffer the buffer
     * @param dest the target
     * @param indent the indentation to use
     * @param columns the number of 8-byte columns
     * @throws IOException if an error occurs during append
     */
    public static void dump(final ByteBuffer buffer, final Appendable dest, final int indent, final int columns) throws IOException {
        final int pos = buffer.position();
        final int remaining = buffer.remaining();
        final int rowLength = (8 << (columns - 1));
        final int n = Math.max(Integer.toString(buffer.remaining(), 16).length(), 4);
        for (int idx = 0; idx < remaining; idx += rowLength) {
            // state: start of line
            for (int i = 0; i < indent; i ++) {
                dest.append(' ');
            }
            final String s = Integer.toString(idx, 16);
            for (int i = n - s.length(); i > 0; i --) {
                dest.append('0');
            }
            dest.append(s);
            dest.append(" - ");
            appendHexRow(buffer, dest, pos + idx, columns);
            appendTextRow(buffer, dest, pos + idx, columns);
            dest.append('\n');
        }
    }

    private static void appendHexRow(final ByteBuffer buffer, final Appendable dest, final int startPos, final int columns) throws IOException {
        final int limit = buffer.limit();
        int pos = startPos;
        for (int c = 0; c < columns; c ++) {
            for (int i = 0; i < 8; i ++) {
                if (pos >= limit) {
                    dest.append("  ");
                } else {
                    final int v = buffer.get(pos++) & 0xff;
                    final String hexVal = Integer.toString(v, 16);
                    if (v < 16) {
                        dest.append('0');
                    }
                    dest.append(hexVal);
                }
                dest.append(' ');
            }
            dest.append(' ');
            dest.append(' ');
        }
    }

    private static void appendTextRow(final ByteBuffer buffer, final Appendable dest, final int startPos, final int columns) throws IOException {
        final int limit = buffer.limit();
        int pos = startPos;
        dest.append('[');
        dest.append(' ');
        for (int c = 0; c < columns; c ++) {
            for (int i = 0; i < 8; i ++) {
                if (pos >= limit) {
                    dest.append(' ');
                } else {
                    final char v = (char) (buffer.get(pos++) & 0xff);
                    if (Character.isISOControl(v)) {
                        dest.append('.');
                    } else {
                        dest.append(v);
                    }
                }
            }
            dest.append(' ');
        }
        dest.append(']');
    }

    /**
     * Create an object that returns the dumped form of the given character buffer when its {@code toString()} method is called.
     * Useful for logging character buffers; if the {@code toString()} method is never called, the process of dumping the
     * buffer is never performed.
     *
     * @param buffer the buffer
     * @param indent the indentation to use
     * @param columns the number of 8-byte columns
     * @return a stringable object
     */
    public static Object createDumper(final CharBuffer buffer, final int indent, final int columns) {
        return new Object() {
            public String toString() {
                StringBuilder b = new StringBuilder();
                try {
                    dump(buffer, b, indent, columns);
                } catch (IOException e) {
                    // ignore, not possible!
                }
                return b.toString();
            }
        };
    }

    /**
     * Dump a character buffer to the given target.
     *
     * @param buffer the buffer
     * @param dest the target
     * @param indent the indentation to use
     * @param columns the number of 8-byte columns
     * @throws IOException if an error occurs during append
     */
    public static void dump(final CharBuffer buffer, final Appendable dest, final int indent, final int columns) throws IOException {
        final int pos = buffer.position();
        final int remaining = buffer.remaining();
        final int rowLength = (8 << (columns - 1));
        final int n = Math.max(Integer.toString(buffer.remaining(), 16).length(), 4);
        for (int idx = 0; idx < remaining; idx += rowLength) {
            // state: start of line
            for (int i = 0; i < indent; i ++) {
                dest.append(' ');
            }
            final String s = Integer.toString(idx, 16);
            for (int i = n - s.length(); i > 0; i --) {
                dest.append('0');
            }
            dest.append(s);
            dest.append(" - ");
            appendHexRow(buffer, dest, pos + idx, columns);
            appendTextRow(buffer, dest, pos + idx, columns);
            dest.append('\n');
        }
    }

    private static void appendHexRow(final CharBuffer buffer, final Appendable dest, final int startPos, final int columns) throws IOException {
        final int limit = buffer.limit();
        int pos = startPos;
        for (int c = 0; c < columns; c ++) {
            for (int i = 0; i < 8; i ++) {
                if (pos >= limit) {
                    dest.append("  ");
                } else {
                    final char v = buffer.get(pos++);
                    final String hexVal = Integer.toString(v, 16);
                    dest.append("0000".substring(hexVal.length()));
                    dest.append(hexVal);
                }
                dest.append(' ');
            }
            dest.append(' ');
            dest.append(' ');
        }
    }

    private static void appendTextRow(final CharBuffer buffer, final Appendable dest, final int startPos, final int columns) throws IOException {
        final int limit = buffer.limit();
        int pos = startPos;
        dest.append('[');
        dest.append(' ');
        for (int c = 0; c < columns; c ++) {
            for (int i = 0; i < 8; i ++) {
                if (pos >= limit) {
                    dest.append(' ');
                } else {
                    final char v = buffer.get(pos++);
                    if (Character.isISOControl(v) || Character.isHighSurrogate(v) || Character.isLowSurrogate(v)) {
                        dest.append('.');
                    } else {
                        dest.append(v);
                    }
                }
            }
            dest.append(' ');
        }
        dest.append(']');
    }

    /**
     * Create a heap-based buffer allocator.
     *
     * @param size the size of the returned buffers
     * @return the buffer allocator
     * @since 1.1
     */
    public static Pool<ByteBuffer> createHeapByteBufferAllocator(final int size) {
        return new Pool<ByteBuffer>() {
            public Pooled<ByteBuffer> allocate() {
                final ByteBuffer buffer = ByteBuffer.allocate(size);
                return new Pooled<ByteBuffer>() {
                    public void discard() {
                    }

                    public void free() {
                    }

                    public ByteBuffer getResource() {
                        return buffer;
                    }
                };
            }
        };
    }

    /**
     * The empty byte buffer.
     */
    public static final ByteBuffer EMPTY_BYTE_BUFFER = ByteBuffer.allocate(0);

    /**
     * Determine whether any of the buffers has remaining data.
     *
     * @param buffers the buffers
     * @param offs the offset into the buffers array
     * @param len the number of buffers to check
     * @return {@code true} if any of the selected buffers has remaining data
     */
    public static boolean hasRemaining(final Buffer[] buffers, final int offs, final int len) {
        for (int i = 0; i < len; i ++) {
            if (buffers[i + offs].hasRemaining()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determine whether any of the buffers has remaining data.
     *
     * @param buffers the buffers
     * @return {@code true} if any of the selected buffers has remaining data
     */
    public static boolean hasRemaining(final Buffer[] buffers) {
        return hasRemaining(buffers, 0, buffers.length);
    }

    /**
     * Get the total remaining size of all the given buffers.
     *
     * @param buffers the buffers
     * @param offs the offset into the buffers array
     * @param len the number of buffers to check
     * @return the number of remaining elements
     */
    public static long remaining(final Buffer[] buffers, final int offs, final int len) {
        long t = 0L;
        for (int i = 0; i < len; i ++) {
            t += buffers[i + offs].remaining();
        }
        return t;
    }

    /**
     * Get the total remaining size of all the given buffers.
     *
     * @param buffers the buffers
     * @return the number of remaining elements
     */
    public static long remaining(final Buffer[] buffers) {
        return remaining(buffers, 0, buffers.length);
    }

    /**
     * Put the string into the byte buffer, encoding it using "modified UTF-8" encoding.
     *
     * @param dest the byte buffer
     * @param orig the source bytes
     * @return the byte buffer
     * @throws BufferOverflowException if there is not enough space in the buffer for the complete string
     * @see DataOutput#writeUTF(String)
     */
    public static ByteBuffer putModifiedUtf8(ByteBuffer dest, String orig) throws BufferOverflowException {
        final char[] chars = orig.toCharArray();
        for (char c : chars) {
            if (c > 0 && c <= 0x7f) {
                dest.put((byte) c);
            } else if (c <= 0x07ff) {
                dest.put((byte)(0xc0 | 0x1f & c >> 6));
                dest.put((byte)(0x80 | 0x3f & c));
            } else {
                dest.put((byte)(0xe0 | 0x0f & c >> 12));
                dest.put((byte)(0x80 | 0x3f & c >> 6));
                dest.put((byte)(0x80 | 0x3f & c));
            }
        }
        return dest;
    }

    /**
     * Get a 0-terminated string from the byte buffer, decoding it using "modified UTF-8" encoding.
     *
     * @param src the source buffer
     * @return the string
     * @throws BufferUnderflowException if the end of the buffer was reached before encountering a {@code 0}
     */
    public static String getModifiedUtf8Z(ByteBuffer src) throws BufferUnderflowException {
        final StringBuilder builder = new StringBuilder();
        for (;;) {
            final int ch = readUTFChar(src);
            if (ch == -1) {
                return builder.toString();
            }
            builder.append((char) ch);
        }
    }

    /**
     * Get a modified UTF-8 string from the remainder of the buffer.
     *
     * @param src the buffer
     * @return the modified UTF-8 string
     * @throws BufferUnderflowException if the buffer ends abruptly in the midst of a single character
     */
    public static String getModifiedUtf8(ByteBuffer src) throws BufferUnderflowException {
        final StringBuilder builder = new StringBuilder();
        while (src.hasRemaining()) {
            final int ch = readUTFChar(src);
            if (ch == -1) {
                builder.append('\0');
            } else {
                builder.append((char) ch);
            }
        }
        return builder.toString();
    }

    private static int readUTFChar(final ByteBuffer src) throws BufferUnderflowException {
        final int a = src.get() & 0xff;
        if (a == 0) {
            return -1;
        } else if (a < 0x80) {
            return (char)a;
        } else if (a < 0xc0) {
            return '?';
        } else if (a < 0xe0) {
            final int b = src.get() & 0xff;
            if ((b & 0xc0) != 0x80) {
                return '?';
            }
            return (a & 0x1f) << 6 | b & 0x3f;
        } else if (a < 0xf0) {
            final int b = src.get() & 0xff;
            if ((b & 0xc0) != 0x80) {
                return '?';
            }
            final int c = src.get() & 0xff;
            if ((c & 0xc0) != 0x80) {
                return '?';
            }
            return (a & 0x0f) << 12 | (b & 0x3f) << 6 | c & 0x3f;
        }
        return '?';
    }

    /**
     * Read an ASCIIZ ({@code NUL}-terminated) string from a byte buffer, appending the results to the given string
     * builder.  If no {@code NUL} character is encountered, {@code false} is returned, indicating that more data needs
     * to be acquired before the operation can be complete.  On return, there may be data remaining
     * in the source buffer.  If an invalid byte is read, the character {@code '?'} is written
     * to the string builder in its place.
     *
     * @param src the source buffer
     * @param builder the destination builder
     * @return {@code true} if the entire string was read, {@code false} if more data is needed
     */
    public static boolean readAsciiZ(final ByteBuffer src, final StringBuilder builder) {
        return readAsciiZ(src, builder, '?');
    }

    /**
     * Read an ASCIIZ ({@code NUL}-terminated) string from a byte buffer, appending the results to the given string
     * builder.  If no {@code NUL} character is encountered, {@code false} is returned, indicating that more data needs
     * to be acquired before the operation can be complete.  On return, there may be data remaining
     * in the source buffer.  If an invalid byte is read, the character designated by {@code replacement} is written
     * to the string builder in its place.
     *
     * @param src the source buffer
     * @param builder the destination builder
     * @param replacement the replacement character for invalid bytes
     * @return {@code true} if the entire string was read, {@code false} if more data is needed
     */
    public static boolean readAsciiZ(final ByteBuffer src, final StringBuilder builder, final char replacement) {
        for (;;) {
            if (! src.hasRemaining()) {
                return false;
            }
            final byte b = src.get();
            if (b == 0) {
                return true;
            }
            builder.append(b < 0 ? replacement : (char) b);
        }
    }

    /**
     * Read a single line of ASCII text from a byte buffer, appending the results to the given string
     * builder.  If no {@code EOL} character is encountered, {@code false} is returned, indicating that more data needs
     * to be acquired before the operation can be complete.  On return, there may be data remaining
     * in the source buffer.  If an invalid byte is read, the character {@code '?'} is written
     * to the string builder in its place.  The {@code EOL} character will be included in the resultant string.
     *
     * @param src the source buffer
     * @param builder the destination builder
     * @return {@code true} if the entire string was read, {@code false} if more data is needed
     */
    public static boolean readAsciiLine(final ByteBuffer src, final StringBuilder builder) {
        return readAsciiLine(src, builder, '?', '\n');
    }

    /**
     * Read a single line of ASCII text from a byte buffer, appending the results to the given string
     * builder.  If no {@code EOL} character is encountered, {@code false} is returned, indicating that more data needs
     * to be acquired before the operation can be complete.  On return, there may be data remaining
     * in the source buffer.  If an invalid byte is read, the character designated by {@code replacement} is written
     * to the string builder in its place.  The {@code EOL} character will be included in the resultant string.
     *
     * @param src the source buffer
     * @param builder the destination builder
     * @param replacement the replacement character for invalid bytes
     * @return {@code true} if the entire string was read, {@code false} if more data is needed
     */
    public static boolean readAsciiLine(final ByteBuffer src, final StringBuilder builder, final char replacement) {
        return readAsciiLine(src, builder, replacement, '\n');
    }

    /**
     * Read a single line of ASCII text from a byte buffer, appending the results to the given string
     * builder, using the given delimiter character instead of {@code EOL}.  If no delimiter character is encountered,
     * {@code false} is returned, indicating that more data needs
     * to be acquired before the operation can be complete.  On return, there may be data remaining
     * in the source buffer.  If an invalid byte is read, the character designated by {@code replacement} is written
     * to the string builder in its place.  The delimiter character will be included in the resultant string.
     *
     * @param src the source buffer
     * @param builder the destination builder
     * @param replacement the replacement character for invalid bytes
     * @param delimiter the character which marks the end of the line
     * @return {@code true} if the entire string was read, {@code false} if more data is needed
     */
    public static boolean readAsciiLine(final ByteBuffer src, final StringBuilder builder, final char replacement, final char delimiter) {
        for (;;) {
            if (! src.hasRemaining()) {
                return false;
            }
            final byte b = src.get();
            builder.append(b < 0 ? replacement : (char) b);
            if (b == delimiter) {
                return true;
            }
        }
    }

    /**
     * Read the remainder of a buffer as ASCII text, appending the results to the given string
     * builder.  If an invalid byte is read, the character {@code '?'} is written
     * to the string builder in its place.
     *
     * @param src the source buffer
     * @param builder the destination builder
     */
    public static void readAscii(final ByteBuffer src, final StringBuilder builder) {
        readAscii(src, builder, '?');
    }

    /**
     * Read the remainder of a buffer as ASCII text, appending the results to the given string
     * builder.  If an invalid byte is read, the character designated by {@code replacement} is written
     * to the string builder in its place.
     *
     * @param src the source buffer
     * @param builder the destination builder
     * @param replacement the replacement character for invalid bytes
     */
    public static void readAscii(final ByteBuffer src, final StringBuilder builder, final char replacement) {
        for (;;) {
            if (! src.hasRemaining()) {
                return;
            }
            final byte b = src.get();
            builder.append(b < 0 ? replacement : (char) b);
        }
    }

    /**
     * Read the remainder of a buffer as ASCII text, up to a certain limit, appending the results to the given string
     * builder.  If an invalid byte is read, the character designated by {@code replacement} is written
     * to the string builder in its place.
     *
     * @param src the source buffer
     * @param builder the destination builder
     * @param limit the maximum number of characters to write
     * @param replacement the replacement character for invalid bytes
     */
    public static void readAscii(final ByteBuffer src, final StringBuilder builder, int limit, final char replacement) {
        while (limit > 0) {
            if (! src.hasRemaining()) {
                return;
            }
            final byte b = src.get();
            builder.append(b < 0 ? replacement : (char) b);
            limit--;
        }
    }

    /**
     * Read a {@code NUL}-terminated Latin-1 string from a byte buffer, appending the results to the given string
     * builder.  If no {@code NUL} character is encountered, {@code false} is returned, indicating that more data needs
     * to be acquired before the operation can be complete.  On return, there may be data remaining
     * in the source buffer.
     *
     * @param src the source buffer
     * @param builder the destination builder
     * @return {@code true} if the entire string was read, {@code false} if more data is needed
     */
    public static boolean readLatin1Z(final ByteBuffer src, final StringBuilder builder) {
        for (;;) {
            if (! src.hasRemaining()) {
                return false;
            }
            final byte b = src.get();
            if (b == 0) {
                return true;
            }
            builder.append((char) (b & 0xff));
        }
    }

    /**
     * Read a single line of Latin-1 text from a byte buffer, appending the results to the given string
     * builder.  If no {@code EOL} character is encountered, {@code false} is returned, indicating that more data needs
     * to be acquired before the operation can be complete.  On return, there may be data remaining
     * in the source buffer.  The {@code EOL} character will be included in the resultant string.
     *
     * @param src the source buffer
     * @param builder the destination builder
     * @return {@code true} if the entire string was read, {@code false} if more data is needed
     */
    public static boolean readLatin1Line(final ByteBuffer src, final StringBuilder builder) {
        for (;;) {
            if (! src.hasRemaining()) {
                return false;
            }
            final byte b = src.get();
            builder.append((char) (b & 0xff));
            if (b == '\n') {
                return true;
            }
        }
    }

    /**
     * Read a single line of Latin-1 text from a byte buffer, appending the results to the given string
     * builder.  If no delimiter character is encountered, {@code false} is returned, indicating that more data needs
     * to be acquired before the operation can be complete.  On return, there may be data remaining
     * in the source buffer.  The delimiter character will be included in the resultant string.
     *
     * @param src the source buffer
     * @param builder the destination builder
     * @param delimiter the character which marks the end of the line
     * @return {@code true} if the entire string was read, {@code false} if more data is needed
     */
    public static boolean readLatin1Line(final ByteBuffer src, final StringBuilder builder, final char delimiter) {
        for (;;) {
            if (! src.hasRemaining()) {
                return false;
            }
            final byte b = src.get();
            builder.append((char) (b & 0xff));
            if (b == delimiter) {
                return true;
            }
        }
    }

    /**
     * Read the remainder of a buffer as ASCII text, appending the results to the given string
     * builder.
     *
     * @param src the source buffer
     * @param builder the destination builder
     */
    public static void readLatin1(final ByteBuffer src, final StringBuilder builder) {
        for (;;) {
            if (! src.hasRemaining()) {
                return;
            }
            final byte b = src.get();
            builder.append((char) (b & 0xff));
        }
    }

    /**
     * Read a {@code NUL}-terminated {@link DataInput modified UTF-8} string from a byte buffer, appending the results to the given string
     * builder.  If no {@code NUL} byte is encountered, {@code false} is returned, indicating that more data needs
     * to be acquired before the operation can be complete.  On return, there may be data remaining
     * in the source buffer.  If an invalid byte sequence is read, the character {@code '?'} is written
     * to the string builder in its place.
     *
     * @param src the source buffer
     * @param builder the destination builder
     * @return {@code true} if the entire string was read, {@code false} if more data is needed
     */
    public static boolean readModifiedUtf8Z(final ByteBuffer src, final StringBuilder builder) {
        return readModifiedUtf8Z(src, builder, '?');
    }

    /**
     * Read a {@code NUL}-terminated {@link DataInput modified UTF-8} string from a byte buffer, appending the results to the given string
     * builder.  If no {@code NUL} byte is encountered, {@code false} is returned, indicating that more data needs
     * to be acquired before the operation can be complete.  On return, there may be data remaining
     * in the source buffer.  If an invalid byte sequence is read, the character designated by {@code replacement} is written
     * to the string builder in its place.
     *
     * @param src the source buffer
     * @param builder the destination builder
     * @param replacement the replacement character to use
     * @return {@code true} if the entire string was read, {@code false} if more data is needed
     */
    public static boolean readModifiedUtf8Z(final ByteBuffer src, final StringBuilder builder, final char replacement) {
        for (;;) {
            if (! src.hasRemaining()) {
                return false;
            }
            final int a = src.get() & 0xff;
            if (a == 0) {
                return true;
            } else if (a < 0x80) {
                builder.append((char)a);
            } else if (a < 0xc0) {
                builder.append(replacement);
            } else if (a < 0xe0) {
                if (src.hasRemaining()) {
                    final int b = src.get() & 0xff;
                    if ((b & 0xc0) != 0x80) {
                        builder.append(replacement);
                    } else {
                        builder.append((char) ((a & 0x1f) << 6 | b & 0x3f));
                    }
                } else {
                    unget(src, 1);
                    return false;
                }
            } else if (a < 0xf0) {
                if (src.hasRemaining()) {
                    final int b = src.get() & 0xff;
                    if ((b & 0xc0) != 0x80) {
                        builder.append(replacement);
                    } else {
                        if (src.hasRemaining()) {
                            final int c = src.get() & 0xff;
                            if ((c & 0xc0) != 0x80) {
                                builder.append(replacement);
                            } else {
                                builder.append((char) ((a & 0x0f) << 12 | (b & 0x3f) << 6 | c & 0x3f));
                            }
                        } else {
                            unget(src, 2);
                            return false;
                        }
                    }
                } else {
                    unget(src, 1);
                    return false;
                }
            }
        }
    }

    /**
     * Read a single line of {@link DataInput modified UTF-8} text from a byte buffer, appending the results to the given string
     * builder.  If no {@code EOL} character is encountered, {@code false} is returned, indicating that more data needs
     * to be acquired before the operation can be complete.  On return, there may be data remaining
     * in the source buffer.  If an invalid byte is read, the character {@code '?'} is written
     * to the string builder in its place.  The {@code EOL} character will be included in the resultant string.
     *
     * @param src the source buffer
     * @param builder the destination builder
     * @return {@code true} if the entire string was read, {@code false} if more data is needed
     */
    public static boolean readModifiedUtf8Line(final ByteBuffer src, final StringBuilder builder) {
        return readModifiedUtf8Line(src, builder, '?');
    }

    /**
     * Read a single line of {@link DataInput modified UTF-8} text from a byte buffer, appending the results to the given string
     * builder.  If no {@code EOL} character is encountered, {@code false} is returned, indicating that more data needs
     * to be acquired before the operation can be complete.  On return, there may be data remaining
     * in the source buffer.  If an invalid byte is read, the character designated by {@code replacement} is written
     * to the string builder in its place.  The {@code EOL} character will be included in the resultant string.
     *
     * @param src the source buffer
     * @param builder the destination builder
     * @param replacement the replacement character for invalid bytes
     * @return {@code true} if the entire string was read, {@code false} if more data is needed
     */
    public static boolean readModifiedUtf8Line(final ByteBuffer src, final StringBuilder builder, final char replacement) {
        return readModifiedUtf8Line(src, builder, replacement, '\n');
    }

    /**
     * Read a single line of {@link DataInput modified UTF-8} text from a byte buffer, appending the results to the given string
     * builder.  If no {@code EOL} character is encountered, {@code false} is returned, indicating that more data needs
     * to be acquired before the operation can be complete.  On return, there may be data remaining
     * in the source buffer.  If an invalid byte is read, the character designated by {@code replacement} is written
     * to the string builder in its place.  The delimiter character will be included in the resultant string.
     *
     * @param src the source buffer
     * @param builder the destination builder
     * @param replacement the replacement character for invalid bytes
     * @param delimiter the character which marks the end of the line
     * @return {@code true} if the entire string was read, {@code false} if more data is needed
     */
    public static boolean readModifiedUtf8Line(final ByteBuffer src, final StringBuilder builder, final char replacement, final char delimiter) {
        for (;;) {
            if (! src.hasRemaining()) {
                return false;
            }
            final int a = src.get() & 0xff;
            if (a < 0x80) {
                builder.append((char)a);
                if (a == delimiter) {
                    return true;
                }
            } else if (a < 0xc0) {
                builder.append(replacement);
            } else if (a < 0xe0) {
                if (src.hasRemaining()) {
                    final int b = src.get() & 0xff;
                    if ((b & 0xc0) != 0x80) {
                        builder.append(replacement);
                    } else {
                        final char ch = (char) ((a & 0x1f) << 6 | b & 0x3f);
                        builder.append(ch);
                        if (ch == delimiter) {
                            return true;
                        }
                    }
                } else {
                    unget(src, 1);
                    return false;
                }
            } else if (a < 0xf0) {
                if (src.hasRemaining()) {
                    final int b = src.get() & 0xff;
                    if ((b & 0xc0) != 0x80) {
                        builder.append(replacement);
                    } else {
                        if (src.hasRemaining()) {
                            final int c = src.get() & 0xff;
                            if ((c & 0xc0) != 0x80) {
                                builder.append(replacement);
                            } else {
                                final char ch = (char) ((a & 0x0f) << 12 | (b & 0x3f) << 6 | c & 0x3f);
                                builder.append(ch);
                                if (ch == delimiter) {
                                    return true;
                                }
                            }
                        } else {
                            unget(src, 2);
                            return false;
                        }
                    }
                } else {
                    unget(src, 1);
                    return false;
                }
            }
        }
    }

    /**
     * Read a single line of text from a byte buffer, appending the results to the given string
     * builder.  If no {@code EOL} character is encountered, {@code false} is returned, indicating that more data needs
     * to be acquired before the operation can be complete.  On return, there may be data remaining
     * in the source buffer.  Invalid bytes are handled according to the policy specified by the {@code decoder} instance.
     * Since this method decodes only one character at a time, it should not be expected to have the same performance
     * as the other optimized, character set-specific methods specified in this class.
     * The {@code EOL} character will be included in the resultant string.
     *
     * @param src the source buffer
     * @param builder the destination builder
     * @param decoder the decoder to use
     * @return {@code true} if the entire string was read, {@code false} if more data is needed
     */
    public static boolean readLine(final ByteBuffer src, final StringBuilder builder, final CharsetDecoder decoder) {
        return readLine(src, builder, decoder, '\n');
    }

    /**
     * Read a single line of text from a byte buffer, appending the results to the given string
     * builder.  If no delimiter character is encountered, {@code false} is returned, indicating that more data needs
     * to be acquired before the operation can be complete.  On return, there may be data remaining
     * in the source buffer.  Invalid bytes are handled according to the policy specified by the {@code decoder} instance.
     * Since this method decodes only one character at a time, it should not be expected to have the same performance
     * as the other optimized, character set-specific methods specified in this class.  The delimiter character will be
     * included in the resultant string.
     *
     * @param src the source buffer
     * @param builder the destination builder
     * @param decoder the decoder to use
     * @param delimiter the character which marks the end of the line
     * @return {@code true} if the entire string was read, {@code false} if more data is needed
     */
    public static boolean readLine(final ByteBuffer src, final StringBuilder builder, final CharsetDecoder decoder, final char delimiter) {
        final CharBuffer oneChar = CharBuffer.allocate(1);
        for (;;) {
            final CoderResult coderResult = decoder.decode(src, oneChar, false);
            if (coderResult.isUnderflow()) {
                return false;
            }
            if (oneChar.hasRemaining()) {
                throw new IllegalStateException();
            }
            final char ch = oneChar.get(0);
            builder.append(ch);
            if (ch == delimiter) {
                return true;
            }
            oneChar.clear();
        }
    }
}
