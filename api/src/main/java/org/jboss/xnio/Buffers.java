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

package org.jboss.xnio;

import java.nio.Buffer;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;
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
     * @see java.nio.Buffer#flip()
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
     * @see java.nio.Buffer#clear()
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
     * @see java.nio.Buffer#limit(int)
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
     * @see java.nio.Buffer#mark()
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
     * @see java.nio.Buffer#reset()
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
     * @see java.nio.Buffer#rewind()
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
     * @see java.nio.ByteBuffer#slice()
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
     * @see java.nio.CharBuffer#slice()
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
     * @see java.nio.ShortBuffer#slice()
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
     * @see java.nio.IntBuffer#slice()
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
     * @see java.nio.LongBuffer#slice()
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
     * @param cnt the distantce to skip
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
     * @param cnt the distantce to skip backwards
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
    public static BufferAllocator<ByteBuffer> createHeapByteBufferAllocator(final int size) {
        return new BufferAllocator<ByteBuffer>() {
            public ByteBuffer allocate() {
                return ByteBuffer.allocate(size);
            }

            public void free(final ByteBuffer buffer) {
            }

            public void discard(final ByteBuffer resource) {
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
     * Put as many bytes as possible from {@code src} into the byte buffers in a scatter fashion.
     *
     * @param dsts the destination buffers
     * @param doffs the offset into the destination buffers array
     * @param dlen the number of buffers to update
     * @param src the source buffer
     */
    public static long put(final ByteBuffer[] dsts, final int doffs, final int dlen, final ByteBuffer src) {
        long t = 0L;
        for (int i = 0; i < dlen; i ++) {
            final ByteBuffer buffer = dsts[i + doffs];
            final int dr = buffer.remaining();
            if (dr == 0) {
                continue;
            } else if (dr < src.remaining()) {
                buffer.put(slice(src, dr));
                t += dr;
            } else {
                buffer.put(src);
                t += dr;
                return t;
            }
        }
        return t;
    }
}
