/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, JBoss Inc., and individual contributors as indicated
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

package org.xnio.sasl;

import java.nio.ByteBuffer;

import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

/**
 * A wrapper delegation class for SASL that presents the same wrap/unwrap API regardless of whether it is
 * dealing with a SASL client or server.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class SaslWrapper {

    /**
     * Wrap a message.  Wrapping occurs from the source buffer to the destination idea.
     * <p>
     * The {@code source} buffer should have its position and remaining length set to encompass exactly one SASL
     * message (without the length field).  The SASL message itself does not encode any length information so it is up
     * to the protocol implementer to ensure that the message is properly framed.
     *
     * @param destination the buffer into which bytes should be written
     * @param source the buffers from which bytes should be read
     * @throws SaslException if a SASL error occurs
     * @see SaslClient#wrap(byte[], int, int)
     * @see SaslServer#wrap(byte[], int, int)
     */
    public abstract void wrap(ByteBuffer destination, ByteBuffer source) throws SaslException;

    /**
     * Unwrap a message.  Unwrapping occurs from the source buffer to the destination idea.
     * <p>
     * The {@code source} buffer should have its position and remaining length set to encompass exactly one SASL
     * message (without the length field).  The SASL message itself does not encode any length information so it is up
     * to the protocol implementer to ensure that the message is properly framed.
     *
     * @param destination the buffer into which bytes should be written
     * @param source the buffers from which bytes should be read
     * @throws SaslException if a SASL error occurs
     * @see SaslClient#unwrap(byte[], int, int)
     */
    public abstract void unwrap(ByteBuffer destination, ByteBuffer source) throws SaslException;

    /**
     * Create a SASL wrapper for a SASL client.
     *
     * @param saslClient the SASL client
     * @return the wrapper
     */
    public static SaslWrapper create(SaslClient saslClient) {
        return new SaslClientWrapper(saslClient);
    }

    /**
     * Create a SASL wrapper for a SASL server.
     *
     * @param saslServer the SASL server
     * @return the wrapper
     */
    public static SaslWrapper create(SaslServer saslServer) {
        return new SaslServerWrapper(saslServer);
    }
}

final class SaslClientWrapper extends SaslWrapper {
    private final SaslClient saslClient;

    SaslClientWrapper(final SaslClient saslClient) {
        this.saslClient = saslClient;
    }

    public void wrap(final ByteBuffer destination, final ByteBuffer source) throws SaslException {
        SaslUtils.wrap(saslClient, destination, source);
    }

    public void unwrap(final ByteBuffer destination, final ByteBuffer source) throws SaslException {
        SaslUtils.unwrap(saslClient, destination, source);
    }
}

final class SaslServerWrapper extends SaslWrapper {
    private final SaslServer saslServer;

    SaslServerWrapper(final SaslServer saslServer) {
        this.saslServer = saslServer;
    }

    public void wrap(final ByteBuffer destination, final ByteBuffer source) throws SaslException {
        SaslUtils.wrap(saslServer, destination, source);
    }

    public void unwrap(final ByteBuffer destination, final ByteBuffer source) throws SaslException {
        SaslUtils.unwrap(saslServer, destination, source);
    }
}
