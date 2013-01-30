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

package org.xnio.conduits;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import org.xnio.ChannelListener;
import org.xnio.Option;
import org.xnio.XnioExecutor;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.CloseListenerSettable;
import org.xnio.channels.Configurable;
import org.xnio.channels.ReadListenerSettable;
import org.xnio.channels.ReadableMessageChannel;

/**
 * A readable message channel which is backed by a message source conduit.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ConduitReadableMessageChannel implements ReadableMessageChannel, ReadListenerSettable<ConduitReadableMessageChannel>, CloseListenerSettable<ConduitReadableMessageChannel>, Cloneable {
    private final Configurable configurable;

    private MessageSourceConduit conduit;
    private ChannelListener<? super ConduitReadableMessageChannel> readListener;
    private ChannelListener<? super ConduitReadableMessageChannel> closeListener;

    /**
     * Construct a new instance.
     *
     * @param configurable the configurable to delegate configuration requests to
     * @param conduit the initial conduit to use for data transport
     */
    public ConduitReadableMessageChannel(final Configurable configurable, final MessageSourceConduit conduit) {
        this.configurable = configurable;
        this.conduit = conduit;
        conduit.setReadReadyHandler(new ReadReadyHandler.ChannelListenerHandler<ConduitReadableMessageChannel>(this));
    }

    /**
     * Get the underlying conduit for this channel.
     *
     * @return the underlying conduit for this channel
     */
    public MessageSourceConduit getConduit() {
        return conduit;
    }

    /**
     * Set the underlying conduit for this channel.
     *
     * @param conduit the underlying conduit for this channel
     */
    public void setConduit(final MessageSourceConduit conduit) {
        this.conduit = conduit;
    }

    public boolean isOpen() {
        return ! conduit.isReadShutdown();
    }

    public void setReadListener(final ChannelListener<? super ConduitReadableMessageChannel> readListener) {
        this.readListener = readListener;
    }

    public ChannelListener<? super ConduitReadableMessageChannel> getReadListener() {
        return readListener;
    }

    public void setCloseListener(final ChannelListener<? super ConduitReadableMessageChannel> closeListener) {
        this.closeListener = closeListener;
    }

    public ChannelListener<? super ConduitReadableMessageChannel> getCloseListener() {
        return closeListener;
    }

    public ChannelListener.Setter<ConduitReadableMessageChannel> getReadSetter() {
        return new ReadListenerSettable.Setter<ConduitReadableMessageChannel>(this);
    }

    public ChannelListener.Setter<ConduitReadableMessageChannel> getCloseSetter() {
        return new CloseListenerSettable.Setter<ConduitReadableMessageChannel>(this);
    }

    public XnioWorker getWorker() {
        return conduit.getWorker();
    }

    public long receive(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        return conduit.receive(dsts, offset, length);
    }

    public long receive(final ByteBuffer[] dsts) throws IOException {
        return conduit.receive(dsts, 0, dsts.length);
    }

    public int receive(final ByteBuffer dst) throws IOException {
        return conduit.receive(dst);
    }

    public void suspendReads() {
        conduit.suspendReads();
    }

    public void resumeReads() {
        conduit.resumeReads();
    }

    public boolean isReadResumed() {
        return conduit.isReadResumed();
    }

    public void wakeupReads() {
        conduit.wakeupReads();
    }

    public void shutdownReads() throws IOException {
        conduit.terminateReads();
    }

    public void awaitReadable() throws IOException {
        conduit.awaitReadable();
    }

    public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
        conduit.awaitReadable(time, timeUnit);
    }

    @Deprecated
    public XnioExecutor getReadThread() {
        return conduit.getReadThread();
    }

    public XnioIoThread getIoThread() {
        return conduit.getReadThread();
    }

    public void close() throws IOException {
        conduit.terminateReads();
    }

    public boolean supportsOption(final Option<?> option) {
        return configurable.supportsOption(option);
    }

    public <T> T getOption(final Option<T> option) throws IOException {
        return configurable.getOption(option);
    }

    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        return configurable.setOption(option, value);
    }

    /**
     * Duplicate this channel.  Changing the delegate conduit in one channel will not affect the other.
     *
     * @return the cloned channel
     */
    public ConduitReadableMessageChannel clone() {
        try {
            return (ConduitReadableMessageChannel) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException(e);
        }
    }
}
