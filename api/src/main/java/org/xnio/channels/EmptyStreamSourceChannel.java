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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.Option;
import org.xnio.XnioExecutor;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;

import static org.xnio.Bits.allAreClear;
import static org.xnio.Bits.allAreSet;
import static org.xnio.Bits.anyAreSet;

/**
 * A stream source channel which is always empty.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public class EmptyStreamSourceChannel implements StreamSourceChannel, ReadListenerSettable<EmptyStreamSourceChannel>, CloseListenerSettable<EmptyStreamSourceChannel> {
    private final XnioIoThread thread;
    private final Runnable readRunnable = new Runnable() {
        public void run() {
            ChannelListener<? super EmptyStreamSourceChannel> listener = readListener;
            if (listener == null) {
                suspendReads();
                return;
            }
            ChannelListeners.invokeChannelListener(EmptyStreamSourceChannel.this, listener);
            final int oldVal = state;
            if (allAreSet(oldVal, RESUMED) && allAreClear(oldVal, EMPTIED | CLOSED)) {
                thread.execute(this);
            }
        }
    };

    @SuppressWarnings("unused")
    private volatile int state;

    private ChannelListener<? super EmptyStreamSourceChannel> readListener;
    private ChannelListener<? super EmptyStreamSourceChannel> closeListener;

    private static final int CLOSED = 1 << 0;
    private static final int EMPTIED = 1 << 1;
    private static final int RESUMED = 1 << 2;
    private static final AtomicIntegerFieldUpdater<EmptyStreamSourceChannel> stateUpdater = AtomicIntegerFieldUpdater.newUpdater(EmptyStreamSourceChannel.class, "state");

    /**
     * Construct a new instance.
     *
     * @param thread the XNIO read thread to use
     */
    public EmptyStreamSourceChannel(final XnioIoThread thread) {
        this.thread = thread;
    }

    public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        return 0;
    }

    public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
        throughBuffer.limit(0);
        emptied();
        return -1;
    }

    public void setReadListener(final ChannelListener<? super EmptyStreamSourceChannel> readListener) {
        this.readListener = readListener;
    }

    public ChannelListener<? super EmptyStreamSourceChannel> getReadListener() {
        return readListener;
    }

    public void setCloseListener(final ChannelListener<? super EmptyStreamSourceChannel> closeListener) {
        this.closeListener = closeListener;
    }

    public ChannelListener<? super EmptyStreamSourceChannel> getCloseListener() {
        return closeListener;
    }

    public ChannelListener.Setter<? extends EmptyStreamSourceChannel> getReadSetter() {
        return new ReadListenerSettable.Setter<EmptyStreamSourceChannel>(this);
    }

    public ChannelListener.Setter<? extends EmptyStreamSourceChannel> getCloseSetter() {
        return new CloseListenerSettable.Setter<EmptyStreamSourceChannel>(this);
    }

    private void emptied() {
        int oldVal, newVal;
        do {
            oldVal = state;
            if (allAreSet(oldVal, EMPTIED)) {
                return;
            }
            newVal = oldVal | EMPTIED;
        } while (! stateUpdater.compareAndSet(this, oldVal, newVal));
    }

    public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        emptied();
        return -1;
    }

    public long read(final ByteBuffer[] dsts) throws IOException {
        emptied();
        return -1;
    }

    public int read(final ByteBuffer dst) throws IOException {
        emptied();
        return -1;
    }

    public void suspendReads() {
        int oldVal, newVal;
        do {
            oldVal = state;
            if (allAreClear(oldVal, RESUMED)) {
                return;
            }
            newVal = oldVal & ~RESUMED;
        } while (! stateUpdater.compareAndSet(this, oldVal, newVal));
    }

    public void resumeReads() {
        int oldVal, newVal;
        do {
            oldVal = state;
            if (anyAreSet(oldVal, RESUMED | CLOSED)) {
                return;
            }
            newVal = RESUMED;
        } while (! stateUpdater.compareAndSet(this, oldVal, newVal));
        if (allAreClear(oldVal, EMPTIED)) {
            thread.execute(readRunnable);
        }
    }

    public boolean isReadResumed() {
        return allAreSet(state, RESUMED);
    }

    public void wakeupReads() {
        int oldVal, newVal;
        do {
            oldVal = state;
            if (anyAreSet(oldVal, CLOSED)) {
                return;
            }
            newVal = RESUMED;
        } while (! stateUpdater.compareAndSet(this, oldVal, newVal));
        thread.execute(readRunnable);
    }

    public void shutdownReads() throws IOException {
        final int oldVal = stateUpdater.getAndSet(this, EMPTIED | CLOSED);
        if (allAreClear(oldVal, CLOSED)) {
            thread.execute(ChannelListeners.getChannelListenerTask(this, closeListener));
        }
    }

    public void awaitReadable() throws IOException {
        // return immediately
    }

    public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
        // return immediately
    }

    @Deprecated
    public XnioExecutor getReadThread() {
        return thread;
    }

    public XnioIoThread getIoThread() {
        return thread;
    }

    public XnioWorker getWorker() {
        return thread.getWorker();
    }

    public boolean isOpen() {
        return allAreClear(state, CLOSED);
    }

    public void close() throws IOException {
        final int oldVal = stateUpdater.getAndSet(this, EMPTIED | CLOSED);
        if (allAreClear(oldVal, CLOSED)) {
            thread.execute(ChannelListeners.getChannelListenerTask(this, closeListener));
        }
    }

    public boolean supportsOption(final Option<?> option) {
        return false;
    }

    public <T> T getOption(final Option<T> option) throws IOException {
        return null;
    }

    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        return null;
    }
}
