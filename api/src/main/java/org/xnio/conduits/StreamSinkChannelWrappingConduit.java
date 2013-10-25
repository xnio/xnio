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
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 * A conduit which wraps a channel, for compatibility.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class StreamSinkChannelWrappingConduit implements StreamSinkConduit {
    private final StreamSinkChannel channel;

    /**
     * Construct a new instance.
     *
     * @param channel the channel to wrap
     */
    public StreamSinkChannelWrappingConduit(final StreamSinkChannel channel) {
        this.channel = channel;
    }

    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        return channel.transferFrom(src, position, count);
    }

    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        return channel.transferFrom(source, count, throughBuffer);
    }

    public int write(final ByteBuffer src) throws IOException {
        return channel.write(src);
    }

    public long write(final ByteBuffer[] srcs, final int offs, final int len) throws IOException {
        return channel.write(srcs, offs, len);
    }

    @Override
    public int writeFinal(ByteBuffer src) throws IOException {
        return channel.writeFinal(src);
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs, int offset, int length) throws IOException {
        return channel.writeFinal(srcs, offset, length);
    }

    public void terminateWrites() throws IOException {
        channel.shutdownWrites();
    }

    public boolean isWriteShutdown() {
        return ! channel.isOpen();
    }

    public void resumeWrites() {
        channel.resumeWrites();
    }

    public void suspendWrites() {
        channel.suspendWrites();
    }

    public void wakeupWrites() {
        channel.wakeupWrites();
    }

    public boolean isWriteResumed() {
        return channel.isWriteResumed();
    }

    public void awaitWritable() throws IOException {
        channel.awaitWritable();
    }

    public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
        channel.awaitWritable(time, timeUnit);
    }

    public XnioIoThread getWriteThread() {
        return channel.getIoThread();
    }

    public void setWriteReadyHandler(final WriteReadyHandler handler) {
        channel.getWriteSetter().set(new ChannelListener<StreamSinkChannel>() {
            public void handleEvent(final StreamSinkChannel channel) {
                handler.writeReady();
            }
        });
    }

    public void truncateWrites() throws IOException {
        channel.close();
    }

    public boolean flush() throws IOException {
        return channel.flush();
    }

    public XnioWorker getWorker() {
        return channel.getWorker();
    }
}
