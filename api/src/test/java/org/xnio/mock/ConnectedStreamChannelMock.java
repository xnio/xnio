/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2011 Red Hat, Inc. and/or its affiliates, and individual
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

package org.xnio.mock;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import org.xnio.Buffers;
import org.xnio.ChannelListener;
import org.xnio.ChannelListener.Setter;
import org.xnio.IoUtils;
import org.xnio.Option;
import org.xnio.OptionMap;
import org.xnio.XnioExecutor;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.Channels;
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
public class ConnectedStreamChannelMock extends ReadableByteChannelMock implements ConnectedStreamChannel, StreamSourceChannel, StreamSinkChannel, Mock {

    private OptionMap optionMap;
    private Thread readWaiter;
    // written stuff will be copied to this buffer
    private final ByteBuffer writeBuffer = ByteBuffer.allocate(1000);
    // can only write when write is enabled
    private boolean writeEnabled = true;
    private boolean writeResumed = false;
    private boolean writeAwaken = false;
    private boolean readAwaken = false;
    private boolean readResumed = false;
    private boolean readsDown = false;
    private boolean writesDown = false;
    private final boolean allowShutdownWrites = true;
    private boolean flushed = true;
    private boolean flushEnabled = true;
    private XnioWorker worker = new XnioWorkerMock();
    private final XnioIoThread executor = new XnioIoThreadMock(null);
    private Thread writeWaiter;
    private ChannelListener<? super ConnectedStreamChannel> readListener;
    private ChannelListener<? super ConnectedStreamChannel> writeListener;
    private ChannelListener<? super ConnectedStreamChannel> closeListener;
    private String info = null; // any extra information regarding this channel used by tests

    // listener setters
    private final Setter<ConnectedStreamChannel> readListenerSetter = new Setter<ConnectedStreamChannel>() {
        @Override
        public void set(ChannelListener<? super ConnectedStreamChannel> listener) {
            readListener = listener;
        }
    };

    private final Setter<ConnectedStreamChannel> writeListenerSetter = new Setter<ConnectedStreamChannel>() {
        @Override
        public void set(ChannelListener<? super ConnectedStreamChannel> listener) {
            writeListener = listener;
        }
    };

    private final Setter<ConnectedStreamChannel> closeListenerSetter = new Setter<ConnectedStreamChannel>() {
        @Override
        public void set(ChannelListener<? super ConnectedStreamChannel> listener) {
            closeListener = listener;
        }
    };

    /**
     * Feeds {@code readData} to read clients.
     * @param readData data that will be available for reading on this channel mock
     */
    public void setReadData(String... readData) {
        final Thread waiter;
        synchronized (this) {
            int totalLength = super.doSetReadData(readData);
            if (readWaiter == null || totalLength == 0 || !readEnabled) {
                return;
            }
            waiter = readWaiter;
            readWaiter = null;
        }
        LockSupport.unpark(waiter);
    }

    public void setReadDataWithLength(String... readData) {
        final Thread waiter;
        synchronized (this) {
            int totalLength = doSetReadDataWithLength(readData);
            if (readWaiter == null || totalLength == 0 || !readEnabled) {
                return;
            }
            waiter = readWaiter;
        }
        LockSupport.unpark(waiter);
    }

    public void setReadDataWithLength(int length, String... readData) {
        final Thread waiter;
        synchronized (this) {
            int totalLength = doSetReadDataWithLength(length, readData);
            if (readWaiter == null || totalLength == 0 || !readEnabled) {
                return;
            }
            waiter = readWaiter;
        }
        LockSupport.unpark(waiter);
    }

    public void setEof() {
        final Thread waiter;
        synchronized (this) {
            super.setEof();
            if (readWaiter == null || !readEnabled) {
                return;
            }
            waiter = readWaiter;
        }
        LockSupport.unpark(waiter);
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

    public ByteBuffer getWrittenBytes() {
        return writeBuffer;
    }

    public void enableRead(boolean enable) {
        final Thread waiter;
        synchronized (this) {
            super.enableRead(enable);
            if (readWaiter == null || !readEnabled || !((readBuffer.hasRemaining() && readBuffer.limit() != readBuffer.capacity()) || eof)) {
                return;
            }
            waiter = readWaiter;
        }
        LockSupport.unpark(waiter);
    }

    public void enableWrite(boolean enable) {
        final Thread waiter;
        synchronized (this) {
            writeEnabled = enable;
            waiter = writeWaiter;
        }
        if (waiter != null) {
            LockSupport.unpark(waiter);
        }
    }

    @Override
    public boolean supportsOption(Option<?> option) {
        return optionMap != null && optionMap.contains(option);
    }

    @Override
    public <T> T getOption(Option<T> option) throws IOException {
        return optionMap == null? null: optionMap.get(option);
    }

    @Override
    public <T> T setOption(Option<T> option, T value) throws IllegalArgumentException, IOException {
        final OptionMap.Builder optionMapBuilder = OptionMap.builder();
        T previousValue = null;
        if (optionMap != null) {
            optionMapBuilder.addAll(optionMap);
            previousValue = optionMap.get(option);
        }
        optionMapBuilder.set(option, value);
        optionMap = optionMapBuilder.getMap();
        return previousValue;
    }

    public void setOptionMap(OptionMap optionMap) {
        this.optionMap = optionMap;
    }

    @Override
    public OptionMap getOptionMap() {
        return optionMap;
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

    /**
     * This mock supports only one read thread waiting at most.
     */
    @Override
    public void awaitReadable() throws IOException {
        synchronized(this) {
            if (readWaiter != null) {
                throw new IllegalStateException("ConnectedStreamChannelMock can be used only with one read waiter thread at most... there is already a  waiting thread" + readWaiter);
            }
            if (((readBuffer.hasRemaining() && readBuffer.capacity() != readBuffer.limit()) || eof) && readEnabled) {
                return;
            }
            readWaiter = Thread.currentThread();
        }
        LockSupport.park(readWaiter);
        synchronized(this) {
            readWaiter = null;
        }
    }

    /**
     * This mock supports only one read thread waiting at most.
     */
    @Override
    public void awaitReadable(long time, TimeUnit timeUnit) throws IOException {
        synchronized (this) {
            if (readWaiter != null) {
                throw new IllegalStateException("ConnectedStreamChannelMock can be used only with one read waiter thread at most... there is already a  waiting thread" + readWaiter);
            }
            if (((readBuffer.hasRemaining() && readBuffer.capacity() != readBuffer.limit()) || eof) && readEnabled) {
                return;
            }
            readWaiter = Thread.currentThread();
        }
        // FIXME assertSame("ConnectedStreamChannelMock.awaitReadable(long, TimeUnit) can be used only with TimeUnit.NANOSECONDS", TimeUnit.MILLISECONDS, timeUnit);
        LockSupport.parkNanos(readWaiter, timeUnit.toNanos(time));
        synchronized (this) {
            readWaiter = null;
        }
    }

    @Override
    @Deprecated
    public XnioExecutor getReadThread() {
        return executor;
    }

    @Override
    public XnioIoThread getIoThread() {
        return executor;
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
        if (!allowShutdownWrites) {
            return;
        }
        writesDown = true;
        final Thread waiter;
        synchronized (this) {
            super.setEof();
            if (readWaiter == null) {
                return;
            }
            waiter = readWaiter;
        }
        LockSupport.unpark(waiter);
    }

    public boolean isShutdownWrites() {
        return writesDown;
    }

    @Override
    public void awaitWritable() throws IOException {
        synchronized(this) {
            if (writeWaiter != null) {
                throw new IllegalStateException("ConnectedStreamChannelMock can be used only with one write waiter thread at most... there is already a  waiting thread" + writeWaiter);
            }
            if (writeEnabled) {
                return;
            }
            writeWaiter = Thread.currentThread();
        }
        LockSupport.park(writeWaiter);
        synchronized(this) {
            writeWaiter = null;
        }
    }

    @Override
    public void awaitWritable(long time, TimeUnit timeUnit) throws IOException {
        synchronized (this) {
            if (writeWaiter != null) {
                throw new IllegalStateException("ConnectedStreamChannelMock can be used only with one write waiter thread at most... there is already a  waiting thread" + writeWaiter);
            }
            if (writeEnabled) {
                return;
            }
            writeWaiter = Thread.currentThread();
        }
        // FIXME assertSame("ConnectedStreamChannelMock.awaitWritable(long, TimeUnit) can be used only with TimeUnit.NANOSECONDS", TimeUnit.NANOSECONDS, timeUnit);
        LockSupport.parkNanos(writeWaiter, timeUnit.toNanos(time));
        synchronized (this) {
            writeWaiter = null;
        }
    }

    @Override
    @Deprecated
    public XnioExecutor getWriteThread() {
        return executor;
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

    public synchronized void enableFlush(boolean enable) {
        flushEnabled = enable;
    }

    @Override
    public long transferFrom(FileChannel src, long position, long count) throws IOException {
        if (writeEnabled) {
            return src.transferTo(position, count, this);
        }
        return 0;
    }

    @Override
    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        if (writeEnabled) {
            IoUtils.transfer(source, count, throughBuffer, this);
        }
        return 0;
    }

    @Override
    public synchronized int write(ByteBuffer src) throws IOException {
        if (closed && checkClosed) {
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
    public synchronized long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        if (closed && checkClosed) {
            throw new ClosedChannelException();
        }
        if (writeEnabled) {
            if (writeBuffer.limit() < writeBuffer.capacity()) {
                writeBuffer.limit(writeBuffer.capacity());
            }
            int bytes = Buffers.copy(writeBuffer, srcs, offset, length);
            if (bytes > 0) {
                flushed = false;
            }
            return bytes;
        }
        return 0;
    }

    @Override
    public synchronized long write(ByteBuffer[] srcs) throws IOException {
        if (closed && checkClosed) {
            throw new ClosedChannelException();
        }
        if (writeEnabled) {
            if (writeBuffer.limit() < writeBuffer.capacity()) {
                writeBuffer.limit(writeBuffer.capacity());
            }
            return Buffers.copy(writeBuffer, srcs, 0, srcs.length);
        }
        return 0;
    }

    @Override
    public long transferTo(long position, long count, FileChannel target) throws IOException {
        if (readEnabled) {
            return target.transferFrom(this, position, count);
        }
        return 0;
    }

    @Override
    public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
        if (readEnabled) {
            return IoUtils.transfer(this, count, throughBuffer, target);
        }
        return 0;
    }

    @Override
    public synchronized long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        if (closed && checkClosed) {
            throw new ClosedChannelException();
        }
        if (readEnabled) {
            if ((!readBuffer.hasRemaining() || readBuffer.position() == 0 && readBuffer.limit() == readBuffer.capacity()) && eof) {
                return -1;
            }
            if (readBuffer.limit() == readBuffer.capacity() && readBuffer.position() == 0) {
                return 0;
            }
            return Buffers.copy(dsts, offset, length, readBuffer);
        }
        return 0;
    }

    public synchronized boolean allReadDataConsumed() {
        return readBuffer.position() == readBuffer.limit();
    }

    @Override
    public synchronized long read(ByteBuffer[] dsts) throws IOException {
        if (closed && checkClosed) {
            throw new ClosedChannelException();
        }
        if (readEnabled) {
            if ((!readBuffer.hasRemaining() || readBuffer.position() == 0 && readBuffer.limit() == readBuffer.capacity()) && eof) {
                return -1;
            }
            if (readBuffer.limit() == readBuffer.capacity() && readBuffer.position() == 0) {
                return 0;
            }
            return Buffers.copy(dsts, 0, dsts.length, readBuffer);
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

    public void setWorker(XnioWorker worker) {
        this.worker = worker;
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
        return readListenerSetter;
    }

    @Override
    public Setter<? extends ConnectedStreamChannel> getWriteSetter() {
        return writeListenerSetter;
    }

    @Override
    public Setter<? extends ConnectedStreamChannel> getCloseSetter() {
        return closeListenerSetter;
    }

    @Override
    public int writeFinal(ByteBuffer src) throws IOException {
        return Channels.writeFinalBasic(this, src);
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs, int offset, int length) throws IOException {
        return Channels.writeFinalBasic(this, srcs, offset, length);
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs) throws IOException {
        return Channels.writeFinalBasic(this, srcs, 0, srcs.length);
    }

    public ChannelListener<? super ConnectedStreamChannel> getReadListener() {
        return readListener;
    }

    public ChannelListener<? super ConnectedStreamChannel> getWriteListener() {
        return writeListener;
    }

    public ChannelListener<? super ConnectedStreamChannel> getCloseListener() {
        return closeListener;
    }

    @Override
    public String getInfo() {
        return info;
    }

    @Override
    public void setInfo(String i) {
        info = i;
    }

    @Override
    public void close() throws IOException {
        super.close();
        shutdownWrites();
        shutdownReads();
    }

}
