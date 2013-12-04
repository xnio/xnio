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

import static java.lang.Thread.currentThread;
import static org.xnio.Bits.allAreClear;
import static org.xnio.Bits.allAreSet;
import static org.xnio.nio.NioXnio.REPORT_VIOLATIONS;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
abstract class NioHandle {
    private final WorkerThread workerThread;
    private final SelectionKey selectionKey;
    private int ops;

    protected NioHandle(final WorkerThread workerThread, final SelectionKey selectionKey) {
        this.workerThread = workerThread;
        this.selectionKey = selectionKey;
        ops = selectionKey.interestOps();
    }

    void resume(final int ops) {
        final int oldOps = this.ops;
        if (allAreSet(oldOps, ops)) {
            return;
        }
        if (REPORT_VIOLATIONS && oldOps != 0 && currentThread() != workerThread) {
            throw new IllegalStateException("Resume is only allowed from the channel I/O thread when already resumed");
        }
        try {
            this.ops = oldOps | ops;
            if (! allAreSet(selectionKey.interestOps(), ops)) {
                workerThread.setOps(selectionKey, ops);
            }
        } catch (CancelledKeyException ignored) {}
    }

    void wakeup(final int ops) {
        final int oldOps = this.ops;
        if (! allAreSet(oldOps, ops)) {
            if (REPORT_VIOLATIONS && oldOps != 0 && currentThread() != workerThread) {
                throw new IllegalStateException("Resume is only allowed from the channel I/O thread when already resumed");
            }
            try {
                this.ops = oldOps | ops;
                if (! allAreSet(selectionKey.interestOps(), ops)) {
                    workerThread.setOps(selectionKey, ops);
                }
            } catch (CancelledKeyException ignored) {}
        }
        workerThread.queueTask(new Runnable() {
            public void run() {
                handleReady(ops);
            }
        });
    }

    void suspend(final int ops) {
        if (allAreClear(this.ops, ops)) {
            return;
        }
        if (REPORT_VIOLATIONS && currentThread() != workerThread) {
            throw new IllegalStateException("Suspend is only allowed from the channel I/O thread");
        }
        this.ops &= ~ops;
    }

    void shutdown() {
        this.ops = 0;
    }

    boolean isResumed(final int ops) {
        return allAreSet(this.ops, ops);
    }

    final void preHandleReady(int ops) {
        int spuriousOps = ops & ~this.ops;
        if (spuriousOps != 0) {
            workerThread.clearOps(selectionKey, spuriousOps);
        }
        ops &= ~spuriousOps;
        if (ops != 0) {
            handleReady(ops);
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
}
