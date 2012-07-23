/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
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
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.Option;
import org.xnio.XnioExecutor;
import org.xnio.XnioWorker;

import static org.xnio.Bits.*;

/**
 * A bit-bucket stream sink channel.  This channel type is always writable.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class NullStreamSinkChannel implements StreamSinkChannel {
    private final XnioWorker worker;
    private final XnioExecutor executor;

    @SuppressWarnings("unused")
    private volatile int state;

    private final ChannelListener.SimpleSetter<NullStreamSinkChannel> writeSetter = new ChannelListener.SimpleSetter<NullStreamSinkChannel>();
    private final ChannelListener.SimpleSetter<NullStreamSinkChannel> closeSetter = new ChannelListener.SimpleSetter<NullStreamSinkChannel>();

    private static final AtomicIntegerFieldUpdater<NullStreamSinkChannel> stateUpdater = AtomicIntegerFieldUpdater.newUpdater(NullStreamSinkChannel.class, "state");

    private static final int FLAG_ENTERED = 1 << 0;
    private static final int FLAG_CLOSED = 1 << 1;
    private static final int FLAG_RESUMED = 1 << 2;

    /**
     * Construct a new instance.
     *
     * @param worker the worker to use
     * @param executor the write thread for this channel
     */
    public NullStreamSinkChannel(final XnioWorker worker, final XnioExecutor executor) {
        this.worker = worker;
        this.executor = executor;
    }

    public XnioWorker getWorker() {
        return worker;
    }

    public XnioExecutor getWriteThread() {
        return executor;
    }

    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        int val = enterWrite();
        try {
            return Math.min(src.size() - position, count);
        } finally {
            exitWrite(val);
        }
    }

    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        int val = enterWrite();
        try {
            return Channels.drain(source, count);
        } finally {
            throughBuffer.clear();
            throughBuffer.position(0);
            exitWrite(val);
        }
    }

    public ChannelListener.Setter<? extends StreamSinkChannel> getWriteSetter() {
        return writeSetter;
    }

    public ChannelListener.Setter<? extends StreamSinkChannel> getCloseSetter() {
        return closeSetter;
    }

    public int write(final ByteBuffer src) throws IOException {
        int val = enterWrite();
        try {
            return src.remaining();
        } finally {
            src.position(src.limit());
            exitWrite(val);
        }
    }

    public long write(final ByteBuffer[] srcs) throws IOException {
        return write(srcs, 0, srcs.length);
    }

    public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        if (length == 0) {
            return 0L;
        }
        int val = enterWrite();
        try {
            long t = 0L;
            ByteBuffer src;
            for (int i = 0; i < length; i ++) {
                src = srcs[i];
                t += src.remaining();
                src.position(src.limit());
            }
            return t;
        } finally {
            exitWrite(val);
        }
    }

    public void suspendWrites() {
        int oldVal, newVal;
        do {
            oldVal = state;
            if (allAreClear(oldVal, FLAG_RESUMED) || allAreSet(oldVal, FLAG_CLOSED)) {
                return;
            }
            newVal = oldVal & ~FLAG_RESUMED;
        } while (! stateUpdater.compareAndSet(this, oldVal, newVal));
    }

    public void resumeWrites() {
        int oldVal, newVal;
        do {
            oldVal = state;
            if (anyAreSet(oldVal, FLAG_RESUMED | FLAG_CLOSED)) {
                return;
            }
            newVal = oldVal | FLAG_RESUMED;
        } while (! stateUpdater.compareAndSet(this, oldVal, newVal));
        executor.execute(ChannelListeners.getChannelListenerTask(this, writeSetter));
    }

    public void wakeupWrites() {
        resumeWrites();
    }

    public boolean isWriteResumed() {
        final int state = this.state;
        return allAreSet(state, FLAG_RESUMED) && allAreClear(state, FLAG_CLOSED);
    }

    public void shutdownWrites() throws IOException {
        int oldVal, newVal;
        do {
            oldVal = state;
            if (allAreSet(oldVal, FLAG_CLOSED)) {
                return;
            }
            newVal = oldVal | FLAG_CLOSED;
        } while (! stateUpdater.compareAndSet(this, oldVal, newVal));
        writeSetter.set(null);
        ChannelListeners.invokeChannelListener(this, closeSetter.get());
    }

    public void awaitWritable() throws IOException {
        // always writable
    }

    public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
        // always writable
    }

    public boolean flush() throws IOException {
        return true;
    }

    public boolean isOpen() {
        return allAreClear(state, FLAG_CLOSED);
    }

    public void close() throws IOException {
        shutdownWrites();
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

    private int enterWrite() throws ClosedChannelException {
        int oldVal, newVal;
        do {
            oldVal = state;
            if (allAreSet(oldVal, FLAG_ENTERED)) {
                throw new ConcurrentStreamChannelAccessException();
            }
            if (allAreSet(oldVal, FLAG_CLOSED)) {
                throw new ClosedChannelException();
            }
            newVal = oldVal | FLAG_ENTERED;
        } while (! stateUpdater.compareAndSet(this, oldVal, newVal));
        return newVal;
    }

    private void exitWrite(int oldVal) {
        int newVal = oldVal & ~FLAG_ENTERED;
        while (! stateUpdater.compareAndSet(this, oldVal, newVal)) {
            oldVal = state;
            newVal = oldVal & ~FLAG_ENTERED;
        }
    }
}
