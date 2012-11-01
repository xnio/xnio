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

import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;

import static org.xnio.Bits.*;
import static org.xnio.nio.Log.log;

final class NioHandle<C extends Channel> implements Runnable {
    private final SelectionKey selectionKey;
    private final WorkerThread workerThread;
    private final ChannelListener.SimpleSetter<C> handlerSetter;
    private final C channel;
    @SuppressWarnings("unused")
    private volatile int state;

    private static final int FLAG_SCHEDULED = 1 << 5;
    private static final int FLAG_SUS_RES = 1 << 6;
    private static final int FLAG_RESUMED = 1 << 7;
    private static final int MASK_OPS = intBitMask(0, 4); // NIO uses five bits; 1 << 1 is unused

    @SuppressWarnings({ "raw", "rawtypes" })
    private static final AtomicIntegerFieldUpdater<NioHandle> stateUpdater = AtomicIntegerFieldUpdater.newUpdater(NioHandle.class, "state");

    NioHandle(final SelectionKey selectionKey, final WorkerThread workerThread, final ChannelListener.SimpleSetter<C> handlerSetter, final C channel, final int ops) {
        this.selectionKey = selectionKey;
        this.workerThread = workerThread;
        this.handlerSetter = handlerSetter;
        this.channel = channel;
        this.state = ops & MASK_OPS;
    }

    WorkerThread getWorkerThread() {
        return workerThread;
    }

    ChannelListener.SimpleSetter<C> getHandlerSetter() {
        return handlerSetter;
    }

    void cancelKey() {
        suspend();
        workerThread.cancelKey(selectionKey);
    }

    int setOps(int newOps) {
        int oldVal, newVal;
        do {
            oldVal = state;
            if ((oldVal & MASK_OPS) == newOps) {
                return newOps;
            }
            newVal = oldVal & ~MASK_OPS | newOps;
        } while (! stateUpdater.compareAndSet(this, oldVal, newVal));
        return oldVal & MASK_OPS;
    }

    void resume() {
        int oldVal, newVal;
        do {
            oldVal = state;
            if (allAreSet(oldVal, FLAG_RESUMED)) {
                return;
            }
            newVal = oldVal | FLAG_RESUMED | FLAG_SUS_RES;
        } while (! stateUpdater.compareAndSet(this, oldVal, newVal));
        if (allAreSet(oldVal, FLAG_SUS_RES)) {
            return;
        }
        oldVal = newVal;
        newVal = oldVal & ~FLAG_SUS_RES;
        workerThread.setOps(selectionKey, oldVal & MASK_OPS);
        if (! stateUpdater.compareAndSet(this, oldVal, newVal)) {
            boolean resume = true;
            do {
                oldVal = state;
                newVal = oldVal & ~FLAG_SUS_RES;
                if (allAreSet(oldVal, FLAG_RESUMED) != resume || (oldVal & MASK_OPS) != (newVal & MASK_OPS)) {
                    resume = !resume;
                    workerThread.setOps(selectionKey, resume ? oldVal & MASK_OPS : 0);
                }
            } while (! stateUpdater.compareAndSet(this, oldVal, newVal));
        }
    }

    void suspend() {
        int oldVal, newVal;
        do {
            oldVal = state;
            if (allAreClear(oldVal, FLAG_RESUMED)) {
                return;
            }
            newVal = oldVal & ~FLAG_RESUMED | FLAG_SUS_RES;
        } while (! stateUpdater.compareAndSet(this, oldVal, newVal));
        if (allAreSet(oldVal, FLAG_SUS_RES)) {
            return;
        }
        oldVal = newVal;
        newVal = oldVal & ~FLAG_SUS_RES;
        workerThread.setOps(selectionKey, 0);
        if (! stateUpdater.compareAndSet(this, oldVal, newVal)) {
            final int ops = oldVal & MASK_OPS;
            boolean resume = false;
            do {
                if (allAreSet(oldVal, FLAG_RESUMED) != resume) {
                    resume = !resume;
                    workerThread.setOps(selectionKey, resume ? ops : 0);
                }
                oldVal = state;
                newVal = oldVal & ~FLAG_SUS_RES;
            } while (! stateUpdater.compareAndSet(this, oldVal, newVal));
        }
    }

    boolean isResumed() {
        return allAreSet(state, FLAG_RESUMED);
    }

    C getChannel() {
        return channel;
    }

    public void run() {
        int oldVal, newVal;
        do {
            oldVal = state;
            newVal = oldVal & ~FLAG_SCHEDULED;
        } while (! stateUpdater.compareAndSet(this, oldVal, newVal));
        final ChannelListener<? super C> listener = handlerSetter.get();
        if (listener == null) {
            log.tracef("Null listener; suspending %s to prevent runaway", this);
            // prevent runaway
            suspend();
        } else if (allAreSet(oldVal, FLAG_RESUMED)) {
            ChannelListeners.invokeChannelListener(channel, listener);
        }
    }

    void execute() {
        int oldVal, newVal;
        do {
            oldVal = state;
            if (allAreSet(oldVal, FLAG_SCHEDULED)) {
                return;
            }
            newVal = oldVal | FLAG_SCHEDULED;
        } while (! stateUpdater.compareAndSet(this, oldVal, newVal));
        workerThread.execute(this);
    }
}
