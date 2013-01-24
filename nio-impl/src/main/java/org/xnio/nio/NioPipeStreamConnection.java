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
import java.nio.channels.ClosedChannelException;
import java.nio.channels.Pipe;
import org.xnio.Option;
import org.xnio.Options;
import org.xnio.XnioWorker;

import static org.xnio.IoUtils.safeClose;

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

    private void terminated(final AbstractNioConnectionConduit<?, ?> conduit) {
        if (conduit != null) conduit.terminated();
    }

    protected void notifyWriteClosed() {
        terminated(sourceConduit);
    }

    protected void notifyReadClosed() {
        terminated(sinkConduit);
    }

    private void cancelKey(final AbstractNioConduit<?> conduit) {
        if (conduit != null) conduit.cancelKey();
    }

    protected void closeAction() throws IOException {
        try {
            cancelKey(sourceConduit);
            cancelKey(sinkConduit);
            try {
                sourceChannel.close();
            } catch (ClosedChannelException ignored) {
            }
            try {
                sinkChannel.close();
            } catch (ClosedChannelException ignored) {
            }
        } finally {
            safeClose(sourceChannel);
            safeClose(sinkChannel);
        }
    }

    protected void setSourceConduit(final NioPipeSourceConduit sourceConduit) {
        this.sourceConduit = sourceConduit;
        super.setSourceConduit(sourceConduit);
    }

    protected void setSinkConduit(final NioPipeSinkConduit sinkConduit) {
        this.sinkConduit = sinkConduit;
        super.setSinkConduit(sinkConduit);
    }

    public boolean supportsOption(final Option<?> option) {
        return option == Options.READ_TIMEOUT && sourceConduit != null || option == Options.WRITE_TIMEOUT && sinkConduit != null;
    }

    public <T> T getOption(final Option<T> option) throws IOException {
        if (option == Options.READ_TIMEOUT) {
            final NioPipeSourceConduit conduit = sourceConduit;
            return conduit == null ? null : option.cast(Integer.valueOf(conduit.getReadTimeout()));
        } else if (option == Options.WRITE_TIMEOUT) {
            final NioPipeSinkConduit conduit = sinkConduit;
            return conduit == null ? null : option.cast(Integer.valueOf(conduit.getWriteTimeout()));
        } else {
            return null;
        }
    }

    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        T result;
        if (option == Options.READ_TIMEOUT) {
            final NioPipeSourceConduit conduit = sourceConduit;
            result = conduit == null ? null : option.cast(Integer.valueOf(conduit.getAndSetReadTimeout(value == null ? 0 : Options.READ_TIMEOUT.cast(value).intValue())));
        } else if (option == Options.WRITE_TIMEOUT) {
            final NioPipeSinkConduit conduit = sinkConduit;
            result = conduit == null ? null : option.cast(Integer.valueOf(conduit.getAndSetWriteTimeout(value == null ? 0 : Options.WRITE_TIMEOUT.cast(value).intValue())));
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
