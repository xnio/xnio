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

import java.io.IOError;
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
import org.xnio.mock.StreamConnectionMock;
import org.xnio.ssl.mock.SSLEngineMock.HandshakeAction;

/**
 * Test for SslConnection on start tls mode.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 */
public class StartTLSConnectionTestCase extends AbstractSslConnectionTest {

    @SuppressWarnings("unchecked")
    @Override
    protected SslConnection createSslConnection() {
        final Pool<ByteBuffer> socketBufferPool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 17000, 17000 * 16);
        final Pool<ByteBuffer> applicationBufferPool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 17000, 17000 * 16);
        final StreamConnectionMock connectionMock = new StreamConnectionMock(conduitMock);
        final SslConnection connection = new JsseSslConnection(connectionMock, engineMock, socketBufferPool, applicationBufferPool);
        try {
            connection.startHandshake();
        } catch (IOException e) {
            throw new IOError(e);
        }
        connection.getSourceChannel().getReadSetter().set(context.mock(ChannelListener.class, "read listener"));
        connection.getSinkChannel().getWriteSetter().set(context.mock(ChannelListener.class, "write listener"));
        return connection;
    }

    @Test
    public void getSecureOption() throws IOException {
        assertFalse(connection.getOption(Options.SECURE));
        startHandshake();
        assertTrue(connection.getOption(Options.SECURE));
    }

    @Test
    public void getSslSession() throws IOException {
        assertNull(connection.getSslSession());
        startHandshake();
        assertNotNull(connection.getSslSession());
    }

    @Test
    public void testSimpleFlush() throws IOException {
        // handshake action: NEED_WRAP
        conduitMock.enableWrites(true);
        final ByteBuffer buffer = ByteBuffer.allocate(100);
        buffer.put("MSG".getBytes("UTF-8")).flip();
        assertEquals(3, sinkConduit.write(buffer));
        assertWrittenMessage("MSG");
        assertFalse(conduitMock.isFlushed());

        sinkConduit.flush();
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
        assertEquals(3, sinkConduit.write(buffer));
        assertWrittenMessage("MSG");
        assertFalse(conduitMock.isFlushed());

        sinkConduit.flush();
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
        sourceConduit.resumeReads();
        assertTrue(sourceConduit.isReadResumed());
        assertFalse(conduitMock.isReadAwaken());
        assertFalse(conduitMock.isWriteResumed());
        // attempt to read... channel is expected to return 0 as it stumbles upon a NEED_WRAP that cannot be executed
        assertEquals(0, sourceConduit.read(new ByteBuffer[]{buffer}, 0, 1));
        // everything is kept the same
        assertTrue(conduitMock.isReadResumed());
        assertTrue(sourceConduit.isReadResumed());
        assertFalse(conduitMock.isWriteResumed());
        assertFalse(sinkConduit.isWriteResumed());
        // no handshake is performed
        assertSame(HandshakeStatus.NEED_WRAP, engineMock.getHandshakeStatus());
        assertEquals(0, sourceConduit.read(buffer));
        assertSame(HandshakeStatus.NEED_WRAP, engineMock.getHandshakeStatus());
        conduitMock.enableReads(true);
        assertEquals(7, sourceConduit.read(buffer));
        assertWrittenMessage(new String[0]);
        // close message is just read as a plain message, as sourceConduit.read is simply delegated to conduitMock
        assertReadMessage(buffer, CLOSE_MSG);
        assertSame(HandshakeStatus.NEED_WRAP, engineMock.getHandshakeStatus());

        sinkConduit.terminateWrites();
        assertTrue(sinkConduit.flush());
        assertTrue(conduitMock.isWriteShutdown());

        sourceConduit.terminateReads();
        assertTrue(conduitMock.isReadShutdown());

        conduitMock.enableWrites(true);
        sinkConduit.terminateWrites();
        assertTrue(sinkConduit.flush());
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
        assertEquals(3, sinkConduit.write(buffer));
        // no read/write operation has been resumed either
        assertFalse(conduitMock.isReadResumed());
        assertFalse(conduitMock.isWriteResumed());
        // the first handshake action is as before, nothing has changed
        assertSame(HandshakeStatus.NEED_UNWRAP, engineMock.getHandshakeStatus());

        assertFalse(sinkConduit.flush());
        assertWrittenMessage("MSG");
        assertFalse(conduitMock.isFlushed());

        conduitMock.enableFlush(true);
        assertTrue(sinkConduit.flush());
        assertWrittenMessage("MSG");

        sinkConduit.terminateWrites();
        assertTrue(sinkConduit.flush());
        assertWrittenMessage("MSG");
        assertTrue(conduitMock.isFlushed());
    }

    @Test
    public void cantForceResumeReadsOnResumedReadChannel() throws IOException {
        sourceConduit.resumeReads();
        sinkConduit.resumeWrites();
        assertTrue(sourceConduit.isReadResumed());
        assertTrue(sinkConduit.isWriteResumed());
        assertFalse(conduitMock.isReadAwaken());
        assertFalse(conduitMock.isWriteAwaken());
        engineMock.setHandshakeActions(NEED_UNWRAP);
        final ByteBuffer buffer = ByteBuffer.allocate(100);
        buffer.put("COULDNT WRITE WITHOUT UNWRAP".getBytes("UTF-8")).flip();

        assertEquals(28, sinkConduit.write(buffer));
        assertWrittenMessage("COULDNT WRITE WITHOUT UNWRAP");
        assertTrue(sinkConduit.isWriteResumed());
        assertTrue(sourceConduit.isReadResumed());
        assertTrue(conduitMock.isWriteResumed());
        assertFalse(conduitMock.isReadAwaken());

        // everything keeps the same at conduitMock when we try to resume reads
        sinkConduit.resumeWrites();
        assertTrue(sinkConduit.isWriteResumed());
        assertTrue(sourceConduit.isReadResumed());
        assertTrue(conduitMock.isWriteResumed());
        assertFalse(conduitMock.isReadAwaken());
    }

    @Test
    public void cantForceResumeReadsOnSuspendedReadChannel() throws IOException {
        // resume writes, reads are suspended
        sinkConduit.resumeWrites();
        assertFalse(sourceConduit.isReadResumed());
        assertTrue(sinkConduit.isWriteResumed());
        assertFalse(conduitMock.isReadResumed());
        assertFalse(conduitMock.isWriteAwaken());

        // write needs to unwrap... try to write
        engineMock.setHandshakeActions(NEED_UNWRAP);
        final ByteBuffer buffer = ByteBuffer.allocate(100);
        buffer.put("COULDNT WRITE WITHOUT UNWRAP".getBytes("UTF-8")).flip();
        assertEquals(28, sinkConduit.write(buffer));
        assertWrittenMessage("COULDNT WRITE WITHOUT UNWRAP");

        // nothing happens with read/write resumed on START_TLS channel
        assertTrue(sinkConduit.isWriteResumed());
        assertFalse(sourceConduit.isReadResumed());
        assertTrue(conduitMock.isWriteResumed());
        assertFalse(conduitMock.isReadResumed());
        assertFalse(conduitMock.isReadAwaken());

        // everything keeps the same at conduitMock when we try to resume writes
        sinkConduit.resumeWrites();
        assertTrue(sinkConduit.isWriteResumed());
        assertFalse(sourceConduit.isReadResumed());
        assertTrue(conduitMock.isWriteResumed());
        assertFalse(conduitMock.isReadResumed());
        assertFalse(conduitMock.isReadAwaken());
    }

    @Test
    public void resumeAndSuspendReadsOnNewChannel() throws Exception {
        // brand newly created sslChannel, isReadable returns aLWAYS and resuming read will awakeReads for conduitMock
        assertFalse(sourceConduit.isReadResumed());
        assertFalse(conduitMock.isReadResumed());
        sourceConduit.resumeReads();
        assertTrue(sourceConduit.isReadResumed());
        assertFalse(conduitMock.isReadAwaken());
        sourceConduit.suspendReads();

        assertFalse(sourceConduit.isReadResumed());
        assertFalse(conduitMock.isReadResumed());
    }

    @Test
    public void resumeAndSuspendReads() throws IOException {
        assertEquals(0, sourceConduit.read(ByteBuffer.allocate(5)));

        // not a brand newly created sslChannel, isReadable returns OKAY and resuming read will just reasumeReads for conduitMock
        assertFalse(sourceConduit.isReadResumed());
        assertFalse(conduitMock.isReadResumed());
        sourceConduit.resumeReads();
        assertTrue(sourceConduit.isReadResumed());
        assertFalse(conduitMock.isReadAwaken());
        assertTrue(conduitMock.isReadResumed());
        sourceConduit.suspendReads();
        assertFalse(sourceConduit.isReadResumed());
        assertFalse(conduitMock.isReadResumed());
    }

    @Test
    public void resumeAndSuspendWritesOnNewChannel() throws Exception {
        // brand newly created sslChannel, isWritable returns aLWAYS and resuming writes will awakeWrites for conduitMock
        assertFalse(sinkConduit.isWriteResumed());
        assertFalse(conduitMock.isWriteResumed());
        sinkConduit.resumeWrites();
        assertTrue(sinkConduit.isWriteResumed());
        assertFalse(conduitMock.isWriteAwaken());
        sinkConduit.suspendWrites();
        assertFalse(sinkConduit.isWriteResumed());
        assertFalse(conduitMock.isWriteResumed());
    }

    @Test
    public void resumeAndSuspendWrites() throws Exception {
        assertEquals(0, sourceConduit.read(ByteBuffer.allocate(5)));

        // not a brand newly created sslChannel, isWritable returns OKAY and resuming writes will just reasumeWritesfor conduitMock
        assertFalse(sinkConduit.isWriteResumed());
        assertFalse(conduitMock.isWriteResumed());
        sinkConduit.resumeWrites();
        assertTrue(sinkConduit.isWriteResumed());
        assertFalse(conduitMock.isWriteAwaken());
        assertTrue(conduitMock.isWriteResumed());
        sinkConduit.suspendWrites();
        assertFalse(sinkConduit.isWriteResumed());
        assertFalse(conduitMock.isWriteResumed());
    }

    @Test
    public void resumeAndSuspendWritesOnWriteNeedsUnwrapChannel() throws Exception {
        // create the read needs wrap channel\
        engineMock.setHandshakeActions(HandshakeAction.NEED_UNWRAP, HandshakeAction.FINISH);
        ByteBuffer buffer = ByteBuffer.allocate(5);
        buffer.put("12345".getBytes("UTF-8")).flip();
        // channel manages to write anyway, because we are on START_TLS mode
        assertEquals(5, sinkConduit.write(new ByteBuffer[]{buffer}, 0, 1));
        assertWrittenMessage("12345");
        // no read action is forced
        assertFalse(conduitMock.isReadResumed());
        assertFalse(sourceConduit.isReadResumed());
        assertFalse(sinkConduit.isWriteResumed());
        assertFalse(conduitMock.isWriteResumed());

        // try to resume writes
        sinkConduit.resumeWrites();
        assertTrue(sinkConduit.isWriteResumed());
        assertTrue(conduitMock.isWriteResumed());
        sinkConduit.suspendWrites();
        assertFalse(sinkConduit.isWriteResumed());
        assertFalse(conduitMock.isWriteResumed());
    }

    @Test
    public void suspendWritesOnResumedWriteNeedsUnwrapChannel() throws Exception {
        // resume writes first of all
        sinkConduit.resumeWrites();
        assertTrue(sinkConduit.isWriteResumed());
        assertTrue(conduitMock.isWriteResumed());
        assertFalse(sourceConduit.isReadResumed());
        assertFalse(conduitMock.isReadResumed());
        // need unwrap is the first handshake action, and conduitMock has read ops disabled
        engineMock.setHandshakeActions(HandshakeAction.NEED_UNWRAP, HandshakeAction.FINISH);

        // channel can write regardless of the NEED_UNWRAP handshake action, given START_TLS mode is on
        ByteBuffer buffer = ByteBuffer.allocate(1);
        buffer.put((byte) 0).flip();
        assertEquals(1, sinkConduit.write(buffer));
        assertWrittenMessage("\0");
        assertTrue(sinkConduit.isWriteResumed());
        assertTrue(conduitMock.isWriteResumed());
        assertFalse(sourceConduit.isReadResumed());
        assertFalse(conduitMock.isReadResumed());

        // suspendWrites 
        sinkConduit.suspendWrites();
        assertFalse(sinkConduit.isWriteResumed());
        assertFalse(conduitMock.isWriteResumed());
        assertFalse(sourceConduit.isReadResumed());
        assertFalse(conduitMock.isReadResumed());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void startTLSWithWriteNeedsUnwrap() throws IOException {
        //set a handshake listener
        final ChannelListener<SslConnection> listener = context.mock(ChannelListener.class, "write needs unwrap");
        context.checking(new Expectations() {{
            atLeast(1).of(listener).handleEvent(connection);
        }});
        connection.getHandshakeSetter().set(listener);

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
        startHandshake();

        // attempt to write... channel is expected to return 0 as it stumbles upon a NEED_UNWRAP that cannot be executed
        assertEquals(0, sinkConduit.write(buffer));
        assertFalse(conduitMock.isReadResumed());
        assertFalse(conduitMock.isWriteResumed());
        assertSame(HandshakeStatus.NEED_UNWRAP, engineMock.getHandshakeStatus());
        assertEquals(0, sinkConduit.write(buffer));
        assertSame(HandshakeStatus.NEED_UNWRAP, engineMock.getHandshakeStatus());
        conduitMock.enableReads(true);
        assertEquals(3, sinkConduit.write(buffer));
        assertSame(HandshakeStatus.NOT_HANDSHAKING, engineMock.getHandshakeStatus());
        assertWrittenMessage(new String[0]);

        assertFalse(sinkConduit.flush());
        assertWrittenMessage(new String[0]);

        sinkConduit.terminateWrites();
        assertFalse(sinkConduit.flush());
        assertFalse(conduitMock.isWriteShutdown());
        conduitMock.enableWrites(true);
        assertFalse(conduitMock.isWriteShutdown());

        conduitMock.setReadData(CLOSE_MSG);
        assertTrue(sinkConduit.flush());
        assertTrue(conduitMock.isWriteShutdown());

        assertTrue(conduitMock.isOpen());
        connection.close();
        assertFalse(conduitMock.isOpen());
        assertTrue(sourceConduit.isReadShutdown());
        assertTrue(sinkConduit.isWriteShutdown());
        assertTrue(conduitMock.isReadShutdown());
        assertTrue(conduitMock.isWriteShutdown());

        assertWrittenMessage("MSG", CLOSE_MSG);
        assertTrue(conduitMock.isFlushed());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void startTLSWithReadNeedsWrap() throws IOException {
        //set a handshake listener
        final ChannelListener<SslConnection> listener = context.mock(ChannelListener.class, "read needs unwrap");
        context.checking(new Expectations() {{
            atLeast(1).of(listener).handleEvent(connection);
        }});
        connection.getHandshakeSetter().set(listener);

        // handshake action: NEED_WRAP
        engineMock.setHandshakeActions(NEED_WRAP, FINISH);
        conduitMock.setReadData("MSG");
        conduitMock.enableReads(true);
        conduitMock.enableWrites(false);

        final ByteBuffer buffer = ByteBuffer.allocate(100);
        assertFalse(conduitMock.isReadResumed());
        assertFalse(conduitMock.isWriteResumed());

        // start tls
        startHandshake();

        // attempt to write... channel is expected to return 0 as it stumbles upon a NEED_UNWRAP that cannot be executed
        assertEquals(0, sourceConduit.read(buffer));
        assertFalse(conduitMock.isReadResumed());
        assertFalse(conduitMock.isWriteResumed());
        assertSame(HandshakeStatus.FINISHED, engineMock.getHandshakeStatus());
        conduitMock.enableWrites(true);
        assertEquals(3, sourceConduit.read(new ByteBuffer[]{buffer}, 0, 1));
        assertReadMessage(buffer, "MSG");
        assertSame(HandshakeStatus.NOT_HANDSHAKING, engineMock.getHandshakeStatus());
        assertWrittenMessage(HANDSHAKE_MSG);

        assertTrue(sinkConduit.flush());
        assertWrittenMessage(HANDSHAKE_MSG);

        sourceConduit.terminateReads();
        assertFalse(conduitMock.isWriteShutdown());
        conduitMock.setReadData(CLOSE_MSG);
        sinkConduit.terminateWrites();
        assertTrue(sinkConduit.flush());
        assertTrue(conduitMock.isWriteShutdown());

        // channel is already closed
        assertTrue(conduitMock.isOpen());
        assertFalse(sourceConduit.isReadShutdown());
        assertTrue(sinkConduit.isWriteShutdown());
        assertFalse(conduitMock.isReadShutdown());
        assertTrue(conduitMock.isWriteShutdown());
        connection.close();
        assertFalse(conduitMock.isOpen());
        assertTrue(sourceConduit.isReadShutdown());
        assertTrue(sinkConduit.isWriteShutdown());
        assertTrue(conduitMock.isReadShutdown());
        assertTrue(conduitMock.isWriteShutdown());

        assertWrittenMessage(HANDSHAKE_MSG, CLOSE_MSG);
        assertTrue(conduitMock.isFlushed());
    }

    private void startHandshake() throws IOException {
        connection.startHandshake();
        // update sinkConduits
        sinkConduit = connection.getSinkChannel().getConduit();
        sourceConduit = connection.getSourceChannel().getConduit();
    }
}
