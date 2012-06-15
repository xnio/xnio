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
