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

    @SuppressWarnings("unchecked")
    public ChannelListener.Setter<? extends AssembledConnectedSslStreamChannel> getCloseSetter() {
        return (ChannelListener.Setter<? extends AssembledConnectedSslStreamChannel>) super.getCloseSetter();
    }

    @SuppressWarnings("unchecked")
    public ChannelListener.Setter<? extends AssembledConnectedSslStreamChannel> getReadSetter() {
        return (ChannelListener.Setter<? extends AssembledConnectedSslStreamChannel>) super.getReadSetter();
    }

    @SuppressWarnings("unchecked")
    public ChannelListener.Setter<? extends AssembledConnectedSslStreamChannel> getWriteSetter() {
        return (ChannelListener.Setter<? extends AssembledConnectedSslStreamChannel>) super.getWriteSetter();
    }
}
