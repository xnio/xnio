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

package org.xnio.conduits;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.xnio.XnioIoThread;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class AbstractSynchronizedSinkConduit<D extends SinkConduit> extends AbstractSynchronizedConduit<D> implements SinkConduit {

    protected AbstractSynchronizedSinkConduit(final D next) {
        super(next);
    }

    protected AbstractSynchronizedSinkConduit(final D next, final Object lock) {
        super(next, lock);
    }

    public void terminateWrites() throws IOException {
        synchronized (lock) {
            next.terminateWrites();
        }
    }

    public boolean isWriteShutdown() {
        synchronized (lock) {
            return next.isWriteShutdown();
        }
    }

    public void resumeWrites() {
        synchronized (lock) {
            next.resumeWrites();
        }
    }

    public void suspendWrites() {
        synchronized (lock) {
            next.suspendWrites();
        }
    }

    public void wakeupWrites() {
        synchronized (lock) {
            next.wakeupWrites();
        }
    }

    public boolean isWriteResumed() {
        synchronized (lock) {
            return next.isWriteResumed();
        }
    }

    public void awaitWritable() throws IOException {
        synchronized (lock) {
            next.awaitWritable();
        }
    }

    public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
        synchronized (lock) {
            next.awaitWritable(time, timeUnit);
        }
    }

    public XnioIoThread getWriteThread() {
        return next.getWriteThread();
    }

    public void setWriteReadyHandler(final WriteReadyHandler handler) {
        synchronized (lock) {
            next.setWriteReadyHandler(handler);
        }
    }

    public void truncateWrites() throws IOException {
        synchronized (lock) {
            next.truncateWrites();
        }
    }

    public boolean flush() throws IOException {
        synchronized (lock) {
            return next.flush();
        }
    }
}
