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

/**
 * An abstract synchronized conduit.  All conduit operations are wrapped in synchronization blocks for simplified
 * thread safety.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class AbstractSynchronizedConduit<D extends Conduit> extends AbstractConduit<D> {
    protected final Object lock;

    /**
     * Construct a new instance.  A new lock object is created.
     *
     * @param next the next conduit in the chain
     */
    protected AbstractSynchronizedConduit(final D next) {
        this(next, new Object());
    }

    /**
     * Construct a new instance.
     *
     * @param next the next conduit in the chain
     * @param lock the lock object to use
     */
    protected AbstractSynchronizedConduit(final D next, final Object lock) {
        super(next);
        this.lock = lock;
    }
}
