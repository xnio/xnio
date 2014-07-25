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

package org.xnio.racecondition;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;

import org.jboss.byteman.contrib.bmunit.BMScript;
import org.jboss.byteman.contrib.bmunit.BMUnitRunner;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.channels.ConnectedSslStreamChannel;
import org.xnio.ssl.AbstractConnectedSslStreamChannelTest;
import org.xnio.ssl.mock.SSLEngineMock.HandshakeAction;

/**
 * Close an JsseConnectedSslStreamChannel that is executing a read request.
 * The close action takes place at the exact moment the channel is attempting to wrap to handle handshake.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
@RunWith(BMUnitRunner.class)
@BMScript(dir="src/test/resources")
public class CloseReadingSslChannelTestCase extends AbstractConnectedSslStreamChannelTest {
    @Test
    public void test() throws InterruptedException {
        conduitMock.setReadData("read data");
        conduitMock.enableReads(true);
        engineMock.setHandshakeActions(HandshakeAction.NEED_WRAP, HandshakeAction.NEED_WRAP, HandshakeAction.FINISH);
        ReadFromChannel readRunnable = new ReadFromChannel(sslChannel);
        Thread readThread = new Thread(readRunnable);
        Thread closeThread = new Thread(new CloseChannel(sslChannel));
        readThread.start();
        closeThread.start();
        readThread.join();
        closeThread.join();
        assertFalse(readRunnable.hasFailed());
    }

    private static class ReadFromChannel implements Runnable {
        private final ConnectedSslStreamChannel channel;
        private boolean failed = false;
        public ReadFromChannel(ConnectedSslStreamChannel channel) {
            this.channel = channel;
        }

        @Override
        public void run() {
            try {
                assertEquals(9, channel.read(ByteBuffer.allocate(10)));
            } catch (ClosedChannelException e) {
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
