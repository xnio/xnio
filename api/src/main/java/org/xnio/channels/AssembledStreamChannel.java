/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2012 Red Hat, Inc. and/or its affiliates, and individual
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

package org.xnio.channels;

import static org.xnio._private.Messages.msg;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.Option;
import org.xnio.XnioExecutor;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;

/**
 * A stream channel assembled from a stream source and stream sink.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public class AssembledStreamChannel implements StreamChannel {
    private final CloseableChannel closeable;
    private final StreamSourceChannel source;
    private final StreamSinkChannel sink;

    private final ChannelListener.Setter<AssembledStreamChannel> readSetter;
    private final ChannelListener.Setter<AssembledStreamChannel> writeSetter;
    private final ChannelListener.Setter<AssembledStreamChannel> closeSetter;

    /**
     * Construct a new instance.
     *
     * @param closeable the single central closeable channel
     * @param source the stream source channel
     * @param sink the stream sink channel
     */
    public AssembledStreamChannel(final CloseableChannel closeable, final StreamSourceChannel source, final StreamSinkChannel sink) {
        if (source.getWorker() != sink.getWorker() || source.getWorker() != closeable.getWorker()) {
            throw msg.differentWorkers();
        }
        this.closeable = closeable;
        this.source = source;
        this.sink = sink;
        readSetter = ChannelListeners.getDelegatingSetter(source.getReadSetter(), this);
        writeSetter = ChannelListeners.getDelegatingSetter(sink.getWriteSetter(), this);
        closeSetter = ChannelListeners.getDelegatingSetter(closeable.getCloseSetter(), this);
    }

    /**
     * Construct a new instance.
     *
     * @param source the stream source channel
     * @param sink the stream sink channel
     */
    public AssembledStreamChannel(final StreamSourceChannel source, final StreamSinkChannel sink) {
        this(new AssembledChannel(source, sink), source, sink);
    }

    // Read side

    public ChannelListener.Setter<? extends AssembledStreamChannel> getReadSetter() {
        return readSetter;
    }

    public void suspendReads() {
        source.suspendReads();
    }

    public void resumeReads() {
        source.resumeReads();
    }

    public boolean isReadResumed() {
        return source.isReadResumed();
    }

    public void wakeupReads() {
        source.wakeupReads();
    }

    public void shutdownReads() throws IOException {
        source.shutdownReads();
    }

    public void awaitReadable() throws IOException {
        source.awaitReadable();
    }

    public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
        source.awaitReadable(time, timeUnit);
    }

    @Deprecated
    public XnioExecutor getReadThread() {
        return source.getReadThread();
    }

    public XnioIoThread getIoThread() {
        return source.getIoThread();
    }

    public int read(final ByteBuffer dst) throws IOException {
        return source.read(dst);
    }

    public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        return source.read(dsts, offset, length);
    }

    public long read(final ByteBuffer[] dsts) throws IOException {
        return source.read(dsts);
    }

    public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        return source.transferTo(position, count, target);
    }

    public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
        return source.transferTo(count, throughBuffer, target);
    }

    // Write side

    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        return sink.transferFrom(src, position, count);
    }

    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        return sink.transferFrom(source, count, throughBuffer);
    }

    public ChannelListener.Setter<? extends AssembledStreamChannel> getWriteSetter() {
        return writeSetter;
    }

    public int write(final ByteBuffer src) throws IOException {
        return sink.write(src);
    }

    public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        return sink.write(srcs, offset, length);
    }

    public long write(final ByteBuffer[] srcs) throws IOException {
        return sink.write(srcs);
    }

    public void suspendWrites() {
        sink.suspendWrites();
    }

    public void resumeWrites() {
        sink.resumeWrites();
    }

    public boolean isWriteResumed() {
        return sink.isWriteResumed();
    }

    public void wakeupWrites() {
        sink.wakeupWrites();
    }

    public void shutdownWrites() throws IOException {
        sink.shutdownWrites();
    }

    public void awaitWritable() throws IOException {
        sink.awaitWritable();
    }

    public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
        sink.awaitWritable(time, timeUnit);
    }

    @Deprecated
    public XnioExecutor getWriteThread() {
        return sink.getWriteThread();
    }

    public boolean flush() throws IOException {
        return sink.flush();
    }

    // Single side

    public ChannelListener.Setter<? extends AssembledStreamChannel> getCloseSetter() {
        return closeSetter;
    }

    @Override
    public int writeFinal(ByteBuffer src) throws IOException {
        return sink.writeFinal(src);
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs, int offset, int length) throws IOException {
        return sink.writeFinal(srcs, offset, length);
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs) throws IOException {
        return sink.writeFinal(srcs);
    }

    public XnioWorker getWorker() {
        return closeable.getWorker();
    }

    public void close() throws IOException {
        closeable.close();
    }

    public boolean isOpen() {
        return closeable.isOpen();
    }

    public boolean supportsOption(final Option<?> option) {
        return closeable.supportsOption(option);
    }

    public <T> T getOption(final Option<T> option) throws IOException {
        return closeable.getOption(option);
    }

    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        return closeable.setOption(option, value);
    }
}
