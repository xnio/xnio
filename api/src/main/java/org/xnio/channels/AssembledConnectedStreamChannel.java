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
 * A connected stream channel assembled from a stream source and stream sink.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public class AssembledConnectedStreamChannel extends AssembledStreamChannel implements ConnectedStreamChannel {
    private final ConnectedChannel connection;

    /**
     * Construct a new instance.
     *
     * @param connection the connected channel
     * @param source the source
     * @param sink the sink
     */
    public AssembledConnectedStreamChannel(final ConnectedChannel connection, final StreamSourceChannel source, final StreamSinkChannel sink) {
        super(connection, source, sink);
        this.connection = connection;
    }

    /**
     * Construct a new instance.  At least one side must be connected.
     *
     * @param source the source
     * @param sink the sink
     */
    public AssembledConnectedStreamChannel(final StreamSourceChannel source, final StreamSinkChannel sink) {
        this(new AssembledConnectedChannel(source, sink), source, sink);
    }

    public ChannelListener.Setter<? extends AssembledConnectedStreamChannel> getCloseSetter() {
        return (ChannelListener.Setter<? extends AssembledConnectedStreamChannel>) super.getCloseSetter();
    }

    public ChannelListener.Setter<? extends AssembledConnectedStreamChannel> getReadSetter() {
        return (ChannelListener.Setter<? extends AssembledConnectedStreamChannel>) super.getReadSetter();
    }

    public ChannelListener.Setter<? extends AssembledConnectedStreamChannel> getWriteSetter() {
        return (ChannelListener.Setter<? extends AssembledConnectedStreamChannel>) super.getWriteSetter();
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
