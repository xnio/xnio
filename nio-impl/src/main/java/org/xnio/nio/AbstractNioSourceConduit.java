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

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.concurrent.TimeUnit;
import org.xnio.Connection;
import org.xnio.XnioExecutor;
import org.xnio.conduits.ReadReadyHandler;
import org.xnio.conduits.SourceConduit;

import static java.nio.channels.SelectionKey.OP_READ;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
abstract class AbstractNioSourceConduit<N extends AbstractSelectableChannel, C extends Connection> extends AbstractNioConnectionConduit<N, C> implements SourceConduit {
    private ReadReadyHandler handler;

    protected AbstractNioSourceConduit(final C connection, final SelectionKey selectionKey, final WorkerThread workerThread) {
        super(connection, selectionKey, workerThread);
    }

    public void terminateReads() throws IOException {
        if (tryClose()) try {
            closeAction();
        } finally {
            cancelKey();
            try { handler.terminated(); } catch (Throwable ignored) {}
        }
    }

    protected void closeAction() throws IOException {
        getChannel().close();
    }

    void handleReady() {
        final ReadReadyHandler handler = this.handler;
        if (handler == null) {
            suspend();
        } else {
            handler.readReady();
        }
    }

    void forceTermination() {
        final ReadReadyHandler handler = this.handler;
        if (handler == null) {
            try {
                terminateReads();
            } catch (Throwable ignored) {}
        } else {
            handler.forceTermination();
        }
    }

    public boolean isReadShutdown() {
        return getConnection().isReadShutdown();
    }

    public void resumeReads() {
        resume();
    }

    public void suspendReads() {
        suspend();
    }

    public void wakeupReads() {
        resume();
        execute();
    }

    public boolean isReadResumed() {
        return isResumed();
    }

    public void awaitReadable() throws IOException {
        SelectorUtils.await(getWorker().getXnio(), getChannel(), OP_READ);
    }

    public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
        SelectorUtils.await(getWorker().getXnio(), getChannel(), OP_READ, time, timeUnit);
    }

    public XnioExecutor getReadThread() {
        return getWorkerThread();
    }

    public void setReadReadyHandler(final ReadReadyHandler handler) {
        this.handler = handler;
    }
}
