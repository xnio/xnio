/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2013 Red Hat, Inc. and/or its affiliates.
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

package org.xnio;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import org.xnio.channels.CloseableChannel;
import org.xnio.channels.ConnectedChannel;

import static org.xnio.Bits.allAreClear;
import static org.xnio.Bits.allAreSet;
import static org.xnio.Bits.anyAreClear;

/**
 * The base for all connections.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class Connection implements CloseableChannel, ConnectedChannel {

    protected final XnioIoThread thread;
    @SuppressWarnings("unused")
    private volatile int state;

    private static final int FLAG_READ_CLOSED           = 0b0001;
    private static final int FLAG_WRITE_CLOSED          = 0b0010;

    private static final AtomicIntegerFieldUpdater<Connection> stateUpdater = AtomicIntegerFieldUpdater.newUpdater(Connection.class, "state");

    /**
     * Construct a new instance.
     *
     * @param thread the I/O thread of this connection
     */
    protected Connection(final XnioIoThread thread) {
        this.thread = thread;
    }

    private static <A extends SocketAddress> A castAddress(final Class<A> type, SocketAddress address) {
        return type.isInstance(address) ? type.cast(address) : null;
    }

    public final <A extends SocketAddress> A getPeerAddress(final Class<A> type) {
        return castAddress(type, getPeerAddress());
    }

    public final <A extends SocketAddress> A getLocalAddress(final Class<A> type) {
        return castAddress(type, getLocalAddress());
    }

    public final XnioWorker getWorker() {
        return thread.getWorker();
    }

    public XnioIoThread getIoThread() {
        return thread;
    }

    /**
     * Indicate that reads have been closed on this connection.
     *
     * @return {@code true} if read closure was successfully indicated; {@code false} if this method has already been called
     */
    protected boolean readClosed() {
        int oldVal, newVal;
        do {
            oldVal = state;
            if (allAreSet(oldVal, FLAG_READ_CLOSED)) {
                return false;
            }
            newVal = oldVal | FLAG_READ_CLOSED;
        } while (! stateUpdater.compareAndSet(this, oldVal, newVal));
        if (allAreSet(newVal, FLAG_READ_CLOSED | FLAG_WRITE_CLOSED)) {
            try {
                closeAction();
            } catch (Throwable ignored) {}
            invokeCloseListener();
        }
        return true;
    }

    /**
     * Indicate that writes have been closed on this connection.
     *
     * @return {@code true} if write closure was successfully indicated; {@code false} if this method has already been called
     */
    protected boolean writeClosed() {
        int oldVal, newVal;
        do {
            oldVal = state;
            if (allAreSet(oldVal, FLAG_WRITE_CLOSED)) {
                return false;
            }
            newVal = oldVal | FLAG_WRITE_CLOSED;
        } while (! stateUpdater.compareAndSet(this, oldVal, newVal));
        if (allAreSet(newVal, FLAG_READ_CLOSED | FLAG_WRITE_CLOSED)) {
            try {
                closeAction();
            } catch (Throwable ignored) {}
            invokeCloseListener();
        }
        return true;
    }

    public final void close() throws IOException {
        int oldVal, newVal;
        do {
            oldVal = state;
            if (allAreSet(oldVal, FLAG_WRITE_CLOSED | FLAG_READ_CLOSED)) {
                return;
            }
            newVal = oldVal | FLAG_READ_CLOSED | FLAG_WRITE_CLOSED;
        } while (! stateUpdater.compareAndSet(this, oldVal, newVal));
        try {
            closeAction();
        } finally {
            if (allAreClear(oldVal, FLAG_WRITE_CLOSED)) try {
                notifyWriteClosed();
            } catch (Throwable ignored) {
            }
            if (allAreClear(oldVal, FLAG_READ_CLOSED)) try {
                notifyReadClosed();
            } catch (Throwable ignored) {
            }
            invokeCloseListener();
        }
    }

    /**
     * Determine whether reads have been shut down on this connection.
     *
     * @return {@code true} if reads were shut down
     */
    public boolean isReadShutdown() {
        return allAreSet(state, FLAG_READ_CLOSED);
    }

    /**
     * Determine whether writes have been shut down on this connection.
     *
     * @return {@code true} if writes were shut down
     */
    public boolean isWriteShutdown() {
        return allAreSet(state, FLAG_WRITE_CLOSED);
    }

    public boolean isOpen() {
        return anyAreClear(state, FLAG_READ_CLOSED | FLAG_WRITE_CLOSED);
    }

    /**
     * Indicate to conduit handlers that writes have been closed.
     */
    protected abstract void notifyWriteClosed();

    /**
     * Indicate to conduit handlers that reads have been closed.
     */
    protected abstract void notifyReadClosed();

    abstract void invokeCloseListener();

    /**
     * The close action to perform on this connection.
     *
     * @throws IOException if close fails
     */
    protected void closeAction() throws IOException {}

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
