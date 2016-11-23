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

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface XnioServerMXBean {
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
    String getWorkerName();

    /**
     * Get the bind address.  The address is converted into a readable string form.
     *
     * @return the bind address
     */
    String getBindAddress();

    /**
     * Get an estimate of the current connection count.
     *
     * @return an estimate of the current connection count
     */
    int getConnectionCount();

    /**
     * Get the connection limit high-water mark.  If the connection count hits this number, no new connections
     * will be accepted until the count drops below the low-water mark.
     *
     * @return the connection limit high-water mark
     */
    int getConnectionLimitHighWater();

    /**
     * Get the connection limit low-water mark.  If the connection count has previously hit the high water mark,
     * once it drops back down below this count, connections will be accepted again.
     *
     * @return the connection limit low-water mark
     */
    int getConnectionLimitLowWater();

}
