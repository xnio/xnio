/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2022 Red Hat, Inc. and/or its affiliates, and individual
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

package org.xnio.mock;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;

import org.xnio.Buffers;

/**
 * Mock of a connected stream channel.<p>
 * This channel mock will store everything that is written to it for later comparison, and allows feeding of bytes for
 * reading.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
public class ReadableByteChannelMock implements ReadableByteChannel {

    // read stuff will be taken from this buffer
    protected ByteBuffer readBuffer = ByteBuffer.allocate(10000);
    // read stuff can only be read if read is enabled
    protected boolean readEnabled;
    // indicates if this channel is closed
    protected boolean closed = false;
    protected boolean checkClosed = true;
    protected boolean eof = false;

    /**
     * Feeds {@code readData} to read clients.
     * @param readData data that will be available for reading on this channel mock
     */
    public void setReadData(String... readData) {
        doSetReadData(readData);
    }

    protected synchronized int doSetReadData(String... readData) {
        int totalLength = 0;
        for (String data: readData) {
            totalLength += data.length();
        }
        int position = readBuffer.position();
        boolean resetPosition = false;
        if (!readBuffer.hasRemaining()) {
            readBuffer.compact();
        } else if(readBuffer.position() > 0 || readBuffer.limit() != readBuffer.capacity()) {
            if (readBuffer.capacity() - readBuffer.limit() < totalLength) {
                if (readBuffer.position() > 0 && readBuffer.capacity() - readBuffer.limit() + readBuffer.position() >= totalLength) {
                    readBuffer.compact();
                }
                throw new RuntimeException("ReadBuffer is full - not enough space to add more read data");
            }
            int limit = readBuffer.limit();
            readBuffer.position(limit);
            readBuffer.limit(limit + totalLength);
            resetPosition = true;
        }
        for (String data: readData) {
            readBuffer.put(data.getBytes(StandardCharsets.UTF_8));
        }
        readBuffer.flip();
        if (resetPosition) {
            readBuffer.position(position);
        }
        return totalLength;
    }

    /**
     * Feeds {@code readData} to read clients.
     * @param readData data that will be available for reading on this channel mock
     */
    public void setReadDataWithLength(String... readData) {
        doSetReadDataWithLength(readData);
    }

    protected synchronized int doSetReadDataWithLength(String... readData) {
        if (eof) {
            throw new IllegalStateException("Cannot add read data once eof is set");
        }
        int totalLength = 0;
        for (String data: readData) {
            totalLength += data.length();
        }
        int position = readBuffer.position();
        boolean resetPosition = false;
        if (!readBuffer.hasRemaining()) {
            readBuffer.compact();
        } else if(readBuffer.position() > 0 || readBuffer.limit() != readBuffer.capacity()) {
            if (readBuffer.capacity() - readBuffer.limit() + 4 < totalLength) {
                if (readBuffer.position() > 0 && readBuffer.capacity() - readBuffer.limit() + readBuffer.position() + 4 >= totalLength) {
                    readBuffer.compact();
                }
                throw new RuntimeException("ReadBuffer is full - not enough space to add more read data");
            }
            int limit = readBuffer.limit();
            readBuffer.position(limit);
            readBuffer.limit(limit + totalLength + 4);
            resetPosition = true;
        }
        readBuffer.putInt(totalLength);
        for (String data: readData) {
            readBuffer.put(data.getBytes(StandardCharsets.UTF_8));
        }
        readBuffer.flip();
        if (resetPosition) {
            readBuffer.position(position);
        }
        return totalLength;
    }

    /**
     * Feeds {@code readData} to read clients.
     * @param readData data that will be available for reading on this channel mock
     */
    public void setReadDataWithLength(int length, String... readData) {
        doSetReadDataWithLength(length, readData);
    }

    protected synchronized int doSetReadDataWithLength(int length, String... readData) {
        if (eof) {
            throw new IllegalStateException("Cannot add read data once eof is set");
        }
        int totalLength = 0;
        for (String data: readData) {
            totalLength += data.length();
        }
        int position = readBuffer.position();
        boolean resetPosition = false;
        if (!readBuffer.hasRemaining()) {
            readBuffer.compact();
        } else if(readBuffer.position() > 0 || readBuffer.limit() != readBuffer.capacity()) {
            if (readBuffer.capacity() - readBuffer.limit() + 4 < totalLength) {
                if (readBuffer.position() > 0 && readBuffer.capacity() - readBuffer.limit() + readBuffer.position() + 4 >= totalLength) {
                    readBuffer.compact();
                }
                throw new RuntimeException("ReadBuffer is full - not enough space to add more read data");
            }
            int limit = readBuffer.limit();
            readBuffer.position(limit);
            readBuffer.limit(limit + totalLength + 4);
            resetPosition = true;
        }
        readBuffer.putInt(length);
        for (String data: readData) {
            readBuffer.put(data.getBytes(StandardCharsets.UTF_8));
        }
        readBuffer.flip();
        if (resetPosition) {
            readBuffer.position(position);
        }
        return totalLength;
    }

    public synchronized void setEof() {
        eof = true;
    }

    public synchronized void enableRead(boolean enable) {
        readEnabled = enable;
    }

    public synchronized void enableClosedCheck(boolean enable) {
        checkClosed = enable;
    }

    @Override
    public void close() throws IOException {
        closed = true;
    }

    @Override
    public boolean isOpen() {
        return !closed;
    }

    @Override
    public synchronized int read(ByteBuffer dst) throws IOException {
        if (closed && checkClosed) {
            throw new ClosedChannelException();
        }
        if (readEnabled) {
            try {
                if ((!readBuffer.hasRemaining() || readBuffer.position() == 0 && readBuffer.limit() == readBuffer.capacity()) && eof) {
                    return -1;
                }
                if (readBuffer.limit() == readBuffer.capacity() && readBuffer.position() == 0) {
                    return 0;
                }
                return Buffers.copy(dst, readBuffer);
            } catch (RuntimeException e) {
                System.out.println("Got exception at attempt of copying contents of dst "+ dst.remaining()  +  " into read buffer " + readBuffer.remaining());
                throw e;
            }
        }
        return 0;
    }
}
