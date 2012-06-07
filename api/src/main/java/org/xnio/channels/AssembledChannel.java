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
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Option;
import org.xnio.XnioWorker;

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
            throw new IllegalArgumentException("Both channels must come from the same worker");
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
