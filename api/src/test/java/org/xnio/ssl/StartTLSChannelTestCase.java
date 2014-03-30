/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2013 Red Hat, Inc. and/or its affiliates, and individual
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

import java.io.EOFException;
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
import org.xnio.channels.AssembledConnectedSslStreamChannel;
import org.xnio.channels.ConnectedSslStreamChannel;
import org.xnio.channels.SslChannel;
import org.xnio.ssl.mock.SSLEngineMock.HandshakeAction;

/**
 * Test for AssembledConnectedSslStreamChannel on start tls mode.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 */
public class StartTLSChannelTestCase extends AbstractConnectedSslStreamChannelTest {

    @SuppressWarnings("unchecked")
    @Override
    protected ConnectedSslStreamChannel createSslChannel() {
        final Pool<ByteBuffer> socketBufferPool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 17000, 17000 * 16);
        final Pool<ByteBuffer> applicationBufferPool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 17000, 17000 * 16);
        final SslConnection connection = new JsseSslConnection(connectionMock, engineMock, socketBufferPool, applicationBufferPool);
        final ConnectedSslStreamChannel channel = new AssembledConnectedSslStreamChannel(connection, connection.getSourceChannel(), connection.getSinkChannel());
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
        conduitMock.enableWrites(true);
        final ByteBuffer buffer = ByteBuffer.allocate(100);
        buffer.put("MSG".getBytes("UTF-8")).flip();
        assertEquals(3, sslChannel.write(buffer));
        assertWrittenMessage("MSG");
        assertFalse(conduitMock.isFlushed());

        sslChannel.flush();
        assertTrue(conduitMock.isFlushed());
        assertWrittenMessage("MSG");
    }

    @Test
    public void testFlushWithHandshaking() throws IOException {
        // handshake action: NEED_WRAP... this hanshake action will be ignored on START_TLS mode
        engineMock.setHandshakeActions(NEED_WRAP);
        conduitMock.enableWrites(true);
        final ByteBuffer buffer = ByteBuffer.allocate(100);
        buffer.put("MSG".getBytes("UTF-8")).flip();
        assertEquals(3, sslChannel.write(buffer));
        assertWrittenMessage("MSG");
        assertFalse(conduitMock.isFlushed());

        sslChannel.flush();
        assertTrue(conduitMock.isFlushed());
        assertWrittenMessage("MSG");
    }

    @Test
    public void readNeedsWrapWriteAndReadDisabled() throws IOException {
        // handshake action: NEED_WRAP
        engineMock.setHandshakeActions(NEED_WRAP, FINISH);
        conduitMock.setReadData(CLOSE_MSG);
        conduitMock.enableReads(false);
        conduitMock.enableWrites(false);
        final ByteBuffer buffer = ByteBuffer.allocate(100);
        assertFalse(conduitMock.isReadAwaken());
        sslChannel.resumeReads();
        assertTrue(sslChannel.isReadResumed());
        assertFalse(conduitMock.isReadAwaken());
        assertFalse(conduitMock.isWriteResumed());
        // attempt to read... channel is expected to return 0 as it stumbles upon a NEED_WRAP that cannot be executed
        assertEquals(0, sslChannel.read(new ByteBuffer[]{buffer}));
        // everything is kept the same
        assertTrue(conduitMock.isReadResumed());
        assertTrue(sslChannel.isReadResumed());
        assertFalse(conduitMock.isWriteResumed());
        assertFalse(sslChannel.isWriteResumed());
        // no handshake is performed
        assertSame(HandshakeStatus.NEED_WRAP, engineMock.getHandshakeStatus());
        assertEquals(0, sslChannel.read(buffer));
        assertSame(HandshakeStatus.NEED_WRAP, engineMock.getHandshakeStatus());
        conduitMock.enableReads(true);
        assertEquals(7, sslChannel.read(buffer));
        assertWrittenMessage(new String[0]);
        // close message is just read as a plain message, as sslChannel.read is simply delegated to conduitMock
        assertReadMessage(buffer, CLOSE_MSG);
        assertSame(HandshakeStatus.NEED_WRAP, engineMock.getHandshakeStatus());

        sslChannel.shutdownWrites();
        assertTrue(sslChannel.flush());
        assertTrue(conduitMock.isWriteShutdown());

        sslChannel.shutdownReads();
        assertTrue(conduitMock.isReadShutdown());

        conduitMock.enableWrites(true);
        sslChannel.shutdownWrites();
        assertTrue(sslChannel.flush());
        assertTrue(conduitMock.isWriteShutdown());
        assertSame(HandshakeStatus.NEED_WRAP, engineMock.getHandshakeStatus());

        assertWrittenMessage(new String[0]);
        assertTrue(conduitMock.isFlushed());
    }

    @Test
    public void writeNeedsUnwrapReadAndFlushDisabled () throws IOException {
        // handshake action: NEED_WRAP
        engineMock.setHandshakeActions(NEED_UNWRAP, FINISH);
        conduitMock.setReadData(HANDSHAKE_MSG);
        conduitMock.enableReads(false);
        conduitMock.enableWrites(true);
        conduitMock.enableFlush(false);
        final ByteBuffer buffer = ByteBuffer.allocate(100);
        buffer.put("MSG".getBytes("UTF-8")).flip();
        assertFalse(conduitMock.isReadResumed());
        assertFalse(conduitMock.isWriteResumed());
        // attempt to write... channel is expected to write because, on STARTLS mode, it wil simply delegate the
        // write request to conduitMock
        assertEquals(3, sslChannel.write(buffer));
        // no read/write operation has been resumed either
        assertFalse(conduitMock.isReadResumed());
        assertFalse(conduitMock.isWriteResumed());
        // the first handshake action is as before, nothing has changed
        assertSame(HandshakeStatus.NEED_UNWRAP, engineMock.getHandshakeStatus());

        assertFalse(sslChannel.flush());
        assertWrittenMessage("MSG");
        assertFalse(conduitMock.isFlushed());

        conduitMock.enableFlush(true);
        assertTrue(sslChannel.flush());
        assertWrittenMessage("MSG");

        sslChannel.shutdownWrites();
        assertTrue(sslChannel.flush());
        assertWrittenMessage("MSG");
        assertTrue(conduitMock.isFlushed());
    }

    @Test
    public void cantForceResumeReadsOnResumedReadChannel() throws IOException {
        sslChannel.resumeReads();
        sslChannel.resumeWrites();
        assertTrue(sslChannel.isReadResumed());
        assertTrue(sslChannel.isWriteResumed());
        assertFalse(conduitMock.isReadAwaken());
        assertFalse(conduitMock.isWriteAwaken());
        engineMock.setHandshakeActions(NEED_UNWRAP);
        final ByteBuffer buffer = ByteBuffer.allocate(100);
        buffer.put("COULDNT WRITE WITHOUT UNWRAP".getBytes("UTF-8")).flip();

        assertEquals(28, sslChannel.write(buffer));
        assertWrittenMessage("COULDNT WRITE WITHOUT UNWRAP");
        assertTrue(sslChannel.isWriteResumed());
        assertTrue(sslChannel.isReadResumed());
        assertTrue(conduitMock.isWriteResumed());
        assertFalse(conduitMock.isReadAwaken());

        // everything keeps the same at conduitMock when we try to resume reads
        sslChannel.resumeWrites();
        assertTrue(sslChannel.isWriteResumed());
        assertTrue(sslChannel.isReadResumed());
        assertTrue(conduitMock.isWriteResumed());
        assertFalse(conduitMock.isReadAwaken());
    }

    @Test
    public void cantForceResumeReadsOnSuspendedReadChannel() throws IOException {
        // resume writes, reads are suspended
        sslChannel.resumeWrites();
        assertFalse(sslChannel.isReadResumed());
        assertTrue(sslChannel.isWriteResumed());
        assertFalse(conduitMock.isReadResumed());
        assertFalse(conduitMock.isWriteAwaken());

        // write needs to unwrap... try to write
        engineMock.setHandshakeActions(NEED_UNWRAP);
        final ByteBuffer buffer = ByteBuffer.allocate(100);
        buffer.put("COULDNT WRITE WITHOUT UNWRAP".getBytes("UTF-8")).flip();
        assertEquals(28, sslChannel.write(buffer));
        assertWrittenMessage("COULDNT WRITE WITHOUT UNWRAP");

        // nothing happens with read/write resumed on START_TLS channel
        assertTrue(sslChannel.isWriteResumed());
        assertFalse(sslChannel.isReadResumed());
        assertTrue(conduitMock.isWriteResumed());
        assertFalse(conduitMock.isReadResumed());
        assertFalse(conduitMock.isReadAwaken());

        // everything keeps the same at conduitMock when we try to resume writes
        sslChannel.resumeWrites();
        assertTrue(sslChannel.isWriteResumed());
        assertFalse(sslChannel.isReadResumed());
        assertTrue(conduitMock.isWriteResumed());
        assertFalse(conduitMock.isReadResumed());
        assertFalse(conduitMock.isReadAwaken());
    }

    @Test
    public void resumeAndSuspendReadsOnNewChannel() throws Exception {
        // brand newly created sslChannel, isReadable returns aLWAYS and resuming read will awakeReads for conduitMock
        assertFalse(sslChannel.isReadResumed());
        assertFalse(conduitMock.isReadResumed());
        sslChannel.resumeReads();
        assertTrue(sslChannel.isReadResumed());
        assertFalse(conduitMock.isReadAwaken());
        sslChannel.suspendReads();

        assertFalse(sslChannel.isReadResumed());
        assertFalse(conduitMock.isReadResumed());
    }

    @Test
    public void resumeAndSuspendReads() throws IOException {
        assertEquals(0, sslChannel.read(ByteBuffer.allocate(5)));

        // not a brand newly created sslChannel, isReadable returns OKAY and resuming read will just reasumeReads for conduitMock
        assertFalse(sslChannel.isReadResumed());
        assertFalse(conduitMock.isReadResumed());
        sslChannel.resumeReads();
        assertTrue(sslChannel.isReadResumed());
        assertFalse(conduitMock.isReadAwaken());
        assertTrue(conduitMock.isReadResumed());
        sslChannel.suspendReads();
        assertFalse(sslChannel.isReadResumed());
        assertFalse(conduitMock.isReadResumed());
    }

    @Test
    public void resumeAndSuspendWritesOnNewChannel() throws Exception {
        // brand newly created sslChannel, isWritable returns aLWAYS and resuming writes will awakeWrites for conduitMock
        assertFalse(sslChannel.isWriteResumed());
        assertFalse(conduitMock.isWriteResumed());
        sslChannel.resumeWrites();
        assertTrue(sslChannel.isWriteResumed());
        assertFalse(conduitMock.isWriteAwaken());
        sslChannel.suspendWrites();
        assertFalse(sslChannel.isWriteResumed());
        assertFalse(conduitMock.isWriteResumed());
    }

    @Test
    public void resumeAndSuspendWrites() throws Exception {
        assertEquals(0, sslChannel.read(ByteBuffer.allocate(5)));

        // not a brand newly created sslChannel, isWritable returns OKAY and resuming writes will just reasumeWritesfor conduitMock
        assertFalse(sslChannel.isWriteResumed());
        assertFalse(conduitMock.isWriteResumed());
        sslChannel.resumeWrites();
        assertTrue(sslChannel.isWriteResumed());
        assertFalse(conduitMock.isWriteAwaken());
        assertTrue(conduitMock.isWriteResumed());
        sslChannel.suspendWrites();
        assertFalse(sslChannel.isWriteResumed());
        assertFalse(conduitMock.isWriteResumed());
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
        assertFalse(conduitMock.isReadResumed());
        assertFalse(sslChannel.isReadResumed());
        assertFalse(sslChannel.isWriteResumed());
        assertFalse(conduitMock.isWriteResumed());

        // try to resume writes
        sslChannel.resumeWrites();
        assertTrue(sslChannel.isWriteResumed());
        assertTrue(conduitMock.isWriteResumed());
        sslChannel.suspendWrites();
        assertFalse(sslChannel.isWriteResumed());
        assertFalse(conduitMock.isWriteResumed());
    }

    @Test
    public void suspendWritesOnResumedWriteNeedsUnwrapChannel() throws Exception {
        // resume writes first of all
        sslChannel.resumeWrites();
        assertTrue(sslChannel.isWriteResumed());
        assertTrue(conduitMock.isWriteResumed());
        assertFalse(sslChannel.isReadResumed());
        assertFalse(conduitMock.isReadResumed());
        // need unwrap is the first handshake action, and conduitMock has read ops disabled
        engineMock.setHandshakeActions(HandshakeAction.NEED_UNWRAP, HandshakeAction.FINISH);

        // channel can write regardless of the NEED_UNWRAP handshake action, given START_TLS mode is on
        ByteBuffer buffer = ByteBuffer.allocate(1);
        buffer.put((byte) 0).flip();
        assertEquals(1, sslChannel.write(buffer));
        assertWrittenMessage("\0");
        assertTrue(sslChannel.isWriteResumed());
        assertTrue(conduitMock.isWriteResumed());
        assertFalse(sslChannel.isReadResumed());
        assertFalse(conduitMock.isReadResumed());

        // suspendWrites 
        sslChannel.suspendWrites();
        assertFalse(sslChannel.isWriteResumed());
        assertFalse(conduitMock.isWriteResumed());
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
        conduitMock.setReadData(HANDSHAKE_MSG);
        conduitMock.enableReads(false);
        conduitMock.enableWrites(false);

        final ByteBuffer buffer = ByteBuffer.allocate(100);
        buffer.put("MSG".getBytes("UTF-8")).flip();
        assertFalse(conduitMock.isReadResumed());
        assertFalse(conduitMock.isWriteResumed());

        // start tls
        sslChannel.startHandshake();

        // attempt to write... channel is expected to return 0 as it stumbles upon a NEED_UNWRAP that cannot be executed
        assertEquals(0, sslChannel.write(buffer));
        assertFalse(conduitMock.isReadResumed());
        assertFalse(conduitMock.isWriteResumed());
        assertSame(HandshakeStatus.NEED_UNWRAP, engineMock.getHandshakeStatus());
        assertEquals(0, sslChannel.write(buffer));
        assertSame(HandshakeStatus.NEED_UNWRAP, engineMock.getHandshakeStatus());
        conduitMock.enableReads(true);
        assertEquals(3, sslChannel.write(buffer));
        assertSame(HandshakeStatus.NOT_HANDSHAKING, engineMock.getHandshakeStatus());
        assertWrittenMessage(new String[0]);

        assertFalse(sslChannel.flush());
        assertWrittenMessage(new String[0]);

        sslChannel.shutdownWrites();
        assertFalse(sslChannel.flush());
        assertFalse(conduitMock.isWriteShutdown());
        conduitMock.enableWrites(true);

        /* One might assume that this flush should return false, since the close message has not yet been
           received; however, this is not part of the write shutdown contract.  Thus as soon as we flush our own
           write termination message to the wire, we consider writes to be fully shut down, and that's that.  It
           is up to the reader side to make sure that read is carried through to EOF before terminating reads.  If
           a read termination is received before flushing the write shutdown, then shutdown will close the channel.
           If the read termination comes after, then upon reading the -1, when reads are terminated, the channel will
           close at that time. */
        assertTrue(sslChannel.flush());
        assertTrue(sslChannel.flush());

        conduitMock.setReadData(CLOSE_MSG);
        buffer.clear();
        assertEquals(-1, sslChannel.read(buffer));
        sslChannel.shutdownReads();
        assertTrue(sslChannel.flush());
        assertTrue(conduitMock.isReadShutdown());
        assertTrue(conduitMock.isWriteShutdown());

        assertFalse(conduitMock.isOpen());

        assertWrittenMessage("MSG", CLOSE_MSG);
        assertTrue(conduitMock.isFlushed());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void startTLSWithReadNeedsWrap() throws IOException {
        //set a handshake listener
        final ChannelListener<SslChannel> listener = context.mock(ChannelListener.class, "read needs wrap");
        context.checking(new Expectations() {{
            atLeast(1).of(listener).handleEvent(sslChannel);
        }});
        sslChannel.getHandshakeSetter().set(listener);

        // handshake action: NEED_WRAP
        engineMock.setHandshakeActions(NEED_WRAP, FINISH);
        conduitMock.setReadData("MSG");
        conduitMock.enableReads(true);
        conduitMock.enableWrites(false);
        
        final ByteBuffer buffer = ByteBuffer.allocate(100);
        assertFalse(conduitMock.isReadResumed());
        assertFalse(conduitMock.isWriteResumed());

        // start tls
        sslChannel.startHandshake();

        // attempt to write... channel is expected to return 3 regardless of the fact it cannot flush
        assertSame(HandshakeStatus.NEED_WRAP, engineMock.getHandshakeStatus());
        assertEquals(3, sslChannel.read(new ByteBuffer[]{buffer}));
        assertReadMessage(buffer, "MSG");
        assertSame(HandshakeStatus.NOT_HANDSHAKING, engineMock.getHandshakeStatus());
        assertWrittenMessage("");

        assertFalse(sslChannel.flush());
        // enable writes and hence flush handshake message
        conduitMock.enableWrites(true);
        assertTrue(sslChannel.flush());
        assertWrittenMessage(HANDSHAKE_MSG);

        EOFException expected = null;
        try {
            sslChannel.shutdownReads();
        } catch (EOFException e) {
            expected = e;
        }
        assertNotNull(expected);

        assertFalse(conduitMock.isWriteShutdown());
        conduitMock.setReadData(CLOSE_MSG);
        sslChannel.shutdownWrites();
        assertTrue(sslChannel.flush());
        assertTrue(conduitMock.isWriteShutdown());

        // channel closed
        assertFalse(conduitMock.isOpen());

        assertWrittenMessage(HANDSHAKE_MSG, CLOSE_MSG);
        assertTrue(conduitMock.isFlushed());
    }
}
