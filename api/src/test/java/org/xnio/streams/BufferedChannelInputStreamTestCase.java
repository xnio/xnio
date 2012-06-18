/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.mock.ConnectedStreamChannelMock;

/**
 * Test for {@link BufferedChannelInputStream}.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
public class BufferedChannelInputStreamTestCase extends AbstractChannelInputStreamTest<BufferedChannelInputStream>{

    @Test
    public void illegalBufferSizeArgument() {
        final ConnectedStreamChannelMock sourceChannel = new ConnectedStreamChannelMock();
        IllegalArgumentException constructorException = null;
        // buffer sized 0
        try {
            new BufferedChannelInputStream(sourceChannel, 0);
        } catch (IllegalArgumentException e) {
            constructorException = e;
        }
        assertNotNull(constructorException);
        constructorException = null;
        try {
            new BufferedChannelInputStream(sourceChannel, 0, 100, TimeUnit.MILLISECONDS);
        } catch (IllegalArgumentException e) {
            constructorException = e;
        }
        assertNotNull(constructorException);
        constructorException = null;
        // buffer size < 0
        try {
            new BufferedChannelInputStream(sourceChannel, -2);
        } catch (IllegalArgumentException e) {
            constructorException = e;
        }
        assertNotNull(constructorException);
        constructorException = null;
        try {
            new BufferedChannelInputStream(sourceChannel, -5, 5000, TimeUnit.MICROSECONDS);
        } catch (IllegalArgumentException e) {
            constructorException = e;
        }
        assertNotNull(constructorException);
        constructorException = null;
    }

    @Test
    public void skipBlocks() throws Exception {
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        final BufferedChannelInputStream stream = createChannelInputStream(channelMock, 10);
        channelMock.setReadData("skip all");
        channelMock.enableRead(true);
        final SkipBytesTask skipTask = new SkipBytesTask(stream, 16);
        Thread skipBytesThread = new Thread(skipTask);
        skipBytesThread.start();
        skipBytesThread.join();
        assertEquals(8, skipTask.getSkipResult());
        // try again
        skipBytesThread = new Thread(skipTask);
        skipBytesThread.start();
        skipBytesThread.join(200);
        assertTrue(skipBytesThread.isAlive());

        channelMock.setReadData("skip all this - data");
        skipBytesThread.join();
        assertEquals(16, skipTask.getSkipResult());

        assertAvailableBytes(stream, 4, 4);
        assertEquals('d', stream.read());
        assertEquals('a', stream.read());
        assertEquals('t', stream.read());
        assertEquals('a', stream.read());
    }

    @Test
    public void availableThrowsIOException() throws IOException {
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        channelMock.close(); // channel mock will always throw ClosedChannelException
        final BufferedChannelInputStream stream = createChannelInputStream(channelMock, 10);
        // try to check availability, test twice to make sure that buffer is kept consistent
        ClosedChannelException expected = null;
        try {
            stream.available();
        } catch (ClosedChannelException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            stream.available();
        } catch (ClosedChannelException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    @Test
    public void close() throws IOException {
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        final BufferedChannelInputStream stream = createChannelInputStream(channelMock, 5);
        channelMock.setReadData("12345");
        channelMock.enableRead(true);
        assertAvailableBytes(stream, 5, 5);

        // close!
        stream.close();
        channelMock.setReadData("67890");
        assertAvailableBytes(stream, 0, 0);
        assertEquals(-1, stream.read());
        assertEquals(0, stream.skip(2));
        assertEquals(-1, stream.read());
        assertEquals(-1, stream.read(new byte[10]));
        assertEquals(0, stream.skip(3));
        // close is idempotent
        stream.close();
        assertAvailableBytes(stream, 0, 0);
        assertEquals(-1, stream.read());
        assertEquals(-1, stream.read(new byte[10]));
        assertEquals(0, stream.skip(3));
    }

    @Override
    protected BufferedChannelInputStream createChannelInputStream(StreamSourceChannel sourceChannel, int internalBufferSize) {
        return new BufferedChannelInputStream(sourceChannel, internalBufferSize);
    }

    @Override
    protected BufferedChannelInputStream createChannelInputStream(StreamSourceChannel sourceChannel, long timeout,
            TimeUnit timeUnit, int internalBufferSize) {
        return new BufferedChannelInputStream(sourceChannel, internalBufferSize, timeout, timeUnit);
    }

    @Override
    protected long getReadTimeout(BufferedChannelInputStream stream, TimeUnit timeUnit) {
        return stream.getReadTimeout(timeUnit);
    }

    @Override
    protected void setReadTimeout(BufferedChannelInputStream stream, int timeout, TimeUnit timeUnit) {
        stream.setReadTimeout(timeout, timeUnit);
    }

    @Override
    protected void assertAvailableBytes(BufferedChannelInputStream stream, int availableInBuffer, int availableTotal)
            throws IOException {
        assertEquals(availableInBuffer, stream.available());
    }
}
