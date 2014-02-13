/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2009 Red Hat, Inc. and/or its affiliates.
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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.io.UnsupportedEncodingException;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.ByteBuffer;

import static java.lang.Integer.signum;
import static java.lang.Math.abs;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.System.arraycopy;
import static org.xnio._private.Messages.msg;


/**
 * An immutable string of bytes.  Since instances of this class are guaranteed to be immutable, they are
 * safe to use as {@link Option} values and in an {@link OptionMap}.  Some operations can treat this byte string
 * as an ASCII- or ISO-8858-1-encoded character string.
 */
public final class ByteString implements Comparable<ByteString>, Serializable, CharSequence {

    private static final long serialVersionUID = -5998895518404718196L;

    private final byte[] bytes;
    private final int offs;
    private final int len;
    private transient int hashCode;
    private transient int hashCodeIgnoreCase;

    private ByteString(final byte[] bytes, final int offs, final int len) {
        this.bytes = bytes;
        this.offs = offs;
        this.len = len;
        if (offs < 0) {
            throw msg.parameterOutOfRange("offs");
        }
        if (len < 0) {
            throw msg.parameterOutOfRange("len");
        }
        if (offs + len > bytes.length) {
            throw msg.parameterOutOfRange("offs");
        }
    }

    private static int calcHashCode(final byte[] bytes, final int offs, final int len) {
        int hc = 31;
        final int end = offs + len;
        for (int i = offs; i < end; i++) {
            hc = (hc << 5) - hc + (bytes[i] & 0xff);
        }
        return hc == 0 ? Integer.MAX_VALUE : hc;
    }

    private static int calcHashCodeIgnoreCase(final byte[] bytes, final int offs, final int len) {
        int hc = 31;
        final int end = offs + len;
        for (int i = offs; i < end; i++) {
            hc = (hc << 5) - hc + (upperCase(bytes[i]) & 0xff);
        }
        return hc == 0 ? Integer.MAX_VALUE : hc;
    }

    private ByteString(final byte[] bytes) {
        this(bytes, 0, bytes.length);
    }

    /**
     * Create a byte string of the given literal bytes.  The given array is copied.
     *
     * @param bytes the bytes
     * @return the byte string
     */
    public static ByteString of(byte... bytes) {
        return new ByteString(bytes.clone());
    }

    /**
     * Create a byte string from the given array segment.
     *
     * @param b the byte array
     * @param offs the offset into the array
     * @param len the number of bytes to copy
     * @return the new byte string
     */
    public static ByteString copyOf(byte[] b, int offs, int len) {
        return new ByteString(Arrays.copyOfRange(b, offs, offs + len));
    }

    /**
     * Get a byte string from the bytes of a character string.
     *
     * @param str the character string
     * @param charset the character set to use
     * @return the byte string
     * @throws UnsupportedEncodingException if the encoding is not supported
     */
    public static ByteString getBytes(String str, String charset) throws UnsupportedEncodingException {
        return new ByteString(str.getBytes(charset));
    }

    /**
     * Get a byte string from the bytes of a character string.
     *
     * @param str the character string
     * @param charset the character set to use
     * @return the byte string
     */
    public static ByteString getBytes(String str, Charset charset) {
        return new ByteString(str.getBytes(charset));
    }

    /**
     * Get a byte string from the bytes of the character string.  The string must be a Latin-1 string.
     *
     * @param str the character string
     * @return the byte string
     */
    public static ByteString getBytes(String str) {
        final int length = str.length();
        return new ByteString(getStringBytes(false, new byte[length], 0, str, 0, length), 0, length);
    }

    /**
     * Get a byte string from all remaining bytes of a ByteBuffer.
     *
     * @param buffer the buffer
     * @return the byte string
     */
    public static ByteString getBytes(ByteBuffer buffer) {
        return new ByteString(Buffers.take(buffer));
    }

    /**
     * Get a byte string from a ByteBuffer.
     *
     * @param buffer the buffer
     * @param length the number of bytes to get
     * @return the byte string
     */
    public static ByteString getBytes(ByteBuffer buffer, int length) {
        return new ByteString(Buffers.take(buffer, length));
    }

    /**
     * Get a copy of the bytes of this ByteString.
     *
     * @return the copy
     */
    public byte[] getBytes() {
        return Arrays.copyOfRange(bytes, offs, len);
    }

    /**
     * Copy the bytes of this ByteString into the destination array.  If the array is too short to hold
     * the bytes, then only enough bytes to fill the array will be copied.
     *
     * @param dest the destination array
     *
     * @deprecated Replaced by {@link #copyTo(byte[])}.
     */
    public void getBytes(byte[] dest) {
        copyTo(dest);
    }

    /**
     * Copy the bytes of this ByteString into the destination array.  If the array is too short to hold
     * the bytes, then only enough bytes to fill the array will be copied.
     *
     * @param dest the destination array
     * @param offs the offset into the destination array
     *
     * @deprecated Replaced by {@link #copyTo(byte[],int)}.
     */
    public void getBytes(byte[] dest, int offs) {
        copyTo(dest, offs);
    }

    /**
     * Copy the bytes of this ByteString into the destination array.  If the array is too short to hold
     * the bytes, then only enough bytes to fill the array will be copied.
     *
     * @param dest the destination array
     * @param offs the offset into the destination array
     * @param len the maximum number of bytes to copy
     *
     * @deprecated Replaced by {@link #copyTo(byte[],int,int)}.
     */
    public void getBytes(byte[] dest, int offs, int len) {
        copyTo(dest, offs, len);
    }

    /**
     * Copy {@code len} bytes from this string at offset {@code srcOffs} to the given array at the given offset.
     *
     * @param srcOffs the source offset
     * @param dst     the destination
     * @param offs    the destination offset
     * @param len     the number of bytes to copy
     */
    public void copyTo(int srcOffs, byte[] dst, int offs, int len) {
        arraycopy(bytes, srcOffs + this.offs, dst, offs, min(this.len, len));
    }

    /**
     * Copy {@code len} bytes from this string to the given array at the given offset.
     *
     * @param dst  the destination
     * @param offs the destination offset
     * @param len  the number of bytes
     */
    public void copyTo(byte[] dst, int offs, int len) {
        copyTo(0, dst, offs, len);
    }

    /**
     * Copy all the bytes from this string to the given array at the given offset.
     *
     * @param dst  the destination
     * @param offs the destination offset
     */
    public void copyTo(byte[] dst, int offs) {
        copyTo(dst, offs, dst.length - offs);
    }

    /**
     * Copy all the bytes from this string to the given array at the given offset.
     *
     * @param dst  the destination
     */
    public void copyTo(byte[] dst) {
        copyTo(dst, 0, dst.length);
    }

    /**
     * Append the bytes of this string into the given buffer.
     *
     * @param dest the target buffer
     */
    public void appendTo(ByteBuffer dest) {
        dest.put(bytes, offs, len);
    }

    /**
     * Append as many bytes as possible to a byte buffer.
     *
     * @param offs the start offset
     * @param buffer the buffer to append to
     * @return the number of bytes appended
     */
    public int tryAppendTo(final int offs, final ByteBuffer buffer) {
        final byte[] b = bytes;
        final int len = min(buffer.remaining(), b.length - offs);
        buffer.put(b, offs + this.offs, len);
        return len;
    }

    /**
     * Append to an output stream.
     *
     * @param output the stream to write to
     * @throws IOException if an error occurs
     */
    public void writeTo(OutputStream output) throws IOException {
        // todo - determine if the output stream is trusted
        output.write(bytes, offs, len);
    }

    /**
     * Compare this string to another in a case-sensitive manner.
     *
     * @param other the other string
     * @return -1, 0, or 1
     */
    @Override
    public int compareTo(final ByteString other) {
        if (other == this) return 0;
        final int length = this.len;
        final int otherLength = other.len;
        final int len1 = min(length, otherLength);
        final byte[] bytes = this.bytes;
        final byte[] otherBytes = other.bytes;
        final int offs = this.offs;
        final int otherOffs = other.offs;
        int res;
        for (int i = 0; i < len1; i++) {
            res = signum(bytes[i + offs] - otherBytes[i + otherOffs]);
            if (res != 0) return res;
        }
        // shorter strings sort higher
        return signum(length - otherLength);
    }

    /**
     * Compare this string to another in a case-insensitive manner.
     *
     * @param other the other string
     * @return -1, 0, or 1
     */
    public int compareToIgnoreCase(final ByteString other) {
        if (other == this) return 0;
        if (other == this) return 0;
        final int length = this.len;
        final int otherLength = other.len;
        final int len1 = min(length, otherLength);
        final byte[] bytes = this.bytes;
        final byte[] otherBytes = other.bytes;
        final int offs = this.offs;
        final int otherOffs = other.offs;
        int res;
        for (int i = 0; i < len1; i++) {
            res = signum(upperCase(bytes[i + offs]) - upperCase(otherBytes[i + otherOffs]));
            if (res != 0) return res;
        }
        // shorter strings sort higher
        return signum(length - otherLength);
    }

    private static int upperCase(byte b) {
        return b >= 'a' && b <= 'z' ? b & 0xDF : b;
    }

    /**
     * Convert this byte string to a standard string.
     *
     * @param charset the character set to use
     * @return the standard string
     * @throws UnsupportedEncodingException if the charset is unknown
     */
    public String toString(String charset) throws UnsupportedEncodingException {
        if ("ISO-8859-1".equalsIgnoreCase(charset) || "Latin-1".equalsIgnoreCase(charset) || "ISO-Latin-1".equals(charset)) return toString();
        return new String(bytes, offs, len, charset);
    }

    /**
     * Get the number of bytes in this byte string.
     *
     * @return the number of bytes
     */
    public int length() {
        return len;
    }

    /**
     * Decode this byte string as a Latin-1 string.
     *
     * @return the Latin-1-decoded version of this string
     */
    @SuppressWarnings("deprecation")
    public String toString() {
        return new String(bytes, 0, offs, len);
    }

    /**
     * Decode this byte string as a UTF-8 string.
     *
     * @return the UTF-8-decoded version of this string
     */
    public String toUtf8String() {
        return new String(bytes, offs, len, StandardCharsets.UTF_8);
    }

    /**
     * Get the byte at an index.
     *
     * @return the byte at an index
     */
    public byte byteAt(int idx) {
        if (idx < 0 || idx > len) {
            throw new ArrayIndexOutOfBoundsException();
        }
        return bytes[idx + offs];
    }

    /**
     * Get the substring of this string starting at the given offset.
     *
     * @param offs the offset
     * @return the substring
     */
    public ByteString substring(int offs) {
        return substring(offs, len - offs);
    }

    /**
     * Get the substring of this string starting at the given offset.
     *
     * @param offs the offset
     * @param len the substring length
     * @return the substring
     */
    public ByteString substring(int offs, int len) {
        if (this.len - offs < len) {
            throw new IndexOutOfBoundsException();
        }
        return new ByteString(bytes, this.offs + offs, len);
    }

    /**
     * Get the hash code for this ByteString.
     *
     * @return the hash code
     */
    public int hashCode() {
        int hashCode = this.hashCode;
        if (hashCode == 0) {
            this.hashCode = hashCode = calcHashCode(bytes, offs, len);
        }
        return hashCode;
    }

    public int hashCodeIgnoreCase() {
        int hashCode = this.hashCodeIgnoreCase;
        if (hashCode == 0) {
            this.hashCodeIgnoreCase = hashCode = calcHashCodeIgnoreCase(bytes, offs, len);
        }
        return hashCode;
    }

    private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
        ois.defaultReadObject();
    }

    private static boolean equals(byte[] a, int aoff, byte[] b, int boff, int len) {
        for (int i = 0; i < len; i ++) {
            if (a[i + aoff] != b[i + boff]) return false;
        }
        return true;
    }

    private static boolean equalsIgnoreCase(byte[] a, int aoff, byte[] b, int boff, int len) {
        for (int i = 0; i < len; i ++) {
            if (upperCase(a[i + aoff]) != upperCase(b[i + boff])) return false;
        }
        return true;
    }

    /**
     * Determine if this ByteString equals another ByteString.
     *
     * @param obj the other object
     * @return {@code true} if they are equal
     */
    public boolean equals(final Object obj) {
        return (obj instanceof ByteString) && equals((ByteString) obj);
    }

    /**
     * Determine if this ByteString equals another ByteString.
     *
     * @param other the other object
     * @return {@code true} if they are equal
     */
    public boolean equals(final ByteString other) {
        final int len = this.len;
        return this == other || other != null && len == other.len && equals(bytes, offs, other.bytes, other.offs, len);
    }

    /**
     * Determine if this ByteString equals another ByteString, ignoring case (ASCII).
     *
     * @param other the other object
     * @return {@code true} if they are equal
     */
    public boolean equalsIgnoreCase(final ByteString other) {
        final int len = this.len;
        return this == other || other != null && len == other.len && equalsIgnoreCase(bytes, offs, other.bytes, other.offs, len);
    }

    /**
     * Get the unsigned {@code int} value of this string.  If the value is greater than would fit in 32 bits, only
     * the low 32 bits are returned.  Parsing stops on the first non-digit character.
     *
     * @param start the index to start at (must be less than or equal to length)
     * @return the value
     */
    public int toInt(final int start) {
        final int len = this.len;
        if (start >= len) {
            return 0;
        }
        final byte[] bytes = this.bytes;
        int v = 0;
        byte b;
        for (int i = start + offs; i < len; i ++) {
            b = bytes[i];
            if (b < '0' || b > '9') {
                return v;
            }
            v = (v << 3) + (v << 1) + (b - '0');
        }
        return v;
    }

    /**
     * Get the unsigned {@code int} value of this string.  If the value is greater than would fit in 32 bits, only
     * the low 32 bits are returned.  Parsing stops on the first non-digit character.
     *
     * @return the value
     */
    public int toInt() {
        return toInt(0);
    }

    /**
     * Get the unsigned {@code long} value of this string.  If the value is greater than would fit in 64 bits, only
     * the low 64 bits are returned.  Parsing stops on the first non-digit character.
     *
     * @param start the index to start at (must be less than or equal to length)
     * @return the value
     */
    public long toLong(final int start) {
        final int len = this.len;
        if (start >= len) {
            return 0;
        }
        final byte[] bytes = this.bytes;
        long v = 0;
        byte b;
        for (int i = start; i < len; i ++) {
            b = bytes[i];
            if (b < '0' || b > '9') {
                return v;
            }
            v = (v << 3) + (v << 1) + (b - '0');
        }
        return v;
    }

    /**
     * Get the unsigned {@code long} value of this string.  If the value is greater than would fit in 64 bits, only
     * the low 64 bits are returned.  Parsing stops on the first non-digit character.
     *
     * @return the value
     */
    public long toLong() {
        return toLong(0);
    }

    private static int decimalCount(int val) {
        assert val >= 0;
        // afaik no faster way exists to do this
        if (val < 10) return 1;
        if (val < 100) return 2;
        if (val < 1000) return 3;
        if (val < 10000) return 4;
        if (val < 100000) return 5;
        if (val < 1000000) return 6;
        if (val < 10000000) return 7;
        if (val < 100000000) return 8;
        if (val < 1000000000) return 9;
        return 10;
    }

    private static int decimalCount(long val) {
        assert val >= 0;
        // afaik no faster way exists to do this
        if (val < 10L) return 1;
        if (val < 100L) return 2;
        if (val < 1000L) return 3;
        if (val < 10000L) return 4;
        if (val < 100000L) return 5;
        if (val < 1000000L) return 6;
        if (val < 10000000L) return 7;
        if (val < 100000000L) return 8;
        if (val < 1000000000L) return 9;
        if (val < 10000000000L) return 10;
        if (val < 100000000000L) return 11;
        if (val < 1000000000000L) return 12;
        if (val < 10000000000000L) return 13;
        if (val < 100000000000000L) return 14;
        if (val < 1000000000000000L) return 15;
        if (val < 10000000000000000L) return 16;
        if (val < 100000000000000000L) return 17;
        if (val < 1000000000000000000L) return 18;
        return 19;
    }

    private static final ByteString ZERO = new ByteString(new byte[] { '0' });

    /**
     * Get a string version of the given value.
     *
     * @param val the value
     * @return the string
     */
    public static ByteString fromLong(long val) {
        if (val == 0) return ZERO;
        // afaik no faster way exists to do this
        int i = decimalCount(abs(val));
        final byte[] b;
        if (val < 0) {
            b = new byte[++i];
            b[0] = '-';
        } else {
            b = new byte[i];
        }
        long quo;
        // modulus
        int mod;
        do {
            quo = val / 10;
            mod = (int) (val - ((quo << 3) + (quo << 1)));
            b[--i] = (byte) (mod + '0');
            val = quo;
        } while (i > 0);
        return new ByteString(b);
    }

    /**
     * Get a string version of the given value.
     *
     * @param val the value
     * @return the string
     */
    public static ByteString fromInt(int val) {
        if (val == 0) return ZERO;
        // afaik no faster way exists to do this
        int i = decimalCount(abs(val));
        final byte[] b;
        if (val < 0) {
            b = new byte[++i];
            b[0] = '-';
        } else {
            b = new byte[i];
        }
        int quo;
        // modulus
        int mod;
        do {
            quo = val / 10;
            mod = val - ((quo << 3) + (quo << 1));
            b[--i] = (byte) (mod + '0');
            val = quo;
        } while (i > 0);
        return new ByteString(b);
    }

    /**
     * Determine whether this {@code ByteString} is equal (case-sensitively) to the given {@code String}.
     *
     * @param str the string to check
     * @return {@code true} if the given string is equal (case-sensitively) to this instance, {@code false} otherwise
     */
    public boolean equalToString(String str) {
        if (str == null) return false;
        final byte[] bytes = this.bytes;
        final int length = bytes.length;
        if (str.length() != length) {
            return false;
        }
        char ch;
        final int end = offs + len;
        for (int i = offs; i < end; i++) {
            ch = str.charAt(i);
            if (ch > 0xff || bytes[i] != (byte) str.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Determine whether this {@code ByteString} is equal (case-insensitively) to the given {@code String}.
     *
     * @param str the string to check
     * @return {@code true} if the given string is equal (case-insensitively) to this instance, {@code false} otherwise
     */
    public boolean equalToStringIgnoreCase(String str) {
        if (str == null) return false;
        final byte[] bytes = this.bytes;
        final int length = bytes.length;
        if (str.length() != length) {
            return false;
        }
        char ch;
        final int end = offs + len;
        for (int i = offs; i < end; i++) {
            ch = str.charAt(i);
            if (ch > 0xff || upperCase(bytes[i]) != upperCase((byte) ch)) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Get the index of the given character in this string.
     *
     * @param c the character
     * @return the index, or -1 if it was not found
     */
    public int indexOf(final char c) {
        return indexOf(c, 0);
    }

    /**
     * Get the index of the given character in this string.
     *
     * @param c the character
     * @return the index, or -1 if it was not found
     */
    public int indexOf(final char c, int start) {
        if (c > 255) {
            return -1;
        }
        final int len = this.len;
        if (start > len) {
            return -1;
        }
        start = max(0, start) + offs;
        final byte[] bytes = this.bytes;
        final byte bc = (byte) c;
        final int end = start + len;
        for (int i = start; i < end; i++) {
            if (bytes[i] == bc) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Get the last index of the given character in this string.
     *
     * @param c the character
     * @return the index, or -1 if it was not found
     */
    public int lastIndexOf(final char c) {
        return lastIndexOf(c, length() - 1);
    }

    /**
     * Get the last index of the given character in this string.
     *
     * @param c the character
     * @return the index, or -1 if it was not found
     */
    public int lastIndexOf(final char c, int start) {
        if (c > 255) {
            return -1;
        }
        final byte[] bytes = this.bytes;
        final int offs = this.offs;
        start = min(start, len - 1) + offs;
        final byte bc = (byte) c;
        for (int i = start; i >= offs; --i) {
            if (bytes[i] == bc) {
                return i;
            }
        }
        return -1;
    }
    
        // Linear array searches

    private static int arrayIndexOf(byte[] a, int aOffs, byte[] b, int bOffs, int bLen) {
        final int aLen = a.length - aOffs;
        if (bLen > aLen || aLen < 0) {
            return -1;
        }
        aOffs = max(0, aOffs);
        if (bLen == 0) {
            return aOffs;
        }
        final byte startByte = b[bOffs];
        final int limit = aLen - bLen;
        OUTER: for (int i = aOffs; i < limit; i ++) {
            if (a[i] == startByte) {
                for (int j = 1; j < bLen; j ++) {
                    if (a[i + j] != b[j + bOffs]) {
                        continue OUTER;
                    }
                }
                return i;
            }
        }
        return -1;
    }

    private static int arrayIndexOf(byte[] a, int aOffs, String string) {
        final int aLen = a.length - aOffs;
        final int bLen = string.length();
        if (bLen > aLen || aLen < 0) {
            return -1;
        }
        aOffs = max(0, aOffs);
        if (bLen == 0) {
            return aOffs;
        }
        final char startChar = string.charAt(0);
        if (startChar > 0xff) {
            return -1;
        }
        char ch;
        final int limit = aLen - bLen;
        OUTER: for (int i = aOffs; i < limit; i ++) {
            if (a[i] == startChar) {
                for (int j = 1; j < bLen; j ++) {
                    ch = string.charAt(j);
                    if (ch > 0xff) {
                        return -1;
                    }
                    if (a[i + j] != ch) {
                        continue OUTER;
                    }
                }
                return i;
            }
        }
        return -1;
    }

    private static int arrayIndexOfIgnoreCase(byte[] a, int aOffs, byte[] b, int bOffs, int bLen) {
        final int aLen = a.length - aOffs;
        if (bLen > aLen || aLen < 0) {
            return -1;
        }
        aOffs = max(0, aOffs);
        if (bLen == 0) {
            return aOffs;
        }
        final int startChar = upperCase(b[bOffs]);
        final int limit = aLen - bLen;
        OUTER: for (int i = aOffs; i < limit; i ++) {
            if (upperCase(a[i]) == startChar) {
                for (int j = 1; j < bLen; j ++) {
                    if (upperCase(a[i + j]) != upperCase(b[j + bOffs])) {
                        continue OUTER;
                    }
                }
                return i;
            }
        }
        return -1;
    }

    private static int arrayIndexOfIgnoreCase(byte[] a, int aOffs, String string) {
        final int aLen = a.length - aOffs;
        final int bLen = string.length();
        if (bLen > aLen || aLen < 0) {
            return -1;
        }
        aOffs = max(0, aOffs);
        if (bLen == 0) {
            return aOffs;
        }
        final char startChar = string.charAt(0);
        if (startChar > 0xff) {
            return -1;
        }
        final int startCP = upperCase((byte) startChar);
        final int limit = aLen - bLen;
        char ch;
        OUTER: for (int i = aOffs; i < limit; i ++) {
            if (upperCase(a[i]) == startCP) {
                for (int j = 1; j < bLen; j ++) {
                    ch = string.charAt(j);
                    if (ch > 0xff) {
                        return -1;
                    }
                    // technically speaking, 'ı' (0x131) maps to I and 'ſ' (0x17F) maps to S, but this is unlikely to come up in ISO-8859-1
                    if (upperCase(a[i + j]) != upperCase((byte) ch)) {
                        continue OUTER;
                    }
                }
                return i;
            }
        }
        return -1;
    }

    private static int arrayLastIndexOf(byte[] a, int aOffs, byte[] b, final int bOffs, final int bLen) {
        final int aLen = a.length - aOffs;
        if (bLen > aLen || aLen < 0 || aOffs < 0) {
            return -1;
        }
        // move to the last possible position it could be
        aOffs = min(aLen - bLen, aOffs);
        if (bLen == 0) {
            return aOffs;
        }
        final byte startByte = b[0];
        OUTER: for (int i = aOffs - 1; i >= 0; i --) {
            if (a[i] == startByte) {
                for (int j = 1; j < bLen; j++) {
                    if (a[i + j] != b[bOffs + j]) {
                        continue OUTER;
                    }
                    return i;
                }
            }
        }
        return -1;
    }

    private static int arrayLastIndexOf(byte[] a, int aOffs, String string) {
        final int aLen = a.length - aOffs;
        final int bLen = string.length();
        if (bLen > aLen || aLen < 0 || aOffs < 0) {
            return -1;
        }
        // move to the last possible position it could be
        aOffs = min(aLen - bLen, aOffs);
        if (bLen == 0) {
            return aOffs;
        }
        final char startChar = string.charAt(0);
        if (startChar > 0xff) {
            return -1;
        }
        final byte startByte = (byte) startChar;
        char ch;
        OUTER: for (int i = aOffs - 1; i >= 0; i --) {
            if (a[i] == startByte) {
                for (int j = 1; j < bLen; j++) {
                    ch = string.charAt(j);
                    if (ch > 0xff) {
                        return -1;
                    }
                    if (a[i + j] != (byte) ch) {
                        continue OUTER;
                    }
                    return i;
                }
            }
        }
        return -1;
    }

    private static int arrayLastIndexOfIgnoreCase(byte[] a, int aOffs, byte[] b, final int bOffs, final int bLen) {
        final int aLen = a.length - aOffs;
        if (bLen > aLen || aLen < 0 || aOffs < 0) {
            return -1;
        }
        // move to the last possible position it could be
        aOffs = min(aLen - bLen, aOffs);
        if (bLen == 0) {
            return aOffs;
        }
        final int startCP = upperCase(b[bOffs]);
        OUTER: for (int i = aOffs - 1; i >= 0; i --) {
            if (upperCase(a[i]) == startCP) {
                for (int j = 1; j < bLen; j++) {
                    if (upperCase(a[i + j]) != upperCase(b[j + bOffs])) {
                        continue OUTER;
                    }
                    return i;
                }
            }
        }
        return -1;
    }

    private static int arrayLastIndexOfIgnoreCase(byte[] a, int aOffs, String string) {
        final int aLen = a.length - aOffs;
        final int bLen = string.length();
        if (bLen > aLen || aLen < 0 || aOffs < 0) {
            return -1;
        }
        // move to the last possible position it could be
        aOffs = min(aLen - bLen, aOffs);
        if (bLen == 0) {
            return aOffs;
        }
        final char startChar = string.charAt(0);
        if (startChar > 0xff) {
            return -1;
        }
        final int startCP = upperCase((byte) startChar);
        char ch;
        OUTER: for (int i = aOffs - 1; i >= 0; i --) {
            if (upperCase(a[i]) == startCP) {
                for (int j = 1; j < bLen; j++) {
                    ch = string.charAt(j);
                    if (ch > 0xff) {
                        return -1;
                    }
                    // technically speaking, 'ı' (0x131) maps to I and 'ſ' (0x17F) maps to S, but this is unlikely to come up in ISO-8859-1
                    if (upperCase(a[i + j]) != upperCase((byte) ch)) {
                        continue OUTER;
                    }
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Determine whether this string contains another string (case-sensitive).
     *
     * @param other the string to test
     * @return {@code true} if this string contains {@code other}, {@code false} otherwise
     */
    public boolean contains(final ByteString other) {
        if (other == this) return true;
        if (other == null) return false;
        final byte[] otherBytes = other.bytes;
        return arrayIndexOf(bytes, offs, otherBytes, other.offs, other.len) != -1;
    }

    /**
     * Determine whether this string contains another string (case-sensitive).
     *
     * @param other the string to test
     * @return {@code true} if this string contains {@code other}, {@code false} otherwise
     */
    public boolean contains(final String other) {
        return other != null && toString().contains(other);
    }

    /**
     * Determine whether this string contains another string (case-insensitive).
     *
     * @param other the string to test
     * @return {@code true} if this string contains {@code other}, {@code false} otherwise
     */
    public boolean containsIgnoreCase(final ByteString other) {
        return other == this || other != null && arrayIndexOfIgnoreCase(bytes, offs, other.bytes, other.offs, other.len) != -1;
    }

    /**
     * Determine whether this string contains another string (case-sensitive).
     *
     * @param other the string to test
     * @return {@code true} if this string contains {@code other}, {@code false} otherwise
     */
    public boolean containsIgnoreCase(final String other) {
        return arrayIndexOfIgnoreCase(bytes, offs, other) != -1;
    }

    public int indexOf(final ByteString other) {
        return arrayIndexOf(bytes, offs, other.bytes, other.offs, other.len);
    }

    public int indexOf(final ByteString other, int start) {
        if (start > len) return -1;
        if (start < 0) start = 0;
        return arrayIndexOf(bytes, offs + start, other.bytes, other.offs, other.len);
    }

    public int indexOf(final String other) {
        return arrayIndexOf(bytes, offs, other);
    }

    public int indexOf(final String other, int start) {
        if (start > len) return -1;
        if (start < 0) start = 0;
        return arrayIndexOf(bytes, offs + start, other);
    }

    public int indexOfIgnoreCase(final ByteString other) {
        return arrayIndexOfIgnoreCase(bytes, offs, other.bytes, other.offs, other.len);
    }

    public int indexOfIgnoreCase(final ByteString other, int start) {
        if (start > len) return -1;
        if (start < 0) start = 0;
        return arrayIndexOfIgnoreCase(bytes, offs + start, other.bytes, other.offs, other.len);
    }

    public int indexOfIgnoreCase(final String other) {
        return arrayIndexOfIgnoreCase(bytes, offs, other);
    }

    public int indexOfIgnoreCase(final String other, int start) {
        if (start > len) return -1;
        if (start < 0) start = 0;
        return arrayIndexOfIgnoreCase(bytes, offs + start, other);
    }

    public int lastIndexOf(final ByteString other) {
        return arrayLastIndexOf(bytes, offs, other.bytes, other.offs, other.len);
    }

    public int lastIndexOf(final ByteString other, int start) {
        if (start > len) return -1;
        if (start < 0) start = 0;
        return arrayLastIndexOf(bytes, offs + start, other.bytes, other.offs, other.len);
    }

    public int lastIndexOf(final String other) {
        return arrayLastIndexOf(bytes, offs, other);
    }

    public int lastIndexOf(final String other, int start) {
        return arrayLastIndexOf(bytes, offs + start, other);
    }

    public int lastIndexOfIgnoreCase(final ByteString other) {
        return arrayLastIndexOfIgnoreCase(bytes, offs, other.bytes, other.offs, other.len);
    }

    public int lastIndexOfIgnoreCase(final ByteString other, int start) {
        if (start > len) return -1;
        if (start < 0) start = 0;
        return arrayLastIndexOfIgnoreCase(bytes, offs + start, other.bytes, other.offs, other.len);
    }

    public int lastIndexOfIgnoreCase(final String other) {
        return arrayLastIndexOfIgnoreCase(bytes, offs, other);
    }

    public int lastIndexOfIgnoreCase(final String other, int start) {
        return arrayLastIndexOfIgnoreCase(bytes, offs + start, other);
    }

    public boolean regionMatches(boolean ignoreCase, int offset, byte[] other, int otherOffset, int len) {
        if (offset < 0 || otherOffset < 0 || offset + len > this.len || otherOffset + len > other.length) {
            return false;
        }
        if (ignoreCase) {
            return equalsIgnoreCase(bytes, offset + offs, other, otherOffset, len);
        } else {
            return equals(bytes, offset + offs, other, otherOffset, len);
        }
    }

    public boolean regionMatches(boolean ignoreCase, int offset, ByteString other, int otherOffset, int len) {
        if (offset < 0 || otherOffset < 0 || offset + len > this.len || otherOffset + len > other.len) {
            return false;
        }
        if (ignoreCase) {
            return equalsIgnoreCase(bytes, offset + offs, other.bytes, otherOffset, len);
        } else {
            return equals(bytes, offset + offs, other.bytes, otherOffset, len);
        }
    }

    public boolean regionMatches(boolean ignoreCase, int offset, String other, int otherOffset, int len) {
        if (offset < 0 || otherOffset < 0 || offset + len > this.len || otherOffset + len > other.length()) {
            return false;
        }
        if (ignoreCase) {
            return equalsIgnoreCase(bytes, offset + offs, other, otherOffset, len);
        } else {
            return equals(bytes, offset + offs, other, otherOffset, len);
        }
    }

    private static boolean equalsIgnoreCase(final byte[] a, int aOffs, String string, int stringOffset, int length) {
        char ch;
        for (int i = 0; i < length; i ++) {
            ch = string.charAt(i + stringOffset);
            if (ch > 0xff) {
                return false;
            }
            if (a[i + aOffs] != (byte) ch) {
                return false;
            }
        }
        return true;
    }

    private static boolean equals(final byte[] a, int aOffs, String string, int stringOffset, int length) {
        char ch;
        for (int i = 0; i < length; i ++) {
            ch = string.charAt(i + stringOffset);
            if (ch > 0xff) {
                return false;
            }
            if (upperCase(a[i + aOffs]) != upperCase((byte) ch)) {
                return false;
            }
        }
        return true;
    }

    public boolean startsWith(ByteString prefix) {
        return regionMatches(false, 0, prefix, 0, prefix.length());
    }

    public boolean startsWith(String prefix) {
        return regionMatches(false, 0, prefix, 0, prefix.length());
    }

    public boolean startsWith(char prefix) {
        return prefix <= 0xff && len > 0 && bytes[offs] == (byte) prefix;
    }

    public boolean startsWithIgnoreCase(ByteString prefix) {
        return regionMatches(true, 0, prefix, 0, prefix.length());
    }

    public boolean startsWithIgnoreCase(String prefix) {
        return regionMatches(true, 0, prefix, 0, prefix.length());
    }

    public boolean startsWithIgnoreCase(char prefix) {
        return prefix <= 0xff && len > 0 && upperCase(bytes[offs]) == upperCase((byte) prefix);
    }

    public boolean endsWith(ByteString suffix) {
        final int suffixLength = suffix.len;
        return regionMatches(false, len - suffixLength, suffix, 0, suffixLength);
    }

    public boolean endsWith(String suffix) {
        final int suffixLength = suffix.length();
        return regionMatches(false, len - suffixLength, suffix, 0, suffixLength);
    }

    public boolean endsWith(char suffix) {
        final int len = this.len;
        return suffix <= 0xff && len > 0 && bytes[offs + len - 1] == (byte) suffix;
    }

    public boolean endsWithIgnoreCase(ByteString suffix) {
        final int suffixLength = suffix.length();
        return regionMatches(true, len - suffixLength, suffix, 0, suffixLength);
    }

    public boolean endsWithIgnoreCase(String suffix) {
        final int suffixLength = suffix.length();
        return regionMatches(true, len - suffixLength, suffix, 0, suffixLength);
    }

    public boolean endsWithIgnoreCase(char suffix) {
        final int len = this.len;
        return suffix <= 0xff && len > 0 && upperCase(bytes[offs + len - 1]) == upperCase((byte) suffix);
    }

    public ByteString concat(byte[] suffixBytes) {
        return concat(suffixBytes, 0, suffixBytes.length);
    }

    public ByteString concat(byte[] suffixBytes, int offs, int len) {
        if (len <= 0) { return this; }
        final int length = this.len;
        byte[] newBytes = Arrays.copyOfRange(bytes, this.offs, length + len);
        System.arraycopy(suffixBytes, offs, newBytes, length, len);
        return new ByteString(newBytes);
    }

    public ByteString concat(ByteString suffix) {
        return concat(suffix.bytes, suffix.offs, suffix.len);
    }

    public ByteString concat(ByteString suffix, int offs, int len) {
        return concat(suffix.bytes, offs + suffix.offs, min(len, suffix.len));
    }

    public ByteString concat(String suffix) {
        return concat(suffix, 0, suffix.length());
    }

    @SuppressWarnings("deprecation")
    private static byte[] getStringBytes(final boolean trust, final byte[] dst, final int dstOffs, final String src, final int srcOffs, final int len) {
        if (trust) {
            src.getBytes(srcOffs, srcOffs + len, dst, dstOffs);
        } else {
            for (int i = srcOffs; i < len; i++) {
                char c = src.charAt(i);
                if (c > 0xff) {
                    throw new IllegalArgumentException("Invalid string contents");
                }
                dst[i + dstOffs] = (byte) c;
            }
        }
        return dst;
    }

    public ByteString concat(String suffix, int offs, int len) {
        if (len <= 0) { return this; }
        final byte[] bytes = this.bytes;
        final int length = this.len;
        byte[] newBytes = Arrays.copyOfRange(bytes, offs, offs + length + len);
        getStringBytes(false, newBytes, length, suffix, offs, len);
        return new ByteString(newBytes);
    }

    public static ByteString concat(String prefix, ByteString suffix) {
        final int prefixLength = prefix.length();
        final byte[] suffixBytes = suffix.bytes;
        final int suffixLength = suffixBytes.length;
        final byte[] newBytes = new byte[prefixLength + suffixLength];
        getStringBytes(false, newBytes, 0, prefix, 0, prefixLength);
        System.arraycopy(suffixBytes, suffix.offs, newBytes, prefixLength, suffixLength);
        return new ByteString(newBytes);
    }

    public static ByteString concat(String prefix, String suffix) {
        final int prefixLength = prefix.length();
        final int suffixLength = suffix.length();
        final byte[] newBytes = new byte[prefixLength + suffixLength];
        getStringBytes(false, newBytes, 0, prefix, 0, prefixLength);
        getStringBytes(false, newBytes, prefixLength, suffix, 0, suffixLength);
        return new ByteString(newBytes);
    }

    public char charAt(final int index) {
        if (index < 0 || index > len) {
            throw new ArrayIndexOutOfBoundsException();
        }
        return (char) (bytes[index + offs] & 0xff);
    }

    public ByteString subSequence(final int start, final int end) {
        return substring(start, end);
    }
}
