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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import org.jboss.byteman.contrib.bmunit.BMScript;
import org.jboss.byteman.contrib.bmunit.BMUnitRunner;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.channels.ConnectedSslStreamChannel;
import org.xnio.mock.ConduitMock;
import org.xnio.ssl.AbstractConnectedSslStreamChannelTest;
import org.xnio.ssl.mock.SSLEngineMock.HandshakeAction;

/**
 * Attempt to force a scenario where a write setsWriteRequiresRead on a ssl channel while there is a read
 * thread attempting to setReadRequiresWrite.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 *
 */
@Ignore
@RunWith(BMUnitRunner.class)
@BMScript(dir="src/test/resources")
public class SetWriteRequiresReadOnReadingSslChannelTestCase extends AbstractConnectedSslStreamChannelTest {

    @Test
    public void test() throws InterruptedException {
        engineMock.setHandshakeActions(HandshakeAction.NEED_WRAP, HandshakeAction.NEED_UNWRAP);
        conduitMock.enableWrites(false);
        Thread readThread = new Thread(new Read(sslChannel));
        Thread writeThread = new Thread(new Write(sslChannel, conduitMock));
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
        private ConduitMock conduitMock;

        public Write(ConnectedSslStreamChannel channel, ConduitMock conduitMock) {
            this.channel = channel;
            this.conduitMock = conduitMock;
        }

        public void run() {
            ByteBuffer buffer = ByteBuffer.allocate(10);
            try {
                conduitMock.enableWrites(true);
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
