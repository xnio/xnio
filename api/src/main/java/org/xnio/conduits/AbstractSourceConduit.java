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
 * An abstract base class for filtering source conduits.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class AbstractSourceConduit<D extends SourceConduit> extends AbstractConduit<D> implements SourceConduit {

    /**
     * Construct a new instance.
     *
     * @param next the delegate conduit to set
     */
    protected AbstractSourceConduit(final D next) {
        super(next);
    }

    public void terminateReads() throws IOException {
        next.terminateReads();
    }

    public boolean isReadShutdown() {
        return next.isReadShutdown();
    }

    public void resumeReads() {
        next.resumeReads();
    }

    public void suspendReads() {
        next.suspendReads();
    }

    public void wakeupReads() {
        next.wakeupReads();
    }

    public boolean isReadResumed() {
        return next.isReadResumed();
    }

    public void awaitReadable() throws IOException {
        next.awaitReadable();
    }

    public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
        next.awaitReadable(time, timeUnit);
    }

    public XnioIoThread getReadThread() {
        return next.getReadThread();
    }

    public void setReadReadyHandler(final ReadReadyHandler handler) {
        next.setReadReadyHandler(handler);
    }
}
