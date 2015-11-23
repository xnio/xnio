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
import org.xnio.ChannelListeners;

import static java.lang.Thread.currentThread;
import static org.xnio.IoUtils.safeClose;

/**
* @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
*/
final class NioTcpServerHandle extends NioHandle implements ChannelClosed {

    private final Runnable freeTask;
    private final NioTcpServer server;
    private int count;
    private int low;
    private int high;
    private int tokenCount = -1;
    private boolean stopped;

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
            if (! stopped && server.resumed) super.resume(SelectionKey.OP_ACCEPT);
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
            if (stopped || ! server.resumed) super.suspend(SelectionKey.OP_ACCEPT);
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
            stopped = false;
            if (server.resumed) {
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
                    if (server.resumed) {
                        super.resume(SelectionKey.OP_ACCEPT);
                    }
                }
                return;
            }
            workerThread = workerThread.getNextThread();
        }
        setThreadNewCount(workerThread, newCount);
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
        if (stopped) {
            return false;
        }
        if (tokenCount != -1 && --tokenCount == 0) {
            setThreadNewCount(getWorkerThread().getNextThread(), server.getTokenConnectionCount());
        }
        if (++count >= high || tokenCount == 0) {
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
                if (server.resumed) resume();
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
}
