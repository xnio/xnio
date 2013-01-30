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
import java.nio.ByteBuffer;

/**
 * An abstract base class for filtering message source conduits.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class AbstractMessageSourceConduit<D extends MessageSourceConduit> extends AbstractSourceConduit<D> implements MessageSourceConduit {

    /**
     * Construct a new instance.
     *
     * @param next the delegate conduit to set
     */
    protected AbstractMessageSourceConduit(final D next) {
        super(next);
    }

    public int receive(final ByteBuffer dst) throws IOException {
        return next.receive(dst);
    }

    public long receive(final ByteBuffer[] dsts, final int offs, final int len) throws IOException {
        return next.receive(dsts, offs, len);
    }
}
