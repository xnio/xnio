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
import java.util.concurrent.TimeUnit;

import org.xnio.ChannelListeners;

import static java.lang.Math.min;
import static java.lang.Math.max;
import static java.lang.Thread.currentThread;

import org.jboss.logging.Logger;

import static org.xnio.IoUtils.safeClose;
import static org.xnio.nio.Log.tcpServerConnectionLimitLog;
import static org.xnio.nio.Log.tcpServerLog;

/**
* @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
*/
final class NioTcpServerHandle extends NioHandle implements ChannelClosed {

    private static final String FQCN = NioTcpServerHandle.class.getName();
    private final Runnable freeTask;
    private final NioTcpServer server;
    private int count;
    private int low;
    private int high;
    private int tokenCount = -1;
    private boolean stopped;
    private boolean backOff;
    private int backOffTime = 0;

    NioTcpServerHandle(final NioTcpServer server, final SelectionKey key, final WorkerThread thread, final int low, final int high) {
        super(thread, key);
        this.server = server;
        this.low = low;
        this.high = high;
        freeTask = new Runnable() {
            public void run() {
                freeConnection();
            }
        };
    }

    void handleReady(final int ops) {
        ChannelListeners.invokeChannelListener(server, server.getAcceptListener());
    }

    void forceTermination() {
        safeClose(server);
    }

    void terminated() {
        server.invokeCloseHandler();
    }

    Runnable getFreeTask() {
        return freeTask;
    }

    void resume() {
        final WorkerThread thread = getWorkerThread();
        if (thread == currentThread()) {
            if (! stopped && ! backOff && server.resumed) super.resume(SelectionKey.OP_ACCEPT);
        } else {
            thread.execute(new Runnable() {
                public void run() {
                    resume();
                }
            });
        }
    }

    void suspend() {
        final WorkerThread thread = getWorkerThread();
        if (thread == currentThread()) {
            if (stopped || backOff || ! server.resumed) super.suspend(SelectionKey.OP_ACCEPT);
        } else {
            thread.execute(new Runnable() {
                public void run() {
                    suspend();
                }
            });
        }
    }

    public void channelClosed() {
        final WorkerThread thread = getWorkerThread();
        if (thread == currentThread()) {
            freeConnection();
        } else {
            thread.execute(freeTask);
        }
    }

    void freeConnection() {
        assert currentThread() == getWorkerThread();
        if (count-- <= low && tokenCount != 0 && stopped) {
            tcpServerConnectionLimitLog.logf(FQCN, Logger.Level.DEBUG, null,
                    "Connection freed, resuming accept connections");
            stopped = false;
            if (server.resumed) {
                // end backoff optimistically
                backOff = false;
                super.resume(SelectionKey.OP_ACCEPT);
            }
        }
    }

    void setTokenCount(final int newCount) {
        WorkerThread workerThread = getWorkerThread();
        if (workerThread == currentThread()) {
            if (tokenCount == 0) {
                tokenCount = newCount;
                if (count <= low && stopped) {
                    stopped = false;
                    if (server.resumed && ! backOff) {
                        super.resume(SelectionKey.OP_ACCEPT);
                    }
                }
                return;
            }
            workerThread = workerThread.getNextThread();
        }
        setThreadNewCount(workerThread, newCount);
    }

    /**
     * Start back-off, when an accept produces an exception.
     */
    void startBackOff() {
        backOff = true;
        backOffTime = max(250, min(30_000, backOffTime << 2));
        suspend();
        getWorkerThread().executeAfter(this::endBackOff, backOffTime, TimeUnit.MILLISECONDS);
    }

    /**
     * End back-off, when an accept may be retried.
     */
    void endBackOff() {
        backOff = false;
        resume();
    }

    /**
     * Reset back-off, when an accept has succeeded.
     */
    void resetBackOff() {
        backOffTime = 0;
    }

    private void setThreadNewCount(final WorkerThread workerThread, final int newCount) {
        final int number = workerThread.getNumber();
        workerThread.execute(new Runnable() {
            public void run() {
                server.getHandle(number).setTokenCount(newCount);
            }
        });
    }

    void initializeTokenCount(final int newCount) {
        WorkerThread workerThread = getWorkerThread();
        final int number = workerThread.getNumber();
        if (workerThread == currentThread()) {
            tokenCount = newCount;
            if (newCount == 0) {
                stopped = true;
                super.suspend(SelectionKey.OP_ACCEPT);
            }
        } else {
            workerThread.execute(new Runnable() {
                public void run() {
                    server.getHandle(number).initializeTokenCount(newCount);
                }
            });
        }
    }

    boolean getConnection() {
        assert currentThread() == getWorkerThread();
        if (stopped || backOff) {
            tcpServerConnectionLimitLog.logf(FQCN, Logger.Level.DEBUG, null, "Refusing accepting request (temporarily stopped: %s, backed off: %s)", stopped, backOff);
            return false;
        }
        if (tokenCount != -1 && --tokenCount == 0) {
            setThreadNewCount(getWorkerThread().getNextThread(), server.getTokenConnectionCount());
        }
        if (++count >= high || tokenCount == 0) {
            if (tcpServerLog.isDebugEnabled() && count >= high)
                tcpServerConnectionLimitLog.logf(FQCN, Logger.Level.DEBUG, null,
                            "Total open connections reach high water limit (%s) by this new accepting request. Temporarily stopping accept connections",
                            high);
            stopped = true;
            super.suspend(SelectionKey.OP_ACCEPT);
        }
        return true;
    }

    public void executeSetTask(final int high, final int low) {
        final WorkerThread thread = getWorkerThread();
        if (thread == currentThread()) {
            this.high = high;
            this.low = low;
            if (count >= high && ! stopped) {
                stopped = true;
                suspend();
            } else if (count <= low && stopped) {
                stopped = false;
                if (server.resumed && ! backOff) resume();
            }
        } else {
            thread.execute(new Runnable() {
                public void run() {
                    executeSetTask(high, low);
                }
            });
        }
    }

    int getConnectionCount() {
        assert currentThread() == getWorkerThread();
        return count;
    }

    int getBackOffTime() {
        return backOffTime;
    }
}
