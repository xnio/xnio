/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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

import javax.net.ssl.SSLSession;

import org.xnio.ChannelListener;

/**
 * A channel which can use SSL/TLS to negotiate a security layer.
 */
public interface SslChannel extends ConnectedChannel {

    /**
     * Start or restart the SSL/TLS handshake.  To force a complete SSL/TLS session renegotiation, the current
     * session should be invalidated prior to calling this method.  This method is not needed for the initial handshake
     * as sending or receiving over the channel will automatically initiate it.
     *
     * @throws IOException if an I/O error occurs
     */
    void startHandshake() throws IOException;

    /**
     * Get the current {@code SSLSession} for this channel.
     *
     * @return the current {@code SSLSession}
     */
    SSLSession getSslSession();

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends SslChannel> getCloseSetter();

    /**
     * Get the setter which can be used to change the handshake listener for this channel.
     *
     * @return the setter
     */
    ChannelListener.Setter<? extends SslChannel> getHandshakeSetter();
}
