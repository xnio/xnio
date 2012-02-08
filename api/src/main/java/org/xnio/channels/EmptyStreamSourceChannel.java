/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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
import org.xnio.XnioWorker;

import static org.xnio.Bits.allAreClear;
import static org.xnio.Bits.allAreSet;
import static org.xnio.Bits.anyAreSet;

/**
 * A stream source channel which is always empty.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class EmptyStreamSourceChannel implements StreamSourceChannel {
    private final XnioWorker worker;
    private final XnioExecutor executor;
    private final ChannelListener.SimpleSetter<EmptyStreamSourceChannel> readSetter = new ChannelListener.SimpleSetter<EmptyStreamSourceChannel>();
    private final ChannelListener.SimpleSetter<EmptyStreamSourceChannel> closeSetter = new ChannelListener.SimpleSetter<EmptyStreamSourceChannel>();
    private final Runnable readRunnable = new Runnable() {
        public void run() {
            ChannelListeners.invokeChannelListener(EmptyStreamSourceChannel.this, readSetter.get());
            final int oldVal = state;
            if (allAreSet(oldVal, RESUMED) && allAreClear(oldVal, EMPTIED | CLOSED)) {
                executor.execute(this);
            }
        }
    };

    @SuppressWarnings("unused")
    private volatile int state;

    private static final int CLOSED = 1 << 0;
    private static final int EMPTIED = 1 << 1;
    private static final int RESUMED = 1 << 2;
    private static final AtomicIntegerFieldUpdater<EmptyStreamSourceChannel> stateUpdater = AtomicIntegerFieldUpdater.newUpdater(EmptyStreamSourceChannel.class, "state");

    /**
     * Construct a new instance.
     *
     * @param worker the XNIO worker to use
     * @param executor the XNIO read thread to use
     */
    public EmptyStreamSourceChannel(final XnioWorker worker, final XnioExecutor executor) {
        this.worker = worker;
        this.executor = executor;
    }

    public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        return 0;
    }

    public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
        emptied();
        return -1;
    }

    public ChannelListener.Setter<? extends StreamSourceChannel> getReadSetter() {
        return readSetter;
    }

    public ChannelListener.Setter<? extends StreamSourceChannel> getCloseSetter() {
        return closeSetter;
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
            wakeupReads();
        }
    }

    public boolean isReadResumed() {
        return allAreSet(state, RESUMED);
    }

    public void wakeupReads() {
        executor.execute(readRunnable);
    }

    public void shutdownReads() throws IOException {
        final int oldVal = stateUpdater.getAndSet(this, EMPTIED | CLOSED);
        if (allAreClear(oldVal, CLOSED)) {
            executor.execute(ChannelListeners.getChannelListenerTask(this, closeSetter.get()));
        }
    }

    public void awaitReadable() throws IOException {
        // return immediately
    }

    public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
        // return immediately
    }

    public XnioExecutor getReadThread() {
        return executor;
    }

    public XnioWorker getWorker() {
        return worker;
    }

    public boolean isOpen() {
        return allAreClear(state, CLOSED);
    }

    public void close() throws IOException {
        final int oldVal = stateUpdater.getAndSet(this, EMPTIED | CLOSED);
        if (allAreClear(oldVal, CLOSED)) {
            executor.execute(ChannelListeners.getChannelListenerTask(this, closeSetter.get()));
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
