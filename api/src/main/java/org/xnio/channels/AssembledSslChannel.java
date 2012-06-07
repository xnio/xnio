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

import java.io.IOException;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;

import javax.net.ssl.SSLSession;

/**
 * An assembled SSL channel.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public class AssembledSslChannel extends AssembledConnectedChannel implements SslChannel {

    private final SslChannel sslChannel;

    private final ChannelListener.Setter<AssembledSslChannel> handshakeSetter;

    /**
     * Construct a new instance.  At least one side must be an SSL channel.
     *
     * @param readChannel the read channel
     * @param writeChannel the write channel
     */
    public AssembledSslChannel(final SuspendableReadChannel readChannel, final SuspendableWriteChannel writeChannel) {
        super(readChannel, writeChannel);
        if (readChannel instanceof SslChannel) {
            sslChannel = (SslChannel) readChannel;
        } else if (writeChannel instanceof SslChannel) {
            sslChannel = (SslChannel) writeChannel;
        } else {
            throw new IllegalArgumentException("At least one specified channel must be an SSL channel");
        }
        handshakeSetter = ChannelListeners.getDelegatingSetter(sslChannel.getHandshakeSetter(), this);
    }

    public void startHandshake() throws IOException {
        sslChannel.startHandshake();
    }

    public SSLSession getSslSession() {
        return sslChannel.getSslSession();
    }

    public ChannelListener.Setter<? extends AssembledSslChannel> getHandshakeSetter() {
        return handshakeSetter;
    }

    public ChannelListener.Setter<? extends AssembledSslChannel> getCloseSetter() {
        return (ChannelListener.Setter<? extends AssembledSslChannel>) super.getCloseSetter();
    }
}
