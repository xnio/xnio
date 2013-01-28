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
 * A half-duplex (read side) wrapper for a full-duplex channel.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class SplitStreamSourceChannel implements StreamSourceChannel, ReadListenerSettable<SplitStreamSourceChannel>, CloseListenerSettable<SplitStreamSourceChannel> {
    private final StreamSourceChannel delegate;

    private volatile int state;

    private ChannelListener<? super SplitStreamSourceChannel> readListener;
    private ChannelListener<? super SplitStreamSourceChannel> closeListener;

    private static final int FLAG_DELEGATE_CONFIG = 1 << 0;
    private static final int FLAG_CLOSED = 1 << 1;

    private static final AtomicIntegerFieldUpdater<SplitStreamSourceChannel> stateUpdater = AtomicIntegerFieldUpdater.newUpdater(SplitStreamSourceChannel.class, "state");

    /**
     * Construct a new instance.
     *
     * @param delegate the delegate channel
     * @param delegateConfig {@code true} to delegate configuration, {@code false} otherwise
     */
    public SplitStreamSourceChannel(final StreamSourceChannel delegate, boolean delegateConfig) {
        this.delegate = delegate;
        delegate.getReadSetter().set(new ChannelListener<StreamSourceChannel>() {
            public void handleEvent(final StreamSourceChannel channel) {
                invokeChannelListener(SplitStreamSourceChannel.this, readListener);
            }
        });
        state = delegateConfig ? FLAG_DELEGATE_CONFIG : 0;
    }

    /**
     * Construct a new instance which doesn't delegate configuration.
     *
     * @param delegate the delegate channel
     */
    public SplitStreamSourceChannel(final StreamSourceChannel delegate) {
        this(delegate, false);
    }

    public void setReadListener(final ChannelListener<? super SplitStreamSourceChannel> readListener) {
        this.readListener = readListener;
    }

    public ChannelListener<? super SplitStreamSourceChannel> getReadListener() {
        return readListener;
    }

    public void setCloseListener(final ChannelListener<? super SplitStreamSourceChannel> closeListener) {
        this.closeListener = closeListener;
    }

    public ChannelListener<? super SplitStreamSourceChannel> getCloseListener() {
        return closeListener;
    }

    public ChannelListener.Setter<? extends SplitStreamSourceChannel> getReadSetter() {
        return new ReadListenerSettable.Setter<SplitStreamSourceChannel>(this);
    }

    public ChannelListener.Setter<? extends SplitStreamSourceChannel> getCloseSetter() {
        return new CloseListenerSettable.Setter<SplitStreamSourceChannel>(this);
    }

    public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        return delegate.transferTo(position, count, target);
    }

    public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
        return delegate.transferTo(count, throughBuffer, target);
    }

    public int read(final ByteBuffer dst) throws IOException {
        return delegate.read(dst);
    }

    public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        return delegate.read(dsts, offset, length);
    }

    public long read(final ByteBuffer[] dsts) throws IOException {
        return delegate.read(dsts);
    }

    public void suspendReads() {
        delegate.suspendReads();
    }

    public void resumeReads() {
        delegate.resumeReads();
    }

    public void wakeupReads() {
        delegate.wakeupReads();
    }

    public boolean isReadResumed() {
        return delegate.isReadResumed();
    }

    public void awaitReadable() throws IOException {
        delegate.awaitReadable();
    }

    public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
        delegate.awaitReadable(time, timeUnit);
    }

    public XnioWorker getWorker() {
        return delegate.getWorker();
    }

    public boolean supportsOption(final Option<?> option) {
        return allAreSet(state, FLAG_DELEGATE_CONFIG) ? delegate.supportsOption(option) : false;
    }

    public <T> T getOption(final Option<T> option) throws IOException {
        return allAreSet(state, FLAG_DELEGATE_CONFIG) ? delegate.getOption(option) : null;
    }

    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        return allAreSet(state, FLAG_DELEGATE_CONFIG) ? delegate.setOption(option, value) : null;
    }

    public void shutdownReads() throws IOException {
        int oldVal, newVal;
        do {
            oldVal = state;
            if (allAreSet(oldVal, FLAG_CLOSED)) {
                return;
            }
            newVal = oldVal | FLAG_CLOSED;
        } while (! stateUpdater.compareAndSet(this, oldVal, newVal));
        delegate.shutdownReads();
        invokeChannelListener(this, closeListener);
    }

    @Deprecated
    public XnioExecutor getReadThread() {
        return delegate.getReadThread();
    }

    public XnioIoThread getIoThread() {
        return delegate.getIoThread();
    }

    public boolean isOpen() {
        return allAreClear(state, FLAG_CLOSED);
    }

    public void close() throws IOException {
        shutdownReads();
    }
}
