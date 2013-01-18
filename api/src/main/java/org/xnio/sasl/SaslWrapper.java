/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2011 Red Hat, Inc. and/or its affiliates, and individual
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
     * Wrap a message.
     *
     * @param bytes the incoming message
     * @param off the offset into the byte array
     * @param len the length of the byte array to wrap
     * @return the wrap result
     * @throws SaslException if a problem occurs
     */
    public abstract byte[] wrap(byte[] bytes, int off, int len) throws SaslException;

    /**
     * Wrap a message.
     *
     * @param bytes the incoming message
     * @return the wrap result
     * @throws SaslException if a problem occurs
     */
    public final byte[] wrap(byte[] bytes) throws SaslException {
        return unwrap(bytes, 0, bytes.length);
    }

    /**
     * Wrap a message.
     *
     * @param source the buffer from which bytes should be read
     * @return the wrap result
     * @throws SaslException if a problem occurs
     */
    public abstract byte[] wrap(ByteBuffer source) throws SaslException;

    /**
     * Unwrap a message.
     *
     * @param bytes the incoming message
     * @param off the offset into the byte array
     * @param len the length of the byte array to wrap
     * @return the unwrap result
     * @throws SaslException if a problem occurs
     */
    public abstract byte[] unwrap(byte[] bytes, int off, int len) throws SaslException;

    /**
     * Unwrap a message.
     *
     * @param bytes the incoming message
     * @return the unwrap result
     * @throws SaslException if a problem occurs
     */
    public final byte[] unwrap(byte[] bytes) throws SaslException {
        return unwrap(bytes, 0, bytes.length);
    }

    /**
     * Unwrap a message.
     *
     * @param source the buffer from which bytes should be read
     * @return the unwrap result
     * @throws SaslException if a problem occurs
     */
    public abstract byte[] unwrap(ByteBuffer source) throws SaslException;

    /**
     * Wrap a message.  Wrapping occurs from the source buffer to the destination idea.
     * <p/>
     * The {@code source} buffer should have its position and remaining length set to encompass exactly one SASL message
     * (without the length field).  The SASL message itself does not encode any length information so it is up to the
     * protocol implementer to ensure that the message is properly framed.
     *
     * @param destination the buffer into which bytes should be written
     * @param source the buffers from which bytes should be read
     *
     * @throws SaslException if a SASL error occurs
     * @see SaslClient#wrap(byte[], int, int)
     * @see SaslServer#wrap(byte[], int, int)
     */
    public final void wrap(ByteBuffer destination, ByteBuffer source) throws SaslException {
        destination.put(wrap(source));
    }

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
    public final void unwrap(ByteBuffer destination, ByteBuffer source) throws SaslException {
        destination.put(wrap(source));
    }

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

    public byte[] wrap(final byte[] bytes, final int off, final int len) throws SaslException {
        return saslClient.wrap(bytes, off, len);
    }

    public byte[] unwrap(final byte[] bytes, final int off, final int len) throws SaslException {
        return saslClient.unwrap(bytes, off, len);
    }

    public byte[] wrap(final ByteBuffer source) throws SaslException {
        return SaslUtils.wrap(saslClient, source);
    }

    public byte[] unwrap(final ByteBuffer source) throws SaslException {
        return SaslUtils.unwrap(saslClient, source);
    }
}

final class SaslServerWrapper extends SaslWrapper {
    private final SaslServer saslServer;

    SaslServerWrapper(final SaslServer saslServer) {
        this.saslServer = saslServer;
    }

    public byte[] wrap(final byte[] bytes, final int off, final int len) throws SaslException {
        return saslServer.wrap(bytes, off, len);
    }

    public byte[] unwrap(final byte[] bytes, final int off, final int len) throws SaslException {
        return saslServer.unwrap(bytes, off, len);
    }

    public byte[] wrap(final ByteBuffer source) throws SaslException {
        return SaslUtils.wrap(saslServer, source);
    }

    public byte[] unwrap(final ByteBuffer source) throws SaslException {
        return SaslUtils.unwrap(saslServer, source);
    }
}
