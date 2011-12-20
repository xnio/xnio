/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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
package org.xnio.ssl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.xnio.ssl.mock.SSLEngineMock.CLOSE_MSG;
import static org.xnio.ssl.mock.SSLEngineMock.HANDSHAKE_MSG;
import static org.xnio.ssl.mock.SSLEngineMock.HandshakeAction.FINISH;
import static org.xnio.ssl.mock.SSLEngineMock.HandshakeAction.NEED_UNWRAP;
import static org.xnio.ssl.mock.SSLEngineMock.HandshakeAction.NEED_WRAP;

import java.io.IOException;
import java.nio.ByteBuffer;

import javax.net.ssl.SSLEngineResult.HandshakeStatus;

import org.jmock.Expectations;
import org.junit.Test;
import org.xnio.BufferAllocator;
import org.xnio.ByteBufferSlicePool;
import org.xnio.ChannelListener;
import org.xnio.Options;
import org.xnio.Pool;
import org.xnio.channels.SslChannel;
import org.xnio.ssl.mock.SSLEngineMock.HandshakeAction;

/**
 * Test for JsseConnectedSslStreamChannel on start tls mode.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
public class StartTLSTestCase extends AbstractJsseConnectedSslStreamChannelTest {

    @SuppressWarnings("unchecked")
    @Override
    protected JsseConnectedSslStreamChannel createSslChannel() {
        final Pool<ByteBuffer> socketBufferPool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 17000, 17000 * 16);
        final Pool<ByteBuffer> applicationBufferPool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 17000, 17000 * 16);
        JsseConnectedSslStreamChannel channel = new JsseConnectedSslStreamChannel(connectedChannelMock, engineMock, socketBufferPool, applicationBufferPool, true);
        channel.getReadSetter().set(context.mock(ChannelListener.class, "read listener"));
        channel.getWriteSetter().set(context.mock(ChannelListener.class, "write listener"));
        return channel;
    }

    @Test
    public void getSecureOption() throws IOException {
        assertFalse(sslChannel.getOption(Options.SECURE));
        sslChannel.startHandshake();
        assertTrue(sslChannel.getOption(Options.SECURE));
    }

    @Test
    public void getSslSession() throws IOException {
        assertNull(sslChannel.getSslSession());
        sslChannel.startHandshake();
        assertNotNull(sslChannel.getSslSession());
    }

    @Test
    public void testSimpleFlush() throws IOException {
        // handshake action: NEED_WRAP
        connectedChannelMock.enableWrite(true);
        final ByteBuffer buffer = ByteBuffer.allocate(100);
        buffer.put("MSG".getBytes("UTF-8")).flip();
        assertEquals(3, sslChannel.write(buffer));
        assertWrittenMessage("MSG");
        assertFalse(connectedChannelMock.isFlushed());

        sslChannel.flush();
        assertTrue(connectedChannelMock.isFlushed());
        assertWrittenMessage("MSG");
    }

    @Test
    public void testFlushWithHandshaking() throws IOException {
        // handshake action: NEED_WRAP... this hanshake action will be ignored on START_TLS mode
        engineMock.setHandshakeActions(NEED_WRAP);
        connectedChannelMock.enableWrite(true);
        final ByteBuffer buffer = ByteBuffer.allocate(100);
        buffer.put("MSG".getBytes("UTF-8")).flip();
        assertEquals(3, sslChannel.write(buffer));
        assertWrittenMessage("MSG");
        assertFalse(connectedChannelMock.isFlushed());

        sslChannel.flush();
        assertTrue(connectedChannelMock.isFlushed());
        assertWrittenMessage("MSG");
    }

    @Test
    public void readNeedsWrapWriteAndReadDisabled() throws IOException {
        // handshake action: NEED_WRAP
        engineMock.setHandshakeActions(NEED_WRAP, FINISH);
        connectedChannelMock.setReadData(CLOSE_MSG);
        connectedChannelMock.enableRead(false);
        connectedChannelMock.enableWrite(false);
        final ByteBuffer buffer = ByteBuffer.allocate(100);
        assertFalse(connectedChannelMock.isReadAwaken());
        sslChannel.resumeReads();
        assertTrue(sslChannel.isReadResumed());
        assertTrue(connectedChannelMock.isReadAwaken());
        assertFalse(connectedChannelMock.isWriteResumed());
        // attempt to read... channel is expected to return 0 as it stumbles upon a NEED_WRAP that cannot be executed
        assertEquals(0, sslChannel.read(new ByteBuffer[]{buffer}));
        // everything is kept the same
        assertTrue(connectedChannelMock.isReadResumed());
        assertTrue(sslChannel.isReadResumed());
        assertFalse(connectedChannelMock.isWriteResumed());
        assertFalse(sslChannel.isWriteResumed());
        // no handshake is performed
        assertSame(HandshakeStatus.NEED_WRAP, engineMock.getHandshakeStatus());
        assertEquals(0, sslChannel.read(buffer));
        assertSame(HandshakeStatus.NEED_WRAP, engineMock.getHandshakeStatus());
        connectedChannelMock.enableRead(true);
        assertEquals(7, sslChannel.read(buffer));
        assertWrittenMessage(new String[0]);
        // close message is just read as a plain message, as sslChannel.read is simply delegated to connectedChannelMock
        assertReadMessage(buffer, CLOSE_MSG);
        assertSame(HandshakeStatus.NEED_WRAP, engineMock.getHandshakeStatus());

        sslChannel.shutdownWrites();
        assertTrue(sslChannel.flush());
        assertTrue(connectedChannelMock.isShutdownWrites());

        sslChannel.shutdownReads();
        assertTrue(connectedChannelMock.isShutdownReads());

        connectedChannelMock.enableWrite(true);
        sslChannel.shutdownWrites();
        assertTrue(sslChannel.flush());
        assertTrue(connectedChannelMock.isShutdownWrites());
        assertSame(HandshakeStatus.NEED_WRAP, engineMock.getHandshakeStatus());

        assertWrittenMessage(new String[0]);
        assertTrue(connectedChannelMock.isFlushed());
    }

    @Test
    public void writeNeedsUnwrapReadAndFlushDisabled () throws IOException {
        // handshake action: NEED_WRAP
        engineMock.setHandshakeActions(NEED_UNWRAP, FINISH);
        connectedChannelMock.setReadData(HANDSHAKE_MSG);
        connectedChannelMock.enableRead(false);
        connectedChannelMock.enableWrite(true);
        connectedChannelMock.enableFlush(false);
        final ByteBuffer buffer = ByteBuffer.allocate(100);
        buffer.put("MSG".getBytes("UTF-8")).flip();
        assertFalse(connectedChannelMock.isReadResumed());
        assertFalse(connectedChannelMock.isWriteResumed());
        // attempt to write... channel is expected to write because, on STARTLS mode, it wil simply delegate the
        // write request to connectedChannelMock
        assertEquals(3, sslChannel.write(buffer));
        // no read/write operation has been resumed either
        assertFalse(connectedChannelMock.isReadResumed());
        assertFalse(connectedChannelMock.isWriteResumed());
        // the first handshake action is as before, nothing has changed
        assertSame(HandshakeStatus.NEED_UNWRAP, engineMock.getHandshakeStatus());

        assertFalse(sslChannel.flush());
        assertWrittenMessage("MSG");
        assertFalse(connectedChannelMock.isFlushed());

        connectedChannelMock.enableFlush(true);
        assertTrue(sslChannel.flush());
        assertWrittenMessage("MSG");

        sslChannel.shutdownWrites();
        assertTrue(sslChannel.flush());
        assertWrittenMessage("MSG");
        assertTrue(connectedChannelMock.isFlushed());
    }

    @Test
    public void cantForceResumeReadsOnResumedReadChannel() throws IOException {
        sslChannel.resumeReads();
        sslChannel.resumeWrites();
        assertTrue(sslChannel.isReadResumed());
        assertTrue(sslChannel.isWriteResumed());
        assertTrue(connectedChannelMock.isReadAwaken());
        assertTrue(connectedChannelMock.isWriteAwaken());
        engineMock.setHandshakeActions(NEED_UNWRAP);
        final ByteBuffer buffer = ByteBuffer.allocate(100);
        buffer.put("COULDNT WRITE WITHOUT UNWRAP".getBytes("UTF-8")).flip();

        assertEquals(28, sslChannel.write(buffer));
        assertWrittenMessage("COULDNT WRITE WITHOUT UNWRAP");
        assertTrue(sslChannel.isWriteResumed());
        assertTrue(sslChannel.isReadResumed());
        assertTrue(connectedChannelMock.isWriteResumed());
        assertTrue(connectedChannelMock.isReadAwaken());

        // everything keeps the same at connectedChannelMock when we try to resume reads
        sslChannel.resumeWrites();
        assertTrue(sslChannel.isWriteResumed());
        assertTrue(sslChannel.isReadResumed());
        assertTrue(connectedChannelMock.isWriteResumed());
        assertTrue(connectedChannelMock.isReadAwaken());
    }

    @Test
    public void cantForceResumeReadsOnSuspendedReadChannel() throws IOException {
        // resume writes, reads are suspended
        sslChannel.resumeWrites();
        assertFalse(sslChannel.isReadResumed());
        assertTrue(sslChannel.isWriteResumed());
        assertFalse(connectedChannelMock.isReadResumed());
        assertTrue(connectedChannelMock.isWriteAwaken());

        // write needs to unwrap... try to write
        engineMock.setHandshakeActions(NEED_UNWRAP);
        final ByteBuffer buffer = ByteBuffer.allocate(100);
        buffer.put("COULDNT WRITE WITHOUT UNWRAP".getBytes("UTF-8")).flip();
        assertEquals(28, sslChannel.write(buffer));
        assertWrittenMessage("COULDNT WRITE WITHOUT UNWRAP");

        // nothing happens with read/write resumed on START_TLS channel
        assertTrue(sslChannel.isWriteResumed());
        assertFalse(sslChannel.isReadResumed());
        assertTrue(connectedChannelMock.isWriteResumed());
        assertFalse(connectedChannelMock.isReadResumed());
        assertFalse(connectedChannelMock.isReadAwaken());

        // everything keeps the same at connectedChannelMock when we try to resume writes
        sslChannel.resumeWrites();
        assertTrue(sslChannel.isWriteResumed());
        assertFalse(sslChannel.isReadResumed());
        assertTrue(connectedChannelMock.isWriteResumed());
        assertFalse(connectedChannelMock.isReadResumed());
        assertFalse(connectedChannelMock.isReadAwaken());
    }

    @Test
    public void resumeAndSuspendReadsOnNewChannel() throws Exception {
        // brand newly created sslChannel, isReadable returns aLWAYS and resuming read will awakeReads for connectedChannelMock
        assertFalse(sslChannel.isReadResumed());
        assertFalse(connectedChannelMock.isReadResumed());
        sslChannel.resumeReads();
        assertTrue(sslChannel.isReadResumed());
        assertTrue(connectedChannelMock.isReadAwaken());
        sslChannel.suspendReads();

        assertFalse(sslChannel.isReadResumed());
        assertTrue(connectedChannelMock.isReadResumed());
        sslChannelHandleWritable();
        sslChannel.handleReadable();
        assertFalse(sslChannel.isReadResumed());
        assertFalse(connectedChannelMock.isReadResumed());
    }

    @Test
    public void resumeAndSuspendReads() throws IOException {
        assertEquals(0, sslChannel.read(ByteBuffer.allocate(5)));

        // not a brand newly created sslChannel, isReadable returns OKAY and resuming read will just reasumeReads for connectedChannelMock
        assertFalse(sslChannel.isReadResumed());
        assertFalse(connectedChannelMock.isReadResumed());
        sslChannel.resumeReads();
        assertTrue(sslChannel.isReadResumed());
        assertTrue(connectedChannelMock.isReadAwaken());
        assertTrue(connectedChannelMock.isReadResumed());
        sslChannel.suspendReads();
        assertFalse(sslChannel.isReadResumed());
        assertTrue(connectedChannelMock.isReadResumed());
        sslChannel.handleReadable();
        assertFalse(sslChannel.isReadResumed());
        assertFalse(connectedChannelMock.isReadResumed());
    }

    @Test
    public void resumeAndSuspendWritesOnNewChannel() throws Exception {
        // brand newly created sslChannel, isWritable returns aLWAYS and resuming writes will awakeWrites for connectedChannelMock
        assertFalse(sslChannel.isWriteResumed());
        assertFalse(connectedChannelMock.isWriteResumed());
        sslChannel.resumeWrites();
        assertTrue(sslChannel.isWriteResumed());
        assertTrue(connectedChannelMock.isWriteAwaken());
        sslChannel.suspendWrites();
        assertFalse(sslChannel.isWriteResumed());
        assertTrue(connectedChannelMock.isWriteResumed());
        sslChannelHandleWritable();
        assertFalse(sslChannel.isWriteResumed());
        assertFalse(connectedChannelMock.isWriteResumed());
    }

    @Test
    public void resumeAndSuspendWrites() throws Exception {
        assertEquals(0, sslChannel.read(ByteBuffer.allocate(5)));

        // not a brand newly created sslChannel, isWritable returns OKAY and resuming writes will just reasumeWritesfor connectedChannelMock
        assertFalse(sslChannel.isWriteResumed());
        assertFalse(connectedChannelMock.isWriteResumed());
        sslChannel.resumeWrites();
        assertTrue(sslChannel.isWriteResumed());
        assertTrue(connectedChannelMock.isWriteAwaken());
        assertTrue(connectedChannelMock.isWriteResumed());
        sslChannel.suspendWrites();
        assertFalse(sslChannel.isWriteResumed());
        assertTrue(connectedChannelMock.isWriteResumed());
        sslChannelHandleWritable();
        assertFalse(sslChannel.isWriteResumed());
        assertFalse(connectedChannelMock.isWriteResumed());
    }

    @Test
    public void resumeAndSuspendWritesOnWriteNeedsUnwrapChannel() throws Exception {
        // create the read needs wrap channel\
        engineMock.setHandshakeActions(HandshakeAction.NEED_UNWRAP, HandshakeAction.FINISH);
        ByteBuffer buffer = ByteBuffer.allocate(5);
        buffer.put("12345".getBytes("UTF-8")).flip();
        // channel manages to write anyway, because we are on START_TLS mode
        assertEquals(5, sslChannel.write(new ByteBuffer[]{buffer}));
        assertWrittenMessage("12345");
        // no read action is forced
        assertFalse(connectedChannelMock.isReadResumed());
        assertFalse(sslChannel.isReadResumed());
        assertFalse(sslChannel.isWriteResumed());
        assertFalse(connectedChannelMock.isWriteResumed());

        // try to resume writes
        sslChannel.resumeWrites();
        assertTrue(sslChannel.isWriteResumed());
        assertTrue(connectedChannelMock.isWriteResumed());
        sslChannel.suspendWrites();
        assertFalse(sslChannel.isWriteResumed());
        assertTrue(connectedChannelMock.isWriteResumed());
        sslChannelHandleWritable();
        assertFalse(sslChannel.isWriteResumed());
        assertFalse(connectedChannelMock.isWriteResumed());
    }

    @Test
    public void suspendWritesOnResumedWriteNeedsUnwrapChannel() throws Exception {
        // resume writes first of all
        sslChannel.resumeWrites();
        assertTrue(sslChannel.isWriteResumed());
        assertTrue(connectedChannelMock.isWriteResumed());
        assertFalse(sslChannel.isReadResumed());
        assertFalse(connectedChannelMock.isReadResumed());
        // need unwrap is the first handshake action, and connectedChannelMock has read ops disabled
        engineMock.setHandshakeActions(HandshakeAction.NEED_UNWRAP, HandshakeAction.FINISH);

        // channel can write regardless of the NEED_UNWRAP handshake action, given START_TLS mode is on
        ByteBuffer buffer = ByteBuffer.allocate(1);
        buffer.put((byte) 0).flip();
        assertEquals(1, sslChannel.write(buffer));
        assertWrittenMessage("\0");
        assertTrue(sslChannel.isWriteResumed());
        assertTrue(connectedChannelMock.isWriteResumed());
        assertFalse(sslChannel.isReadResumed());
        assertFalse(connectedChannelMock.isReadResumed());

        // suspendWrites 
        sslChannel.suspendWrites();
        assertFalse(sslChannel.isWriteResumed());
        assertTrue(connectedChannelMock.isWriteResumed());
        assertFalse(sslChannel.isReadResumed());
        assertFalse(connectedChannelMock.isReadResumed());
        sslChannelHandleWritable();
        assertFalse(sslChannel.isWriteResumed());
        assertFalse(connectedChannelMock.isWriteResumed());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void startTLSWithWriteNeedsUnwrap() throws IOException {
        //set a handshake listener
        final ChannelListener<SslChannel> listener = context.mock(ChannelListener.class, "write needs unwrap");
        context.checking(new Expectations() {{
            atLeast(1).of(listener).handleEvent(sslChannel);
        }});
        sslChannel.getHandshakeSetter().set(listener);

        // handshake action: NEED_WRAP
        engineMock.setHandshakeActions(NEED_UNWRAP, FINISH);
        connectedChannelMock.setReadData(HANDSHAKE_MSG);
        connectedChannelMock.enableRead(false);
        connectedChannelMock.enableWrite(false);

        final ByteBuffer buffer = ByteBuffer.allocate(100);
        buffer.put("MSG".getBytes("UTF-8")).flip();
        assertFalse(connectedChannelMock.isReadResumed());
        assertFalse(connectedChannelMock.isWriteResumed());

        // start tls
        sslChannel.startHandshake();

        // attempt to write... channel is expected to return 0 as it stumbles upon a NEED_UNWRAP that cannot be executed
        assertEquals(0, sslChannel.write(buffer));
        assertTrue(connectedChannelMock.isReadResumed());
        assertFalse(connectedChannelMock.isWriteResumed());
        assertSame(HandshakeStatus.NEED_UNWRAP, engineMock.getHandshakeStatus());
        assertEquals(0, sslChannel.write(buffer));
        assertSame(HandshakeStatus.NEED_UNWRAP, engineMock.getHandshakeStatus());
        connectedChannelMock.enableRead(true);
        assertEquals(3, sslChannel.write(buffer));
        assertSame(HandshakeStatus.NOT_HANDSHAKING, engineMock.getHandshakeStatus());
        assertWrittenMessage(new String[0]);

        assertFalse(sslChannel.flush());
        assertWrittenMessage(new String[0]);

        sslChannel.shutdownWrites();
        assertFalse(sslChannel.flush());
        assertFalse(connectedChannelMock.isShutdownWrites());
        connectedChannelMock.enableWrite(true);
        assertFalse(sslChannel.flush());
        assertFalse(connectedChannelMock.isShutdownWrites());

        connectedChannelMock.setReadData(CLOSE_MSG);
        assertTrue(sslChannel.flush());
        assertTrue(connectedChannelMock.isShutdownWrites());

        assertTrue(connectedChannelMock.isOpen());
        sslChannel.close();
        assertFalse(connectedChannelMock.isOpen());

        assertWrittenMessage("MSG", CLOSE_MSG);
        assertTrue(connectedChannelMock.isFlushed());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void startTLSWithReadNeedsWrap() throws IOException {
        //set a handshake listener
        final ChannelListener<SslChannel> listener = context.mock(ChannelListener.class, "read needs unwrap");
        context.checking(new Expectations() {{
            atLeast(1).of(listener).handleEvent(sslChannel);
        }});
        sslChannel.getHandshakeSetter().set(listener);

        // handshake action: NEED_WRAP
        engineMock.setHandshakeActions(NEED_WRAP, FINISH);
        connectedChannelMock.setReadData("MSG");
        connectedChannelMock.enableRead(true);
        connectedChannelMock.enableWrite(false);
        
        final ByteBuffer buffer = ByteBuffer.allocate(100);
        assertFalse(connectedChannelMock.isReadResumed());
        assertFalse(connectedChannelMock.isWriteResumed());

        // start tls
        sslChannel.startHandshake();

        // attempt to write... channel is expected to return 0 as it stumbles upon a NEED_UNWRAP that cannot be executed
        assertEquals(0, sslChannel.read(buffer));
        assertFalse(connectedChannelMock.isReadResumed());
        assertTrue(connectedChannelMock.isWriteResumed());
        assertSame(HandshakeStatus.FINISHED, engineMock.getHandshakeStatus());
        connectedChannelMock.enableWrite(true);
        assertEquals(3, sslChannel.read(new ByteBuffer[]{buffer}));
        assertReadMessage(buffer, "MSG");
        assertSame(HandshakeStatus.NOT_HANDSHAKING, engineMock.getHandshakeStatus());
        assertWrittenMessage(HANDSHAKE_MSG);

        assertTrue(sslChannel.flush());
        assertWrittenMessage(HANDSHAKE_MSG);

        sslChannel.shutdownReads();
        assertFalse(connectedChannelMock.isShutdownWrites());
        connectedChannelMock.setReadData(CLOSE_MSG);
        sslChannel.shutdownWrites();
        assertTrue(sslChannel.flush());
        assertTrue(connectedChannelMock.isShutdownWrites());

        // channel is already closed
        assertFalse(connectedChannelMock.isOpen());
        sslChannel.close();
        assertFalse(connectedChannelMock.isOpen());

        assertWrittenMessage(HANDSHAKE_MSG, CLOSE_MSG);
        assertTrue(connectedChannelMock.isFlushed());
    }
}