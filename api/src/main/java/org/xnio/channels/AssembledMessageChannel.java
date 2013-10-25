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
import java.util.concurrent.TimeUnit;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.Option;
import org.xnio.XnioExecutor;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;

/**
 * A bidirectional message channel assembled from a readable and writable message channel.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public class AssembledMessageChannel implements MessageChannel {
    private final CloseableChannel closeable;
    private final ReadableMessageChannel readable;
    private final WritableMessageChannel writable;

    private final ChannelListener.Setter<AssembledMessageChannel> readSetter;
    private final ChannelListener.Setter<AssembledMessageChannel> writeSetter;
    private final ChannelListener.Setter<AssembledMessageChannel> closeSetter;

    /**
     * Construct a new instance.
     *
     * @param closeable the single central closeable channel
     * @param readable the read channel
     * @param writable the write channel
     */
    public AssembledMessageChannel(final CloseableChannel closeable, final ReadableMessageChannel readable, final WritableMessageChannel writable) {
        if (readable.getWorker() != writable.getWorker() || readable.getWorker() != closeable.getWorker()) {
            throw msg.differentWorkers();
        }
        this.closeable = closeable;
        this.readable = readable;
        this.writable = writable;
        readSetter = ChannelListeners.getDelegatingSetter(readable.getReadSetter(), this);
        writeSetter = ChannelListeners.getDelegatingSetter(writable.getWriteSetter(), this);
        closeSetter = ChannelListeners.getDelegatingSetter(closeable.getCloseSetter(), this);
    }

    /**
     * Construct a new instance.
     *
     * @param readable the read channel
     * @param writable the write channel
     */
    public AssembledMessageChannel(final ReadableMessageChannel readable, final WritableMessageChannel writable) {
        this(new AssembledChannel(readable, writable), readable, writable);
    }

    public XnioIoThread getIoThread() {
        // both should be the same
        return readable.getIoThread();
    }

    // Read side

    public ChannelListener.Setter<? extends AssembledMessageChannel> getReadSetter() {
        return readSetter;
    }

    public void suspendReads() {
        readable.suspendReads();
    }

    public void resumeReads() {
        readable.resumeReads();
    }

    public boolean isReadResumed() {
        return readable.isReadResumed();
    }

    public void wakeupReads() {
        readable.wakeupReads();
    }

    public void shutdownReads() throws IOException {
        readable.shutdownReads();
    }

    public void awaitReadable() throws IOException {
        readable.awaitReadable();
    }

    public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
        readable.awaitReadable(time, timeUnit);
    }

    @Deprecated
    public XnioExecutor getReadThread() {
        return readable.getReadThread();
    }

    public int receive(final ByteBuffer buffer) throws IOException {
        return readable.receive(buffer);
    }

    public long receive(final ByteBuffer[] buffers) throws IOException {
        return readable.receive(buffers);
    }

    public long receive(final ByteBuffer[] buffers, final int offs, final int len) throws IOException {
        return readable.receive(buffers, offs, len);
    }

    // Write side

    public ChannelListener.Setter<? extends AssembledMessageChannel> getWriteSetter() {
        return writeSetter;
    }

    public void suspendWrites() {
        writable.suspendWrites();
    }

    public void resumeWrites() {
        writable.resumeWrites();
    }

    public boolean isWriteResumed() {
        return writable.isWriteResumed();
    }

    public void wakeupWrites() {
        writable.wakeupWrites();
    }

    public void shutdownWrites() throws IOException {
        writable.shutdownWrites();
    }

    public void awaitWritable() throws IOException {
        writable.awaitWritable();
    }

    public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
        writable.awaitWritable(time, timeUnit);
    }

    @Deprecated
    public XnioExecutor getWriteThread() {
        return writable.getWriteThread();
    }

    public boolean send(final ByteBuffer buffer) throws IOException {
        return writable.send(buffer);
    }

    public boolean send(final ByteBuffer[] buffers) throws IOException {
        return writable.send(buffers);
    }

    public boolean send(final ByteBuffer[] buffers, final int offs, final int len) throws IOException {
        return writable.send(buffers, offs, len);
    }

    @Override
    public boolean sendFinal(ByteBuffer buffer) throws IOException {
        return writable.sendFinal(buffer);
    }

    @Override
    public boolean sendFinal(ByteBuffer[] buffers) throws IOException {
        return writable.sendFinal(buffers);
    }

    @Override
    public boolean sendFinal(ByteBuffer[] buffers, int offs, int len) throws IOException {
        return writable.sendFinal(buffers, offs, len);
    }

    public boolean flush() throws IOException {
        return writable.flush();
    }

    // Single side

    public ChannelListener.Setter<? extends AssembledMessageChannel> getCloseSetter() {
        return closeSetter;
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
