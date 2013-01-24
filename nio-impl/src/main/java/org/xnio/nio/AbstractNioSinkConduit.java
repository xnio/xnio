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
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.concurrent.TimeUnit;
import org.xnio.Connection;
import org.xnio.XnioExecutor;
import org.xnio.conduits.SinkConduit;
import org.xnio.conduits.WriteReadyHandler;

import static java.nio.channels.SelectionKey.OP_WRITE;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
abstract class AbstractNioSinkConduit<N extends AbstractSelectableChannel, C extends Connection> extends AbstractNioConnectionConduit<N, C> implements SinkConduit {
    private WriteReadyHandler handler;

    protected AbstractNioSinkConduit(final C connection, final SelectionKey selectionKey, final WorkerThread workerThread) {
        super(connection, selectionKey, workerThread);
    }

    public void terminateWrites() throws IOException {
        truncateWrites();
    }

    public void truncateWrites() throws IOException {
        if (tryClose()) try {
            closeAction();
        } catch (ClosedChannelException ignored) {
        } finally {
            cancelKey();
            terminated();
        }
    }

    void terminated() {
        try {
            handler.terminated();
        } catch (Throwable ignored) {
        }
    }

    protected void closeAction() throws IOException {
        getChannel().close();
    }

    void handleReady() {
        final WriteReadyHandler handler = this.handler;
        if (handler == null) {
            suspend();
        } else {
            handler.writeReady();
        }
    }

    void forceTermination() {
        final WriteReadyHandler handler = this.handler;
        if (handler == null) {
            try {
                truncateWrites();
            } catch (Throwable ignored) {}
        } else {
            handler.forceTermination();
        }
    }

    public boolean isWriteShutdown() {
        return getConnection().isWriteShutdown();
    }

    public void resumeWrites() {
        resume();
    }

    public void suspendWrites() {
        suspend();
    }

    public void wakeupWrites() {
        resume();
        execute();
    }

    public boolean flush() throws IOException {
        return true;
    }

    public boolean isWriteResumed() {
        return isResumed();
    }

    public void awaitWritable() throws IOException {
        SelectorUtils.await(getWorker().getXnio(), getChannel(), OP_WRITE);
    }

    public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
        SelectorUtils.await(getWorker().getXnio(), getChannel(), OP_WRITE, time, timeUnit);
    }

    public XnioExecutor getWriteThread() {
        return getWorkerThread();
    }

    public void setWriteReadyHandler(final WriteReadyHandler handler) {
        this.handler = handler;
    }
}
