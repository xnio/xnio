/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc.


package org.xnio.nio;

import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;

import static org.xnio.nio.Log.log;

final class NioHandle<C extends Channel> implements Runnable {
    private final SelectionKey selectionKey;
    private final WorkerThread workerThread;
    private final ChannelListener.SimpleSetter<C> handlerSetter;
    private final C channel;
    @SuppressWarnings("unused")
    private volatile int scheduled;

    private static final AtomicIntegerFieldUpdater<NioHandle> scheduledUpdater = AtomicIntegerFieldUpdater.newUpdater(NioHandle.class, "scheduled");

    NioHandle(final SelectionKey selectionKey, final WorkerThread workerThread, final ChannelListener.SimpleSetter<C> handlerSetter, final C channel) {
        this.selectionKey = selectionKey;
        this.workerThread = workerThread;
        this.handlerSetter = handlerSetter;
        this.channel = channel;
    }

    SelectionKey getSelectionKey() {
        return selectionKey;
    }

    WorkerThread getWorkerThread() {
        return workerThread;
    }

    ChannelListener.SimpleSetter<C> getHandlerSetter() {
        return handlerSetter;
    }

    void cancelKey() {
        workerThread.cancelKey(selectionKey);
    }

    void resume(final int op) {
        workerThread.setOps(selectionKey, op);
    }

    void suspend() {
        workerThread.setOps(selectionKey, 0);
    }

    boolean isResumed(final int op) {
        return (workerThread.getOps(selectionKey) & op) == op;
    }

    C getChannel() {
        return channel;
    }

    public void run() {
        scheduled = 0;
        final ChannelListener<? super C> listener = handlerSetter.get();
        if (listener == null) {
            log.tracef("Null listener; suspending %s to prevent runaway", this);
            // prevent runaway
            suspend();
        } else if (workerThread.getOps(selectionKey) != 0) {
            ChannelListeners.invokeChannelListener(channel, listener);
        }
    }

    void execute() {
        if (scheduledUpdater.compareAndSet(this, 0, 1)) {
            workerThread.execute(this);
        }
    }
}
