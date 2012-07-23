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
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.mock.ConnectedStreamChannelMock;

/**
 * Test for {@link ChannelInputStream}.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
public class ChannelInputStreamTestCase extends AbstractChannelInputStreamTest<ChannelInputStream> {

    @Test
    public void close() throws IOException {
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        final ChannelInputStream stream = new ChannelInputStream(channelMock);
        channelMock.setReadData("12345");
        channelMock.enableRead(true);
        assertEquals(0, stream.available());

        // close!
        stream.close();
        channelMock.setReadData("67890");
        assertEquals(0, stream.available());
        assertEquals(-1, stream.read());
        assertEquals(-1, stream.read(new byte[10]));
        assertEquals(0, stream.skip(3));
        // close is idempotent
        stream.close();
        assertEquals(0, stream.available());
        assertEquals(-1, stream.read());
        assertEquals(-1, stream.read(new byte[10]));
        assertEquals(0, stream.skip(3));
    }

    @Test
    public void skipBlocks() throws Exception {
        final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
        final ChannelInputStream stream = new ChannelInputStream(channelMock);
        channelMock.setReadData("skip all");
        channelMock.enableRead(false);
        SkipBytesTask skipTask = new SkipBytesTask(stream, 16);
        Thread skipBytesThread = new Thread(skipTask);
        skipBytesThread.start();
        skipBytesThread.join(200);
        assertTrue(skipBytesThread.isAlive());

        channelMock.enableRead(true);
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

    @Override
    protected ChannelInputStream createChannelInputStream(StreamSourceChannel sourceChannel, int internalBufferSize) {
        return new ChannelInputStream(sourceChannel);
    }

    @Override
    protected ChannelInputStream createChannelInputStream(StreamSourceChannel sourceChannel, long timeout, TimeUnit timeUnit, int internalBufferSize) {
        return new ChannelInputStream(sourceChannel, timeout, timeUnit);
    }

    @Override
    protected long getReadTimeout(ChannelInputStream stream, TimeUnit timeUnit) {
        return stream.getReadTimeout(timeUnit);
    }

    @Override
    protected void setReadTimeout(ChannelInputStream stream, int timeout, TimeUnit timeUnit) {
        stream.setReadTimeout(timeout, timeUnit);
    }

    @Override
    protected void assertAvailableBytes(ChannelInputStream stream, int availableInBuffer, int availableTotal) throws IOException {
        assertEquals(0, stream.available());
    }
}
