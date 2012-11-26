/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc., and individual contributors as indicated
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

package org.xnio.test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import junit.framework.TestCase;
import org.xnio.IoUtils;

/**
 *
 */
@SuppressWarnings({"unchecked"})
public final class IoUtilsTestCase extends TestCase {

    public void testDirectExecutor() {
        final Thread t = Thread.currentThread();
        final boolean ok[] = new boolean[1];
        IoUtils.directExecutor().execute(new Runnable() {
            public void run() {
                assertSame(t, Thread.currentThread());
                ok[0] = true;
            }
        });
        assertTrue(ok[0]);
    }

    public void testNullExecutor() {
        IoUtils.nullExecutor().execute(new Runnable() {
            public void run() {
                fail("null executor ran task");
            }
        });
    }

    public void testSafeClose() {
        IoUtils.safeClose(new Closeable() {
            public void close() throws IOException {
                throw new RuntimeException("This error should be consumed but logged");
            }
        });
        IoUtils.safeClose(new Closeable() {
            public void close() throws IOException {
                throw new Error("This error should be consumed but logged");
            }
        });
        IoUtils.safeClose(new Closeable() {
            public void close() throws IOException {
                throw new IOException("This error should be consumed but logged");
            }
        });
    }

    public void testTransferThroughBuffer() throws IOException{
        byte[] bytes = "This bytes".getBytes();
        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteBuffer buffer = ByteBuffer.allocate(16);
        assertEquals(bytes.length, IoUtils.transfer(Channels.newChannel(in), bytes.length, buffer, Channels.newChannel(out)));
        assertFalse(buffer.hasRemaining());
    }
}
