/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc.


package org.xnio.nio;

import java.nio.channels.SelectableChannel;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.TimeUnit;
import org.xnio.Xnio;

final class SelectorUtils {
    private SelectorUtils() {
    }

    public static void await(NioXnio nioXnio, SelectableChannel channel, int op) throws IOException {
        Xnio.checkBlockingAllowed();
        final Selector selector = nioXnio.getSelector();
        final SelectionKey selectionKey = channel.register(selector, op);
        selector.select();
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException();
        }
        selectionKey.interestOps(0);
    }

    public static void await(NioXnio nioXnio, SelectableChannel channel, int op, long time, TimeUnit unit) throws IOException {
        Xnio.checkBlockingAllowed();
        final Selector selector = nioXnio.getSelector();
        final SelectionKey selectionKey = channel.register(selector, op);
        selector.select(unit.toMillis(time));
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException();
        }
        selectionKey.interestOps(0);
    }
}
