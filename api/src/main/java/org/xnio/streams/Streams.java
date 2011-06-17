/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, JBoss Inc., and individual contributors as indicated
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
