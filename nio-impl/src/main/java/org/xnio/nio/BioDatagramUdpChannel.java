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

package org.xnio.nio;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.logging.Logger;
import org.xnio.Buffers;
import org.xnio.ChannelListeners;
import org.xnio.Option;
import org.xnio.Options;
import org.xnio.XnioExecutor;
import org.xnio.channels.MulticastMessageChannel;
import org.xnio.channels.SocketAddressBuffer;
import org.xnio.channels.UnsupportedOptionException;

/**
 *
 */
class BioDatagramUdpChannel extends AbstractNioChannel<BioDatagramUdpChannel> implements MulticastMessageChannel {
    private static final Logger log = Logger.getLogger("org.xnio.nio.udp.bio-server.channel");

    private final DatagramSocket datagramSocket;
    private final DatagramPacket receivePacket;
    private final ByteBuffer receiveBuffer;
    private final DatagramPacket sendPacket;
    private final ByteBuffer sendBuffer;
    private final ReaderTask readerTask = new ReaderTask();
    private final WriterTask writerTask = new WriterTask();
    private final ReadHandlerTask readHandlerTask = new ReadHandlerTask();
    private final WriteHandlerTask writeHandlerTask = new WriteHandlerTask();

    private final NioSetter<BioDatagramUdpChannel> readSetter = new NioSetter<BioDatagramUdpChannel>();
    private final NioSetter<BioDatagramUdpChannel> writeSetter = new NioSetter<BioDatagramUdpChannel>();

    private final WorkerThread readThread;
    private final WorkerThread writeThread;

    private final Object readLock = new Object();
    private final Object writeLock = new Object();

    // @protectedby {@link #readLock}
    private boolean enableRead;
    // @protectedby {@link #writeLock}
    private boolean enableWrite;
    // @protectedby {@link #readLock}
    private boolean readable;
    // @protectedby {@link #writeLock}
    private boolean writable;
    // @protectedby {@link #readLock}
    private IOException readException;

    private final AtomicBoolean closeCalled = new AtomicBoolean(false);

    BioDatagramUdpChannel(final NioXnioWorker worker, int sendBufSize, int recvBufSize, final DatagramSocket datagramSocket, final WorkerThread readThread, final WorkerThread writeThread) {
        super(worker);
        this.datagramSocket = datagramSocket;
        if (sendBufSize == -1) {
            sendBufSize = 4096;
        } else if (sendBufSize < 0) {
            throw new IllegalArgumentException("sendBufSize is less than 0");
        }
        if (recvBufSize == -1) {
            recvBufSize = 4096;
        } else if (recvBufSize < 0) {
            throw new IllegalArgumentException("recvBufSize is less than 0");
        }
        final byte[] sendBufferBytes = new byte[sendBufSize];
        sendBuffer = ByteBuffer.wrap(sendBufferBytes);
        final byte[] recvBufferBytes = new byte[recvBufSize];
        receiveBuffer = ByteBuffer.wrap(recvBufferBytes);
        sendPacket = new DatagramPacket(sendBufferBytes, sendBufSize);
        receivePacket = new DatagramPacket(recvBufferBytes, recvBufSize);
        this.readThread = readThread;
        this.writeThread = writeThread;
        this.worker = worker;
        log.tracef("Constructed a new channel (%s); send buffer size %d, receive buffer size %d", this, Integer.valueOf(sendBufSize), Integer.valueOf(recvBufSize));
    }

    protected void open() {
        final ThreadFactory threadFactory = Executors.defaultThreadFactory();
        final Thread readThread = threadFactory.newThread(readerTask);
        boolean ok = false;
        try {
            final Thread writeThread = threadFactory.newThread(writerTask);
            try {
                readThread.start();
                writeThread.start();
                ok = true;
            } finally {
                if (! ok) {
                    writerTask.cancel();
                }
            }
        } finally {
            if (! ok) {
                readerTask.cancel();
            }
        }
        log.tracef("Channel %s opened", this);
    }

    public NioSetter<BioDatagramUdpChannel> getReadSetter() {
        return readSetter;
    }

    public NioSetter<BioDatagramUdpChannel> getWriteSetter() {
        return writeSetter;
    }

    public boolean flush() throws IOException {
        return true;
    }

    public SocketAddress getLocalAddress() {
        return datagramSocket.getLocalSocketAddress();
    }

    public <A extends SocketAddress> A getLocalAddress(final Class<A> type) {
        final SocketAddress address = getLocalAddress();
        return type.isInstance(address) ? type.cast(address) : null;
    }

    public int receiveFrom(final SocketAddressBuffer addressBuffer, final ByteBuffer buffer) throws IOException {
        synchronized (readLock) {
            if (!readable) {
                return 0;
            }
            readable = false;
            if (readException != null) {
                try {
                    readException.setStackTrace(new Throwable().getStackTrace());
                    throw readException;
                } finally {
                    readException = null;
                }
            }
            final int size = Math.min(buffer.remaining(), receiveBuffer.remaining());
            receiveBuffer.limit(size);
            buffer.put(receiveBuffer);
            readLock.notify();
            final SocketAddress socketAddress = receivePacket.getSocketAddress();
            if (addressBuffer != null) {
                addressBuffer.setSourceAddress(socketAddress);
                addressBuffer.setDestinationAddress(null);
            }
            return size;
        }
    }

    public long receiveFrom(final SocketAddressBuffer addressBuffer, final ByteBuffer[] buffers) throws IOException {
        return receiveFrom(addressBuffer, buffers, 0, buffers.length);
    }

    public long receiveFrom(final SocketAddressBuffer addressBuffer, final ByteBuffer[] buffers, final int offs, final int len) throws IOException {
        synchronized (readLock) {
            if (!readable) {
                return 0;
            }
            readable = false;
            if (readException != null) {
                try {
                    readException.setStackTrace(new Throwable().getStackTrace());
                    throw readException;
                } finally {
                    readException = null;
                }
            }
            final int size = (int) Math.min(Buffers.remaining(buffers, offs, len), (long) receiveBuffer.remaining());
            receiveBuffer.limit(size);
            Buffers.copy(buffers, offs, len, receiveBuffer);
            readLock.notify();
            final SocketAddress socketAddress = receivePacket.getSocketAddress();
            if (addressBuffer != null) {
                addressBuffer.setSourceAddress(socketAddress);
                addressBuffer.setDestinationAddress(null);
            }
            return size;
        }
    }

    public boolean isOpen() {
        return ! datagramSocket.isClosed();
    }

    public void close() throws IOException {
        if (! closeCalled.getAndSet(true)) {
            synchronized (writeLock) {
                enableWrite = false;
            }
            synchronized (readLock) {
                enableRead = false;
            }
            try {
                readerTask.cancel();
            } catch (Throwable t) {
                log.tracef(t, "Reader task cancel failed");
            }
            try {
                writerTask.cancel();
            } catch (Throwable t) {
                log.tracef(t, "Writer task cancel failed");
            }
            synchronized (writeLock) {
                writable = false;
            }
            synchronized (readLock) {
                readable = false;
            }
            datagramSocket.close();
            invokeCloseHandler();
            log.tracef("Closing channel %s", this);
        }
    }

    public boolean sendTo(final SocketAddress target, final ByteBuffer buffer) throws IOException {
        synchronized (writeLock) {
            if (! writable) {
                return false;
            }
            sendBuffer.clear();
            if (sendBuffer.remaining() < buffer.remaining()) {
                throw new IOException("Insufficient room in send buffer (send will never succeed); send buffer is " + sendBuffer.remaining() + " bytes, but transmitted datagram is " + buffer.remaining() + " bytes");
            }
            sendBuffer.put(buffer);
            sendPacket.setSocketAddress(target);
            sendPacket.setData(sendBuffer.array(), sendBuffer.arrayOffset(), sendBuffer.position());
            writeLock.notifyAll();
            writable = false;
            return true;
        }
    }

    public boolean sendTo(final SocketAddress target, final ByteBuffer[] dsts) throws IOException {
        return sendTo(target, dsts, 0, dsts.length);
    }

    public boolean sendTo(final SocketAddress target, final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        synchronized (writeLock) {
            if (! writable) {
                return false;
            }
            sendBuffer.clear();
            long t = 0;
            for (int i = 0; i < length; i ++) {
                t += dsts[i + offset].remaining();
            }
            if (sendBuffer.remaining() < t) {
                throw new IOException("Insufficient room in send buffer (send will never succeed); send buffer is " + sendBuffer.remaining() + " bytes, but transmitted datagram is " + t + " bytes");
            }
            for (int i = 0; i < length; i ++) {
                sendBuffer.put(dsts[i + offset]);
            }
            sendPacket.setSocketAddress(target);
            sendPacket.setData(sendBuffer.array(), sendBuffer.arrayOffset(), sendBuffer.position());
            writeLock.notifyAll();
            writable = false;
            return true;
        }
    }

    public void suspendReads() {
        synchronized (readLock) {
            enableRead = false;
        }
    }

    public void suspendWrites() {
        synchronized (readLock) {
            enableWrite = false;
        }
    }

    public void resumeReads() {
        synchronized (readLock) {
            enableRead = true;
            if (readable) {
                final WorkerThread readThread = this.readThread;
                if (readThread == null) {
                    throw new IllegalStateException("No read thread");
                }
                readThread.execute(readHandlerTask);
            }
            readLock.notifyAll();
        }
    }

    public boolean isReadResumed() {
        synchronized (readLock) {
            return enableRead;
        }
    }

    public void resumeWrites() {
        synchronized (writeLock) {
            enableWrite = true;
            if (writable) {
                final WorkerThread writeThread = this.writeThread;
                if (writeThread == null) {
                    throw new IllegalStateException("No write thread");
                }
                writeThread.execute(writeHandlerTask);
            }
            writeLock.notifyAll();
        }
    }

    public boolean isWriteResumed() {
        synchronized (writeLock) {
            return enableWrite;
        }
    }

    public void wakeupReads() {
        resumeReads();
        final WorkerThread readThread = this.readThread;
        if (readThread != null) readThread.execute(readHandlerTask);
    }

    public void wakeupWrites() {
        resumeWrites();
        final WorkerThread writeThread = this.writeThread;
        if (writeThread != null) writeThread.execute(writeHandlerTask);
    }

    public void shutdownReads() throws IOException {
        throw new UnsupportedOperationException("Shutdown reads");
    }

    public void shutdownWrites() throws IOException {
        throw new UnsupportedOperationException("Shutdown writes");
    }

    public void awaitReadable() throws IOException {
        try {
            synchronized (readLock) {
                if (! isOpen()) {
                    return;
                }
                while (! readable) {
                    readLock.wait();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
        try {
            synchronized (readLock) {
                if (! isOpen()) {
                    return;
                }
                if (! readable) {
                    timeUnit.timedWait(readLock, time);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public XnioExecutor getReadThread() {
        return readThread;
    }

    public void awaitWritable() throws IOException {
        try {
            synchronized (writeLock) {
                if (! isOpen()) {
                    return;
                }
                while (! writable) {
                    writeLock.wait();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
        try {
            synchronized (writeLock) {
                if (! isOpen()) {
                    return;
                }
                if (! writable) {
                    timeUnit.timedWait(writeLock, time);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public XnioExecutor getWriteThread() {
        return writeThread;
    }

    private static final Set<Option<?>> OPTIONS = Option.setBuilder()
            .add(Options.BROADCAST)
            .add(Options.IP_TRAFFIC_CLASS)
            .create();

    public boolean supportsOption(final Option<?> option) {
        return OPTIONS.contains(option);
    }

    public <T> T getOption(final Option<T> option) throws UnsupportedOptionException, IOException {
        if (Options.BROADCAST.equals(option)) {
            return option.cast(Boolean.valueOf(datagramSocket.getBroadcast()));
        } else if (Options.IP_TRAFFIC_CLASS.equals(option)) {
            final int v = datagramSocket.getTrafficClass();
            return v == -1 ? null : option.cast(Integer.valueOf(v));
        } else {
            return null;
        }
    }

    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        final Object old;
        if (Options.BROADCAST.equals(option)) {
            old = Boolean.valueOf(datagramSocket.getBroadcast());
            datagramSocket.setBroadcast(Options.BROADCAST.cast(value).booleanValue());
        } else if (Options.IP_TRAFFIC_CLASS.equals(option)) {
            old = Integer.valueOf(datagramSocket.getTrafficClass());
            datagramSocket.setTrafficClass(Options.IP_TRAFFIC_CLASS.cast(value).intValue());
        } else {
            return null;
        }
        return option.cast(old);
    }

    public Key join(final InetAddress group, final NetworkInterface iface) throws IOException {
        throw new UnsupportedOptionException("Multicast not supported");
    }

    public Key join(final InetAddress group, final NetworkInterface iface, final InetAddress source) throws IOException {
        throw new UnsupportedOptionException("Multicast not supported");
    }

    private final class ReaderTask implements Runnable {
        private volatile Thread thread;

        public void run() {
            thread = Thread.currentThread();
            try {
                for (;;) {
                    synchronized (readLock) {
                        while (readable) {
                            try {
                                log.trace("Waiting for user to consume read data");
                                readLock.wait();
                            } catch (InterruptedException e) {
                                return;
                            }
                        }
                    }
                    try {
                        datagramSocket.receive(receivePacket);
                        log.trace("Packet received");
                    } catch (IOException e) {
                        synchronized (readLock) {
                            // pass the exception on to the user
                            readException = e;
                            readable = true;
                            if (enableRead) {
                                wakeupReads();
                            }
                            continue;
                        }
                    }
                    synchronized (readLock) {
                        receiveBuffer.limit(receivePacket.getLength());
                        receiveBuffer.position(0);
                        readable = true;
                        if (enableRead) {
                            wakeupReads();
                        }
                    }
                }
            } finally {
                thread = null;
                log.trace("Exiting thread");
            }
        }

        public void cancel() {
            final Thread thread = this.thread;
            if (thread != null) {
                thread.interrupt();
            }
        }
    }

    private final class WriterTask implements Runnable {
        private volatile Thread thread;

        public void run() {
            thread = Thread.currentThread();
            try {
                for (;;) {
                    synchronized (writeLock) {
                        writable = true;
                        while (writable) {
                            if (enableWrite) {
                                enableWrite = false;
                                wakeupWrites();
                            }
                            if (writable) try {
                                writeLock.wait();
                            } catch (InterruptedException e) {
                                return;
                            }
                        }
                    }
                    try {
                        datagramSocket.send(sendPacket);
                    } catch (IOException e) {
                        log.tracef("Packet send failed: %s", e);
                    }
                }
            } finally {
                thread = null;
                log.trace("Exiting thread");
            }
        }

        public void cancel() {
            final Thread thread = this.thread;
            if (thread != null) {
                thread.interrupt();
            }
        }
    }

    private final class ReadHandlerTask implements Runnable {
        public void run() {
            final boolean readable;
            synchronized (readLock) {
                readable = BioDatagramUdpChannel.this.readable;
            }
            if (readable) ChannelListeners.invokeChannelListener(BioDatagramUdpChannel.this, getReadSetter().get());
        }
    }

    private final class WriteHandlerTask implements Runnable {
        public void run() {
            final boolean writable;
            synchronized (writeLock) {
                writable = BioDatagramUdpChannel.this.writable;
            }
            if (writable) ChannelListeners.invokeChannelListener(BioDatagramUdpChannel.this, getWriteSetter().get());
        }
    }
}
