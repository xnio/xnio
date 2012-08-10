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
package org.xnio.nio.test.racecondition;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

import org.jboss.byteman.contrib.bmunit.BMScript;
import org.jboss.byteman.contrib.bmunit.BMUnitRunner;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.ConnectedStreamChannel;

/**
 * AwaitAcceptable on a server that is about to reach its connection limit.
 * After awaitAcceptable has checked twice that CONN_FULL is not set, force the server to reach its connection limit
 * before awaitAcceptable performs a last check for CONN_FULL.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
@RunWith(BMUnitRunner.class)
@BMScript(dir="src/test/resources")
public class SetConnFullDuringAwaitTestCase {
    private static final int SERVER_PORT = 2345;

    @After
    public void afterTest() {
        System.out.println(">>>>>>>>>>>>..CLEARING");
    }
    
    @Test
    public void plainAwait() throws Exception {
        
        final Xnio xnio = Xnio.getInstance("nio", UnsetConnFullDuringAwaitTestCase.class.getClassLoader());
        final XnioWorker worker = xnio.createWorker(OptionMap.create(Options.WORKER_WRITE_THREADS, 2, Options.WORKER_READ_THREADS, 2));
        try {
            final AcceptingChannel<? extends ConnectedStreamChannel> server = worker.createStreamServer(
                    new InetSocketAddress(Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 }), SERVER_PORT),
                    null, OptionMap.create(Options.REUSE_ADDRESSES, Boolean.TRUE, Options.CONNECTION_HIGH_WATER, 20));
            System.out.println("server class: " + server.getClass());
            server.resumeAccepts();

            // start awaitAcceptable thread
            final Runnable acceptableAwaiter = new AcceptableAwaiter(server);
            final Thread acceptableAwaiterThread = new Thread(acceptableAwaiter);
            acceptableAwaiterThread.start();

            // start performing connections
            final Collection<ConnectedStreamChannel> channels = new ArrayList<ConnectedStreamChannel>();
            while (true) {
                final SocketChannel channel;
                try {
                    channel = SocketChannel.open();
                } catch (SocketException e) {
                    System.out.println("Opened " + channels.size());
                    throw e;
                }
                channel.configureBlocking(false);
                channel.socket().bind(new InetSocketAddress(0));
                channel.connect(new InetSocketAddress(Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 }), SERVER_PORT));
                final ConnectedStreamChannel connectedChannel = server.accept();
                if (connectedChannel == null) {
                    break;
                }
                 if (!channel.finishConnect()) {
                     break;
                 }
                 channels.add(connectedChannel);
            }

            // try to join, it will fail
            acceptableAwaiterThread.join(200);
            assertTrue(acceptableAwaiterThread.isAlive());

            for (ConnectedStreamChannel channel: channels) {
                channel.close();
            }

            acceptableAwaiterThread.join();
        } finally {
            worker.shutdown();
            worker.awaitTermination(1L, TimeUnit.MINUTES);
        }
    }

    @Test
    public void timeoutAwait() throws Exception {
        final Xnio xnio = Xnio.getInstance("nio", UnsetConnFullDuringAwaitTestCase.class.getClassLoader());
        final XnioWorker worker = xnio.createWorker(OptionMap.create(Options.WORKER_WRITE_THREADS, 2, Options.WORKER_READ_THREADS, 2));
        try {
            final AcceptingChannel<? extends ConnectedStreamChannel> server = worker.createStreamServer(
                    new InetSocketAddress(Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 }), SERVER_PORT),
                    null, OptionMap.create(Options.REUSE_ADDRESSES, Boolean.TRUE, Options.CONNECTION_HIGH_WATER, 20));
            System.out.println("server class: " + server.getClass());
            server.resumeAccepts();

            // start awaitAcceptable thread
            final Runnable acceptableAwaiter = new AcceptableAwaiter(server, Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            final Thread acceptableAwaiterThread = new Thread(acceptableAwaiter);
            acceptableAwaiterThread.start();

            // start performing connections
            final Collection<ConnectedStreamChannel> channels = new ArrayList<ConnectedStreamChannel>();
            while (true) {
                final SocketChannel channel;
                try {
                    channel = SocketChannel.open();
                } catch (SocketException e) {
                    System.out.println("Opened " + channels.size());
                    throw e;
                }
                channel.configureBlocking(false);
                channel.socket().bind(new InetSocketAddress(0));
                channel.connect(new InetSocketAddress(Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 }), SERVER_PORT));
                final ConnectedStreamChannel connectedChannel = server.accept();
                if (connectedChannel == null) {
                    break;
                }
                 if (!channel.finishConnect()) {
                     break;
                 }
                 channels.add(connectedChannel);
            }

            // try to join, it will fail
            acceptableAwaiterThread.join(200);
            assertTrue(acceptableAwaiterThread.isAlive());

            for (ConnectedStreamChannel channel: channels) {
                channel.close();
            }

            acceptableAwaiterThread.join();
        } finally {
            worker.shutdown();
            worker.awaitTermination(1L, TimeUnit.MINUTES);
        }
    }

    @Test
    public void timeoutAwaitExpires() throws Exception {
        final Xnio xnio = Xnio.getInstance("nio", UnsetConnFullDuringAwaitTestCase.class.getClassLoader());
        final XnioWorker worker = xnio.createWorker(OptionMap.create(Options.WORKER_WRITE_THREADS, 2, Options.WORKER_READ_THREADS, 2));
        try {
            final AcceptingChannel<? extends ConnectedStreamChannel> server = worker.createStreamServer(
                    new InetSocketAddress(Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 }), SERVER_PORT),
                    null, OptionMap.create(Options.REUSE_ADDRESSES, Boolean.TRUE, Options.CONNECTION_HIGH_WATER, 20));
            System.out.println("server class: " + server.getClass());
            server.resumeAccepts();

            // start awaitAcceptable thread
            final Runnable acceptableAwaiter = new AcceptableAwaiter(server, 10, TimeUnit.MILLISECONDS);
            final Thread acceptableAwaiterThread = new Thread(acceptableAwaiter);
            acceptableAwaiterThread.start();

            // start performing connections
            final Collection<ConnectedStreamChannel> channels = new ArrayList<ConnectedStreamChannel>();
            while (true) {
                final SocketChannel channel;
                try {
                    channel = SocketChannel.open();
                } catch (SocketException e) {
                    System.out.println("Opened " + channels.size());
                    throw e;
                }
                channel.configureBlocking(false);
                channel.socket().bind(new InetSocketAddress(0));
                channel.connect(new InetSocketAddress(Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 }), SERVER_PORT));
                final ConnectedStreamChannel connectedChannel = server.accept();
                if (connectedChannel == null) {
                    break;
                }
                 if (!channel.finishConnect()) {
                     break;
                 }
                 channels.add(connectedChannel);
            }

            // join, the await should timeout
            acceptableAwaiterThread.join();

            for (ConnectedStreamChannel channel: channels) {
                channel.close();
            }

        } finally {
            worker.shutdown();
            worker.awaitTermination(1L, TimeUnit.MINUTES);
        }
    }

    private static class AcceptableAwaiter implements Runnable {

        private final AcceptingChannel<? extends ConnectedStreamChannel> acceptingChannel;
        private final long timeout;
        private final TimeUnit timeoutUnit;

        public AcceptableAwaiter(AcceptingChannel<? extends ConnectedStreamChannel> c, long t, TimeUnit tu) {
            acceptingChannel = c;
            timeout = t;
            timeoutUnit = tu;
        }

        public AcceptableAwaiter(AcceptingChannel<? extends ConnectedStreamChannel> c) {
            this(c, -1, null);
        }

        public void run() {
            try {
                if (timeoutUnit == null) {
                    acceptingChannel.awaitAcceptable();
                } else {
                    acceptingChannel.awaitAcceptable(timeout, timeoutUnit);
                }
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }
}
