/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2017 Red Hat, Inc. and/or its affiliates.
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

package org.xnio;

import java.io.IOException;
import java.util.ServiceLoader;

/**
 * An interface which is loaded via {@link ServiceLoader} in order to take over the configuration of the default worker
 * in the event that no overriding configuration is present.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface XnioWorkerConfigurator {

    /**
     * Create the worker, returning {@code null} if no worker is available.
     *
     * @return the worker or {@code null} if none is available
     * @throws IOException if configuring the worker resulted in an exception
     */
    XnioWorker createWorker() throws IOException;
}
