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
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Option;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;

import static org.xnio._private.Messages.msg;

/**
 * A closeable view over a read and write side of a suspendable channel.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public class AssembledChannel implements CloseableChannel {

    private final SuspendableReadChannel readChannel;
    private final SuspendableWriteChannel writeChannel;

    // Actual type is SimpleSetter<THIS>
    private final ChannelListener.SimpleSetter<AssembledChannel> closeSetter = new ChannelListener.SimpleSetter<AssembledChannel>();

    private final ChannelListener<CloseableChannel> listener = new ChannelListener<CloseableChannel>() {
        public void handleEvent(final CloseableChannel channel) {
            int newState, oldState;
            final AssembledChannel obj = AssembledChannel.this;
            do {
                oldState = stateUpdater.get(obj);
                if (oldState == 3) {
                    return;
                }
                newState = oldState;
                if (channel == readChannel) {
                    newState |= 1;
                }
                if (channel == writeChannel) {
                    newState |= 2;
                }
            } while (! stateUpdater.compareAndSet(obj, oldState, newState));
            if (newState == 3) {
                ChannelListeners.invokeChannelListener(obj, closeSetter.get());
            }
        }
    };

    @SuppressWarnings("unused")
    private volatile int state = 0;

    private static final AtomicIntegerFieldUpdater<AssembledChannel> stateUpdater = AtomicIntegerFieldUpdater.newUpdater(AssembledChannel.class, "state");

    /**
     * Construct a new instance.
     *
     * @param readChannel the read side
     * @param writeChannel the write side
     */
    public AssembledChannel(final SuspendableReadChannel readChannel, final SuspendableWriteChannel writeChannel) {
        this.readChannel = readChannel;
        this.writeChannel = writeChannel;
        if (readChannel.getWorker() != writeChannel.getWorker()) {
            throw msg.differentWorkers();
        }
    }

    public ChannelListener.Setter<? extends CloseableChannel> getCloseSetter() {
        readChannel.getCloseSetter().set(listener);
        writeChannel.getCloseSetter().set(listener);
        return closeSetter;
    }

    public XnioWorker getWorker() {
        // both should be the same
        return readChannel.getWorker();
    }

    public XnioIoThread getIoThread() {
        // both should be the same
        return readChannel.getIoThread();
    }

    public void close() throws IOException {
        try {
            readChannel.close();
            writeChannel.close();
        } finally {
            IoUtils.safeClose(readChannel);
            IoUtils.safeClose(writeChannel);
        }
    }

    public boolean isOpen() {
        return readChannel.isOpen() && writeChannel.isOpen();
    }

    public boolean supportsOption(final Option<?> option) {
        return readChannel.supportsOption(option) || writeChannel.supportsOption(option);
    }

    private static <T> T nonNullOrFirst(T one, T two) {
        return one != null ? one : two;
    }

    public <T> T getOption(final Option<T> option) throws IOException {
        return nonNullOrFirst(readChannel.getOption(option), writeChannel.getOption(option));
    }

    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        return nonNullOrFirst(readChannel.setOption(option, value), writeChannel.setOption(option, value));
    }
}
