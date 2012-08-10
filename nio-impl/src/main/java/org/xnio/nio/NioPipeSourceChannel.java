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

package org.xnio.nio;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.Pipe;
import java.nio.channels.ScatteringByteChannel;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class NioPipeSourceChannel extends AbstractNioStreamSourceChannel<NioPipeSourceChannel> {
    private final Pipe.SourceChannel sourceChannel;

    @SuppressWarnings("unused")
    private volatile int closed = 0;

    private static final AtomicIntegerFieldUpdater<NioPipeSourceChannel> closedUpdater = AtomicIntegerFieldUpdater.newUpdater(NioPipeSourceChannel.class, "closed");

    NioPipeSourceChannel(final NioXnioWorker worker, final Pipe.SourceChannel sourceChannel) throws ClosedChannelException {
        super(worker);
        this.sourceChannel = sourceChannel;
    }

    protected ScatteringByteChannel getReadChannel() {
        return sourceChannel;
    }

    public void shutdownReads() throws IOException {
        if (closedUpdater.compareAndSet(this, 0, 1)) try {
            try { cancelReadKey(); } catch (Throwable ignored) {}
            sourceChannel.close();
        } finally {
            invokeCloseHandler();
        }
    }

    public boolean isOpen() {
        return sourceChannel.isOpen();
    }

    public void close() throws IOException {
        shutdownReads();
    }
}
