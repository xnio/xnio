/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2013 Red Hat, Inc. and/or its affiliates.
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

package org.xnio.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author Stuart Douglas
 */
public class HttpParserTestCase {


    @Test
    public void testSimpleResponses() throws IOException {
        runTest("HTTP/1.1 101 Upgrade\r\n\r\n", 101, "HTTP/1.1", "Upgrade");
        runTest("HTTP/1.1 101 Upgrade\r\nConnection: Upgrade\r\n\r\n", 101, "HTTP/1.1", "Upgrade", "connection", "Upgrade");
        runTest("HTTP/1.1 404 Not Found\r\nConnection: close\r\nSet-Cookie: someCookie\r\n\r\n", 404, "HTTP/1.1", "Not Found", "connection", "close", "set-cookie", "someCookie");
    }

    public void runTest(String response, final int status, final String version, final String message, final String... header) throws IOException {
        final Map<String, String> headerMap = new HashMap<String, String>();
        Assert.assertEquals("Headers must be a multiple of 2", 0, header.length % 2);
        for (int i = 0; i < header.length; i += 2) {
            headerMap.put(header[i], header[i + 1]);
        }
        testMethodSplit(response, status, version, message, headerMap);
        testOneCharacterAtATime(response, status, version, message, headerMap);
    }


    void testMethodSplit(String response, final int status, final String version, final String message, final Map<String, String> headers) {
        byte[] in = response.getBytes();
        for (int i = 0; i < in.length - 4; ++i) {
            try {
                testSplit(i, in, status, version, message, headers);
            } catch (Throwable e) {
                throw new RuntimeException("Test failed at split " + i, e);
            }
        }
    }

    public void testOneCharacterAtATime(String response, final int status, final String version, final String message, final Map<String, String> headers) throws IOException {

        ByteBuffer buffer = ByteBuffer.wrap(response.getBytes());
        buffer.limit(0);
        final HttpUpgradeParser parser = new HttpUpgradeParser();
        while (!parser.isComplete()) {
            buffer.limit(buffer.limit() + 1);
            parser.parse(buffer);
        }
        runAssertions(parser, status, version, message, headers);
    }

    private void testSplit(final int split, byte[] in, final int status, final String version, final String message, final Map<String, String> headers) throws IOException {
        final HttpUpgradeParser parser = new HttpUpgradeParser();
        ByteBuffer buffer = ByteBuffer.wrap(in);
        buffer.limit(split);
        parser.parse(buffer);
        buffer.limit(buffer.capacity());
        parser.parse(buffer);
        runAssertions(parser, status, version, message, headers);
    }

    private void runAssertions(HttpUpgradeParser parser, final int status, final String version, final String message, final Map<String, String> headers) {
        Assert.assertEquals(status, parser.getResponseCode());
        Assert.assertEquals(version, parser.getHttpVersion());
        Assert.assertEquals(message, parser.getMessage());
        Assert.assertEquals(headers.size(), parser.getHeaders().size());
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            Assert.assertEquals(entry.getValue(), parser.getHeaders().get(entry.getKey()).get(0));
        }


    }


}
