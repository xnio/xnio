/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2012 Red Hat, Inc. and/or its affiliates, and individual
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
        SkipBytesTask skipTask = new SkipBytesTask(stream, 16);
        Thread skipBytesThread = new Thread(skipTask);
        skipBytesThread.start();
        skipBytesThread.join(200);
        assertTrue(skipBytesThread.isAlive());

        channelMock.setReadData("moredataskip");
        skipBytesThread.join();
        assertEquals(16, skipTask.getSkipResult());
        // try again
        skipBytesThread = new Thread(skipTask);
        skipBytesThread.start();
        skipBytesThread.join(200);
        assertTrue(skipBytesThread.isAlive());

        channelMock.setReadData(" all this - data - and a little bit more");
        skipBytesThread.join();
        assertEquals(16, skipTask.getSkipResult());

        assertAvailableBytes(stream, 10, 28);
        assertEquals('d', stream.read());
        assertEquals('a', stream.read());
        assertEquals('t', stream.read());
        assertEquals('a', stream.read());

        // one more time
        skipTask = new SkipBytesTask(stream, 50);
        skipBytesThread = new Thread(skipTask);
        skipBytesThread.start();
        skipBytesThread.join(200);
        assertTrue(skipBytesThread.isAlive());

        channelMock.setEof();
        skipBytesThread.join();
        assertEquals(24, skipTask.getSkipResult());
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
