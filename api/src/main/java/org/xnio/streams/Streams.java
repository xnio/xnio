/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2011 Red Hat, Inc. and/or its affiliates, and individual
 * contributors as indicated by the @author tags.
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import org.xnio.IoUtils;

/**
 * Stream utility class.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class Streams {

    private Streams() {
    }

    /**
     * Copy from one stream to another.
     *
     * @param input the source stream
     * @param output the destination stream
     * @param close {@code true} if the input and output streams should be closed
     * @param bufferSize the buffer size
     * @throws IOException if an I/O error occurs
     */
    public static void copyStream(InputStream input, OutputStream output, boolean close, int bufferSize) throws IOException {
        final byte[] buffer = new byte[bufferSize];
        int res;
        try {
            for (;;) {
                res = input.read(buffer);
                if (res == -1) {
                    if (close) {
                        input.close();
                        output.close();
                    }
                    return;
                }
                output.write(buffer, 0, res);
            }
        } finally {
            if (close) {
                IoUtils.safeClose(input);
                IoUtils.safeClose(output);
            }
        }
    }

    /**
     * Copy from one stream to another.  A default buffer size is assumed.
     *
     * @param input the source stream
     * @param output the destination stream
     * @param close {@code true} if the input and output streams should be closed
     * @throws IOException if an I/O error occurs
     */
    public static void copyStream(InputStream input, OutputStream output, boolean close) throws IOException {
        copyStream(input, output, close, 8192);
    }

    /**
     * Copy from one stream to another.  A default buffer size is assumed, and both streams are closed on completion.
     *
     * @param input the source stream
     * @param output the destination stream
     * @throws IOException if an I/O error occurs
     */
    public static void copyStream(InputStream input, OutputStream output) throws IOException {
        copyStream(input, output, true, 8192);
    }

    static Charset getCharset(final String charsetName) throws UnsupportedEncodingException {
        try {
            return Charset.forName(charsetName);
        } catch (UnsupportedCharsetException e) {
            throw new UnsupportedEncodingException(e.getMessage());
        }
    }
}
