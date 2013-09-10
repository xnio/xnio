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
import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.io.UnsupportedEncodingException;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.ByteBuffer;

import static org.xnio._private.Messages.msg;


/**
 * An immutable string of bytes.  Since instances of this class are guaranteed to be immutable, they are
 * safe to use as {@link Option} values and in an {@link OptionMap}.
 */
public final class ByteString implements Comparable<ByteString>, Serializable {

    private static final long serialVersionUID = -5998895518404718196L;

    private final byte[] bytes;
    private final int offs;
    private final int len;
    private final transient int hashCode;

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
        hashCode = calcHashCode(bytes, offs, len);
    }

    private static int calcHashCode(final byte[] bytes, final int offs, final int len) {
        int hashCode = 1;
        final int end = offs + len;
        for (int i = offs; i < end; i ++) {
            hashCode = 31 * hashCode + bytes[i];
        }
        return hashCode;
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
     * Get a byte string from all remaining bytes of a ByteBuffer.
     *
     * @param buffer the buffer
     * @return the byte string
     */
    public static ByteString getBytes(ByteBuffer buffer) {
        return getBytes(buffer, buffer.remaining());
    }

    /**
     * Get a byte string from a ByteBuffer.
     *
     * @param buffer the buffer
     * @param length the number of bytes to get
     * @return the byte string
     */
    public static ByteString getBytes(ByteBuffer buffer, int length) {
        final byte[] b = new byte[length];
        buffer.get(b);
        return new ByteString(b);
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
     */
    public void getBytes(byte[] dest) {
        getBytes(dest, 0, dest.length);
    }

    /**
     * Copy the bytes of this ByteString into the destination array.  If the array is too short to hold
     * the bytes, then only enough bytes to fill the array will be copied.
     *
     * @param dest the destination array
     * @param offs the offset into the destination array
     */
    public void getBytes(byte[] dest, int offs) {
        getBytes(dest, offs, dest.length - offs);
    }

    /**
     * Copy the bytes of this ByteString into the destination array.  If the array is too short to hold
     * the bytes, then only enough bytes to fill the array will be copied.
     *
     * @param dest the destination array
     * @param offs the offset into the destination array
     * @param len the maximum number of bytes to copy
     */
    public void getBytes(byte[] dest, int offs, int len) {
        System.arraycopy(bytes, this.offs, dest, offs, Math.min(this.len, len));
    }

    /**
     * Convert this byte string to a standard string.
     *
     * @param charset the character set to use
     * @return the standard string
     * @throws UnsupportedEncodingException if the charset is unknown
     */
    public String toString(String charset) throws UnsupportedEncodingException {
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
     * Compare this ByteString to another.
     *
     * @param o the other ByteString
     * @return the comparison result
     */
    public int compareTo(final ByteString o) {
        if (this == o) {
            return 0;
        }
        final int len = this.len;
        final int olen = o.len;
        final int offs = this.offs;
        final int ooffs = o.offs;
        final byte[] b = bytes;
        final byte[] ob = o.bytes;
        int clen = Math.min(len, olen);
        for (int i = 0; i < clen; i ++) {
            int d = b[offs + i] - ob[ooffs + i];
            if (d != 0) {
                return Integer.signum(d);
            }
        }
        return Integer.signum(len - olen);
    }

    /**
     * Get the hash code for this ByteString.
     *
     * @return the hash code
     */
    public int hashCode() {
        return hashCode;
    }

    private static final Field hashCodeField;

    static {
        hashCodeField = AccessController.doPrivileged(new PrivilegedAction<Field>() {
            public Field run() {
                Field f = null;
                for (Field field : ByteString.class.getDeclaredFields()) {
                    if (field.getName().equals("hashCode")) {
                        f = field;
                        break;
                    }
                }
                if (f == null) {
                    throw new NoSuchFieldError("No hashCode field");
                }
                f.setAccessible(true);
                return f;
            }
        });
    }

    private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
        ois.defaultReadObject();
        try {
            hashCodeField.set(this, Integer.valueOf(calcHashCode(bytes, offs, len)));
        } catch (IllegalAccessException e) {
            throw new IllegalAccessError(e.getMessage());
        }
    }

    private static boolean equals(byte[] a, int aoff, byte[] b, int boff, int len) {
        for (int i = 0; i < len; i ++) {
            if (a[i + aoff] != b[i + boff]) return false;
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
        return this == other || len == other.len && hashCode == other.hashCode && equals(bytes, offs, other.bytes, other.offs, len);
    }
}
