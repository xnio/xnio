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
 * A connected SSL stream channel assembled from a stream source and stream sink.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public class AssembledConnectedSslStreamChannel extends AssembledConnectedStreamChannel implements ConnectedSslStreamChannel {
    private final SslChannel sslChannel;
    private final ChannelListener.Setter<AssembledConnectedSslStreamChannel> handshakeSetter;

    /**
     * Construct a new instance.
     *
     * @param sslChannel the SSL channel
     * @param source the source
     * @param sink the sink
     */
    public AssembledConnectedSslStreamChannel(final SslChannel sslChannel, final StreamSourceChannel source, final StreamSinkChannel sink) {
        super(sslChannel, source, sink);
        this.sslChannel = sslChannel;
        handshakeSetter = ChannelListeners.getDelegatingSetter(sslChannel.getHandshakeSetter(), this);
    }

    /**
     * Construct a new instance.  At least one side must be an SSL channel.
     *
     * @param source the source
     * @param sink the sink
     */
    public AssembledConnectedSslStreamChannel(final StreamSourceChannel source, final StreamSinkChannel sink) {
        this(new AssembledSslChannel(source, sink), source, sink);
    }

    public void startHandshake() throws IOException {
        sslChannel.startHandshake();
    }

    public SSLSession getSslSession() {
        return sslChannel.getSslSession();
    }

    public ChannelListener.Setter<? extends AssembledConnectedSslStreamChannel> getHandshakeSetter() {
        return handshakeSetter;
    }

    public ChannelListener.Setter<? extends AssembledConnectedSslStreamChannel> getCloseSetter() {
        return (ChannelListener.Setter<? extends AssembledConnectedSslStreamChannel>) super.getCloseSetter();
    }

    public ChannelListener.Setter<? extends AssembledConnectedSslStreamChannel> getReadSetter() {
        return (ChannelListener.Setter<? extends AssembledConnectedSslStreamChannel>) super.getReadSetter();
    }

    public ChannelListener.Setter<? extends AssembledConnectedSslStreamChannel> getWriteSetter() {
        return (ChannelListener.Setter<? extends AssembledConnectedSslStreamChannel>) super.getWriteSetter();
    }
}
