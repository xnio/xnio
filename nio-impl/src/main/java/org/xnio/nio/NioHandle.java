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

import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;

import static org.xnio.Bits.allAreClear;
import static org.xnio.Bits.allAreSet;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
abstract class NioHandle {
    private final WorkerThread workerThread;
    private final SelectionKey selectionKey;

    protected NioHandle(final WorkerThread workerThread, final SelectionKey selectionKey) {
        this.workerThread = workerThread;
        this.selectionKey = selectionKey;
    }

    void resume(final int ops) {
        try {
            if (! allAreSet(selectionKey.interestOps(), ops)) {
                workerThread.setOps(selectionKey, ops);
            }
        } catch (CancelledKeyException ignored) {}
    }

    void wakeup(final int ops) {
        workerThread.queueTask(new Runnable() {
            public void run() {
                handleReady(ops);
            }
        });
        try {
            if (! allAreSet(selectionKey.interestOps(), ops)) {
                workerThread.setOps(selectionKey, ops);
            }
        } catch (CancelledKeyException ignored) {}
    }

    void suspend(final int ops) {
        try {
            if (! allAreClear(selectionKey.interestOps(), ops)) {
                workerThread.clearOps(selectionKey, ops);
            }
        } catch (CancelledKeyException ignored) {}
    }

    boolean isResumed(final int ops) {
        try {
            return allAreSet(selectionKey.interestOps(), ops);
        } catch (CancelledKeyException ignored) {
            return false;
        }
    }

    abstract void handleReady(final int ops);

    abstract void forceTermination();

    abstract void terminated();

    WorkerThread getWorkerThread() {
        return workerThread;
    }

    SelectionKey getSelectionKey() {
        return selectionKey;
    }

    void cancelKey(final boolean block) {
        workerThread.cancelKey(selectionKey, block);
    }
}
