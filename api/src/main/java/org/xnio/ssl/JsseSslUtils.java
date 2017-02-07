/*
 * JBoss, Home of Professional Open Source.
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

package org.xnio.ssl;

import static org.xnio._private.Messages.msg;

import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;

import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Sequence;

/**
 * Utility methods for creating JSSE constructs and configuring them via XNIO option maps.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class JsseSslUtils {

    private JsseSslUtils() {
    }

    /**
     * Create a new SSL context, configured from an option map.
     *
     * @param optionMap the SSL context options
     * @return a new context
     * @throws NoSuchProviderException if there is no matching provider
     * @throws NoSuchAlgorithmException if there is no matching algorithm
     * @throws KeyManagementException if the context initialization fails
     */
    public static SSLContext createSSLContext(OptionMap optionMap) throws NoSuchProviderException, NoSuchAlgorithmException, KeyManagementException {
        return createSSLContext(null, null, null, optionMap);
    }

    /**
     * Create a new SSL context, configured from an option map and the given parameters.
     *
     * @param keyManagers the key managers to use, or {@code null} to configure from the option map
     * @param trustManagers the trust managers to use, or {@code null} to configure from the option map
     * @param secureRandom the secure RNG to use, or {@code null} to choose a system default
     * @param optionMap the SSL context options
     * @return a new context
     * @throws NoSuchProviderException if there is no matching provider
     * @throws NoSuchAlgorithmException if there is no matching algorithm
     * @throws KeyManagementException if the context initialization fails
     */
    public static SSLContext createSSLContext(KeyManager[] keyManagers, TrustManager[] trustManagers, SecureRandom secureRandom, OptionMap optionMap) throws NoSuchAlgorithmException, NoSuchProviderException, KeyManagementException {
        final String provider = optionMap.get(Options.SSL_PROVIDER);
        final String protocol = optionMap.get(Options.SSL_PROTOCOL);
        final SSLContext sslContext;
        if (protocol == null) {
            // Default context is initialized automatically
            return SSLContext.getDefault();
        } else if (provider == null) {
            sslContext = SSLContext.getInstance(protocol);
        } else {
            sslContext = SSLContext.getInstance(protocol, provider);
        }
        if (keyManagers == null) {
            final Sequence<Class<? extends KeyManager>> keyManagerClasses = optionMap.get(Options.SSL_JSSE_KEY_MANAGER_CLASSES);
            if (keyManagerClasses != null) {
                final int size = keyManagerClasses.size();
                keyManagers = new KeyManager[size];
                for (int i = 0; i < size; i ++) {
                    keyManagers[i] = instantiate(keyManagerClasses.get(i));
                }
            }
        }
        if (trustManagers == null) {
            final Sequence<Class<? extends TrustManager>> trustManagerClasses = optionMap.get(Options.SSL_JSSE_TRUST_MANAGER_CLASSES);
            if (trustManagerClasses != null) {
                final int size = trustManagerClasses.size();
                trustManagers = new TrustManager[size];
                for (int i = 0; i < size; i ++) {
                    trustManagers[i] = instantiate(trustManagerClasses.get(i));
                }
            }
        }
        sslContext.init(keyManagers, trustManagers, secureRandom);
        sslContext.getClientSessionContext().setSessionCacheSize(optionMap.get(Options.SSL_CLIENT_SESSION_CACHE_SIZE, 0));
        sslContext.getClientSessionContext().setSessionTimeout(optionMap.get(Options.SSL_CLIENT_SESSION_TIMEOUT, 0));
        sslContext.getServerSessionContext().setSessionCacheSize(optionMap.get(Options.SSL_SERVER_SESSION_CACHE_SIZE, 0));
        sslContext.getServerSessionContext().setSessionTimeout(optionMap.get(Options.SSL_SERVER_SESSION_TIMEOUT, 0));
        return sslContext;
    }

    @SuppressWarnings("TryWithIdenticalCatches")
    private static <T> T instantiate(Class<T> clazz) {
        try {
            return clazz.getConstructor().newInstance();
        } catch (InstantiationException e) {
            throw msg.cantInstantiate(clazz, e);
        } catch (IllegalAccessException e) {
            throw msg.cantInstantiate(clazz, e);
        } catch (NoSuchMethodException e) {
            throw msg.cantInstantiate(clazz, e);
        } catch (InvocationTargetException e) {
            throw msg.cantInstantiate(clazz, e.getCause());
        }
    }

    /**
     * Create a new client mode SSL engine, configured from an option map.
     *
     * @param sslContext the SSL context
     * @param optionMap the SSL options
     * @param peerAddress the peer address of the connection
     * @return the configured SSL engine
     */
    public static SSLEngine createSSLEngine(SSLContext sslContext, OptionMap optionMap, InetSocketAddress peerAddress) {
        final SSLEngine engine = sslContext.createSSLEngine(
                optionMap.get(Options.SSL_PEER_HOST_NAME, peerAddress.getHostString()),
                optionMap.get(Options.SSL_PEER_PORT, peerAddress.getPort())
        );
        engine.setUseClientMode(true);
        engine.setEnableSessionCreation(optionMap.get(Options.SSL_ENABLE_SESSION_CREATION, true));
        final Sequence<String> cipherSuites = optionMap.get(Options.SSL_ENABLED_CIPHER_SUITES);
        if (cipherSuites != null) {
            final Set<String> supported = new HashSet<String>(Arrays.asList(engine.getSupportedCipherSuites()));
            final List<String> finalList = new ArrayList<String>();
            for (String name : cipherSuites) {
                if (supported.contains(name)) {
                    finalList.add(name);
                }
            }
            engine.setEnabledCipherSuites(finalList.toArray(new String[finalList.size()]));
        }
        final Sequence<String> protocols = optionMap.get(Options.SSL_ENABLED_PROTOCOLS);
        if (protocols != null) {
            final Set<String> supported = new HashSet<String>(Arrays.asList(engine.getSupportedProtocols()));
            final List<String> finalList = new ArrayList<String>();
            for (String name : protocols) {
                if (supported.contains(name)) {
                    finalList.add(name);
                }
            }
            engine.setEnabledProtocols(finalList.toArray(new String[finalList.size()]));
        }
        return engine;
    }
}
