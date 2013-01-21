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

package org.xnio;

import java.io.IOException;
import java.net.SocketAddress;
import org.xnio.channels.CloseListenerSettable;
import org.xnio.channels.CloseableChannel;
import org.xnio.channels.ConnectedChannel;
import org.xnio.conduits.StreamSinkConduit;
import org.xnio.conduits.StreamSourceConduit;

/**
 * A connection between peers.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class Connection implements CloseableChannel, ConnectedChannel, CloseListenerSettable<Connection> {

    private final XnioWorker worker;
    private final StreamSourceConduit sourceConduit;
    private final StreamSinkConduit sinkConduit;
    private ChannelListener<? super Connection> closeListener;

    protected Connection(final XnioWorker worker, final StreamSourceConduit sourceConduit, final StreamSinkConduit sinkConduit) {
        this.worker = worker;
        this.sourceConduit = sourceConduit;
        this.sinkConduit = sinkConduit;
    }

    public void setCloseListener(final ChannelListener<? super Connection> listener) {
        this.closeListener = listener;
    }

    public ChannelListener<? super Connection> getCloseListener() {
        return closeListener;
    }

    public ChannelListener.Setter<Connection> getCloseSetter() {
        return new Setter<Connection>(this);
    }

    public StreamSourceConduit getSourceConduit() {
        return sourceConduit;
    }

    public StreamSinkConduit getSinkConduit() {
        return sinkConduit;
    }

    public final <A extends SocketAddress> A getPeerAddress(final Class<A> type) {
        return castAddress(type, getPeerAddress());
    }

    public final <A extends SocketAddress> A getLocalAddress(final Class<A> type) {
        return castAddress(type, getLocalAddress());
    }

    private static <A extends SocketAddress> A castAddress(final Class<A> type, SocketAddress address) {
        return type.isInstance(address) ? type.cast(address) : null;
    }

    public final XnioWorker getWorker() {
        return worker;
    }

    public boolean supportsOption(final Option<?> option) {
        return false;
    }

    public <T> T getOption(final Option<T> option) throws IOException {
        return null;
    }

    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        return null;
    }
}
