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
package org.xnio.racecondition;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;

import org.jboss.byteman.contrib.bmunit.BMScript;
import org.jboss.byteman.contrib.bmunit.BMUnitRunner;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.channels.ConnectedSslStreamChannel;
import org.xnio.ssl.AbstractJsseConnectedSslStreamChannelTest;
import org.xnio.ssl.mock.SSLEngineMock;
import org.xnio.ssl.mock.SSLEngineMock.HandshakeAction;

/**
 * Close an JsseConnectedSslStreamChannel that is executing a write request.
 * The close action takes place at the exact moment the channel is attempting to unwrap to handle handshake.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
@RunWith(BMUnitRunner.class)
@BMScript(dir="src/test/resources")
public class CloseWritingSslChannelTestCase extends AbstractJsseConnectedSslStreamChannelTest {
    @Test
    public void testConnectedChannelAndEngineClosed() throws InterruptedException {
        test();
    }

    @Test
    public void testWithEngineClosedOnly() throws InterruptedException {
        connectedChannelMock.enableClosedChek(false);
        test();
    }
    
    public void test() throws InterruptedException {
        connectedChannelMock.setReadData(SSLEngineMock.HANDSHAKE_MSG);
        connectedChannelMock.enableRead(true);
        engineMock.setHandshakeActions(HandshakeAction.NEED_UNWRAP, HandshakeAction.FINISH);
        WriteToChannel writeRunnable = new WriteToChannel(sslChannel);
        Thread readThread = new Thread(writeRunnable);
        Thread closeThread = new Thread(new CloseChannel(sslChannel));
        readThread.start();
        closeThread.start();
        readThread.join();
        closeThread.join();
        assertTrue(writeRunnable.hasFailed());
    }

    private static class WriteToChannel implements Runnable {
        private final ConnectedSslStreamChannel channel;
        private boolean failed = false;
        public WriteToChannel(ConnectedSslStreamChannel channel) {
            this.channel = channel;
        }

        @Override
        public void run() {
            ByteBuffer buffer = ByteBuffer.allocate(10);
            try {
                buffer.put("write data".getBytes("UTF-8")).flip();
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
            try {
                assertEquals(10, channel.write(buffer));
            } catch (ClosedChannelException e) {
                e.printStackTrace();
                failed = true;
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }

        public boolean hasFailed() {
            return failed;
        }
    }

    private static class CloseChannel implements Runnable {
        private final ConnectedSslStreamChannel channel;
        public CloseChannel(ConnectedSslStreamChannel channel) {
            this.channel = channel;
        }

        @Override
        public void run() {
            try {
                channel.close();
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }
}
