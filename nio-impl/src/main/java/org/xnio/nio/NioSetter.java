/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, JBoss Inc.


package org.xnio.nio;

import java.nio.channels.Channel;
import org.xnio.ChannelListener;

final class NioSetter<T extends Channel> implements ChannelListener.Setter<T> {
    private volatile ChannelListener<? super T> listener;

    public void set(final ChannelListener<? super T> listener) {
        this.listener = listener;
    }

    public ChannelListener<? super T> get() {
        return listener;
    }
}
