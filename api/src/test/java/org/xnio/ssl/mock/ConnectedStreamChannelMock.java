/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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
package org.xnio.ssl.mock;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.xnio.Buffers;
import org.xnio.ChannelListener;
import org.xnio.ChannelListener.Setter;
import org.xnio.Option;
import org.xnio.OptionMap;
import org.xnio.Xnio;
import org.xnio.XnioExecutor;
import org.xnio.XnioWorker;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 * Mock of a connected stream channel.<p>
 * This channel mock will store everything that is written to it for later comparison, and allows feeding of bytes for
 * reading.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
public class ConnectedStreamChannelMock implements ConnectedStreamChannel {

    // written stuff will be copied to this buffer
    private ByteBuffer writeBuffer = ByteBuffer.allocate(1000);
    // read stuff will be taken from this buffer
    private ByteBuffer readBuffer = ByteBuffer.allocate(10000);
    // read stuff can only be read if read is enabled
    private boolean readEnabled;
    // can only write when write is enabled
    private boolean writeEnabled = true;
    // indicates if this channel is closed
    private boolean closed = false;
    private boolean writeResumed = false;
    private boolean writeAwaken = false;
    private boolean readAwaken = false;
    private boolean readResumed = false;
    private boolean readsDown = false;
    private boolean writesDown = false;
    private boolean allowShutdownWrites = true;
    private boolean flushed = true;
    private boolean flushEnabled = true;
    private XnioWorker worker = new XnioWorkerMock();

    // dummy listener setter
    private final ChannelListener.Setter<ConnectedStreamChannel> listenerSetter = new ChannelListener.Setter<ConnectedStreamChannel>() {
        @Override
        public void set(ChannelListener<? super ConnectedStreamChannel> listener) {}
    };

    /**
     * Feeds {@code readData} to read clients.
     * @param readData data that will be available for reading on this channel mock
     */
    public synchronized void setReadData(String... readData) {
        int totalLength = 0;
        for (String data: readData) {
            totalLength += data.length();
        }
        int position = readBuffer.position();
        boolean resetPosition = false;
        if (!readBuffer.hasRemaining()) {
            readBuffer.compact();
        } else if(readBuffer.position() > 0 || readBuffer.limit() != readBuffer.capacity()) {
            if (readBuffer.capacity() - readBuffer.limit() < totalLength) {
                if (readBuffer.position() > 0 && readBuffer.capacity() - readBuffer.limit() + readBuffer.position() >= totalLength) {
                    // TODO delete from 0 to position -1, and put data at beginning of buffer
                }
                throw new RuntimeException("ReadBuffer is full - not enough space to add more read data");
            }
            int limit = readBuffer.limit();
            readBuffer.position(limit);
            readBuffer.limit(limit += totalLength);
            resetPosition = true;
        }
        for (String data: readData) {
            readBuffer.put(data.getBytes());
        }
        readBuffer.flip();
        if (resetPosition) {
            readBuffer.position(position);
        }
    }

    public synchronized void enableRead(boolean enable) {
        readEnabled = enable;
    }

    public synchronized void enableWrite(boolean enable) {
        writeEnabled = enable;
    }

    /**
     * Returns all the bytes that have been written to this channel mock.
     * 
     * @return the written bytes in the form of a UTF-8 string
     */
    public String getWrittenText() {
        if (writeBuffer.position() == 0 && writeBuffer.limit() == writeBuffer.capacity()) {
            return "";
        }
        writeBuffer.flip();
        return Buffers.getModifiedUtf8(writeBuffer);
    }

    @Override
    public void close() throws IOException {
        closed = true;
    }

    @Override
    public boolean isOpen() {
        return !closed;
    }

    private OptionMap optionMap; 

    @Override
    public boolean supportsOption(Option<?> option) {
        return optionMap.contains(option);
    }

    @Override
    public <T> T getOption(Option<T> option) throws IOException {
        return optionMap.get(option);
    }

    @Override
    public <T> T setOption(Option<T> option, T value) throws IllegalArgumentException, IOException {
        throw new RuntimeException("Not supported");
    }

    public void setOptionMap(OptionMap optionMap) {
        this.optionMap = optionMap;
    }

    @Override
    public void suspendReads() {
        readAwaken = false;
        readResumed = false;
    }

    @Override
    public void resumeReads() {
        readResumed = true;
    }

    @Override
    public boolean isReadResumed() {
        return readResumed;
    }

    @Override
    public void shutdownReads() throws IOException {
        readsDown = true;
    }
    
    public boolean isShutdownReads() {
        return readsDown;
    }

    @Override
    public void awaitReadable() throws IOException {
        throw new RuntimeException ("Not supported");
    }

    @Override
    public void awaitReadable(long time, TimeUnit timeUnit) throws IOException {
        throw new RuntimeException ("Not supported");
    }

    @Override
    public XnioExecutor getReadThread() {
        throw new RuntimeException ("Not supported");
    }

    @Override
    public void suspendWrites() {
        writeAwaken = false;
        writeResumed = false;
    }

    @Override
    public void resumeWrites() {
        writeResumed = true;
    }

    @Override
    public boolean isWriteResumed() {
        return writeResumed;
    }

    @Override
    public synchronized void shutdownWrites() throws IOException {
        if (allowShutdownWrites) {
            writesDown = true;
        }
        return;
    }

    public boolean isShutdownWrites() {
        return writesDown;
    }

    @Override
    public void awaitWritable() throws IOException {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void awaitWritable(long time, TimeUnit timeUnit) throws IOException {
        throw new RuntimeException("Not supported");
    }

    @Override
    public XnioExecutor getWriteThread() {
        throw new RuntimeException("Not supported");
    }

    @Override
    public synchronized boolean flush() throws IOException {
        if (flushEnabled) {
            flushed = true;
        }
        return flushed;
    }

    public boolean isFlushed() {
        return flushed;
    }

    public void enableFlush(boolean enable) {
        flushEnabled = enable;
    }

    @Override
    public long transferFrom(FileChannel src, long position, long count) throws IOException {
        throw new RuntimeException("Not supported");
    }

    @Override
    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        throw new RuntimeException("Not supported");
    }

    @Override
    public synchronized int write(ByteBuffer src) throws IOException {
        if (closed) {
            throw new ClosedChannelException();
        }
        if (writeEnabled) {
            if (writeBuffer.limit() < writeBuffer.capacity()) {
                writeBuffer.limit(writeBuffer.capacity());
            }
            int bytes = Buffers.copy(writeBuffer, src);
            if (bytes > 0) {
                flushed = false;
            }
            return bytes;
        }
        return 0;
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        if (closed) {
            throw new ClosedChannelException();
        }
        if (writeEnabled) {
            int bytes = Buffers.copy(writeBuffer, srcs, offset, length);
            if (bytes > 0) {
                flushed = false;
            }
            return bytes;
        }
        return 0;
    }

    @Override
    public long write(ByteBuffer[] srcs) throws IOException {
        if (closed) {
            throw new ClosedChannelException();
        }
        if (writeEnabled) {
            return Buffers.copy(writeBuffer, srcs, 0, srcs.length);
        }
        return 0;
    }

    @Override
    public long transferTo(long position, long count, FileChannel target) throws IOException {
        throw new RuntimeException("Not supported");
    }

    @Override
    public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
        throw new RuntimeException("Not supported");
    }

    @Override
    public synchronized int read(ByteBuffer dst) throws IOException {
        if (closed) {
            throw new ClosedChannelException();
        }
        if (readEnabled) {
            try {
                return Buffers.copy(dst, readBuffer);
            } catch (RuntimeException e) {
                System.out.println("Got exception at attempt of copying contents of dst "+ dst.remaining()  +  " into read buffer " + readBuffer.remaining());
                throw e;
            }
        }
        return 0;
    }

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        if (closed) {
            throw new ClosedChannelException();
        }
        if (readEnabled) {
            return Buffers.copy(readBuffer, dsts, offset, length);
        }
        return 0;
    }
    
    public boolean allReadDataConsumed() {
        return readBuffer.position() == readBuffer.limit();
    }

    @Override
    public long read(ByteBuffer[] dsts) throws IOException {
        if (closed) {
            throw new ClosedChannelException();
        }
        if (readEnabled) {
            return Buffers.copy(readBuffer, dsts, 0, dsts.length);
        }
        return 0;
    }

    private SocketAddress peerAddress;

    @Override
    public SocketAddress getPeerAddress() {
        return peerAddress;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <A extends SocketAddress> A getPeerAddress(Class<A> type) {
        if (type.isAssignableFrom(peerAddress.getClass())) {
            return (A) peerAddress;
        }
        return null;
    }

    public void setPeerAddress(SocketAddress peerAddress) {
        this.peerAddress = peerAddress;
    }

    private SocketAddress localAddress;

    @Override
    public SocketAddress getLocalAddress() {
        return localAddress;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <A extends SocketAddress> A getLocalAddress(Class<A> type) {
        if (type.isAssignableFrom(localAddress.getClass())) {
            return (A) localAddress;
        }
        return null;
    }

    public void setLocalAddress(SocketAddress localAddress) {
        this.localAddress = localAddress;
    }

    @Override
    public XnioWorker getWorker() {
        return worker;
    }

    @Override
    public void wakeupReads() {
        readAwaken = true;
        readResumed = true;
    }

    public boolean isReadAwaken() {
        return readAwaken;
    }

    @Override
    public void wakeupWrites() {
        writeAwaken = true;
        writeResumed = true;
    }

    public boolean isWriteAwaken() {
        return writeAwaken;
    }

    @Override
    public Setter<? extends ConnectedStreamChannel> getReadSetter() {
        return listenerSetter;
    }

    @Override
    public Setter<? extends ConnectedStreamChannel> getWriteSetter() {
        return listenerSetter;
    }

    @Override
    public Setter<? extends ConnectedStreamChannel> getCloseSetter() {
        return listenerSetter;
    }

    private static class XnioWorkerMock extends XnioWorker {

        public XnioWorkerMock() {
            super(new Xnio("mock"){
                @Override
                public XnioWorker createWorker(ThreadGroup threadGroup, OptionMap optionMap, Runnable terminationTask)
                        throws IOException, IllegalArgumentException {
                    return null;
                }}, new ThreadGroup("mock"), OptionMap.EMPTY, new Runnable() {
                @Override
                public void run() {}});
        }

        @Override
        public void shutdown() {}

        @Override
        public List<Runnable> shutdownNow() {return null;}

        @Override
        public boolean isShutdown() {return false;}

        @Override
        public boolean isTerminated() {return false;}

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return false;
        }
    }
}