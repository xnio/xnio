/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2010 Red Hat, Inc. and/or its affiliates, and individual
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

import static org.xnio._private.Messages.msg;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.PipedInputStream;

/**
 * An in-VM pipe between an input stream and an output stream, which does not suffer from the
 * bugs in {@link PipedInputStream}.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class Pipe {
    private final Object lock = new Object();
    /** the point at which a read shall occur **/
    private int tail;
    /** the size of the buffer content **/
    private int size;
    private final byte[] buffer;
    private boolean writeClosed;
    private boolean readClosed;

    /**
     * Construct a new instance.
     *
     * @param bufferSize the buffer size to use
     */
    public Pipe(int bufferSize) {
        buffer = new byte[bufferSize];
    }

    /**
     * Wait for the read side to close.  Used when the writer needs to know when
     * the reader finishes consuming a message.
     */
    public void await() {
        boolean intr = false;
        final Object lock = this.lock;
        try {
            synchronized (lock) {
                while (! readClosed) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        intr = true;
                    }
                }
            }
        } finally {
            if (intr) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private final InputStream in = new InputStream() {
        public int read() throws IOException {
            final Object lock = Pipe.this.lock;
            synchronized (lock) {
                if (writeClosed && size == 0) {
                    return -1;
                }
                while (size == 0) {
                    try {
                        lock.wait();
                        if (writeClosed && size == 0) {
                            return -1;
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw msg.interruptedIO();
                    }
                }
                lock.notifyAll();
                int tail= Pipe.this.tail;
                try {
                    return buffer[tail++] & 0xff;
                } finally {
                    Pipe.this.tail = tail == buffer.length ? 0 : tail;
                    size--;
                }
            }
        }

        public int read(final byte[] b, final int off, final int len) throws IOException {
            final Object lock = Pipe.this.lock;
            synchronized (lock) {
                if (writeClosed && size == 0) {
                    return -1;
                }
                if (len == 0) {
                    return 0;
                }
                int size;
                while ((size = Pipe.this.size) == 0) {
                    try {
                        lock.wait();
                        if (writeClosed && (size = Pipe.this.size) == 0) {
                            return -1;
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw msg.interruptedIO();
                    }
                }
                final byte[] buffer = Pipe.this.buffer;
                final int bufLen = buffer.length;
                int cnt;
                int tail = Pipe.this.tail;
                if (size + tail > bufLen) {
                    // wrapped
                    final int lastLen = bufLen - tail;
                    if (lastLen < len) {
                        final int firstLen = tail + size - bufLen;
                        System.arraycopy(buffer, tail, b, off, lastLen);
                        int rem = Math.min(len - lastLen, firstLen);
                        System.arraycopy(buffer, 0, b, off + lastLen, rem);
                        cnt = rem + lastLen;
                    } else {
                        System.arraycopy(buffer, tail, b, off, len);
                        cnt = len;
                    }
                } else {
                    // not wrapped
                    cnt = Math.min(len, size);
                    System.arraycopy(buffer, tail, b, off, cnt);
                }
                tail += cnt;
                size -= cnt;
                Pipe.this.tail = tail >= bufLen ? tail - bufLen : tail;
                Pipe.this.size = size;
                lock.notifyAll();
                return cnt;
            }
        }

        public void close() throws IOException {
            final Object lock = Pipe.this.lock;
            synchronized (lock) {
                writeClosed = true;
                readClosed = true;
                // closing the read side drops the remaining bytes
                size = 0;
                lock.notifyAll();
                return;
            }
        }

        public String toString() {
            return "Pipe read half";
        }
    };
    private final OutputStream out = new OutputStream() {
        public void write(final int b) throws IOException {
            final Object lock = Pipe.this.lock;
            synchronized (lock) {
                if (writeClosed) {
                    throw msg.streamClosed();
                }
                final byte[] buffer = Pipe.this.buffer;
                final int bufLen = buffer.length;
                while (size == bufLen) {
                    try {
                        lock.wait();
                        if (writeClosed) {
                            throw msg.streamClosed();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw msg.interruptedIO();
                    }
                }
                final int tail = Pipe.this.tail;
                int startPos = tail + size;
                if (startPos >= bufLen) {
                    buffer[startPos - bufLen] = (byte) b;
                } else {
                    buffer[startPos] = (byte) b;
                }
                size ++;
                lock.notifyAll();
            }
        }

        public void write(final byte[] b, int off, final int len) throws IOException {
            int remaining = len;
            final Object lock = Pipe.this.lock;
            synchronized (lock) {
                if (writeClosed) {
                    throw msg.streamClosed();
                }
                final byte[] buffer = Pipe.this.buffer;
                final int bufLen = buffer.length;
                int size;
                int tail;
                int cnt;
                while (remaining > 0) {
                    while ((size = Pipe.this.size) == bufLen) {
                        try {
                            lock.wait();
                            if (writeClosed) {
                                throw msg.streamClosed();
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw msg.interruptedIO(len - remaining);
                        }
                    }
                    tail = Pipe.this.tail;
                    int startPos = tail + size;
                    if (startPos >= bufLen) {
                        // read wraps, write doesn't
                        startPos -= bufLen;
                        cnt = Math.min(remaining, bufLen - size);
                        System.arraycopy(b, off, buffer, startPos, cnt);
                        remaining -= cnt;
                        off += cnt;
                    } else {
                        // write wraps, read doesn't
                        final int firstPart = Math.min(remaining, bufLen - (tail + size));
                        System.arraycopy(b, off, buffer, startPos, firstPart);
                        off += firstPart;
                        remaining -= firstPart;
                        if (remaining > 0) {
                            final int latter = Math.min(remaining, tail);
                            System.arraycopy(b, off, buffer, 0, latter);
                            cnt = firstPart + latter;
                            off += latter;
                            remaining -= latter;
                        } else {
                            cnt = firstPart;
                        }
                    }
                    Pipe.this.size += cnt;
                    lock.notifyAll();
                }
            }
        }

        public void close() throws IOException {
            final Object lock = Pipe.this.lock;
            synchronized (lock) {
                writeClosed = true;
                lock.notifyAll();
                return;
            }
        }

        public String toString() {
            return "Pipe write half";
        }
    };

    /**
     * Get the input (read) side of the pipe.
     *
     * @return the input side
     */
    public InputStream getIn() {
        return in;
    }

    /**
     * Get the output (write) side of the pipe.
     *
     * @return the output side
     */
    public OutputStream getOut() {
        return out;
    }
}
