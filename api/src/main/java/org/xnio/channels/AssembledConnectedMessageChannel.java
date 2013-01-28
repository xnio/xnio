/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2012 Red Hat, Inc. and/or its affiliates, and individual
 * contributors as indicated by the @author tags.
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

package org.xnio.channels;

import java.net.SocketAddress;
import org.xnio.ChannelListener;

/**
 * A connected bidirectional message channel assembled from a readable and writable message channel.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public class AssembledConnectedMessageChannel extends AssembledMessageChannel implements ConnectedMessageChannel {
    private final ConnectedChannel connection;

    /**
     * Construct a new instance.
     *
     * @param connection the connected channel
     * @param readable the read channel
     * @param writable the write channel
     */
    public AssembledConnectedMessageChannel(final ConnectedChannel connection, final ReadableMessageChannel readable, final WritableMessageChannel writable) {
        super(connection, readable, writable);
        this.connection = connection;
    }

    /**
     * Construct a new instance.  At least one side must be connected.
     *
     * @param readable the read channel
     * @param writable the write channel
     */
    public AssembledConnectedMessageChannel(final ReadableMessageChannel readable, final WritableMessageChannel writable) {
        this(new AssembledConnectedChannel(readable, writable), readable, writable);
    }

    @SuppressWarnings("unchecked")
    public ChannelListener.Setter<? extends AssembledConnectedMessageChannel> getCloseSetter() {
        return (ChannelListener.Setter<? extends AssembledConnectedMessageChannel>) super.getCloseSetter();
    }

    @SuppressWarnings("unchecked")
    public ChannelListener.Setter<? extends AssembledConnectedMessageChannel> getReadSetter() {
        return (ChannelListener.Setter<? extends AssembledConnectedMessageChannel>) super.getReadSetter();
    }

    @SuppressWarnings("unchecked")
    public ChannelListener.Setter<? extends AssembledConnectedMessageChannel> getWriteSetter() {
        return (ChannelListener.Setter<? extends AssembledConnectedMessageChannel>) super.getWriteSetter();
    }

    public SocketAddress getPeerAddress() {
        return connection.getPeerAddress();
    }

    public <A extends SocketAddress> A getPeerAddress(final Class<A> type) {
        return connection.getPeerAddress(type);
    }

    public SocketAddress getLocalAddress() {
        return connection.getLocalAddress();
    }

    public <A extends SocketAddress> A getLocalAddress(final Class<A> type) {
        return connection.getLocalAddress(type);
    }
}
