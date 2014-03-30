/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2013 Red Hat, Inc. and/or its affiliates, and individual
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
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import org.xnio.Buffers;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.StreamConnection;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.AssembledConnectedStreamChannel;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.Conduits;
import org.xnio.conduits.ReadReadyHandler;
import org.xnio.conduits.StreamSinkConduit;
import org.xnio.conduits.StreamSourceConduit;
import org.xnio.conduits.WriteReadyHandler;

/**
 * Mock of a sink/source conduit.<p>
 * This channel mock will store everything that is written to it for later comparison, and allows feeding of bytes for
 * reading.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 */
public class ConduitMock implements StreamSinkConduit, StreamSourceConduit, Mock{

    // written stuff will be copied to this buffer
    private ByteBuffer writeBuffer = ByteBuffer.allocate(1000);
    // read stuff will be taken from this buffer
    private ByteBuffer readBuffer = ByteBuffer.allocate(10000);
    // if eof is true, read will return -1 if readBuffer is empty
    private boolean eof = false;

    // read stuff can only be read if read operations are enabled
    private boolean readsEnabled;
    // can only write when write operations are enabled
    private boolean writesEnabled = true;
    // terminateWrites() will be ignored if allowTerminateWrites is false
    private boolean allowTerminateWrites = true;
    // is flush enabled
    private boolean flushEnabled = true;
    // enables check for closed conduit (if an attempt to perform an operation is performed once this conduit is
    // closed, a ClosedChannelException will be thrown only if checkClosed is true)
    private boolean checkClosed = true;


    // is write operation resumed
    private boolean writesResumed = false;
    // is write operation awaken
    private boolean writesAwaken = false;
    // is write operation terminated
    private boolean writesTerminated = false;
    // is write operation truncated
    private boolean writesTruncated = false;
    // are all written contents flushed
    private boolean flushed = true;
    // is read operation resumed
    private boolean readsResumed = false;
    // is read operation awaken
    private boolean readsAwaken = false;
    // is read operation terminated
    private boolean readsTerminated = false;
    // indicates if this conduit is closed
    private boolean closed = false;

    // the worker
    private XnioWorker worker;
    // the executor
    private XnioIoThread executor;

    // read waiter
    private Thread readWaiter;
    // write waiter
    private Thread writeWaiter;

    // write ready handler
    // implement this when needed
    @SuppressWarnings("unused")
    private WriteReadyHandler writeReadyHandler;
    // read ready handler
    // implement this when needed
    @SuppressWarnings("unused")
    private ReadReadyHandler readReadyHandler;

    // any extra information regarding this channel used by tests
    private String info = null;

    public ConduitMock(XnioWorker worker, XnioIoThread xnioIoThread) {
        this.executor = xnioIoThread;
        this.worker = worker;
    }

    public ConduitMock() {
        final XnioWorkerMock worker = new XnioWorkerMock();
        this.worker = worker;
        this.executor = worker.chooseThread();
    }

    /**
     * Returns the executor.
     */
    XnioIoThread getXnioIoThread() {
        return executor;
    }

    // implement this for handlers when needed
    // listener setters
//    private final ChannelListener.Setter<ConnectedStreamChannel> readListenerSetter = new ChannelListener.Setter<ConnectedStreamChannel>() {
//        @Override
//        public void set(ChannelListener<? super ConnectedStreamChannel> listener) {
//            readListener = listener;
//        }
//    };
//
//    private final ChannelListener.Setter<ConnectedStreamChannel> writeListenerSetter = new ChannelListener.Setter<ConnectedStreamChannel>() {
//        @Override
//        public void set(ChannelListener<? super ConnectedStreamChannel> listener) {
//            writeListener = listener;
//        }
//    };
//
//    private final ChannelListener.Setter<ConnectedStreamChannel> closeListenerSetter = new ChannelListener.Setter<ConnectedStreamChannel>() {
//        @Override
//        public void set(ChannelListener<? super ConnectedStreamChannel> listener) {
//            closeListener = listener;
//        }
//    };


    /**
     * Feeds {@code readData} to read clients.
     * @param readData data that will be available for reading
     */
    public void setReadData(String... readData) {
        final Thread waiter;

        synchronized (this) {
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
                    readBuffer.compact();
                }
                throw new RuntimeException("ReadBuffer is full - not enough space to add more read data");
            }
            int limit = readBuffer.limit();
            readBuffer.position(limit);
            readBuffer.limit(limit += totalLength);
            resetPosition = true;
        }
        for (String data: readData) {
            try {
                readBuffer.put(data.getBytes("UTF-8"));
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }
        readBuffer.flip();
        if (resetPosition) {
            readBuffer.position(position);
        }
        
        if (readWaiter == null || totalLength == 0 || !readsEnabled) {
            return;
        }
        waiter = readWaiter;
        readWaiter = null;
        }
        LockSupport.unpark(waiter);
    }

    /**
     * Feeds {@code readData} to read clients.
     * @param readData data that will be available for reading
     */
    public void setReadDataWithLength(String... readData) {
        final Thread waiter;
        synchronized (this) {
            if (eof == true) {
                throw new IllegalStateException("Cannot add read data once eof is set");
            }
            int totalLength = 0;
            for (String data: readData) {
                totalLength += data.length();
            }
            int position = readBuffer.position();
            boolean resetPosition = false;
            if (!readBuffer.hasRemaining()) {
                readBuffer.compact();
            } else if(readBuffer.position() > 0 || readBuffer.limit() != readBuffer.capacity()) {
                if (readBuffer.capacity() - readBuffer.limit() + 4 < totalLength) {
                    if (readBuffer.position() > 0 && readBuffer.capacity() - readBuffer.limit() + readBuffer.position() + 4 >= totalLength) {
                        readBuffer.compact();
                    }
                    throw new RuntimeException("ReadBuffer is full - not enough space to add more read data");
                }
                int limit = readBuffer.limit();
                readBuffer.position(limit);
                readBuffer.limit(limit += totalLength + 4);
                resetPosition = true;
            }
            readBuffer.putInt(totalLength);
            for (String data: readData) {
                try {
                    readBuffer.put(data.getBytes("UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }
            }
            readBuffer.flip();
            if (resetPosition) {
                readBuffer.position(position);
            }
            if (readWaiter == null || totalLength == 0 || !readsEnabled) {
                return;
            }
            waiter = readWaiter;
        }
        LockSupport.unpark(waiter);
    }

    /**
     * Feeds {@code readData} to read clients.
     * @param readData data that will be available for reading on this channel mock
     */
    public void setReadDataWithLength(int length, String... readData) {
        final Thread waiter;
        synchronized (this) {
            if (eof == true) {
                throw new IllegalStateException("Cannot add read data once eof is set");
            }
            int totalLength = 0;
            for (String data: readData) {
                totalLength += data.length();
            }
            int position = readBuffer.position();
            boolean resetPosition = false;
            if (!readBuffer.hasRemaining()) {
                readBuffer.compact();
            } else if(readBuffer.position() > 0 || readBuffer.limit() != readBuffer.capacity()) {
                if (readBuffer.capacity() - readBuffer.limit() + 4 < totalLength) {
                    if (readBuffer.position() > 0 && readBuffer.capacity() - readBuffer.limit() + readBuffer.position() + 4 >= totalLength) {
                        readBuffer.compact();
                    }
                    throw new RuntimeException("ReadBuffer is full - not enough space to add more read data");
                }
                int limit = readBuffer.limit();
                readBuffer.position(limit);
                readBuffer.limit(limit += totalLength + 4);
                resetPosition = true;
            }
            readBuffer.putInt(length);
            for (String data: readData) {
                try {
                    readBuffer.put(data.getBytes("UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }
            }
            readBuffer.flip();
            if (resetPosition) {
                readBuffer.position(position);
            }
            if (readWaiter == null || totalLength == 0 || !readsEnabled) {
                return;
            }
            waiter = readWaiter;
        }
        LockSupport.unpark(waiter);
    }

    /**
     * Marks the eof for read operations. Once eof is set, all read operations will return -1 as soon as there is no
     * read data available. 
     */
    public void setEof() {
        final Thread waiter;
        synchronized (this) {
            eof = true;
            if (readWaiter == null || !readsEnabled) {
                return;
            }
            waiter = readWaiter;
        }
        LockSupport.unpark(waiter);
    }

    /**
     * Indicates if has all read data been consumed by read operations.
     */
    public synchronized boolean allReadDataConsumed() {
        return readBuffer.position() == readBuffer.limit();
    }

    /**
     * Enables and disables read operations. If read operations are disabled, read will always return 0, even if
     * there is {@link #setReadData(String...) read data available} in the local buffer.
     * <p>
     * Read operations are disabled by default.
     * 
     * @param enable {@code false} for disabling reads, {@code true} for enabling.
     */
    public void enableReads(boolean enable) {
        final Thread waiter;
        synchronized (this) {
            readsEnabled = enable;
            if (readWaiter == null || !readsEnabled || !((readBuffer.hasRemaining() && readBuffer.limit() != readBuffer.capacity()) || eof)) {
                return;
            }
            waiter = readWaiter;
        }
        LockSupport.unpark(waiter);
    }

    /**
     * Enables and disables write operations. If write operations are disabled, write will always return 0.
     * <p>
     * Write operations are enabled by default.
     * 
     * @param enable {@code false} for disabling writes, {@code true} for enabling.
     */
    public void enableWrites(boolean enable) {
        final Thread waiter;
        synchronized (this) {
            writesEnabled = enable;
            waiter = writeWaiter;
        }
        if (waiter != null) {
            LockSupport.unpark(waiter);
        }
    }

    /**
     * Enables check for closed. This will result in a ClosedChannelException is an attempt to execute an operation
     * is performed when this conduit is closed. If closed check is disabled, any operation can be performed on this
     * mock regardless of whether it is closed.
     * <p>
     * This check is enabled by default.
     * 
     * @param enable {@code true} for enabling the closed check, {@code false} for disabling it
     */
    public synchronized void enableClosedCheck(boolean enable) {
        checkClosed = enable;
    }

    /**
     * Returns all the bytes that have been written to this conduit mock.
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

    /**
     * Returns the written bytes buffer
     * 
     * @return the buffer containing all the bytes that have been written to this conduit mock
     */
    public ByteBuffer getWrittenBytes() {
        return writeBuffer;
    }

    /**
     * Indicates if all data written to this conduit has been flushed.
     * @return
     */
    public boolean isFlushed() {
        return flushed;
    }

    /**
     * Enables and disables flush. If flush is disabled, requests to flush data are ignored.
     * <p>
     * Flush is enabled by default.
     * 
     * @param enable {@code true} for enabling flush, {@code false} for disabling
     */
    public synchronized void enableFlush(boolean enable) {
        flushEnabled = enable;
    }

    /**
     * Changes the worker associated with this conduit mock.
     */
    public void setWorker(XnioWorker worker) {
        this.worker = worker;
    }


    @Override
    public OptionMap getOptionMap() {
        return optionMap;
    }

    @Override
    public String getInfo() {
        return info;
    }

    @Override
    public void setInfo(String i) {
        info = i;
    }

    public boolean isOpen() {
        return !writesTerminated || !readsTerminated; 
    }

    private OptionMap optionMap; 
// review this
//    @Override
//    public boolean supportsOption(Option<?> option) {
//        return optionMap == null? false: optionMap.contains(option);
//    }
//
//    @Override
//    public <T> T getOption(Option<T> option) throws IOException {
//        return optionMap == null? null: optionMap.get(option);
//    }
//
//    @Override
//    public <T> T setOption(Option<T> option, T value) throws IllegalArgumentException, IOException {
//        final OptionMap.Builder optionMapBuilder = OptionMap.builder();
//        T previousValue = null;
//        if (optionMap != null) {
//            optionMapBuilder.addAll(optionMap);
//            previousValue = optionMap.get(option);
//        }
//        optionMapBuilder.set(option, value);
//        optionMap = optionMapBuilder.getMap();
//        return previousValue;
//    }

    public void setOptionMap(OptionMap optionMap) {
        this.optionMap = optionMap;
    }

    @Override
    public void suspendReads() {
        readsAwaken = false;
        readsResumed = false;
    }

    @Override
    public void resumeReads() {
        readsResumed = true;
    }

    @Override
    public boolean isReadResumed() {
        return readsResumed;
    }

    @Override
    public void terminateReads() throws IOException {
        readsTerminated = true;
        return;
    }

    @Override
    public synchronized boolean isReadShutdown() {
        return readsTerminated;
    }

    /**
     * This mock does not support more than one read thread waiter at the same time.
     */
    @Override
    public void awaitReadable() throws IOException {
        synchronized(this) {
            if (readWaiter != null) {
                throw new IllegalStateException("ConnectedStreamChannelMock can be used only with one read waiter thread at most... there is already a  waiting thread" + readWaiter);
            }
            if (((readBuffer.hasRemaining() && readBuffer.capacity() != readBuffer.limit()) || eof) && readsEnabled) {
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
     * This mock does not support more than one read thread waiter at the same time.
     */
    @Override
    public void awaitReadable(long time, TimeUnit timeUnit) throws IOException {
        synchronized (this) {
            if (readWaiter != null) {
                throw new IllegalStateException("ConnectedStreamChannelMock can be used only with one read waiter thread at most... there is already a  waiting thread" + readWaiter);
            }
            if (((readBuffer.hasRemaining() && readBuffer.capacity() != readBuffer.limit()) || eof) && readsEnabled) {
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
    public void suspendWrites() {
        writesAwaken = false;
        writesResumed = false;
    }

    @Override
    public void resumeWrites() {
        writesResumed = true;
    }

    @Override
    public boolean isWriteResumed() {
        return writesResumed;
    }

    @Override
    public synchronized void terminateWrites() throws IOException {
        if (!allowTerminateWrites) {
            return;
        }
        writesTerminated = true;
        final Thread waiter;
        synchronized (this) {
            if (readWaiter == null) {
                return;
            }
            waiter = readWaiter;
        }
        LockSupport.unpark(waiter);
        return;
    }

    @Override
    public synchronized void truncateWrites() throws IOException {
        terminateWrites();
        writesTruncated = true;
    }

    @Override
    public synchronized boolean isWriteShutdown() {
        return writesTerminated;
    }

    public synchronized boolean isWriteTruncated() {
        return writesTruncated;
    }

    /**
     * This mock does not support more than one read thread waiter at the same time.
     */
    @Override
    public void awaitWritable() throws IOException {
        synchronized(this) {
            if (writeWaiter != null) {
                throw new IllegalStateException("ConnectedStreamChannelMock can be used only with one write waiter thread at most... there is already a  waiting thread" + writeWaiter);
            }
            if (writesEnabled) {
                return;
            }
            writeWaiter = Thread.currentThread();
        }
        LockSupport.park(writeWaiter);
        synchronized(this) {
            writeWaiter = null;
        }
    }

    /**
     * This mock does not support more than one write thread waiter at the same time.
     */
    @Override
    public void awaitWritable(long time, TimeUnit timeUnit) throws IOException {
        synchronized (this) {
            if (writeWaiter != null) {
                throw new IllegalStateException("ConnectedStreamChannelMock can be used only with one write waiter thread at most... there is already a  waiting thread" + writeWaiter);
            }
            if (writesEnabled) {
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
    public XnioIoThread getWriteThread() {
        return executor;
    }

    @Override
    public synchronized boolean flush() throws IOException {
        if (flushEnabled) {
            flushed = true;
        }
        return flushed;
    }

    @Override
    public long transferFrom(FileChannel src, long position, long count) throws IOException {
        if (writesEnabled) {
            final StreamConnection connection = new StreamConnectionMock(this);
            final AssembledConnectedStreamChannel assembledChannel = new AssembledConnectedStreamChannel(connection, connection.getSourceChannel(), connection.getSinkChannel());
            return src.transferTo(position, count, assembledChannel);
        }
        return 0;
    }

    @Override
    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        if (writesEnabled) {
            final StreamConnection connection = new StreamConnectionMock(this);
            final AssembledConnectedStreamChannel assembledChannel = new AssembledConnectedStreamChannel(connection, connection.getSourceChannel(), connection.getSinkChannel());
            IoUtils.transfer(source, count, throughBuffer, assembledChannel);
        }
        return 0;
    }

    @Override
    public synchronized int write(ByteBuffer src) throws IOException {
        if (closed && checkClosed) {
            throw new ClosedChannelException();
        }
        if (writesEnabled) {
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
        if (writesEnabled) {
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
    public int writeFinal(ByteBuffer src) throws IOException {
        return Conduits.writeFinalBasic(this, src);
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs, int offset, int length) throws IOException {
        return Conduits.writeFinalBasic(this, srcs, offset, length);
    }

    @Override
    public long transferTo(long position, long count, FileChannel target) throws IOException {
        if (readsEnabled) {
            final StreamConnection connection = new StreamConnectionMock(this);
            final AssembledConnectedStreamChannel assembledChannel = new AssembledConnectedStreamChannel(connection, connection.getSourceChannel(), connection.getSinkChannel());
            return target.transferFrom(assembledChannel, position, count);
        }
        return 0;
    }

    @Override
    public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
        if (readsEnabled) {
            final StreamConnection connection = new StreamConnectionMock(this);
            final AssembledConnectedStreamChannel assembledChannel = new AssembledConnectedStreamChannel(connection, connection.getSourceChannel(), connection.getSinkChannel());
            return IoUtils.transfer(assembledChannel, count, throughBuffer, target);
        }
        return 0;
    }

    @Override
    public synchronized int read(ByteBuffer dst) throws IOException {
        if (closed && checkClosed) {
            throw new ClosedChannelException();
        }
        if (readsEnabled) {
            try {
                if ((!readBuffer.hasRemaining() || readBuffer.position() == 0 && readBuffer.limit() == readBuffer.capacity()) && eof) {
                    return -1;
                }
                if (readBuffer.limit() == readBuffer.capacity() && readBuffer.position() == 0) {
                    return 0;
                }
                return Buffers.copy(dst, readBuffer);
            } catch (RuntimeException e) {
                System.out.println("Got exception at attempt of copying contents of dst "+ dst.remaining()  +  " into read buffer " + readBuffer.remaining());
                throw e;
            }
        }
        return 0;
    }

    @Override
    public synchronized long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        if (closed && checkClosed) {
            throw new ClosedChannelException();
        }
        if (readsEnabled) {
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

    @Override
    public XnioWorker getWorker() {
        return worker;
    }

    @Override
    public void wakeupReads() {
        readsAwaken = true;
        readsResumed = true;
    }

    public boolean isReadAwaken() {
        return readsAwaken;
    }

    @Override
    public void wakeupWrites() {
        writesAwaken = true;
        writesResumed = true;
    }

    public boolean isWriteAwaken() {
        return writesAwaken;
    }

    // implement this when needed
//    @Override
//    public Setter<? extends StreamConnection> getReadSetter() {
//        return readListenerSetter;
//    }
//
//    @Override
//    public Setter<? extends ConnectedStreamChannel> getWriteSetter() {
//        return writeListenerSetter;
//    }
//
//    @Override
//    public Setter<? extends ConnectedStreamChannel> getCloseSetter() {
//        return closeListenerSetter;
//    }

//    public ChannelListener<? super ConnectedStreamChannel> getReadListener() {
//        return readListener;
//    }
//
//    public ChannelListener<? super ConnectedStreamChannel> getWriteListener() {
//        return writeListener;
//    }
//
//    public ChannelListener<? super ConnectedStreamChannel> getCloseListener() {
//        return closeListener;
//    }

    @Override // make ready handler active when needed
    public synchronized void setWriteReadyHandler(WriteReadyHandler handler) {
        writeReadyHandler = handler;
    }

    @Override // make ready handler active when needed
    public void setReadReadyHandler(ReadReadyHandler handler) {
        readReadyHandler = handler;
    }

    @Override
    public XnioIoThread getReadThread() {
        return executor;
    }
}
