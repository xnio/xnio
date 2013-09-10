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

import static org.xnio._private.Messages.msg;

import org.xnio.XnioWorker;

/**
 * An abstract base class for filtering conduits.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class AbstractConduit<D extends Conduit> implements Conduit {

    /**
     * The delegate conduit.
     */
    protected final D next;

    /**
     * Construct a new instance.
     *
     * @param next the delegate conduit to set
     */
    protected AbstractConduit(final D next) {
        if (next == null) {
            throw msg.nullParameter("next");
        }
        this.next = next;
    }

    public XnioWorker getWorker() {
        return next.getWorker();
    }
}
