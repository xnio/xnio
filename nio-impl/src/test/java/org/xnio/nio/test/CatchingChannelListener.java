/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2009 


package org.xnio.nio.test;

import java.util.List;
import org.xnio.ChannelListener;
import java.nio.channels.Channel;

final class CatchingChannelListener<T extends Channel> implements ChannelListener<T> {

    private final ChannelListener<? super T> delegate;
    private final List<Throwable> problems;

    CatchingChannelListener(final ChannelListener<? super T> delegate, final List<Throwable> problems) {
        this.delegate = delegate;
        this.problems = problems;
    }

    public void handleEvent(final T channel) {
        try {
            if (delegate != null) delegate.handleEvent(channel);
        } catch (RuntimeException t) {
            problems.add(t);
            throw t;
        } catch (Error t) {
            problems.add(t);
            throw t;
        }
    }
}
