/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc.


package org.xnio.nio;

import java.nio.channels.ClosedChannelException;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.XnioWorker;
import org.xnio.channels.CloseableChannel;

abstract class AbstractNioChannel<C extends AbstractNioChannel<C>> implements CloseableChannel {

    private final ChannelListener.SimpleSetter<C> closeSetter = new ChannelListener.SimpleSetter<C>();

    protected volatile NioXnioWorker worker;

    public AbstractNioChannel(final NioXnioWorker worker) {
        this.worker = worker;
    }

    public final XnioWorker getWorker() {
        return worker;
    }

    public final ChannelListener.Setter<? extends C> getCloseSetter() {
        return closeSetter;
    }

    @SuppressWarnings("unchecked")
    protected final C typed() {
        return (C) this;
    }

    protected final void invokeCloseHandler() {
        ChannelListeners.invokeChannelListener(typed(), closeSetter.get());
    }

    void migrateTo(NioXnioWorker worker) throws ClosedChannelException {
        this.worker = worker;
    }
}
