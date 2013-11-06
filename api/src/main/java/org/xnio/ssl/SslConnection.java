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
package org.xnio.ssl;

import java.io.IOException;

import javax.net.ssl.SSLSession;

import org.xnio.ChannelListener;
import org.xnio.Options;
import org.xnio.StreamConnection;
import org.xnio.XnioIoThread;
import org.xnio.channels.SslChannel;

/**
 * A stream connection which can use SSL/TLS to negotiate a security layer.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 *
 */
public abstract class SslConnection extends StreamConnection implements SslChannel {

    /**
     * Construct a new instance.
     *
     * @param thread the I/O thread of this connection
     */
    protected SslConnection(XnioIoThread thread) {
        super(thread);
    }

    /**
     * Start or restart the SSL/TLS handshake. To force a complete SSL/TLS session renegotiation, the current session
     * should be invalidated prior to calling this method. This method is not needed for the initial handshake unless
     * the {@link Options#SSL_STARTTLS} option is set as sending or receiving over the channel will automatically
     * initiate it.  This method must not be called while a read or write operation is taking place.
     *
     * @throws IOException if an I/O error occurs
     */
    public abstract void startHandshake() throws IOException;

    /**
     * Get the current {@code SSLSession} for this channel.
     *
     * @return the current {@code SSLSession}
     */
    public abstract SSLSession getSslSession();

    /**
     * Get the setter which can be used to change the handshake listener for this channel.
     *
     * @return the setter
     */
    public abstract ChannelListener.Setter<? extends SslConnection> getHandshakeSetter();

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override
    public ChannelListener.Setter<? extends SslConnection> getCloseSetter() {
        return (ChannelListener.Setter<? extends SslConnection>) super.getCloseSetter();
    }
}