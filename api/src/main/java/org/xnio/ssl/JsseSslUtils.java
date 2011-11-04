/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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

package org.xnio.ssl;

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
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Sequence;
import org.xnio.SslClientAuthMode;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;

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

    private static <T> T instantiate(Class<T> clazz) {
        try {
            return clazz.getConstructor().newInstance();
        } catch (InstantiationException e) {
            throw new IllegalArgumentException("Cannot instantiate " + clazz, e);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("Cannot instantiate " + clazz, e);
        } catch (InvocationTargetException e) {
            throw new IllegalArgumentException("Cannot instantiate " + clazz, e.getCause());
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Cannot instantiate " + clazz, e);
        }
    }

    /**
     * Create a new SSL engine, configured from an option map.
     *
     * @param sslContext the SSL context
     * @param optionMap the SSL options
     * @param peerAddress the peer address of the connection
     * @param server {@code true} to default to server mode, {@code false} otherwise
     * @return the configured SSL engine
     */
    public static SSLEngine createSSLEngine(SSLContext sslContext, OptionMap optionMap, InetSocketAddress peerAddress, boolean server) {
        final SSLEngine engine = sslContext.createSSLEngine(
                optionMap.get(Options.SSL_PEER_HOST_NAME, peerAddress.getHostName()),
                optionMap.get(Options.SSL_PEER_PORT, peerAddress.getPort())
        );
        final boolean clientMode = optionMap.get(Options.SSL_USE_CLIENT_MODE, false) ^ ! server;
        engine.setUseClientMode(clientMode);
        if (! clientMode) {
            final SslClientAuthMode clientAuthMode = optionMap.get(Options.SSL_CLIENT_AUTH_MODE);
            if (clientAuthMode != null) switch (clientAuthMode) {
                case NOT_REQUESTED:
                    engine.setNeedClientAuth(false);
                    engine.setWantClientAuth(false);
                    break;
                case REQUESTED:
                    engine.setNeedClientAuth(false);
                    engine.setWantClientAuth(true);
                    break;
                case REQUIRED:
                    engine.setWantClientAuth(false);
                    engine.setNeedClientAuth(true);
                    break;
            }
        }
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
