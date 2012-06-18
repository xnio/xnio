/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, JBoss Inc., and individual contributors as indicated
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

package org.xnio.streams;

import java.io.CharConversionException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import org.xnio.Buffers;

/**
 * An input stream which encodes characters into bytes.
 */
public final class ReaderInputStream extends InputStream {

    private final Reader reader;
    private final CharsetEncoder encoder;
    private final CharBuffer charBuffer;
    private final ByteBuffer byteBuffer;

    /**
     * Construct a new instance.
     *
     * @param reader the reader to encode from
     */
    public ReaderInputStream(final Reader reader) {
        this(reader, Charset.defaultCharset());
    }

    /**
     * Construct a new instance.
     *
     * @param reader the reader to encode from
     * @param charsetName the character set name
     * @throws UnsupportedEncodingException if the character set is not supported
     */
    public ReaderInputStream(final Reader reader, final String charsetName) throws UnsupportedEncodingException {
        this(reader, Streams.getCharset(charsetName));
    }

    /**
     * Construct a new instance.
     *
     * @param reader the reader to encode from
     * @param charset the character set
     */
    public ReaderInputStream(final Reader reader, final Charset charset) {
        this(reader, getEncoder(charset));
    }

    /**
     * Construct a new instance.
     *
     * @param reader the reader to encode from
     * @param encoder the character set encoder
     */
    public ReaderInputStream(final Reader reader, final CharsetEncoder encoder) {
        this(reader, encoder, 1024);
    }

    /**
     * Construct a new instance.
     *
     * @param reader the reader to encode from
     * @param encoder the character set encoder
     * @param bufferSize the buffer size to use
     */
    public ReaderInputStream(final Reader reader, final CharsetEncoder encoder, final int bufferSize) {
        if (reader == null) {
            throw new IllegalArgumentException("writer is null");
        }
        if (encoder == null) {
            throw new IllegalArgumentException("decoder is null");
        }
        if (bufferSize < 1) {
            throw new IllegalArgumentException("bufferSize must be larger than 0");
        }
        this.reader = reader;
        this.encoder = encoder;
        charBuffer = CharBuffer.wrap(new char[bufferSize]);
        byteBuffer = ByteBuffer.wrap(new byte[(int) ((float)bufferSize * encoder.averageBytesPerChar() + 0.5f)]);
        charBuffer.flip();
        byteBuffer.flip();
    }

    private static CharsetEncoder getEncoder(final Charset charset) {
        final CharsetEncoder encoder = charset.newEncoder();
        encoder.onMalformedInput(CodingErrorAction.REPLACE);
        encoder.onUnmappableCharacter(CodingErrorAction.REPLACE);
        return encoder;
    }

    /** {@inheritDoc} */
    public int read() throws IOException {
        final ByteBuffer byteBuffer = this.byteBuffer;
        if (! byteBuffer.hasRemaining()) {
            if (! fill()) {
                return -1;
            }
        }
        return byteBuffer.get() & 0xff;
    }

    /** {@inheritDoc} */
    public int read(final byte[] b, int off, int len) throws IOException {
        final ByteBuffer byteBuffer = this.byteBuffer;
        int cnt = 0;
        while (len > 0) {
            final int r = byteBuffer.remaining();
            if (r == 0) {
                if (! fill()) return cnt == 0 ? -1 : cnt;
                continue;
            }
            final int c = Math.min(r, len);
            byteBuffer.get(b, off, c);
            cnt += c;
            off += c;
            len -= c;
        }
        return cnt;
    }

    private boolean fill() throws IOException {
        final CharBuffer charBuffer = this.charBuffer;
        final ByteBuffer byteBuffer = this.byteBuffer;
        byteBuffer.compact();
        boolean filled = false;
        try {
            while (byteBuffer.hasRemaining()) {
                while (charBuffer.hasRemaining()) {
                    final CoderResult result = encoder.encode(charBuffer, byteBuffer, false);
                    if (result.isOverflow()) {
                        return true;
                    }
                    if (result.isUnderflow()) {
                        filled = true;
                        break;
                    }
                    if (result.isError()) {
                        if (result.isMalformed()) {
                            throw new CharConversionException("Malformed input");
                        }
                        if (result.isUnmappable()) {
                            throw new CharConversionException("Unmappable character");
                        }
                        throw new CharConversionException("Character decoding problem");
                    }
                }
                charBuffer.compact();
                try {
                    final int cnt = reader.read(charBuffer);
                    if (cnt == -1) {
                        return filled;
                    } else if (cnt > 0) {
                        filled = true;
                    }
                } finally {
                    charBuffer.flip();
                }
            }
            return true;
        } finally {
            byteBuffer.flip();
        }
    }

    /** {@inheritDoc} */
    public long skip(long n) throws IOException {
        final ByteBuffer byteBuffer = this.byteBuffer;
        int cnt = 0;
        while (n > 0) {
            final int r = byteBuffer.remaining();
            if (r == 0) {
                if (! fill()) return cnt;
                continue;
            }
            final int c = Math.min(r, n > (long) Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) n);
            Buffers.skip(byteBuffer, c);
            cnt += c;
            n -= c;
        }
        return cnt;
    }

    /** {@inheritDoc} */
    public int available() throws IOException {
        return byteBuffer.remaining();
    }

    /** {@inheritDoc} */
    public void close() throws IOException {
        byteBuffer.clear().flip();
        charBuffer.clear().flip();
        reader.close();
    }

    /**
     * Get a string representation of this object.
     *
     * @return the string
     */
    public String toString() {
        return "ReaderInputStream over " + reader;
    }
}
