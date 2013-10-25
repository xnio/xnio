/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2012 Red Hat, Inc. and/or its affiliates.
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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import org.xnio.ChannelListener;
import org.xnio.Option;
import org.xnio.XnioExecutor;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;

import static org.xnio.Bits.*;
import static org.xnio.ChannelListeners.invokeChannelListener;

/**
 * A half-duplex (write side) wrapper for a full-duplex channel.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class SplitStreamSinkChannel implements StreamSinkChannel, WriteListenerSettable<SplitStreamSinkChannel>, CloseListenerSettable<SplitStreamSinkChannel> {
    private final StreamSinkChannel delegate;

    private volatile int state;

    private ChannelListener<? super SplitStreamSinkChannel> writeListener;
    private ChannelListener<? super SplitStreamSinkChannel> closeListener;

    private static final int FLAG_DELEGATE_CONFIG = 1 << 0;
    private static final int FLAG_CLOSE_REQ = 1 << 1;
    private static final int FLAG_CLOSE_COMP = 1 << 2;

    private static final AtomicIntegerFieldUpdater<SplitStreamSinkChannel> stateUpdater = AtomicIntegerFieldUpdater.newUpdater(SplitStreamSinkChannel.class, "state");

    /**
     * Construct a new instance.
     *
     * @param delegate the delegate channel
     * @param delegateConfig {@code true} to delegate configuration, {@code false} otherwise
     */
    public SplitStreamSinkChannel(final StreamSinkChannel delegate, boolean delegateConfig) {
        this.delegate = delegate;
        state = delegateConfig ? FLAG_DELEGATE_CONFIG : 0;
        delegate.getWriteSetter().set(new ChannelListener<StreamSinkChannel>() {
            public void handleEvent(final StreamSinkChannel channel) {
                invokeChannelListener(SplitStreamSinkChannel.this, writeListener);
            }
        });
    }

    /**
     * Construct a new instance which does not delegate configuration.
     *
     * @param delegate the delegate channel
     */
    public SplitStreamSinkChannel(final StreamSinkChannel delegate) {
        this(delegate, false);
    }

    public void setWriteListener(final ChannelListener<? super SplitStreamSinkChannel> writeListener) {
        this.writeListener = writeListener;
    }

    public ChannelListener<? super SplitStreamSinkChannel> getWriteListener() {
        return writeListener;
    }

    public void setCloseListener(final ChannelListener<? super SplitStreamSinkChannel> closeListener) {
        this.closeListener = closeListener;
    }

    public ChannelListener<? super SplitStreamSinkChannel> getCloseListener() {
        return closeListener;
    }

    public ChannelListener.Setter<? extends SplitStreamSinkChannel> getCloseSetter() {
        return new CloseListenerSettable.Setter<SplitStreamSinkChannel>(this);
    }

    @Override
    public int writeFinal(ByteBuffer src) throws IOException {
        return delegate.writeFinal(src);
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs, int offset, int length) throws IOException {
        return delegate.writeFinal(srcs, offset, length);
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs) throws IOException {
        return delegate.writeFinal(srcs);
    }

    public ChannelListener.Setter<? extends SplitStreamSinkChannel> getWriteSetter() {
        return new WriteListenerSettable.Setter<SplitStreamSinkChannel>(this);
    }

    public void shutdownWrites() throws IOException {
        int oldVal, newVal;
        do {
            oldVal = state;
            if (allAreSet(oldVal, FLAG_CLOSE_REQ)) {
                return;
            }
            newVal = oldVal | FLAG_CLOSE_REQ;
        } while (! stateUpdater.compareAndSet(this, oldVal, newVal));
        delegate.shutdownWrites();
    }

    public boolean isOpen() {
        return allAreClear(state, FLAG_CLOSE_REQ);
    }

    public void close() throws IOException {
        shutdownWrites();
        // best lame-duck effort; can't do any more than this really
        flush();
    }

    public boolean flush() throws IOException {
        int oldVal, newVal;
        oldVal = state;
        if (allAreSet(oldVal, FLAG_CLOSE_COMP)) {
            return true;
        }
        final boolean flushed = delegate.flush();
        if (! flushed) {
            return false;
        }
        do {
            if (allAreClear(oldVal, FLAG_CLOSE_REQ)) {
                return true;
            }
            newVal = oldVal | FLAG_CLOSE_COMP;
        } while (! stateUpdater.compareAndSet(this, oldVal, newVal));
        invokeChannelListener(this, closeListener);
        return true;
    }

    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        return delegate.transferFrom(src, position, count);
    }

    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        return delegate.transferFrom(source, count, throughBuffer);
    }

    public int write(final ByteBuffer src) throws IOException {
        return delegate.write(src);
    }

    public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        return delegate.write(srcs, offset, length);
    }

    public long write(final ByteBuffer[] srcs) throws IOException {
        return delegate.write(srcs);
    }

    public void suspendWrites() {
        delegate.suspendWrites();
    }

    public void resumeWrites() {
        delegate.resumeWrites();
    }

    public boolean isWriteResumed() {
        return delegate.isWriteResumed();
    }

    public void wakeupWrites() {
        delegate.wakeupWrites();
    }

    public void awaitWritable() throws IOException {
        delegate.awaitWritable();
    }

    public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
        delegate.awaitWritable(time, timeUnit);
    }

    @Deprecated
    public XnioExecutor getWriteThread() {
        return delegate.getWriteThread();
    }

    public XnioIoThread getIoThread() {
        return delegate.getIoThread();
    }

    public XnioWorker getWorker() {
        return delegate.getWorker();
    }

    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        return allAreSet(state, FLAG_DELEGATE_CONFIG) ? delegate.setOption(option, value) : null;
    }

    public <T> T getOption(final Option<T> option) throws IOException {
        return allAreSet(state, FLAG_DELEGATE_CONFIG) ? delegate.getOption(option) : null;
    }

    public boolean supportsOption(final Option<?> option) {
        return allAreSet(state, FLAG_DELEGATE_CONFIG) ? delegate.supportsOption(option) : false;
    }
}
