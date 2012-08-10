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
import org.xnio.channels.StreamChannel;

/**
 * Tests a full duplex channel pipe.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 *
 */
public class FullDuplexChannelPipeTestCase extends AbstractNioStreamChannelTest {

    // for a matter of code readability, it is better to name those channels channel1/channel2 in the super class
    // and create new fields in this subclass so we can use a more appropriate name here
    private StreamChannel leftChannel;
    private StreamChannel rightChannel;

    @Before
    public void initChannels() throws IOException {
        super.initChannels();
    }

    @Override
    protected void initChannels(XnioWorker xnioWorker, OptionMap optionMap, TestChannelListener<StreamChannel> leftChannelListener,
            TestChannelListener<StreamChannel> rightChannelListener) throws IOException { 
        if (leftChannel != null) {
            closeChannels();
        }
        final ChannelPipe<StreamChannel, StreamChannel> pipeChannel = xnioWorker.createFullDuplexPipe();
        assertNotNull(pipeChannel);
        assertNotNull(pipeChannel.toString());
        leftChannel = pipeChannel.getLeftSide();
        rightChannel = pipeChannel.getRightSide();
        assertNotNull(leftChannel);
        assertNotNull(leftChannel.toString());
        assertNotNull(rightChannel);
        assertNotNull(rightChannel.toString());
        leftChannelListener.handleEvent(leftChannel);
        rightChannelListener.handleEvent(rightChannel);
    }

    @Test
    public void writeAndReadBufferAndClose() throws IOException {
        final TestChannelListener<StreamChannel> leftWriteListener = new TestChannelListener<StreamChannel>();
        final TestChannelListener<StreamChannel> rightWriteListener = new TestChannelListener<StreamChannel>();
        leftChannel.getWriteSetter().set(leftWriteListener);
        rightChannel.getWriteSetter().set(rightWriteListener);

        // Step 1: communicate several times using a ByteBuffer
        final ByteBuffer buffer = ByteBuffer.allocate(5);
        buffer.put("12345".getBytes()).flip();
        assertEquals(5, leftChannel.write(buffer));
        buffer.clear();
        assertEquals(5, rightChannel.read(buffer));
        buffer.flip();
        assertEquals("12345", Buffers.getModifiedUtf8(buffer));

        buffer.clear();
        buffer.put("67890".getBytes()).flip();
        assertEquals(5, rightChannel.write(buffer));
        buffer.clear();
        assertEquals(5, leftChannel.read(buffer));
        buffer.flip();
        assertEquals("67890", Buffers.getModifiedUtf8(buffer));

        assertFalse(leftChannel.isWriteResumed());
        leftChannel.resumeWrites();
        assertFalse(rightChannel.isWriteResumed());
        rightChannel.resumeWrites();
        assertFalse(leftChannel.isReadResumed());
        leftChannel.resumeReads();
        assertFalse(rightChannel.isReadResumed());
        rightChannel.resumeReads();
        assertTrue(leftWriteListener.isInvoked());
        assertTrue(rightWriteListener.isInvoked());

        // Step 2: close leftChannel
        leftChannel.close();
        assertFalse(leftChannel.isWriteResumed());
        assertFalse(leftChannel.isReadResumed());
        Exception expected = null;
        try {
            leftChannel.write(buffer);
        } catch (ClosedChannelException e) {
            expected = e;
        }
        assertNotNull(expected);

        assertEquals(0, rightChannel.write(buffer));
        buffer.flip();

        expected = null;
        try {
            rightChannel.write(buffer);
        } catch(IOException e) { // broken pipe
            expected = e;
        }
        assertNotNull(expected);

        buffer.clear();
        assertEquals(-1, leftChannel.read(buffer));
        assertEquals(-1, rightChannel.read(buffer));

        // Step 3: close rightChannel
        assertFalse(leftChannel.isOpen());
        assertTrue(rightChannel.isOpen());
        rightChannel.close();
        assertFalse(leftChannel.isOpen());
        assertFalse(rightChannel.isOpen());
        assertFalse(rightChannel.isWriteResumed());
        assertFalse(rightChannel.isReadResumed());

        expected = null;
        try {
            leftChannel.write(buffer);
        } catch (ClosedChannelException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            rightChannel.write(buffer);
        } catch (ClosedChannelException e) {
            expected = e;
        }
        assertNotNull(expected);

        buffer.clear();
        assertEquals(-1, leftChannel.read(buffer));
        assertEquals(-1, rightChannel.read(buffer));

        // Step 4: idempotent close
        assertFalse(leftChannel.isOpen());
        assertFalse(rightChannel.isOpen());
        rightChannel.close();
        leftChannel.close();
        assertFalse(leftChannel.isOpen());
        assertFalse(rightChannel.isOpen());

        expected = null;
        try {
            leftChannel.write(buffer);
        } catch (ClosedChannelException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            rightChannel.write(buffer);
        } catch (ClosedChannelException e) {
            expected = e;
        }
        assertNotNull(expected);

        buffer.clear();
        assertEquals(-1, leftChannel.read(buffer));
        assertEquals(-1, rightChannel.read(buffer));

        // Step 5: shutdown read and write, should be idempotent
        leftChannel.shutdownReads();
        leftChannel.shutdownWrites();
        rightChannel.shutdownReads();
        rightChannel.shutdownWrites();
        assertFalse(leftChannel.isOpen());
        assertFalse(rightChannel.isOpen());

        expected = null;
        try {
            leftChannel.write(buffer);
        } catch (ClosedChannelException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            rightChannel.write(buffer);
        } catch (ClosedChannelException e) {
            expected = e;
        }
        assertNotNull(expected);

        buffer.clear();
        assertEquals(-1, leftChannel.read(buffer));
        assertEquals(-1, rightChannel.read(buffer));
    }

    @Test
    public void writeAndReadMultipleBuffersAndShutdown() throws IOException {
        final TestChannelListener<StreamChannel> leftWriteListener = new TestChannelListener<StreamChannel>();
        final TestChannelListener<StreamChannel> rightWriteListener = new TestChannelListener<StreamChannel>();
        final TestChannelListener<StreamChannel> leftReadListener = new TestChannelListener<StreamChannel>();
        final TestChannelListener<StreamChannel> rightReadListener = new TestChannelListener<StreamChannel>();
        leftChannel.getWriteSetter().set(leftWriteListener);
        rightChannel.getWriteSetter().set(rightWriteListener);
        leftChannel.getReadSetter().set(leftReadListener);
        rightChannel.getReadSetter().set(rightReadListener);

        // Step 1: communicate several times using a ByteBuffer[]
        final ByteBuffer[] buffers = new ByteBuffer[] {ByteBuffer.allocate(5), ByteBuffer.allocate(1), ByteBuffer.allocate(2)};
        buffers[0].put("12345".getBytes()).flip();
        buffers[1].put("6".getBytes()).flip();
        buffers[2].put("78".getBytes()).flip();
        assertEquals(8, leftChannel.write(buffers));
        buffers[0].clear();
        buffers[1].clear();
        buffers[2].clear();

        assertEquals(8, rightChannel.read(buffers));
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
        assertEquals(8, rightChannel.write(buffers));
        buffers[0].clear();
        buffers[1].clear();
        buffers[2].clear();
        assertEquals(8, leftChannel.read(buffers));
        buffers[0].flip();
        assertEquals("09876", Buffers.getModifiedUtf8(buffers[0]));
        buffers[1].flip();
        assertEquals('5', buffers[1].get(0));
        buffers[2].flip();
        assertEquals("43", Buffers.getModifiedUtf8(buffers[2]));

        assertFalse(leftChannel.isWriteResumed());
        leftChannel.resumeWrites();
        assertFalse(rightChannel.isWriteResumed());
        rightChannel.resumeWrites();
        assertFalse(leftChannel.isReadResumed());
        leftChannel.resumeReads();
        assertFalse(rightChannel.isReadResumed());
        rightChannel.resumeReads();
        assertTrue(leftWriteListener.isInvoked());
        assertTrue(rightWriteListener.isInvoked());

        // Step 2: shutdownReads on rightChannel
        rightChannel.shutdownReads();
        assertFalse(rightChannel.isReadResumed());
        assertTrue(rightChannel.isWriteResumed());
        assertTrue(leftChannel.isOpen());
        assertTrue(rightChannel.isOpen());
        rightChannel.awaitReadable();
        rightChannel.awaitReadable(30, TimeUnit.SECONDS);

        assertEquals(1, rightChannel.write(buffers));
        assertEquals(-1, rightChannel.read(buffers));

        buffers[0].flip();
        buffers[1].flip();
        buffers[2].flip();
        Exception expected = null;
        try {
            assertEquals(1, leftChannel.write(buffers));
        } catch (IOException e) { // broken pipe
            expected = e;
        }
        assertNotNull(expected);
        buffers[0].clear();
        buffers[1].clear();
        buffers[2].clear();
        assertEquals(1, leftChannel.read(buffers));
        buffers[0].flip();
        assertEquals('5', buffers[0].get(0));

        // Step 3: shutdownWrites on leftChannel
        leftChannel.shutdownWrites();
        assertTrue(leftChannel.isReadResumed());
        assertFalse(leftChannel.isWriteResumed());
        assertTrue(leftChannel.isOpen());
        leftChannel.awaitWritable();
        leftChannel.awaitWritable(1, TimeUnit.DAYS);

        expected = null;
        try {
            leftChannel.write(buffers);
        } catch (ClosedChannelException e) {
            expected = e;
        }
        assertNotNull(expected);

        assertEquals(4, rightChannel.write(buffers));
        buffers[0].clear();
        buffers[1].clear();
        buffers[2].clear();

        assertEquals(4, leftChannel.read(buffers));
        buffers[0].flip();
        assertEquals("5543", Buffers.getModifiedUtf8(buffers[0]));
        buffers[1].flip();
        assertFalse(buffers[1].hasRemaining());
        buffers[2].flip();
        assertFalse(buffers[2].hasRemaining());

        assertEquals(-1, rightChannel.read(ByteBuffer.allocate(5)));

        // Step 4: shutdownWrites on rightChannel
        rightChannel.shutdownWrites();
        assertFalse(rightChannel.isOpen());
        assertFalse(rightChannel.isReadResumed());
        assertFalse(rightChannel.isWriteResumed());
        rightChannel.awaitWritable();
        rightChannel.awaitWritable(1, TimeUnit.DAYS);

        buffers[0].flip();
        expected = null;
        try {
            rightChannel.write(buffers);
        } catch (ClosedChannelException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            leftChannel.write(buffers);
        } catch (ClosedChannelException e) {
            expected = e;
        }
        assertNotNull(expected);

        buffers[0].clear();
        assertEquals(-1, rightChannel.read(buffers));
        assertEquals(-1, leftChannel.read(buffers));

        // Step 5: shutdownReads on leftChannel
        assertTrue(leftChannel.isOpen());
        leftChannel.shutdownReads();
        assertFalse(leftChannel.isOpen());
        assertFalse(leftChannel.isReadResumed());
        assertFalse(leftChannel.isWriteResumed());
        leftChannel.awaitReadable();
        leftChannel.awaitReadable(30, TimeUnit.SECONDS);

        expected = null;
        try {
            leftChannel.write(buffers);
        } catch (ClosedChannelException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            rightChannel.write(buffers);
        } catch (ClosedChannelException e) {
            expected = e;
        }
        assertNotNull(expected);

        assertEquals(-1, leftChannel.read(buffers));
        assertEquals(-1, rightChannel.read(buffers));

        // Step 6: idempotent shutdown operations
        leftChannel.shutdownReads();
        leftChannel.shutdownWrites();
        rightChannel.shutdownReads();
        rightChannel.shutdownWrites();
        assertFalse(leftChannel.isOpen());
        assertFalse(rightChannel.isOpen());

        expected = null;
        try {
            rightChannel.write(buffers);
        } catch (ClosedChannelException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            leftChannel.write(buffers);
        } catch (ClosedChannelException e) {
            expected = e;
        }
        assertNotNull(expected);

        assertEquals(-1, rightChannel.read(buffers));
        assertEquals(-1, leftChannel.read(buffers));

        // Step 7: close channels, should be idempotent
        leftChannel.close();
        rightChannel.close();
        assertFalse(leftChannel.isOpen());
        assertFalse(rightChannel.isOpen());

        expected = null;
        try {
            rightChannel.write(buffers);
        } catch (ClosedChannelException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            leftChannel.write(buffers);
        } catch (ClosedChannelException e) {
            expected = e;
        }
        assertNotNull(expected);

        assertEquals(-1, rightChannel.read(buffers));
        assertEquals(-1, leftChannel.read(buffers));
    }

    @Test
    public void closePartiallyShutdownChannel() throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocate(3);
        final TestChannelListener<StreamChannel> leftWriteListener = new TestChannelListener<StreamChannel>();
        final TestChannelListener<StreamChannel> rightWriteListener = new TestChannelListener<StreamChannel>();
        final TestChannelListener<StreamChannel> leftReadListener = new TestChannelListener<StreamChannel>();
        final TestChannelListener<StreamChannel> rightReadListener = new TestChannelListener<StreamChannel>();
        leftChannel.getWriteSetter().set(leftWriteListener);
        rightChannel.getWriteSetter().set(rightWriteListener);
        leftChannel.getReadSetter().set(leftReadListener);
        rightChannel.getReadSetter().set(rightReadListener);

        assertFalse(leftChannel.isWriteResumed());
        leftChannel.resumeWrites();
        assertFalse(rightChannel.isWriteResumed());
        rightChannel.resumeWrites();
        assertFalse(leftChannel.isReadResumed());
        leftChannel.resumeReads();
        assertFalse(rightChannel.isReadResumed());
        rightChannel.resumeReads();
        assertTrue(leftWriteListener.isInvoked());
        assertTrue(rightWriteListener.isInvoked());

        // Step 1: shutdownReads on leftChannel
        leftChannel.shutdownReads();
        assertFalse(leftChannel.isReadResumed());
        leftChannel.awaitReadable();
        leftChannel.awaitReadable(10, TimeUnit.SECONDS);
        assertTrue(leftChannel.isWriteResumed());
        assertTrue(leftChannel.isOpen());
        assertTrue(rightChannel.isOpen());

        assertEquals(3, leftChannel.write(buffer));
        buffer.flip();
        assertEquals(3, rightChannel.read(buffer));
        buffer.flip();
        Exception expected = null;
        try {
            rightChannel.write(buffer);
        } catch (IOException e) { // broken pipe
            expected = e;
        }
        assertNotNull(expected);

        buffer.clear();
        assertEquals(-1, leftChannel.read(buffer));

        // Step 2: shutdownWrites on rightChannel
        rightChannel.shutdownWrites();
        assertTrue(rightChannel.isReadResumed());
        assertFalse(rightChannel.isWriteResumed());
        assertTrue(rightChannel.isOpen());
        rightChannel.awaitWritable();
        rightChannel.awaitWritable(1, TimeUnit.MINUTES);

        expected = null;
        try {
            rightChannel.write(buffer);
        } catch (ClosedChannelException e) {
            expected = e;
        }
        assertNotNull(expected);

        assertEquals(3, leftChannel.write(buffer));
        buffer.clear();

        assertEquals(3, rightChannel.read(buffer));

        assertEquals(-1, leftChannel.read(ByteBuffer.allocate(5)));

        // Step 3: close leftChannel
        leftChannel.close();
        assertFalse(leftChannel.isOpen());
        assertFalse(leftChannel.isReadResumed());
        assertFalse(leftChannel.isWriteResumed());
        leftChannel.awaitReadable();
        leftChannel.awaitReadable(10, TimeUnit.SECONDS);
        leftChannel.awaitWritable();
        leftChannel.awaitWritable(1, TimeUnit.MINUTES);

        buffer.flip();
        expected = null;
        try {
            leftChannel.write(buffer);
        } catch (ClosedChannelException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            rightChannel.write(buffer);
        } catch (ClosedChannelException e) {
            expected = e;
        }
        assertNotNull(expected);

        assertEquals(-1, rightChannel.read(buffer));
        assertEquals(-1, leftChannel.read(buffer));

        // Step 4: close rightChannel
        assertTrue(rightChannel.isOpen());
        rightChannel.close();
        assertFalse(rightChannel.isOpen());
        assertFalse(rightChannel.isReadResumed());
        assertFalse(rightChannel.isWriteResumed());
        rightChannel.awaitReadable();
        rightChannel.awaitReadable(10, TimeUnit.SECONDS);
        rightChannel.awaitWritable();
        rightChannel.awaitWritable(1, TimeUnit.MINUTES);

        expected = null;
        try {
            leftChannel.write(buffer);
        } catch (ClosedChannelException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            rightChannel.write(buffer);
        } catch (ClosedChannelException e) {
            expected = e;
        }
        assertNotNull(expected);

        assertEquals(-1, leftChannel.read(buffer));
        assertEquals(-1, rightChannel.read(buffer));

        // Step 5: idempotent shutdown and close operations
        leftChannel.shutdownReads();
        leftChannel.shutdownWrites();
        rightChannel.shutdownReads();
        rightChannel.shutdownWrites();
        leftChannel.close();
        rightChannel.close();
        assertFalse(leftChannel.isOpen());
        assertFalse(rightChannel.isOpen());

        expected = null;
        try {
            rightChannel.write(buffer);
        } catch (ClosedChannelException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            leftChannel.write(buffer);
        } catch (ClosedChannelException e) {
            expected = e;
        }
        assertNotNull(expected);

        assertEquals(-1, rightChannel.read(buffer));
        assertEquals(-1, leftChannel.read(buffer));
    }

    @Test
    public void optionSetup() throws IOException {
        initChannels();
        final Option<?>[] unsupportedOptions = OptionHelper.getNotSupportedOptions(Options.READ_TIMEOUT,
                Options.WRITE_TIMEOUT);
        for (Option<?> option: unsupportedOptions) {
            assertFalse("Channel supports " + option, leftChannel.supportsOption(option));
            assertNull("Expected null value for option " + option + " but got " + leftChannel.getOption(option) +
                    " instead", leftChannel.getOption(option));
        }

        assertTrue(leftChannel.supportsOption(Options.READ_TIMEOUT));
        assertTrue(leftChannel.supportsOption(Options.WRITE_TIMEOUT));

        leftChannel.setOption(Options.READ_TIMEOUT, 39710);
        leftChannel.setOption(Options.WRITE_TIMEOUT, 1301093);
        assertNull("Unexpected option value: " + leftChannel.getOption(Options.MAX_INBOUND_MESSAGE_SIZE),
                leftChannel.setOption(Options.MAX_INBOUND_MESSAGE_SIZE, 50000));// unsupported

        assertEquals(39710, (int) leftChannel.getOption(Options.READ_TIMEOUT));
        assertEquals(1301093, (int) leftChannel.getOption(Options.WRITE_TIMEOUT));
        assertNull(leftChannel.getOption(Options.MAX_INBOUND_MESSAGE_SIZE));// unsupported
        assertEquals(39710, (int) leftChannel.setOption(Options.READ_TIMEOUT, 70977010));
        assertEquals(1301093, (int) leftChannel.setOption(Options.WRITE_TIMEOUT, 293265));
        assertEquals(293265, (int) leftChannel.getOption(Options.WRITE_TIMEOUT));
        assertEquals(70977010, (int) leftChannel.getOption(Options.READ_TIMEOUT));

        // XNIO-171 test setOption(*, null)?
    }
}
