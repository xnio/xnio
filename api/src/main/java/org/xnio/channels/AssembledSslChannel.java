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
            throw msg.oneChannelMustBeSSL();
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

    @SuppressWarnings("unchecked")
    public ChannelListener.Setter<? extends AssembledSslChannel> getCloseSetter() {
        return (ChannelListener.Setter<? extends AssembledSslChannel>) super.getCloseSetter();
    }
}
