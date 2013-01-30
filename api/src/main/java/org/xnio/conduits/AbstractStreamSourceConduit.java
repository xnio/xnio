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
import java.nio.channels.FileChannel;
import org.xnio.channels.StreamSinkChannel;

/**
 * An abstract base class for filtering stream source conduits.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class AbstractStreamSourceConduit<D extends StreamSourceConduit> extends AbstractSourceConduit<D> implements StreamSourceConduit {

    /**
     * Construct a new instance.
     *
     * @param next the delegate conduit to set
     */
    protected AbstractStreamSourceConduit(final D next) {
        super(next);
    }

    public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        return next.transferTo(position, count, target);
    }

    public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
        return next.transferTo(count, throughBuffer, target);
    }

    public int read(final ByteBuffer dst) throws IOException {
        return next.read(dst);
    }

    public long read(final ByteBuffer[] dsts, final int offs, final int len) throws IOException {
        return next.read(dsts, offs, len);
    }
}
