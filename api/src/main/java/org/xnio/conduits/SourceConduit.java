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
public interface SourceConduit extends Conduit {

    void terminateReads() throws IOException;

    boolean isReadShutdown();

    void resumeReads();

    void suspendReads();

    void wakeupReads();

    boolean isReadResumed();

    void awaitReadable() throws IOException;

    void awaitReadable(long time, TimeUnit timeUnit) throws IOException;

    /**
     * Get the XNIO read thread.
     *
     * @return the XNIO read thread
     */
    XnioIoThread getReadThread();

    void setReadReadyHandler(ReadReadyHandler handler);
}
