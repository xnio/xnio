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

import static java.security.AccessController.doPrivileged;
import static org.xnio._private.Messages.msg;

import java.nio.ByteBuffer;
import java.security.PrivilegedAction;
import java.security.Provider;
import java.security.Security;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

import org.xnio.Buffers;
import org.xnio.Option;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Property;
import org.xnio.Sequence;

import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslClientFactory;
import javax.security.sasl.SaslServer;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServerFactory;

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
     * Returns an iterator of all of the registered {@code SaslServerFactory}s where the order is based on the
     * order of the Provider registration and/or class path order.  Class path providers are listed before
     * global providers; in the event of a name conflict, the class path provider is preferred.
     *
     * @param classLoader the class loader to use
     * @param includeGlobal {@code true} to include globally registered providers, {@code false} to exclude them
     * @return the {@code Iterator} of {@code SaslServerFactory}s
     */
    public static Iterator<SaslServerFactory> getSaslServerFactories(ClassLoader classLoader, boolean includeGlobal) {
        return getFactories(SaslServerFactory.class, classLoader, includeGlobal);
    }

    /**
     * Returns an iterator of all of the registered {@code SaslServerFactory}s where the order is based on the
     * order of the Provider registration and/or class path order.
     *
     * @return the {@code Iterator} of {@code SaslServerFactory}s
     */
    public static Iterator<SaslServerFactory> getSaslServerFactories() {
        return getFactories(SaslServerFactory.class, null, true);
    }

    /**
     * Returns an iterator of all of the registered {@code SaslClientFactory}s where the order is based on the
     * order of the Provider registration and/or class path order.  Class path providers are listed before
     * global providers; in the event of a name conflict, the class path provider is preferred.
     *
     * @param classLoader the class loader to use
     * @param includeGlobal {@code true} to include globally registered providers, {@code false} to exclude them
     * @return the {@code Iterator} of {@code SaslClientFactory}s
     */
    public static Iterator<SaslClientFactory> getSaslClientFactories(ClassLoader classLoader, boolean includeGlobal) {
        return getFactories(SaslClientFactory.class, classLoader, includeGlobal);
    }

    /**
     * Returns an iterator of all of the registered {@code SaslClientFactory}s where the order is based on the
     * order of the Provider registration and/or class path order.
     *
     * @return the {@code Iterator} of {@code SaslClientFactory}s
     */
    public static Iterator<SaslClientFactory> getSaslClientFactories() {
        return getFactories(SaslClientFactory.class, null, true);
    }

    private static <T> Iterator<T> getFactories(Class<T> type, ClassLoader classLoader, boolean includeGlobal) {
        Set<T> factories = new LinkedHashSet<T>();
        final ServiceLoader<T> loader = ServiceLoader.load(type, classLoader);
        for (T factory : loader) {
            factories.add(factory);
        }
        if (includeGlobal) {
            Set<String> loadedClasses = new HashSet<String>();
            final String filter = type.getSimpleName() + ".";

            Provider[] providers = Security.getProviders();
            final SecurityManager sm = System.getSecurityManager();
            for (final Provider currentProvider : providers) {
                final ClassLoader cl = sm == null ? currentProvider.getClass().getClassLoader() : doPrivileged(new PrivilegedAction<ClassLoader>() {
                    public ClassLoader run() {
                        return currentProvider.getClass().getClassLoader();
                    }
                });
                for (Object currentKey : currentProvider.keySet()) {
                    if (currentKey instanceof String &&
                            ((String) currentKey).startsWith(filter) &&
                            ((String) currentKey).indexOf(' ') < 0) {
                        String className = currentProvider.getProperty((String) currentKey);
                        if (className != null && loadedClasses.add(className)) {
                            try {
                                factories.add(Class.forName(className, true, cl).asSubclass(type).newInstance());
                            } catch (ClassNotFoundException e) {
                            } catch (ClassCastException e) {
                            } catch (InstantiationException e) {
                            } catch (IllegalAccessException e) {
                            }
                        }
                    }
                }
            }
        }
        return factories.iterator();
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
        result = evaluateChallenge(client, source);
        if (result != null) {
            if (destination == null) {
                throw msg.extraChallenge();
            }
            destination.put(result);
            return false;
        } else {
            return true;
        }
    }

    /**
     * Evaluate a sasl challenge.  If the result is non-{@code null} then the negotiation is not yet complete and the data
     * returned needs to be sent to the server as a response.  If the result is {@code null}
     * then negotiation was successful and no response needs to be sent to the server.
     * <p>
     * The {@code source} buffer should have its position and remaining length set to encompass exactly one SASL
     * message.  The SASL message itself does not encode any length information so it is up to the protocol implementer
     * to ensure that the message is properly framed.
     *
     * @param client the SASL client to use to evaluate the challenge message
     * @param source the source buffer from which the challenge message should be read
     * @return {@code null} if negotiation is complete and successful, or the response otherwise
     * @throws SaslException if negotiation failed or another error occurred
     */
    public static byte[] evaluateChallenge(SaslClient client, ByteBuffer source) throws SaslException {
        return client.evaluateChallenge(Buffers.take(source));
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
        result = evaluateResponse(server, source);
        if (result != null) {
            if (destination == null) {
                throw msg.extraResponse();
            }
            destination.put(result);
            return server.isComplete();
        } else {
            return true;
        }
    }

    /**
     * Evaluate a sasl response.  If the result is non-{@code null} then the negotiation is not yet complete and the data
     * returned needs to be sent to the server as a response.  If the result is {@code null}
     * then negotiation was successful and no response needs to be sent to the client (other than a successful completion
     * message, depending on the protocol).
     * <p>
     * The {@code source} buffer should have its position and remaining length set to encompass exactly one SASL
     * message.  The SASL message itself does not encode any length information so it is up to the protocol implementer
     * to ensure that the message is properly framed.
     *
     * @param server the SASL server to use to evaluate the response message
     * @param source the source buffer from which the response message should be read
     * @return {@code true} if negotiation is complete and successful, {@code false} otherwise
     * @throws SaslException if negotiation failed or another error occurred
     */
    public static byte[] evaluateResponse(final SaslServer server, final ByteBuffer source) throws SaslException {
        return server.evaluateResponse(source.hasRemaining() ? Buffers.take(source) : EMPTY_BYTES);
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
        destination.put(wrap(client, source));
    }

    /**
     * Wrap a message.  Wrapping occurs from the source buffer to the destination idea.
     * <p>
     * The {@code source} buffer should have its position and remaining length set to encompass exactly one SASL
     * message (without the length field).  The SASL message itself does not encode any length information so it is up
     * to the protocol implementer to ensure that the message is properly framed.
     *
     * @param client the SASL client to wrap with
     * @param source the buffers from which bytes should be read
     * @return the wrap result
     * @throws SaslException if a SASL error occurs
     * @see SaslClient#wrap(byte[], int, int)
     */
    public static byte[] wrap(final SaslClient client, final ByteBuffer source) throws SaslException {
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
            result = client.wrap(Buffers.take(source, len), 0, len);
        }
        return result;
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
        destination.put(wrap(server, source));
    }

    /**
     * Wrap a message.  Wrapping occurs from the source buffer to the destination idea.
     * <p>
     * The {@code source} buffer should have its position and remaining length set to encompass exactly one SASL
     * message (without the length field).  The SASL message itself does not encode any length information so it is up
     * to the protocol implementer to ensure that the message is properly framed.
     *
     * @param server the SASL server to wrap with
     * @param source the buffers from which bytes should be read
     * @return the wrap result
     * @throws SaslException if a SASL error occurs
     * @see SaslServer#wrap(byte[], int, int)
     */
    public static byte[] wrap(final SaslServer server, final ByteBuffer source) throws SaslException {
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
        return result;
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
        destination.put(unwrap(client, source));
    }

    /**
     * Unwrap a message.  Unwrapping occurs from the source buffer to the destination idea.
     * <p>
     * The {@code source} buffer should have its position and remaining length set to encompass exactly one SASL
     * message (without the length field).  The SASL message itself does not encode any length information so it is up
     * to the protocol implementer to ensure that the message is properly framed.
     *
     * @param client the SASL client to unwrap with
     * @param source the buffers from which bytes should be read
     * @return the wrap result
     * @throws SaslException if a SASL error occurs
     * @see SaslClient#unwrap(byte[], int, int)
     */
    public static byte[] unwrap(final SaslClient client, final ByteBuffer source) throws SaslException {
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
        return result;
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
        destination.put(unwrap(server, source));
    }

    /**
     * Unwrap a message.  Unwrapping occurs from the source buffer to the destination idea.
     * <p>
     * The {@code source} buffer should have its position and remaining length set to encompass exactly one SASL
     * message (without the length field).  The SASL message itself does not encode any length information so it is up
     * to the protocol implementer to ensure that the message is properly framed.
     *
     * @param server the SASL server to unwrap with
     * @param source the buffers from which bytes should be read
     * @return the wrap result
     * @throws SaslException if a SASL error occurs
     * @see SaslServer#unwrap(byte[], int, int)
     */
    public static byte[] unwrap(final SaslServer server, final ByteBuffer source) throws SaslException {
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
        return result;
    }

    /**
     * Create a SASL property map from an XNIO option map.
     *
     * @param optionMap the option map
     * @param secure {@code true} if the channel is secure, {@code false} otherwise
     * @return the property map
     */
    public static Map<String, Object> createPropertyMap(final OptionMap optionMap, final boolean secure) {
        final Map<String,Object> propertyMap = new HashMap<String, Object>();
        add(optionMap, Options.SASL_POLICY_FORWARD_SECRECY, propertyMap, Sasl.POLICY_FORWARD_SECRECY, null);
        add(optionMap, Options.SASL_POLICY_NOACTIVE, propertyMap, Sasl.POLICY_NOACTIVE, null);
        add(optionMap, Options.SASL_POLICY_NOANONYMOUS, propertyMap, Sasl.POLICY_NOANONYMOUS, Boolean.TRUE);
        add(optionMap, Options.SASL_POLICY_NODICTIONARY, propertyMap, Sasl.POLICY_NODICTIONARY, null);
        add(optionMap, Options.SASL_POLICY_NOPLAINTEXT, propertyMap, Sasl.POLICY_NOPLAINTEXT, Boolean.valueOf(! secure));
        add(optionMap, Options.SASL_POLICY_PASS_CREDENTIALS, propertyMap, Sasl.POLICY_PASS_CREDENTIALS, null);
        add(optionMap, Options.SASL_REUSE, propertyMap, Sasl.REUSE, null);
        add(optionMap, Options.SASL_SERVER_AUTH, propertyMap, Sasl.SERVER_AUTH, null);
        addQopList(optionMap, Options.SASL_QOP, propertyMap, Sasl.QOP);
        add(optionMap, Options.SASL_STRENGTH, propertyMap, Sasl.STRENGTH, null);
        addSaslProperties(optionMap, Options.SASL_PROPERTIES, propertyMap);
        return propertyMap;
    }

    private static <T> void add(OptionMap optionMap, Option<T> option, Map<String, Object> map, String propName, T defaultVal) {
        final Object value = optionMap.get(option, defaultVal);
        if (value != null) map.put(propName, value.toString().toLowerCase(Locale.US));
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

    private static void addSaslProperties(OptionMap optionMap, Option<Sequence<Property>> option, Map<String, Object> map) {
        final Sequence<Property> value = optionMap.get(option);
        if (value == null) return;
        for (Property current : value) {
            map.put(current.getKey(), current.getValue());
        }
    }
}
