/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.xnio.channels;

import java.net.SocketAddress;
import org.xnio.ChannelListener;

/**
 * A connected bidirectional message channel assembled from a readable and writable message channel.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public class AssembledConnectedMessageChannel extends AssembledMessageChannel implements ConnectedChannel {
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

    public ChannelListener.Setter<? extends AssembledConnectedMessageChannel> getCloseSetter() {
        return (ChannelListener.Setter<? extends AssembledConnectedMessageChannel>) super.getCloseSetter();
    }

    public ChannelListener.Setter<? extends AssembledConnectedMessageChannel> getReadSetter() {
        return (ChannelListener.Setter<? extends AssembledConnectedMessageChannel>) super.getReadSetter();
    }

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
