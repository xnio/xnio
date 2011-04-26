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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.xnio.Buffers;
import org.xnio.Option;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Sequence;

import javax.security.sasl.Sasl;
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
     * A zero-length byte array, useful for sending and receiving empty SASL messages.
     */
    public static final byte[] EMPTY_BYTES = new byte[0];

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
     * then negotiation was successful and no response needs to be sent to the client (other than a successful completion
     * message, depending on the protocol).
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
        result = server.evaluateResponse(source.hasRemaining() ? Buffers.take(source) : EMPTY_BYTES);
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
     * message (without the length field).  The SASL message itself does not encode any length information so it is up
     * to the protocol implementer to ensure that the message is properly framed.
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
        if (len == 0) {
            result = client.wrap(EMPTY_BYTES, 0, len);
        } else if (source.hasArray()) {
            final byte[] array = source.array();
            final int offs = source.arrayOffset();
            source.position(source.position() + len);
            result = client.wrap(array, offs, len);
        } else {
            result = client.wrap(len == 0 ? EMPTY_BYTES : Buffers.take(source, len), 0, len);
        }
        destination.put(result, 0, result.length);
    }

    /**
     * Wrap a message.  Wrapping occurs from the source buffer to the destination idea.
     * <p>
     * The {@code source} buffer should have its position and remaining length set to encompass exactly one SASL
     * message (without the length field).  The SASL message itself does not encode any length information so it is up
     * to the protocol implementer to ensure that the message is properly framed.
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
        if (len == 0) {
            result = server.wrap(EMPTY_BYTES, 0, len);
        } else if (source.hasArray()) {
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
     * message (without the length field).  The SASL message itself does not encode any length information so it is up
     * to the protocol implementer to ensure that the message is properly framed.
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
        if (len == 0) {
            result = client.unwrap(EMPTY_BYTES, 0, len);
        } else if (source.hasArray()) {
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
     * message (without the length field).  The SASL message itself does not encode any length information so it is up
     * to the protocol implementer to ensure that the message is properly framed.
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
        if (len == 0) {
            result = server.unwrap(EMPTY_BYTES, 0, len);
        } else if (source.hasArray()) {
            final byte[] array = source.array();
            final int offs = source.arrayOffset();
            source.position(source.position() + len);
            result = server.unwrap(array, offs, len);
        } else {
            result = server.unwrap(Buffers.take(source, len), 0, len);
        }
        destination.put(result, 0, result.length);
    }

    /**
     * Create a SASL property map from an XNIO option map.
     *
     * @param optionMap the option map
     * @return the property map
     */
    public static Map<String, Object> createPropertyMap(OptionMap optionMap) {
        final Map<String,Object> propertyMap = new HashMap<String, Object>();

        add(optionMap, Options.SASL_POLICY_FORWARD_SECRECY, propertyMap, Sasl.POLICY_FORWARD_SECRECY);
        add(optionMap, Options.SASL_POLICY_NOACTIVE, propertyMap, Sasl.POLICY_NOACTIVE);
        add(optionMap, Options.SASL_POLICY_NOANONYMOUS, propertyMap, Sasl.POLICY_NOANONYMOUS);
        add(optionMap, Options.SASL_POLICY_NODICTIONARY, propertyMap, Sasl.POLICY_NODICTIONARY);
        add(optionMap, Options.SASL_POLICY_NOPLAINTEXT, propertyMap, Sasl.POLICY_NOPLAINTEXT);
        add(optionMap, Options.SASL_POLICY_PASS_CREDENTIALS, propertyMap, Sasl.POLICY_PASS_CREDENTIALS);
        add(optionMap, Options.SASL_REUSE, propertyMap, Sasl.REUSE);
        add(optionMap, Options.SASL_SERVER_AUTH, propertyMap, Sasl.SERVER_AUTH);
        addQopList(optionMap, Options.SASL_QOP, propertyMap, Sasl.QOP);
        add(optionMap, Options.SASL_STRENGTH, propertyMap, Sasl.STRENGTH);
        return propertyMap;
    }

    private static void add(OptionMap optionMap, Option<?> option, Map<String, Object> map, String propName) {
        final Object value = optionMap.get(option);
        if (value != null) map.put(propName, value.toString().toLowerCase());
    }

    private static void addQopList(OptionMap optionMap, Option<Sequence<SaslQop>> option, Map<String, Object> map, String propName) {
        final Sequence<SaslQop> value = optionMap.get(option);
        if (value == null) return;
        final Sequence<SaslQop> seq = value;
        final StringBuilder builder = new StringBuilder();
        final Iterator<SaslQop> iterator = seq.iterator();
        if (!iterator.hasNext()) {
            return;
        } else do {
            builder.append(iterator.next().getString());
            if (iterator.hasNext()) {
                builder.append(',');
            }
        } while (iterator.hasNext());
        map.put(propName, builder.toString());
    }
}
