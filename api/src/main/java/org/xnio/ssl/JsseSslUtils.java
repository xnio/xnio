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

import java.net.InetSocketAddress;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Sequence;
import org.xnio.SslClientAuthMode;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

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
     */
    public static SSLContext createSSLContext(OptionMap optionMap) throws NoSuchProviderException, NoSuchAlgorithmException {
        final String provider = optionMap.get(Options.SSL_PROVIDER);
        final String protocol = optionMap.get(Options.SSL_PROTOCOL);
        final SSLContext sslContext;
        if (protocol == null) {
            sslContext = SSLContext.getDefault();
        } else if (provider == null) {
            sslContext = SSLContext.getInstance(protocol);
        } else {
            sslContext = SSLContext.getInstance(protocol, provider);
        }
        return sslContext;
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
        final SSLEngine engine = sslContext.createSSLEngine(peerAddress.getHostName(), peerAddress.getPort());
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
