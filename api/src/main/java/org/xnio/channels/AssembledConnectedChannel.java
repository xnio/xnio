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
        if (readChannel instanceof ConnectedChannel) {
            connection = (ConnectedChannel) readChannel;
        } else if (writeChannel instanceof ConnectedChannel) {
            connection = (ConnectedChannel) writeChannel;
        } else {
            throw new IllegalArgumentException("At least one specified channel must be a connected channel");
        }
    }

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
