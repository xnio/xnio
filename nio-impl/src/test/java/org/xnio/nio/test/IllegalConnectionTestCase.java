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
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.ConnectedStreamChannel;

/**
 * Test case for scenarios where an attempt to perform an illegal connection is made.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 *
 */
public class IllegalConnectionTestCase {

    private static SocketAddress bindAddress;
    private static XnioWorker worker;

    @BeforeClass
    public static void createWorker() throws IOException {
        final Xnio xnio = Xnio.getInstance("nio", AcceptChannelTestCase.class.getClassLoader());
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
        IllegalArgumentException expected = null;
        try {
            worker.createStreamServer(bindAddress, null, OptionMap.create(Options.WORKER_ACCEPT_THREADS, 5, Options.CONNECTION_HIGH_WATER, 20));
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            worker.createStreamServer(bindAddress, null, OptionMap.create(Options.WORKER_ACCEPT_THREADS, 4, Options.WORKER_ESTABLISH_WRITING, true));
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        final AcceptingChannel<? extends ConnectedStreamChannel> server = worker.createStreamServer(bindAddress, null,
                OptionMap.create(Options.WORKER_ACCEPT_THREADS, 0));
        expected = null;
        try {
            server.wakeupAccepts();
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        server.close();
        assertNotNull(expected);

        expected = null;
        try {
            worker.createStreamServer(bindAddress, null, OptionMap.create(Options.WORKER_ACCEPT_THREADS, -5));
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        final Xnio xnio = Xnio.getInstance("nio", AcceptChannelTestCase.class.getClassLoader());
        XnioWorker zeroThreadWorker = xnio.createWorker(OptionMap.create(Options.WORKER_WRITE_THREADS, 0, Options.WORKER_READ_THREADS, 0));
        expected = null;
        try {
            zeroThreadWorker.createStreamServer(bindAddress, null, OptionMap.create(Options.WORKER_ACCEPT_THREADS, 1, Options.WORKER_ESTABLISH_WRITING, true));
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            zeroThreadWorker.createStreamServer(bindAddress, null, OptionMap.create(Options.WORKER_ACCEPT_THREADS, 1, Options.WORKER_ESTABLISH_WRITING, false));
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    @Test
    public void illegalReceiveBufferSize() throws IOException {
        IllegalArgumentException expected = null;
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
            worker.createStreamServer(bindAddress, null, OptionMap.create(Options.CONNECTION_HIGH_WATER, 1 << 20));
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
        
        final AcceptingChannel<? extends ConnectedStreamChannel> server = worker.createStreamServer(bindAddress, null,
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
    }

}
