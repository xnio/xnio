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

import static javax.net.ssl.SSLEngineResult.HandshakeStatus.FINISHED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

import org.junit.Test;
import org.xnio.ssl.mock.SSLEngineMock.HandshakeAction;

/**
 * Test that checks the coordination of read and write tasks in scenarios that involve a multitude of combinations 
 * of readNeedsWrap/writeNeedsUnwrap and read/ẃrite is resumed/suspended.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
public class SslReadWriteTasksCoordinationTestCase extends AbstractJsseConnectedSslStreamChannelTest {

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
        assertEquals(0, sslChannel.read(buffer));
        assertFalse(connectedChannelMock.isReadResumed());
        assertTrue(sslChannel.isReadResumed());
        assertTrue(connectedChannelMock.isWriteResumed());
        assertTrue(connectedChannelMock.isWriteResumed());
        assertSame(FINISHED, engineMock.getHandshakeStatus());
        assertEquals(0, sslChannel.read(buffer));
        assertSame(HandshakeStatus.NOT_HANDSHAKING, engineMock.getHandshakeStatus());
        connectedChannelMock.enableRead(true);
        assertEquals(-1, sslChannel.read(buffer));
        assertWrittenMessage(new String[0]);
        assertSame(HandshakeStatus.NEED_WRAP, engineMock.getHandshakeStatus());

        assertFalse(sslChannel.shutdownWrites());
        assertFalse(connectedChannelMock.isShutdownWrites());

        connectedChannelMock.enableWrite(true);
        assertTrue(sslChannel.shutdownWrites());
        assertTrue(connectedChannelMock.isShutdownWrites());
        assertSame(HandshakeStatus.NOT_HANDSHAKING, engineMock.getHandshakeStatus());

        assertWrittenMessage(HANDSHAKE_MSG, CLOSE_MSG);
        assertTrue(connectedChannelMock.isFlushed());
    }

    @Test
    public void readNeedsWrapWriteDisabled() throws IOException {
        // handshake action: NEED_WRAP
        engineMock.setHandshakeActions(NEED_WRAP, FINISH);
        connectedChannelMock.setReadData(CLOSE_MSG);
        connectedChannelMock.enableRead(true);
        connectedChannelMock.enableWrite(false);
        final ByteBuffer buffer = ByteBuffer.allocate(100);
        // attempt to read... channel is expected to return 0 as it stumbles upon a NEED_WRAP that cannot be executed
        assertEquals(0, sslChannel.read(buffer));
        assertSame(HandshakeStatus.FINISHED, engineMock.getHandshakeStatus());
        assertTrue(connectedChannelMock.isWriteResumed());
        assertFalse(connectedChannelMock.isReadResumed());
        assertEquals(-1, sslChannel.read(buffer));
        assertSame(HandshakeStatus.NEED_WRAP, engineMock.getHandshakeStatus());
        assertFalse(connectedChannelMock.isShutdownReads());
        sslChannel.shutdownReads();
        assertTrue(connectedChannelMock.isShutdownReads());
        assertFalse(sslChannel.shutdownWrites());
        assertFalse(sslChannel.shutdownWrites());
        assertFalse(sslChannel.shutdownWrites());
        assertSame(HandshakeStatus.NEED_WRAP, engineMock.getHandshakeStatus());
        assertWrittenMessage(new String[0]);

        connectedChannelMock.enableWrite(true);
        assertFalse(connectedChannelMock.isShutdownWrites());
        assertTrue(sslChannel.shutdownWrites());
        assertTrue(connectedChannelMock.isShutdownWrites());
        assertSame(HandshakeStatus.NOT_HANDSHAKING, engineMock.getHandshakeStatus());
        assertWrittenMessage(HANDSHAKE_MSG, CLOSE_MSG);
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
        assertWrittenMessage("MSG");
        assertFalse(connectedChannelMock.isFlushed());

        connectedChannelMock.enableFlush(true);
        assertTrue(sslChannel.flush());
        assertWrittenMessage("MSG");

        assertFalse(sslChannel.shutdownWrites());
        assertWrittenMessage("MSG", CLOSE_MSG);
        assertTrue(connectedChannelMock.isFlushed());

        connectedChannelMock.setReadData(CLOSE_MSG);
        assertTrue(sslChannel.shutdownWrites());
        assertTrue(connectedChannelMock.isShutdownWrites());
        assertTrue(connectedChannelMock.isFlushed());
        assertWrittenMessage("MSG", CLOSE_MSG);

        assertTrue(connectedChannelMock.isOpen());
        sslChannel.close();
        assertFalse(connectedChannelMock.isOpen());

        assertWrittenMessage("MSG", CLOSE_MSG);
        assertTrue(connectedChannelMock.allReadDataConsumed());
    }

    @Test
    public void writeNeedsUnwrapReadAndWriteDisabled () throws IOException {
        // handshake action: NEED_WRAP
        engineMock.setHandshakeActions(NEED_UNWRAP, FINISH);
        connectedChannelMock.setReadData(HANDSHAKE_MSG);
        connectedChannelMock.enableRead(false);
        connectedChannelMock.enableWrite(false);
        final ByteBuffer buffer = ByteBuffer.allocate(100);
        buffer.put("MSG".getBytes("UTF-8")).flip();
        assertFalse(connectedChannelMock.isReadResumed());
        assertFalse(connectedChannelMock.isWriteResumed());
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

        assertFalse(sslChannel.shutdownWrites());
        assertFalse(connectedChannelMock.isShutdownWrites());
        connectedChannelMock.enableWrite(true);
        assertFalse(sslChannel.shutdownWrites());
        assertFalse(connectedChannelMock.isShutdownWrites());

        connectedChannelMock.setReadData(CLOSE_MSG);
        assertTrue(sslChannel.shutdownWrites());
        assertTrue(connectedChannelMock.isShutdownWrites());

        assertTrue(connectedChannelMock.isOpen());
        sslChannel.close();
        assertFalse(connectedChannelMock.isOpen());

        assertWrittenMessage("MSG", CLOSE_MSG);
        assertTrue(connectedChannelMock.isFlushed());
    }

    @Test
    public void writeNeedsUnwrapReadDisabled() throws IOException {
        // handshake action: NEED_WRAP
        engineMock.setHandshakeActions(NEED_UNWRAP, NEED_WRAP, FINISH);
        connectedChannelMock.setReadData(HANDSHAKE_MSG);
        connectedChannelMock.enableRead(false);
        connectedChannelMock.enableWrite(true);
        final ByteBuffer buffer = ByteBuffer.allocate(100);
        buffer.put("MSG READ DISABLED".getBytes("UTF-8")).flip();
        assertFalse(connectedChannelMock.isReadResumed());
        assertFalse(connectedChannelMock.isWriteResumed());
        // attempt to write... channel is expected to return 0 as it stumbles upon a NEED_UNWRAP that cannot be executed
        assertEquals(0, sslChannel.write(buffer));
        assertTrue(connectedChannelMock.isReadResumed());
        assertFalse(connectedChannelMock.isWriteResumed());
        assertSame(HandshakeStatus.NEED_UNWRAP, engineMock.getHandshakeStatus());
        assertEquals(0, sslChannel.write(buffer));
        assertSame(HandshakeStatus.NEED_UNWRAP, engineMock.getHandshakeStatus());
        connectedChannelMock.enableRead(true);
        assertEquals(17, sslChannel.write(buffer));
        assertSame(HandshakeStatus.NOT_HANDSHAKING, engineMock.getHandshakeStatus());
        assertWrittenMessage(HANDSHAKE_MSG);

        assertTrue(connectedChannelMock.isFlushed());
        assertTrue(sslChannel.flush());
        assertWrittenMessage(HANDSHAKE_MSG, "MSG READ DISABLED");

        assertTrue(sslChannel.flush());
        assertWrittenMessage(HANDSHAKE_MSG, "MSG READ DISABLED");

        assertFalse(sslChannel.shutdownWrites());
        assertFalse(connectedChannelMock.isShutdownWrites());
        connectedChannelMock.enableWrite(true);
        assertFalse(sslChannel.shutdownWrites());
        assertFalse(connectedChannelMock.isShutdownWrites());

        connectedChannelMock.setReadData(CLOSE_MSG);
        assertTrue(sslChannel.shutdownWrites());
        assertTrue(connectedChannelMock.isShutdownWrites());

        assertTrue(connectedChannelMock.isOpen());
        sslChannel.close();
        assertFalse(connectedChannelMock.isOpen());

        assertWrittenMessage(HANDSHAKE_MSG, "MSG READ DISABLED", CLOSE_MSG);
        assertTrue(connectedChannelMock.isFlushed());
    }

    @Test
    public void testSimpleFlush() throws IOException {
        // handshake action: NEED_WRAP
        connectedChannelMock.enableWrite(true);
        final ByteBuffer buffer = ByteBuffer.allocate(100);
        buffer.put("MSG".getBytes("UTF-8")).flip();
        assertEquals(3, sslChannel.write(buffer));
        assertWrittenMessage(new String[0]);

        sslChannel.flush();
        assertTrue(connectedChannelMock.isFlushed());
        assertWrittenMessage("MSG");
    }

    @Test
    public void testFlushWithHandshaking() throws IOException {
        // handshake action: NEED_WRAP
        engineMock.setHandshakeActions(NEED_WRAP);
        connectedChannelMock.enableWrite(true);
        final ByteBuffer buffer = ByteBuffer.allocate(100);
        buffer.put("MSG".getBytes("UTF-8")).flip();
        assertEquals(3, sslChannel.write(buffer));
        assertWrittenMessage(HANDSHAKE_MSG);
        assertTrue(connectedChannelMock.isFlushed());

        sslChannel.flush();
        assertTrue(connectedChannelMock.isFlushed());
        assertWrittenMessage(HANDSHAKE_MSG, "MSG");
    }

    @Test
    public void forceResumeReadsOnResumedReadChannel() throws IOException {
        sslChannel.resumeReads();
        sslChannel.resumeWrites();
        assertTrue(sslChannel.isReadResumed());
        assertTrue(sslChannel.isWriteResumed());
        assertTrue(connectedChannelMock.isReadAwaken());
        assertTrue(connectedChannelMock.isWriteAwaken());
        engineMock.setHandshakeActions(NEED_UNWRAP);
        final ByteBuffer buffer = ByteBuffer.allocate(100);
        buffer.put("CANT WRITE WITHOUT UNWRAP".getBytes("UTF-8")).flip();

        sslChannel.write(buffer);
        assertTrue(sslChannel.isWriteResumed());
        assertTrue(sslChannel.isReadResumed());
        assertFalse(connectedChannelMock.isWriteResumed());
        assertTrue(connectedChannelMock.isReadAwaken());

        // everything keeps the same at connectedChannelMock when we try to resume reads
        sslChannel.resumeWrites();
        assertTrue(sslChannel.isWriteResumed());
        assertTrue(sslChannel.isReadResumed());
        assertFalse(connectedChannelMock.isWriteResumed());
        assertTrue(connectedChannelMock.isReadAwaken());
    }

    @Test
    public void forceResumeReadsOnSuspendedReadChannel() throws IOException {
        // resume writes, reads are suspended
        sslChannel.resumeWrites();
        assertFalse(sslChannel.isReadResumed());
        assertTrue(sslChannel.isWriteResumed());
        assertFalse(connectedChannelMock.isReadResumed());
        assertTrue(connectedChannelMock.isWriteAwaken());

        // write needs to unwrap... try to write
        engineMock.setHandshakeActions(NEED_UNWRAP);
        final ByteBuffer buffer = ByteBuffer.allocate(100);
        buffer.put("CANT WRITE WITHOUT UNWRAP".getBytes("UTF-8")).flip();
        assertEquals(0, sslChannel.write(buffer));

        // write is still resumed at the sslChannel, and read is still suspended;
        // but at connected channel mock it is the other way around
        assertTrue(sslChannel.isWriteResumed());
        assertFalse(sslChannel.isReadResumed());
        assertFalse(connectedChannelMock.isWriteResumed());
        assertTrue(connectedChannelMock.isReadResumed());
        assertFalse(connectedChannelMock.isReadAwaken());

        // everything keeps the same at connectedChannelMock when we try to resume writes
        sslChannel.resumeWrites();
        assertTrue(sslChannel.isWriteResumed());
        assertFalse(sslChannel.isReadResumed());
        assertFalse(connectedChannelMock.isWriteResumed());
        assertTrue(connectedChannelMock.isReadResumed());
        assertFalse(connectedChannelMock.isReadAwaken());
    }

    @Test
    public void forceResumeWritesOnResumedWriteChannel() throws IOException {
        // resume writes, reads are suspended
        sslChannel.resumeReads();
        sslChannel.resumeWrites();
        assertTrue(sslChannel.isReadResumed());
        assertTrue(sslChannel.isWriteResumed());
        assertTrue(connectedChannelMock.isReadAwaken());
        assertTrue(connectedChannelMock.isWriteAwaken());
        engineMock.setHandshakeActions(NEED_WRAP);
        connectedChannelMock.enableWrite(false);
        connectedChannelMock.enableRead(false);
        connectedChannelMock.setReadData("CAN'T READ WITHOUT WRAP");
        final ByteBuffer buffer = ByteBuffer.allocate(100);
        sslChannel.read(buffer);
        assertTrue(sslChannel.isWriteResumed());
        assertTrue(sslChannel.isReadResumed());
        assertFalse(connectedChannelMock.isReadResumed());
        assertTrue(connectedChannelMock.isWriteAwaken());

        // everything keeps the same at connectedChannelMock when we try to resume reads
        sslChannel.resumeReads();
        assertTrue(sslChannel.isReadResumed());
        assertTrue(sslChannel.isWriteResumed());
        assertFalse(connectedChannelMock.isReadResumed());
        assertTrue(connectedChannelMock.isWriteAwaken());
    }

    @Test
    public void forceResumeWritesOnSuspendedWriteChannel() throws IOException {
        sslChannel.resumeReads();
        assertTrue(sslChannel.isReadResumed());
        assertFalse(sslChannel.isWriteResumed());
        assertTrue(connectedChannelMock.isReadAwaken());
        assertFalse(connectedChannelMock.isWriteResumed());
        engineMock.setHandshakeActions(NEED_WRAP);
        connectedChannelMock.enableWrite(false);
        connectedChannelMock.enableRead(true);
        connectedChannelMock.setReadData("CAN'T READ WITHOUT WRAP");
        final ByteBuffer buffer = ByteBuffer.allocate(100);
        sslChannel.read(buffer);
        assertTrue(sslChannel.isReadResumed());
        assertFalse(sslChannel.isWriteResumed());
        assertFalse(connectedChannelMock.isReadResumed());
        assertTrue(connectedChannelMock.isWriteResumed());
        assertFalse(connectedChannelMock.isWriteAwaken());

        // everything keeps the same at connectedChannelMock when we try to resume reads
        sslChannel.resumeReads();
        assertTrue(sslChannel.isReadResumed());
        assertFalse(sslChannel.isWriteResumed());
        assertFalse(connectedChannelMock.isReadResumed());
        assertTrue(connectedChannelMock.isWriteResumed());
        assertFalse(connectedChannelMock.isWriteAwaken());
    }

    @Test
    public void resumeReadsOnForcedResumeReadsChannel() throws IOException {
        // enable flush and resume writes only
        connectedChannelMock.enableFlush(true);
        sslChannel.resumeWrites();
        assertTrue(sslChannel.isWriteResumed());
        assertTrue(connectedChannelMock.isWriteResumed());
        assertFalse(sslChannel.isReadResumed());
        assertFalse(connectedChannelMock.isReadResumed());
        // handshake actions will require read, but there is no read data right now
        engineMock.setHandshakeActions(HandshakeAction.NEED_WRAP, HandshakeAction.NEED_UNWRAP, HandshakeAction.NEED_TASK, HandshakeAction.FINISH);
        // try to write
        ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put("TEXT".getBytes("UTF-8")).flip();
        assertEquals(0, sslChannel.write(buffer));
        // as a result, we will have a forced resume reads
        assertFalse(sslChannel.isReadResumed());
        assertTrue(connectedChannelMock.isReadResumed());
        // resume reads now
        sslChannel.resumeReads();
        // nothing changes for connectedChannelMock
        assertTrue(sslChannel.isReadResumed());
        assertTrue(connectedChannelMock.isReadResumed());

        // now, add the data to read
        connectedChannelMock.setReadData(HANDSHAKE_MSG);
        connectedChannelMock.enableRead(true);

        // try to write now
        assertEquals(4, sslChannel.write(buffer));
        // write and read continue enabled
        assertTrue(sslChannel.isWriteResumed());
        assertTrue(connectedChannelMock.isWriteResumed());
        assertTrue(sslChannel.isReadResumed());
        assertTrue(connectedChannelMock.isReadResumed());

        assertWrittenMessage(HANDSHAKE_MSG);
        assertTrue(connectedChannelMock.isFlushed());
        assertTrue(sslChannel.flush());
        assertWrittenMessage(HANDSHAKE_MSG, "TEXT");
        assertTrue(connectedChannelMock.isFlushed());
        assertTrue(connectedChannelMock.allReadDataConsumed());
    }

    @Test
    public void resumeWritesOnForcedResumeWritesChannel() throws IOException {
        // disable flush and resume writes only
        connectedChannelMock.enableFlush(false);
        connectedChannelMock.enableRead(true);
        sslChannel.resumeReads();
        assertTrue(sslChannel.isReadResumed());
        assertTrue(connectedChannelMock.isReadResumed());
        assertFalse(sslChannel.isWriteResumed());
        assertFalse(connectedChannelMock.isWriteResumed());
        // data to be read
        connectedChannelMock.setReadData(HANDSHAKE_MSG, "TEXT", "TO", "READ");
        // handshake actions will require wrap, but there is no way it can write the data with flush disabled
        engineMock.setHandshakeActions(HandshakeAction.NEED_WRAP, HandshakeAction.NEED_UNWRAP, HandshakeAction.NEED_TASK, HandshakeAction.FINISH);
        // try to read
        ByteBuffer buffer = ByteBuffer.allocate(10);
        assertEquals(0, sslChannel.read(buffer));
        // as a result, we will have a forced resume writes
        assertFalse(sslChannel.isWriteResumed());
        assertTrue(connectedChannelMock.isWriteResumed());

        // resume writes now
        sslChannel.resumeWrites();
        // nothing changes to connectedChannelMock
        assertTrue(sslChannel.isWriteResumed());
        assertTrue(connectedChannelMock.isWriteResumed());

        // now enable flush on the channel
        connectedChannelMock.enableFlush(true);

        // try to read now
        assertEquals(10, sslChannel.read(buffer));
        // write and read continue enabled
        assertTrue(sslChannel.isWriteResumed());
        assertTrue(connectedChannelMock.isWriteResumed());
        assertTrue(sslChannel.isReadResumed());
        assertTrue(connectedChannelMock.isReadResumed());

        assertReadMessage(buffer, "TEXT", "TO", "READ");
        assertTrue(connectedChannelMock.allReadDataConsumed());
        assertWrittenMessage(HANDSHAKE_MSG);
        assertTrue(sslChannel.flush());
        assertWrittenMessage(HANDSHAKE_MSG);
        assertTrue(connectedChannelMock.isFlushed());
    }

    @Test
    // test that a forced read is undone as soon as the issue that caused the forced read is solved
    public void properHandlingOfForcedRead() throws IOException {
        /* Create the forced read scenario */
        engineMock.setHandshakeActions(NEED_WRAP, NEED_UNWRAP, FINISH);
        connectedChannelMock.setReadData(HANDSHAKE_MSG);
        connectedChannelMock.enableRead(false);

        sslChannel.resumeWrites();
        assertTrue(sslChannel.isWriteResumed());
        assertTrue(connectedChannelMock.isWriteResumed());
        assertFalse(sslChannel.isReadResumed());
        assertFalse(connectedChannelMock.isReadResumed());

        ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put("MSG".getBytes("UTF-8")).flip();
        assertEquals(0, sslChannel.write(buffer));

        assertTrue(sslChannel.isWriteResumed());
        assertFalse(connectedChannelMock.isWriteResumed());
        assertFalse(sslChannel.isReadResumed());
        assertTrue(connectedChannelMock.isReadResumed());

        /* Solve the forced read */
        connectedChannelMock.enableRead(true);
        assertEquals(0, sslChannel.read(ByteBuffer.allocate(2)));

        assertTrue(connectedChannelMock.allReadDataConsumed());
        /* Assert the forced reads actions have been undone */
        assertTrue(sslChannel.isWriteResumed());
        assertTrue(connectedChannelMock.isWriteResumed());
        assertFalse(sslChannel.isReadResumed());
        assertFalse(connectedChannelMock.isReadResumed());
    }

    @Test
    // test that a forced write is undone as soon as the issue that caused the forced write is solved
    public void properHandlingOfForcedWrite() throws IOException {
        /* Create the forced write scenario */
        engineMock.setHandshakeActions(NEED_WRAP, NEED_UNWRAP, FINISH);
        connectedChannelMock.setReadData(HANDSHAKE_MSG, "TXT");
        connectedChannelMock.enableWrite(false);
        connectedChannelMock.enableRead(true);

        sslChannel.resumeReads();
        assertTrue(sslChannel.isReadResumed());
        assertTrue(connectedChannelMock.isReadResumed());
        assertFalse(sslChannel.isWriteResumed());
        assertFalse(connectedChannelMock.isWriteResumed());

        ByteBuffer buffer = ByteBuffer.allocate(10);
        assertEquals(0, sslChannel.read(buffer));

        assertTrue(sslChannel.isReadResumed());
        assertFalse(connectedChannelMock.isReadResumed());
        assertFalse(sslChannel.isWriteResumed());
        assertTrue(connectedChannelMock.isWriteResumed());

        /* Solve the forced write issue*/
        connectedChannelMock.enableWrite(true);
        ByteBuffer writeBuffer = ByteBuffer.allocate(3);
        writeBuffer.put("TXT".getBytes("UTF-8")).flip();
        assertEquals(3, sslChannel.write(writeBuffer));

        assertWrittenMessage(HANDSHAKE_MSG);
        assertTrue(connectedChannelMock.isFlushed());
        sslChannel.flush();
        assertWrittenMessage(HANDSHAKE_MSG, "TXT");
        assertTrue(connectedChannelMock.isFlushed());

        /* Assert the forced write actions have been undone */
        assertTrue(sslChannel.isReadResumed());
        assertTrue(connectedChannelMock.isReadResumed());
        assertFalse(sslChannel.isWriteResumed());
        assertFalse(connectedChannelMock.isWriteResumed());
    }

    @Test
    public void resumeAndSuspendReadsOnNewChannel() throws IOException {
        // brand newly created sslChannel, isReadable returns aLWAYS and resuming read will awakeReads for connectedChannelMock
        assertFalse(sslChannel.isReadResumed());
        assertFalse(connectedChannelMock.isReadResumed());
        sslChannel.resumeReads();
        assertTrue(sslChannel.isReadResumed());
        assertTrue(connectedChannelMock.isReadAwaken());
        sslChannel.suspendReads();
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
        assertFalse(connectedChannelMock.isReadAwaken());
        assertTrue(connectedChannelMock.isReadResumed());
        sslChannel.suspendReads();
        assertFalse(sslChannel.isReadResumed());
        assertFalse(connectedChannelMock.isReadResumed());
    }

    @Test
    public void resumeAndSuspendReadsOnReadNeedsWrapChannel() throws IOException {
        // create the read needs wrap channel\
        connectedChannelMock.enableWrite(false);
        engineMock.setHandshakeActions(HandshakeAction.NEED_WRAP, HandshakeAction.FINISH);
        assertEquals(0, sslChannel.read(ByteBuffer.allocate(5)));
        assertTrue(connectedChannelMock.isWriteResumed());
        assertFalse(sslChannel.isWriteResumed());

        // with read needs wrap, resuming read will do nothing for connectedChannelMock, as isReadable returns NEVER
        assertFalse(sslChannel.isReadResumed());
        assertFalse(connectedChannelMock.isReadResumed());
        sslChannel.resumeReads();
        assertTrue(sslChannel.isReadResumed());
        assertFalse(connectedChannelMock.isReadResumed());
        sslChannel.suspendReads();
        assertFalse(sslChannel.isReadResumed());
        assertFalse(connectedChannelMock.isReadResumed());
    }

    @Test
    public void suspendReadsOnResumedReadNeedsWrapChannel() throws IOException {
        // resume reads first of all
        sslChannel.resumeReads();
        assertTrue(sslChannel.isReadResumed());
        assertTrue(connectedChannelMock.isReadResumed());
        assertFalse(sslChannel.isWriteResumed());
        assertFalse(connectedChannelMock.isWriteResumed());
        // need wrap is the first handshake action, and connectedChannelMock does not allow flush
        engineMock.setHandshakeActions(HandshakeAction.NEED_WRAP, HandshakeAction.FINISH);
        connectedChannelMock.enableFlush(false);

        // force resume writes on a readNeedsWrap scenario
        assertEquals(0, sslChannel.read(ByteBuffer.allocate(5)));
        assertTrue(sslChannel.isReadResumed());
        assertFalse(connectedChannelMock.isReadResumed());
        assertFalse(sslChannel.isWriteResumed());
        assertTrue(connectedChannelMock.isWriteResumed());

        // suspendReads
        sslChannel.suspendReads();
        assertFalse(sslChannel.isReadResumed());
        assertFalse(connectedChannelMock.isReadResumed());
        assertFalse(sslChannel.isWriteResumed());
        // FIXME assertFalse(connectedChannelMock.isWriteResumed());
    }

    @Test
    public void resumeAndSuspendWritesOnNewChannel() throws IOException {
        // brand newly created sslChannel, isWritable returns aLWAYS and resuming writes will awakeWrites for connectedChannelMock
        assertFalse(sslChannel.isWriteResumed());
        assertFalse(connectedChannelMock.isWriteResumed());
        sslChannel.resumeWrites();
        assertTrue(sslChannel.isWriteResumed());
        assertTrue(connectedChannelMock.isWriteAwaken());
        sslChannel.suspendWrites();
        assertFalse(sslChannel.isWriteResumed());
        assertFalse(connectedChannelMock.isWriteResumed());
    }

    @Test
    public void resumeAndSuspendWrites() throws IOException {
        assertEquals(0, sslChannel.read(ByteBuffer.allocate(5)));
        
        // not a brand newly created sslChannel, isWritable returns OKAY and resuming writes will just reasumeWritesfor connectedChannelMock
        assertFalse(sslChannel.isWriteResumed());
        assertFalse(connectedChannelMock.isWriteResumed());
        sslChannel.resumeWrites();
        assertTrue(sslChannel.isWriteResumed());
        assertFalse(connectedChannelMock.isWriteAwaken());
        assertTrue(connectedChannelMock.isWriteResumed());
        sslChannel.suspendWrites();
        assertFalse(sslChannel.isWriteResumed());
        assertFalse(connectedChannelMock.isWriteResumed());
    }

    @Test
    public void resumeAndSuspendWritesOnWriteNeedsUnwrapChannel() throws IOException {
        // create the read needs wrap channel\
        engineMock.setHandshakeActions(HandshakeAction.NEED_UNWRAP, HandshakeAction.FINISH);
        ByteBuffer buffer = ByteBuffer.allocate(5);
        buffer.put("12345".getBytes("UTF-8")).flip();
        assertEquals(0, sslChannel.write(buffer));
        assertTrue(connectedChannelMock.isReadResumed());
        assertFalse(sslChannel.isReadResumed());

        // with write needs unwrap, resuming writes will do nothing for connectedChannelMock, as isWritable returns NEVER
        assertFalse(sslChannel.isWriteResumed());
        assertFalse(connectedChannelMock.isWriteResumed());
        sslChannel.resumeWrites();
        assertTrue(sslChannel.isWriteResumed());
        assertFalse(connectedChannelMock.isWriteResumed());
        sslChannel.suspendWrites();
        assertFalse(sslChannel.isWriteResumed());
        assertFalse(connectedChannelMock.isWriteResumed());
    }

    @Test
    public void suspendWritesOnResumedWriteNeedsUnwrapChannel() throws IOException {
        // resume writes first of all
        sslChannel.resumeWrites();
        assertTrue(sslChannel.isWriteResumed());
        assertTrue(connectedChannelMock.isWriteResumed());
        assertFalse(sslChannel.isReadResumed());
        assertFalse(connectedChannelMock.isReadResumed());
        // need unwrap is the first handshake action, and connectedChannelMock has read ops disabled
        engineMock.setHandshakeActions(HandshakeAction.NEED_UNWRAP, HandshakeAction.FINISH);

        // force resume reads on a writeNeedsunwrap scenario
        ByteBuffer buffer = ByteBuffer.allocate(1);
        buffer.put((byte) 0).flip();
        assertEquals(0, sslChannel.write(buffer));
        assertTrue(sslChannel.isWriteResumed());
        assertFalse(connectedChannelMock.isWriteResumed());
        assertFalse(sslChannel.isReadResumed());
        assertTrue(connectedChannelMock.isReadResumed());

        // suspendWrites 
        sslChannel.suspendWrites();
        assertFalse(sslChannel.isWriteResumed());
        assertFalse(connectedChannelMock.isWriteResumed());
        assertFalse(sslChannel.isReadResumed());
        // FIXME assertFalse(connectedChannelMock.isReadResumed());
    }
}