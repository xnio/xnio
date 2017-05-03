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

package org.xnio.management;

import java.util.Set;


/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface XnioWorkerMXBean {

    /**
     * Get the name of the provider.
     *
     * @return the name of the provider
     */
    String getProviderName();

    /**
     * Get the worker's name.
     *
     * @return the worker's name
     */
    String getName();

    /**
     * Determine whether shutdown has been requested for this worker.
     *
     * @return {@code true} if shutdown was requested, {@code false} otherwise
     */
    boolean isShutdownRequested();

    /**
     * Get the core worker thread pool size.
     *
     * @return the core worker pool size
     */
    int getCoreWorkerPoolSize();

    /**
     * Get the maximum worker thread pool size.
     *
     * @return the maximum worker pool size
     */
    int getMaxWorkerPoolSize();

    /**
     * Get an estimate of the number of busy threads in the worker pool.
     *
     * @return the estimated number of busy threads in the worker pool
     */
    int getBusyWorkerThreadCount();

    /**
     * Get the I/O thread count.
     *
     * @return the I/O thread count
     */
    int getIoThreadCount();

    /**
     * Get an estimate of the number of tasks in the worker queue.
     *
     * @return the task count estimate
     */
    int getWorkerQueueSize();

    /**
     * Get servers that are opened under this worker.
     * @return set of {@link XnioServerMXBean}
     */
    Set<XnioServerMXBean> getServerMXBeans();
}
