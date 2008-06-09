package org.jboss.xnio.core.nio;

import org.jboss.xnio.channels.MultipointDatagramChannel;
import org.jboss.xnio.channels.MultipointReadHandler;
import org.jboss.xnio.channels.UnsupportedOptionException;
import org.jboss.xnio.channels.ConfigurableChannel;
import org.jboss.xnio.IoHandler;
import java.net.SocketAddress;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.nio.ByteBuffer;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.Map;
import java.util.Collections;

/**
 *
 */
public class BioDatagramChannelImpl implements MultipointDatagramChannel<SocketAddress> {
    private final DatagramSocket datagramSocket;
    private final DatagramPacket receivePacket;
    private final ByteBuffer receiveBuffer;
    private final DatagramPacket sendPacket;
    private final ByteBuffer sendBuffer;
    private final Executor handlerExecutor;
    private final IoHandler<? super MultipointDatagramChannel<SocketAddress>> handler;
    private final Runnable readHandlerTask = new ReadHandlerTask();
    private final Runnable writeHandlerTask = new WriteHandlerTask();
    private final ReaderTask readerTask = new ReaderTask();
    private final WriterTask writerTask = new WriterTask();

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

    protected BioDatagramChannelImpl(final int sendBufSize, final int recvBufSize, final Executor handlerExecutor, final IoHandler<? super MultipointDatagramChannel<SocketAddress>> handler, final DatagramSocket datagramSocket) {
        this.datagramSocket = datagramSocket;
        this.handlerExecutor = handlerExecutor;
        this.handler = handler;
        final byte[] sendBufferBytes = new byte[sendBufSize];
        sendBuffer = ByteBuffer.wrap(sendBufferBytes);
        final byte[] recvBufferBytes = new byte[recvBufSize];
        receiveBuffer = ByteBuffer.wrap(recvBufferBytes);
        sendPacket = new DatagramPacket(sendBufferBytes, sendBufSize);
        receivePacket = new DatagramPacket(recvBufferBytes, recvBufSize);
    }


    public SocketAddress getLocalAddress() {
        return datagramSocket.getLocalSocketAddress();
    }

    public boolean receive(final ByteBuffer buffer, final MultipointReadHandler<SocketAddress> readHandler) throws IOException {
        synchronized (readLock) {
            if (!readable) {
                return false;
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
            // if the user throws an exception from the handler, that's their problem
            if (readHandler != null) readHandler.handle(receivePacket.getSocketAddress(), null);
            return true;
        }
    }

    public boolean isOpen() {
        return datagramSocket.isBound();
    }

    public void close() throws IOException {
        try {
            readerTask.cancel();
        } catch (Throwable t) {
            // todo log it
        }
        try {
            writerTask.cancel();
        } catch (Throwable t) {
            // todo log it
        }
        synchronized (writeLock) {
            writable = false;
        }
        synchronized (readLock) {
            readable = false;
        }
        datagramSocket.close();
    }

    public boolean send(final SocketAddress target, final ByteBuffer buffer) throws IOException {
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

    public boolean send(final SocketAddress target, final ByteBuffer[] dsts) throws IOException {
        return send(target, dsts, 0, dsts.length);
    }

    public boolean send(final SocketAddress target, final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        synchronized (writeLock) {
            if (! writable) {
                return false;
            }
            sendBuffer.clear();
            long t = 0;
            for (int i = 0; i < length; i ++) {
                t += (long) dsts[i + offset].remaining();
            }
            if ((long)sendBuffer.remaining() < t) {
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
                handlerExecutor.execute(readHandlerTask);
            }
        }
    }

    public void resumeWrites() {
        synchronized (writeLock) {
            enableWrite = true;
            if (writable) {
                handlerExecutor.execute(writeHandlerTask);
            }
        }
    }

    public Object getOption(final String name) throws UnsupportedOptionException, IOException {
        throw new UnsupportedOptionException("No options supported");
    }

    public Map<String, Class<?>> getOptions() {
        return Collections.emptyMap();
    }

    public ConfigurableChannel setOption(final String name, final Object value) throws IllegalArgumentException, IOException {
        throw new UnsupportedOptionException("No options supported");
    }

    private final class ReaderTask implements Runnable {
        private volatile Thread thread;

        public void run() {
            thread = Thread.currentThread();
            try {
                for (;;) {
                    synchronized (readLock) {
                        while (readable) try {
                            readLock.wait();
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                    try {
                        datagramSocket.receive(receivePacket);
                    } catch (IOException e) {
                        synchronized (readLock) {
                            // pass the exception on to the user
                            readException = e;
                            readable = true;
                            if (enableRead) {
                                handlerExecutor.execute(readHandlerTask);
                            }
                            continue;
                        }
                    }
                    synchronized (readLock) {
                        receiveBuffer.limit(receivePacket.getLength());
                        receiveBuffer.position(0);
                        readable = true;
                        if (enableRead) {
                            handlerExecutor.execute(readHandlerTask);
                        }
                    }
                }
            } finally {
                thread = null;
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
            for (;;) {
                thread = Thread.currentThread();
                synchronized (writeLock) {
                    writable = true;
                    if (enableWrite) {
                        enableWrite = false;
                        handlerExecutor.execute(writeHandlerTask);
                    }
                    while (writable) try {
                        writeLock.wait();
                    } catch (InterruptedException e) {
                        return;
                    }
                }
                try {
                    datagramSocket.send(sendPacket);
                } catch (IOException e) {
                    // todo log failure
                }
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
            handler.handleReadable(BioDatagramChannelImpl.this);
        }
    }

    private final class WriteHandlerTask implements Runnable {
        public void run() {
            handler.handleWritable(BioDatagramChannelImpl.this);
        }
    }
}
