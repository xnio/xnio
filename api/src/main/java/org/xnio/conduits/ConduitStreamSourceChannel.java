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
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;
import org.xnio.ChannelListener;
import org.xnio.Option;
import org.xnio.XnioExecutor;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.CloseListenerSettable;
import org.xnio.channels.Configurable;
import org.xnio.channels.ReadListenerSettable;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 * A stream source channel which wraps a stream source conduit.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ConduitStreamSourceChannel implements StreamSourceChannel, ReadListenerSettable<ConduitStreamSourceChannel>, CloseListenerSettable<ConduitStreamSourceChannel>, Cloneable {
    private final Configurable configurable;

    private StreamSourceConduit conduit;
    private ChannelListener<? super ConduitStreamSourceChannel> readListener;
    private ChannelListener<? super ConduitStreamSourceChannel> closeListener;

    /**
     * Construct a new instance.
     *
     * @param configurable the configurable to delegate configuration requests to
     * @param conduit the initial conduit to use for data transport
     */
    public ConduitStreamSourceChannel(final Configurable configurable, final StreamSourceConduit conduit) {
        this.configurable = configurable;
        this.conduit = conduit;
        conduit.setReadReadyHandler(new ReadReadyHandler.ChannelListenerHandler<ConduitStreamSourceChannel>(this));
    }

    /**
     * Get the underlying conduit for this channel.
     *
     * @return the underlying conduit for this channel
     */
    public StreamSourceConduit getConduit() {
        return conduit;
    }

    /**
     * Set the underlying conduit for this channel.
     *
     * @param conduit the underlying conduit for this channel
     */
    public void setConduit(final StreamSourceConduit conduit) {
        this.conduit = conduit;
    }

    public boolean isOpen() {
        return ! conduit.isReadShutdown();
    }

    public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        return conduit.transferTo(position, count, target);
    }

    public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
        return conduit.transferTo(count, throughBuffer, target);
    }

    public void setReadListener(final ChannelListener<? super ConduitStreamSourceChannel> readListener) {
        this.readListener = readListener;
    }

    public ChannelListener<? super ConduitStreamSourceChannel> getReadListener() {
        return readListener;
    }

    public void setCloseListener(final ChannelListener<? super ConduitStreamSourceChannel> closeListener) {
        this.closeListener = closeListener;
    }

    public ChannelListener<? super ConduitStreamSourceChannel> getCloseListener() {
        return closeListener;
    }

    public ChannelListener.Setter<ConduitStreamSourceChannel> getReadSetter() {
        return new ReadListenerSettable.Setter<ConduitStreamSourceChannel>(this);
    }

    public ChannelListener.Setter<ConduitStreamSourceChannel> getCloseSetter() {
        return new CloseListenerSettable.Setter<ConduitStreamSourceChannel>(this);
    }

    public XnioWorker getWorker() {
        return conduit.getWorker();
    }

    public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        return conduit.read(dsts, offset, length);
    }

    public long read(final ByteBuffer[] dsts) throws IOException {
        return conduit.read(dsts, 0, dsts.length);
    }

    public int read(final ByteBuffer dst) throws IOException {
        return conduit.read(dst);
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
    public ConduitStreamSourceChannel clone() {
        try {
            return (ConduitStreamSourceChannel) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException(e);
        }
    }
}
