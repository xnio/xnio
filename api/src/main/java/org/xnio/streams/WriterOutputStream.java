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
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;

/**
 * An output stream which decodes bytes into a character writer.
 */
public final class WriterOutputStream extends OutputStream {

    private final Writer writer;
    private final CharsetDecoder decoder;
    private final ByteBuffer byteBuffer;
    private final char[] chars;
    private volatile boolean closed;

    /**
     * Construct a new instance.
     *
     * @param writer the writer to decode into
     */
    public WriterOutputStream(final Writer writer) {
        this(writer, Charset.defaultCharset());
    }

    /**
     * Construct a new instance.
     *
     * @param writer the writer to decode into
     * @param decoder the charset decoder to use
     */
    public WriterOutputStream(final Writer writer, final CharsetDecoder decoder) {
        this(writer, decoder, 1024);
    }

    /**
     * Construct a new instance.
     *
     * @param writer the writer to decode into
     * @param decoder the charset decoder to use
     * @param bufferSize the buffer size to use
     */
    public WriterOutputStream(final Writer writer, final CharsetDecoder decoder, int bufferSize) {
        if (writer == null) {
            throw msg.nullParameter("writer");
        }
        if (decoder == null) {
            throw msg.nullParameter("decoder");
        }
        if (bufferSize < 1) {
            throw msg.parameterOutOfRange("bufferSize");
        }
        this.writer = writer;
        this.decoder = decoder;
        byteBuffer = ByteBuffer.allocate(bufferSize);
        chars = new char[(int) ((float)bufferSize * decoder.maxCharsPerByte() + 0.5f)];
    }

    /**
     * Construct a new instance.
     *
     * @param writer the writer to decode into
     * @param charset the character set to use
     */
    public WriterOutputStream(final Writer writer, final Charset charset) {
        this(writer, getDecoder(charset));
    }

    /**
     * Construct a new instance.
     *
     * @param writer the writer to decode into
     * @param charsetName the character set name to use
     * @throws UnsupportedEncodingException if the character set name is unknown
     */
    public WriterOutputStream(final Writer writer, final String charsetName) throws UnsupportedEncodingException {
        this(writer, Streams.getCharset(charsetName));
    }

    private static CharsetDecoder getDecoder(final Charset charset) {
        final CharsetDecoder decoder = charset.newDecoder();
        decoder.onMalformedInput(CodingErrorAction.REPLACE);
        decoder.onUnmappableCharacter(CodingErrorAction.REPLACE);
        decoder.replaceWith("?");
        return decoder;
    }

    /** {@inheritDoc} */
    public void write(final int b) throws IOException {
        if (closed) throw msg.streamClosed();
        final ByteBuffer byteBuffer = this.byteBuffer;
        if (! byteBuffer.hasRemaining()) {
            doFlush(false);
        }
        byteBuffer.put((byte) b);
    }

    /** {@inheritDoc} */
    public void write(final byte[] b, int off, int len) throws IOException {
        if (closed) throw msg.streamClosed();
        final ByteBuffer byteBuffer = this.byteBuffer;
        // todo Correct first, fast later
        while (len > 0) {
            final int r = byteBuffer.remaining();
            if (r == 0) {
                doFlush(false);
                continue;
            }
            final int c = Math.min(len, r);
            byteBuffer.put(b, off, c);
            len -= c;
            off += c;
        }
    }

    private void doFlush(final boolean eof) throws IOException {
        final CharBuffer charBuffer = CharBuffer.wrap(chars);
        final ByteBuffer byteBuffer = this.byteBuffer;
        final CharsetDecoder decoder = this.decoder;
        byteBuffer.flip();
        try {
            while (byteBuffer.hasRemaining()) {
                final CoderResult result = decoder.decode(byteBuffer, charBuffer, eof);
                if (result.isOverflow()) {
                    writer.write(chars, 0, charBuffer.position());
                    charBuffer.clear();
                    continue;
                }
                if (result.isUnderflow()) {
                    final int p = charBuffer.position();
                    if (p > 0) {
                        writer.write(chars, 0, p);
                    }
                    return;
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
        } finally {
            byteBuffer.compact();
        }
    }

    /** {@inheritDoc} */
    public void flush() throws IOException {
        if (closed) throw msg.streamClosed();
        doFlush(false);
        writer.flush();
    }

    /** {@inheritDoc} */
    public void close() throws IOException {
        closed = true;
        doFlush(true);
        byteBuffer.clear();
        writer.close();
    }

    /**
     * Get the string representation of this object.
     *
     * @return the string
     */
    public String toString() {
        return "Output stream writing to " + writer;
    }
}
