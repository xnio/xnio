/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. 



package org.xnio.nio;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.Pipe;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class NioPipeSinkChannel extends AbstractNioStreamSinkChannel<NioPipeSinkChannel> {
    private final Pipe.SinkChannel sinkChannel;

    @SuppressWarnings("unused")
    private volatile int closed = 0;

    private static final AtomicIntegerFieldUpdater<NioPipeSinkChannel> closedUpdater = AtomicIntegerFieldUpdater.newUpdater(NioPipeSinkChannel.class, "closed");

    NioPipeSinkChannel(final NioXnioWorker worker, final Pipe.SinkChannel sinkChannel) throws ClosedChannelException {
        super(worker);
        this.sinkChannel = sinkChannel;
    }

    protected GatheringByteChannel getWriteChannel() {
        return sinkChannel;
    }

    public void shutdownWrites() throws IOException {
        if (closedUpdater.compareAndSet(this, 0, 1)) try {
            sinkChannel.close();
        } finally {
            invokeCloseHandler();
        }
    }

    public boolean isOpen() {
        return sinkChannel.isOpen();
    }

    public void close() throws IOException {
        shutdownWrites();
    }
}
