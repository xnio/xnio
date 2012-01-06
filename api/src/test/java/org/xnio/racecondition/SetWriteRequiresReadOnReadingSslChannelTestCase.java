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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import org.jboss.byteman.contrib.bmunit.BMScript;
import org.jboss.byteman.contrib.bmunit.BMUnitRunner;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.channels.ConnectedSslStreamChannel;
import org.xnio.mock.ConnectedStreamChannelMock;
import org.xnio.ssl.AbstractJsseConnectedSslStreamChannelTest;
import org.xnio.ssl.mock.SSLEngineMock.HandshakeAction;

/**
 * Attempt to force a scenario where a write setsWriteRequiresRead on a ssl channel while there is a read
 * thread attempting to setReadRequiresWrite.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 *
 */
@RunWith(BMUnitRunner.class)
@BMScript(dir="src/test/resources")
public class SetWriteRequiresReadOnReadingSslChannelTestCase extends AbstractJsseConnectedSslStreamChannelTest {

    @Test
    public void test() throws InterruptedException {
        engineMock.setHandshakeActions(HandshakeAction.NEED_WRAP, HandshakeAction.NEED_UNWRAP);
        connectedChannelMock.enableWrite(false);
        Thread readThread = new Thread(new Read(sslChannel));
        Thread writeThread = new Thread(new Write(sslChannel, connectedChannelMock));
        readThread.start();
        writeThread.start();
        readThread.join();
        writeThread.join();
    }

    private static class Read implements Runnable {
        private ConnectedSslStreamChannel channel;

        public Read(ConnectedSslStreamChannel channel) {
            this.channel = channel;
        }

        public void run() {
            try {
                assertEquals(0, channel.read(ByteBuffer.allocate(15)));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static class Write implements Runnable {
        private ConnectedSslStreamChannel channel;
        private ConnectedStreamChannelMock connectedChannelMock;

        public Write(ConnectedSslStreamChannel channel, ConnectedStreamChannelMock connectedChannelMock) {
            this.channel = channel;
            this.connectedChannelMock = connectedChannelMock;
        }

        public void run() {
            ByteBuffer buffer = ByteBuffer.allocate(10);
            try {
                connectedChannelMock.enableWrite(true);
                buffer.put("message".getBytes("UTF-8")).flip();
                assertEquals(0, channel.write(buffer));
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            
        }
    }
}
