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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * @author Stuart Douglas
 */
class HttpUpgradeParser {


    private static final int VERSION = 0;
    private static final int STATUS_CODE = 1;
    private static final int MESSAGE = 2;
    private static final int HEADER_NAME = 3;
    private static final int HEADER_VALUE = 4;
    private static final int COMPLETE = 5;

    private int parseState = 0;
    private String httpVersion;
    private int responseCode;
    private String message;
    private final Map<String, List<String>> headers = new HashMap<String, List<String>>();

    private final StringBuilder current = new StringBuilder();
    private String headerName;

    void parse(final ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining() && !isComplete()) {
            switch (parseState) {
                case VERSION:
                    parseVersion(buffer);
                    break;
                case STATUS_CODE:
                    parseStatusCode(buffer);
                    break;
                case MESSAGE:
                    parseMessage(buffer);
                    break;
                case HEADER_NAME:
                    parseHeaderName(buffer);
                    break;
                case HEADER_VALUE:
                    parseHeaderValue(buffer);
                    break;
                case COMPLETE:
                    return;
            }
        }

    }

    private void parseHeaderValue(final ByteBuffer buffer) {
        while (buffer.hasRemaining()) {
            byte b = buffer.get();
            if (b == '\r' || b == '\n') {
                String key = headerName.toLowerCase(Locale.ENGLISH);
                List<String> list = headers.get(key);
                if(list == null) {
                    headers.put(key, list = new ArrayList<String>());
                }
                list.add(current.toString().trim());
                parseState--;
                current.setLength(0);
                return;
            } else {
                current.append((char) b);
            }
        }
    }

    private void parseHeaderName(final ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            byte b = buffer.get();
            if (b == '\r' || b == '\n') {
                if (current.length() > 2) {
                    throw new IOException("Invalid response");
                } else if (current.length() == 2) {
                    //the first /r was consumed by the previous line
                    if (current.charAt(0) == '\n' &&
                            current.charAt(1) == '\r'
                            && b == '\n') {
                        parseState = COMPLETE;
                        return;
                    }
                    throw new IOException("Invalid response");
                }
                current.append((char) b);
            } else if (b == ':') {
                headerName = current.toString().trim();
                parseState++;
                current.setLength(0);
                return;
            } else {
                current.append((char) b);
            }
        }
    }

    private void parseMessage(final ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            byte b = buffer.get();
            if (b == '\r' || b == '\n') {
                message = current.toString().trim();
                parseState++;
                current.setLength(0);
                return;
            } else {
                current.append((char) b);
            }
        }
    }

    private void parseStatusCode(final ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            byte b = buffer.get();
            if (b == ' ' || b == '\t') {
                responseCode = Integer.parseInt(current.toString().trim());
                parseState++;
                current.setLength(0);
                return;
            } else if (Character.isDigit(b)) {
                current.append((char) b);
            } else {
                throw new IOException("Invalid response");
            }
        }
    }

    private void parseVersion(final ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            byte b = buffer.get();
            if (b == ' ' || b == '\t') {
                httpVersion = current.toString().trim();
                parseState++;
                current.setLength(0);
                return;
            } else if(Character.isDigit(b) || Character.isAlphabetic(b) || b == '.' || b == '/') {
                current.append((char) b);
            } else {
                throw new IOException("Invalid response");
            }
        }
    }


    boolean isComplete() {
        return parseState == COMPLETE;
    }

    public String getHttpVersion() {
        return httpVersion;
    }

    public int getResponseCode() {
        return responseCode;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }
}
