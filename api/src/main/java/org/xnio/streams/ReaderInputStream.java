/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2010 Red Hat, Inc. and/or its affiliates.
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

import static org.xnio._private.Messages.msg;

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
            throw msg.nullParameter("writer");
        }
        if (encoder == null) {
            throw msg.nullParameter("decoder");
        }
        if (bufferSize < 1) {
            throw msg.parameterOutOfRange("bufferSize");
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
                            throw msg.malformedInput();
                        }
                        if (result.isUnmappable()) {
                            throw msg.unmappableCharacter();
                        }
                        throw msg.characterDecodingProblem();
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
