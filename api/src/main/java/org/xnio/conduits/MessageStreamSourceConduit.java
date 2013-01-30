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
 * A stream source conduit which reads stream data from messages.  The receive buffer should always be as large as
 * the largest possible incoming message, or larger, to avoid data loss.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class MessageStreamSourceConduit extends AbstractSourceConduit<MessageSourceConduit> implements StreamSourceConduit {

    /**
     * Construct a new instance.
     *
     * @param next the delegate conduit to set
     */
    public MessageStreamSourceConduit(final MessageSourceConduit next) {
        super(next);
    }

    public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        return target.transferFrom(new ConduitReadableByteChannel(this), position, count);
    }

    public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
        return Conduits.transfer(this, count, throughBuffer, target);
    }

    public int read(final ByteBuffer dst) throws IOException {
        return next.receive(dst);
    }

    public long read(final ByteBuffer[] dsts, final int offs, final int len) throws IOException {
        return next.receive(dsts, offs, len);
    }
}
