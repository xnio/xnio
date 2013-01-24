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
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.xnio.Buffers;
import org.xnio.LocalSocketAddress;
import org.xnio.Option;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.channels.MulticastMessageChannel;
import org.xnio.channels.SocketAddressBuffer;

/**
 * Test for the UDP channel.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 *
 */
public class UdpChannelTestCase {

    private static final InetSocketAddress address1 = new InetSocketAddress("localhost", 1050);
    private static final InetSocketAddress address2 = new InetSocketAddress("localhost", 2050);
    private static final Xnio xnio = Xnio.getInstance();

    @Test
    public void addressRetrieval() throws IOException {
        final XnioWorker xnioWorker = xnio.createWorker(OptionMap.EMPTY);
        final MulticastMessageChannel server1 = xnioWorker.createUdpServer(address1, OptionMap.EMPTY);
        final MulticastMessageChannel server2 = xnioWorker.createUdpServer(address2, OptionMap.EMPTY);
        try {
            assertEquals(address1, server1.getLocalAddress());
            assertEquals(address1, server1.getLocalAddress(InetSocketAddress.class));
            assertNull(server1.getLocalAddress(LocalSocketAddress.class));
            assertEquals(address2, server2.getLocalAddress());
            assertEquals(address2, server2.getLocalAddress(InetSocketAddress.class));
            assertNull(server2.getLocalAddress(LocalSocketAddress.class));
        } finally {
            server1.close();
            server2.close();
            xnioWorker.shutdown();
        }
    }

    @Test
    public void testSimpleConnection() throws IOException {
        final XnioWorker xnioWorker = xnio.createWorker(OptionMap.EMPTY);
        final MulticastMessageChannel server1 = xnioWorker.createUdpServer(address1, OptionMap.EMPTY);
        final MulticastMessageChannel server2 = xnioWorker.createUdpServer(address2, OptionMap.EMPTY);
        assertTrue(server1.isOpen());
        assertTrue(server2.isOpen());

        try {
            final ByteBuffer buffer = ByteBuffer.allocate(10);
            buffer.put("1234567890".getBytes()).flip();
            assertTrue(server2.sendTo(address1, buffer));
            final ByteBuffer receiveBuffer = ByteBuffer.allocate(10);
            SocketAddressBuffer addressBuffer = new SocketAddressBuffer();
            assertEquals(10, server1.receiveFrom(addressBuffer, receiveBuffer));
            receiveBuffer.flip();
            assertEquals("1234567890", Buffers.getModifiedUtf8(receiveBuffer));
            assertEquals(address2, addressBuffer.getSourceAddress());
            assertNull(addressBuffer.getDestinationAddress());

            buffer.clear();
            buffer.put("0987654321".getBytes()).flip();
            assertTrue(server1.sendTo(address2, buffer));
            receiveBuffer.clear();
            addressBuffer = new SocketAddressBuffer();
            assertEquals(10, server2.receiveFrom(null, receiveBuffer));
            receiveBuffer.flip();
            assertEquals("0987654321", Buffers.getModifiedUtf8(receiveBuffer));

            assertTrue(server1.isOpen());
            assertTrue(server2.isOpen());
        } finally {
            server1.close();
            server2.close();
            xnioWorker.shutdown();
            assertFalse(server1.isOpen());
            assertFalse(server2.isOpen());
        }
    }

    @Test
    public void testChannelWithOneThreadOnly() throws IllegalArgumentException, IOException {
        final XnioWorker xnioWorker1 = xnio.createWorker(OptionMap.create(Options.WORKER_READ_THREADS, 0));
        final XnioWorker xnioWorker2 = xnio.createWorker(OptionMap.create(Options.WORKER_WRITE_THREADS, 0));
        final MulticastMessageChannel server1 = xnioWorker1.createUdpServer(address1, OptionMap.EMPTY);
        final MulticastMessageChannel server2 = xnioWorker2.createUdpServer(address2, OptionMap.EMPTY);
        assertTrue(server1.isOpen());
        assertTrue(server2.isOpen());

        try {
            final ByteBuffer buffer = ByteBuffer.allocate(15);
            buffer.put("msg to server 2".getBytes()).flip();
            assertTrue(server1.sendTo(address2, buffer));
            final ByteBuffer receiveBuffer = ByteBuffer.allocate(15);
            SocketAddressBuffer addressBuffer = new SocketAddressBuffer();
            assertEquals(15, server2.receiveFrom(addressBuffer, receiveBuffer));
            receiveBuffer.flip();
            assertEquals("msg to server 2", Buffers.getModifiedUtf8(receiveBuffer));
            assertEquals(address1, addressBuffer.getSourceAddress());
            assertNull(addressBuffer.getDestinationAddress());

            buffer.clear();
            buffer.put("msg to server 1".getBytes()).flip();
            assertTrue(server2.sendTo(address1, buffer));
            receiveBuffer.clear();
            assertEquals(15, server1.receiveFrom(null, receiveBuffer));
            receiveBuffer.flip();
            assertEquals("msg to server 1", Buffers.getModifiedUtf8(receiveBuffer));

            assertTrue(server1.isOpen());
            assertTrue(server2.isOpen());

            Exception expected = null;
            try {
                server1.resumeReads();
            } catch (IllegalArgumentException e) {
                expected = e;
            }
            assertNotNull(expected);
            expected = null;
            try {
                server1.wakeupReads();
            } catch (IllegalArgumentException e) {
                expected = e;
            }
            assertNotNull(expected);
            assertNull(server1.getReadThread());
            TestChannelListener<MulticastMessageChannel> server1Listener = new TestChannelListener<MulticastMessageChannel>();
            server1.getWriteSetter().set(server1Listener);
            server1.resumeWrites();
            server1.wakeupWrites();
            assertTrue(server1Listener.isInvoked());
            assertNotNull(server1.getWriteThread());
            assertFalse(server1.isReadResumed());
            assertTrue(server1.isWriteResumed());
            server1.suspendReads();
            server1.suspendWrites();
            assertFalse(server1.isReadResumed());
            assertFalse(server1.isWriteResumed());

            TestChannelListener<MulticastMessageChannel> server2Listener = new TestChannelListener<MulticastMessageChannel>();
            server2.getReadSetter().set(server2Listener);
            server2.resumeReads();
            server2.wakeupReads();
            assertTrue(server2Listener.isInvoked());
            expected = null;
            try {
                server2.resumeWrites();
            } catch (IllegalArgumentException e) {
                expected = e;
            }
            expected = null;
            try {
                server2.wakeupWrites();
            } catch (IllegalArgumentException e) {
                expected = e;
            }
            assertNotNull(expected);
            assertNotNull(server2.getReadThread());
            assertNull(server2.getWriteThread());
            assertTrue(server2.isReadResumed());
            assertFalse(server2.isWriteResumed());
            server2.suspendReads();
            server2.suspendWrites();
            assertFalse(server2.isReadResumed());
            assertFalse(server2.isWriteResumed());

        } finally {
            server1.close();
            server2.close();
            xnioWorker1.shutdown();
            xnioWorker2.shutdown();
            assertFalse(server1.isOpen());
            assertFalse(server2.isOpen());
        }
    }

    @Test
    public void communicateUsingClosedChannel() throws IOException {
        final XnioWorker xnioWorker1 = xnio.createWorker(OptionMap.create(Options.WORKER_READ_THREADS, 0));
        final XnioWorker xnioWorker2 = xnio.createWorker(OptionMap.create(Options.WORKER_WRITE_THREADS, 0));
        final MulticastMessageChannel server1 = xnioWorker1.createUdpServer(address1, OptionMap.EMPTY);
        final MulticastMessageChannel server2 = xnioWorker2.createUdpServer(address2, OptionMap.EMPTY);
        assertTrue(server1.isOpen());
        assertTrue(server2.isOpen());
        server1.close();
        assertFalse(server1.isOpen());
        assertTrue(server2.isOpen());

        try {
            assertTrue(server1.flush());
            assertTrue(server2.flush());

            final ByteBuffer buffer = ByteBuffer.allocate(15);
            buffer.put("attempt to send".getBytes()).flip();
            ClosedChannelException expected = null;
            try {
                server1.sendTo(address2, buffer);
            } catch (ClosedChannelException e) {
                expected = e;
            }
            assertNotNull(expected);

            expected = null;
            try {
                server1.sendTo(address2, new ByteBuffer[]{buffer});
            } catch (ClosedChannelException e) {
                expected = e;
            }
            assertNotNull(expected);

            expected = null;
            try {
                server1.sendTo(address2, new ByteBuffer[]{buffer, buffer, buffer}, 0, 2);
            } catch (ClosedChannelException e) {
                expected = e;
            }
            assertNotNull(expected);

            final SocketAddressBuffer addressBuffer = new SocketAddressBuffer();
            final ByteBuffer receiveBuffer = ByteBuffer.allocate(3);
            expected = null;
            assertEquals(-1, server1.receiveFrom(addressBuffer, receiveBuffer));
            assertEquals(-1, server1.receiveFrom(addressBuffer, new ByteBuffer[]{receiveBuffer}));
            assertEquals(-1, server1.receiveFrom(addressBuffer, new ByteBuffer[]{receiveBuffer, receiveBuffer, receiveBuffer}));

            assertFalse(server2.sendTo(address1, buffer));
            assertEquals(0, server2.receiveFrom(addressBuffer, receiveBuffer));

            assertTrue(server1.flush());
            assertTrue(server1.flush());
            assertTrue(server2.flush());
        } finally {
            server2.close();
            xnioWorker1.shutdown();
            xnioWorker2.shutdown();
            assertFalse(server1.isOpen());
            assertFalse(server2.isOpen());
        }
    }

    @Test
    public void sendEmptyBuffer() throws IOException {
        final XnioWorker xnioWorker = xnio.createWorker(OptionMap.create(Options.WORKER_READ_THREADS, 0));
        final MulticastMessageChannel server = xnioWorker.createUdpServer(address1, OptionMap.EMPTY);
        assertTrue(server.isOpen());
        try {
            assertFalse(server.sendTo(address2, Buffers.EMPTY_BYTE_BUFFER));
        } finally {
            server.close();
            xnioWorker.shutdown();
        }
    }

    @Test
    public void sendAndReceiveMultipleBuffers() throws IOException {
        final XnioWorker xnioWorker = xnio.createWorker(OptionMap.EMPTY);
        final MulticastMessageChannel server1 = xnioWorker.createUdpServer(address1, OptionMap.EMPTY);
        final MulticastMessageChannel server2 = xnioWorker.createUdpServer(address2, OptionMap.EMPTY);
        assertTrue(server1.isOpen());
        assertTrue(server2.isOpen());

        try {
            final ByteBuffer[] buffers = new ByteBuffer[] {Buffers.EMPTY_BYTE_BUFFER, ByteBuffer.allocate(10),
                    ByteBuffer.allocate(5), ByteBuffer.allocate(1), ByteBuffer.allocate(1), ByteBuffer.allocate(1), 
                    ByteBuffer.allocate(2)};
            buffers[1].put("nio udp".getBytes()).flip();
            buffers[2].put("test".getBytes()).flip();
            buffers[3].put((byte) 'c').flip();
            buffers[4].put((byte) 'a').flip();
            buffers[5].put((byte) 's').flip();
            buffers[6].put("e!".getBytes()).flip();

            assertTrue(server2.sendTo(address1, buffers, 0, 2));

            final ByteBuffer[] receiveBuffers = new ByteBuffer[]{ByteBuffer.allocate(5), ByteBuffer.allocate(6),
                    ByteBuffer.allocate(8)};
            SocketAddressBuffer addressBuffer = new SocketAddressBuffer();
            assertEquals(5, server1.receiveFrom(addressBuffer, receiveBuffers, 0, 1));
            receiveBuffers[0].flip();
            assertEquals("nio u", Buffers.getModifiedUtf8(receiveBuffers[0]));
            assertEquals(address2, addressBuffer.getSourceAddress());
            assertNull(addressBuffer.getDestinationAddress());

            receiveBuffers[0].clear();
            assertEquals(0, server1.receiveFrom(null, receiveBuffers, 0, 1));

            assertFalse(server1.sendTo(address2, buffers, 0, 0));
            assertEquals(0, server2.receiveFrom(null, receiveBuffers, 2, 0));

            assertTrue(server2.sendTo(address1,  buffers, 2, 1));
            assertEquals(4, server1.receiveFrom(null,  receiveBuffers));
            receiveBuffers[0].flip();
            assertEquals("test", Buffers.getModifiedUtf8(receiveBuffers[0]));
            receiveBuffers[0].clear();

            assertFalse(server1.sendTo(address2, buffers, 0, 2));
            addressBuffer = new SocketAddressBuffer();
            assertEquals(0, server2.receiveFrom(addressBuffer, receiveBuffers, 0, 3));
            assertEquals(address1, addressBuffer.getSourceAddress());
            assertNull(addressBuffer.getDestinationAddress());

            addressBuffer = new SocketAddressBuffer();
            assertEquals(0, server2.receiveFrom(addressBuffer, receiveBuffers, 0, 3));
            assertNull(addressBuffer.getSourceAddress());
            assertNull(addressBuffer.getDestinationAddress());

            buffers[1].flip();
            buffers[2].flip();
            assertTrue(server1.sendTo(address2, buffers));
            assertEquals(16, server2.receiveFrom(addressBuffer, receiveBuffers));
            receiveBuffers[0].flip();
            assertEquals("nio u", Buffers.getModifiedUtf8(receiveBuffers[0]));
            receiveBuffers[1].flip();
            assertEquals("dptest", Buffers.getModifiedUtf8(receiveBuffers[1]));
            receiveBuffers[2].flip();
            assertEquals("case!", Buffers.getModifiedUtf8(receiveBuffers[2]));
        } finally {
            server1.close();
            server2.close();
            xnioWorker.shutdown();
            assertFalse(server1.isOpen());
            assertFalse(server2.isOpen());
        }
    }

    @Test
    public void sendTooBigBuffer() throws IOException {
        final XnioWorker xnioWorker = xnio.createWorker(OptionMap.EMPTY);
        final MulticastMessageChannel server = xnioWorker.createUdpServer(address1, OptionMap.EMPTY);
        assertTrue(server.isOpen());

        try {
            final ByteBuffer[] buffers = new ByteBuffer[] {ByteBuffer.allocate(65536), ByteBuffer.allocate(65536)};
            buffers[0].limit(buffers[0].capacity());
            
            IllegalArgumentException expected = null;
            try {
                server.sendTo(address1, buffers);
            } catch (IllegalArgumentException e) {
                expected = e;
            }
            assertNotNull(expected);
        } finally {
            server.close();
            xnioWorker.shutdown();
            assertFalse(server.isOpen());
        }
    }

    @Test
    public void shutdownReadsAndWrite() throws IOException {
        final XnioWorker xnioWorker = xnio.createWorker(OptionMap.EMPTY);
        final MulticastMessageChannel server = xnioWorker.createUdpServer(address1, OptionMap.EMPTY);

        try {
            UnsupportedOperationException expected = null;
            try {
                server.shutdownReads();
            } catch (UnsupportedOperationException e) {
                expected = e;
            }
            assertNotNull(expected);
    
            expected = null;
            try {
                server.shutdownWrites();
            } catch (UnsupportedOperationException e) {
                expected = e;
            }
            assertNotNull(expected);
        } finally {
            server.close();
            xnioWorker.shutdown();
        }
    }

    @Test
    public void awaitReadableWritable() throws IOException, InterruptedException {
        final XnioWorker xnioWorker = xnio.createWorker(OptionMap.EMPTY);
        final MulticastMessageChannel server1 = xnioWorker.createUdpServer(address1, OptionMap.EMPTY);
        final MulticastMessageChannel server2 = xnioWorker.createUdpServer(address2, OptionMap.EMPTY);

        try {
            server1.awaitWritable(); 

            final Thread readWaitThread1 = new Thread(new ReadableWaiter(server1));
            final Thread readWaitThread2 = new Thread(new ReadableWaiter(server1, 7, TimeUnit.MICROSECONDS));
            final Thread readWaitThread3 = new Thread(new ReadableWaiter(server1, 7, TimeUnit.DAYS));
            readWaitThread1.start();
            readWaitThread2.start();
            readWaitThread3.start();
            readWaitThread1.join(30);
            readWaitThread2.join();
            readWaitThread3.join(30);
            assertTrue(readWaitThread1.isAlive());
            assertTrue(readWaitThread3.isAlive());
    
            server1.resumeReads();
            readWaitThread1.join(20);
            readWaitThread3.join(20);
            assertTrue(readWaitThread1.isAlive());
            assertTrue(readWaitThread3.isAlive());
    
            final ByteBuffer buffer = ByteBuffer.allocate(3);
            buffer.put("msg".getBytes()).flip();
            assertTrue(server2.sendTo(address1, buffer));
            readWaitThread1.join();
            readWaitThread3.join();
    
            server1.awaitWritable();
            server1.awaitWritable(10, TimeUnit.MICROSECONDS);
        } finally {
            server1.close();
            server2.close();
            xnioWorker.shutdown();
        }
    }

//    @Test
//    public void join() throws IOException {
//        final InetAddress address = InetAddress.getByName("225.0.0.100");
//        final NetworkInterface ni = NetworkInterface.getByName("wlan0");
//        assertNotNull(ni);
//
//        final XnioWorker xnioWorker = xnio.createWorker(OptionMap.EMPTY);
//        final MulticastMessageChannel server1 = xnioWorker.createUdpServer(address1, OptionMap.create(Options.MULTICAST, true));
//        final MulticastMessageChannel server2 = xnioWorker.createUdpServer(address2, OptionMap.create(Options.MULTICAST, true));
//        final InetSocketAddress address3 = new InetSocketAddress("localhost", 1051);
//        final InetSocketAddress address4 = new InetSocketAddress("localhost", 1052);
//        final InetSocketAddress address5 = new InetSocketAddress("localhost", 1053);
//        final InetSocketAddress address6 = new InetSocketAddress("localhost", 1054);
//        final InetSocketAddress address7 = new InetSocketAddress("localhost", 1055);
//        final MulticastMessageChannel server3 = xnioWorker.createUdpServer(address3, OptionMap.create(Options.MULTICAST, true));
//        final MulticastMessageChannel server4 = xnioWorker.createUdpServer(address4, OptionMap.create(Options.MULTICAST, true));
//        final MulticastMessageChannel server5 = xnioWorker.createUdpServer(address5, OptionMap.create(Options.MULTICAST, true));
//        final MulticastMessageChannel server6 = xnioWorker.createUdpServer(address6, OptionMap.create(Options.MULTICAST, true));
//        final MulticastMessageChannel server7 = xnioWorker.createUdpServer(address7, OptionMap.create(Options.MULTICAST, true));
//        server1.setOption(Options.MULTICAST, true);
//        try {
//            server1.join(address,  ni);
//            server2.join(address, ni);
//            server3.join(address, ni);
//            server4.join(address,  ni);
//
//            final ByteBuffer buffer = ByteBuffer.allocate(3);
//            buffer.put("abc".getBytes()).flip();
//
//            assertTrue(server5.sendTo(new InetSocketAddress(address, 950), buffer));
//            
//            final ByteBuffer receiveBuffer = ByteBuffer.allocate(5);
//            SocketAddressBuffer addressBuffer = new SocketAddressBuffer();
//            assertEquals(3, server3.receiveFrom(addressBuffer, receiveBuffer));
//        } finally {
//            server1.close();
//            server2.close();
//            server3.close();
//            server4.close();
//            server5.close();
//            server6.close();
//            server7.close();
//            xnioWorker.shutdown();
//        }
//    }

    @Test
    public void optionSetup() throws IOException {
        final XnioWorker xnioWorker = xnio.createWorker(OptionMap.EMPTY);
        final MulticastMessageChannel server = xnioWorker.createUdpServer(address1, OptionMap.EMPTY);
        final Option<?>[] unsupportedOptions = OptionHelper.getNotSupportedOptions(Options.BROADCAST,
                Options.RECEIVE_BUFFER, Options.SEND_BUFFER, Options.IP_TRAFFIC_CLASS, Options.MULTICAST_TTL);
        try {
            for (Option<?> option: unsupportedOptions) {
                assertFalse("Server supports " + option, server.supportsOption(option));
                assertNull("Expected null value for option " + option + " but got " + server.getOption(option) + " instead",
                        server.getOption(option));
            }
            assertTrue(server.supportsOption(Options.BROADCAST));
            assertFalse(server.getOption(Options.BROADCAST));
            assertTrue(server.supportsOption(Options.RECEIVE_BUFFER));
            assertTrue(server.getOption(Options.RECEIVE_BUFFER) > 0);
            assertTrue(server.supportsOption(Options.SEND_BUFFER));
            assertTrue(server.getOption(Options.SEND_BUFFER) > 0);
            assertTrue(server.supportsOption(Options.IP_TRAFFIC_CLASS));
            assertNotNull(server.getOption(Options.IP_TRAFFIC_CLASS));
            assertTrue(server.supportsOption(Options.MULTICAST_TTL));
            assertNotNull(server.getOption(Options.MULTICAST_TTL));
    
            assertFalse(server.setOption(Options.BROADCAST, true));
            assertTrue(server.setOption(Options.RECEIVE_BUFFER, 30000) > 0);
            assertTrue(server.setOption(Options.SEND_BUFFER, 3000) > 0);
            assertNotNull(server.setOption(Options.IP_TRAFFIC_CLASS, 200));
            assertNotNull(server.setOption(Options.MULTICAST_TTL, 150));
            assertNull(server.setOption(Options.REUSE_ADDRESSES, true));
    
            assertTrue(server.getOption(Options.BROADCAST));
            assertEquals(30000, (int) server.getOption(Options.RECEIVE_BUFFER));
            assertEquals(3000, (int) server.getOption(Options.SEND_BUFFER));
            assertNotNull((int) server.getOption(Options.IP_TRAFFIC_CLASS)); // it is okay that 200 is not returned
            // 200 will only be set if the channels' family equals StandardProtocolFamily.INET
            assertEquals(150, (int) server.getOption(Options.MULTICAST_TTL));
            assertNull(server.getOption(Options.REUSE_ADDRESSES));
        } finally {
            server.close();
            xnioWorker.shutdown();
        }
        
        // TODO XNIO-171 we check setOption(*, null)
    }

    private class ReadableWaiter implements Runnable {
        private final MulticastMessageChannel channel;
        private final long timeout;
        private final TimeUnit timeoutUnit;

        public ReadableWaiter(MulticastMessageChannel c) {
            this(c, -1, null);
        }

        public ReadableWaiter(MulticastMessageChannel c, long t, TimeUnit tu) {
            channel = c;
            timeout = t;
            timeoutUnit = tu;
        }

        public void run() {
            try {
                if (timeout == -1) {
                    channel.awaitReadable();
                } else {
                    channel.awaitReadable(timeout, timeoutUnit);
                }
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }
}
