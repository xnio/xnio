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
import org.xnio.XnioWorker;
import org.xnio.channels.CloseListenerSettable;
import org.xnio.channels.Configurable;
import org.xnio.channels.WritableMessageChannel;
import org.xnio.channels.WriteListenerSettable;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ConduitWritableMessageChannel implements WritableMessageChannel, WriteListenerSettable<ConduitWritableMessageChannel>, CloseListenerSettable<ConduitWritableMessageChannel>, Cloneable {
    private final Configurable configurable;
    private final MessageSinkConduit conduit;

    private ChannelListener<? super ConduitWritableMessageChannel> writeListener;
    private ChannelListener<? super ConduitWritableMessageChannel> closeListener;

    public ConduitWritableMessageChannel(final Configurable configurable, final MessageSinkConduit conduit) {
        this.configurable = configurable;
        this.conduit = conduit;
        conduit.setWriteReadyHandler(new WriteReadyHandler.ChannelListenerHandler<ConduitWritableMessageChannel>(this));
    }

    public MessageSinkConduit getConduit() {
        return conduit;
    }

    public ChannelListener<? super ConduitWritableMessageChannel> getWriteListener() {
        return writeListener;
    }

    public void setWriteListener(final ChannelListener<? super ConduitWritableMessageChannel> writeListener) {
        this.writeListener = writeListener;
    }

    public ChannelListener<? super ConduitWritableMessageChannel> getCloseListener() {
        return closeListener;
    }

    public void setCloseListener(final ChannelListener<? super ConduitWritableMessageChannel> closeListener) {
        this.closeListener = closeListener;
    }

    public ChannelListener.Setter<ConduitWritableMessageChannel> getWriteSetter() {
        return new WriteListenerSettable.Setter<ConduitWritableMessageChannel>(this);
    }

    public ChannelListener.Setter<ConduitWritableMessageChannel> getCloseSetter() {
        return new CloseListenerSettable.Setter<ConduitWritableMessageChannel>(this);
    }

    public void suspendWrites() {
        conduit.suspendWrites();
    }

    public void resumeWrites() {
        conduit.resumeWrites();
    }

    public void wakeupWrites() {
        conduit.wakeupWrites();
    }

    public boolean isWriteResumed() {
        return conduit.isWriteResumed();
    }

    public void awaitWritable() throws IOException {
        conduit.awaitWritable();
    }

    public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
        conduit.awaitWritable(time, timeUnit);
    }

    public boolean send(final ByteBuffer dst) throws IOException {
        return conduit.send(dst);
    }

    public boolean send(final ByteBuffer[] srcs) throws IOException {
        return conduit.send(srcs, 0, srcs.length);
    }

    public boolean send(final ByteBuffer[] dsts, final int offs, final int len) throws IOException {
        return conduit.send(dsts, offs, len);
    }

    public boolean flush() throws IOException {
        return conduit.flush();
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

    public void shutdownWrites() throws IOException {
        conduit.terminateWrites();
    }

    public boolean isOpen() {
        return ! conduit.isWriteShutdown();
    }

    public void close() throws IOException {
        conduit.truncateWrites();
    }

    public XnioExecutor getWriteThread() {
        return conduit.getWriteThread();
    }

    public XnioWorker getWorker() {
        return conduit.getWorker();
    }

    public ConduitWritableMessageChannel clone() {
        try {
            return (ConduitWritableMessageChannel) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException(e);
        }
    }
}
