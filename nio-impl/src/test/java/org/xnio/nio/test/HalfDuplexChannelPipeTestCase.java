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
package org.xnio.nio.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.xnio.Buffers;
import org.xnio.ChannelPipe;
import org.xnio.Option;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 * Tests a half duplex channel pipe.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 *
 */
public class HalfDuplexChannelPipeTestCase extends AbstractStreamSinkSourceChannelTest<StreamSinkChannel, StreamSourceChannel> {

    @Override
    protected void initChannels(XnioWorker xnioWorker, OptionMap optionMap,
            TestChannelListener<StreamSinkChannel> sinkChannelListener,
            TestChannelListener<StreamSourceChannel> sourceChannelListener) throws IOException {
        final ChannelPipe<StreamSourceChannel, StreamSinkChannel> pipeChannel = xnioWorker.createHalfDuplexPipe();
        sinkChannelListener.handleEvent(pipeChannel.getRightSide());
        sourceChannelListener.handleEvent(pipeChannel.getLeftSide());
    }

    @Before
    public void initChannels() throws IOException {
        super.initChannels();
    }

    @Test
    public void writeAndReadBufferAndClose() throws IOException {
        final TestChannelListener<StreamSinkChannel> writeListener = new TestChannelListener<StreamSinkChannel>();
        sinkChannel.getWriteSetter().set(writeListener);

        // Step 1: communicate several times using a ByteBuffer
        final ByteBuffer buffer = ByteBuffer.allocate(5);
        buffer.put("12345".getBytes()).flip();
        assertEquals(5, sinkChannel.write(buffer));
        buffer.clear();
        assertEquals(5, sourceChannel.read(buffer));
        buffer.flip();
        assertEquals("12345", Buffers.getModifiedUtf8(buffer));

        buffer.clear();
        buffer.put("67890".getBytes()).flip();
        assertEquals(5, sinkChannel.write(buffer));
        buffer.clear();
        assertEquals(5, sourceChannel.read(buffer));
        buffer.flip();
        assertEquals("67890", Buffers.getModifiedUtf8(buffer));

        assertFalse(sinkChannel.isWriteResumed());
        sinkChannel.resumeWrites();
        assertTrue(writeListener.isInvoked());

        // Step 2: close sinkChannel
        sinkChannel.close();
        assertFalse(sinkChannel.isWriteResumed());
        Exception expected = null;
        try {
            sinkChannel.write(buffer);
        } catch (ClosedChannelException e) {
            expected = e;
        }
        assertNotNull(expected);

        buffer.clear();
        assertEquals(-1, sourceChannel.read(buffer));

        // Step 3: close sourceChannel
        assertFalse(sinkChannel.isOpen());
        assertTrue(sourceChannel.isOpen());
        sourceChannel.close();
        assertFalse(sinkChannel.isOpen());
        assertFalse(sourceChannel.isOpen());
        assertFalse(sourceChannel.isReadResumed());

        expected = null;
        try {
            sinkChannel.write(buffer);
        } catch (ClosedChannelException e) {
            expected = e;
        }
        assertNotNull(expected);

        buffer.clear();
        assertEquals(-1, sourceChannel.read(buffer));

        // Step 4: idempotent close
        assertFalse(sinkChannel.isOpen());
        assertFalse(sourceChannel.isOpen());
        sourceChannel.close();
        sinkChannel.close();
        assertFalse(sinkChannel.isOpen());
        assertFalse(sourceChannel.isOpen());

        expected = null;
        try {
            sinkChannel.write(buffer);
        } catch (ClosedChannelException e) {
            expected = e;
        }
        assertNotNull(expected);

        buffer.clear();
        assertEquals(-1, sourceChannel.read(buffer));

        // Step 5: shutdown read and write, should be idempotent
        sourceChannel.shutdownReads();
        sinkChannel.shutdownWrites();
        assertFalse(sinkChannel.isOpen());
        assertFalse(sourceChannel.isOpen());

        expected = null;
        try {
            sinkChannel.write(buffer);
        } catch (ClosedChannelException e) {
            expected = e;
        }
        assertNotNull(expected);

        buffer.clear();
        assertEquals(-1, sourceChannel.read(buffer));
    }

    @Test
    public void writeAndReadMultipleBuffersAndShutdown() throws IOException {
        final TestChannelListener<StreamSinkChannel> writeListener = new TestChannelListener<StreamSinkChannel>();
        final TestChannelListener<StreamSourceChannel> readListener = new TestChannelListener<StreamSourceChannel>();
        sinkChannel.getWriteSetter().set(writeListener);
        sourceChannel.getReadSetter().set(readListener);

        // Step 1: communicate several times using a ByteBuffer[]
        final ByteBuffer[] buffers = new ByteBuffer[] {ByteBuffer.allocate(5), ByteBuffer.allocate(1), ByteBuffer.allocate(2)};
        buffers[0].put("12345".getBytes()).flip();
        buffers[1].put("6".getBytes()).flip();
        buffers[2].put("78".getBytes()).flip();
        assertEquals(8, sinkChannel.write(buffers));
        buffers[0].clear();
        buffers[1].clear();
        buffers[2].clear();

        assertEquals(8, sourceChannel.read(buffers));
        buffers[0].flip();
        assertEquals("12345", Buffers.getModifiedUtf8(buffers[0]));
        buffers[1].flip();
        assertEquals('6', buffers[1].get(0));
        buffers[2].flip();
        assertEquals("78", Buffers.getModifiedUtf8(buffers[2]));

        buffers[0].clear();
        buffers[0].put("09876".getBytes()).flip();
        buffers[1].clear();
        buffers[1].put("5".getBytes()).flip();
        buffers[2].clear();
        buffers[2].put("43".getBytes()).flip();
        assertEquals(8, sinkChannel.write(buffers));
        buffers[0].clear();
        buffers[1].clear();
        buffers[2].clear();
        assertEquals(8, sourceChannel.read(buffers));
        buffers[0].flip();
        assertEquals("09876", Buffers.getModifiedUtf8(buffers[0]));
        buffers[1].flip();
        assertEquals('5', buffers[1].get(0));
        buffers[2].flip();
        assertEquals("43", Buffers.getModifiedUtf8(buffers[2]));

        assertTrue(sinkChannel.flush());

        assertFalse(sinkChannel.isWriteResumed());
        sinkChannel.resumeWrites();
        assertFalse(sourceChannel.isReadResumed());
        sourceChannel.resumeReads();
        assertTrue(writeListener.isInvoked());

        // Step 2: shutdownReads on sourceChannel
        sourceChannel.shutdownReads();
        assertFalse(sourceChannel.isReadResumed());
        assertFalse(sourceChannel.isOpen());
        sourceChannel.awaitReadable();
        sourceChannel.awaitReadable(30, TimeUnit.SECONDS);

        IOException expected = null;
        try {
            sinkChannel.write(buffers);
        } catch (IOException e) { // broken pipe
            expected = e;
        }
        assertNotNull(expected);

        assertEquals(-1, sourceChannel.read(buffers));

        // Step 3: shutdownWrites on sinkChannel
        sinkChannel.shutdownWrites();
        assertFalse(sinkChannel.isWriteResumed());
        assertFalse(sinkChannel.isOpen());
        sinkChannel.awaitWritable();
        sinkChannel.awaitWritable(1, TimeUnit.DAYS);

        expected = null;
        try {
            sinkChannel.write(buffers);
        } catch (ClosedChannelException e) {
            expected = e;
        }
        assertNotNull(expected);

        assertEquals(-1, sourceChannel.read(ByteBuffer.allocate(5)));

        // Step 4: idempotent shutdown operations
        sourceChannel.shutdownReads();
        sinkChannel.shutdownWrites();
        assertFalse(sinkChannel.isOpen());
        assertFalse(sourceChannel.isOpen());

        expected = null;
        try {
            sinkChannel.write(buffers);
        } catch (ClosedChannelException e) {
            expected = e;
        }
        assertNotNull(expected);

        assertEquals(-1, sourceChannel.read(buffers));

        // Step 5: close channels, should be idempotent
        sinkChannel.close();
        sinkChannel.close();
        assertFalse(sinkChannel.isOpen());
        assertFalse(sourceChannel.isOpen());

        expected = null;
        try {
            sinkChannel.write(buffers);
        } catch (ClosedChannelException e) {
            expected = e;
        }
        assertNotNull(expected);

        assertEquals(-1, sourceChannel.read(buffers));
    }

    @Test
    public void closeShutdownChannel() throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocate(3);
        final TestChannelListener<StreamSinkChannel> writeListener = new TestChannelListener<StreamSinkChannel>();
        final TestChannelListener<StreamSourceChannel> readListener = new TestChannelListener<StreamSourceChannel>();
        sinkChannel.getWriteSetter().set(writeListener);
        sourceChannel.getReadSetter().set(readListener);

        assertFalse(sinkChannel.isWriteResumed());
        sinkChannel.resumeWrites();
        assertFalse(sourceChannel.isReadResumed());
        sourceChannel.resumeReads();
        assertTrue(writeListener.isInvoked());

        // Step 1: shutdownReads on sourceChannel
        sourceChannel.shutdownReads();
        assertFalse(sourceChannel.isReadResumed());
        sourceChannel.awaitReadable();
        sourceChannel.awaitReadable(10, TimeUnit.SECONDS);
        assertFalse(sourceChannel.isOpen());
        assertTrue(sinkChannel.isOpen());

        IOException expected = null;
        try {
            sinkChannel.write(buffer);
        } catch (IOException e) { // broken pipe
            expected = e;
        }
        assertNotNull(expected);

        buffer.flip();
        assertEquals(-1, sourceChannel.read(buffer));

        // Step 2: shutdownWrites on sinkChannel
        sinkChannel.shutdownWrites();
        assertFalse(sinkChannel.isWriteResumed());
        assertFalse(sinkChannel.isOpen());
        sinkChannel.awaitWritable();
        sinkChannel.awaitWritable(1, TimeUnit.MINUTES);

        expected = null;
        try {
            sinkChannel.write(buffer);
        } catch (ClosedChannelException e) {
            expected = e;
        }
        assertNotNull(expected);

        assertEquals(-1, sourceChannel.read(ByteBuffer.allocate(5)));

        // Step 3: close sinkChannel (idempotent)
        sinkChannel.close();
        assertFalse(sinkChannel.isOpen());
        assertFalse(sinkChannel.isWriteResumed());
        sinkChannel.awaitWritable();
        sinkChannel.awaitWritable(1, TimeUnit.MINUTES);

        buffer.flip();
        expected = null;
        try {
            sinkChannel.write(buffer);
        } catch (ClosedChannelException e) {
            expected = e;
        }
        assertNotNull(expected);

        assertEquals(-1, sourceChannel.read(buffer));

        // Step 4: close sourceChannel (idempotent)
        assertFalse(sourceChannel.isOpen());
        sourceChannel.close();
        assertFalse(sourceChannel.isOpen());
        assertFalse(sourceChannel.isReadResumed());
        sourceChannel.awaitReadable();
        sourceChannel.awaitReadable(10, TimeUnit.SECONDS);

        expected = null;
        try {
            sinkChannel.write(buffer);
        } catch (ClosedChannelException e) {
            expected = e;
        }
        assertNotNull(expected);

        assertEquals(-1, sourceChannel.read(buffer));

        // Step 5: idempotent shutdown and close operations
        sinkChannel.shutdownWrites();
        sourceChannel.shutdownReads();
        sinkChannel.close();
        sourceChannel.close();
        assertFalse(sinkChannel.isOpen());
        assertFalse(sourceChannel.isOpen());

        expected = null;
        try {
            sinkChannel.write(buffer);
        } catch (ClosedChannelException e) {
            expected = e;
        }
        assertNotNull(expected);

        assertEquals(-1, sourceChannel.read(buffer));
    }

    @Test
    public void sinkChannelOptionSetup() throws IOException {
        initChannels();
        final Option<?>[] unsupportedOptions = OptionHelper.getNotSupportedOptions(Options.WRITE_TIMEOUT);
        for (Option<?> option: unsupportedOptions) {
            assertFalse("Channel supports " + option, sinkChannel.supportsOption(option));
            assertNull("Expected null value for option " + option + " but got " + sinkChannel.getOption(option) +
                    " instead", sinkChannel.getOption(option));
        }

        assertTrue(sinkChannel.supportsOption(Options.WRITE_TIMEOUT));

        sinkChannel.setOption(Options.WRITE_TIMEOUT, 1301093);
        assertNull("Unexpected option value: " + sinkChannel.getOption(Options.MAX_INBOUND_MESSAGE_SIZE),
                sinkChannel.setOption(Options.MAX_INBOUND_MESSAGE_SIZE, 50000));// unsupported

        assertEquals(1301093, (int) sinkChannel.getOption(Options.WRITE_TIMEOUT));
        assertNull(sinkChannel.getOption(Options.MAX_INBOUND_MESSAGE_SIZE));// unsupported
        assertEquals(1301093, (int) sinkChannel.setOption(Options.WRITE_TIMEOUT, 293265));
        assertEquals(293265, (int) sinkChannel.getOption(Options.WRITE_TIMEOUT));

        // TODO XNIO-171 test setOption(*, null)?
    }

    @Test
    public void sourceChannelOptionSetup() throws IOException {
        initChannels();
        final Option<?>[] unsupportedOptions = OptionHelper.getNotSupportedOptions(Options.READ_TIMEOUT);
        for (Option<?> option: unsupportedOptions) {
            assertFalse("Channel supports " + option, sourceChannel.supportsOption(option));
            assertNull("Expected null value for option " + option + " but got " + sourceChannel.getOption(option) +
                    " instead", sourceChannel.getOption(option));
        }

        assertTrue(sourceChannel.supportsOption(Options.READ_TIMEOUT));

        sourceChannel.setOption(Options.READ_TIMEOUT, 293265);
        assertNull("Unexpected option value: " + sourceChannel.getOption(Options.MAX_INBOUND_MESSAGE_SIZE),
                sourceChannel.setOption(Options.MAX_OUTBOUND_MESSAGE_SIZE, 50000));// unsupported

        assertEquals(293265, (int) sourceChannel.getOption(Options.READ_TIMEOUT));
        assertNull(sourceChannel.getOption(Options.MAX_OUTBOUND_MESSAGE_SIZE));// unsupported
        assertEquals(293265, (int) sourceChannel.setOption(Options.READ_TIMEOUT, 1301093));
        assertEquals(1301093, (int) sourceChannel.getOption(Options.READ_TIMEOUT));

        // TODO XNIO-171 test setOption(*, null)?
    }
}
