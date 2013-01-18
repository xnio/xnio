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

package org.xnio.ducts;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class MessageStreamSinkDuct extends AbstractSinkDuct<MessageSinkDuct> implements StreamSinkDuct {

    public MessageStreamSinkDuct(final MessageSinkDuct next) {
        super(next);
    }

    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        return src.transferTo(position, count, new DuctWritableByteChannel(this));
    }

    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        return Ducts.transfer(source, count, throughBuffer, this);
    }

    public int write(final ByteBuffer src) throws IOException {
        return next.send(src);
    }

    public long write(final ByteBuffer[] srcs, final int offs, final int len) throws IOException {
        return next.send(srcs, offs, len);
    }
}
