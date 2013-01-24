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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.Channel;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xnio.ChannelListener;
import org.xnio.IoFuture;
import org.xnio.LocalSocketAddress;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.BoundChannel;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.channels.MulticastMessageChannel;

/**
 * Test for XnioWorker.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 *
 */
public class XnioWorkerTestCase {

    private static final int SERVER_PORT = 12345;
    private static SocketAddress bindAddress;
    private static XnioWorker worker;
    private static Xnio xnio;

    protected AcceptingChannel<? extends ConnectedStreamChannel> server;

    @BeforeClass
    public static void createWorker() throws IOException {
        xnio = Xnio.getInstance("nio", AcceptChannelTestCase.class.getClassLoader());
        bindAddress = new InetSocketAddress(Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 }), SERVER_PORT);
    }

    @AfterClass
    public static void destroyWorker() throws InterruptedException {
        if (worker != null && !worker.isShutdown()) {
            worker.shutdown();
            worker.awaitTermination(1L, TimeUnit.MINUTES);
        }
    }

    @Test
    public void illegalWorker() throws IOException {
        IllegalArgumentException expected = null;
        try {
            xnio.createWorker(OptionMap.create(Options.WORKER_READ_THREADS, -1));
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            xnio.createWorker(OptionMap.create(Options.WORKER_WRITE_THREADS, -5));
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            xnio.createWorker(OptionMap.create(Options.STACK_SIZE, -10000l));
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    @Test
    public void createTcpStreamServerAndConnect() throws IOException {
        final XnioWorker xnioWorker = xnio.createWorker(OptionMap.create(Options.THREAD_DAEMON, true));
        final ChannelListener<AcceptingChannel<ConnectedStreamChannel>> acceptingChannelListener = new TestChannelListener<AcceptingChannel<ConnectedStreamChannel>>();;
        final AcceptingChannel<? extends ConnectedStreamChannel> streamServer = xnioWorker.createStreamServer(
                bindAddress, acceptingChannelListener, OptionMap.create(Options.BROADCAST, true));
        assertNotNull(streamServer);
        try {
            assertEquals(bindAddress, streamServer.getLocalAddress());
            assertSame(xnioWorker, streamServer.getWorker());
            final TestChannelListener<ConnectedStreamChannel> channelListener1 = new TestChannelListener<ConnectedStreamChannel>();
            final TestChannelListener<ConnectedStreamChannel> channelListener2 = new TestChannelListener<ConnectedStreamChannel>();
            final TestChannelListener<BoundChannel> bindListener1 = new TestChannelListener<BoundChannel>();
            final IoFuture<ConnectedStreamChannel> connectedStreamChannel1 = xnioWorker.connectStream(bindAddress, channelListener1, OptionMap.create(Options.MAX_INBOUND_MESSAGE_SIZE, 50000, Options.WORKER_ESTABLISH_WRITING, true));
            final IoFuture<ConnectedStreamChannel> connectedStreamChannel2 = xnioWorker.connectStream(bindAddress, channelListener2, bindListener1, OptionMap.create(Options.MAX_OUTBOUND_MESSAGE_SIZE, 50000));
            assertNotNull(connectedStreamChannel1);
            assertNotNull(connectedStreamChannel2);
    
            final ConnectedStreamChannel channel1 = connectedStreamChannel1.get();
            final ConnectedStreamChannel channel2 = connectedStreamChannel2.get();
            assertNotNull(channel1);
            assertNotNull(channel2);
            try {
                
                assertTrue(channelListener1.isInvoked());
                assertSame(channel1, channelListener1.getChannel());
                assertEquals(bindAddress, channel1.getPeerAddress());

                assertTrue(channelListener2.isInvoked());
                assertSame(channel2, channelListener2.getChannel());
                assertTrue(bindListener1.isInvoked());
                final BoundChannel boundChannel = bindListener1.getChannel();
                assertNotNull(boundChannel);
                assertEquals(channel2.getLocalAddress(), boundChannel.getLocalAddress());
                assertEquals(channel2.getLocalAddress(InetSocketAddress.class), boundChannel.getLocalAddress(InetSocketAddress.class));
                assertEquals(channel2.getLocalAddress(LocalSocketAddress.class), boundChannel.getLocalAddress(LocalSocketAddress.class));
                assertSame(channel2.getWorker(), boundChannel.getWorker());
                assertEquals(channel2.getOption(Options.SEND_BUFFER), boundChannel.getOption(Options.SEND_BUFFER));
                channel2.setOption(Options.SEND_BUFFER, 3000);
                assertEquals(3000, (int) boundChannel.getOption(Options.SEND_BUFFER));
                assertEquals(bindAddress, channel2.getPeerAddress());
                assertTrue(boundChannel.supportsOption(Options.KEEP_ALIVE));
                assertFalse(boundChannel.supportsOption(Options.CONNECTION_LOW_WATER));
                assertNotNull(boundChannel.toString());
                
                final TestChannelListener<BoundChannel> boundChannelCloseListener = new TestChannelListener<BoundChannel>();
                boundChannel.getCloseSetter().set(boundChannelCloseListener);
                assertTrue(boundChannel.isOpen());
                assertTrue(channel2.isOpen());
                assertFalse(boundChannelCloseListener.isInvokedYet());
                boundChannel.close();
                assertTrue(boundChannelCloseListener.isInvoked());
                assertFalse(boundChannel.isOpen());
                assertFalse(channel2.isOpen());

            } finally {
                channel1.close();
                channel2.close();
            }
        } finally {
            streamServer.close();
        }
    }

    @Test
    public void illegalConnect() throws IOException {
        final XnioWorker xnioWorker1 = xnio.createWorker(OptionMap.create(Options.WORKER_READ_THREADS, 0));
        final XnioWorker xnioWorker2 = xnio.createWorker(OptionMap.create(Options.WORKER_WRITE_THREADS, 0));

        IllegalArgumentException expected = null;
        try {
            xnioWorker1.createStreamServer(bindAddress, null, OptionMap.create(Options.WORKER_ESTABLISH_WRITING, false));
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            xnioWorker1.createStreamServer(bindAddress, null, OptionMap.EMPTY);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            xnioWorker2.createStreamServer(bindAddress, null, OptionMap.create(Options.WORKER_ESTABLISH_WRITING, true));
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        xnioWorker1.shutdown();
        xnioWorker2.shutdown();
    }

    @Test
    public void cancelTcpConnection() throws IOException {
        final XnioWorker xnioWorker = xnio.createWorker(OptionMap.create(Options.THREAD_DAEMON, true));
        final ChannelListener<AcceptingChannel<ConnectedStreamChannel>> acceptingChannelListener = new TestChannelListener<AcceptingChannel<ConnectedStreamChannel>>();;
        final AcceptingChannel<? extends ConnectedStreamChannel> streamServer = xnioWorker.createStreamServer(
                bindAddress, acceptingChannelListener, OptionMap.create(Options.BROADCAST, true));
        assertNotNull(streamServer);
        try {
            final TestChannelListener<ConnectedStreamChannel> channelListener = new TestChannelListener<ConnectedStreamChannel>();
            final TestChannelListener<BoundChannel> bindListener = new TestChannelListener<BoundChannel>();
            IoFuture<ConnectedStreamChannel> connectedStreamChannel = null;
            do {
                if (connectedStreamChannel != null) {
                    connectedStreamChannel.await();
                    final IoFuture.Status status = connectedStreamChannel.getStatus();
                    if (status == IoFuture.Status.DONE) {
                        connectedStreamChannel.get().close();
                    }
                    channelListener.clear();
                }
                connectedStreamChannel = xnioWorker.connectStream(bindAddress, channelListener, OptionMap.create(Options.MAX_INBOUND_MESSAGE_SIZE, 50000, Options.WORKER_ESTABLISH_WRITING, true)).cancel();
                connectedStreamChannel.cancel();
            } while (connectedStreamChannel.getStatus() != IoFuture.Status.CANCELLED);

            CancellationException expected = null;
            try {
                connectedStreamChannel.get();
            } catch (CancellationException e) {
                expected = e;
            }
            assertNotNull(expected);
            assertSame(IoFuture.Status.CANCELLED, connectedStreamChannel.getStatus());

            assertFalse(channelListener.isInvokedYet());
            assertFalse(bindListener.isInvokedYet());

            // make sure that the server is up and can accept more connections
            assertTrue(streamServer.isOpen());
            final IoFuture<ConnectedStreamChannel> anotherChannel = xnioWorker.connectStream(bindAddress, null, OptionMap.EMPTY);
            assertNotNull(anotherChannel.get());
            anotherChannel.get().close();
        } finally {
            streamServer.close();
        }
    }

    @Test
    public void createLocalStreamServer() throws IOException {
        final XnioWorker xnioWorker = xnio.createWorker(OptionMap.EMPTY);
        UnsupportedOperationException expected = null;
        try {
            xnioWorker.createStreamServer(new LocalSocketAddress("server"), null, OptionMap.EMPTY);
        } catch (UnsupportedOperationException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    @Test
    public void connectLocalStream() throws IOException {
        final XnioWorker xnioWorker = xnio.createWorker(OptionMap.EMPTY);
        UnsupportedOperationException expected = null;
        try {
            xnioWorker.connectStream(new LocalSocketAddress("server for test"), null, OptionMap.EMPTY);
        } catch (UnsupportedOperationException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    @Test
    public void acceptTcpStream() throws IOException {
        final XnioWorker xnioWorker = xnio.createWorker(OptionMap.EMPTY);
        final InetSocketAddress bindAddress2 = new InetSocketAddress(Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 }), 23456);
        final TestChannelListener<ConnectedStreamChannel> channelListener1 = new TestChannelListener<ConnectedStreamChannel>();
        final TestChannelListener<ConnectedStreamChannel> channelListener2 = new TestChannelListener<ConnectedStreamChannel>();
        final TestChannelListener<BoundChannel> bindListener1 = new TestChannelListener<BoundChannel>();
        final TestChannelListener<BoundChannel> bindListener2 = new TestChannelListener<BoundChannel>();
        final OptionMap optionMap = OptionMap.create(Options.READ_TIMEOUT, 800000);
        final IoFuture<ConnectedStreamChannel> channelFuture1 = xnioWorker.acceptStream(bindAddress, channelListener1, bindListener1, optionMap);
        final IoFuture<ConnectedStreamChannel> channelFuture2 = xnioWorker.acceptStream(bindAddress2, channelListener2, bindListener2, optionMap);
        final IoFuture<ConnectedStreamChannel> connectedStreamChannel1 = xnioWorker.connectStream(bindAddress, null, OptionMap.EMPTY);
        final IoFuture<ConnectedStreamChannel> connectedStreamChannel2 = xnioWorker.connectStream(bindAddress2, null, OptionMap.EMPTY);
        assertNotNull(connectedStreamChannel1);
        assertNotNull(connectedStreamChannel2);
        assertNotNull(channelFuture1);
        assertNotNull(channelFuture2);

        final ConnectedStreamChannel channel1 = channelFuture1.get();
        final ConnectedStreamChannel channel2 = channelFuture2.get();
        assertNotNull(channel1);
        assertNotNull(channel2);
        try {
            assertTrue(channelListener1.isInvoked());
            assertSame(channel1, channelListener1.getChannel());
            assertTrue(bindListener1.isInvoked());
            assertEquals(connectedStreamChannel1.get().getLocalAddress(), channel1.getPeerAddress());

            final BoundChannel boundChannel1 = bindListener1.getChannel();
            assertNotNull(boundChannel1);
            assertSame(xnioWorker, boundChannel1.getWorker());
            assertEquals(bindAddress, boundChannel1.getLocalAddress());
            assertNull(boundChannel1.getLocalAddress(LocalSocketAddress.class));
            assertNotNull(boundChannel1.getCloseSetter());
            assertFalse(boundChannel1.isOpen()); // expected
            assertFalse(boundChannel1.supportsOption(Options.KEEP_ALIVE));
            assertNull(boundChannel1.getOption(Options.KEEP_ALIVE));
            assertNull(boundChannel1.setOption(Options.KEEP_ALIVE, null));
            assertNull(boundChannel1.getOption(Options.KEEP_ALIVE));
            assertNotNull(boundChannel1.toString());

            assertTrue(channelListener2.isInvoked());
            assertSame(channel2, channelListener2.getChannel());
            assertTrue(bindListener2.isInvoked());
            assertEquals(connectedStreamChannel2.get().getLocalAddress(), channel2.getPeerAddress());

            final BoundChannel boundChannel2 = bindListener2.getChannel();
            assertNotNull(boundChannel2);
            assertSame(xnioWorker, boundChannel2.getWorker());
            assertEquals(bindAddress2, boundChannel2.getLocalAddress());
            assertNull(boundChannel2.getLocalAddress(LocalSocketAddress.class));
            assertNotNull(boundChannel2.getCloseSetter());
            assertFalse(boundChannel2.isOpen()); // expected
            assertFalse(boundChannel2.supportsOption(Options.KEEP_ALIVE));
            assertNull(boundChannel2.getOption(Options.KEEP_ALIVE));
            assertNull(boundChannel2.setOption(Options.KEEP_ALIVE, null));
            assertNull(boundChannel2.getOption(Options.KEEP_ALIVE));
            assertNotNull(boundChannel2.toString());
        } finally {
            channel1.close();
            channel2.close();
        }
    }

    // @Test FIXME XNIO-169
    public void cancelAcceptTcpStream() throws CancellationException, IOException {
        final XnioWorker xnioWorker = xnio.createWorker(OptionMap.EMPTY);
        final TestChannelListener<ConnectedStreamChannel> channelListener = new TestChannelListener<ConnectedStreamChannel>();
        final TestChannelListener<BoundChannel> bindListener = new TestChannelListener<BoundChannel>();
        final IoFuture<ConnectedStreamChannel> channelFuture = xnioWorker.acceptStream(bindAddress, channelListener, bindListener, OptionMap.EMPTY);
        final IoFuture<ConnectedStreamChannel> connectedStreamChannel = xnioWorker.connectStream(bindAddress, null, OptionMap.EMPTY);

        assertNotNull(connectedStreamChannel);
        assertNotNull(channelFuture);

        channelFuture.cancel();

        CancellationException expected = null;
        try {
            channelFuture.get();
        } catch (CancellationException e) {
            expected = e;
        }
        assertNotNull(expected);

        final ConnectedStreamChannel channel = channelFuture.get();
        assertNotNull(channel);
        assertFalse(channel.isOpen());
        assertFalse(channelListener.isInvokedYet());
        assertFalse(bindListener.isInvokedYet());
    }

    @Test
    public void acceptLocalStream() throws IOException {
        final XnioWorker xnioWorker = xnio.createWorker(OptionMap.EMPTY);
        UnsupportedOperationException expected = null;
        try {
            xnioWorker.acceptStream(new LocalSocketAddress("local address"), null, null, OptionMap.EMPTY);
        } catch (UnsupportedOperationException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    @Test
    public void connectTcpDatagram() throws IOException {
        final XnioWorker xnioWorker = xnio.createWorker(OptionMap.EMPTY);
        UnsupportedOperationException expected = null;
        try {
            xnioWorker.connectDatagram(bindAddress, null, null, OptionMap.EMPTY);
        } catch (UnsupportedOperationException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    @Test
    public void connectLocalDatagram() throws IOException {
        final XnioWorker xnioWorker = xnio.createWorker(OptionMap.EMPTY);
        UnsupportedOperationException expected = null;
        try {
            xnioWorker.connectDatagram(new LocalSocketAddress("local"), null, null, OptionMap.EMPTY);
        } catch (UnsupportedOperationException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    @Test
    public void acceptDatagram() throws IOException {
        final XnioWorker xnioWorker = xnio.createWorker(OptionMap.EMPTY);
        UnsupportedOperationException expected = null;
        try {
            xnioWorker.acceptDatagram(bindAddress, null, null, OptionMap.EMPTY);
        } catch (UnsupportedOperationException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    @Test
    public void createUdpServer() throws IOException {
        final XnioWorker xnioWorker = xnio.createWorker(OptionMap.EMPTY);
        final InetSocketAddress address = new InetSocketAddress(0);
        final OptionMap optionMap = OptionMap.create(Options.MULTICAST, true, Options.SECURE, false);
        final MulticastMessageChannel channel = xnioWorker.createUdpServer(address, optionMap);
        assertNotNull(channel);
        try {
            // check address
            assertNotNull(channel.getLocalAddress());
        } finally {
            channel.close();
        }
    }

    @Test
    public void createUdpServerWithListener() throws IOException {
        final XnioWorker xnioWorker = xnio.createWorker(OptionMap.EMPTY);
        final InetSocketAddress address = new InetSocketAddress(0);
        final TestChannelListener<MulticastMessageChannel> listener = new TestChannelListener<MulticastMessageChannel>();
        final OptionMap optionMap = OptionMap.create(Options.MULTICAST, true, Options.SECURE, true);
        final MulticastMessageChannel channel = xnioWorker.createUdpServer(address, listener, optionMap);
        assertNotNull(channel);
        try {
            // check address
            assertNotNull(channel.getLocalAddress());
            // check listener
            assertTrue(listener.isInvoked());
            assertSame(channel, listener.getChannel());
        } finally {
            channel.close();
        }
    }

    @Test
    public void createFullDuplexPipe() throws IOException {
        final XnioWorker xnioWorker = xnio.createWorker(OptionMap.EMPTY);
        assertNotNull(xnioWorker.createFullDuplexPipe());
        assertNotNull(xnioWorker.createHalfDuplexPipe());
    }

    @Test
    public void shutdownNowWithTerminationTask() throws IOException, InterruptedException {
        final TerminationTask terminationTask = new TerminationTask();
        final XnioWorker xnioWorker = xnio.createWorker(Thread.currentThread().getThreadGroup(), OptionMap.EMPTY, terminationTask);
        assertFalse(xnioWorker.isShutdown());
        assertFalse(xnioWorker.isTerminated());
        xnioWorker.shutdownNow();
        assertTrue(xnioWorker.isShutdown());
        assertFalse(xnioWorker.isTerminated());
        xnioWorker.awaitTermination();
        assertTrue(terminationTask.isInvoked());
        assertTrue(xnioWorker.isShutdown());
        assertTrue(xnioWorker.isTerminated());

        xnioWorker.awaitTermination();
        // idempotent
        xnioWorker.shutdown();
        xnioWorker.shutdownNow();
    }

    @Test
    public void awaitTerminationWithMultipleAwaiters1() throws IOException, InterruptedException {
        final XnioWorker worker = xnio.createWorker(OptionMap.EMPTY);
        final TerminationAwaiter awaiter1 = new TerminationAwaiter(worker);
        final TerminationAwaiter awaiter2 = new TerminationAwaiter(worker, 10, TimeUnit.HOURS);
        final TerminationAwaiter awaiter3 = new TerminationAwaiter(worker, 10, TimeUnit.MILLISECONDS);
        final Thread thread1 = new Thread(awaiter1);
        final Thread thread2 = new Thread(awaiter2);
        final Thread thread3 = new Thread(awaiter3);
        thread1.start();
        thread2.start();
        thread3.start();

        thread1.join(20);
        thread2.join(20);
        thread3.join();
        assertFalse(awaiter3.isInterrupted());
        assertTrue(thread1.isAlive());
        assertTrue(thread2.isAlive());

        worker.shutdown();
        thread1.join();
        thread2.join();
        assertFalse(awaiter1.isInterrupted());
        assertFalse(awaiter2.isInterrupted());
    }

    @Test
    public void awaitTerminationWithMultipleAwaiters2() throws IOException, InterruptedException {
        final XnioWorker worker = xnio.createWorker(OptionMap.EMPTY);
        final TerminationAwaiter awaiter1 = new TerminationAwaiter(worker);
        final TerminationAwaiter awaiter2 = new TerminationAwaiter(worker, 10, TimeUnit.HOURS);
        final TerminationAwaiter awaiter3 = new TerminationAwaiter(worker, 300, TimeUnit.MILLISECONDS);
        final TerminationAwaiter awaiter4 = new TerminationAwaiter(worker, 10, TimeUnit.MILLISECONDS);
        final TerminationAwaiter awaiter5 = new TerminationAwaiter(worker, 100, TimeUnit.MILLISECONDS);
        final TerminationAwaiter awaiter6 = new TerminationAwaiter(worker, 10, TimeUnit.MINUTES);
        final TerminationAwaiter awaiter7 = new TerminationAwaiter(worker, 1, TimeUnit.MILLISECONDS);
        final Thread thread1 = new Thread(awaiter1);
        final Thread thread2 = new Thread(awaiter2);
        final Thread thread3 = new Thread(awaiter3);
        final Thread thread4 = new Thread(awaiter4);
        final Thread thread5 = new Thread(awaiter5);
        final Thread thread6 = new Thread(awaiter6);
        final Thread thread7 = new Thread(awaiter7);
        thread1.start();
        thread2.start();
        thread3.start();
        thread4.start();
        thread5.start();
        thread6.start();
        thread7.start();

        thread1.join(20);
        thread2.join(20);
        thread4.join();
        thread5.join();
        thread3.join();
        thread6.join(20);
        thread7.join();
        assertFalse(awaiter3.isInterrupted());
        assertFalse(awaiter4.isInterrupted());
        assertFalse(awaiter5.isInterrupted());
        assertFalse(awaiter7.isInterrupted());
        assertTrue(thread1.isAlive());
        assertTrue(thread2.isAlive());
        assertTrue(thread6.isAlive());

        worker.shutdown();
        thread1.join();
        thread2.join();
        thread6.join();
        assertFalse(awaiter1.isInterrupted());
        assertFalse(awaiter2.isInterrupted());
        assertFalse(awaiter6.isInterrupted());
    }

    @Test
    public void awaitTerminationWithMultipleAwaiters3() throws IOException, InterruptedException {
        final XnioWorker worker = xnio.createWorker(OptionMap.EMPTY);
        final TerminationAwaiter awaiter1 = new TerminationAwaiter(worker, 1, TimeUnit.NANOSECONDS);
        final TerminationAwaiter awaiter2 = new TerminationAwaiter(worker, 10, TimeUnit.HOURS);
        final TerminationAwaiter awaiter3 = new TerminationAwaiter(worker);
        final TerminationAwaiter awaiter4 = new TerminationAwaiter(worker, 3, TimeUnit.MILLISECONDS);
        final TerminationAwaiter awaiter5 = new TerminationAwaiter(worker);
        final Thread thread1 = new Thread(awaiter1);
        final Thread thread2 = new Thread(awaiter2);
        final Thread thread3 = new Thread(awaiter3);
        final Thread thread4 = new Thread(awaiter4);
        final Thread thread5 = new Thread(awaiter5);
        thread1.start();
        thread2.start();
        thread3.start();
        thread4.start();
        thread5.start();

        thread1.join();
        thread2.join(20);
        thread3.join(20);
        thread4.join();
        thread5.join(20);
        assertFalse(awaiter1.isInterrupted());
        assertFalse(awaiter4.isInterrupted());
        assertTrue(thread2.isAlive());
        assertTrue(thread3.isAlive());
        assertTrue(thread5.isAlive());

        worker.shutdown();
        thread2.join();
        thread3.join();
        thread5.join();
        assertFalse(awaiter2.isInterrupted());
        assertFalse(awaiter3.isInterrupted());
        assertFalse(awaiter5.isInterrupted());
    }

    @Test
    public void interruptedAwaiters() throws IOException, InterruptedException {
        final XnioWorker worker = xnio.createWorker(OptionMap.EMPTY);
        final TerminationAwaiter awaiter1 = new TerminationAwaiter(worker);
        final TerminationAwaiter awaiter2 = new TerminationAwaiter(worker, 3, TimeUnit.MINUTES);
        final Thread thread1 = new Thread(awaiter1);
        final Thread thread2 = new Thread(awaiter2);

        thread1.start();
        thread2.start();

        thread1.interrupt();
        thread2.interrupt();

        thread1.join();
        thread2.join();
        assertTrue(awaiter1.isInterrupted());
        assertTrue(awaiter2.isInterrupted());

        worker.shutdown();
    }

    @Test
    public void invalidAcceptStream() throws IOException {
        final XnioWorker worker = xnio.createWorker(OptionMap.create(Options.WORKER_READ_THREADS, 0, Options.WORKER_WRITE_THREADS, 0));
        assertNotNull(worker);

        IllegalArgumentException expected = null;
        try {
            worker.acceptStream(bindAddress, null, null, OptionMap.EMPTY);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            worker.acceptStream(bindAddress, null, null, OptionMap.create(Options.WORKER_ESTABLISH_WRITING, false));
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            worker.acceptStream(bindAddress, null, null, OptionMap.create(Options.WORKER_ESTABLISH_WRITING, false));
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    private static class TestChannelListener<C extends Channel> implements ChannelListener<C> {

        private boolean invoked = false;
        private C channel = null;
        private CountDownLatch latch = new CountDownLatch(1);

        @Override
        public void handleEvent(C c) {
            invoked = true;
            channel = c;
            latch.countDown();
        }

        public boolean isInvoked() {
            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return invoked;
        }

        public boolean isInvokedYet() {
            return invoked;
        }

        public C getChannel() {
            return channel;
        }

        public void clear() {
            invoked = false;
        }
    }

    private static class TerminationTask implements Runnable {

        private boolean invoked = false;
        private final CountDownLatch countDown = new CountDownLatch(1);

        @Override
        public void run() {
            invoked = true;
            countDown.countDown();
        }

        public boolean isInvoked() {
            try {
                countDown.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return invoked;
        }
    }

    private static class TerminationAwaiter implements Runnable {

        private final XnioWorker xnioWorker;
        private final long timeout;
        private final TimeUnit timeoutUnit;
        private boolean interrupted;

        public TerminationAwaiter (XnioWorker w, long t, TimeUnit tu) {
            xnioWorker = w;;
            timeout = t;
            timeoutUnit = tu;
        }

        public TerminationAwaiter(XnioWorker w) {
            this(w, -1, null);
        }

        public void run() {
            try {
                if (timeoutUnit == null) {
                    xnioWorker.awaitTermination();
                } else {
                    xnioWorker.awaitTermination(timeout, timeoutUnit);
                }
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }

        public boolean isInterrupted() {
            return interrupted;
        }
    }
}
