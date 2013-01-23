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

package org.xnio.nio;

import java.net.SocketAddress;
import java.nio.channels.Pipe;
import org.xnio.XnioWorker;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class NioPipeStreamConnection extends AbstractNioStreamConnection {
    private final Pipe.SourceChannel sourceChannel;
    private final Pipe.SinkChannel sinkChannel;

    NioPipeStreamConnection(final XnioWorker worker, final Pipe.SourceChannel sourceChannel, final Pipe.SinkChannel sinkChannel) {
        super(worker);
        this.sourceChannel = sourceChannel;
        this.sinkChannel = sinkChannel;
    }

    public SocketAddress getPeerAddress() {
        return null;
    }

    public SocketAddress getLocalAddress() {
        return null;
    }

    protected boolean readClosed() {
        return super.readClosed();
    }

    protected boolean writeClosed() {
        return super.writeClosed();
    }

    Pipe.SourceChannel getSourcePipeChannel() {
        return sourceChannel;
    }

    Pipe.SinkChannel getSinkPipeChannel() {
        return sinkChannel;
    }
}
