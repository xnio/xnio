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

package org.jboss.xnio.test;

import junit.framework.TestCase;
import org.jboss.xnio.ByteBufferInputStream;
import static org.jboss.xnio.IoUtils.safeClose;
import static org.jboss.xnio.Buffers.flip;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.Arrays;

/**
 *
 */
public final class ByteBufferInputStreamTestCase extends TestCase {
    public void testRandom() throws Exception {
        final byte[] bytes = new byte[400];
        final Random random = new Random();
        random.nextBytes(bytes);
        final ByteBuffer buf = ByteBuffer.allocate(400);
        final ByteBufferInputStream stream = new ByteBufferInputStream(flip(buf.put(bytes)));
        try {
            final byte[] target = new byte[400];
            assertEquals(stream.available(), 400);
            stream.read(target);
            assertTrue(Arrays.equals(bytes, target));
            stream.close();
        } finally {
            safeClose(stream);
        }
    }
}
