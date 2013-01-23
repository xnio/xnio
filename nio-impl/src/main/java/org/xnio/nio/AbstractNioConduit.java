/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2013 Red Hat, Inc. and/or its affiliates.
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

import java.nio.channels.SelectionKey;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import org.xnio.conduits.Conduit;

import static org.xnio.Bits.allAreClear;
import static org.xnio.Bits.allAreSet;
import static org.xnio.Bits.intBitMask;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
abstract class AbstractNioConduit<N extends AbstractSelectableChannel> implements Conduit, Runnable {

    private static final int FLAG_SCHEDULED = 1 << 5;
    private static final int FLAG_SUS_RES = 1 << 6;
    private static final int FLAG_RESUMED = 1 << 7;
    private static final int MASK_OPS = intBitMask(0, 4); // NIO uses five bits; 1 << 1 is unused

    protected final SelectionKey selectionKey;
    protected final WorkerThread workerThread;

    @SuppressWarnings("unused")
    private volatile int state;

    @SuppressWarnings("rawtypes")
    private static final AtomicIntegerFieldUpdater<AbstractNioConduit> stateUpdater = AtomicIntegerFieldUpdater.newUpdater(AbstractNioConduit.class, "state");

    public AbstractNioConduit(final SelectionKey selectionKey, final WorkerThread workerThread) {
        this.selectionKey = selectionKey;
        this.workerThread = workerThread;
        selectionKey.attach(this);
    }

    SelectionKey getSelectionKey() {
        return selectionKey;
    }

    WorkerThread getWorkerThread() {
        return workerThread;
    }

    N getChannel() {
        return (N) selectionKey.channel();
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

    public void run() {
        int oldVal, newVal;
        do {
            oldVal = state;
            newVal = oldVal & ~FLAG_SCHEDULED;
        } while (! stateUpdater.compareAndSet(this, oldVal, newVal));
        if (allAreSet(oldVal, FLAG_RESUMED)) {
            handleReady();
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

    void wakeup() {
        resume();
        execute();
    }

    public NioXnioWorker getWorker() {
        return workerThread.getWorker();
    }

    abstract void handleReady();

    abstract void forceTermination();
}
