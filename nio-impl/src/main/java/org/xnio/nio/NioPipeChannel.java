/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.Pipe;
import java.nio.channels.ScatteringByteChannel;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import org.xnio.IoUtils;
import org.xnio.Option;

final class NioPipeChannel extends AbstractNioStreamChannel<NioPipeChannel> {

    private final Pipe.SourceChannel sourceChannel;
    private final Pipe.SinkChannel sinkChannel;

    private volatile int closeBits = 0;

    private static final AtomicIntegerFieldUpdater<NioPipeChannel> closeBitsUpdater = AtomicIntegerFieldUpdater.newUpdater(NioPipeChannel.class, "closeBits");

    NioPipeChannel(final NioXnio xnio, final Pipe.SinkChannel sinkChannel, final Pipe.SourceChannel sourceChannel) {
        super(xnio);
        this.sinkChannel = sinkChannel;
        this.sourceChannel = sourceChannel;
    }

    protected ScatteringByteChannel getReadChannel() {
        return sourceChannel;
    }

    protected GatheringByteChannel getWriteChannel() {
        return sinkChannel;
    }

    public void shutdownReads() throws IOException {
        boolean ok = false;
        try {
            sourceChannel.close();
            ok = true;
        } finally {
            if (setBits(this, 0x02) == 0x01) {
                if (ok) close(); else IoUtils.safeClose(this);
            }
        }
    }

    public boolean shutdownWrites() throws IOException {
        boolean ok = false;
        try {
            sinkChannel.close();
            ok = true;
        } finally {
            if (setBits(this, 0x01) == 0x02) {
                if (ok) close(); else IoUtils.safeClose(this);
            }
        }
        return true;
    }

    public boolean isOpen() {
        return closeBits < 0x03;
    }

    public boolean supportsOption(final Option<?> option) {
        return false;
    }

    public <T> T getOption(final Option<T> option) throws IOException {
        return null;
    }

    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        return null;
    }

    private static int setBits(NioPipeChannel instance, int bits) {
        int old;
        int updated;
        do {
            old = instance.closeBits;
            updated = old | bits;
            if (updated == old) {
                break;
            }
        } while (! closeBitsUpdater.compareAndSet(instance, old, updated));
        return old;
    }

    public void close() throws IOException {
        // since we've got two channels, only rethrow a failure on the WRITE side, since that's the side that stands to lose data
        IoUtils.safeClose(sourceChannel);
        try {
            sinkChannel.close();
        } finally {
            if (setBits(this, 0x04) < 0x04) {
                invokeCloseHandler();
            }
            cancelWriteKey();
            cancelReadKey();
        }
    }

   public String toString() {
        return String.format("pipe channel (NIO) <%h>", this);
    }
}
