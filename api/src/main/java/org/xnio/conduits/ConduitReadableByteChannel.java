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
import java.nio.channels.ReadableByteChannel;

/**
 * A byte channel which wraps a conduit.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ConduitReadableByteChannel implements ReadableByteChannel {

    private StreamSourceConduit conduit;

    /**
     * Construct a new instance.
     *
     * @param conduit the conduit to delegate to
     */
    public ConduitReadableByteChannel(final StreamSourceConduit conduit) {
        this.conduit = conduit;
    }

    public int read(final ByteBuffer dst) throws IOException {
        return conduit.read(dst);
    }

    public boolean isOpen() {
        return ! conduit.isReadShutdown();
    }

    public void close() throws IOException {
        conduit.terminateReads();
    }
}
