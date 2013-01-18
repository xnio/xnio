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
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.channels.WriteListenerSettable;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class DuctStreamSinkChannel implements StreamSinkChannel, WriteListenerSettable<DuctStreamSinkChannel>, CloseListenerSettable<DuctStreamSinkChannel>, Cloneable {
    private final StreamSinkDuct duct;

    private ChannelListener<? super DuctStreamSinkChannel> writeListener;
    private ChannelListener<? super DuctStreamSinkChannel> closeListener;

    public DuctStreamSinkChannel(final StreamSinkDuct duct) {
        this.duct = duct;
        duct.setWriteReadyHandler(new WriteReadyHandler.ChannelListenerHandler<DuctStreamSinkChannel>(this));
    }

    public StreamSinkDuct getDuct() {
        return duct;
    }

    public ChannelListener<? super DuctStreamSinkChannel> getWriteListener() {
        return writeListener;
    }

    public void setWriteListener(final ChannelListener<? super DuctStreamSinkChannel> writeListener) {
        this.writeListener = writeListener;
    }

    public ChannelListener<? super DuctStreamSinkChannel> getCloseListener() {
        return closeListener;
    }

    public void setCloseListener(final ChannelListener<? super DuctStreamSinkChannel> closeListener) {
        this.closeListener = closeListener;
    }

    public ChannelListener.Setter<DuctStreamSinkChannel> getWriteSetter() {
        return new WriteListenerSettable.Setter<DuctStreamSinkChannel>(this);
    }

    public ChannelListener.Setter<DuctStreamSinkChannel> getCloseSetter() {
        return new CloseListenerSettable.Setter<DuctStreamSinkChannel>(this);
    }

    public void suspendWrites() {
        duct.suspendWrites();
    }

    public void resumeWrites() {
        duct.resumeWrites();
    }

    public void wakeupWrites() {
        duct.wakeupWrites();
    }

    public boolean isWriteResumed() {
        return duct.isWriteResumed();
    }

    public void awaitWritable() throws IOException {
        duct.awaitWritable();
    }

    public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
        duct.awaitWritable(time, timeUnit);
    }

    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        return duct.transferFrom(src, position, count);
    }

    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        return duct.transferFrom(source, count, throughBuffer);
    }

    public int write(final ByteBuffer dst) throws IOException {
        return duct.write(dst);
    }

    public long write(final ByteBuffer[] srcs) throws IOException {
        return duct.write(srcs, 0, srcs.length);
    }

    public long write(final ByteBuffer[] dsts, final int offs, final int len) throws IOException {
        return duct.write(dsts, offs, len);
    }

    public boolean flush() throws IOException {
        return duct.flush();
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

    public void shutdownWrites() throws IOException {
        duct.terminateWrites();
    }

    public boolean isOpen() {
        return ! duct.isWriteShutdown();
    }

    public void close() throws IOException {
        duct.truncateWrites();
    }

    public XnioExecutor getWriteThread() {
        return duct.getWriteThread();
    }

    public XnioWorker getWorker() {
        return duct.getWorker();
    }

    public DuctStreamSinkChannel clone() {
        try {
            return (DuctStreamSinkChannel) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException(e);
        }
    }
}
