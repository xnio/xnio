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
import org.xnio.Buffers;

import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslServer;
import javax.security.sasl.SaslException;

/**
 * Utility methods for handling SASL authentication using NIO-style programming methods.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class SaslUtils {

    private SaslUtils() {
    }

    /**
     * Evaluate a sasl challenge.  If the result is {@code false} then the negotiation is not yet complete and the data
     * written into the destination buffer needs to be sent to the server as a response.  If the result is {@code true}
     * then negotiation was successful and no response needs to be sent to the server.
     * <p>
     * The {@code source} buffer should have its position and remaining length set to encompass exactly one SASL
     * message.  The SASL message itself does not encode any length information so it is up to the protocol implementer
     * to ensure that the message is properly framed.
     *
     * @param client the SASL client to use to evaluate the challenge message
     * @param destination the destination buffer into which the response message should be written, if any
     * @param source the source buffer from which the challenge message should be read
     * @return {@code true} if negotiation is complete and successful, {@code false} otherwise
     * @throws SaslException if negotiation failed or another error occurred
     */
    public static boolean evaluateChallenge(SaslClient client, ByteBuffer destination, ByteBuffer source) throws SaslException {
        final byte[] result;
        result = client.evaluateChallenge(Buffers.take(source));
        if (result != null) {
            destination.put(result);
            return false;
        } else {
            return true;
        }
    }

    /**
     * Evaluate a sasl response.  If the result is {@code false} then the negotiation is not yet complete and the data
     * written into the destination buffer needs to be sent to the server as a response.  If the result is {@code true}
     * then negotiation was successful and no response needs to be sent to the server.
     * <p>
     * The {@code source} buffer should have its position and remaining length set to encompass exactly one SASL
     * message.  The SASL message itself does not encode any length information so it is up to the protocol implementer
     * to ensure that the message is properly framed.
     *
     * @param server the SASL server to use to evaluate the response message
     * @param destination the destination buffer into which the response message should be written, if any
     * @param source the source buffer from which the response message should be read
     * @return {@code true} if negotiation is complete and successful, {@code false} otherwise
     * @throws SaslException if negotiation failed or another error occurred
     */
    public static boolean evaluateResponse(SaslServer server, ByteBuffer destination, ByteBuffer source) throws SaslException {
        final byte[] result;
        result = server.evaluateResponse(Buffers.take(source));
        if (result != null) {
            destination.put(result);
            return false;
        } else {
            return true;
        }
    }

    /**
     * Wrap a message.  Wrapping occurs from the source buffer to the destination idea.
     * <p>
     * The {@code source} buffer should have its position and remaining length set to encompass exactly one SASL
     * message.  The SASL message itself does not encode any length information so it is up to the protocol implementer
     * to ensure that the message is properly framed.
     *
     * @param client the SASL client to wrap with
     * @param destination the buffer into which bytes should be written
     * @param source the buffers from which bytes should be read
     * @throws SaslException if a SASL error occurs
     * @see SaslClient#wrap(byte[], int, int)
     */
    public static void wrap(SaslClient client, ByteBuffer destination, ByteBuffer source) throws SaslException {
        final byte[] result;
        final int len = source.remaining();
        if (source.hasArray()) {
            final byte[] array = source.array();
            final int offs = source.arrayOffset();
            source.position(source.position() + len);
            result = client.wrap(array, offs, len);
        } else {
            result = client.wrap(Buffers.take(source, len), 0, len);
        }
        destination.put(result, 0, result.length);
    }

    /**
     * Wrap a message.  Wrapping occurs from the source buffer to the destination idea.
     * <p>
     * The {@code source} buffer should have its position and remaining length set to encompass exactly one SASL
     * message.  The SASL message itself does not encode any length information so it is up to the protocol implementer
     * to ensure that the message is properly framed.
     *
     * @param server the SASL server to wrap with
     * @param destination the buffer into which bytes should be written
     * @param source the buffers from which bytes should be read
     * @throws SaslException if a SASL error occurs
     * @see SaslServer#wrap(byte[], int, int)
     */
    public static void wrap(SaslServer server, ByteBuffer destination, ByteBuffer source) throws SaslException {
        final byte[] result;
        final int len = source.remaining();
        if (source.hasArray()) {
            final byte[] array = source.array();
            final int offs = source.arrayOffset();
            source.position(source.position() + len);
            result = server.wrap(array, offs, len);
        } else {
            result = server.wrap(Buffers.take(source, len), 0, len);
        }
        destination.put(result, 0, result.length);
    }

    /**
     * Unwrap a message.  Unwrapping occurs from the source buffer to the destination idea.
     * <p>
     * The {@code source} buffer should have its position and remaining length set to encompass exactly one SASL
     * message.  The SASL message itself does not encode any length information so it is up to the protocol implementer
     * to ensure that the message is properly framed.
     *
     * @param client the SASL client to unwrap with
     * @param destination the buffer into which bytes should be written
     * @param source the buffers from which bytes should be read
     * @throws SaslException if a SASL error occurs
     * @see SaslClient#unwrap(byte[], int, int)
     */
    public static void unwrap(SaslClient client, ByteBuffer destination, ByteBuffer source) throws SaslException {
        final byte[] result;
        final int len = source.remaining();
        if (source.hasArray()) {
            final byte[] array = source.array();
            final int offs = source.arrayOffset();
            source.position(source.position() + len);
            result = client.unwrap(array, offs, len);
        } else {
            result = client.unwrap(Buffers.take(source, len), 0, len);
        }
        destination.put(result, 0, result.length);
    }

    /**
     * Unwrap a message.  Unwrapping occurs from the source buffer to the destination idea.
     * <p>
     * The {@code source} buffer should have its position and remaining length set to encompass exactly one SASL
     * message.  The SASL message itself does not encode any length information so it is up to the protocol implementer
     * to ensure that the message is properly framed.
     *
     * @param server the SASL server to unwrap with
     * @param destination the buffer into which bytes should be written
     * @param source the buffers from which bytes should be read
     * @throws SaslException if a SASL error occurs
     * @see SaslServer#unwrap(byte[], int, int)
     */
    public static void unwrap(SaslServer server, ByteBuffer destination, ByteBuffer source) throws SaslException {
        final byte[] result;
        final int len = source.remaining();
        if (source.hasArray()) {
            final byte[] array = source.array();
            final int offs = source.arrayOffset();
            source.position(source.position() + len);
            result = server.unwrap(array, offs, len);
        } else {
            result = server.unwrap(Buffers.take(source, len), 0, len);
        }
        destination.put(result, 0, result.length);
    }
}
