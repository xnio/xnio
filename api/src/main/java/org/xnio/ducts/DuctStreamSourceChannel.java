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

package org.xnio.ducts;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;
import org.xnio.ChannelListener;
import org.xnio.Option;
import org.xnio.XnioExecutor;
import org.xnio.XnioWorker;
import org.xnio.channels.CloseListenerSettable;
import org.xnio.channels.ReadListenerSettable;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class DuctStreamSourceChannel implements StreamSourceChannel, ReadListenerSettable<DuctStreamSourceChannel>, CloseListenerSettable<DuctStreamSourceChannel>, Cloneable {
    private final StreamSourceDuct duct;

    private ChannelListener<? super DuctStreamSourceChannel> readListener;
    private ChannelListener<? super DuctStreamSourceChannel> closeListener;

    public DuctStreamSourceChannel(final StreamSourceDuct duct) {
        this.duct = duct;
        duct.setReadReadyHandler(new ReadReadyHandler.ChannelListenerHandler<DuctStreamSourceChannel>(this));
    }

    public StreamSourceDuct getDuct() {
        return duct;
    }

    public boolean isOpen() {
        return ! duct.isReadShutdown();
    }

    public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        return duct.transferTo(position, count, target);
    }

    public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
        return duct.transferTo(count, throughBuffer, target);
    }

    public void setReadListener(final ChannelListener<? super DuctStreamSourceChannel> readListener) {
        this.readListener = readListener;
    }

    public ChannelListener<? super DuctStreamSourceChannel> getReadListener() {
        return readListener;
    }

    public void setCloseListener(final ChannelListener<? super DuctStreamSourceChannel> closeListener) {
        this.closeListener = closeListener;
    }

    public ChannelListener<? super DuctStreamSourceChannel> getCloseListener() {
        return closeListener;
    }

    public ChannelListener.Setter<DuctStreamSourceChannel> getReadSetter() {
        return new ReadListenerSettable.Setter<DuctStreamSourceChannel>(this);
    }

    public ChannelListener.Setter<DuctStreamSourceChannel> getCloseSetter() {
        return new CloseListenerSettable.Setter<DuctStreamSourceChannel>(this);
    }

    public XnioWorker getWorker() {
        return duct.getWorker();
    }

    public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        return duct.read(dsts, offset, length);
    }

    public long read(final ByteBuffer[] dsts) throws IOException {
        return duct.read(dsts, 0, dsts.length);
    }

    public int read(final ByteBuffer dst) throws IOException {
        return duct.read(dst);
    }

    public void suspendReads() {
        duct.suspendReads();
    }

    public void resumeReads() {
        duct.resumeReads();
    }

    public boolean isReadResumed() {
        return duct.isReadResumed();
    }

    public void wakeupReads() {
        duct.wakeupReads();
    }

    public void shutdownReads() throws IOException {
        duct.terminateReads();
    }

    public void awaitReadable() throws IOException {
        duct.awaitReadable();
    }

    public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
        duct.awaitReadable(time, timeUnit);
    }

    public XnioExecutor getReadThread() {
        return duct.getReadThread();
    }

    public void close() throws IOException {
        duct.terminateReads();
    }

    public boolean supportsOption(final Option<?> option) {
        return duct.supportsOption(option);
    }

    public <T> T getOption(final Option<T> option) throws IOException {
        return duct.getOption(option);
    }

    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        return duct.setOption(option, value);
    }

    public DuctStreamSourceChannel clone() {
        try {
            return (DuctStreamSourceChannel) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException(e);
        }
    }
}
