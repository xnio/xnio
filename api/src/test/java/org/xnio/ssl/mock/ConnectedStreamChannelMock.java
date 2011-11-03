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
import java.util.concurrent.TimeUnit;

import org.xnio.Buffers;
import org.xnio.ChannelListener;
import org.xnio.ChannelListener.Setter;
import org.xnio.Option;
import org.xnio.XnioWorker;
import org.xnio.channels.ConnectedStreamChannel;

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
    // indicates if this channel is closed
    private boolean closed = false;
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

    /**
     * Returns all the bytes that have been written to this channel mock.
     * 
     * @return the written bytes in the form of a UTF-8 string
     */
    public String getWrittenText() {
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

    @Override
    public boolean supportsOption(Option<?> option) {
        System.out.println("Calling SUPPORTS OPTION");
        return false;
    }

    @Override
    public <T> T getOption(Option<T> option) throws IOException {
        System.out.println("Calling GET OPTION");
        return null;
    }

    @Override
    public <T> T setOption(Option<T> option, T value) throws IllegalArgumentException, IOException {
        System.out.println("Calling SET OPTION");
        return null;
    }

    @Override
    public void suspendReads() {
        // do nothing for now...
    }

    @Override
    public void resumeReads() {
        //do nothing for now...
    }

    @Override
    public void shutdownReads() throws IOException {
        // do nothing for now...
    }

    @Override
    public void awaitReadable() throws IOException {
        // do nothing for now...
    }

    @Override
    public void awaitReadable(long time, TimeUnit timeUnit) throws IOException {
        // do nothing for now...
    }

    @Override
    public void suspendWrites() {
        // do nothing for now...
    }

    @Override
    public void resumeWrites() {
        // do nothing for now...
    }

    @Override
    public boolean shutdownWrites() throws IOException {
        return true;
    }

    @Override
    public void awaitWritable() throws IOException {
        // do nothing for now...
    }

    @Override
    public void awaitWritable(long time, TimeUnit timeUnit) throws IOException {
        // do nothing for now...
    }

    @Override
    public boolean flush() throws IOException {
        // return always true for now...
        return true;
    }

    @Override
    public long transferFrom(FileChannel src, long position, long count) throws IOException {
        // do nothing for now...
        return 0;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        if (closed) {
            throw new ClosedChannelException();
        }
        return Buffers.copy(writeBuffer, src);
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        if (closed) {
            throw new ClosedChannelException();
        }
        return Buffers.copy(writeBuffer, srcs, offset, length);
    }

    @Override
    public long write(ByteBuffer[] srcs) throws IOException {
        if (closed) {
            throw new ClosedChannelException();
        }
        return Buffers.copy(writeBuffer, srcs, 0, srcs.length);
    }

    @Override
    public long transferTo(long position, long count, FileChannel target) throws IOException {
        // do nothing for now...
        return 0;
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

    @Override
    public SocketAddress getPeerAddress() {
        // do nothing for now...
        return null;
    }

    @Override
    public <A extends SocketAddress> A getPeerAddress(Class<A> type) {
        // do nothing for now...
        return null;
    }

    @Override
    public SocketAddress getLocalAddress() {
        // do nothing for now...
        return null;
    }

    @Override
    public <A extends SocketAddress> A getLocalAddress(Class<A> type) {
        // do nothing for now...
        return null;
    }

    @Override
    public XnioWorker getWorker() {
        // do nothing for now...
        return null;
    }

    @Override
    public void wakeupReads() {
        // do nothing for now...
    }

    @Override
    public void wakeupWrites() {
        // do nothing for now...
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
}