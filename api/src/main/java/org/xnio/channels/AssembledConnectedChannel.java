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

import static org.xnio._private.Messages.msg;

import java.net.SocketAddress;
import org.xnio.ChannelListener;

/**
 * A closeable, connected view over a read and write side of a suspendable channel, at least one of which is connected.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public class AssembledConnectedChannel extends AssembledChannel implements ConnectedChannel {
    private final ConnectedChannel connection;

    /**
     * Construct a new instance.  At least one of the channels must be an instance of {@link ConnectedChannel}.
     *
     * @param readChannel the read channel
     * @param writeChannel the write channel
     */
    public AssembledConnectedChannel(final SuspendableReadChannel readChannel, final SuspendableWriteChannel writeChannel) {
        super(readChannel, writeChannel);
        ConnectedChannel ch = Channels.unwrap(ConnectedChannel.class, readChannel);
        if (ch == null) ch = Channels.unwrap(ConnectedChannel.class, writeChannel);
        if (ch == null) throw msg.oneChannelMustBeConnection();
        connection = ch;
    }

    @SuppressWarnings("unchecked")
    public ChannelListener.Setter<? extends AssembledConnectedChannel> getCloseSetter() {
        return (ChannelListener.Setter<? extends AssembledConnectedChannel>) super.getCloseSetter();
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
