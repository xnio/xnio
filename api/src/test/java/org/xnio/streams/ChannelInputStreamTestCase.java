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
        skipBytesThread.join();
        assertEquals(8, skipTask.getSkipResult());

        channelMock.setReadData("skip all this - data");
        channelMock.setEof();

        // try again
        skipTask = new SkipBytesTask(stream, 16);
        skipBytesThread = new Thread(skipTask);
        skipBytesThread.start();
        skipBytesThread.join();
        assertEquals(16, skipTask.getSkipResult());

        assertEquals('d', stream.read());
        assertEquals('a', stream.read());
        assertEquals('t', stream.read());
        assertEquals('a', stream.read());
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
