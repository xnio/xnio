/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
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
package org.xnio.nio.test;

import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.StreamConnection;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.ConnectedStreamChannel;

/**
 * Test case for scenarios where an attempt to perform an illegal connection is made.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 *
 */
@SuppressWarnings("deprecation")
public class IllegalConnectionTestCase {

    private static SocketAddress bindAddress;
    private static XnioWorker worker;

    @BeforeClass
    public static void createWorker() throws IOException {
        final Xnio xnio = Xnio.getInstance("nio", IllegalConnectionTestCase.class.getClassLoader());
        worker = xnio.createWorker(OptionMap.create(Options.WORKER_WRITE_THREADS, 3, Options.WORKER_READ_THREADS, 4));
        bindAddress = new InetSocketAddress(Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 }), 12345);
    }

    @AfterClass
    public static void destroyWorker() throws InterruptedException {
        worker.shutdown();
        worker.awaitTermination(1L, TimeUnit.MINUTES);
    }

    @Test
    public void illegalAcceptThreads() throws IOException {
        IllegalArgumentException expected;

        final Xnio xnio = Xnio.getInstance("nio", getClass().getClassLoader());
        final XnioWorker zeroThreadWorker = xnio.createWorker(OptionMap.create(Options.WORKER_IO_THREADS, 0));

        expected = null;
        try {
            zeroThreadWorker.createStreamConnectionServer(bindAddress, null, OptionMap.create(Options.WORKER_ESTABLISH_WRITING, true));
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            zeroThreadWorker.createStreamConnectionServer(bindAddress, null, OptionMap.create(Options.WORKER_ESTABLISH_WRITING, false));
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            zeroThreadWorker.createStreamServer(bindAddress, null, OptionMap.create(Options.WORKER_ESTABLISH_WRITING, true));
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            zeroThreadWorker.createStreamServer(bindAddress, null, OptionMap.create(Options.WORKER_ESTABLISH_WRITING, false));
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    @Test
    public void illegalReceiveBufferSize() throws IOException {
        IllegalArgumentException expected = null;
        try {
            worker.createStreamConnectionServer(bindAddress, null, OptionMap.create(Options.RECEIVE_BUFFER, 0));
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            worker.createStreamConnectionServer(bindAddress, null, OptionMap.create(Options.RECEIVE_BUFFER, -1));
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            worker.createStreamServer(bindAddress, null, OptionMap.create(Options.RECEIVE_BUFFER, 0));
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            worker.createStreamServer(bindAddress, null, OptionMap.create(Options.RECEIVE_BUFFER, -1));
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    @Test
    public void illegalLowWaterMark() throws IOException {
        IllegalArgumentException expected = null;
        try {
            worker.createStreamConnectionServer(bindAddress, null, OptionMap.create(Options.CONNECTION_HIGH_WATER, 2, Options.CONNECTION_LOW_WATER, 3));
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            worker.createStreamConnectionServer(bindAddress, null, OptionMap.create(Options.CONNECTION_LOW_WATER, -1));
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            worker.createStreamServer(bindAddress, null, OptionMap.create(Options.CONNECTION_HIGH_WATER, 2, Options.CONNECTION_LOW_WATER, 3));
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            worker.createStreamServer(bindAddress, null, OptionMap.create(Options.CONNECTION_LOW_WATER, -1));
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    @Test
    public void illegalHighWaterMark() throws IOException {
        IllegalArgumentException expected = null;
        try {
            worker.createStreamConnectionServer(bindAddress, null, OptionMap.create(Options.CONNECTION_HIGH_WATER, -1));
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            worker.createStreamServer(bindAddress, null, OptionMap.create(Options.CONNECTION_HIGH_WATER, -1));
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    @Test
    public void illegalSendBufferSize() throws IOException {
        IllegalArgumentException expected = null;
        try {
           worker.createStreamConnectionServer(bindAddress, null, OptionMap.create(Options.SEND_BUFFER, 0));
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            worker.createStreamConnectionServer(bindAddress, null, OptionMap.create(Options.SEND_BUFFER, -2));
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        final AcceptingChannel<? extends StreamConnection> server = worker.createStreamConnectionServer(bindAddress, null,
                OptionMap.EMPTY);
        expected = null;
        try {
            server.setOption(Options.SEND_BUFFER, 0);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        server.close();
        assertNotNull(expected);

        expected = null;
        try {
            server.setOption(Options.SEND_BUFFER, -5);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        server.close();
        assertNotNull(expected);
        
        expected = null;
        try {
           worker.createStreamServer(bindAddress, null, OptionMap.create(Options.SEND_BUFFER, 0));
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            worker.createStreamServer(bindAddress, null, OptionMap.create(Options.SEND_BUFFER, -2));
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        final AcceptingChannel<? extends ConnectedStreamChannel> serverChannel = worker.createStreamServer(bindAddress, null,
                OptionMap.EMPTY);
        expected = null;
        try {
            serverChannel.setOption(Options.SEND_BUFFER, 0);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        serverChannel.close();
        assertNotNull(expected);

        expected = null;
        try {
            serverChannel.setOption(Options.SEND_BUFFER, -5);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        serverChannel.close();
        assertNotNull(expected);
    }

}
