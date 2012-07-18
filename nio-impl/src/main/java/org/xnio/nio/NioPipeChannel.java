/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc.


package org.xnio.nio;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.Pipe;
import java.nio.channels.ScatteringByteChannel;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import org.xnio.IoUtils;

final class NioPipeChannel extends AbstractNioStreamChannel<NioPipeChannel> {

    private final Pipe.SourceChannel sourceChannel;
    private final Pipe.SinkChannel sinkChannel;

    private volatile int closeBits = 0;

    private static final AtomicIntegerFieldUpdater<NioPipeChannel> closeBitsUpdater = AtomicIntegerFieldUpdater.newUpdater(NioPipeChannel.class, "closeBits");

    NioPipeChannel(final NioXnioWorker worker, final Pipe.SinkChannel sinkChannel, final Pipe.SourceChannel sourceChannel) throws ClosedChannelException {
        super(worker);
        this.sinkChannel = sinkChannel;
        this.sourceChannel = sourceChannel;
        start();
    }

    protected ScatteringByteChannel getReadChannel() {
        return sourceChannel;
    }

    protected GatheringByteChannel getWriteChannel() {
        return sinkChannel;
    }

    public void shutdownReads() throws IOException {
        final int old = setBits(this, 0x01);
        if ((old & 2) == 0) {
            try {
                sourceChannel.close();
            } finally {
                cancelReadKey();
                if (old == 0x01) {
                    invokeCloseHandler();
                }
            }
        }
    }

    public void shutdownWrites() throws IOException {
        final int old = setBits(this, 0x01);
        if ((old & 1) == 0) {
            try {
                sinkChannel.close();
            } finally {
                cancelWriteKey();
                if (old == 0x02) {
                    invokeCloseHandler();
                }
            }
        }
    }

    public boolean isOpen() {
        return closeBits < 0x03;
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
        final int old = setBits(this, 0x03);
        if (old != 0x03) try {
            if (old == 0) {
                // since we've got two channels, only rethrow a failure on the WRITE side, since that's the side that stands to lose data
                IoUtils.safeClose(sourceChannel);
                try {
                    sinkChannel.close();
                } finally {
                    cancelWriteKey();
                    cancelReadKey();
                }
            } else if (old == 0x01) try {
                sourceChannel.close();
            } finally {
                cancelReadKey();
            } else if (old == 0x02) try {
                sinkChannel.close();
            } finally {
                cancelWriteKey();
            }
        } finally {
            invokeCloseHandler();
        }
    }

   public String toString() {
        return String.format("pipe channel (NIO) <%h>", this);
    }
}
