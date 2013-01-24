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

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.Pipe;
import java.util.Set;
import org.xnio.Option;
import org.xnio.Options;
import org.xnio.XnioWorker;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class NioPipeStreamConnection extends AbstractNioStreamConnection {
    private final Pipe.SourceChannel sourceChannel;
    private final Pipe.SinkChannel sinkChannel;
    private NioPipeSourceConduit sourceConduit;
    private NioPipeSinkConduit sinkConduit;

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

    protected void setSourceConduit(final NioPipeSourceConduit sourceConduit) {
        this.sourceConduit = sourceConduit;
        super.setSourceConduit(sourceConduit);
    }

    protected void setSinkConduit(final NioPipeSinkConduit sinkConduit) {
        this.sinkConduit = sinkConduit;
        super.setSinkConduit(sinkConduit);
    }

    private static final Set<Option<?>> OPTIONS = Option.setBuilder()
            .add(Options.READ_TIMEOUT)
            .add(Options.WRITE_TIMEOUT)
            .create();

    public boolean supportsOption(final Option<?> option) {
        return OPTIONS.contains(option);
    }

    public <T> T getOption(final Option<T> option) throws IOException {
        if (option == Options.READ_TIMEOUT) {
            return option.cast(Integer.valueOf(sourceConduit.getReadTimeout()));
        } else if (option == Options.WRITE_TIMEOUT) {
            return option.cast(Integer.valueOf(sinkConduit.getWriteTimeout()));
        } else {
            return null;
        }
    }

    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        T result;
        if (option == Options.READ_TIMEOUT) {
            result = option.cast(Integer.valueOf(sourceConduit.getAndSetReadTimeout(Options.READ_TIMEOUT.cast(value).intValue())));
        } else if (option == Options.WRITE_TIMEOUT) {
            result = option.cast(Integer.valueOf(sinkConduit.getAndSetWriteTimeout(Options.WRITE_TIMEOUT.cast(value).intValue())));
        } else {
            return null;
        }
        return result;
    }

    Pipe.SourceChannel getSourcePipeChannel() {
        return sourceChannel;
    }

    Pipe.SinkChannel getSinkPipeChannel() {
        return sinkChannel;
    }
}
