/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.xnio.ChannelListener;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.BoundChannel;
import org.xnio.channels.ConnectedStreamChannel;

/**
 * Test for accept operations at the TCP server.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 *
 */
public final class AcceptChannelTestCase extends TcpServerTest {

    @Test
    public void awaitAcceptable() throws Exception {
       createServer(OptionMap.create(Options.REUSE_ADDRESSES, Boolean.TRUE));
       final Runnable acceptableAwaiter = new AcceptableAwaiter(server);
       assertTrue(server.getClass().getName().endsWith("NioTcpServer"));
       final Thread acceptableAwaiterThread = new Thread(acceptableAwaiter);
       acceptableAwaiterThread.start();
       acceptableAwaiterThread.join(500);
       assertTrue(acceptableAwaiterThread.isAlive());
       assertNull(server.accept());
       server.resumeAccepts();
       assertNull(server.accept());

       final SocketChannel channel = SocketChannel.open();
       channel.configureBlocking(false);
       channel.socket().bind(new InetSocketAddress(0));
       channel.connect(new InetSocketAddress(Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 }), SERVER_PORT));
       assertNotNull(channel.finishConnect());
       acceptableAwaiterThread.join();
       channel.close();

       server.suspendAccepts();
    }

    @Test
    public void awaitAcceptableWithMultipleAwaiters() throws Exception {
        createServer(OptionMap.create(Options.REUSE_ADDRESSES, Boolean.TRUE, Options.KEEP_ALIVE, true));

        final Runnable acceptableAwaiter1 = new AcceptableAwaiter(server);
        final Runnable acceptableAwaiter2 = new AcceptableAwaiter(server);
        final Runnable acceptableAwaiter3 = new AcceptableAwaiter(server);
        final Runnable acceptableAwaiter4 = new AcceptableAwaiter(server);
        final Runnable acceptableAwaiter5 = new AcceptableAwaiter(server);

        final Thread acceptableAwaiterThread1 = new Thread(acceptableAwaiter1);
        final Thread acceptableAwaiterThread2 = new Thread(acceptableAwaiter2);
        final Thread acceptableAwaiterThread3 = new Thread(acceptableAwaiter3);
        final Thread acceptableAwaiterThread4 = new Thread(acceptableAwaiter4);
        final Thread acceptableAwaiterThread5 = new Thread(acceptableAwaiter5);

        acceptableAwaiterThread1.start();
        acceptableAwaiterThread1.join(50);
        acceptableAwaiterThread2.start();
        acceptableAwaiterThread2.join(50);
        acceptableAwaiterThread3.start();
        acceptableAwaiterThread3.join(50);
        acceptableAwaiterThread4.start();
        acceptableAwaiterThread4.join(50);
        acceptableAwaiterThread5.start();
        acceptableAwaiterThread5.join(500);
        assertTrue(acceptableAwaiterThread1.isAlive());
        assertTrue(acceptableAwaiterThread2.isAlive());
        assertTrue(acceptableAwaiterThread3.isAlive());
        assertTrue(acceptableAwaiterThread4.isAlive());
        assertTrue(acceptableAwaiterThread5.isAlive());

        server.resumeAccepts();

        final SocketChannel channel = SocketChannel.open();
        channel.configureBlocking(false);
        channel.socket().bind(new InetSocketAddress(0));
        channel.connect(new InetSocketAddress(Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 }), SERVER_PORT));
        assertNotNull(channel.finishConnect());
        acceptableAwaiterThread1.join();
        acceptableAwaiterThread2.join();
        acceptableAwaiterThread3.join();
        acceptableAwaiterThread4.join();
        acceptableAwaiterThread5.join();
        channel.close();
    }

    @Test
    public void awaitAcceptableWithConnectionFull() throws Exception {
        createServer(OptionMap.create(Options.TCP_OOB_INLINE, Boolean.TRUE, Options.CONNECTION_HIGH_WATER, 20));
        server.resumeAccepts();

        final Collection<ConnectedStreamChannel> channels = new ArrayList<ConnectedStreamChannel>();
        while (true) {
            final ConnectedStreamChannel connectedChannel = attemptToConnect(server);
            if (connectedChannel == null) {
                break;
            }
            channels.add(connectedChannel);
        }

        final Runnable acceptableAwaiter = new AcceptableAwaiter(server);
        final Thread acceptableAwaiterThread = new Thread(acceptableAwaiter);
        acceptableAwaiterThread.start();
        acceptableAwaiterThread.join(100);
        assertTrue(acceptableAwaiterThread.isAlive());

        for (ConnectedStreamChannel channel: channels) {
            channel.close();
        }
        acceptableAwaiterThread.join();
    }

    @Test
    public void awaitAcceptableWithConnectionFullMultipleAwaiters() throws Exception {
        createServer(OptionMap.create(Options.TCP_NODELAY, Boolean.TRUE, Options.CONNECTION_HIGH_WATER, 20));
        server.resumeAccepts();

        final Collection<ConnectedStreamChannel> channels = new ArrayList<ConnectedStreamChannel>();
        while (true) {
            final ConnectedStreamChannel connectedChannel = attemptToConnect(server);
            if (connectedChannel == null) {
                break;
            }
            channels.add(connectedChannel);
        }

        final Runnable acceptableAwaiter1 = new AcceptableAwaiter(server);
        final Runnable acceptableAwaiter2 = new AcceptableAwaiter(server);
        final Runnable acceptableAwaiter3 = new AcceptableAwaiter(server);
        final Thread acceptableAwaiterThread1 = new Thread(acceptableAwaiter1);
        final Thread acceptableAwaiterThread2 = new Thread(acceptableAwaiter2);
        final Thread acceptableAwaiterThread3 = new Thread(acceptableAwaiter3);
        acceptableAwaiterThread1.start();
        acceptableAwaiterThread1.join(100);
        acceptableAwaiterThread2.start();
        acceptableAwaiterThread2.join(100);
        acceptableAwaiterThread3.start();
        acceptableAwaiterThread3.join(100);
        assertTrue(acceptableAwaiterThread1.isAlive());
        assertTrue(acceptableAwaiterThread2.isAlive());
        assertTrue(acceptableAwaiterThread3.isAlive());

        // suspend accept before closing channels for a change
        server.suspendAccepts();
        for (ConnectedStreamChannel channel: channels) {
            channel.close();
        }
        acceptableAwaiterThread1.join();
        acceptableAwaiterThread2.join();
        acceptableAwaiterThread3.join();
    }

    @Test
    public void interruptAwaiter() throws Exception {
        createServer(OptionMap.create(Options.REUSE_ADDRESSES, Boolean.TRUE, Options.SEND_BUFFER, 15000));

        final Runnable acceptableAwaiter = new AcceptableAwaiter(server);
        assertTrue(server.getClass().getName().endsWith("NioTcpServer"));
        final Thread acceptableAwaiterThread = new Thread(acceptableAwaiter);
        acceptableAwaiterThread.start();
        acceptableAwaiterThread.join(50);
        assertTrue(acceptableAwaiterThread.isAlive());
        server.resumeAccepts();
        assertNull(server.accept());

        acceptableAwaiterThread.interrupt();
        acceptableAwaiterThread.join();
    }

    @Test
    public void interruptAwaiterWithConnectionFull() throws Exception { //final Runnable body, final ChannelListener<? super ConnectedStreamChannel> clientHandler, final ChannelListener<? super ConnectedStreamChannel> serverHandler) throws Exception {
        createServer(OptionMap.create(Options.KEEP_ALIVE, Boolean.TRUE, Options.CONNECTION_HIGH_WATER, 20));
        server.resumeAccepts();

        final Collection<ConnectedStreamChannel> channels = new ArrayList<ConnectedStreamChannel>();
        while (true) {
            final ConnectedStreamChannel connectedChannel = attemptToConnect(server);
            if (connectedChannel == null) {
                break;
            }
            channels.add(connectedChannel);
        }

        final Runnable acceptableAwaiter = new AcceptableAwaiter(server);
        final Thread acceptableAwaiterThread = new Thread(acceptableAwaiter);
        acceptableAwaiterThread.start();
        acceptableAwaiterThread.join(100);
        assertTrue(acceptableAwaiterThread.isAlive());

        acceptableAwaiterThread.interrupt();
        acceptableAwaiterThread.join();
    }

    @Test
    public void awaitAcceptableWithTimeout() throws Exception {
        createServer(OptionMap.create(Options.REUSE_ADDRESSES, Boolean.TRUE));

        final Runnable acceptableAwaiter = new AcceptableAwaiter(server, 300, TimeUnit.SECONDS);
        assertTrue(server.getClass().getName().endsWith("NioTcpServer"));
        final Thread acceptableAwaiterThread = new Thread(acceptableAwaiter);
        acceptableAwaiterThread.start();
        acceptableAwaiterThread.join(500);
        assertTrue(acceptableAwaiterThread.isAlive());
        server.resumeAccepts();
        assertNull(server.accept());

        final SocketChannel channel = SocketChannel.open();
        channel.configureBlocking(false);
        channel.socket().bind(new InetSocketAddress(0));
        channel.connect(new InetSocketAddress(Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 }), SERVER_PORT));
        assertNotNull(channel.finishConnect());
        acceptableAwaiterThread.join();
        channel.close();
    }

    @Test
    public void awaitAcceptableWithMultipleTimeoutAwaiters() throws Exception {
        createServer(OptionMap.create(Options.REUSE_ADDRESSES, Boolean.TRUE));

        final Runnable acceptableAwaiter1 = new AcceptableAwaiter(server,  1, TimeUnit.MINUTES);
        final Runnable acceptableAwaiter2 = new AcceptableAwaiter(server, 10, TimeUnit.SECONDS);
        final Runnable acceptableAwaiter3 = new AcceptableAwaiter(server, 1000, TimeUnit.MILLISECONDS);
        final Runnable acceptableAwaiter4 = new AcceptableAwaiter(server, 1000, TimeUnit.MICROSECONDS);
        final Runnable acceptableAwaiter5 = new AcceptableAwaiter(server, 1000l, TimeUnit.NANOSECONDS);

        final Thread acceptableAwaiterThread1 = new Thread(acceptableAwaiter1);
        final Thread acceptableAwaiterThread2 = new Thread(acceptableAwaiter2);
        final Thread acceptableAwaiterThread3 = new Thread(acceptableAwaiter3);
        final Thread acceptableAwaiterThread4 = new Thread(acceptableAwaiter4);
        final Thread acceptableAwaiterThread5 = new Thread(acceptableAwaiter5);

        acceptableAwaiterThread1.start();
        acceptableAwaiterThread1.join(50);
        acceptableAwaiterThread2.start();
        acceptableAwaiterThread2.join(50);
        acceptableAwaiterThread3.start();
        acceptableAwaiterThread3.join(50);
        acceptableAwaiterThread4.start();
        acceptableAwaiterThread4.join();
        acceptableAwaiterThread5.start();
        acceptableAwaiterThread5.join();
        assertTrue(acceptableAwaiterThread1.isAlive());
        assertTrue(acceptableAwaiterThread2.isAlive());
        assertTrue(acceptableAwaiterThread3.isAlive());
        assertFalse(acceptableAwaiterThread4.isAlive());
        assertFalse(acceptableAwaiterThread5.isAlive());

        server.resumeAccepts();

        final SocketChannel channel = SocketChannel.open();
        channel.configureBlocking(false);
        channel.socket().bind(new InetSocketAddress(0));
        channel.connect(new InetSocketAddress(Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 }), SERVER_PORT));
        assertNotNull(channel.finishConnect());
        acceptableAwaiterThread1.join();
        acceptableAwaiterThread2.join();
        acceptableAwaiterThread3.join();
        channel.close();
    }

    @Test
    public void awaitAcceptableWithConnectionFullAndTimeout() throws Exception { //final Runnable body, final ChannelListener<? super ConnectedStreamChannel> clientHandler, final ChannelListener<? super ConnectedStreamChannel> serverHandler) throws Exception {
        createServer(OptionMap.create(Options.REUSE_ADDRESSES, Boolean.TRUE, Options.CONNECTION_HIGH_WATER, 20));
        server.resumeAccepts();

        final Collection<ConnectedStreamChannel> channels = new ArrayList<ConnectedStreamChannel>();
        while (true) {
            final ConnectedStreamChannel connectedChannel = attemptToConnect(server);
            if (connectedChannel == null) {
                break;
            }
            channels.add(connectedChannel);
        }

        final Runnable acceptableAwaiter = new AcceptableAwaiter(server, 10000, TimeUnit.SECONDS);
        final Thread acceptableAwaiterThread = new Thread(acceptableAwaiter);
        acceptableAwaiterThread.start();
        acceptableAwaiterThread.join(100);
        assertTrue(acceptableAwaiterThread.isAlive());

        for (ConnectedStreamChannel channel: channels) {
            channel.close();
        }
        acceptableAwaiterThread.join();
    }

    @Test
    public void awaitAcceptableWithConnectionFullMultipleAwaitersWithTimeout() throws Exception {
        createServer(OptionMap.create(Options.SEND_BUFFER, 25000, Options.CONNECTION_HIGH_WATER, 20));
        server.resumeAccepts();

        final Collection<ConnectedStreamChannel> channels = new ArrayList<ConnectedStreamChannel>();
        while (true) {
            final ConnectedStreamChannel connectedChannel = attemptToConnect(server);
            if (connectedChannel == null) {
                break;
            }
            channels.add(connectedChannel);
        }

        final Runnable acceptableAwaiter1 = new AcceptableAwaiter(server, 8, TimeUnit.DAYS);
        final Runnable acceptableAwaiter2 = new AcceptableAwaiter(server, 4, TimeUnit.MINUTES);
        final Runnable acceptableAwaiter3 = new AcceptableAwaiter(server, 2, TimeUnit.MILLISECONDS);
        final Thread acceptableAwaiterThread1 = new Thread(acceptableAwaiter1);
        final Thread acceptableAwaiterThread2 = new Thread(acceptableAwaiter2);
        final Thread acceptableAwaiterThread3 = new Thread(acceptableAwaiter3);
        acceptableAwaiterThread1.start();
        acceptableAwaiterThread1.join(100);
        acceptableAwaiterThread2.start();
        acceptableAwaiterThread2.join(100);
        acceptableAwaiterThread3.start();
        acceptableAwaiterThread3.join(100);
        assertTrue(acceptableAwaiterThread1.isAlive());
        assertTrue(acceptableAwaiterThread2.isAlive());
        assertFalse(acceptableAwaiterThread3.isAlive());

        for (ConnectedStreamChannel channel: channels) {
            channel.close();
        }
        acceptableAwaiterThread1.join();
        acceptableAwaiterThread2.join();
    }

    @Test
    public void interruptTimeoutAwaiter() throws Exception { //final Runnable body, final ChannelListener<? super ConnectedStreamChannel> clientHandler, final ChannelListener<? super ConnectedStreamChannel> serverHandler) throws Exception {
        createServer(OptionMap.create(Options.REUSE_ADDRESSES, Boolean.TRUE));

        final Runnable acceptableAwaiter = new AcceptableAwaiter(server, 10, TimeUnit.MINUTES);
        assertTrue(server.getClass().getName().endsWith("NioTcpServer"));
        final Thread acceptableAwaiterThread = new Thread(acceptableAwaiter);
        acceptableAwaiterThread.start();
        acceptableAwaiterThread.join(50);
        assertTrue(acceptableAwaiterThread.isAlive());
        server.resumeAccepts();
        assertNull(server.accept());

        acceptableAwaiterThread.interrupt();
        acceptableAwaiterThread.join();
    }

    @Test
    public void interruptTimeoutAwaiterWithConnectionFull() throws Exception { //final Runnable body, final ChannelListener<? super ConnectedStreamChannel> clientHandler, final ChannelListener<? super ConnectedStreamChannel> serverHandler) throws Exception {
        createServer(OptionMap.create(Options.REUSE_ADDRESSES, Boolean.TRUE, Options.CONNECTION_HIGH_WATER, 20));
        server.resumeAccepts();

        final Collection<ConnectedStreamChannel> channels = new ArrayList<ConnectedStreamChannel>();
        while (true) {
            final ConnectedStreamChannel connectedChannel = attemptToConnect(server);
            if (connectedChannel == null) {
                break;
            }
            channels.add(connectedChannel);
        }

        final Runnable acceptableAwaiter = new AcceptableAwaiter(server, 1, TimeUnit.HOURS);
        final Thread acceptableAwaiterThread = new Thread(acceptableAwaiter);
        acceptableAwaiterThread.start();
        acceptableAwaiterThread.join(100);
        assertTrue(acceptableAwaiterThread.isAlive());

        acceptableAwaiterThread.interrupt();
        acceptableAwaiterThread.join();
    }

    @Test
    public void awakeAccepts() throws Exception {
        createServer(OptionMap.create(Options.WORKER_ACCEPT_THREADS, getWorkerReadThreads() == 1? 1: getWorkerReadThreads() -1, Options.CONNECTION_HIGH_WATER, 20));

        final AcceptListener listener = new AcceptListener();
        server.getAcceptSetter().set(listener);
        server.wakeupAccepts();

        Thread.sleep(500);

        assertEquals(1, listener.getNotificationsTotal());
        listener.checkAlwaysSameChannel();
        assertNotNull(listener.getTargetChannel());

        final Collection<ConnectedStreamChannel> channels = new ArrayList<ConnectedStreamChannel>();
        while (true) {
            final ConnectedStreamChannel connectedChannel = attemptToConnect(server);
            if (connectedChannel == null) {
                break;
            }
            channels.add(connectedChannel);
        }

        final Runnable acceptableAwaiter = new AcceptableAwaiter(server, 1, TimeUnit.HOURS);
        final Thread acceptableAwaiterThread = new Thread(acceptableAwaiter);
        acceptableAwaiterThread.start();
        acceptableAwaiterThread.join(100);
        assertTrue(acceptableAwaiterThread.isAlive());

        acceptableAwaiterThread.interrupt();
        acceptableAwaiterThread.join();
    }

    @Test
    public void connectionFullWithSetWaterMark() throws IOException, InterruptedException {
         createServer(OptionMap.create(Options.REUSE_ADDRESSES, Boolean.TRUE, Options.CONNECTION_HIGH_WATER, 20));
         server.resumeAccepts();

         final Collection<ConnectedStreamChannel> channels = new ArrayList<ConnectedStreamChannel>();
         for (int i = 0; i < 3; i++) {
             final ConnectedStreamChannel connectedChannel = attemptToConnect(server);
             assertNotNull(connectedChannel);
             channels.add(connectedChannel);
         }

         // force conn full status, by lowering the water mark limit
         assertEquals(20, (int) server.setOption(Options.CONNECTION_HIGH_WATER, 2));
         assertEquals(2, (int) server.getOption(Options.CONNECTION_HIGH_WATER));
         assertNull(attemptToConnect(server));

         // check channels are still open, depite the fact that there are more channels open than high water mark
         for (ConnectedStreamChannel channel: channels) {
             assertTrue(channel.isOpen());
         }

         // increase water mark limit, which will cause conn full status to be unset
         assertEquals(2, (int) server.setOption(Options.CONNECTION_HIGH_WATER, 10));
         assertEquals(10, (int) server.getOption(Options.CONNECTION_HIGH_WATER));
         assertNull(attemptToConnect(server));

         assertEquals(2, (int) server.setOption(Options.CONNECTION_LOW_WATER, 5));
         assertEquals(5, (int) server.getOption(Options.CONNECTION_LOW_WATER));

         while(true) {
             ConnectedStreamChannel channel = attemptToConnect(server);
             if (channel == null) {
                 break;
             } else {
                 channels.add(channel);
             }
         }
         assertEquals(10, channels.size());

         for (ConnectedStreamChannel channel: channels) {
             channel.close();
         }
    }

    @Test
    public void awaitWithConnectionFullBySetWaterMark() throws IOException, InterruptedException {
         createServer(OptionMap.create(Options.REUSE_ADDRESSES, Boolean.TRUE, Options.CONNECTION_HIGH_WATER, 20));

         server.resumeAccepts();

         final Collection<ConnectedStreamChannel> channels = new ArrayList<ConnectedStreamChannel>();
         for (int i = 0; i < 5; i++) {
             final ConnectedStreamChannel connectedChannel = attemptToConnect(server);
             assertNotNull(connectedChannel);
             channels.add(connectedChannel);
         }

         // lower the water mark limit
         assertEquals(20, (int) server.setOption(Options.CONNECTION_LOW_WATER, 5));
         assertEquals(5, (int) server.getOption(Options.CONNECTION_LOW_WATER));
         final ConnectedStreamChannel connectedChannel = attemptToConnect(server);
         assertNotNull(connectedChannel);
         channels.add(connectedChannel);

         // force conn full status, by lowering the water mark limit
         assertEquals(20, (int) server.setOption(Options.CONNECTION_HIGH_WATER, 4));
         assertEquals(4, (int) server.getOption(Options.CONNECTION_HIGH_WATER));
         assertNull(attemptToConnect(server));

         // await acceptable
         final Runnable acceptableAwaiter = new AcceptableAwaiter(server, 10, TimeUnit.MINUTES);
         assertTrue(server.getClass().getName().endsWith("NioTcpServer"));
         final Thread acceptableAwaiterThread = new Thread(acceptableAwaiter);
         acceptableAwaiterThread.start();
         acceptableAwaiterThread.join(50);
         assertTrue(acceptableAwaiterThread.isAlive());
         server.resumeAccepts();
         assertNull(server.accept());

         // check channels are still open, despite the fact that there are more channels open than high water mark
         for (ConnectedStreamChannel channel: channels) {
             assertTrue(channel.isOpen());
         }

         // decrease water mark limit even more, nothing will change
         assertEquals(4, (int) server.setOption(Options.CONNECTION_HIGH_WATER, 3));
         assertEquals(3, (int) server.getOption(Options.CONNECTION_HIGH_WATER));
         assertNull(attemptToConnect(server));

         // increase water mark limit, which will cause conn full status to be unset
         assertEquals(3, (int) server.setOption(Options.CONNECTION_HIGH_WATER, 10));
         assertEquals(10, (int) server.getOption(Options.CONNECTION_HIGH_WATER));
         assertNull(attemptToConnect(server));

         assertEquals(3, (int) server.setOption(Options.CONNECTION_LOW_WATER, 4));
         assertEquals(4, (int) server.getOption(Options.CONNECTION_LOW_WATER));
         assertNull(attemptToConnect(server));

         acceptableAwaiterThread.join(50);
         assertTrue(acceptableAwaiterThread.isAlive());

         assertEquals(4, (int) server.setOption(Options.CONNECTION_LOW_WATER, 6));
         assertEquals(6, (int) server.getOption(Options.CONNECTION_LOW_WATER));

         acceptableAwaiterThread.join();

         while(true) {
             ConnectedStreamChannel channel = attemptToConnect(server);
             if (channel == null) {
                 break;
             } else {
                 channels.add(channel);
             }
         }
         assertEquals(10, channels.size());

         for (ConnectedStreamChannel channel: channels) {
             channel.close();
         }
    }

    private ConnectedStreamChannel attemptToConnect(final AcceptingChannel<? extends ConnectedStreamChannel> server) throws IOException {
        final SocketChannel channel;
        try {
            channel = SocketChannel.open();
        } catch (SocketException e) {
            throw e;
        }
        channel.configureBlocking(false);
        channel.socket().bind(new InetSocketAddress(0));
        channel.connect(new InetSocketAddress(Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 }), SERVER_PORT));
        final ConnectedStreamChannel connectedChannel = server.accept();
        if (connectedChannel != null && !channel.finishConnect()) {
            fail("Channel didn't finish connect");
        }
        return connectedChannel;
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

    public static class AcceptListener implements ChannelListener<BoundChannel> {

        private int notificationsTotal = 0;
        private BoundChannel previousNotificationChannel;
        private BoundChannel unexpectedChannel;

        @Override
        public void handleEvent(BoundChannel channel) {
            if (previousNotificationChannel == null) {
                previousNotificationChannel = channel;
            } else if (previousNotificationChannel != channel) {
                unexpectedChannel = channel;
            }
            notificationsTotal ++;
        }

        public int getNotificationsTotal() {
            return notificationsTotal;
        }

        public BoundChannel getTargetChannel() {
            return previousNotificationChannel;
        }

        public void checkAlwaysSameChannel() {
            assertNull("At least two different channels were handled by this listener: " + previousNotificationChannel +
                    " and " + unexpectedChannel, unexpectedChannel);
        }
    }
}
